const db = require('../config/db');
const idUtils = require('../utils/idUtils');
const AccountSettlement = require('../services/AccountSettlement');

/**
 * 卡片账单模型 - 对应数据库 card_bill 表
 *
 * 业务规则：
 * 1. 账单按月生成，每卡每月一条
 * 2. 账单周期：每月16日 ~ 次月15日
 * 3. 消费日期 <= 15日 归属当月账单，消费日期 >= 16日 归属下月账单（新周期）
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
   * 账单周期：每月16日 ~ 次月15日
   * 消费日期 <= 15日 属于当月账单，消费日期 >= 16日 属于下月账单（开启新周期）
   */
  static getBillMonthByDate(transDate) {
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

    // day <= 15 属于当月账单，day >= 16 属于下月账单（开启新周期）
    if (day >= 16) {
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

    const billMonth = this.getBillMonthByDate(transDate);
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

    const billMonth = this.getBillMonthByDate(transDate);
    const bill = await this.findByCardAndMonth(cardId, billMonth);
    if (!bill) return;

    const now = String(Date.now());
    const amountNum = parseFloat(amount);
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;
    const currentRepaid = parseFloat(bill.repaid) || 0;
    const currentBillAmount = parseFloat(bill.bill_amount) || 0;

    // 计算撤销消费后的状态
    const newBillAmount = Math.max(0, currentBillAmount - amountNum);
    const newUsedLimit = Math.max(0, (parseFloat(bill.used_limit) || 0) - amountNum);

    // 计算溢缴款逻辑
    const tempNeedRepay = newBillAmount - currentRepaid;
    let overflowAmount = 0;
    let newRepaid = currentRepaid;

    if (tempNeedRepay <= 0) {
      // 撤销消费后无欠款，剩余还款转为溢缴款
      overflowAmount = Math.abs(tempNeedRepay);
      newRepaid = 0; // 已还清，重置 repaid

      // 软删除该账单月的所有还款记录
      await db.execute(
        `UPDATE card_repay SET is_deleted = 1, update_time = ? WHERE bill_id = ? AND is_deleted = 0`,
        [now, bill.id]
      );
      console.log(`[撤销消费] 软删除账单 ${bill.id} 的所有还款记录`);
    }

    const newNeedRepay = Math.max(0, newBillAmount - newRepaid);
    const newAvailLimit = creditLimit + tempLimit - newUsedLimit + overflowAmount;
    const newPoints = Math.round(newUsedLimit * (card.points_rate || 1));

    // 如果有溢缴款，写入 account_balance 表
    if (overflowAmount > 0) {
      // 检查是否已有该卡片的余额记录
      const [existingBalance] = await db.execute(
        `SELECT id, balance FROM account_balance WHERE card_id = ? AND is_deleted = 0`,
        [cardId]
      );

      if (existingBalance.length > 0) {
        // 更新已有余额
        const currentBalance = parseFloat(existingBalance[0].balance) || 0;
        const newBalance = currentBalance + overflowAmount;
        await db.execute(
          `UPDATE account_balance SET balance = ?, update_time = ? WHERE id = ?`,
          [newBalance.toFixed(2), now, existingBalance[0].id]
        );
        console.log(`[撤销消费] 更新溢缴款余额 ${existingBalance[0].id}，+${overflowAmount}，新余额=${newBalance}`);
      } else {
        // 创建新的余额记录
        const balanceId = idUtils.billId();
        await db.execute(
          `INSERT INTO account_balance (id, user_id, card_id, balance, currency, update_time, is_deleted)
           VALUES (?, ?, ?, ?, 'CNY', ?, 0)`,
          [balanceId, userId, cardId, overflowAmount.toFixed(2), now]
        );
        console.log(`[撤销消费] 创建溢缴款余额 ${balanceId}，金额=${overflowAmount}`);
      }
    }

    // 重置账单
    await db.execute(
      `UPDATE ${this.tableName} SET
         bill_amount = ?, used_limit = ?, avail_limit = ?,
         repaid = ?, need_repay = ?, min_repay = ?, points = ?,
         repay_status = ?, update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [newBillAmount.toFixed(2), newUsedLimit.toFixed(2), newAvailLimit.toFixed(2),
       newRepaid.toFixed(2), newNeedRepay.toFixed(2), Math.max(0, newBillAmount * 0.1).toFixed(2), newPoints,
       newNeedRepay <= 0 ? '已还款' : (newRepaid > 0 ? '部分还款' : '未还款'),
       now, bill.id]
    );

    console.log(`[撤销消费] billMonth=${billMonth}, 撤销=${amountNum}, 新账单=${newBillAmount}, repaid已重置=${newRepaid}, 溢缴款=${overflowAmount}`);

    return this.findById(bill.id, userId);
  }

  /**
   * 回滚还款：删除还款流水时恢复账单已用额度
   * 效果：增加已用额度，减少已还金额
   */
  static async rollbackRepay(cardId, userId, repayAmount, transDate) {
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') return;

    const billMonth = this.getBillMonthByDate(transDate);
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
    console.log(`[重建账单] ========== 函数被调用 ========== cardId=${cardId}, userId=${userId}`);
    const card = await this.getCardInfo(cardId);
    if (!card || card.card_type !== 'credit') {
      console.log(`[重建账单] 卡不存在或非信用卡，退出`);
      return null;
    }
    console.log(`[重建账单] card:`, card.card_type, card.id);

    const now = String(Date.now());

    // ===== 检测并修复旧数据异常 =====
    // 旧版：原还款 direction=1（收入），冲正 direction=0（支出）→ 余额被多扣
    // 新版：原还款 direction=0（支出），冲正 direction=1（收入）→ 余额正确恢复
    // 检测：冲正 direction=0 且原还款 direction=1（才是旧版格式）
    const [reversalRows] = await db.execute(
      `SELECT r.id as reversal_id, r.amount, r.trans_date,
              o.id as original_id, o.amount as original_amount, o.direction as original_direction
       FROM account r
       LEFT JOIN account o ON r.reversed_id = o.id
       WHERE r.card_id = ? AND r.user_id = ? 
         AND r.reversed_id IS NOT NULL AND r.is_deleted = 0
         AND r.direction = 0 AND o.direction = 1`,
      [cardId, userId]
    );

    if (reversalRows.length > 0) {
      console.log(`[重建账单] 检测到 ${reversalRows.length} 条旧版冲正记录需要修复`);

      for (const reversal of reversalRows) {
        // 创建补偿流水（direction=1 收入）来恢复余额
        const [existingCompensation] = await db.execute(
          `SELECT id FROM account WHERE reversed_id = ? AND is_deleted = 0`,
          [reversal.reversal_id]
        );

        if (existingCompensation.length === 0) {
          const compensationId = idUtils.billId();
          await db.execute(
            `INSERT INTO account (id, user_id, direction, category_id, pay_type, pay_method, 
              amount, currency, exchange_rate, trans_date, remark, card_id, 
              create_time, update_time, is_deleted, reversed_id)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`,
            [
              compensationId, userId, 1, 'CATEGORY_REPAY', '还款', '余额',
              reversal.original_amount, 'CNY', 1, reversal.trans_date,
              `余额补偿-冲正修复(${reversal.original_amount}元)`,
              cardId, now, now, reversal.reversal_id
            ]
          );
          console.log(`[重建账单] 已创建补偿流水 ${compensationId}，金额 ${reversal.original_amount}，关联冲正 ${reversal.reversal_id}`);
        }
      }
    }

    // ===== 重新获取流水（包含新创建的补偿流水）=====
    const [rows] = await db.execute(
      `SELECT id, direction, amount, trans_date, category_id, reversed_id, is_deleted
       FROM account 
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0
       ORDER BY trans_date ASC`,
      [cardId, userId]
    );

    // ===== 从 card_repay 表汇总还款（使用 create_time 计算账单月）=====
    const [repayRows] = await db.execute(
      `SELECT repay_amount, create_time, is_deleted
       FROM card_repay 
       WHERE card_id = ? AND is_deleted = 0`,
      [cardId]
    );

    // 按账单月聚合消费和还款
    const billMonthData = {};

    // 处理消费和溢缴款流水
    for (const row of rows) {
      const billMonth = this.getBillMonthByDate(row.trans_date);
      if (!billMonthData[billMonth]) {
        billMonthData[billMonth] = { totalExpense: 0, totalRepaid: 0, totalOverflow: 0 };
      }

      const amount = parseFloat(row.amount) || 0;

      // 被冲正的流水，跳过（冲正流水会抵消原流水）
      if (row.reversed_id) {
        continue;
      }


      // 判断流水类型
      if (row.category_id === 'CATEGORY_REPAY') {
        // 还款流水（direction=0 支出）
        if (row.direction === 0) {
          billMonthData[billMonth].totalRepaid += amount;
        }
      } else if (row.category_id === 'CATEGORY_OVERFLOW') {
        // 溢缴款流水（direction=1 收入）
        if (row.direction === 1) {
          billMonthData[billMonth].totalOverflow += amount;
        }
      } else {
        // 普通消费流水（direction=0 支出）
        if (row.direction === 0) {
          billMonthData[billMonth].totalExpense += amount;
        }
      }
    }

    // 处理 card_repay 表的还款记录（使用 create_time 计算账单月）
    for (const repay of repayRows) {
      if (repay.is_deleted === 1) continue; // 跳过已删除的还款记录
      const billMonth = this.getBillMonthByDate(repay.create_time);
      if (!billMonthData[billMonth]) {
        billMonthData[billMonth] = { totalExpense: 0, totalRepaid: 0, totalOverflow: 0 };
      }
      billMonthData[billMonth].totalRepaid += parseFloat(repay.repay_amount) || 0;
    }

    const results = [];
    const creditLimit = parseFloat(card.credit_limit) || 0;
    const tempLimit = parseFloat(card.temp_limit) || 0;

    for (const billMonth of Object.keys(billMonthData).sort().reverse()) {
      const { totalExpense, totalRepaid, totalOverflow } = billMonthData[billMonth];

      let bill = await this.findByCardAndMonth(cardId, billMonth);
      if (!bill) {
        bill = await this.create({ userId, cardId, billMonth });
      }
      if (!bill) continue;

      const { billStartDate, billEndDate } = this.calculateBillPeriod(billMonth, card.bill_day);
      const usedLimit = totalExpense;
      const billAmount = totalExpense;
      // 实际已还金额 = 还款流水（冲正已被过滤）
      const repaid = Math.max(0, totalRepaid);
      // 可用额度 = 额度 - 已用 + 溢缴款（从流水统计的 totalOverflow）
      const availLimit = creditLimit + tempLimit - usedLimit + totalOverflow;
      // 需还款 = 账单金额 - 已还款 - 溢缴款（溢缴款抵消欠款）
      const needRepay = Math.max(0, billAmount - repaid - totalOverflow);
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
         billStartDate, billEndDate, billAmount.toFixed(2), Math.max(0, billAmount * 0.1).toFixed(2),
         repaid.toFixed(2), needRepay.toFixed(2), points, repayStatus, now, bill.id]
      );

      results.push({ 
        billMonth, 
        billId: bill.id, 
        totalExpense, 
        totalRepaid, 
        totalOverflow,
        needRepay: needRepay.toFixed(2) 
      });
    }

    // 确保当前月有账单（新卡无历史流水时也创建空账单）
    const currentBillMonth = this.getCurrentBillMonth();
    const existingCurrentBill = await this.findByCardAndMonth(cardId, currentBillMonth);
    if (!existingCurrentBill) {
      const newBill = await this.create({ userId, cardId, billMonth: currentBillMonth });
      if (newBill) {
        results.push({ billMonth: currentBillMonth, billId: newBill.id, totalExpense: 0, totalRepaid: 0, totalOverflow: 0, needRepay: '0.00' });
      }
    }

    await AccountSettlement.syncBalanceSnapshot(cardId, userId);

    return results;
  }
}

module.exports = CardBill;
