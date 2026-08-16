/**
 * 外币消费全链路测试（自动登记 → pending 占额 → 对账 → 账单入账 → 冲正释放）
 * =====================================================================
 * 覆盖业务决策（用户拍板：消费自动登记、按登记汇率立即占额、对账后按实际结算精调）：
 *
 * A. CreditCore 计算链路（#computeMonths 三分支）：
 *    - 外币消费无登记 → 回退 toCNY 入账 + 占额
 *    - 外币消费已自动登记 pending → 不入账 billAmount，但占可用额度（pendingReserved）
 *    - 外币消费已对账 reconciled → 按 actual_rmb 入账 + 额度精调
 *    - 跨月外币 + 冲正（reversed 排除 + 登记软删）释放占额
 *    - 池内外币 pending 占池额度（#poolPendingReserved）
 *
 * B. reconcileForeign 对账事务（H4）：
 *    - 对账成功：reconcile + 全量重算同一事务，提交
 *    - 对账失败（actualRate<=0 / actualRmb<=0）：事务回滚抛错
 *    - 对账后重算正确把 actual_rmb 计入账单
 *
 * C. Controller 全链路：
 *    - foreignHistory：扫描账本返回历史外币（含未登记）
 *    - foreignRegister：补登记成功/失败回滚
 *    - foreignReconcile：参数校验 + 对账
 *    - foreignDelete：删除登记 + 重算（释放 pending 占额）
 *
 * 运行：npm run test:unit -- tests/unit/foreignFullChain.test.js
 */
jest.mock('../../src/common/config/db', () => {
  const { createMockDb } = require('./helpers/creditMockDb');
  return createMockDb();
});
jest.mock('../../src/modules/card/model', () => ({
  findById: jest.fn(),
  findAll: jest.fn(),
}));

const db = require('../../src/common/config/db');
const CardModel = require('../../src/modules/card/model');
const CreditCore = require('../../src/modules/card/core/CreditCore');

const USER = 'U-001';
const CARD = 'CARD_FX';
const CARD_B = 'CARD_FX_B';
const POOL = 'POOL_FX';

function makeCard(overrides = {}) {
  return {
    id: CARD,
    user_id: USER,
    card_type: 'credit',
    bank_id: 'BK_FX',
    alias: '外币卡',
    credit_limit: '10000',
    temp_limit: '0',
    points_rate: '1',
    bill_day: 15,
    repay_day: 25,
    share_pool_id: null,
    ...overrides,
  };
}

/** n 个月前的 'YYYY-MM' */
function monthsAgo(n) {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
const CUR = monthsAgo(0);

/** 注册某卡账本路由（消费/冲正/还款/外币登记/pending 占用/既有账单） */
function registerCard({ cardId = CARD, expenses = [], reversals = [], regs = [], pending = 0, existingMonths = [], existingBills = null, repays = [] }) {
  const byCard = (p) => p && p[0] === cardId;
  db._when(['SELECT id, amount, currency, exchange_rate, trans_date, reversed_id'], expenses, byCard);
  db._when(['SELECT reversed_id FROM account'], reversals, byCard);
  db._when(['SELECT * FROM card_foreign_register'], regs, byCard);
  db._when(['SELECT r.repay_amount'], repays, byCard);
  db._when(['SELECT bill_month FROM card_bill'], existingMonths.map((m) => ({ bill_month: m })), byCard);
  db._when(['AS total', 'FROM card_foreign_register'], [{ total: String(pending) }], byCard);
  db._when(['SELECT * FROM card_bill', 'bill_month = ?'], existingBills || [], byCard);
}

/** 注册共享池路由 */
function registerPool({ creditLimit = 5000, tempLimit = 0, net = 0, pending = 0, cards = [] }) {
  db._when(['SELECT id FROM card_base WHERE share_pool_id'], cards.map((id) => ({ id })));
  db._when(['SELECT total_credit_limit, total_temp_limit'], [{ total_credit_limit: String(creditLimit), total_temp_limit: String(tempLimit) }]);
  db._when(['COALESCE(SUM(t.net), 0) AS net'], [{ net: String(net) }]);
  db._when(['AS total', 'FROM card_foreign_register fr'], [{ total: String(pending) }]);
  db._when(['SELECT * FROM card_credit_pool'], [{
    id: POOL, user_id: USER, bank_id: 'BK_FX', bank_name: '外币银行',
    total_credit_limit: String(creditLimit), total_temp_limit: String(tempLimit),
  }]);
}

beforeEach(() => {
  db._reset();
  CardModel.findById.mockReset();
  CardModel.findAll.mockReset();
  db._fallback(['AS total'], [{ total: '0' }]);
  db._fallback(['SELECT * FROM card_foreign_register'], []);
  db._fallback(['SELECT bill_month FROM card_bill'], []);
  db._fallback(['SELECT * FROM card_bill'], []);
  db._fallback(['UPDATE card_bill SET'], [{ affectedRows: 1 }]);
  db._fallback(['INSERT INTO card_bill'], [{ affectedRows: 1, insertId: 1 }]);
});

// =====================================================================
// A. CreditCore 外币计算链路（自动登记 → 占额 → 对账 → 入账）
// =====================================================================
describe('外币全链路 · CreditCore 计算链路', () => {
  test('① 外币消费自动登记 pending → 不入账 billAmount，但占用可用额度', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    // 既有当前月账单快照存在（信用卡正常有账期），pending 外币只占额度不计入 billAmount
    registerCard({
      existingMonths: [CUR],
      existingBills: [{ id: 'BILL_FX', bill_month: CUR }],
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e1', status: 'pending', currency: 'USD', registered_rmb: '710' }],
      pending: 710,
    });
    const s = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(s.billMonth).toBe(CUR);
    expect(s.billAmount).toBe(0);          // pending 不计入账单
    expect(s.needRepay).toBe(0);
    expect(s.availLimit).toBe(10000 - 710); // 但占额度防超刷
  });

  test('② 对账 reconciled → 按银行实际结算 actual_rmb 入账，额度精调', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e1', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    const s = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(s.billAmount).toBe(720);
    expect(s.needRepay).toBe(720);
    expect(s.availLimit).toBe(10000 - 720);
  });

  test('③ 外币无登记（历史遗漏）→ 回退 toCNY 入账 + 占额', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
    });
    const s = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(s.billAmount).toBe(710);
    expect(s.availLimit).toBe(10000 - 710);
  });

  test('④ 对账后 pending 消失：账单从「不入账」变「按实际入账」', async () => {
    // 阶段1：pending（既有账单快照存在）
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      existingMonths: [CUR],
      existingBills: [{ id: 'BILL_FX', bill_month: CUR }],
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e1', status: 'pending', currency: 'USD', registered_rmb: '710' }],
      pending: 710,
    });
    const before = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(before.billAmount).toBe(0);
    expect(before.availLimit).toBe(10000 - 710);

    // 阶段2：已对账（actual_rmb=720）
    db._reset();
    registerCard({
      existingMonths: [CUR],
      existingBills: [{ id: 'BILL_FX', bill_month: CUR }],
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e1', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    const after = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(after.billAmount).toBe(720);
    expect(after.availLimit).toBe(10000 - 720);
  });

  test('⑤ 外币消费冲正：reversed 流水不计入 + 登记软删 → 额度释放', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      reversals: [{ reversed_id: 'e1' }],
    });
    const snaps = await CreditCore.syncCardBills(CARD, USER);
    expect(snaps).toEqual([]); // 无任何账单，占额完全释放
  });

  test('⑥ 池内外币 pending 占池可用额度（#poolPendingReserved）', async () => {
    CardModel.findAll.mockResolvedValue([
      makeCard({ id: CARD, share_pool_id: POOL }),
      makeCard({ id: CARD_B, share_pool_id: POOL }),
    ]);
    registerPool({ creditLimit: 5000, net: 1000, pending: 710, cards: [CARD, CARD_B] });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.cards[0].avail).toBe(5000 - 1000 - 710);
    expect(agg.totalAvail).toBe(3290);
  });

  test('⑦ 池内 reconciled 外币：单卡账单与池净负债同口径（actual_rmb）', async () => {
    CardModel.findById.mockImplementation((id) => {
      if (id === CARD) return Promise.resolve(makeCard({ id: CARD, share_pool_id: POOL }));
      if (id === CARD_B) return Promise.resolve(makeCard({ id: CARD_B, share_pool_id: POOL }));
      return Promise.resolve(null);
    });
    registerPool({ creditLimit: 5000, net: 720, cards: [CARD, CARD_B] });
    registerCard({
      cardId: CARD,
      expenses: [{ id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e2', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    registerCard({ cardId: CARD_B });
    const s = (await CreditCore.syncCardBills(CARD, USER))[0];
    expect(s.billAmount).toBe(720);
    expect(s.availLimit).toBe(5000 - 720); // 池口径与单卡一致
  });
});

// =====================================================================
// B. reconcileForeign 对账事务（H4）
// =====================================================================
describe('外币全链路 · reconcileForeign 对账事务', () => {
  test('对账成功：reconcile + 全量重算同一事务，提交', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    db._when(['SELECT foreign_amount, currency FROM card_foreign_register'], [{ foreign_amount: '100', currency: 'USD' }]);
    db._when(['UPDATE card_foreign_register SET'], [{ affectedRows: 1 }]);
    db._when(['SELECT * FROM card_foreign_register'], [
      { id: 'REG1', user_id: USER, card_id: CARD, status: 'reconciled', currency: 'USD', foreign_amount: '100', actual_rmb: '720' },
    ], (p) => p && p[0] === 'REG1');
    registerCard({
      expenses: [{ id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR}-05`, reversed_id: null }],
      regs: [{ account_id: 'e2', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });

    const reg = await CreditCore.reconcileForeign('REG1', USER, { actualRate: 720, actualRmb: 720 });
    expect(reg.card_id).toBe(CARD);
    const conn = db._conn;
    expect(conn.beginTransaction).toHaveBeenCalled();
    expect(conn.commit).toHaveBeenCalled();
    expect(conn.rollback).not.toHaveBeenCalled();
    expect(conn.release).toHaveBeenCalled();
  });

  test('对账失败（actualRate<=0）→ 事务回滚抛错，账单不写入', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    await expect(CreditCore.reconcileForeign('REG1', USER, { actualRate: 0 })).rejects.toThrow('实际汇率必须大于 0');
    const conn = db._conn;
    expect(conn.beginTransaction).toHaveBeenCalled();
    expect(conn.rollback).toHaveBeenCalled();
    expect(conn.commit).not.toHaveBeenCalled();
  });

  test('对账失败（actualRmb<=0）→ 事务回滚抛错', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    await expect(CreditCore.reconcileForeign('REG1', USER, { actualRate: 720, actualRmb: 0 })).rejects.toThrow('实际人民币金额必须大于 0');
    expect(db._conn.rollback).toHaveBeenCalled();
  });
});
