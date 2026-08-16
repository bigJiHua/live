/**
 * 日历提醒 V2 函数测试（信用卡还款提醒 + recurring 固定支出当前月过滤）
 * =====================================================================
 * 信用卡还款提醒 V2 的业务语义（用户反馈修复）：
 *   - 每个未还清账单产生「两条」日历事件，各自归位到所属自然月：
 *       A. 账单日出账提醒：happen_date=账单月+bill_day（归位账单月），
 *          remind_time=账单日-remind_days（账单日前 N 天开始闪烁），
 *          remark=`账单月份 X（账单日提醒）`。
 *       B. 还款日逾期预警：happen_date=账单月下月+repay_day（归位还款日所在月），
 *          remind_time=还款日-remind_days，remark=`账单月份 X（还款日提醒）`。
 *     即：账单在「账单日」就应提醒该还款了（落账单月）；若跨月仍未还清，
 *     则在「还款日」前预警即将逾期（落还款日所在月，无需跨月搬运）。
 *   - recurring 固定支出只返回当前自然月。
 *
 * 运行：npm run test:unit -- tests/unit/reminderV2.test.js
 */
jest.mock('../../src/common/config/db', () => {
  const { createMockDb } = require('./helpers/creditMockDb');
  return createMockDb();
});

const db = require('../../src/common/config/db');
const CardBill = require('../../src/modules/card/model/bill');
const Recurring = require('../../src/modules/recurring/model');

// 用 spyOn 局部替换 findAll / toCalendarEvent（保留真实 getUpcomingRemindersCurrentMonth）
jest.spyOn(Recurring, 'findAll').mockImplementation(() => Promise.resolve([]));
jest.spyOn(Recurring, 'toCalendarEvent').mockImplementation(() => null);

const USER = 'U-001';

function currentMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
function nextMonth() {
  const d = new Date();
  d.setMonth(d.getMonth() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
/** 账单月 + bill_day → YYYY-MM-DD（含短月 clamp） */
function expectedBill(billMonth, billDay) {
  return CardBill.calculateBillDate(billMonth, billDay);
}
/** 当前月（账单月）下月的还款日，repay_day 给定 → YYYY-MM-DD */
function expectedRepay(billMonth, repayDay) {
  return CardBill.calculateRepayDate(billMonth, repayDay);
}

beforeEach(() => {
  db._reset();
  Recurring.findAll.mockClear();
  Recurring.toCalendarEvent.mockClear();
  Recurring.findAll.mockImplementation(() => Promise.resolve([]));
  Recurring.toCalendarEvent.mockImplementation(() => null);
});

// =====================================================================
// 1. 信用卡还款提醒 V2（账单日 + 还款日双事件）
// =====================================================================
describe('CardBill.getRepaymentRemindersV2（信用卡还款提醒）', () => {
  const curM = currentMonth();
  const nextM = nextMonth();
  // 目标月（还款日所属月）= 下月（账单月下月）
  const repayTargetM = nextM;

  function regBill({ billMonth = curM, billDay = 18, repayDay = 6, remindDays = 3, needRepay = 100 }) {
    db._when(
      ['SELECT cb.id, cb.card_id, cb.bill_month, cb.need_repay, cb.remind_days'],
      [{
        id: 'BILL_1', card_id: 'CARD_1', bill_month: billMonth, need_repay: needRepay,
        remind_days: remindDays, card_alias: '南航联名卡套卡', card_last4: 'IW4MA',
        bill_day: billDay, repay_day: repayDay,
      }]
    );
  }

  test('账单日提醒落在账单月、还款日提醒落在还款日所在月（双事件）', async () => {
    regBill({ billMonth: curM, billDay: 18, repayDay: 6, remindDays: 3 });
    // 查账单月（当前月）→ 期望只有「账单日提醒」事件
    const billRows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(curM, 10), month: parseInt(curM.slice(5), 10) });
    expect(billRows).toHaveLength(1);
    const a = billRows[0];
    expect(a.happen_date).toBe(expectedBill(curM, 18)); // 账单月18号
    expect(a.remark).toBe(`账单月份 ${curM}（账单日提醒）`);
    expect(a.id).toBe('cardbill_billday_BILL_1');
    // remind_time = 账单日零点 - 3天
    const billTime = new Date(`${a.happen_date}T00:00:00`).getTime();
    expect(Number(a.remind_time)).toBe(billTime - 3 * 86400000);

    // 查还款日所属月（下月）→ 期望只有「还款日提醒」事件
    const repayRows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(repayTargetM, 10), month: parseInt(repayTargetM.slice(5), 10) });
    expect(repayRows).toHaveLength(1);
    const b = repayRows[0];
    expect(b.happen_date).toBe(expectedRepay(curM, 6)); // 账单月下月6号
    expect(b.remark).toBe(`账单月份 ${curM}（还款日提醒）`);
    expect(b.id).toBe('cardbill_repayday_BILL_1');
    const repayTime = new Date(`${b.happen_date}T00:00:00`).getTime();
    expect(Number(b.remind_time)).toBe(repayTime - 3 * 86400000);
  });

  test('账单日提醒：happen_date=账单月+bill_day；remind_time=账单日-remind_days 天', async () => {
    regBill({ billMonth: curM, billDay: 18, remindDays: 3 });
    const rows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(curM, 10), month: parseInt(curM.slice(5), 10) });
    expect(rows).toHaveLength(1);
    const r = rows[0];
    expect(r.happen_date).toBe(expectedBill(curM, 18));
    expect(r.happen_date.startsWith(`${curM}-`)).toBe(true); // 账单日在账单月
    const billTime = new Date(`${r.happen_date}T00:00:00`).getTime();
    expect(Number(r.remind_time)).toBe(billTime - 3 * 86400000);
    expect(r.content).toContain('南航联名卡套卡');
    expect(r.content).toContain('¥100.00');
    expect(r.source).toBe('card_bill');
  });

  test('还款日提醒：happen_date=账单月下月+repay_day；remind_time=还款日-remind_days 天', async () => {
    regBill({ billMonth: curM, repayDay: 6, remindDays: 3 });
    const rows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(repayTargetM, 10), month: parseInt(repayTargetM.slice(5), 10) });
    expect(rows).toHaveLength(1);
    const r = rows[0];
    expect(r.happen_date).toBe(expectedRepay(curM, 6));
    expect(r.happen_date.startsWith(`${repayTargetM}-`)).toBe(true); // 还款日在目标月
    const repayTime = new Date(`${r.happen_date}T00:00:00`).getTime();
    expect(Number(r.remind_time)).toBe(repayTime - 3 * 86400000);
    expect(r.content).toContain('南航联名卡套卡');
    expect(r.content).toContain('¥100.00');
    expect(r.source).toBe('card_bill');
  });

  test('账单月视图不含还款日事件（9月还款不推到8月）', async () => {
    // 账单月=当前月，账单日/还款日都在各自月；查询当前月（账单月）→ 只有账单日事件，无还款日事件
    regBill({ billMonth: curM, billDay: 18, repayDay: 6 });
    const rows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(curM, 10), month: parseInt(curM.slice(5), 10) });
    expect(rows).toHaveLength(1);
    expect(rows[0].remark).toContain('（账单日提醒）');
  });

  test('remind_days=0 → 账单日/还款日 remind_time 均=当天', async () => {
    regBill({ billMonth: curM, billDay: 18, repayDay: 6, remindDays: 0 });
    const billRows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(curM, 10), month: parseInt(curM.slice(5), 10) });
    const a = billRows[0];
    const billTime = new Date(`${a.happen_date}T00:00:00`).getTime();
    expect(Number(a.remind_time)).toBe(billTime);
    const repayRows = await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(repayTargetM, 10), month: parseInt(repayTargetM.slice(5), 10) });
    const b = repayRows[0];
    const repayTime = new Date(`${b.happen_date}T00:00:00`).getTime();
    expect(Number(b.remind_time)).toBe(repayTime);
  });

  test('repay_day 为 31 遇短月 → clamp 到目标月最后一天（与新版核心一致）', async () => {
    regBill({ billMonth: curM, billDay: 18, repayDay: 31, remindDays: 3 });
    const r = (await CardBill.getRepaymentRemindersV2(USER, { year: parseInt(repayTargetM, 10), month: parseInt(repayTargetM.slice(5), 10) }))[0];
    expect(r.happen_date).toBe(expectedRepay(curM, 31)); // calculateRepayDate 内部 clamp
  });
});

// =====================================================================
// 2. recurring 固定支出当前月过滤
// =====================================================================
describe('Recurring.getUpcomingRemindersCurrentMonth（固定支出当前月过滤）', () => {
  const curM = currentMonth();
  const nextM = nextMonth();

  test('只返回当前自然月内的固定支出（9月数据不推8月）', async () => {
    Recurring.findAll.mockImplementation((userId, { month }) => {
      return Promise.resolve(month === curM ? [{ id: 'CUR', name: '本月话费' }] : [{ id: 'NEXT', name: '下月话费' }]);
    });
    Recurring.toCalendarEvent.mockImplementation((row, month) => {
      return {
        id: `recurring_${row.id}_${month}`,
        source: 'recurring',
        content: row.name,
        happen_date: `${month}-03`,
        month_status: 'pending',
      };
    });

    const rows = await Recurring.getUpcomingRemindersCurrentMonth(USER);
    expect(rows).toHaveLength(1); // 只有当前月一条
    expect(rows[0].content).toBe('本月话费'); // 9月数据被排除
    expect(rows[0].happen_date).toBe(`${curM}-03`);
    // findAll 只被调用一次（仅当前月）
    expect(Recurring.findAll).toHaveBeenCalledTimes(1);
  });

  test('已完成/已跳过的记录不返回', async () => {
    Recurring.findAll.mockResolvedValue([
      { id: 'DONE', name: '已完成', status: 'done' },
      { id: 'SKIP', name: '已跳过', status: 'skipped' },
      { id: 'PEND', name: '待完成', status: 'pending' },
    ]);
    Recurring.toCalendarEvent.mockImplementation((row, month) => ({
      id: `recurring_${row.id}_${month}`,
      content: row.name,
      happen_date: `${curM}-03`,
      month_status: row.status,
    }));
    const rows = await Recurring.getUpcomingRemindersCurrentMonth(USER);
    expect(rows).toHaveLength(1);
    expect(rows[0].content).toBe('待完成');
  });
});
