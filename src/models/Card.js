const db = require('../config/db');
const idUtils = require('../utils/idUtils');

/**
 * 卡片模型 - 对应数据库 card_base 表
 */
class Card {
  static tableName = 'card_base';

  /**
   * 获取卡片列表 - 根据卡类型返回不同字段
   */
  static async findAll(userId, filters = {}) {
    let whereClause = 'WHERE user_id = ? AND is_deleted = 0';
    const params = [userId];

    if (filters.cardType) {
      whereClause += ' AND card_type = ?';
      params.push(filters.cardType);
    }

    if (filters.isHide !== undefined) {
      whereClause += ' AND is_hide = ?';
      params.push(filters.isHide);
    }

    // 根据卡类型返回不同字段
    if (filters.cardType === 'credit') {
      // 信用卡返回完整字段
      const query = `
        SELECT id, user_id, bank_id, card_type, card_bin, card_length, last4_no, alias,
               open_date, expire_date, card_org, tag, bill_day, repay_day,
               card_img, color
        FROM ${this.tableName}
        ${whereClause}
        ORDER BY is_default DESC, sort ASC, create_time DESC
      `;
      const [rows] = await db.execute(query, params);
      return rows;
    } else if (filters.cardType === 'debit') {
      // 借记卡返回基础字段
      const query = `
        SELECT id, user_id, bank_id, card_type, card_bin, card_length, last4_no, alias,
               open_date, expire_date, card_org, tag, card_img, color
        FROM ${this.tableName}
        ${whereClause}
        ORDER BY is_default DESC, sort ASC, create_time DESC
      `;
      const [rows] = await db.execute(query, params);
      return rows;
    } else {
      // 不筛选时默认返回信用卡字段
      const query = `
        SELECT id, user_id, bank_id, card_type, card_bin, card_length, last4_no, alias,
               open_date, expire_date, card_org, tag, bill_day, repay_day,
               card_img, color
        FROM ${this.tableName}
        ${whereClause}
        ORDER BY is_default DESC, sort ASC, create_time DESC
      `;
      const [rows] = await db.execute(query, params);
      return rows;
    }
  }

  /**
   * 根据ID查找卡片
   */
  static async findById(id, userId) {
    const query = `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 创建卡片
   */
  static async create({
    userId,
    bankId,
    cardType,
    cardLevel,
    mainSub,
    cardOrg,
    cardLength,
    last4No,
    cardBin,
    creditLimit,
    tempLimit,
    alias,
    cardImg,
    openDate,
    expireDate,
    billDay,
    repayDay,
    currency,
    status,
    isDefault,
    isHide,
    sort,
    tag,
    remark,
    color,
    annualFee,
    feeFreeRule,
    sourceFrom,
    pointsRate
  }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    const query = `
      INSERT INTO ${this.tableName} (
        id, user_id, bank_id, card_type, card_level, main_sub, card_org,
        card_length, last4_no, card_bin, credit_limit, temp_limit,
        alias, card_img, open_date, expire_date,
        bill_day, repay_day, currency, status, is_default, is_hide, sort,
        tag, remark, color, annual_fee, fee_free_rule, source_from,
        points_rate, create_time, update_time, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `;

    await db.execute(query, [
      id,
      userId,
      bankId || '',
      cardType || 'debit',
      cardLevel || '',
      mainSub || '主卡',
      cardOrg || '',
      cardLength || '19',
      last4No || '',
      cardBin || '',
      creditLimit || 0,
      tempLimit || 0,
      alias || '',
      cardImg || '',
      openDate || '',
      expireDate || '',
      billDay || 0,
      repayDay || 0,
      currency || 'CNY',
      status || '正常',
      isDefault ? 1 : 0,
      isHide ? 1 : 0,
      sort || 99,
      tag || '',
      remark || '',
      color || '#0052cc',
      annualFee || 0,
      feeFreeRule || '',
      sourceFrom || '手动',
      pointsRate || 1,
      now,
      now
    ]);

    // 信用卡：立即初始化当前月账单
    if (cardType === 'credit') {
      try {
        const CardBill = require('./CardBill');
        await CardBill.getOrCreateBill(id, userId);
      } catch (err) {
        console.error('初始化账单失败:', err);
      }
    }

    return this.findById(id, userId);
  }

  /**
   * 更新卡片
   */
  static async update(id, userId, updates) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    const fieldMap = {
      bankId: 'bank_id',
      cardType: 'card_type',
      cardLevel: 'card_level',
      mainSub: 'main_sub',
      cardOrg: 'card_org',
      cardLength: 'card_length',
      last4No: 'last4_no',
      cardBin: 'card_bin',
      alias: 'alias',
      cardImg: 'card_img',
      openDate: 'open_date',
      expireDate: 'expire_date',
      billDay: 'bill_day',
      repayDay: 'repay_day',
      currency: 'currency',
      status: 'status',
      isDefault: 'is_default',
      isHide: 'is_hide',
      sort: 'sort',
      tag: 'tag',
      remark: 'remark',
      color: 'color',
      annualFee: 'annual_fee',
      feeFreeRule: 'fee_free_rule',
      sourceFrom: 'source_from'
    };

    Object.keys(updates).forEach(key => {
      if (fieldMap[key] !== undefined) {
        let value = updates[key];
        // 处理布尔值转 tinyint
        if (key === 'isDefault' || key === 'isHide') {
          value = value ? 1 : 0;
        }
        fields.push(`${fieldMap[key]} = ?`);
        params.push(value);
      }
    });

    if (fields.length === 0) {
      return this.findById(id, userId);
    }

    fields.push('update_time = ?');
    params.push(now);
    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName}
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;
    await db.execute(query, params);
    return this.findById(id, userId);
  }

  /**
   * 删除卡片
   * - 只删除 account_balance（余额数据无意义）
   * - 其他关联数据（账单/还款/流水/日志）保留，查询时用 is_deleted=0 过滤
   */
  static async delete(id, userId) {
    const now = String(Date.now());

    // 1. 删除关联的账户余额
    await db.execute(
      `UPDATE account_balance SET is_deleted = 1, update_time = ? WHERE card_id = ? AND user_id = ?`,
      [now, id, userId]
    );

    // 2. 删除关联的流水记录（可选，如需清理）
    await db.execute(
      `UPDATE account SET is_deleted = 1, update_time = ? WHERE card_id = ? AND user_id = ?`,
      [now, id, userId]
    );

    // 3. 软删除卡片主表
    const query = `
      UPDATE ${this.tableName}
      SET is_deleted = 1, update_time = ?
      WHERE id = ? AND user_id = ?
    `;
    const [result] = await db.execute(query, [now, id, userId]);
    return result.affectedRows > 0;
  }

  /**
   * 获取卡片数量
   */
  static async count(userId, cardType) {
    let query = `SELECT COUNT(*) as count FROM ${this.tableName} WHERE user_id = ? AND is_deleted = 0`;
    const params = [userId];

    if (cardType) {
      query += ' AND card_type = ?';
      params.push(cardType);
    }

    const [rows] = await db.execute(query, params);
    return rows[0].count;
  }
}

module.exports = Card;
