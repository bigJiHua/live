const db = require('../config/db');
const idUtils = require('../utils/idUtils');
const CardBill = require('./CardBill');
const Account = require('./Account');
const AccountBalance = require('./AccountBalance');
const AccountSettlement = require('../services/AccountSettlement');

/**
 * 卡片还款记录模型 - 对应数据库 card_repay 表
 */
class CardRepay {
  static tableName = 'card_repay';

  /**
   * 获取还款记录列表
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE cr.user_id = ? AND cr.is_deleted = 0';
    const params = [userId];

    if (filters.cardId) {
      whereClause += ' AND cr.card_id = ?';
      params.push(filters.cardId);
    }

    if (filters.billId) {
      whereClause += ' AND cr.bill_id = ?';
      params.push(filters.billId);
    }

    if (filters.billMonth) {
      whereClause += ' AND cr.bill_month = ?';
      params.push(filters.billMonth);
    }

    const query = `
      SELECT cr.*, c.alias as card_alias, c.last4_no as card_last4,
             cb.bill_amount, cb.need_repay as bill_need_repay,
             rc.alias as repay_card_alias, rc.last4_no as repay_card_last4
      FROM ${this.tableName} cr
      LEFT JOIN card_base c ON cr.card_id = c.id
      LEFT JOIN card_bill cb ON cr.bill_id = cb.id
      LEFT JOIN card_base rc ON cr.repay_card_id = rc.id
      ${whereClause}
      ORDER BY cr.repay_time DESC
    `;
    const [rows] = await db.execute(query, params);
    return rows;
  }

  /**
   * 根据ID查找记录
   */
  static async findById(id, userId) {
    const query = `
      SELECT cr.*, c.alias as card_alias, c.last4_no as card_last4,
             cb.bill_amount, cb.need_repay as bill_need_repay,
             rc.alias as repay_card_alias, rc.last4_no as repay_card_last4
      FROM ${this.tableName} cr
      LEFT JOIN card_base c ON cr.card_id = c.id
      LEFT JOIN card_bill cb ON cr.bill_id = cb.id
      LEFT JOIN card_base rc ON cr.repay_card_id = rc.id
      WHERE cr.id = ? AND cr.user_id = ? AND cr.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 执行还款核心逻辑
   * @param {Object} params - 还款参数
   * @param {string} params.userId - 用户ID
   * @param {string} params.cardId - 卡片ID
   * @param {string} params.billId - 账单ID（可选）
   * @param {number} params.repayAmount - 还款金额
   * @param {string} params.repayMethod - 还款方式：card(本卡)/balance(余额)/bank_card(银行卡)/cash(现金)
   * @param {string} params.repayMethodCardId - 还款方式使用的卡ID（bank_card时）
   * @param {string} params.repayTime - 还款时间
   * @param {string} params.remark - 备注
   */
  static async executeRepay({
    userId,
    cardId,
    billId,
    repayAmount,
    repayMethod,
    repayMethodCardId,
    repayTime,
    remark
  }) {
    const now = String(Date.now());
    const repayAmountNum = parseFloat(repayAmount);

    if (repayAmountNum <= 0) {
      throw new Error('还款金额必须大于0');
    }

    // 获取卡片信息
    const [cardRows] = await db.execute(
      'SELECT * FROM card_base WHERE id = ? AND is_deleted = 0',
      [cardId]
    );
    
    const card = cardRows[0];
    console.log(card);
    
    if (!card) {
      throw new Error('卡片不存在');
    }

    // 获取当前账单（如果有）
    let bill = null;
    if (billId) {
      const [billRows] = await db.execute(
        'SELECT * FROM card_bill WHERE id = ? AND is_deleted = 0',
        [billId]
      );
      bill = billRows[0];
    }

    // ===== 验证还款来源余额 =====
    let sourceCardId = null; // 资金来源的card_id
    let sourceBalance = 0;   // 当前余额

    if (repayMethod === 'balance') {
      // 使用余额还款（微信+支付宝，card_id = yyyy）
      const balance = await AccountSettlement.calculateBalance('yyyy', userId);
      sourceBalance = balance.balance;
      sourceCardId = 'yyyy';
    } else if (repayMethod === 'bank_card') {
      // 使用指定银行卡还款
      if (!repayMethodCardId) {
        throw new Error('请指定还款使用的银行卡');
      }

      // 校验还款卡类型，禁止信用卡还信用卡
      const [sourceCardRows] = await db.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0',
        [repayMethodCardId]
      );
      const sourceCardType = sourceCardRows[0]?.card_type;
      if (sourceCardType === 'credit') {
        throw new Error('禁止使用信用卡还款，请选择储蓄卡或其他方式');
      }

      const balance = await AccountSettlement.calculateBalance(repayMethodCardId, userId);
      sourceBalance = balance.balance;
      sourceCardId = repayMethodCardId;
    } else if (repayMethod === 'cash') {
      // 现金还款，查询虚拟现金账户（card_id = xxxx）
      const balance = await AccountSettlement.calculateBalance('xxxx', userId);
      sourceBalance = balance.balance;
      sourceCardId = 'xxxx';
    }

    // 验证余额是否充足（card方式不需要验证）
    if (repayMethod !== 'card' && sourceBalance < repayAmountNum) {
      throw new Error(`余额不足，当前余额 ${sourceBalance}，需要 ${repayAmountNum}`);
    }

    // ===== 计算还款结果 =====
    const currentUsedLimit = bill ? parseFloat(bill.used_limit || 0) : 0;
    const creditLimit = bill ? parseFloat(bill.credit_limit || 0) : 0;

    let actualRepayToBill = repayAmountNum; // 实际用于还账单的部分
    let overflowAmount = 0;                // 溢缴款金额

    // 如果还款金额 > 已用额度，多余部分为溢缴款
    if (currentUsedLimit > 0 && repayAmountNum > currentUsedLimit) {
      overflowAmount = repayAmountNum - currentUsedLimit;
      actualRepayToBill = currentUsedLimit;
    }

    // ===== 处理资金转移 =====
    let repayAccountId = null; // 保存还款流水ID，用于关联 card_repay
    
    if (repayMethod !== 'card') {
      // 1. 从还款来源扣款（生成支出流水）
      const repayAccount = await Account.create({
        userId,
        direction: 0, // 支出
        categoryId: 'CATEGORY_REPAY', // 还款分类（需确保存在）
        payType: '还款',
        payMethod: repayMethod === 'balance' ? '余额' : (repayMethod === 'bank_card' ? '银行卡' : '现金'),
        accountType: 'debit',
        amount: repayAmountNum,
        currency: card.currency || 'CNY',
        exchangeRate: 1,
        transDate: repayTime || now.substring(0, 10),
        remark: remark || `信用卡还款至${card.alias || card.last4_no}`,
        cardId: sourceCardId
      });
      
      // 保存还款流水ID用于关联
      if (repayAccount && repayAccount.id) {
        repayAccountId = repayAccount.id;
      }

      // 2. 溢缴款写入信用卡余额（作为收入）
      if (overflowAmount > 0) {
        await Account.create({
          userId,
          direction: 1, // 收入
          categoryId: 'CATEGORY_OVERFLOW', // 溢缴款分类
          payType: '还款',
          payMethod: '溢缴款',
          accountType: 'credit',
          amount: overflowAmount,
          currency: card.currency || 'CNY',
          exchangeRate: 1,
          transDate: repayTime || now.substring(0, 10),
          remark: remark || `溢缴款 ${overflowAmount} 元`,
          cardId: cardId
        });
      }
    }

    // ===== 更新账单额度 =====
    if (bill) {
      // 计算新的已用额度和可用额度
      const newUsedLimit = Math.max(0, currentUsedLimit - actualRepayToBill);
      const newAvailLimit = creditLimit - newUsedLimit;

      // 更新 card_bill 的已用额度和可用额度
      await db.execute(
        `UPDATE card_bill 
         SET used_limit = ?, avail_limit = ?, update_time = ?
         WHERE id = ? AND is_deleted = 0`,
        [newUsedLimit.toFixed(2), newAvailLimit.toFixed(2), now, billId]
      );

      // 更新账单的已还金额（从card_repay汇总）
      const [repayRows] = await db.execute(
        `SELECT COALESCE(SUM(repay_amount), 0) as total 
         FROM ${this.tableName} 
         WHERE bill_id = ? AND is_deleted = 0`,
        [billId]
      );
      const newRepaid = parseFloat(repayRows[0].total) || 0;
      const billAmount = parseFloat(bill.bill_amount) || 0;
      const newNeedRepay = Math.max(0, billAmount - newRepaid);
      const repayStatus = newNeedRepay <= 0 ? '已还款' : '未还款';

      // 判断逾期
      const nowDate = new Date();
      let isOverdue = false;
      let overdueDays = 0;
      if (newNeedRepay > 0 && bill.bill_end_date) {
        const deadline = new Date(bill.bill_end_date);
        if (nowDate > deadline) {
          isOverdue = true;
          overdueDays = Math.ceil((nowDate - deadline) / (1000 * 60 * 60 * 24));
        }
      }

      await db.execute(
        `UPDATE card_bill 
         SET repaid = ?, need_repay = ?, repay_status = ?, 
             is_overdue = ?, overdue_days = ?, update_time = ?
         WHERE id = ? AND is_deleted = 0`,
        [newRepaid.toFixed(2), newNeedRepay.toFixed(2), repayStatus, isOverdue ? 1 : 0, overdueDays, now, billId]
      );
    }

    // ===== 同步信用卡余额快照 =====
    await AccountSettlement.syncBalanceSnapshot(cardId, userId);

    // ===== 创建还款记录 =====
    const repayId = idUtils.billId();
    let month = null;
    if (bill) {
      month = bill.bill_month;
    }

    // 获取还款来源卡ID（用于 repay_card_id 字段溯源）
    let repaySourceCardId = null;
    if (repayMethod === 'balance') {
      repaySourceCardId = 'yyyy'; // 余额（微信+支付宝）
    } else if (repayMethod === 'bank_card') {
      repaySourceCardId = repayMethodCardId; // 指定银行卡
    } else if (repayMethod === 'cash') {
      repaySourceCardId = 'xxxx'; // 现金
    } else if (repayMethod === 'card') {
      repaySourceCardId = cardId; // 本卡还款
    }

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, bill_id, bill_month, repay_amount,
        repay_method, repay_card_id, repay_time, remark, account_id, is_deleted, create_time, update_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
    `;

    await db.execute(query, [
      repayId,
      cardId,
      userId,
      billId || null,
      month || null,
      repayAmountNum,
      repayMethod,
      repaySourceCardId,
      repayTime || now.substring(0, 10),
      remark || null,
      repayAccountId || null,
      now,
      now
    ]);

    // ===== 同步更新信用卡账单 =====
    if (billId && actualRepayToBill > 0) {
      await CardBill.syncFromRepay(cardId, billId, userId, actualRepayToBill);
    }

    return {
      repayId,
      repayAmount: repayAmountNum,
      actualRepayToBill,
      overflowAmount,
      newUsedLimit: Math.max(0, currentUsedLimit - actualRepayToBill),
      newAvailLimit: creditLimit - Math.max(0, currentUsedLimit - actualRepayToBill)
    };
  }

  /**
   * 创建还款记录（兼容旧接口）
   * @deprecated 推荐使用 executeRepay 方法
   */
  static async create({
    userId,
    cardId,
    billId,
    billMonth,
    repayAmount,
    repayMethod,
    repayCardId,
    repayTime,
    remark
  }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    // 如果没有传入 billMonth，从 billId 获取
    let month = billMonth;
    if (!month && billId) {
      const [billRows] = await db.execute(
        'SELECT bill_month FROM card_bill WHERE id = ? AND is_deleted = 0',
        [billId]
      );
      if (billRows[0]) {
        month = billRows[0].bill_month;
      }
    }

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, bill_id, bill_month, repay_amount,
        repay_method, repay_card_id, repay_time, remark, is_deleted, create_time, update_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
    `;

    await db.execute(query, [
      id,
      cardId,
      userId,
      billId || null,
      month || null,
      repayAmount,
      repayMethod || null,
      repayCardId || cardId, // 默认还款来源为本卡
      repayTime || now,
      remark || null,
      now,
      now
    ]);

    // 创建成功后，更新关联账单的还款信息
    await this.updateBillRepayInfo(billId, userId);

    return this.findById(id, userId);
  }

  /**
   * 更新关联账单的还款金额
   */
  static async updateBillRepayInfo(billId, userId) {
    if (!billId) return;

    // 从 card_repay 表汇总该账单的已还金额
    const [rows] = await db.execute(
      `SELECT COALESCE(SUM(repay_amount), 0) as total 
       FROM ${this.tableName} 
       WHERE bill_id = ? AND is_deleted = 0`,
      [billId]
    );
    const repaid = parseFloat(rows[0].total) || 0;

    // 获取账单信息
    const [billRows] = await db.execute(
      'SELECT * FROM card_bill WHERE id = ? AND is_deleted = 0',
      [billId]
    );
    const bill = billRows[0];
    if (!bill) return;

    // 计算待还金额
    const needRepay = parseFloat(bill.bill_amount) - repaid;

    // 判断还款状态
    const repayStatus = repaid >= parseFloat(bill.bill_amount) ? '已还款' : '未还款';

    // 计算逾期
    const nowDate = new Date();
    const deadline = new Date(bill.bill_end_date);
    let isOverdue = false;
    let overdueDays = 0;
    if (repaid < parseFloat(bill.bill_amount) && nowDate > deadline) {
      isOverdue = true;
      const diffTime = Math.abs(nowDate - deadline);
      overdueDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    }

    // 更新账单
    const nowStr = String(Date.now());
    await db.execute(
      `UPDATE card_bill 
       SET repaid = ?, need_repay = ?, repay_status = ?, 
           is_overdue = ?, overdue_days = ?, update_time = ?
       WHERE id = ? AND is_deleted = 0`,
      [repaid, needRepay, repayStatus, isOverdue ? 1 : 0, overdueDays, nowStr, billId]
    );
  }

  /**
   * 更新还款记录
   */
  static async update(id, userId, updates) {
    // 先获取原记录
    const oldRecord = await this.findById(id, userId);
    if (!oldRecord) return null;

    const fields = [];
    const params = [];

    const fieldMap = {
      repayAmount: 'repay_amount',
      repayMethod: 'repay_method',
      repayCardId: 'repay_card_id',
      repayTime: 'repay_time',
      remark: 'remark'
    };

    Object.keys(updates).forEach(key => {
      if (fieldMap[key] !== undefined) {
        fields.push(`${fieldMap[key]} = ?`);
        params.push(updates[key]);
      }
    });

    if (fields.length === 0) {
      return this.findById(id, userId);
    }

    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName}
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;
    await db.execute(query, params);

    // 更新成功后，重新计算关联账单的还款信息
    await this.updateBillRepayInfo(oldRecord.bill_id, userId);

    return this.findById(id, userId);
  }

  /**
   * 删除还款记录（软删除）
   */
  static async delete(id, userId) {
    // 先获取记录以获取关联的 bill_id
    const record = await this.findById(id, userId);
    if (!record) return false;

    const now = String(Date.now());
    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1, update_time = ?
      WHERE id = ? AND user_id = ?
    `;
    const [result] = await db.execute(query, [now, id, userId]);

    // 删除成功后，重新计算关联账单的还款信息
    if (result.affectedRows > 0) {
      await this.updateBillRepayInfo(record.bill_id, userId);
    }

    return result.affectedRows > 0;
  }
}

module.exports = CardRepay;
