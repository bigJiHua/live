const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const AccountSettlement = require('../../account/service/settlement');

/**
 * 卡片账单模型 - 对应数据库 card_bill 表
 *
 * 业务规则：
 * 1. 账单按月生成，每卡每月一条
 * 2. 账单周期：账单日(bill_day)次日 ~ 次月账单日前一天
 * 3. 消费日期 < 账单日 归属当月账单，消费日期 >= 账单日 归属下月账单（新周期）
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
   * 消费日期 < 账单日 属于当月账单，消费日期 >= 账单日 属于下月账单（开启新周期）
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
   * 账单周期：N-1月billDay ～ N月billDay-1
   */
  static calculateBillPeriod(billMonth, billDay) {
    const [year, month] = billMonth.split('-').map(Number);
    
    // 账单结束日期：账单月当天
    const billEndDate = new Date(year, month - 1, billDay);
    
    // 账单开始日期：上月上一天
    let lastMonth = month === 1 ? 12 : month - 1;
    let lastYear = month === 1 ? year - 1 : year;
    const billStartDate = new Date(lastYear, lastMonth - 1, billDay + 1);

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
    const repayDate = new Date(nextYear, nextMonth - 1, repayDay);
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
             c.bill_day, c.annual_fee, c.fee_free_rule,
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

    const month = billMonth || this.getCurrentBillMonth();

    let bill = await this.findByCardAndMonth(cardId, month);
    if (bill) return bill;

    bill = await this.create({ userId, cardId, billMonth: month });
    return bill;
  }

  /**
   * 创建账单（幂等：如果存在则更新额度）
   */
  static async create({ userId, cardId, billMonth, creditLimit, tempLimit, pointsRate, remindSwitch, remindDays }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    const card = await this.getCardInfo(cardId);
    if (!card) throw new Error('卡片不存在');

    const month = billMonth || this.getCurrentBillMonth();
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

    // 新建账单（幂等处理，防止并发重复插入）
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

    try {
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
    } catch (error) {
      // 如果是重复键错误，说明有被软删除的旧账单，先恢复它
      if (error.code === 'ER_DUP_ENTRY') {
        // 先检查是否有被软删除的账单
        const [deletedRows] = await db.execute(
          'SELECT id FROM card_bill WHERE card_id = ? AND bill_month = ? AND is_deleted = 1',
          [cardId, month]
        );
        if (deletedRows.length > 0) {
          // 恢复被软删除的账单
          await db.execute(
            'UPDATE card_bill SET is_deleted = 0, update_time = ? WHERE id = ?',
            [now, deletedRows[0].id]
          );
          return this.findById(deletedRows[0].id, userId);
        }
        // 如果没有软删除记录，查询现有记录
        return this.findByCardAndMonth(cardId, month);
      }
      throw error;
    }

    // 同步更新 card_base 的额度
    await db.execute(
      `UPDATE card_base SET credit_limit = ?, temp_limit = ?, update_time = ? WHERE id = ? AND is_deleted = 0`,
      [finalCreditLimit, finalTempLimit, now, cardId]
    );

    return this.findById(id, userId);
  }

  /**
   * 消费实时更新账单
   */
  static async syncFromExpense(cardId, userId, amount, transDate) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return;

    const billMonth = this.getBillMonthByDate(transDate, card.bill_day);
    const bill = await this.getOrCreateBill(cardId, userId, billMonth);
    if (!bill) return;

    const now = String(Date.now());
    const amountNum = parseFloat(amount);
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    const newBillAmount = (parseFloat(bill.bill_amount) || 0) + amountNum;
    const newUsedLimit = (parseFloat(bill.used_limit) || 0) + amountNum;
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit;
    const newNeedRepay = newBillAmount - (parseFloat(bill.repaid) || 0);
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

    // 计算撤销消费后的状态
    const newBillAmount = Math.max(0, (parseFloat(bill.bill_amount) || 0) - amountNum);
    const newUsedLimit = Math.max(0, (parseFloat(bill.used_limit) || 0) - amountNum);
    const newNeedRepay = Math.max(0, newBillAmount - (parseFloat(bill.repaid) || 0));
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit;
    const newPoints = Math.round(newUsedLimit * (card.points_rate || 1));

    await db.execute(
      `UPDATE ${this.tableName} SET
         bill_amount = ?, used_limit = ?, avail_limit = ?,
         need_repay = ?, min_repay = ?, points = ?,
         repay_status = ?, update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newBillAmount.toFixed(2), newUsedLimit.toFixed(2), newAvailLimit.toFixed(2),
       newNeedRepay.toFixed(2), Math.max(0, newBillAmount * 0.1).toFixed(2), newPoints,
       newNeedRepay <= 0 ? '已还款' : ((parseFloat(bill.repaid) || 0) > 0 ? '部分还款' : '未还款'),
       now, bill.id]
    );

    console.log(`[撤销消费] billMonth=${billMonth}, 撤销=${amountNum}, 新账单=${newBillAmount}`);

    return this.findById(bill.id, userId);
  }

  /**
   * 回滚还款：删除还款流水时恢复账单已用额度
   * 效果：增加已用额度，减少已还金额
   */
  static async rollbackRepay(cardId, userId, repayAmount, transDate) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return;

    const billMonth = this.getBillMonthByDate(transDate, card.bill_day);
    const bill = await this.findByCardAndMonth(cardId, billMonth);
    if (!bill) return;

    const now = String(Date.now());
    const amountNum = parseFloat(repayAmount);
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    // 回滚还款：bill_amount 不变（消费记录还在），used_limit += amount, 已还金额 -= amount
    const newUsedLimit = (parseFloat(bill.used_limit) || 0) + amountNum;
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit;
    const newRepaid = Math.max(0, (parseFloat(bill.repaid) || 0) - amountNum);
    const newNeedRepay = (parseFloat(bill.bill_amount) || 0) - newRepaid;
    const newPoints = Math.round(newUsedLimit * (card.points_rate || 1));

    let repayStatus = '未还款';
    if (newNeedRepay <= 0) repayStatus = '已还清';
    else if (newRepaid > 0) repayStatus = '部分还款';

    await db.execute(
      `UPDATE ${this.tableName} SET
         used_limit = ?, avail_limit = ?,
         repaid = ?, need_repay = ?, points = ?,
         repay_status = ?, update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newUsedLimit.toFixed(2), newAvailLimit.toFixed(2),
       newRepaid.toFixed(2), newNeedRepay.toFixed(2), newPoints,
       repayStatus, now, bill.id]
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
    const newRepaid = (parseFloat(bill.repaid) || 0) + amountNum;
    const newNeedRepay = Math.max(0, (parseFloat(bill.bill_amount) || 0) - newRepaid);

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
   * 遍历所有流水（含冲正），根据交易方向和金额计算净消费和净还款
   * 自动检测并修复旧数据异常（还款冲正方向错误导致余额丢失）
   */
  static async rebuildBillFromAccount(cardId, userId) {
    console.log(`[重建账单] ========== cardId=${cardId}, userId=${userId}`);
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') {
      console.log(`[重建账单] 非信用卡，退出`);
      return null;
    }

    const now = String(Date.now());

    // ===== 1. 获取该信用卡的所有消费流水（account 表） =====
    // 注意：还款流水在 account 表中的 card_id 指向的是还款来源卡（借记卡），不是本信用卡
    // 所以这里只获取 direction=0 的普通消费
    const [expenseRows] = await db.execute(
      `SELECT id, direction, amount, trans_date, category_id, reversed_id, is_deleted
       FROM account 
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0
         AND direction = 0 AND (category_id IS NULL OR category_id != 'CATEGORY_REPAY')
       ORDER BY trans_date ASC`,
      [cardId, userId]
    );

    // ===== 2. 获取该信用卡的还款记录（card_repay 表） =====
    // 注：需要验证 account_id 对应的流水未被删除，避免脏数据
    const [repayRows] = await db.execute(
      `SELECT r.id, r.repay_amount, r.bill_month, r.bill_id,
              COALESCE(r.bill_month, b.bill_month) AS target_bill_month
       FROM card_repay r
       LEFT JOIN card_bill b ON r.bill_id = b.id AND b.is_deleted = 0
       LEFT JOIN account a ON r.account_id = a.id
       WHERE r.card_id = ? AND r.is_deleted = 0
         AND (r.account_id IS NULL OR a.is_deleted = 0)`,
      [cardId]
    );

    // ===== 3. 查找并记录被撤销的消费流水ID（避免被重复计入） =====
    const [reversalFlows] = await db.execute(
      `SELECT id, reversed_id, direction FROM account
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0
         AND reversed_id IS NOT NULL AND direction = 1`,
      [cardId, userId]
    );
    const reversedExpenseIds = new Set(reversalFlows.map(r => r.reversed_id));

    // ===== 4. 按账单月聚合消费 =====
    const billMonthData = {};

    for (const row of expenseRows) {
      const amount = parseFloat(row.amount) || 0;
      if (amount <= 0) continue;

      // 跳过被冲正的流水（已有 direction=1 的反向记录）
      if (row.reversed_id || reversedExpenseIds.has(row.id)) continue;

      const billMonth = this.getBillMonthByDate(row.trans_date, card.bill_day);
      if (!billMonthData[billMonth]) {
        billMonthData[billMonth] = { totalExpense: 0, repayments: [] };
      }
      billMonthData[billMonth].totalExpense += amount;
    }

    // ===== 5. 按账单月聚合还款 =====
    for (const repay of repayRows) {
      const amount = parseFloat(repay.repay_amount) || 0;
      if (amount <= 0) continue;

      // 优先使用 card_bill 表的 bill_month（通过 bill_id 关联），更准确
      // 其次使用 card_repay 自身记录的 bill_month
      const billMonth = repay.target_bill_month || repay.bill_month || this.getCurrentBillMonth();
      if (!billMonthData[billMonth]) {
        billMonthData[billMonth] = { totalExpense: 0, repayments: [] };
      }
      billMonthData[billMonth].repayments.push(amount);
    }

    // ===== 5. 按账单月从旧到新处理：消费统计 + 溢缴款顺延 =====
    const sortedMonths = Object.keys(billMonthData).sort();
    let carryOver = 0; // 溢缴款（多还部分）顺延到下一期

    for (const billMonth of sortedMonths) {
      const data = billMonthData[billMonth];
      const totalExpense = data.totalExpense;
      
      // 本账单收到的还款总额
      const totalRepaidThisMonth = data.repayments.reduce((a, b) => a + b, 0);
      
      // 实际可抵金额 = 本期还款 + 上期溢缴款
      const availableRepaid = totalRepaidThisMonth + carryOver;
      
      // 本期应还 = 消费总额
      const billAmount = totalExpense;
      
      // 实际用于本期还款的金额（不超过应还金额）
      const appliedToThisBill = Math.min(availableRepaid, billAmount);
      
      // 溢缴款 = 可用还款 - 实际用于本期还款
      carryOver = Math.max(0, availableRepaid - billAmount);
      
      data.repaid = appliedToThisBill;
      data.needRepay = billAmount - appliedToThisBill;
    }

    // ===== 6. 更新或创建账单 =====
    const results = [];
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    for (const billMonth of sortedMonths) {
      const data = billMonthData[billMonth];
      const { totalExpense, repaid, needRepay } = data;

      let bill = await this.findByCardAndMonth(cardId, billMonth);
      if (!bill) {
        bill = await this.create({ userId, cardId, billMonth });
      }
      if (!bill) continue;

      const { billStartDate, billEndDate } = this.calculateBillPeriod(billMonth, card.bill_day);
      const usedLimit = totalExpense;
      const availLimit = Math.max(0, creditLimit + tempLimit - usedLimit);
      const points = Math.round(usedLimit * (card.points_rate || 1));

      let repayStatus = '未还款';
      if (needRepay <= 0) repayStatus = '已还清';
      else if (repaid > 0) repayStatus = '部分还款';

      await db.execute(
        `UPDATE ${this.tableName} SET
           credit_limit = ?, avail_limit = ?, used_limit = ?, temp_limit = ?,
           bill_start_date = ?, bill_end_date = ?, bill_amount = ?,
           min_repay = ?, repaid = ?, need_repay = ?, points = ?,
           repay_status = ?, update_time = ?
         WHERE id = ? AND is_deleted = 0`,
        [creditLimit, availLimit.toFixed(2), usedLimit.toFixed(2), tempLimit,
         billStartDate, billEndDate, totalExpense.toFixed(2),
         Math.max(0, totalExpense * 0.1).toFixed(2),
         repaid.toFixed(2), needRepay.toFixed(2), points, repayStatus, now, bill.id]
      );

      results.push({ billMonth, billId: bill.id, totalExpense, repaid, needRepay: needRepay.toFixed(2) });
    }

    // ===== 7. 确保当前月有账单（不覆盖已存在的账单数据） =====
    const currentBillMonth = this.getCurrentBillMonth();
    const existingCurrentBill = await this.findByCardAndMonth(cardId, currentBillMonth);

    if (!existingCurrentBill) {
      const newBill = await this.create({ userId, cardId, billMonth: currentBillMonth });
      if (newBill) {
        results.push({ billMonth: currentBillMonth, billId: newBill.id, totalExpense: 0, repaid: 0, needRepay: '0.00', created: true });
      }
    } else if (!billMonthData[currentBillMonth]) {
      // 当前月有账单但不在本次重建范围内，只更新周期
      const { billStartDate, billEndDate } = this.calculateBillPeriod(currentBillMonth, card.bill_day);
      await db.execute(
        `UPDATE ${this.tableName} SET bill_start_date = ?, bill_end_date = ?, update_time = ? WHERE id = ? AND is_deleted = 0`,
        [billStartDate, billEndDate, now, existingCurrentBill.id]
      );
      results.push({ billMonth: currentBillMonth, billId: existingCurrentBill.id, totalExpense: 0, repaid: 0, needRepay: '0.00', updatedPeriod: true });
    }

    await AccountSettlement.syncBalanceSnapshot(cardId, userId);
    console.log(`[重建账单] 完成:`, JSON.stringify(results));
    return results;
  }
}

module.exports = CardBill;
