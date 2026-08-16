/**
 * billController 外币消费历史流水/补登记 controller 层测试
 * =====================================================================
 * 覆盖（痛点4 补充：找出历史外币消费流水做对账）：
 *   - foreignHistory：扫描账本返回历史外币消费（含未登记），参数透传
 *   - foreignRegister：补登记成功（事务提交 + 重算）；以及
 *     流水不存在 / 非信用卡 / 非外币 / 缺汇率 四类错误路径（事务回滚）
 *
 * 运行：npm run test:unit -- tests/unit/foreignRegisterController.test.js
 */
jest.mock('../../src/common/config/db', () => {
  const { createMockDb } = require('./helpers/creditMockDb');
  return createMockDb();
});
jest.mock('../../src/modules/card/model/foreignRegister', () => ({
  ensurePending: jest.fn(),
  findById: jest.fn(),
  findForeignExpenseHistory: jest.fn(),
}));
jest.mock('../../src/modules/card/core/CreditCore', () => ({
  syncCardBills: jest.fn(),
}));

const db = require('../../src/common/config/db');
const ForeignRegister = require('../../src/modules/card/model/foreignRegister');
const CreditCore = require('../../src/modules/card/core/CreditCore');
const controller = require('../../src/modules/card/controller/billController');

const USER = 'U-001';
const CARD = 'CARD_X';
const ACCOUNT = 'ACC_1';

function mockRes() {
  const res = {
    body: null,
    sayMessage: null,
    sayCode: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; },
    say(message, code = 500) { this.sayMessage = message; this.sayCode = code; return this; },
  };
  return res;
}

function mockReq(overrides = {}) {
  return {
    userId: USER,
    ip: '127.0.0.1',
    body: { data: { accountId: ACCOUNT } },
    query: {},
    params: {},
    ...overrides,
  };
}

beforeEach(() => {
  db._reset();
  ForeignRegister.findForeignExpenseHistory.mockReset();
  ForeignRegister.ensurePending.mockReset();
  ForeignRegister.findById.mockReset();
  CreditCore.syncCardBills.mockReset();
});

describe('billController.foreignHistory（历史外币流水）', () => {
  test('返回账本中外币消费流水（含未登记），参数透传', async () => {
    ForeignRegister.findForeignExpenseHistory.mockResolvedValue([
      { account_id: ACCOUNT, card_id: CARD, currency: 'USD', amount: '100', exchange_rate: '710', trans_date: '2026-08-01', reg_status: null },
      { account_id: 'ACC_2', card_id: CARD, currency: 'HKD', amount: '800', exchange_rate: '92', trans_date: '2026-08-02', reg_status: 'pending' },
    ]);
    const res = mockRes();
    const req = mockReq({ query: { cardId: CARD, startDate: '2026-08-01', endDate: '2026-08-31' } });
    await controller.foreignHistory(req, res);

    expect(ForeignRegister.findForeignExpenseHistory).toHaveBeenCalledWith(USER, {
      cardId: CARD, startDate: '2026-08-01', endDate: '2026-08-31',
    });
    expect(res.body.status).toBe(200);
    expect(res.body.data).toHaveLength(2);
    expect(res.body.data[0].reg_status).toBeNull(); // 未登记也返回
  });
});

describe('billController.foreignRegister（补登记历史外币流水）', () => {
  const creditRow = {
    id: ACCOUNT, card_id: CARD, amount: '100', currency: 'USD', exchange_rate: '710', card_type: 'credit',
  };

  function registerQuery() {
    db._when(['SELECT a.id, a.card_id, a.amount'], [creditRow]);
  }

  test('成功：补登记 pending + 事务内重算 + 提交', async () => {
    registerQuery();
    ForeignRegister.ensurePending.mockResolvedValue('REG_1');
    ForeignRegister.findById.mockResolvedValue({ id: 'REG_1', card_id: CARD, status: 'pending' });
    CreditCore.syncCardBills.mockResolvedValue([]);

    const res = mockRes();
    await controller.foreignRegister(mockReq(), res);

    expect(ForeignRegister.ensurePending).toHaveBeenCalledWith({
      userId: USER, cardId: CARD, accountId: ACCOUNT, currency: 'USD', foreignAmount: '100', registeredRate: '710',
    }, db._conn);
    expect(CreditCore.syncCardBills).toHaveBeenCalledWith(CARD, USER, db._conn);
    const conn = db._conn;
    expect(conn.commit).toHaveBeenCalled();
    expect(conn.rollback).not.toHaveBeenCalled();
    expect(conn.release).toHaveBeenCalled();
    expect(res.body.status).toBe(200);
    expect(res.body.message).toContain('已登记为待对账');
  });

  test('流水不存在 → 回滚 + 500', async () => {
    db._when(['SELECT a.id, a.card_id, a.amount'], []);
    const res = mockRes();
    await controller.foreignRegister(mockReq(), res);
    expect(db._conn.rollback).toHaveBeenCalled();
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('流水不存在');
  });

  test('非信用卡流水 → 回滚 + 500', async () => {
    db._when(['SELECT a.id, a.card_id, a.amount'], [{ ...creditRow, card_type: 'debit' }]);
    const res = mockRes();
    await controller.foreignRegister(mockReq(), res);
    expect(db._conn.rollback).toHaveBeenCalled();
    expect(res.sayMessage).toContain('不是信用卡消费');
  });

  test('人民币流水 → 回滚 + 500（无需登记）', async () => {
    db._when(['SELECT a.id, a.card_id, a.amount'], [{ ...creditRow, currency: 'CNY' }]);
    const res = mockRes();
    await controller.foreignRegister(mockReq(), res);
    expect(db._conn.rollback).toHaveBeenCalled();
    expect(res.sayMessage).toContain('不是外币消费');
  });

  test('缺少登记汇率 → 回滚 + 500', async () => {
    db._when(['SELECT a.id, a.card_id, a.amount'], [{ ...creditRow, exchange_rate: '0' }]);
    const res = mockRes();
    await controller.foreignRegister(mockReq(), res);
    expect(db._conn.rollback).toHaveBeenCalled();
    expect(res.sayMessage).toContain('缺少登记汇率');
  });

  test('缺 accountId → 400', async () => {
    const res = mockRes();
    await controller.foreignRegister(mockReq({ body: { data: {} } }), res);
    expect(res.sayCode).toBe(400);
    expect(res.sayMessage).toContain('流水ID');
  });
});
