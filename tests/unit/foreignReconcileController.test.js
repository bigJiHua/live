/**
 * 外币对账/删除 controller 全链路测试
 * =====================================================================
 * 覆盖：
 *   - foreignReconcile：成功、参数校验（actualRate<=0 / actualRmb<=0）、模型抛错
 *   - foreignDelete：成功（删除登记 + 重算 + 提交）、登记不存在回滚
 *
 * 与 foreignRegisterController.test.js 合并构成外币 API 全链路：
 *   foreignHistory（查历史流水）→ foreignRegister（补登记）→ foreignReconcile（对账）→ foreignDelete（删除）
 *
 * 运行：npm run test:unit -- tests/unit/foreignReconcileController.test.js
 */
jest.mock('../../src/common/config/db', () => {
  const { createMockDb } = require('./helpers/creditMockDb');
  return createMockDb();
});
jest.mock('../../src/modules/card/model/foreignRegister', () => ({
  findById: jest.fn(),
  delete: jest.fn(),
}));
jest.mock('../../src/modules/card/core/CreditCore', () => ({
  reconcileForeign: jest.fn(),
  syncCardBills: jest.fn(),
}));

const db = require('../../src/common/config/db');
const ForeignRegister = require('../../src/modules/card/model/foreignRegister');
const CreditCore = require('../../src/modules/card/core/CreditCore');
const controller = require('../../src/modules/card/controller/billController');

const USER = 'U-001';
const CARD = 'CARD_FX';
const REG = 'REG_FX';

function mockRes() {
  return {
    body: null,
    sayMessage: null,
    sayCode: null,
    statusCode: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; },
    say(message, code = 500) { this.sayMessage = message; this.sayCode = code; return this; },
  };
}

function mockReq(overrides = {}) {
  return {
    userId: USER,
    ip: '127.0.0.1',
    params: { id: REG },
    body: { data: { actualRate: 720, actualRmb: 720 } },
    query: {},
    ...overrides,
  };
}

beforeEach(() => {
  db._reset();
  ForeignRegister.findById.mockReset();
  ForeignRegister.delete.mockReset();
  CreditCore.reconcileForeign.mockReset();
  CreditCore.syncCardBills.mockReset();
});

describe('billController.foreignReconcile（外币对账）', () => {
  test('成功：录入实际汇率/人民币，reconcile + 重算，返回 200', async () => {
    CreditCore.reconcileForeign.mockResolvedValue({ id: REG, card_id: CARD, status: 'reconciled', actual_rmb: '720' });
    const res = mockRes();
    await controller.foreignReconcile(mockReq(), res);

    expect(CreditCore.reconcileForeign).toHaveBeenCalledWith(REG, USER, { actualRate: 720, actualRmb: 720, settleDate: undefined, remark: undefined });
    expect(res.body.status).toBe(200);
    expect(res.body.message).toContain('对账成功');
  });

  test('actualRate<=0 → 400（P1-5 防把外币消费归零）', async () => {
    const res = mockRes();
    await controller.foreignReconcile(mockReq({ body: { data: { actualRate: 0 } } }), res);
    expect(CreditCore.reconcileForeign).not.toHaveBeenCalled();
    expect(res.sayCode).toBe(400);
    expect(res.sayMessage).toContain('实际汇率必须大于 0');
  });

  test('actualRmb<=0 → 400（P1-5 防归零）', async () => {
    const res = mockRes();
    await controller.foreignReconcile(mockReq({ body: { data: { actualRate: 720, actualRmb: -1 } } }), res);
    expect(res.sayCode).toBe(400);
    expect(res.sayMessage).toContain('实际人民币金额必须大于 0');
  });

  test('模型抛错（登记不存在）→ 500 + 错误信息', async () => {
    CreditCore.reconcileForeign.mockRejectedValue(new Error('外币登记记录不存在'));
    const res = mockRes();
    await controller.foreignReconcile(mockReq(), res);
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('登记记录不存在');
  });
});

describe('billController.foreignDelete（删除外币登记）', () => {
  test('成功：删除登记 + 重算对应卡 + 提交，返回 200', async () => {
    ForeignRegister.findById.mockResolvedValue({ id: REG, card_id: CARD });
    ForeignRegister.delete.mockResolvedValue(true);
    CreditCore.syncCardBills.mockResolvedValue([]);
    const res = mockRes();
    await controller.foreignDelete(mockReq(), res);

    expect(ForeignRegister.delete).toHaveBeenCalledWith(REG, USER, db._conn);
    expect(CreditCore.syncCardBills).toHaveBeenCalledWith(CARD, USER, db._conn);
    const conn = db._conn;
    expect(conn.commit).toHaveBeenCalled();
    expect(conn.rollback).not.toHaveBeenCalled();
    expect(conn.release).toHaveBeenCalled();
    expect(res.body.message).toContain('删除成功');
  });

  test('登记不存在 → 回滚 + 500', async () => {
    ForeignRegister.findById.mockResolvedValue(null);
    const res = mockRes();
    await controller.foreignDelete(mockReq(), res);
    expect(db._conn.rollback).toHaveBeenCalled();
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('不存在');
  });
});
