/**
 * repayController.mergeRepay（信报合一合并还款）控制器层测试
 * =====================================================================
 * 覆盖（针对线上发现的真实 bug）：
 *   - card_log.card_id NOT NULL，日志不得传 null（曾崩 Column 'card_id' cannot be null）
 *   - 合并还款已成功后，日志失败不得让接口返回 500（曾导致前端误判失败重复提交）
 *   - 余额不足时整笔回滚并返回 500 + 具体错误信息
 *   - 池未开启信报合一 / 池内无欠款 等守卫路径
 *
 * 运行：npm run test:unit -- tests/unit/repayMergeController.test.js
 */
jest.mock('../../src/modules/card/model/repay', () => ({
  executeMergeRepay: jest.fn(),
}));
jest.mock('../../src/modules/card/model/log', () => ({
  log: jest.fn(),
}));

const CardRepay = require('../../src/modules/card/model/repay');
const CardLog = require('../../src/modules/card/model/log');
const controller = require('../../src/modules/card/controller/repayController');

const USER = 'U-001';
const POOL = 'POOL_1';
const CARD_1 = 'CARD_1';
const CARD_2 = 'CARD_2';

function mockRes() {
  const res = {
    statusCode: null,
    body: null,
    sayMessage: null,
    sayCode: null,
    status(code) {
      res.statusCode = code;
      return this;
    },
    json(payload) {
      res.body = payload;
      return this;
    },
    say(message, code = 500) {
      res.sayMessage = message;
      res.sayCode = code;
      return this;
    },
  };
  return res;
}

function mockReq(overrides = {}) {
  return {
    userId: USER,
    ip: '127.0.0.1',
    body: { data: { poolId: POOL, repayMethod: 'balance' } },
    params: {},
    query: {},
    ...overrides,
  };
}

beforeEach(() => {
  CardRepay.executeMergeRepay.mockReset();
  CardLog.log.mockReset();
});

describe('repayController.mergeRepay', () => {
  test('成功：日志用池内首张卡 id（card_log.card_id NOT NULL），返回 200', async () => {
    CardRepay.executeMergeRepay.mockResolvedValue({
      totalAmount: 221.75,
      count: 3,
      repayIds: ['R1', 'R2', 'R3'],
      cardIds: [CARD_1, CARD_2],
    });
    CardLog.log.mockResolvedValue({});
    const res = mockRes();
    await controller.mergeRepay(mockReq(), res);

    expect(CardLog.log).toHaveBeenCalledWith(CARD_1, USER, expect.stringContaining('221.75'), '127.0.0.1');
    expect(res.statusCode).toBe(200);
    expect(res.body.status).toBe(200);
    expect(res.body.message).toContain('合并还款成功');
  });

  test('✅ 修复验证：日志失败不阻塞已成功的还款（曾返回 500 导致前端误判失败）', async () => {
    CardRepay.executeMergeRepay.mockResolvedValue({
      totalAmount: 100,
      count: 1,
      repayIds: ['R1'],
      cardIds: [CARD_1],
    });
    CardLog.log.mockRejectedValue(new Error('CardLog.create 失败'));
    const res = mockRes();
    await controller.mergeRepay(mockReq(), res);

    expect(res.statusCode).toBe(200); // 还款成功，日志失败仅告警
    expect(res.body.status).toBe(200);
  });

  test('余额不足：executeMergeRepay 抛错 → 返回 500 且带具体错误信息', async () => {
    CardRepay.executeMergeRepay.mockRejectedValue(new Error('余额不足，当前余额 17.82，需要 221.75'));
    const res = mockRes();
    await controller.mergeRepay(mockReq(), res);

    expect(CardLog.log).not.toHaveBeenCalled(); // 失败不写成功日志
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('余额不足');
  });

  test('池未开启信报合一 → 500', async () => {
    CardRepay.executeMergeRepay.mockRejectedValue(new Error('该共享池未开启信报合一，无法合并还款'));
    const res = mockRes();
    await controller.mergeRepay(mockReq(), res);
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('信报合一');
  });

  test('池内无欠款 → 500', async () => {
    CardRepay.executeMergeRepay.mockRejectedValue(new Error('共享池内无待还欠款'));
    const res = mockRes();
    await controller.mergeRepay(mockReq(), res);
    expect(res.sayCode).toBe(500);
    expect(res.sayMessage).toContain('无待还欠款');
  });

  test('缺少 poolId / repayMethod → 400', async () => {
    const res1 = mockRes();
    await controller.mergeRepay(mockReq({ body: { data: { repayMethod: 'balance' } } }), res1);
    expect(res1.sayCode).toBe(400);
    expect(res1.sayMessage).toContain('共享池ID');

    const res2 = mockRes();
    await controller.mergeRepay(mockReq({ body: { data: { poolId: POOL } } }), res2);
    expect(res2.sayCode).toBe(400);
    expect(res2.sayMessage).toContain('还款方式');
  });
});
