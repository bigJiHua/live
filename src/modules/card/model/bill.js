const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const { formatCNY } = require('../../../common/utils/currency');
const dayjs = require('dayjs');
const utc = require('dayjs/plugin/utc');
const timezone = require('dayjs/plugin/timezone');
dayjs.extend(utc);
dayjs.extend(timezone);

// ===== 本地日期工具（避免 toISOString 的 UTC 偏移） =====
function toYMD(date) {
  const d = date instanceof Date ? date : new Date(date);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
// 将 'YYYY-MM-DD' 解析为本地零点的时间戳
function ymdToTime(ymd) {
  return new Date(`${ymd}T00:00:00`).getTime();
}

// 还款日 SQL 片段：账单月的下一月 + repay_day（P8：原直接用账单月导致早一个月）
// M2：repay_day 29-31 遇短月 clamp 到目标月最后一天，与 JS calculateRepayDate 口径一致，
//     避免 MySQL STR_TO_DATE('2026-04-31') 返回 NULL 导致逾期判断失效。
const REPAY_DATE_SQL = `STR_TO_DATE(CONCAT(DATE_FORMAT(DATE_ADD(CONCAT(cb.bill_month, '-01'), INTERVAL 1 MONTH), '%Y-%m'), '-', LPAD(LEAST(c.repay_day, DAY(LAST_DAY(DATE_ADD(CONCAT(cb.bill_month, '-01'), INTERVAL 1 MONTH)))) , 2, '0')), '%Y-%m-%d')`;

/**
 * 卡片账单模型 - 对应数据库 card_bill 表
 *
 * 业务规则：
 * 1. 账单按月生成，每卡每月一条
 * 2. 账单周期：账单日(bill_day)次日 ~ 次月账单日前一天
 * 3. 消费日期 <= 账单日 归属当月账单，消费日期 > 账单日 归属下月账单（新周期）
 * 4. 还款日：账单月的下一月repayDay
 * 5. 逾期状态：当前日期 > repay_date 且 need_repay > 0
 *
 * 额度来源：card_base 表的 credit_limit 和 temp_limit
 */
class CardBill {
  static tableName = 'card_bill';

  /**
   * 格式化日期为 YYYY-MM-DD
   */
  static formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * 获取账单月 YYYY-MM
   * 账单周期：账单日(bill_day)次日 ~ 次月账单日前一天
   * 消费日期 <= 账单日 属于当月账单，消费日期 > 账单日 属于下月账单（开启新周期）
   * @param {string|Date|number} transDate - 交易日期
   * @param {number} billDay - 账单日（几号），如果不传则默认为15保持兼容
   */
  static getBillMonthByDate(transDate, billDay = 15) {
    let date;
    if (transDate) {
      // 处理不同格式：日期字符串 或 时间戳（秒/毫秒）
      if (typeof transDate === 'string' && transDate.match(/^\d+$/)) {
        // 纯数字字符串，可能是时间戳
        const ts = parseInt(transDate, 10);
        if (transDate.length === 10) {
          // 秒级时间戳，转为毫秒
          date = new Date(ts * 1000);
        } else if (transDate.length === 13) {
          // 毫秒时间戳
          date = new Date(ts);
        } else {
          date = new Date(transDate);
        }
      } else {
        date = new Date(transDate);
      }
    } else {
      date = new Date();
    }

    const day = date.getDate();
    const year = date.getFullYear();
    const month = date.getMonth() + 1;

    // day < billDay 属于当月账单，day > billDay 属于下月账单（账单日当天仍属当月）
    if (day > billDay) {
      const nextMonth = month === 12 ? 1 : month + 1;
      const nextYear = month === 12 ? year + 1 : year;
      return `${nextYear}-${String(nextMonth).padStart(2, '0')}`;
    } else {
      return `${year}-${String(month).padStart(2, '0')}`;
    }
  }

  /**
   * 获取当前账单月
   * 始终返回当前月份（用户看到的是当前账期的账单）
   */
  static getCurrentBillMonth() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  /**
   * 根据账单月和账单日计算账单周期
   * 账单周期：N-1月billDay+1 ～ N月billDay
   * 注意：账单结束日是 billDay 当天（包含），开始日是上月 billDay 的次日
   */
  static calculateBillPeriod(billMonth, billDay) {
    const [year, month] = billMonth.split('-').map(Number);

    // 账单结束日期：账单月的 billDay
    const billEndDate = new Date(year, month - 1, billDay);

    // 账单开始日期：上月 billDay 的次日
    // 用 Date 自动处理溢出：先定位到上月 billDay，再加一天
    let lastMonth = month === 1 ? 12 : month - 1;
    let lastYear = month === 1 ? year - 1 : year;
    const lastBillDate = new Date(lastYear, lastMonth - 1, billDay);
    const billStartDate = new Date(lastBillDate);
    billStartDate.setDate(lastBillDate.getDate() + 1);

    return {
      billStartDate: this.formatDate(billStartDate),
      billEndDate: this.formatDate(billEndDate),
    };
  }

  /**
   * 计算还款截止日期
   * 还款日：账单月的下一月repayDay
   */
  static calculateRepayDate(billMonth, repayDay) {
    const [year, month] = billMonth.split('-').map(Number);
    // 还款日在账单月的下一个月
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    // M2 审计：repayDay 29-31 遇短月（2月/4月/6月/9月/11月）时 JS new Date 会滚入下月、
    // MySQL STR_TO_DATE 返回 NULL。统一 clamp 到目标月最后一天，保证两端口径一致。
    const lastDay = new Date(nextYear, nextMonth, 0).getDate();
    const safeRepayDay = Math.max(1, Math.min(Number(repayDay) || 0, lastDay));
    const repayDate = new Date(nextYear, nextMonth - 1, safeRepayDay);
    return this.formatDate(repayDate);
  }

  /**
   * 账单日（出账日）：账单月的 bill_day
   * 短月 clamp 与 calculateRepayDate 口径一致（bill_day 29-31 遇短月夹到月末）。
   * @param {string} billMonth YYYY-MM
   * @param {number} billDay 几号
   * @returns {string} YYYY-MM-DD
   */
  static calculateBillDate(billMonth, billDay) {
    const [year, month] = billMonth.split('-').map(Number);
    const lastDay = new Date(year, month, 0).getDate();
    const safeBillDay = Math.max(1, Math.min(Number(billDay) || 0, lastDay));
    const billDate = new Date(year, month - 1, safeBillDay);
    return this.formatDate(billDate);
  }

  /**
   * 获取卡片信息（包含额度）
   */
  static async getCardInfo(cardId, executor = db) {
    const [rows] = await executor.execute(
      `SELECT id, user_id, card_type, bill_day, repay_day, credit_limit, 
              temp_limit, points_rate, currency 
       FROM card_base WHERE id = ? AND is_deleted = 0`,
      [cardId]
    );
    return rows[0] || null;
  }

  /**
   * 获取账单列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE cb.user_id = ? AND cb.is_deleted = 0';
    const params = [userId];

    if (filters.cardId) {
      whereClause += ' AND cb.card_id = ?';
      params.push(filters.cardId);
    }

    if (filters.billMonth) {
      whereClause += ' AND cb.bill_month = ?';
      params.push(filters.billMonth);
    }

    const query = `
      SELECT cb.*, c.alias as card_alias, c.last4_no as card_last4,
             c.currency, c.repay_day,
             c.bill_day, c.annual_fee, c.fee_free_rule,
             ${REPAY_DATE_SQL} as repay_date_calc,
             CASE 
               WHEN cb.need_repay > 0 AND ${REPAY_DATE_SQL} < CURDATE()
               THEN 1 ELSE 0 
             END as is_overdue_calc,
             CASE 
               WHEN cb.need_repay > 0 AND ${REPAY_DATE_SQL} < CURDATE()
               THEN DATEDIFF(CURDATE(), ${REPAY_DATE_SQL})
               ELSE 0 
             END as overdue_days_calc
      FROM ${this.tableName} cb
      LEFT JOIN card_base c ON cb.card_id = c.id
      ${whereClause}
      ORDER BY cb.bill_month DESC, cb.update_time DESC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 【日历提醒专用】获取信用卡还款提醒（V2，新版口径）。
   * ⚠️ 不复用旧的 REPAY_DATE_SQL（避免 SQL 侧日期异常），改用 JS calculateRepayDate /
   *    calculateBillDate 计算，统一到新版 CreditCore 的周期口径。
   * 返回数据（与 todo/recurring 提醒结构兼容）：每个未还清账单产生两条事件——
   *   A. 账单日出账提醒（happen_date=账单月+bill_day，归位账单月）
   *      - status：账单日已过且 need_repay>0 判"逾期"（出账但未还），否则"待完成"
   *      - remind_time：账单日零点 - remind_days*86400000（账单日前 N 天开始闪烁）
   *      - remark：`账单月份 X（账单日提醒）`
   *   B. 还款日逾期预警（happen_date=账单月下月+repay_day，归位还款日所在月）
   *      - status：还款日已过且 need_repay>0 判"逾期"，否则"待完成"
   *      - remind_time：还款日零点 - remind_days*86400000（还款日前 N 天预警）
   *      - remark：`账单月份 X（还款日提醒）`
   * 逻辑：账单在「账单日」就应提醒用户该还款了（A 落在账单月）；若跨月仍未还清，
   *   则在「还款日」前预警即将逾期（B 天然落在还款日所在月，无需跨月搬运）。
   * @param {string} userId
   * @param {object} opts { year?, month?, scope? }
   *   过滤规则：按目标自然月归集，每个事件只落在各自所属月——
   *     - 指定 year+month：只返回 happen_date 落在该月的事件（月视图用）；
   *     - 未指定：只返回 happen_date 落在当前自然月的事件（提醒列表用）。
   * @returns {Promise<Array>}
   */
  static async getRepaymentRemindersV2(userId, opts = {}) {
    const { year, month, happenDate } = opts;
    // 用北京时间计算"今天"，避免 UTC 服务器下 toYMD(new Date()) 取错日导致逾期判断偏移
    const todayYMD = dayjs().tz('Asia/Shanghai').format('YYYY-MM-DD');
    const todayTime = ymdToTime(todayYMD);

    // 目标月：优先 happenDate / year+month，否则当前自然月
    let targetMonth;
    if (happenDate) {
      targetMonth = String(happenDate).substring(0, 7);
    } else if (year && month) {
      targetMonth = `${year}-${String(month).padStart(2, '0')}`;
    } else {
      targetMonth = todayYMD.substring(0, 7);
    }
    const monthPrefix = `${targetMonth}-`;

    // 待还>0 且开启提醒 的账单 + 卡
    const [rows] = await db.execute(
      `SELECT cb.id, cb.card_id, cb.bill_month, cb.need_repay, cb.remind_days,
              c.alias AS card_alias, c.last4_no AS card_last4, c.repay_day, c.bill_day
       FROM ${this.tableName} cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0
         AND (cb.remind_switch = 1 OR cb.remind_switch IS NULL)
         AND cb.need_repay > 0
       ORDER BY cb.bill_month DESC`,
      [userId]
    );

    const result = [];
    for (const r of rows) {
      const needRepay = parseFloat(r.need_repay) || 0;
      if (needRepay <= 0) continue;
      if (!r.bill_month) continue;
      const billDay = parseInt(r.bill_day, 10) || 0;
      const repayDay = parseInt(r.repay_day, 10) || 0;
      if (!repayDay) continue;
      let billDateYMD, repayDateYMD;
      try {
        if (billDay) billDateYMD = this.calculateBillDate(r.bill_month, billDay);
        repayDateYMD = this.calculateRepayDate(r.bill_month, repayDay);
      } catch (e) {
        continue; // 非法月份等，跳过
      }

      const remindDays = parseInt(r.remind_days, 10) || 0;
      const cardName = (r.card_alias || '').trim() || `****${r.card_last4 || ''}`;
      const content = `${cardName} 信用卡还款 ¥${formatCNY(needRepay)}`;

      // 事件 A：账单日出账提醒（归位账单月）
      if (billDateYMD && billDateYMD.startsWith(monthPrefix)) {
        const billTime = ymdToTime(billDateYMD);
        const billOverdue = billTime < todayTime; // 出账后仍未还 → 逾期
        result.push({
          id: `cardbill_billday_${r.id}`,
          source: 'card_bill',
          content,
          happen_date: billDateYMD,
          remind_time: billTime - remindDays * 86400000, // 毫秒数字（账单日前 N 天开始闪烁）
          need_remind: 1,
          priority: 1,
          status: billOverdue ? '逾期' : '待完成',
          event_type: '信用卡还款',
          remark: `账单月份 ${r.bill_month}（账单日提醒）`,
          bill_id: r.id,
          card_id: r.card_id,
          remind_days: remindDays,
        });
      }

      // 事件 B：还款日逾期预警（归位还款日所在月，天然即"跨月仍未还清"才出现）
      if (repayDateYMD.startsWith(monthPrefix)) {
        const repayTime = ymdToTime(repayDateYMD);
        const repayOverdue = repayTime < todayTime;
        result.push({
          id: `cardbill_repayday_${r.id}`,
          source: 'card_bill',
          content,
          happen_date: repayDateYMD,
          remind_time: repayTime - remindDays * 86400000, // 毫秒数字（还款日前 N 天预警）
          need_remind: 1,
          priority: 1,
          status: repayOverdue ? '逾期' : '待完成',
          event_type: '信用卡还款',
          remark: `账单月份 ${r.bill_month}（还款日提醒）`,
          bill_id: r.id,
          card_id: r.card_id,
          remind_days: remindDays,
        });
      }
    }
    return result.sort((a, b) => (a.happen_date || '').localeCompare(b.happen_date || ''));
  }

  /**
   * 根据卡片ID获取最新账单（限定 user_id，防止越权读取）
   */
  static async findLatestByCardId(cardId, userId) {
    const query = `
      SELECT * FROM ${this.tableName}
      WHERE card_id = ? AND user_id = ? AND is_deleted = 0
      ORDER BY bill_month DESC
      LIMIT 1
    `;
    const [rows] = await db.execute(query, [cardId, userId]);
    return rows[0] || null;
  }

  /**
   * 根据ID查找账单
   */
  static async findById(id, userId, executor = db) {
    const query = `
      SELECT cb.*, c.alias as card_alias, c.last4_no as card_last4, c.currency,
             c.repay_day,
             ${REPAY_DATE_SQL} as repay_date_calc,
             CASE 
               WHEN cb.need_repay > 0 AND ${REPAY_DATE_SQL} < CURDATE()
               THEN 1 ELSE 0 
             END as is_overdue_calc,
             CASE 
               WHEN cb.need_repay > 0 AND ${REPAY_DATE_SQL} < CURDATE()
               THEN DATEDIFF(CURDATE(), ${REPAY_DATE_SQL})
               ELSE 0 
             END as overdue_days_calc
      FROM ${this.tableName} cb
      LEFT JOIN card_base c ON cb.card_id = c.id
      WHERE cb.id = ? AND cb.user_id = ? AND cb.is_deleted = 0
    `;
    const [rows] = await executor.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 根据卡片和月份查找账单
   */
  static async findByCardAndMonth(cardId, billMonth, executor = db) {
    const query = `
      SELECT * FROM ${this.tableName}
      WHERE card_id = ? AND bill_month = ? AND is_deleted = 0
    `;
    const [rows] = await executor.execute(query, [cardId, billMonth]);
    return rows[0] || null;
  }

  /**
   * 获取或创建账单（按需创建）
   */
  static async getOrCreateBill(cardId, userId, billMonth, executor = db) {
    const card = await this.getCardInfo(cardId, executor);
    if (!card) throw new Error('卡片不存在');
    if (card.card_type !== 'credit') throw new Error('只有信用卡才需要账单');

    const month = billMonth || this.getCurrentBillMonth();

    let bill = await this.findByCardAndMonth(cardId, month, executor);
    if (bill) return bill;

    bill = await this.create({ userId, cardId, billMonth: month }, executor);
    return bill;
  }

  /**
   * 创建账单（H8 审计：统一由 CreditCore.syncCardBills 全量重算生成快照）。
   * ⚠️ 额度(creditLimit/tempLimit/availLimit)一律由 CreditCore 计算（唯一真相源），
   *    前端传入的额度字段被忽略；本方法不再手工拼接额度快照，避免破坏共享池/溢缴款口径。
   *    仅处理 remindSwitch/remindDays/pointsRate 元数据。
   */
  static async create({ userId, cardId, billMonth, pointsRate, remindSwitch, remindDays }, executor = db) {
    const now = String(Date.now());

    const card = await this.getCardInfo(cardId, executor);
    if (!card) throw new Error('卡片不存在');
    if (card.card_type !== 'credit') throw new Error('只有信用卡才需要账单');

    const month = billMonth || this.getCurrentBillMonth();

    // 已有账单：仅同步提醒/积分元数据，额度一律不动（交由 CreditCore 重算）
    const existing = await this.findByCardAndMonth(cardId, month, executor);
    if (existing) {
      const sets = ['update_time = ?'];
      const params = [now];
      if (remindSwitch !== undefined && remindSwitch !== null) { sets.push('remind_switch = ?'); params.push(remindSwitch ? 1 : 0); }
      if (remindDays !== undefined && remindDays !== null) { sets.push('remind_days = ?'); params.push(remindDays); }
      if (pointsRate !== undefined && pointsRate !== null) { sets.push('points_rate = ?'); params.push(pointsRate); }
      await executor.execute(
        `UPDATE ${this.tableName} SET ${sets.join(', ')} WHERE id = ?`,
        [...params, existing.id]
      );
      return this.findById(existing.id, userId, executor);
    }

    // 不存在：先由 CreditCore 全量重算（生成所有有流水月份的快照，口径统一、含共享池）
    // C8 审计：重算失败必须向上抛出，不得吞错后建"零消费、零待还"空壳——否则会把真实流水暂时显示为零负债。
    const CreditCore = require('../core/CreditCore');
    await CreditCore.syncCardBills(cardId, userId, executor);

    // 若目标月份仍无账单（该月无消费/还款流水），创建空壳快照（额度占位，后续 syncCardBills 覆盖）
    let bill = await this.findByCardAndMonth(cardId, month, executor);
    if (!bill) {
      const id = idUtils.billId();
      const { billStartDate, billEndDate } = this.calculateBillPeriod(month, card.bill_day);
      const finalCreditLimit = parseFloat(card.credit_limit) || 0;
      const finalTempLimit = parseFloat(card.temp_limit) || 0;
      const finalPointsRate = (pointsRate !== undefined && pointsRate !== null)
        ? pointsRate
        : (parseFloat(card.points_rate) || 1);
      try {
        await executor.execute(
          `INSERT INTO ${this.tableName} (
            id, card_id, bill_month, user_id, credit_limit, avail_limit, used_limit,
            temp_limit, bill_start_date, bill_end_date, bill_amount,
            min_repay, repaid, need_repay, points, points_rate,
            remind_switch, remind_days, create_time, update_time, is_deleted
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
          [id, cardId, month, userId,
           finalCreditLimit, finalCreditLimit + finalTempLimit, 0,
           finalTempLimit, billStartDate, billEndDate, 0,
           0, 0, 0, 0, finalPointsRate,
           (remindSwitch !== undefined && remindSwitch !== null) ? (remindSwitch ? 1 : 0) : 1,
           (remindDays !== undefined && remindDays !== null) ? remindDays : 3,
           now, now]
        );
        // 空壳快照也交由 CreditCore 规范化一次（共享池卡会把 avail_limit 覆盖为池口径）
        // C8：规范化失败同样抛出，不允许返回未经核算的空账单
        await CreditCore.syncCardBills(cardId, userId, executor);
      } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
          return this.findByCardAndMonth(cardId, month, executor);
        }
        throw error;
      }
    }
    return this.findByCardAndMonth(cardId, month, executor);
  }

  /**
   * 更新账单（只允许更新提醒相关字段）
   */
  static async update(id, userId, updates) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    const allowedFields = {
      remindSwitch: 'remind_switch',
      remindDays: 'remind_days',
    };

    Object.keys(updates).forEach(key => {
      if (allowedFields[key] !== undefined) {
        let value = updates[key];
        if (key === 'remindSwitch') value = value ? 1 : 0;
        fields.push(`${allowedFields[key]} = ?`);
        params.push(value);
      }
    });

    if (fields.length === 0) return this.findById(id, userId);

    fields.push('update_time = ?');
    params.push(now);
    params.push(id, userId);

    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      params
    );

    return this.findById(id, userId);
  }

  /**
   * 删除账单（软删除）
   * H8 审计：删除后必须重算——若该卡该月仍有账本流水，CreditCore 会按账本重建快照，
   * 避免「删除快照暂时隐藏负债/放大共享池可用额度」。
   */
  static async delete(id, userId) {
    const bill = await this.findById(id, userId);
    if (!bill) return false;
    const now = String(Date.now());
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [now, id, userId]
    );
    if (result.affectedRows > 0 && bill.card_id) {
      try {
        const CreditCore = require('../core/CreditCore');
        await CreditCore.syncCardBills(bill.card_id, userId);
      } catch (e) {
        console.error('[删除账单] 重算失败:', e.message);
      }
    }
    return result.affectedRows > 0;
  }


}

module.exports = CardBill;
