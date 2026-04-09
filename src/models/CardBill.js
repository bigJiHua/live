const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 卡片账单模型 - 对应数据库 card_bill 表
 *
 * 业务规则：
 * 1. 账单按月生成，每卡每月一条
 * 2. 账单周期：上月(bill_day+1) ~ 本月(bill_day)
 * 3. 消费实时更新账单金额和额度
 * 4. 还款实时冲减待还金额
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
   * 根据日期推算所属账单月（账单日之前属于上月账单）
   */
  static getBillMonthByDate(transDate, billDay) {
    const date = transDate ? new Date(transDate) : new Date();
    const day = date.getDate();
    const year = date.getFullYear();
    const month = date.getMonth() + 1;

    if (day <= billDay) {
      const lastMonth = month === 1 ? 12 : month - 1;
      const lastYear = month === 1 ? year - 1 : year;
      return `${lastYear}-${String(lastMonth).padStart(2, '0')}`;
    }

    return `${year}-${String(month).padStart(2, '0')}`;
  }

  /**
   * 根据账单月和账单日计算账单周期
   */
  static calculateBillPeriod(billMonth, billDay) {
    const [year, month] = billMonth.split('-').map(Number);
    const billEndDate = new Date(year, month - 1, billDay);
    const lastMonth = month === 1 ? 12 : month - 1;
    const lastYear = month === 1 ? year - 1 : year;
    const billStartDate = new Date(lastYear, lastMonth - 1, billDay + 1);

    return {
      billStartDate: this.formatDate(billStartDate),
      billEndDate: this.formatDate(billEndDate),
    };
  }

  /**
   * 计算还款截止日期
   */
  static calculateRepayDate(billMonth, repayDay) {
    const [year, month] = billMonth.split('-').map(Number);
    const repayDate = new Date(year, month - 1, repayDay);
    return this.formatDate(repayDate);
  }

  /**
   * 获取卡片信息（包含额度）
   */
  static async getCardInfo(cardId) {
    const [rows] = await db.execute(
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
             STR_TO_DATE(CONCAT(cb.bill_month, '-', LPAD(c.repay_day, 2, '0')), '%Y-%m-%d') as repay_date_calc,
             CASE 
               WHEN cb.need_repay > 0 AND STR_TO_DATE(CONCAT(cb.bill_month, '-', LPAD(c.repay_day, 2, '0')), '%Y-%m-%d') < CURDATE()
               THEN 1 ELSE 0 
             END as is_overdue_calc,
             CASE 
               WHEN cb.need_repay > 0 AND STR_TO_DATE(CONCAT(cb.bill_month, '-', LPAD(c.repay_day, 2, '0')), '%Y-%m-%d') < CURDATE()
               THEN DATEDIFF(CURDATE(), STR_TO_DATE(CONCAT(cb.bill_month, '-', LPAD(c.repay_day, 2, '0')), '%Y-%m-%d'))
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
   * 根据卡片ID获取最新账单
   */
  static async findLatestByCardId(cardId) {
    const query = `
      SELECT * FROM ${this.tableName}
      WHERE card_id = ? AND is_deleted = 0
      ORDER BY bill_month DESC
      LIMIT 1
    `;
    const [rows] = await db.execute(query, [cardId]);
    return rows[0] || null;
  }

  /**
   * 根据ID查找账单
   */
  static async findById(id, userId) {
    const query = `
      SELECT cb.*, c.alias as card_alias, c.last4_no as card_last4, c.currency
      FROM ${this.tableName} cb
      LEFT JOIN card_base c ON cb.card_id = c.id
      WHERE cb.id = ? AND cb.user_id = ? AND cb.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 根据卡片和月份查找账单
   */
  static async findByCardAndMonth(cardId, billMonth) {
    const query = `
      SELECT * FROM ${this.tableName}
      WHERE card_id = ? AND bill_month = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [cardId, billMonth]);
    return rows[0] || null;
  }

  /**
   * 获取或创建账单（按需创建）
   */
  static async getOrCreateBill(cardId, userId, billMonth) {
    const card = await this.getCardInfo(cardId);
    if (!card) throw new Error('卡片不存在');
    if (card.card_type !== 'credit') throw new Error('只有信用卡才需要账单');

    // 如果没传 billMonth，自动计算当前月
    const month = billMonth || this.getCurrentBillMonth(card.bill_day);

    let bill = await this.findByCardAndMonth(cardId, month);
    if (bill) return bill;

    return this.create({ userId, cardId, billMonth: month });
  }

  /**
   * 创建账单（幂等：如果存在则更新额度）
   */
  static async create({ userId, cardId, billMonth, creditLimit, tempLimit, pointsRate, remindSwitch, remindDays }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    const card = await this.getCardInfo(cardId);
    if (!card) throw new Error('卡片不存在');

    const month = billMonth || this.getCurrentBillMonth(card.bill_day);
    const existing = await this.findByCardAndMonth(cardId, month);

    // 确定最终额度：优先使用传入值，其次 card_base 值
    const finalCreditLimit = (creditLimit !== undefined && creditLimit !== null) 
      ? creditLimit 
      : (parseFloat(card.credit_limit) || 0);
    const finalTempLimit = (tempLimit !== undefined && tempLimit !== null) 
      ? tempLimit 
      : (parseFloat(card.temp_limit) || 0);

    if (existing) {
      // 账单已存在：更新额度，同时重新计算 avail_limit
      const currentUsedLimit = parseFloat(existing.used_limit) || 0;
      const newAvailLimit = finalCreditLimit + finalTempLimit - currentUsedLimit;
      await db.execute(
        `UPDATE ${this.tableName} SET 
           credit_limit = ?, temp_limit = ?, avail_limit = ?,
           update_time = ?
         WHERE id = ? AND is_deleted = 0`,
        [finalCreditLimit, finalTempLimit, newAvailLimit, now, existing.id]
      );
      // 同时更新 card_base 的额度
      await db.execute(
        `UPDATE card_base SET credit_limit = ?, temp_limit = ?, update_time = ? WHERE id = ? AND is_deleted = 0`,
        [finalCreditLimit, finalTempLimit, now, cardId]
      );
      return this.findById(existing.id, userId);
    }

    // 新建账单
    const { billStartDate, billEndDate } = this.calculateBillPeriod(month, card.bill_day);

    const finalPointsRate = (pointsRate !== undefined && pointsRate !== null) 
      ? pointsRate 
      : (parseFloat(card.points_rate) || 1);
    const finalRemindSwitch = (remindSwitch !== undefined && remindSwitch !== null) 
      ? (remindSwitch ? 1 : 0) 
      : 1;
    const finalRemindDays = (remindDays !== undefined && remindDays !== null) 
      ? remindDays 
      : 3;

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, bill_month, user_id, credit_limit, avail_limit, used_limit,
        temp_limit, bill_start_date, bill_end_date, bill_amount,
        min_repay, repaid, need_repay, points, points_rate,
        remind_switch, remind_days, update_time, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id, cardId, month, userId,
      finalCreditLimit, finalCreditLimit + finalTempLimit, 0,
      finalTempLimit, billStartDate, billEndDate, 0,
      0, 0, 0, 0, finalPointsRate,
      finalRemindSwitch, finalRemindDays, now,
    ]);

    // 同步更新 card_base 的额度
    await db.execute(
      `UPDATE card_base SET credit_limit = ?, temp_limit = ?, update_time = ? WHERE id = ? AND is_deleted = 0`,
      [finalCreditLimit, finalTempLimit, now, cardId]
    );

    return this.findById(id, userId);
  }

  /**
   * 获取当前账单月
   */
  static getCurrentBillMonth(billDay) {
    const now = new Date();
    const day = now.getDate();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    if (day <= billDay) {
      const lastMonth = month === 1 ? 12 : month - 1;
      const lastYear = month === 1 ? year - 1 : year;
      return `${lastYear}-${String(lastMonth).padStart(2, '0')}`;
    }

    return `${year}-${String(month).padStart(2, '0')}`;
  }

  /**
   * 消费实时更新账单
   */
  static async syncFromExpense(cardId, userId, amount, transDate) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return;

    const billMonth = this.getBillMonthByDate(transDate, card.bill_day);
    const bill = await this.getOrCreateBill(cardId, userId, billMonth);

    const now = String(Date.now());
    const amountNum = parseFloat(amount);
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    const newBillAmount = parseFloat(bill.bill_amount) + amountNum;
    const newUsedLimit = parseFloat(bill.used_limit) + amountNum;
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit;
    const newNeedRepay = newBillAmount - parseFloat(bill.repaid);
    const newPoints = Math.round(newUsedLimit * (card.points_rate || 1));

    await db.execute(
      `UPDATE ${this.tableName} SET
         bill_amount = ?, used_limit = ?, avail_limit = ?,
         need_repay = ?, min_repay = ?, points = ?,
         repay_status = CASE WHEN ? > 0 THEN '未还款' ELSE '已还款' END,
         update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newBillAmount.toFixed(2), newUsedLimit.toFixed(2), newAvailLimit.toFixed(2),
       newNeedRepay.toFixed(2), Math.max(0, newBillAmount * 0.1).toFixed(2), newPoints,
       newNeedRepay, now, bill.id]
    );

    return this.findById(bill.id, userId);
  }

  /**
   * 回滚消费：删除流水时恢复账单额度
   */
  static async rollbackExpense(cardId, userId, amount, transDate) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return;

    const billMonth = this.getBillMonthByDate(transDate, card.bill_day);
    const bill = await this.findByCardAndMonth(cardId, billMonth);
    if (!bill) return;

    const now = String(Date.now());
    const amountNum = parseFloat(amount);
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    // 回滚：bill_amount -= amount, used_limit -= amount, avail_limit += amount
    const newBillAmount = Math.max(0, parseFloat(bill.bill_amount) - amountNum);
    const newUsedLimit = Math.max(0, parseFloat(bill.used_limit) - amountNum);
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit;
    const newNeedRepay = Math.max(0, newBillAmount - parseFloat(bill.repaid));
    const newPoints = Math.round(newUsedLimit * (card.points_rate || 1));

    await db.execute(
      `UPDATE ${this.tableName} SET
         bill_amount = ?, used_limit = ?, avail_limit = ?,
         need_repay = ?, min_repay = ?, points = ?,
         repay_status = CASE WHEN ? > 0 THEN '未还款' ELSE '已还款' END,
         update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newBillAmount.toFixed(2), newUsedLimit.toFixed(2), newAvailLimit.toFixed(2),
       newNeedRepay.toFixed(2), Math.max(0, newBillAmount * 0.1).toFixed(2), newPoints,
       newNeedRepay, now, bill.id]
    );

    return this.findById(bill.id, userId);
  }

  /**
   * 还款实时冲减账单
   */
  static async syncFromRepay(cardId, billId, userId, repayAmount) {
    const bill = await this.findById(billId, userId);
    if (!bill) return;

    const now = String(Date.now());
    const amountNum = parseFloat(repayAmount);
    const newRepaid = parseFloat(bill.repaid) + amountNum;
    const newNeedRepay = Math.max(0, parseFloat(bill.bill_amount) - newRepaid);

    let repayStatus = '未还款';
    if (newNeedRepay <= 0) repayStatus = '已还清';
    else if (newRepaid > 0) repayStatus = '部分还款';

    await db.execute(
      `UPDATE ${this.tableName} SET
         repaid = ?, need_repay = ?, repay_status = ?, update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newRepaid.toFixed(2), newNeedRepay.toFixed(2), repayStatus, now, billId]
    );

    return this.findById(billId, userId);
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
   */
  static async delete(id, userId) {
    const now = String(Date.now());
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [now, id, userId]
    );
    return result.affectedRows > 0;
  }

  /**
   * 根据历史流水重建账单
   */
  static async rebuildBillFromAccount(cardId, userId) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return null;

    // 获取该卡片所有消费流水，按 bill_day 计算归属账单月
    const [rows] = await db.execute(
      `SELECT trans_date, amount FROM account 
       WHERE card_id = ? AND user_id = ? AND direction = 0 AND is_deleted = 0
       ORDER BY trans_date DESC`,
      [cardId, userId]
    );

    // 按账单月聚合消费金额
    const billMonthMap = {};
    for (const row of rows) {
      const billMonth = this.getBillMonthByDate(row.trans_date, card.bill_day);
      if (!billMonthMap[billMonth]) {
        billMonthMap[billMonth] = 0;
      }
      billMonthMap[billMonth] += parseFloat(row.amount) || 0;
    }

    const results = [];
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    for (const billMonth of Object.keys(billMonthMap).sort().reverse()) {
      const totalExpense = billMonthMap[billMonth];

      let bill = await this.findByCardAndMonth(cardId, billMonth);
      if (!bill) {
        bill = await this.create({ userId, cardId, billMonth });
      }

      const [repayRows] = await db.execute(
        `SELECT COALESCE(SUM(repay_amount), 0) as total FROM card_repay
         WHERE card_id = ? AND bill_month = ? AND is_deleted = 0`,
        [cardId, billMonth]
      );
      const totalRepaid = parseFloat(repayRows[0].total) || 0;

      const { billStartDate, billEndDate } = this.calculateBillPeriod(billMonth, card.bill_day);
      const usedLimit = totalExpense;
      const availLimit = creditLimit + tempLimit - usedLimit;
      const billAmount = totalExpense;
      const repaid = totalRepaid;
      const needRepay = Math.max(0, billAmount - repaid);
      const points = Math.round(usedLimit * (card.points_rate || 1));

      let repayStatus = '未还款';
      if (needRepay <= 0) repayStatus = '已还清';
      else if (repaid > 0) repayStatus = '部分还款';

      const now = String(Date.now());
      await db.execute(
        `UPDATE ${this.tableName} SET
           credit_limit = ?, avail_limit = ?, used_limit = ?, temp_limit = ?,
           bill_start_date = ?, bill_end_date = ?, bill_amount = ?,
           min_repay = ?, repaid = ?, need_repay = ?, points = ?,
           repay_status = ?, update_time = ?
         WHERE id = ? AND is_deleted = 0`,
        [creditLimit, availLimit.toFixed(2), usedLimit.toFixed(2), tempLimit,
         billStartDate, billEndDate, billAmount.toFixed(2), Math.max(0, billAmount * 0.1).toFixed(2),
         repaid.toFixed(2), needRepay.toFixed(2), points, repayStatus, now, bill.id]
      );

      results.push({ billMonth, billId: bill.id, totalExpense, totalRepaid, needRepay: needRepay.toFixed(2) });
    }

    // 确保当前月有账单（新卡无历史流水时也创建空账单）
    const currentBillMonth = this.getCurrentBillMonth(card.bill_day);
    const existingCurrentBill = await this.findByCardAndMonth(cardId, currentBillMonth);
    if (!existingCurrentBill) {
      const newBill = await this.create({ userId, cardId, billMonth: currentBillMonth });
      results.push({ billMonth: currentBillMonth, billId: newBill.id, totalExpense: 0, totalRepaid: 0, needRepay: '0.00' });
    }

    return results;
  }
}

module.exports = CardBill;
