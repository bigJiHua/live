const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const CardBill = require('./bill');
const CreditCore = require('../core/CreditCore');
const Account = require('../../account/model');
const DebitAccount = require('../../account/model/debit');
const AccountBalance = require('../../account/model/balance');
const AccountSettlement = require('../../account/service/settlement');
const AssetSnapshot = require('../../asset/model/snapshot');

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
   * 执行还款核心逻辑（单卡）
   * @param {Object} params - 还款参数
   * @param {string} params.userId - 用户ID
   * @param {string} params.cardId - 卡片ID
   * @param {string} params.billId - 账单ID（可选）
   * @param {number} params.repayAmount - 还款金额
   * @param {string} params.repayMethod - 还款方式：balance(余额)/bank_card(银行卡)/cash(现金)
   * @param {string} params.repayMethodCardId - 还款方式使用的卡ID（bank_card时）
   * @param {string} params.repayTime - 还款时间
   * @param {string} params.remark - 备注
   */
  static async executeRepay(params) {
    const { userId, billId } = params;
    const conn = await db.getConnection();
    let repayId;
    try {
      await conn.beginTransaction();
      repayId = await this.#execRepayTx(conn, params);
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      console.error('[还款事务] 回滚:', e.message);
      throw e;
    } finally {
      conn.release();
    }

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    // 返回最新账单快照（由 CreditCore 计算）
    let newUsedLimit = 0;
    let newAvailLimit = 0;
    if (billId) {
      const synced = await CardBill.findById(billId, userId);
      if (synced) {
        newUsedLimit = parseFloat(synced.used_limit) || 0;
        newAvailLimit = parseFloat(synced.avail_limit) || 0;
      }
    }
    return {
      repayId,
      repayAmount: parseFloat(params.repayAmount) || 0,
      actualRepayToBill: parseFloat(params.repayAmount) || 0,
      newUsedLimit,
      newAvailLimit
    };
  }

  /**
   * 事务内单卡还款核心（供 executeRepay / executeMergeRepay 复用）
   * 同一事务连接内完成：锁卡 → 扣来源款 → 写 card_repay → 账单重算。
   * @param {object} conn - 事务连接
   * @param {Object} params - 同 executeRepay 参数
   * @returns {Promise<string>} repayId
   */
  static async #execRepayTx(conn, {
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

    // 获取卡片信息（H6：限定 user_id，防止跨用户还款）
    const [cardRows] = await conn.execute(
      'SELECT * FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0',
      [cardId, userId]
    );
    const card = cardRows[0];
    if (!card) {
      throw new Error('卡片不存在');
    }
    if (card.card_type !== 'credit') {
      throw new Error('该卡片不是信用卡，不能执行信用卡还款');
    }
    // P2-7 审计：移除"本卡还款"(repayMethod='card')——该方式不产生来源资金扣款、account_id 为 NULL，
    // 会凭空减免信用卡负债且与撤销路径冲突。还款必须从真实来源（余额/银行卡/现金）扣款。
    if (repayMethod === 'card') {
      throw new Error('不支持"本卡还款"方式，请选择余额、银行卡或现金还款');
    }

    // 获取当前账单（如果有）（H6：限定 user_id + 校验账单归属该卡片）
    let bill = null;
    if (billId) {
      const [billRows] = await conn.execute(
        'SELECT * FROM card_bill WHERE id = ? AND user_id = ? AND is_deleted = 0',
        [billId, userId]
      );
      bill = billRows[0];
      if (!bill) throw new Error('账单不存在');
      if (bill.card_id !== cardId) throw new Error('账单与卡片不匹配，无法还款');
    }

    // ===== 验证还款来源（P0-2 审计：余额校验在事务内用 conn 重查）=====
    let sourceCardId = null; // 资金来源的card_id

    if (repayMethod === 'balance') {
      sourceCardId = 'yyyy';
    } else if (repayMethod === 'bank_card') {
      if (!repayMethodCardId) {
        throw new Error('请指定还款使用的银行卡');
      }
      // 校验还款卡类型，禁止信用卡还信用卡（H6：限定 user_id）
      const [sourceCardRows] = await conn.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0',
        [repayMethodCardId, userId]
      );
      if (!sourceCardRows[0]) {
        throw new Error('还款银行卡不存在');
      }
      const sourceCardType = sourceCardRows[0].card_type;
      if (sourceCardType === 'credit') {
        throw new Error('禁止使用信用卡还款，请选择储蓄卡或其他方式');
      }
      sourceCardId = repayMethodCardId;
    } else if (repayMethod === 'cash') {
      sourceCardId = 'xxxx';
    }

    // ===== 处理资金转移 =====
    let repayAccountId = null; // 保存还款流水ID，用于关联 card_repay

    // H9：锁信用卡行，串行化并发消费/还款（还款同样改变可用额度）
    await conn.execute(
      'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
      [cardId, userId]
    );

    // P0-2 审计：事务内锁来源卡行 + 用 conn 重查余额并校验。
    if (sourceCardId) {
      await conn.execute(
        'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [sourceCardId, userId]
      );
      const balanceInfo = await AccountSettlement.calculateBalance(sourceCardId, userId, conn);
      const sourceBalance = balanceInfo.balance;
      if (sourceBalance < repayAmountNum) {
        throw new Error(`余额不足，当前余额 ${sourceBalance}，需要 ${repayAmountNum}`);
      }

      // 1. 从还款来源扣款（生成支出流水，归属来源卡=借记卡），使用事务连接
      const repayAccount = await DebitAccount.create({
        userId,
        direction: 0, // 支出
        categoryId: 'CATEGORY_REPAY', // 还款分类（需确保存在）
        payType: '还款',
        payMethod: repayMethod === 'balance' ? '余额' : (repayMethod === 'bank_card' ? '银行卡' : '现金'),
        accountType: 'debit',
        amount: repayAmountNum,
        // C3：还款金额是人民币口径，来源流水必须记 CNY。
        //    若沿用目标信用卡 card.currency（如 USD），来源卡按 toCNY 折算会被错误缩小/放大（每100外币=人民币）。
        currency: 'CNY',
        exchangeRate: 1,
        transDate: repayTime || now.substring(0, 10),
        remark: remark || `信用卡还款至${card.alias || card.last4_no}`,
        cardId: sourceCardId
      }, conn);

      // 保存还款流水ID用于关联
      if (repayAccount && repayAccount.id) {
        repayAccountId = repayAccount.id;
      }
    }

    // 2. 同步信用卡余额快照（事务内，使用同一连接）
    await AccountSettlement.syncBalanceSnapshot(cardId, userId, conn);

    // 3. 创建还款记录
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
    }

    const query = `
      INSERT INTO ${this.tableName} (
        id, card_id, user_id, bill_id, bill_month, repay_amount,
        repay_method, repay_card_id, repay_time, remark, account_id, is_deleted, create_time, update_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
    `;

    await conn.execute(query, [
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

    // 4. 同步更新信用卡账单（事务内，使用同一连接；共享池卡自动全池扩散）
    await CreditCore.syncCardBills(cardId, userId, conn);

    return repayId;
  }

  /**
   * 信报合一合并还款：单事务内一次性结清共享池内全部卡的欠款。
   * 前提：共享池 credit_report_merged = 1。
   * 逻辑：遍历池内所有卡的全部未结清账单，逐笔在同一事务内完成扣款+记还款。
   * @param {Object} params
   * @param {string} params.userId
   * @param {string} params.poolId - 共享池ID
   * @param {string} params.repayMethod - 还款方式：balance/bank_card/cash
   * @param {string} params.repayMethodCardId - bank_card 时指定银行卡
   * @param {string} params.repayTime - 还款时间
   * @param {string} params.remark - 备注
   * @returns {Promise<{totalAmount:number, count:number, repayIds:string[], cardIds:string[]}>}
   */
  static async executeMergeRepay({ userId, poolId, repayMethod, repayMethodCardId, repayTime, remark }) {
    if (!poolId) throw new Error('共享池ID不能为空');
    if (!['balance', 'bank_card', 'cash'].includes(repayMethod)) {
      throw new Error('还款方式不合法');
    }
    if (repayMethod === 'bank_card' && !repayMethodCardId) {
      throw new Error('请指定还款银行卡');
    }

    // 1. 校验共享池存在、属于该用户、且开启信报合一
    const CreditPool = require('./pool');
    const pool = await CreditPool.findById(poolId, userId);
    if (!pool) throw new Error('共享池不存在');
    if (!Number(pool.credit_report_merged)) {
      throw new Error('该共享池未开启信报合一，无法合并还款');
    }

    // 2. 池内全部信用卡
    const [cards] = await db.execute(
      'SELECT id FROM card_base WHERE share_pool_id = ? AND user_id = ? AND is_deleted = 0',
      [poolId, userId]
    );
    if (!cards.length) throw new Error('共享池内暂无信用卡');

    // 3. 汇总池内全部未结清账单（每卡可能多个账单月）
    const debts = []; // {cardId, billId, amount}
    for (const c of cards) {
      const [bills] = await db.execute(
        `SELECT id, need_repay FROM card_bill
         WHERE card_id = ? AND user_id = ? AND is_deleted = 0 AND need_repay > 0
         ORDER BY bill_month ASC`,
        [c.id, userId]
      );
      for (const b of bills) {
        const amt = parseFloat(b.need_repay) || 0;
        if (amt > 0) debts.push({ cardId: c.id, billId: b.id, amount: amt });
      }
    }
    if (!debts.length) throw new Error('共享池内无待还欠款');
    const totalAmount = debts.reduce((s, d) => s + d.amount, 0);

    // 4. 单事务逐笔结清（原子：任一失败整体回滚）
    const conn = await db.getConnection();
    const repayIds = [];
    try {
      await conn.beginTransaction();
      for (const d of debts) {
        const rid = await this.#execRepayTx(conn, {
          userId,
          cardId: d.cardId,
          billId: d.billId,
          repayAmount: d.amount,
          repayMethod,
          repayMethodCardId,
          repayTime,
          remark: remark || `信报合一合并还款(${pool.bank_name || ''})`
        });
        repayIds.push(rid);
      }
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      console.error('[信报合一合并还款] 回滚:', e.message);
      throw e;
    } finally {
      conn.release();
    }

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return { totalAmount, count: debts.length, repayIds, cardIds: cards.map((c) => c.id) };
  }

  /** 事务内冲正一笔来源流水（创建等额反向收入 + 软删原流水，恢复资金） */
  static async #reverseSourceFlow(conn, src, userId, now, reason) {
    const reverseId = idUtils.billId();
    await conn.execute(
      `INSERT INTO account
       (id, user_id, direction, category_id, pay_type, pay_method, account_type, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`,
      [
        reverseId, userId, 1, src.category_id, src.pay_type, src.pay_method,
        src.account_type || 'debit', src.amount, src.currency, src.exchange_rate,
        now.substring(0, 10),
        `${reason}：${src.remark || '还款流水'}`,
        src.card_id, now, now, src.id
      ]
    );
    await conn.execute(
      'UPDATE account SET is_deleted = 1, update_time = ? WHERE id = ?',
      [now, src.id]
    );
    return reverseId;
  }

  /**
   * 更新还款记录
   * H5 审计：金额/还款方式变化时，必须同时调整来源资金（冲正原来源流水 + 按新值重建），
   * 否则会出现「信用卡欠款恢复，但来源卡资金仍被扣除」的错账。
   */
  static async update(id, userId, updates) {
    // 先获取原记录
    const oldRecord = await this.findById(id, userId);
    if (!oldRecord) return null;

    const now = String(Date.now());
    const validMethods = ['balance', 'bank_card', 'cash'];
    const repayAmountNum =
      updates.repayAmount !== undefined && updates.repayAmount !== null && updates.repayAmount !== ''
        ? parseFloat(updates.repayAmount)
        : null;
    // C2：编辑还款禁止 0/负数——负金额支出会反向增加来源卡余额（计算器把负支出计入收入）
    if (repayAmountNum !== null && !Number.isNaN(repayAmountNum) && repayAmountNum <= 0) {
      throw new Error('还款金额必须大于 0');
    }
    const methodChanged =
      updates.repayMethod !== undefined &&
      validMethods.includes(updates.repayMethod) &&
      updates.repayMethod !== oldRecord.repay_method;
    const amountChanged =
      repayAmountNum !== null && !Number.isNaN(repayAmountNum) &&
      repayAmountNum !== parseFloat(oldRecord.repay_amount);
    const needRebuildFund = amountChanged || methodChanged;

    if (!needRebuildFund) {
      // 仅备注/时间等字段，直接更新（repayMethod 非法时忽略，防止污染枚举）
      const fields = ['update_time = ?'];
      const params = [now];
      if (repayAmountNum !== null && !Number.isNaN(repayAmountNum)) {
        fields.push('repay_amount = ?');
        params.push(repayAmountNum);
      }
      if (updates.repayTime) {
        fields.push('repay_time = ?');
        params.push(String(updates.repayTime).substring(0, 10));
      }
      if (updates.remark !== undefined && updates.remark !== null) {
        fields.push('remark = ?');
        params.push(updates.remark);
      }
      params.push(id, userId);
      await db.execute(
        `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
        params
      );
      await CreditCore.syncCardBills(oldRecord.card_id, userId);
      return this.findById(id, userId);
    }

    const effMethod = methodChanged ? updates.repayMethod : oldRecord.repay_method;
    const effAmount = amountChanged ? repayAmountNum : parseFloat(oldRecord.repay_amount);
    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();

      // 锁信用卡行，防并发编辑
      const [lockedCard] = await conn.execute(
        'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [oldRecord.card_id, userId]
      );
      if (!lockedCard[0]) throw new Error('卡片不存在');

      // 1. 冲正原来源流水（若存在），恢复资金
      let oldSourceCardId = null;
      if (oldRecord.account_id) {
        const [srcRows] = await conn.execute(
          `SELECT * FROM account WHERE id = ? AND user_id = ? AND is_deleted = 0`,
          [oldRecord.account_id, userId]
        );
        if (srcRows[0]) {
          oldSourceCardId = srcRows[0].card_id;
          await this.#reverseSourceFlow(conn, srcRows[0], userId, now, '还款修改冲正');
        }
      }

      // 2. 按新方式重建来源流水（P2-7：本卡还款方式已移除，还款必有来源流水）
      if (effMethod === 'card') {
        throw new Error('不支持"本卡还款"方式，请选择余额、银行卡或现金还款');
      }
      let newAccountId = null;
      let newRepayCardId = oldRecord.repay_card_id;
      {
        let srcCardId = null;
        if (effMethod === 'balance') srcCardId = 'yyyy';
        else if (effMethod === 'cash') srcCardId = 'xxxx';
        else if (effMethod === 'bank_card') {
          srcCardId = updates.repayMethodCardId || oldRecord.repay_card_id;
          const [scRows] = await conn.execute(
            'SELECT card_type FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0',
            [srcCardId, userId]
          );
          if (!scRows[0]) throw new Error('还款银行卡不存在');
          if (scRows[0].card_type === 'credit') throw new Error('禁止使用信用卡还款');
        }
        if (srcCardId === oldRecord.card_id) {
          throw new Error('不能使用信用卡本身作为还款来源');
        }
        // 余额校验（用事务连接，读到含本事务冲正后的最新状态）
        const balanceInfo = await AccountSettlement.calculateBalance(srcCardId, userId, conn);
        if (balanceInfo.balance < effAmount) {
          throw new Error(`余额不足，当前 ${balanceInfo.balance}，需要 ${effAmount}`);
        }
        const newAccount = await DebitAccount.create({
          userId,
          direction: 0,
          categoryId: 'CATEGORY_REPAY',
          payType: '还款',
          payMethod: effMethod === 'balance' ? '余额' : (effMethod === 'bank_card' ? '银行卡' : '现金'),
          accountType: 'debit',
          amount: effAmount,
          currency: 'CNY',
          exchangeRate: 1,
          transDate: (updates.repayTime || oldRecord.repay_time || now.substring(0, 10)).substring(0, 10),
          remark: oldRecord.remark || '信用卡还款',
          cardId: srcCardId
        }, conn);
        newAccountId = newAccount.id;
        newRepayCardId = srcCardId;
      }

      // 3. 更新 card_repay
      const fields = ['update_time = ?'];
      const params = [now];
      if (amountChanged) { fields.push('repay_amount = ?'); params.push(repayAmountNum); }
      if (methodChanged) { fields.push('repay_method = ?'); params.push(updates.repayMethod); }
      fields.push('account_id = ?'); params.push(newAccountId);
      fields.push('repay_card_id = ?'); params.push(newRepayCardId);
      if (updates.repayTime) {
        fields.push('repay_time = ?');
        params.push(String(updates.repayTime).substring(0, 10));
      }
      if (updates.remark !== undefined && updates.remark !== null) {
        fields.push('remark = ?');
        params.push(updates.remark);
      }
      params.push(id, userId);
      await conn.execute(
        `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
        params
      );

      // 4. 重算信用卡账单 + 刷新来源卡余额快照（事务内）
      await CreditCore.syncCardBills(oldRecord.card_id, userId, conn);
      if (oldSourceCardId) {
        await AccountSettlement.syncBalanceSnapshot(oldSourceCardId, userId, conn);
      }
      if (newRepayCardId && newRepayCardId !== oldSourceCardId && newRepayCardId !== oldRecord.card_id) {
        await AccountSettlement.syncBalanceSnapshot(newRepayCardId, userId, conn);
      }

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      console.error('[更新还款] 回滚:', e.message);
      throw e;
    } finally {
      conn.release();
    }
    return this.findById(id, userId);
  }

  /**
   * 删除还款记录（软删除）
   * H5 审计：删除还款必须同时冲正原来源流水（恢复资金），
   * 否则「信用卡恢复欠款，但资金仍被扣除」。
   */
  static async delete(id, userId) {
    const record = await this.findById(id, userId);
    if (!record) return false;

    const now = String(Date.now());
    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();

      // 锁信用卡行
      await conn.execute(
        'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [record.card_id, userId]
      );

      // 1. 冲正原来源流水（恢复资金），并刷新来源卡余额快照
      let sourceCardId = null;
      if (record.account_id) {
        const [srcRows] = await conn.execute(
          `SELECT * FROM account WHERE id = ? AND user_id = ? AND is_deleted = 0`,
          [record.account_id, userId]
        );
        if (srcRows[0]) {
          sourceCardId = srcRows[0].card_id;
          await this.#reverseSourceFlow(conn, srcRows[0], userId, now, '删除还款退回');
        }
      }

      // 2. 软删 card_repay
      await conn.execute(
        `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
        [now, id, userId]
      );

      // 3. 重算信用卡账单 + 来源卡余额快照（事务内）
      await CreditCore.syncCardBills(record.card_id, userId, conn);
      if (sourceCardId) {
        await AccountSettlement.syncBalanceSnapshot(sourceCardId, userId, conn);
      }

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      console.error('[删除还款] 回滚:', e.message);
      throw e;
    } finally {
      conn.release();
    }
    return true;
  }
}

module.exports = CardRepay;
