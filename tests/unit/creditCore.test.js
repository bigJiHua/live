/**
 * CreditCore —— 信用卡核心计算层重构验收测试
 * =====================================================================
 * 覆盖目标（对齐《信用卡系统机制分析.md》§5 全部旧口径问题 + Phase2 扩展点）：
 *   A. 旧 syncFromRepay 参数错位 no-op         → syncCardBills 全量重算幂等
 *   B. 还款 avail 不含临时额度                  → avail = credit + temp − net
 *   C. used_limit 三套口径互斥                 → spent 不被还款减；avail 由净负债派生
 *   D. 溢缴款一级建模                          → overflow = max(0, −net)
 *   外币 pending/reconciled 入账口径            → pending 占额不入账 / reconciled 按 actual_rmb
 *   共享池：池内卡快照一致、aggregate 不重复累加
 *   逾期：还款日日期级比较（当天不算逾期）
 *
 * 运行：npm run test:unit -- tests/unit/creditCore.test.js
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
const CardBill = require('../../src/modules/card/model/bill');

const USER = 'U-001';
const CARD_A = 'CARD_A';
const CARD_B = 'CARD_B';
const CARD_C = 'CARD_C';
const POOL = 'POOL_1';

function makeCard(overrides = {}) {
  return {
    id: CARD_A,
    user_id: USER,
    card_type: 'credit',
    bank_id: 'BK01',
    alias: '测试卡',
    credit_limit: '10000',
    temp_limit: '0',
    points_rate: '1',
    bill_day: 15,
    repay_day: 25,
    share_pool_id: null,
    ...overrides,
  };
}

/** 返回 n 个月前的 'YYYY-MM'（保证测试与当前日期无关） */
function monthsAgo(n) {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
const CUR_MONTH = monthsAgo(0);

/**
 * 注册某卡的账本路由（消费/冲正/还款/登记/pending 占用/既有账单）
 */
function registerCard({
  cardId = CARD_A,
  expenses = [],
  reversals = [],
  repays = [],
  existingMonths = [],
  regs = [],
  pending = 0,
  existingBills = null,
}) {
  const byCard = (p) => p && p[0] === cardId;
  db._when(
    ['SELECT id, amount, currency, exchange_rate, trans_date, reversed_id'],
    expenses,
    byCard
  );
  db._when(['SELECT reversed_id FROM account'], reversals, byCard);
  db._when(['SELECT * FROM card_foreign_register'], regs, byCard);
  db._when(['SELECT r.repay_amount'], repays, byCard);
  db._when(
    ['SELECT bill_month FROM card_bill'],
    existingMonths.map((m) => ({ bill_month: m })),
    byCard
  );
  db._when(
    ['AS total', 'FROM card_foreign_register', 'WHERE card_id'],
    [{ total: String(pending) }],
    byCard
  );
  db._when(['SELECT * FROM card_bill', 'bill_month = ?'], existingBills || [], byCard);
}

/**
 * 注册共享池路由（池额度 / 池净负债 / 池 pending 占用 / 池内卡列表 / CreditPool.findById）
 */
function registerPool({
  creditLimit = 5000,
  tempLimit = 0,
  net = 0,
  pending = 0,
  cards = [],
}) {
  db._when(['SELECT id FROM card_base WHERE share_pool_id'], cards.map((id) => ({ id })));
  db._when(
    ['SELECT total_credit_limit, total_temp_limit'],
    [{ total_credit_limit: String(creditLimit), total_temp_limit: String(tempLimit) }]
  );
  db._when(['COALESCE(SUM(t.net), 0) AS net'], [{ net: String(net) }]);
  db._when(['AS total', 'FROM card_foreign_register fr'], [{ total: String(pending) }]);
  db._when(['SELECT * FROM card_credit_pool'], [
    {
      id: POOL,
      user_id: USER,
      bank_id: 'BK01',
      bank_name: '测试银行',
      total_credit_limit: String(creditLimit),
      total_temp_limit: String(tempLimit),
    },
  ]);
}

/** 账单写入路由（UPDATE/INSERT card_bill），返回值不影响逻辑 */
function registerWrites() {
  db._when(['UPDATE card_bill SET'], [[{ affectedRows: 1 }]]);
  db._when(['INSERT INTO card_bill'], [[{ affectedRows: 1, insertId: 1 }]]);
}

beforeEach(() => {
  db._reset();
  CardModel.findById.mockReset();
  CardModel.findAll.mockReset();
  // 全局兜底路由（仅当测试未显式注册对应路由时生效）：
  // SUM 聚合默认 0，外币登记默认空，既有账单默认无，账单写入默认成功
  db._fallback(['AS total'], [{ total: '0' }]);
  db._fallback(['SELECT * FROM card_foreign_register'], []);
  db._fallback(['SELECT bill_month FROM card_bill'], []);
  db._fallback(['SELECT * FROM card_bill'], []);
  db._fallback(['UPDATE card_bill SET'], [{ affectedRows: 1 }]);
  db._fallback(['INSERT INTO card_bill'], [{ affectedRows: 1, insertId: 1 }]);
});

// =====================================================================
// 1. 单卡基础计算
// =====================================================================
describe('syncCardBills 单卡基础计算', () => {
  test('无消费无还款无既有账单 → 返回空数组（不凭空造账）', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toEqual([]);
  });

  test('当月消费 2000 → 生成账单：billAmount/needRepay/used=2000，avail=8000', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [
        { id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
    });
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toHaveLength(1);
    const s = snaps[0];
    expect(s.billMonth).toBe(CUR_MONTH);
    expect(s.billAmount).toBe(2000);
    expect(s.usedLimit).toBe(2000);
    expect(s.needRepay).toBe(2000);
    expect(s.repaid).toBe(0);
    expect(s.availLimit).toBe(8000);
    expect(s.repayStatus).toBe('未还款');
    expect(s.points).toBe(2000);
  });

  test('部分还款：消费2000 还800 → needRepay=1200，avail=8800，状态=部分还款', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      repays: [{ repay_amount: '800', target_bill_month: CUR_MONTH }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.needRepay).toBe(1200);
    expect(s.repaid).toBe(800);
    expect(s.availLimit).toBe(8800);
    expect(s.repayStatus).toBe('部分还款');
  });

  test('全额还款：还款恢复可用额度 avail=10000（旧逻辑 avail 不含还款，验证 B/C 修复）', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      repays: [{ repay_amount: '2000', target_bill_month: CUR_MONTH }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.needRepay).toBe(0);
    expect(s.repaid).toBe(2000);
    expect(s.availLimit).toBe(10000);
    expect(s.repayStatus).toBe('已还清');
  });

  test('多还（溢缴）：消费2000 还3000 → overflow=1000，avail=11000 > 额度', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      repays: [{ repay_amount: '3000', target_bill_month: CUR_MONTH }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.needRepay).toBe(0);
    expect(s.overflow).toBe(1000);
    expect(s.availLimit).toBe(11000);
  });

  test('临时额度生效：temp_limit=2000，消费1000 → avail=11000（旧 executeRepay 不含 temp，验证 B 修复）', async () => {
    CardModel.findById.mockResolvedValue(makeCard({ temp_limit: '2000' }));
    registerCard({
      expenses: [{ id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.tempLimit).toBe(2000);
    expect(s.availLimit).toBe(11000);
  });

  test('冲正消费：reversed 流水不计入账单', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      reversals: [{ reversed_id: 'e1' }],
    });
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toEqual([]);
  });

  test('跨月消费：两笔不同账单月 → 生成两张账单', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    const m1 = monthsAgo(2);
    const m2 = monthsAgo(1);
    registerCard({
      expenses: [
        { id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${m1}-05`, reversed_id: null },
        { id: 'e2', amount: '500', currency: 'CNY', exchange_rate: null, trans_date: `${m2}-05`, reversed_id: null },
      ],
    });
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps.map((s) => s.billMonth)).toEqual([m1, m2]);
    expect(snaps[0].billAmount).toBe(1000);
    expect(snaps[1].billAmount).toBe(500);
  });

  test('既有账单残留月份：本期无流水 → 快照归零保留（不残留脏数据）', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    const m1 = monthsAgo(1);
    registerCard({ existingMonths: [m1], existingBills: [{ id: 'BILL_X', bill_month: m1 }] });
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toHaveLength(1);
    expect(snaps[0].billMonth).toBe(m1);
    expect(snaps[0].billAmount).toBe(0);
    expect(snaps[0].needRepay).toBe(0);
    expect(snaps[0].usedLimit).toBe(0);
    expect(snaps[0].repayStatus).toBe('已还清');
  });

  test('幂等：二次重算走 UPDATE 而非重复 INSERT，结果一致', async () => {
    const card = makeCard();
    CardModel.findById.mockResolvedValue(card);
    // 第一次：无既有账单 → INSERT
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const s1 = await CreditCore.syncCardBills(CARD_A, USER);
    expect(db.execute.mock.calls.some(([sql]) => sql.includes('INSERT INTO card_bill'))).toBe(true);

    // 第二次：模拟已有账单 → UPDATE
    db._reset();
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      existingMonths: [CUR_MONTH],
      existingBills: [{ id: 'BILL_EXIST', bill_month: CUR_MONTH }],
    });
    registerWrites();
    const s2 = await CreditCore.syncCardBills(CARD_A, USER);
    expect(db.execute.mock.calls.some(([sql]) => sql.includes('UPDATE card_bill SET'))).toBe(true);
    expect(db.execute.mock.calls.some(([sql]) => sql.includes('INSERT INTO card_bill'))).toBe(false);
    expect(s2[0].needRepay).toBe(s1[0].needRepay);
    expect(s2[0].availLimit).toBe(s1[0].availLimit);
  });

  test('逾期：还款日已过 → isOverdue=true 且 overdueDays>0（日期级比较，M1）', async () => {
    const m = monthsAgo(3); // 还款日必然在过去
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${m}-05`, reversed_id: null }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.isOverdue).toBe(true);
    expect(s.overdueDays).toBeGreaterThan(0);
  });

  test('未逾期：当前月账单还款日在未来 → isOverdue=false', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.isOverdue).toBe(false);
    expect(s.overdueDays).toBe(0);
  });
});

// =====================================================================
// 2. 外币消费与 pending 占用
// =====================================================================
describe('外币消费与 pending/reconciled 入账口径', () => {
  test('外币消费无登记 → 回退按 toCNY 折算入账（100USD@710 = 710 元）', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [
        { id: 'e1', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.billAmount).toBe(710);
    expect(s.availLimit).toBe(9290);
  });

  test('pending 外币：不入账但按登记汇率占用可用额度', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [
        { id: 'e1', amount: '100', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null },
        { id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
      regs: [{ account_id: 'e2', status: 'pending', currency: 'USD', registered_rmb: '710' }],
      pending: 710,
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.billAmount).toBe(100); // pending 不计入账单
    expect(s.availLimit).toBe(10000 - 100 - 710); // 9190，pending 占用
  });

  test('reconciled 外币：按银行实际结算人民币 actual_rmb 入账', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [
        { id: 'e1', amount: '100', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null },
        { id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
      regs: [{ account_id: 'e2', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.billAmount).toBe(820);
    expect(s.availLimit).toBe(9180);
  });
});

// =====================================================================
// 3. 共享额度池
// =====================================================================
describe('共享额度池', () => {
  test('池内双卡：A 消费 4500（池固额 5000）→ 池内所有卡快照 avail 一致 = 500', async () => {
    CardModel.findById.mockImplementation((id) => {
      if (id === CARD_A) return Promise.resolve(makeCard({ id: CARD_A, share_pool_id: POOL }));
      if (id === CARD_B) return Promise.resolve(makeCard({ id: CARD_B, share_pool_id: POOL }));
      return Promise.resolve(null);
    });
    const m1 = monthsAgo(1);
    registerPool({ creditLimit: 5000, net: 4700, cards: [CARD_A, CARD_B] });
    registerCard({
      cardId: CARD_A,
      expenses: [{ id: 'e1', amount: '4500', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerCard({ cardId: CARD_B, existingMonths: [m1] });
    registerWrites();
    const snaps = await CreditCore.syncCardBills(CARD_A, USER); // 触发池扩散
    // A 本月账单 + B 上月账单，各自 avail 都被覆盖为共享可用
    const a = snaps.find((s) => s.billMonth === CUR_MONTH);
    const b = snaps.find((s) => s.billMonth === m1);
    expect(a).toBeTruthy();
    expect(b).toBeTruthy();
    expect(a.availLimit).toBe(300); // 5000 - 4700(全池净负债)
    expect(b.availLimit).toBe(300);
    expect(a.billAmount).toBe(4500); // 单卡账单金额仍按各自消费
    expect(b.billAmount).toBe(0);
  });

  test('aggregate：池组不重复累加额度，负债=池净负债', async () => {
    CardModel.findAll.mockResolvedValue([
      makeCard({ share_pool_id: POOL }),
      makeCard({ id: CARD_B, share_pool_id: POOL }),
    ]);
    registerPool({ creditLimit: 5000, net: 4700, cards: [CARD_A, CARD_B] });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.cards).toHaveLength(1); // 一个池组
    const g = agg.cards[0];
    expect(g.shared).toBe(true);
    expect(g.creditLimit).toBe(5000); // 不累加两卡各自额度
    expect(g.debt).toBe(4700);
    expect(g.avail).toBe(300);
    expect(g.cards).toHaveLength(2);
    expect(agg.totalCreditLimit).toBe(5000);
    expect(agg.totalDebt).toBe(4700);
  });

  test('aggregate：池被删除时回退到组内卡自身额度之和（P6）', async () => {
    db._when(['SELECT * FROM card_credit_pool'], []); // 池查不到
    CardModel.findAll.mockResolvedValue([
      makeCard({ credit_limit: '5000', share_pool_id: POOL }),
      makeCard({ id: CARD_B, credit_limit: '3000', share_pool_id: POOL }),
    ]);
    db._when(['SELECT id FROM card_base WHERE share_pool_id'], [{ id: CARD_A }, { id: CARD_B }]);
    db._when(['COALESCE(SUM(t.net), 0) AS net'], [{ net: '0' }]);
    db._when(['AS total', 'FROM card_foreign_register fr'], [{ total: '0' }]);
    const agg = await CreditCore.aggregate(USER);
    expect(agg.totalCreditLimit).toBe(8000); // 5000 + 3000 回退
    expect(agg.totalAvail).toBe(8000);
  });

  test('✅ 修复验证：池内 reconciled 外币，单卡账单与池净负债同口径（均按 actual_rmb）', async () => {
    // 测试驱动修复前：#poolNetOwed 池负债 SQL 用 amount*exchange_rate/100（登记汇率 710），
    // 而单卡 #computeMonths 对 reconciled 用 actual_rmb（720）→ 快照 avail 与 billAmount 分裂 10 元。
    // 修复：#poolNetOwed 改为 LEFT JOIN card_foreign_register，reconciled 取 actual_rmb。
    CardModel.findById.mockImplementation((id) => {
      if (id === CARD_A) return Promise.resolve(makeCard({ id: CARD_A, share_pool_id: POOL }));
      if (id === CARD_B) return Promise.resolve(makeCard({ id: CARD_B, share_pool_id: POOL }));
      return Promise.resolve(null);
    });
    registerPool({ creditLimit: 5000, net: 720, cards: [CARD_A, CARD_B] }); // 净负债=actual_rmb 口径
    registerCard({
      cardId: CARD_A,
      expenses: [
        { id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
      regs: [{ account_id: 'e2', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    registerCard({ cardId: CARD_B });
    registerWrites();
    const s = (await CreditCore.syncCardBills(CARD_A, USER))[0];
    expect(s.billAmount).toBe(720); // 单卡口径：实际结算人民币
    expect(s.availLimit).toBe(5000 - 720); // 池口径已与单卡一致
  });
});

// =====================================================================
// 4. aggregate 汇总
// =====================================================================
describe('aggregate 汇总', () => {
  test('独立卡 debt = max(0, 累计净负债)，跨期溢缴不虚高（R1：不能用 Σ needRepay）', async () => {
    // 1月消费1000还300、2月消费500还900：ΣneedRepay=700 但真实负债=300
    const m1 = monthsAgo(2);
    const m2 = monthsAgo(1);
    CardModel.findAll.mockResolvedValue([makeCard()]);
    registerCard({
      cardId: CARD_A,
      expenses: [
        { id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${m1}-05`, reversed_id: null },
        { id: 'e2', amount: '500', currency: 'CNY', exchange_rate: null, trans_date: `${m2}-05`, reversed_id: null },
      ],
      repays: [
        { repay_amount: '300', target_bill_month: m1 },
        { repay_amount: '900', target_bill_month: m2 },
      ],
    });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.totalDebt).toBe(300); // 非 700
    expect(agg.totalAvail).toBe(9700);
    expect(agg.cards[0].debt).toBe(300);
  });

  test('独立卡溢缴：还款多于消费 → totalOverflow>0，avail>额度', async () => {
    CardModel.findAll.mockResolvedValue([makeCard()]);
    registerCard({
      cardId: CARD_A,
      expenses: [{ id: 'e1', amount: '1000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
      repays: [{ repay_amount: '1500', target_bill_month: CUR_MONTH }],
    });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.totalDebt).toBe(0);
    expect(agg.totalOverflow).toBe(500);
    expect(agg.totalAvail).toBe(10500);
  });

  test('共享池内 pending 外币占用池可用额度（poolPending）', async () => {
    CardModel.findAll.mockResolvedValue([
      makeCard({ share_pool_id: POOL }),
      makeCard({ id: CARD_B, share_pool_id: POOL }),
    ]);
    registerPool({ creditLimit: 5000, net: 1000, pending: 710, cards: [CARD_A, CARD_B] });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.cards[0].avail).toBe(5000 - 1000 - 710);
    expect(agg.totalAvail).toBe(3290);
  });

  test('aggregate 支持事务连接 conn（settlement 锁内校验场景）', async () => {
    CardModel.findAll.mockResolvedValue([makeCard({ id: CARD_A, credit_limit: '10000' })]);
    registerCard({
      cardId: CARD_A,
      expenses: [
        { id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
    });
    const conn = await db.getConnection();
    const agg = await CreditCore.aggregate(USER, conn);
    expect(conn.execute).toHaveBeenCalled(); // 账本读取走事务连接
    expect(agg.totalDebt).toBe(2000);
    expect(agg.totalAvail).toBe(8000);
  });

  test('混合：独立卡 + 共享池同时汇总', async () => {
    CardModel.findAll.mockResolvedValue([
      makeCard({ id: CARD_A, credit_limit: '10000' }), // 独立
      makeCard({ id: CARD_B, credit_limit: '2000', share_pool_id: POOL }),
      makeCard({ id: CARD_C, credit_limit: '3000', share_pool_id: POOL }),
    ]);
    registerCard({
      cardId: CARD_A,
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerPool({ creditLimit: 4000, net: 1000, cards: [CARD_B, CARD_C] });
    const agg = await CreditCore.aggregate(USER);
    expect(agg.totalCreditLimit).toBe(14000); // 10000 + 4000
    expect(agg.totalDebt).toBe(3000); // 2000 + 1000
    expect(agg.totalAvail).toBe(11000); // 8000 + 3000
    expect(agg.cards).toHaveLength(2); // 独立组 + 池组
  });
});

// =====================================================================
// 5. reconcileForeign 外币对账
// =====================================================================
describe('reconcileForeign 外币对账（H4：事务内重算）', () => {
  test('正常对账：事务提交 + 使用 actual_rmb 重算账单', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    db._when(
      ['SELECT foreign_amount, currency FROM card_foreign_register'],
      [{ foreign_amount: '100', currency: 'USD' }]
    );
    db._when(['UPDATE card_foreign_register SET'], [{ affectedRows: 1 }]);
    db._when(
      ['SELECT * FROM card_foreign_register'],
      [{ id: 'REG1', user_id: USER, card_id: CARD_A, status: 'reconciled', currency: 'USD', foreign_amount: '100', actual_rmb: '720' }],
      (p) => p && p[0] === 'REG1'
    );
    registerCard({
      cardId: CARD_A,
      expenses: [
        { id: 'e2', amount: '100', currency: 'USD', exchange_rate: '710', trans_date: `${CUR_MONTH}-05`, reversed_id: null },
      ],
      regs: [{ account_id: 'e2', status: 'reconciled', currency: 'USD', actual_rmb: '720' }],
    });
    registerWrites();

    const reg = await CreditCore.reconcileForeign('REG1', USER, { actualRate: 720, actualRmb: 720 });
    expect(reg.card_id).toBe(CARD_A);
    const conn = db._conn;
    expect(conn.beginTransaction).toHaveBeenCalled();
    expect(conn.commit).toHaveBeenCalled();
    expect(conn.rollback).not.toHaveBeenCalled();
    expect(conn.release).toHaveBeenCalled();
  });

  test('对账失败（汇率<=0）→ 事务回滚并抛错', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    await expect(
      CreditCore.reconcileForeign('REG1', USER, { actualRate: 0 })
    ).rejects.toThrow('实际汇率必须大于 0');
    const conn = db._conn;
    expect(conn.beginTransaction).toHaveBeenCalled();
    expect(conn.rollback).toHaveBeenCalled();
    expect(conn.commit).not.toHaveBeenCalled();
  });
});

// =====================================================================
// 6. 守卫与别名
// =====================================================================
describe('守卫与别名', () => {
  test('卡不存在 → 返回 null', async () => {
    CardModel.findById.mockResolvedValue(null);
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toBeNull();
  });

  test('非信用卡 → 返回 null', async () => {
    CardModel.findById.mockResolvedValue(makeCard({ card_type: 'debit' }));
    const snaps = await CreditCore.syncCardBills(CARD_A, USER);
    expect(snaps).toBeNull();
  });

  test('syncByDate / syncByBill 与 syncCardBills 等价（幂等全量重算）', async () => {
    CardModel.findById.mockResolvedValue(makeCard());
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const r1 = await CreditCore.syncCardBills(CARD_A, USER);
    db._reset();
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const r2 = await CreditCore.syncByDate(CARD_A, USER, `${CUR_MONTH}-05`);
    db._reset();
    registerCard({
      expenses: [{ id: 'e1', amount: '2000', currency: 'CNY', exchange_rate: null, trans_date: `${CUR_MONTH}-05`, reversed_id: null }],
    });
    registerWrites();
    const r3 = await CreditCore.syncByBill(CARD_A, USER, 'BILL1');
    expect(r2[0].needRepay).toBe(r1[0].needRepay);
    expect(r2[0].availLimit).toBe(r1[0].availLimit);
    expect(r3[0].needRepay).toBe(r1[0].needRepay);
  });

  test('聚合非信用卡不参与（findAll 只返回信用卡）', async () => {
    CardModel.findAll.mockResolvedValue([]);
    const agg = await CreditCore.aggregate(USER);
    expect(agg.totalCreditLimit).toBe(0);
    expect(agg.totalDebt).toBe(0);
    expect(agg.cards).toEqual([]);
  });
});
