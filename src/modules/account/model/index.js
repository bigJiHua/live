const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const AccountSettlement = require('../service/settlement');
const CardBill = require('../../card/model/bill');
const AssetSnapshot = require('../../asset/model/snapshot');

/**
 * 账务流水模型 - 对应数据库 account 表
 * 
 * 业务规则：
 * 1. 消费时自动同步关联信用卡账单
 * 2. 收支变化时自动记录资产快照
 * 2. 还款时（direction=1, pay_type=还款）自动冲减账单
 * 3. 余额由 AccountSettlement 统一计算
 */
class Account {
  static tableName = 'account';

  /**
   * 获取收支列表（支持分页和过滤）
   */
  static async findAll(userId, filters = {}, page = 1, limit = 20) {
    const offset = (page - 1) * limit;

    let whereClause = 'WHERE a.user_id = ? AND a.is_deleted = 0';
    const params = [userId];

    // 过滤：收支类型
    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }

    // 过滤：分类ID
    if (filters.categoryId) {
      whereClause += ' AND a.category_id = ?';
      params.push(filters.categoryId);
    }

    // 过滤：支付方式
    if (filters.payMethod) {
      whereClause += ' AND a.pay_method = ?';
      params.push(filters.payMethod);
    }

    // 过滤：日期范围
    if (filters.startDate) {
      whereClause += ' AND a.trans_date >= ?';
      params.push(filters.startDate);
    }
    if (filters.endDate) {
      whereClause += ' AND a.trans_date <= ?';
      params.push(filters.endDate);
    }

    // 获取总数
    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    // 获取数据（关联分类和卡片信息）
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      ${whereClause}
      ORDER BY a.trans_date DESC, a.create_time DESC
      LIMIT ? OFFSET ?
    `;
    const queryParams = [...params, String(limit), String(offset)]
    const [rows] = await db.execute(query, queryParams);

    return { rows, total };
  }

  /**
   * 根据ID查找单条记录
   */
  static async findById(id, userId) {
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4,
        rb.remark as reversed_remark
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      LEFT JOIN ${this.tableName} rb ON a.reversed_id = rb.id
      WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0
    `;
    const [rows] = await db.execute(query, [id, userId]);
    return rows[0] || null;
  }

  /**
   * 根据卡片ID获取收支流水（默认本月）
   */
  static async findByCardId(cardId, userId, filters = {}, page = 1, limit = 20) {
    const offset = (page - 1) * limit;

    let whereClause = 'WHERE a.card_id = ? AND a.user_id = ? AND a.is_deleted = 0';
    const params = [cardId, userId];

    // 过滤：收支类型
    if (filters.direction !== undefined) {
      whereClause += ' AND a.direction = ?';
      params.push(filters.direction);
    }

    // 过滤：日期范围（默认当月）
    let startDate = filters.startDate;
    let endDate = filters.endDate;
    if (!startDate && !endDate) {
      const now = new Date();
      const year = now.getFullYear();
      const month = String(now.getMonth() + 1).padStart(2, '0');
      startDate = `${year}-${month}-01`;
      const lastDay = new Date(year, now.getMonth() + 1, 0).getDate();
      endDate = `${year}-${month}-${lastDay}`;
    }
    if (startDate) {
      whereClause += ' AND a.trans_date >= ?';
      params.push(startDate);
    }
    if (endDate) {
      whereClause += ' AND a.trans_date <= ?';
      params.push(endDate);
    }

    // 获取总数
    const countQuery = `SELECT COUNT(*) as total FROM ${this.tableName} a ${whereClause}`;
    const [countResult] = await db.execute(countQuery, params);
    const total = countResult[0].total;

    // 获取数据（关联分类和卡片信息）
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id,
        c.name as category_name,
        cb.alias as card_alias,
        cb.last4_no as card_last4
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      LEFT JOIN card_base cb ON a.card_id = cb.id
      ${whereClause}
      ORDER BY a.trans_date DESC, a.create_time DESC
      LIMIT ? OFFSET ?
    `;
    const [rows] = await db.execute(query, [...params, String(limit), String(offset)]);

    return { rows, total };
  }

  /**
   * 创建账务记录
   * 使用清算中心统一验证
   */
  static async create({ userId, direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId, transferGroupId }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    // 从 card_base 获取真实的 card_type
    let finalAccountType = '';
    if (cardId) {
      const [cardRows] = await db.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0',
        [cardId]
      );
      if (cardRows[0]?.card_type) {
        finalAccountType = cardRows[0].card_type;
      }
    }

    // ===== 信用卡不能收入 =====
    if (finalAccountType === 'credit' && direction === 1) {
      throw new Error('信用卡不能登记收入，还款请使用储蓄卡');
    }

    // ===== 清算中心验证 =====
    if (cardId) {
      const settlementResult = await AccountSettlement.validate({
        card_id: cardId,
        user_id: userId,
        direction: direction,
        amount: amount,
        exchange_rate: exchangeRate || 1,
        currency: currency || 'CNY',
        account_type: finalAccountType
      });

      if (!settlementResult.valid) {
        throw new Error(settlementResult.message);
      }
    }

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, account_type, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, transfer_group_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `;

    await db.execute(query, [
      id,
      userId,
      direction,
      categoryId,
      payType,
      payMethod,
      finalAccountType,
      amount,
      currency || 'CNY',
      exchangeRate || 1,
      transDate || now.substring(0, 10),
      remark || '',
      cardId,
      now,
      now,
      transferGroupId || null
    ]);

    // ===== 清算中心同步余额（从收支表实时计算）=====
    if (cardId) {
      await AccountSettlement.syncBalanceSnapshot(cardId, userId);

      // ===== 信用卡消费实时更新账单 =====
      // 只有信用卡 + 支出 才更新账单
      if (finalAccountType === 'credit' && direction === 0) {
        await CardBill.syncFromExpense(cardId, userId, amount, transDate);
      }
    }

    // ===== 转账关联：收入方向时写入 account_transfer =====
    if (transferGroupId && direction === 1 && payType === '转账') {
      try {
        // 查找同组的支出方记录
        const [expenseRows] = await db.execute(
          `SELECT id, card_id FROM ${this.tableName}
           WHERE transfer_group_id = ? AND user_id = ? AND direction = 0 AND is_deleted = 0
           LIMIT 1`,
          [transferGroupId, userId]
        );
        const expenseRecord = expenseRows[0];
        if (expenseRecord) {
          await db.execute(`
            INSERT INTO account_transfer (id, user_id, from_card_id, to_card_id, amount, trans_date, remark, create_time, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE is_deleted = 0, from_card_id = VALUES(from_card_id), to_card_id = VALUES(to_card_id)
          `, [
            expenseRecord.id, userId, expenseRecord.card_id, cardId,
            amount, transDate || now.substring(0, 10), remark || '转账', now
          ]);
        }
      } catch (e) {
        console.warn(`[转账关联] account_transfer 写入失败:`, e.message);
      }
    }

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return this.findById(id, userId);
  }

  /**
   * 更新账务记录
   */
  static async update(id, userId, { direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId }) {
    const now = String(Date.now());
    const fields = [];
    const params = [];

    if (direction !== undefined) {
      fields.push('direction = ?');
      params.push(direction);
    }
    if (categoryId !== undefined) {
      fields.push('category_id = ?');
      params.push(categoryId);
    }
    if (payType !== undefined) {
      fields.push('pay_type = ?');
      params.push(payType);
    }
    if (payMethod !== undefined) {
      fields.push('pay_method = ?');
      params.push(payMethod);
    }
    if (amount !== undefined) {
      fields.push('amount = ?');
      params.push(amount);
    }
    if (currency !== undefined) {
      fields.push('currency = ?');
      params.push(currency);
    }
    if (exchangeRate !== undefined) {
      fields.push('exchange_rate = ?');
      params.push(exchangeRate);
    }
    if (transDate !== undefined) {
      fields.push('trans_date = ?');
      // 确保日期格式为 YYYY-MM-DD
      const dateStr = String(transDate);
      params.push(dateStr.includes('-') ? dateStr.substring(0, 10) : dateStr);
    }
    if (remark !== undefined) {
      fields.push('remark = ?');
      params.push(remark);
    }
    if (cardId !== undefined) {
      fields.push('card_id = ?');
      params.push(cardId);
    }

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
    
    // 同步余额（如果修改了金额、方向或卡片）
    if (cardId || amount !== undefined || direction !== undefined) {
      const updated = await this.findById(id, userId);
      if (updated?.card_id) {
        await AccountSettlement.syncBalanceSnapshot(updated.card_id, userId);
      }
    }
    
    return updated;
  }

  /**
   * 仅修改备注
   */
  static async updateRemark(id, userId, remark) {
    const now = String(Date.now());

    const query = `
      UPDATE ${this.tableName}
      SET remark = ?, update_time = ?
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;

    const [result] = await db.execute(query, [remark, now, id, userId]);

    if (result.affectedRows === 0) {
      return null;
    }

    return this.findById(id, userId);
  }

  /**
   * 借记卡冲正
   * 原支出 -> 冲正收入，原收入 -> 冲正支出
   */
  static async reverseDebitById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');
    // 旧版转账记录（无 transfer_group_id）拦截
    if (original.pay_type === '转账' && !original.transfer_group_id) {
      throw new Error('该转账流水为早期旧数据，缺少转账关联信息，无法自动冲正。如需撤销请手动创建相反方向的收支记录');
    }

    const reverseDirection = original.direction === 0 ? 1 : 0;

    // 1. 创建冲正流水
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, reverseDirection, original.category_id,
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `冲正：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(`[借记卡冲正] ${original.card_alias || original.card_id} ${original.direction === 0 ? '支出' : '收入'} ${original.amount} -> 冲正${reverseDirection === 1 ? '收入' : '支出'}，原流水已标记删除`);

    return this.findById(id, userId);
  }

  /**
   * 信用卡消费冲正
   * 原支出 -> 冲正收入 + 恢复账单额度
   */
  static async reverseCreditExpenseById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');
    // 旧版转账记录（无 transfer_group_id）拦截
    if (original.pay_type === '转账' && !original.transfer_group_id) {
      throw new Error('该转账流水为早期旧数据，缺少转账关联信息，无法自动冲正。如需撤销请手动创建相反方向的收支记录');
    }

    // 检查该卡是否有未撤销的还款记录
    // TODO 暂时拦截
    const [repayRows] = await db.execute(
      `SELECT id FROM card_repay WHERE card_id = ? AND is_deleted = 0 LIMIT 1`,
      [original.card_id]
    );
    if (repayRows.length > 0) {
      throw new Error('该卡有还款记录，请先撤销还款后再冲正此笔交易');
    }

    // 1. 创建冲正流水
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, 1, original.category_id,
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `冲正：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    // 恢复账单额度
    await CardBill.rollbackExpense(original.card_id, userId, original.amount, original.trans_date);
    
    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(`[信用卡消费冲正] ${original.card_alias || original.card_id} 支出${original.amount} -> 冲正收入 + 恢复账单额度，原流水已标记删除`);

    return this.findById(id, userId);
  }

  /**
   * 信用卡还款撤销冲正
   * 原还款流水 -> 冲正收入（恢复余额）+ 软删除card_repay + 重建账单
   */
  static async reverseCreditRepayById(id, userId, reverseRemark) {
    const now = String(Date.now());

    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');
    if (original.category_id !== 'CATEGORY_REPAY') {
      throw new Error('该接口仅支持信用卡还款流水');
    }

    // 1. 创建冲正流水（收入方向，恢复余额）
    await db.execute(`
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
       exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `, [
      idUtils.billId(), userId, 1, original.category_id,  // direction=1 收入，恢复余额
      original.pay_type, original.pay_method, original.amount, original.currency,
      original.exchange_rate, now.substring(0, 10),
      reverseRemark || `还款撤销：${original.remark || '交易撤销'}`,
      original.card_id, now, now, original.id
    ]);

    // 2. 标记原流水为已删除
    await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
      [now, id]
    );

    // 3. 查询并软删除 card_repay 记录
    const [repayRows] = await db.execute(
      'SELECT * FROM card_repay WHERE account_id = ? AND is_deleted = 0 LIMIT 1',
      [id]
    );

    if (repayRows.length === 0) {
      throw new Error('未找到关联的还款记录');
    }

    const repayRecord = repayRows[0];
    const creditCardId = repayRecord.card_id; // 真正的信用卡ID

    await db.execute(
      'UPDATE card_repay SET is_deleted = 1, update_time = ? WHERE id = ?',
      [now, repayRecord.id]
    );

    console.log(`[信用卡还款撤销] card_repay ${repayRecord.id} 已软删除，卡片=${creditCardId}`);

    // 4. 重建账单（确保账单数据一致性）
    await CardBill.rebuildBillFromAccount(creditCardId, userId);

    await AccountSettlement.syncBalanceSnapshot(creditCardId, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return this.findById(id, userId);
  }

  /**
   * 根据 transfer_group_id 查找同组转账记录
   */
  static async findByTransferGroup(transferGroupId, userId) {
    const query = `
      SELECT 
        a.id, a.user_id, a.direction, a.category_id,
        a.pay_type, a.pay_method, a.account_type, a.amount, a.currency, a.exchange_rate,
        a.trans_date, a.remark, a.card_id, a.create_time, a.update_time, a.reversed_id, a.transfer_group_id
      FROM ${this.tableName} a
      WHERE a.transfer_group_id = ? AND a.user_id = ? AND a.is_deleted = 0
      ORDER BY a.direction ASC
    `;
    const [rows] = await db.execute(query, [transferGroupId, userId]);
    return rows;
  }

  /**
   * 转账冲正（自转 / 提现）
   * 校验：
   *   1. 该流水必须是转账类型 (pay_type === '转账')
   *   2. 收款账户余额必须充足以支持冲正退款
   * 处理：
   *   - 将款项退回原转出账户（创建方向反向的记录）
   *   - 同步扣减收款账户余额（创建方向反向的记录）
   *   - 两条原流水均标记为已删除
   */
  static async reverseTransferById(id, userId, reverseRemark) {
    const now = String(Date.now());

    // 1. 查询当前流水
    const [rows] = await db.execute(
      `SELECT a.*, cb.card_type, cb.alias as card_alias, cb.last4_no
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const current = rows[0];
    if (!current) throw new Error('原流水不存在或已被删除');
    if (current.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');

    // 校验转账类型
    if (current.pay_type !== '转账') {
      throw new Error('该接口仅支持转账类型的流水');
    }
    if (!current.transfer_group_id) {
      throw new Error('该流水不属于转账组，无法执行转账冲正');
    }

    // 2. 查询同组所有未删除的记录
    const groupRecords = await this.findByTransferGroup(current.transfer_group_id, userId);

    // 找到支出方（direction=0）和收入方（direction=1）
    const expenseRecord = groupRecords.find(r => r.direction === 0);
    const incomeRecord = groupRecords.find(r => r.direction === 1);

    if (!expenseRecord || !incomeRecord) {
      throw new Error('未找到完整的转账关联记录（缺少支出或收入方），无法冲正');
    }

    // 如果用户点的是某一条已经被冲正的，已在前面的 reversed_id 检查中拦截
    // 检查同组的另一条是否也被冲正了（通过去数据库检查）
    const [pairRows] = await db.execute(
      `SELECT id FROM ${this.tableName}
       WHERE id IN (?, ?) AND is_deleted = 1`,
      [expenseRecord.id, incomeRecord.id]
    );
    // pairRows 中可能包含被标记删除的记录，如果另一条已经被删除说明已被冲正

    // 3. 校验收款账户（收入方）余额是否充足以支持退款
    // 退款方向：从收入方卡扣钱退回给支出方卡
    const receiverCardId = incomeRecord.card_id;    // 收款卡
    const senderCardId = expenseRecord.card_id;     // 原转出卡

    const settlementResult = await AccountSettlement.validate({
      card_id: receiverCardId,
      user_id: userId,
      direction: 0, // 从收款方扣款（支出）
      amount: current.amount,
      exchange_rate: current.exchange_rate || 1,
      currency: current.currency || 'CNY',
    });

    if (!settlementResult.valid) {
      throw new Error(`收款账户余额不足，无法执行冲正退款：${settlementResult.message}`);
    }

    // 4. 在事务中执行冲正
    const conn = await db.getPool().getConnection();
    try {
      await conn.beginTransaction();

      const nowTs = String(Date.now());

      // 4a. 反转支出方记录（原支出 → 冲正收入，退款给原转出卡）
      const reverseExpenseId = idUtils.billId();
      await conn.execute(`
        INSERT INTO ${this.tableName} 
        (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
         exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
      `, [
        reverseExpenseId, userId, 1, // direction=1 收入（退款）
        expenseRecord.category_id, expenseRecord.pay_type, expenseRecord.pay_method,
        expenseRecord.amount, expenseRecord.currency, expenseRecord.exchange_rate,
        nowTs.substring(0, 10),
        reverseRemark || `转账冲正：${expenseRecord.remark || '转出退款'}`,
        senderCardId, nowTs, nowTs, expenseRecord.id
      ]);

      // 4b. 反转收入方记录（原收入 → 冲正支出，扣回收款）
      const reverseIncomeId = idUtils.billId();
      await conn.execute(`
        INSERT INTO ${this.tableName} 
        (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
         exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
      `, [
        reverseIncomeId, userId, 0, // direction=0 支出（扣回）
        incomeRecord.category_id, incomeRecord.pay_type, incomeRecord.pay_method,
        incomeRecord.amount, incomeRecord.currency, incomeRecord.exchange_rate,
        nowTs.substring(0, 10),
        reverseRemark || `转账冲正：${incomeRecord.remark || '转入扣回'}`,
        receiverCardId, nowTs, nowTs, incomeRecord.id
      ]);

      // 4c. 标记两条原流水为已删除
      await conn.execute(
        `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id IN (?, ?)`,
        [nowTs, expenseRecord.id, incomeRecord.id]
      );

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw new Error(`转账冲正失败：${e.message}`);
    } finally {
      conn.release();
    }

    // 5. 同步双方余额
    const balanceCards = new Set([senderCardId, receiverCardId]);
    for (const cardId of balanceCards) {
      await AccountSettlement.syncBalanceSnapshot(cardId, userId);
    }

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    // 5. 同步软删除 account_transfer 记录
    try {
      await db.execute(
        `UPDATE account_transfer SET is_deleted = 1 WHERE id = ? AND user_id = ?`,
        [current.transfer_group_id, userId]
      );
    } catch (e) {
      console.warn(`[转账冲正] account_transfer 更新失败（可能不存在）:`, e.message);
    }

    console.log(`[转账冲正] ${senderCardId}→${receiverCardId} 金额${current.amount}，转账已撤销`);

    return this.findById(id, userId);
  }

  /**
   * 获取转账明细列表（含自动同步）
   */
  static async getTransferList(userId, page = 1, limit = 20, yearMonth) {
    const now = String(Date.now());

    // 1. 查出所有未配对的转账记录（无 transfer_group_id），不限量自动配对
    const [unpairedRows] = await db.execute(`
      SELECT id, card_id, direction, amount, trans_date, remark
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 AND pay_type = '转账' AND transfer_group_id IS NULL
      ORDER BY create_time DESC
    `, [userId]);

    const pairMap = {};
    for (const row of unpairedRows) {
      const key = `${Number(row.amount).toFixed(2)}_${row.trans_date}`;
      if (!pairMap[key]) pairMap[key] = [];
      pairMap[key].push(row);
    }

    for (const key of Object.keys(pairMap)) {
      const rows = pairMap[key];
      const expense = rows.find((r) => r.direction === 0);
      const income = rows.find((r) => r.direction === 1);
      if (!expense || !income) continue;

      const groupId = expense.id;
      await db.execute(
        `UPDATE ${this.tableName} SET transfer_group_id = ? WHERE id IN (?, ?) AND user_id = ?`,
        [groupId, expense.id, income.id, userId]
      );
      try {
        await db.execute(`
          INSERT INTO account_transfer (id, user_id, from_card_id, to_card_id, amount, trans_date, remark, create_time, is_deleted)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
          ON DUPLICATE KEY UPDATE is_deleted = 0, from_card_id = VALUES(from_card_id), to_card_id = VALUES(to_card_id)
        `, [
          groupId, userId, expense.card_id, income.card_id,
          expense.amount, expense.trans_date, expense.remark || '转账', now
        ]);
        console.log(`[转账同步] 自动配对 account_transfer: ${groupId}`);
      } catch (e) {
        console.warn(`[转账同步] 配对写入失败 ${groupId}:`, e.message);
      }
    }

    // 2. 处理已有 transfer_group_id 但未同步到 account_transfer 的记录
    const [groupedRows] = await db.execute(`
      SELECT DISTINCT transfer_group_id
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 AND pay_type = '转账' AND transfer_group_id IS NOT NULL
    `, [userId]);
    const allGroupIds = groupedRows.map((r) => r.transfer_group_id);

    if (allGroupIds.length > 0) {
      const placeholders = allGroupIds.map(() => '?').join(',');
      const [existingTransfers] = await db.execute(
        `SELECT id FROM account_transfer WHERE id IN (${placeholders}) AND user_id = ? AND is_deleted = 0`,
        [...allGroupIds, userId]
      );
      const existingIds = new Set(existingTransfers.map((r) => r.id));

      for (const groupId of allGroupIds) {
        if (existingIds.has(groupId)) continue;

        const [reversedCheck] = await db.execute(
          `SELECT COUNT(*) as cnt FROM ${this.tableName}
           WHERE transfer_group_id = ? AND user_id = ? AND is_deleted = 1 AND reversed_id IS NOT NULL`,
          [groupId, userId]
        );
        if (reversedCheck[0].cnt > 0) continue;

        const [pairRows] = await db.execute(
          `SELECT id, card_id, direction, amount, trans_date, remark FROM ${this.tableName}
           WHERE transfer_group_id = ? AND user_id = ? AND is_deleted = 0
           ORDER BY direction ASC`,
          [groupId, userId]
        );
        const expense = pairRows.find((r) => r.direction === 0);
        const income = pairRows.find((r) => r.direction === 1);
        if (!expense || !income) continue;

        try {
          await db.execute(`
            INSERT INTO account_transfer (id, user_id, from_card_id, to_card_id, amount, trans_date, remark, create_time, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE is_deleted = 0, from_card_id = VALUES(from_card_id), to_card_id = VALUES(to_card_id)
          `, [
            groupId, userId, expense.card_id, income.card_id,
            expense.amount, expense.trans_date, expense.remark || '转账', now
          ]);
          console.log(`[转账同步] 自动补全 account_transfer: ${groupId}`);
        } catch (e) {
          console.warn(`[转账同步] 写入失败 ${groupId}:`, e.message);
        }
      }
    }

    // 3. 统计总数（从 account_transfer 表）
    let countSql = 'SELECT COUNT(*) as total FROM account_transfer WHERE user_id = ? AND is_deleted = 0';
    const countParams = [userId];
    if (yearMonth) {
      countSql += ' AND (trans_date LIKE ? OR trans_date REGEXP ?)';
      countParams.push(yearMonth + '%', '^[0-9]+$');
    }
    const [countResult] = await db.execute(countSql, countParams);
    const total = countResult[0].total;

    // 4. 分页获取最终数据
    const offset = (page - 1) * limit;
    let listSql = 'SELECT * FROM account_transfer WHERE user_id = ? AND is_deleted = 0';
    const listParams = [userId];
    if (yearMonth) {
      listSql += ' AND (trans_date LIKE ? OR trans_date REGEXP ?)';
      listParams.push(yearMonth + '%', '^[0-9]+$');
    }
    listSql += ' ORDER BY trans_date DESC, create_time DESC LIMIT ? OFFSET ?';
    listParams.push(limit, offset);
    const [finalList] = await db.execute(listSql, listParams);

    // 5. 标准化 trans_date：时间戳转 YYYY-MM-DD
    const normalized = finalList.map((row) => {
      const td = row.trans_date;
      if (td && !/^\d{4}-\d{2}-\d{2}/.test(td)) {
        const ts = Number(td);
        if (!isNaN(ts) && ts > 1000000000) {
          const d = new Date(ts > 1000000000000 ? ts : ts * 1000);
          const y = d.getFullYear();
          const m = String(d.getMonth() + 1).padStart(2, '0');
          const day = String(d.getDate()).padStart(2, '0');
          row.trans_date = `${y}-${m}-${day}`;
        }
      }
      return row;
    });

    return { list: normalized, total };
  }

  /**
   * 获取本月收支统计
   */
  static async getMonthStats(userId, year, month) {
    // 计算本月的开始和结束日期
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 查询时计算人民币价值：CNY 直接用，外币用 amount * exchange_rate / 100
    // 排除信用卡还款（CATEGORY_REPAY），因为它不算支出而是账单还款
    const query = `
      SELECT 
        direction,
        SUM(CASE WHEN currency = 'CNY' THEN amount ELSE ROUND(amount * exchange_rate / 100, 2) END) as total,
        COUNT(*) as count
      FROM ${this.tableName}
      WHERE user_id = ? AND is_deleted = 0 
        AND trans_date >= ? AND trans_date < ?
        AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
      GROUP BY direction
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    let income = 0;
    let expense = 0;
    let incomeCount = 0;
    let expenseCount = 0;

    rows.forEach(row => {
      if (row.direction === 1) {
        income = parseFloat(row.total) || 0;
        incomeCount = row.count;
      } else if (row.direction === 0) {
        expense = parseFloat(row.total) || 0;
        expenseCount = row.count;
      }
    });

    return {
      year,
      month,
      income,
      expense,
      balance: income - expense,
      incomeCount,
      expenseCount
    };
  }

  /**
   * 获取全量统计（总资产、卡片数量、信用卡欠款等）
   */
  static async getAllStats(userId) {
    const now = new Date();
    const year = parseInt(req?.query?.year) || now.getFullYear();
    const month = parseInt(req?.query?.month) || now.getMonth() + 1;

    // 计算本月日期范围
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 1. 计算总资产 = 所有借记卡/余额账户余额总和 + 信用卡溢缴款
    // 从 account_balance 汇总（排除信用卡）
    const [balanceRows] = await db.execute(
      `SELECT COALESCE(SUM(balance), 0) as total 
       FROM account_balance 
       WHERE user_id = ? AND is_deleted = 0`,
      [userId]
    );
    const totalBalance = parseFloat(balanceRows[0]?.total) || 0;

    // 从 card_bill 统计信用卡溢缴款（溢缴款 = 可用额度 - 额度）
    const [creditBillRows] = await db.execute(
      `SELECT COALESCE(SUM(avail_limit - credit_limit - temp_limit), 0) as overflow 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditOverflow = parseFloat(creditBillRows[0]?.overflow) || 0;
    const totalAsset = totalBalance + Math.max(0, creditOverflow);

    // 2. 银行卡数量（排除虚拟账户）
    const [bankCardRows] = await db.execute(
      `SELECT card_type, COUNT(*) as count 
       FROM card_base 
       WHERE user_id = ? AND is_deleted = 0 AND id NOT IN ('xxxx', 'yyyy')
       GROUP BY card_type`,
      [userId]
    );
    let bankCardCount = 0;
    let creditCardCount = 0;
    bankCardRows.forEach(row => {
      if (row.card_type === 'credit') {
        creditCardCount = row.count;
      } else {
        bankCardCount += row.count;
      }
    });

    // 3. 信用卡代还金额（所有未还账单汇总）
    const [repayRows] = await db.execute(
      `SELECT COALESCE(SUM(need_repay), 0) as total 
       FROM card_bill cb
       LEFT JOIN card_base c ON cb.card_id = c.id
       WHERE cb.user_id = ? AND cb.is_deleted = 0 AND c.card_type = 'credit'`,
      [userId]
    );
    const creditCardDebt = parseFloat(repayRows[0]?.total) || 0;

    // 4. 本月收支统计
    const [monthStatsRows] = await db.execute(
      `SELECT 
        direction,
        SUM(CASE WHEN currency = 'CNY' THEN amount ELSE ROUND(amount * exchange_rate / 100, 2) END) as total
       FROM ${this.tableName}
       WHERE user_id = ? AND is_deleted = 0 
         AND trans_date >= ? AND trans_date < ?
         AND NOT (direction = 0 AND category_id = 'CATEGORY_REPAY')
       GROUP BY direction`,
      [userId, startDate, endDate]
    );

    let monthIncome = 0;
    let monthExpense = 0;
    monthStatsRows.forEach(row => {
      if (row.direction === 1) {
        monthIncome = parseFloat(row.total) || 0;
      } else if (row.direction === 0) {
        monthExpense = parseFloat(row.total) || 0;
      }
    });
    const monthBalance = monthIncome - monthExpense;

    return {
      totalAsset,       // 总资产
      bankCardCount,    // 银行卡数量
      creditCardCount,  // 信用卡数量
      creditCardDebt,   // 信用卡代还金额（未还账单）
      monthIncome,      // 本月收入
      monthExpense,     // 本月支出
      monthBalance      // 本月结余
    };
  }

  /**
   * 按分类统计收支（人民币价值）
   */
  static async getStatsByCategory(userId, year, month) {
    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const nextMonth = month === 12 ? 1 : month + 1;
    const nextYear = month === 12 ? year + 1 : year;
    const endDate = `${nextYear}-${String(nextMonth).padStart(2, '0')}-01`;

    // 查询时计算人民币价值
    const query = `
      SELECT 
        a.direction,
        a.category_id,
        c.name as category_name,
        SUM(CASE WHEN a.currency = 'CNY' THEN a.amount ELSE ROUND(a.amount * a.exchange_rate / 100, 2) END) as total,
        COUNT(*) as count
      FROM ${this.tableName} a
      LEFT JOIN bus_category c ON a.category_id = c.id
      WHERE a.user_id = ? AND a.is_deleted = 0 
        AND a.trans_date >= ? AND a.trans_date < ?
      GROUP BY a.direction, a.category_id
      ORDER BY a.direction, total DESC
    `;
    const [rows] = await db.execute(query, [userId, startDate, endDate]);

    const income = [];
    const expense = [];

    rows.forEach(row => {
      const item = {
        categoryId: row.category_id,
        categoryName: row.category_name || '未分类',
        total: parseFloat(row.total) || 0,
        count: row.count
      };
      if (row.direction === 1) {
        income.push(item);
      } else {
        expense.push(item);
      }
    });

    return { income, expense };
  }
}

module.exports = Account;
