const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const CardBill = require('./bill');
// 延迟加载，避免与 CreditCore 形成循环依赖

/**
 * 卡片模型 - 对应数据库 card_base 表
 */
class Card {
  static tableName = 'card_base';

  // 虚拟账户类型
  static VIRTUAL_CARDS = {
    CASH: 'xxxx',     // 现金
    BALANCE: 'yyyy',  // 余额（微信+支付宝）
  };

  /**
   * 初始化虚拟账户（现金、余额）
   * 在 card_base 表中创建虚拟账户记录（如果不存在）
   * 虚拟银行分类写入 bus_category 表 (type='bank')
   */
  static async initVirtualCards(userId) {
    const now = String(Date.now());
    const results = [];

    // 1. 初始化虚拟银行分类到 bus_category 表
    const BusBank = require('../../category/model/bank');
    await BusBank.initVirtualBanks(userId);

    // 2. 创建/更新虚拟卡片（只创建不存在的，不重复更新）
    for (const [type, cardId] of Object.entries(this.VIRTUAL_CARDS)) {
      const alias = type === 'CASH' ? '现金' : '余额';
      const cardType = type === 'CASH' ? 'virtual_cash' : 'virtual_balance';
      const bankId = cardId; // xxxx 或 yyyy

      // 检查是否已存在
      const [existing] = await db.execute(
        'SELECT id, bank_id FROM card_base WHERE id = ? AND user_id = ?',
        [cardId, userId]
      );

      if (existing.length === 0) {
        // 创建新记录 - 所有字段都用占位符
        const query = `
          INSERT INTO card_base (
            id, user_id, bank_id, card_type, card_level, main_sub, card_org,
            card_length, last4_no, card_bin, credit_limit, temp_limit,
            alias, card_img, open_date, expire_date,
            bill_day, repay_day, currency, status, is_default, is_hide, sort,
            tag, remark, color, annual_fee, fee_free_rule, source_from,
            points_rate, create_time, update_time, is_deleted
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `;
        await db.execute(query, [
          cardId, userId, bankId, cardType, '000', '000', '000',
          '0', '0000', '000', 0, 0,
          alias, '', '0000-00-00', '0000-00-00',
          0, 0, 'CNY', '正常', 0, 0, 99,
          '', '', '#999999', 0, '', '系统',
          1, now, now, 0
        ]);
        console.log(`[虚拟账户] 创建 ${alias} (${cardId})`);
      } else if (existing[0].bank_id !== bankId) {
        // 仅当 bank_id 不匹配时才更新
        await db.execute(
          'UPDATE card_base SET bank_id = ?, update_time = ? WHERE id = ? AND user_id = ?',
          [bankId, now, cardId, userId]
        );
        console.log(`[虚拟账户] 修复 bank_id: ${existing[0].bank_id} -> ${bankId}`);
      }

      // 获取并返回虚拟账户信息
      const [rows] = await db.execute(
        'SELECT * FROM card_base WHERE id = ? AND user_id = ?',
        [cardId, userId]
      );
      if (rows[0]) {
        results.push(rows[0]);
      }
    }

    return results;
  }

  /**
   * 获取卡片列表 - 根据卡类型返回不同字段
   * 自动初始化虚拟账户（如果不存在）
   */
  static async findAll(userId, filters = {}) {
    // 确保虚拟账户已初始化
    await this.initVirtualCards(userId);

    let whereClause = `WHERE ${this.tableName}.user_id = ? AND ${this.tableName}.is_deleted = 0`;
    const params = [userId];

    if (filters.cardType) {
      whereClause += ` AND ${this.tableName}.card_type = ?`;
      params.push(filters.cardType);
    }

    if (filters.isHide !== undefined) {
      whereClause += ` AND ${this.tableName}.is_hide = ?`;
      params.push(filters.isHide);
    }

    // 根据卡类型返回不同字段
    // 银行名：LEFT JOIN bus_category(type='bank')，供前端 `alias || 银行名(尾号)` 兜底展示
    const bankJoin = `LEFT JOIN bus_category b ON b.id = ${this.tableName}.bank_id AND b.type = 'bank' AND b.is_deleted = 0`;
    const bankSelect = `, b.name AS bank_name`;

    if (filters.cardType === 'credit') {
      // 信用卡返回完整字段
      const query = `
        SELECT ${this.tableName}.id, ${this.tableName}.user_id, ${this.tableName}.bank_id, ${this.tableName}.card_type, ${this.tableName}.card_bin, ${this.tableName}.card_length, ${this.tableName}.last4_no, ${this.tableName}.alias,
               ${this.tableName}.open_date, ${this.tableName}.expire_date, ${this.tableName}.card_org, ${this.tableName}.tag, ${this.tableName}.bill_day, ${this.tableName}.repay_day,
               ${this.tableName}.credit_limit, ${this.tableName}.temp_limit, ${this.tableName}.points_rate, ${this.tableName}.share_pool_id,
               ${this.tableName}.card_img, ${this.tableName}.color${bankSelect}
        FROM ${this.tableName}
        ${bankJoin}
        ${whereClause}
        ORDER BY ${this.tableName}.is_default DESC, ${this.tableName}.sort ASC, ${this.tableName}.create_time DESC
      `;
      const [rows] = await db.execute(query, params);
      return rows;
    } else if (filters.cardType === 'debit') {
      // 借记卡返回基础字段
      const query = `
        SELECT ${this.tableName}.id, ${this.tableName}.user_id, ${this.tableName}.bank_id, ${this.tableName}.card_type, ${this.tableName}.card_bin, ${this.tableName}.card_length, ${this.tableName}.last4_no, ${this.tableName}.alias,
               ${this.tableName}.open_date, ${this.tableName}.expire_date, ${this.tableName}.card_org, ${this.tableName}.tag, ${this.tableName}.card_img, ${this.tableName}.color${bankSelect}
        FROM ${this.tableName}
        ${bankJoin}
        ${whereClause}
        ORDER BY ${this.tableName}.is_default DESC, ${this.tableName}.sort ASC, ${this.tableName}.create_time DESC
      `;
      const [rows] = await db.execute(query, params);
      return rows;
    } else {
      // 不筛选时默认返回信用卡字段
      const query = `
        SELECT ${this.tableName}.id, ${this.tableName}.user_id, ${this.tableName}.bank_id, ${this.tableName}.card_type, ${this.tableName}.card_bin, ${this.tableName}.card_length, ${this.tableName}.last4_no, ${this.tableName}.alias,
               ${this.tableName}.open_date, ${this.tableName}.expire_date, ${this.tableName}.card_org, ${this.tableName}.tag, ${this.tableName}.bill_day, ${this.tableName}.repay_day,
               ${this.tableName}.credit_limit, ${this.tableName}.temp_limit, ${this.tableName}.points_rate, ${this.tableName}.share_pool_id,
               ${this.tableName}.card_img, ${this.tableName}.color${bankSelect}
        FROM ${this.tableName}
        ${bankJoin}
        ${whereClause}
        ORDER BY ${this.tableName}.is_default DESC, ${this.tableName}.sort ASC, ${this.tableName}.create_time DESC
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
    pointsRate,
    sharePoolId
  }) {
    const id = idUtils.billId();
    const now = String(Date.now());

    // C6：sharePoolId 必须验证池归属，禁止绕过 CreditPool 管理链路直接写入不存在的池
    if (sharePoolId) {
      const CreditPool = require('./pool');
      const pool = await CreditPool.findById(sharePoolId, userId);
      if (!pool) throw new Error('共享池不存在');
    }

    const query = `
      INSERT INTO ${this.tableName} (
        id, user_id, bank_id, card_type, card_level, main_sub, card_org,
        card_length, last4_no, card_bin, credit_limit, temp_limit,
        alias, card_img, open_date, expire_date,
        bill_day, repay_day, currency, status, is_default, is_hide, sort,
        tag, remark, color, annual_fee, fee_free_rule, source_from,
        share_pool_id, points_rate, create_time, update_time, is_deleted
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
      sharePoolId || null,
      pointsRate || 1,
      now,
      now
    ]);

    // 信用卡：立即初始化当前月账单
    if (cardType === 'credit') {
      try {
        const CardBill = require('./bill');
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
    // console.log('[Card.update] 接收到的 updates:', JSON.stringify(updates));
    
    // 获取原始卡片数据
    const original = await this.findById(id, userId);
    // P1-3 审计：禁止 credit ⇄ debit 类型切换——切换会让历史流水被重新解读（信用债务消失/借记支出被当信用负债），
    // 如需变更类型，必须先冲正/迁移该卡全部历史流水后重新创建。
    if (original && updates.cardType !== undefined && updates.cardType !== original.card_type) {
      throw new Error('不允许修改卡片类型（信用卡/借记卡不可互相切换）。如需变更，请删除卡片后重新创建');
    }
    const billDayChanged = updates.billDay !== undefined && original && updates.billDay !== original.bill_day;
    const repayDayChanged = updates.repayDay !== undefined && original && updates.repayDay !== original.repay_day;
    const isCreditCard = original && original.card_type === 'credit';

    // R6：信用卡 billDay/repayDay 必须 1-31（null/0 会让 REPAY_DATE_SQL 与 #computeMonths 计算失效、
    //     MySQL STR_TO_DATE 返回 NULL 导致逾期判断与还款日展示异常）
    if (isCreditCard) {
      if (updates.billDay !== undefined) {
        const bd = Number(updates.billDay);
        if (!Number.isInteger(bd) || bd < 1 || bd > 31) {
          throw new Error('信用卡账单日必须在 1-31 之间');
        }
      }
      if (updates.repayDay !== undefined) {
        const rd = Number(updates.repayDay);
        if (!Number.isInteger(rd) || rd < 1 || rd > 31) {
          throw new Error('信用卡还款日必须在 1-31 之间');
        }
      }
    }

    // 信用卡修改账单日/还款日：检查是否是新卡
    if (isCreditCard && (billDayChanged || repayDayChanged)) {
      const hasAccountRecords = await this._hasAccountRecords(id, userId);
      const hasRepayRecords = await this._hasRepayRecords(id);
      
      if (hasAccountRecords || hasRepayRecords) {
        throw new Error('该卡片已有收支记录或还款记录，不允许修改账单日/还款日。如需修改，请删除相关记录后重新创建卡片。');
      }
      
      // console.log('[Card.update] 新卡检测通过，允许修改账单日/还款日');
    }

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
      sourceFrom: 'source_from',
      creditLimit: 'credit_limit',
      tempLimit: 'temp_limit',
      pointsRate: 'points_rate'
      // C6：sharePoolId 不再在 fieldMap 中直写——必须走 CreditPool.assignCard
      //     （归属校验 + 两侧/全池重算），否则绕过共享池管理链路。
    };

    // C6：检测池变更（与原值不同才算），交由 CreditPool.assignCard 处理
    const poolChange = updates.sharePoolId !== undefined && original &&
      (updates.sharePoolId || null) !== (original.share_pool_id || null);

    Object.keys(updates).forEach(key => {
      if (key === 'sharePoolId') return; // 跳过，走 assignCard
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

    if (fields.length === 0 && !poolChange) {
      // console.log('[Card.update] 没有需要更新的字段，fields:', fields);
      return this.findById(id, userId);
    }

    if (fields.length > 0) {
      fields.push('update_time = ?');
      params.push(now);
      params.push(id, userId);

      const query = `
        UPDATE ${this.tableName}
        SET ${fields.join(', ')}
        WHERE id = ? AND user_id = ? AND is_deleted = 0
      `;
      await db.execute(query, params);
    }

    // C6：池变更必须走 CreditPool.assignCard（归属校验 + 事务化更新 + 旧池/新池全池重算）
    if (poolChange) {
      const CreditPool = require('./pool');
      await CreditPool.assignCard(id, userId, updates.sharePoolId || null);
    }

    // 修改账单日/还款日/额度后，统一由 CreditCore 重算账单
    // （sharePoolId 变更已由 assignCard 完成全池重算，不在此重复触发）
    const limitChanged =
      updates.creditLimit !== undefined ||
      updates.tempLimit !== undefined ||
      updates.pointsRate !== undefined;

    if (isCreditCard && (billDayChanged || repayDayChanged || limitChanged)) {
      const CreditCore = require('../core/CreditCore');
      setImmediate(() => {
        CreditCore.syncCardBills(id, userId).catch(err => {
          console.error(`[账单同步] 失败 (cardId=${id}):`, err.message);
        });
      });
    }

    return this.findById(id, userId);
  }

  /**
   * 批量更新排序：接收 [{ id, sort }]，单事务内逐个 UPDATE。
   * 用于前端拖拽/上下移动后的整列 1-N 重排，一次请求完成。
   * @param {string} userId
   * @param {Array<{id:string, sort:number}>} items
   */
  static async updateSortBatch(userId, items) {
    if (!Array.isArray(items) || items.length === 0) return;
    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();
      for (const it of items) {
        if (!it || !it.id) continue;
        const sort = Number(it.sort);
        if (!Number.isFinite(sort)) continue;
        await conn.execute(
          `UPDATE ${this.tableName} SET sort = ?, update_time = ? WHERE id = ? AND user_id = ? AND is_deleted = 0`,
          [sort, String(Date.now()), it.id, userId]
        );
      }
      await conn.commit();
    } catch (err) {
      await conn.rollback();
      throw err;
    } finally {
      conn.release();
    }
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
   * 检查卡片是否有收支记录（排除冲正）
   * @returns {Promise<boolean>}
   */
  static async _hasAccountRecords(cardId, userId) {
    const [rows] = await db.execute(
      `SELECT COUNT(*) as cnt FROM account 
       WHERE card_id = ? AND user_id = ? AND is_deleted = 0 AND reversed_id IS NULL
       LIMIT 1`,
      [cardId, userId]
    );
    return rows[0].cnt > 0;
  }

  /**
   * 检查卡片是否有还款记录
   * @returns {Promise<boolean>}
   */
  static async _hasRepayRecords(cardId) {
    const [rows] = await db.execute(
      `SELECT COUNT(*) as cnt FROM card_repay 
       WHERE card_id = ? AND is_deleted = 0
       LIMIT 1`,
      [cardId]
    );
    return rows[0].cnt > 0;
  }
}

module.exports = Card;
