const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const { toCNY } = require('../../../common/utils/currency');
const AccountSettlement = require('../service/settlement');
const AssetSnapshot = require('../../asset/model/snapshot');
const Account = require('./index'); // 仅用于调用只读查询方法（findById 等），不调用写入方法

/**
 * 借记卡账务模型 - 独立实现，不与信用卡共用写入方法
 *
 * 处理范围：
 * 1. 借记卡/虚拟账户（现金/余额）的收支创建
 * 2. 转账（自转/提现/对外转账）的流水创建
 * 3. 信用卡还款的支出流水创建（流水归属来源卡=借记卡）
 * 4. 借记卡冲正、转账冲正
 */
class DebitAccount {
  static tableName = 'account';

  /**
   * 创建借记卡收支记录
   * 独立实现，不与信用卡共用。处理：普通收支、转账、提现、还款支出流水。
   */
  static async create({ userId, direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId, transferGroupId }, executor = db) {
    // R5：account.card_id 为 NOT NULL，所有收支必须关联卡片（现金=xxxx / 余额=yyyy / 实体卡）
    if (!cardId) {
      throw new Error('缺少卡片ID：收支必须关联一张卡片（现金/余额为虚拟卡）');
    }
    // P1-4 审计：金额必须 > 0，否则负支出/负收入会反向篡改账户余额
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      throw new Error('收支金额必须大于 0');
    }
    const id = idUtils.billId();
    const now = String(Date.now());

    // 从 card_base 获取真实的 card_type
    let finalAccountType = '';
    if (cardId) {
      const [cardRows] = await executor.execute(
        'SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0',
        [cardId]
      );
      if (cardRows[0]?.card_type) {
        finalAccountType = cardRows[0].card_type;
      }
    }

    // 借记卡模块拦截：若传入信用卡消费，拒绝（应走 credit 模块）
    if (finalAccountType === 'credit' && direction === 0) {
      throw new Error('信用卡消费请使用信用卡接口（/account/credit）');
    }
    // 信用卡不能收入（统一拦截，防止误传）
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
      }, executor);

      if (!settlementResult.valid) {
        throw new Error(settlementResult.message);
      }
    }

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, account_type, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, transfer_group_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `;

    await executor.execute(query, [
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
      await AccountSettlement.syncBalanceSnapshot(cardId, userId, executor);
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

    // ⚠️ 事务内（executor 为事务连接）时，Account.findById 走全局连接读不到未提交记录，
    //    会返回 null，导致调用方(如还款)无法取得流水 ID。此处直接返回内存对象保证可用。
    //    非事务（默认连接）保持查库，返回完整记录供接口响应。
    if (executor !== db) {
      return {
        id,
        user_id: userId,
        card_id: cardId || null,
        direction,
        amount,
        currency: currency || 'CNY',
        exchange_rate: exchangeRate || 1,
        trans_date: transDate || now.substring(0, 10),
        account_type: finalAccountType,
        create_time: now,
        update_time: now,
      };
    }
    return Account.findById(id, userId);
  }

  /**
   * 更新借记卡收支记录
   * 独立实现，仅处理借记卡/虚拟账户流水更新（普通字段更新 + 余额快照同步）
   * 不涉及信用卡账单回滚逻辑
   */
  static async update(id, userId, { direction, categoryId, payType, payMethod, amount, currency, exchangeRate, transDate, remark, cardId }) {
    const now = String(Date.now());
    // P1-4 审计：金额必须 > 0
    if (amount !== undefined) {
      const amt = Number(amount);
      if (!Number.isFinite(amt) || amt <= 0) {
        throw new Error('收支金额必须大于 0');
      }
    }
    // 构造更新的字段（先不注入 update_time/id/userId，由后续分支统一处理）
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
      return Account.findById(id, userId);
    }

    // 校验：本接口仅处理借记卡流水，信用卡流水应走 credit 模块
    const oldRecord = await Account.findById(id, userId);
    if (!oldRecord) throw new Error('原流水不存在');
    if (oldRecord.account_type === 'credit') {
      throw new Error('该流水为信用卡流水，请使用信用卡接口（/account/credit）更新');
    }
    if (oldRecord.reversed_id) {
      throw new Error('该流水已被冲正，无法修改');
    }

    // P1-6 审计：修改金额/方向/卡片时必须重新校验余额，
    // 否则可先小额通过校验、再改大额/反向，绕过清算中心。
    const balanceFieldsChanged = amount !== undefined || direction !== undefined || (cardId !== undefined && cardId !== oldRecord.card_id);

    if (!balanceFieldsChanged) {
      // 不涉及余额的字段（备注/日期/分类等）直接更新
      const query = `
        UPDATE ${this.tableName} 
        SET ${fields.join(', ')}, update_time = ?
        WHERE id = ? AND user_id = ? AND is_deleted = 0
      `;
      await db.execute(query, [...params, now, id, userId]);
      return Account.findById(id, userId);
    }

    const targetCardId = cardId !== undefined ? cardId : oldRecord.card_id;
    const newDirection = direction !== undefined ? direction : oldRecord.direction;
    const newAmount = amount !== undefined ? amount : oldRecord.amount;
    const newCurrency = currency !== undefined ? currency : oldRecord.currency;
    const newRate = exchangeRate !== undefined ? exchangeRate : oldRecord.exchange_rate;

    const conn = await db.getConnection();
    try {
      await conn.beginTransaction();

      // 锁目标卡（新卡）+ 旧卡（换卡场景），串行化并发
      await conn.execute(
        'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [targetCardId, userId]
      );
      if (oldRecord.card_id && oldRecord.card_id !== targetCardId) {
        await conn.execute(
          'SELECT id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
          [oldRecord.card_id, userId]
        );
      }

      // P1-6 审计：按新值手动校验余额（不能直接软删原流水再 validate——
      // 单流水卡会被 totalCount===0 误拒；且须按"原流水贡献 − 新流水贡献"计算改后余额）
      const origCNY = toCNY(Number(oldRecord.amount || 0), oldRecord.currency || 'CNY', oldRecord.exchange_rate || 1);
      const newCNY = toCNY(Number(newAmount || 0), newCurrency || 'CNY', newRate || 1);
      // 原流水对来源余额的贡献（收入 +，支出 −）
      const origContrib = oldRecord.direction === 1 ? origCNY : -origCNY;
      const newContrib = newDirection === 1 ? newCNY : -newCNY;
      const isCardChanged = targetCardId !== oldRecord.card_id;
      // 目标卡当前余额（事务连接内读取，换卡时不含原流水；同卡时含原流水）
      const targetBalance = (await AccountSettlement.calculateBalance(targetCardId, userId, conn)).balance;
      // 改后余额 = 目标卡余额（同卡：− 原流水贡献）+ 新流水贡献
      const afterBalance = targetBalance + (isCardChanged ? 0 : -origContrib) + newContrib;

      // 收入方向总是允许（转入/退款）；支出方向要求改后余额 >= 0
      if (newDirection === 0 && afterBalance < -1e-9) {
        throw new Error(`余额不足！修改后余额将为 ${afterBalance.toFixed(2)}`);
      }

      // 校验通过：按新值写入
      const realQuery = `
        UPDATE ${this.tableName}
        SET ${fields.join(', ')}, update_time = ?
        WHERE id = ? AND user_id = ?
      `;
      await conn.execute(realQuery, [...params, now, id, userId]);

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }

    // 同步余额快照（新卡 + 换卡时的旧卡）
    await AccountSettlement.syncBalanceSnapshot(targetCardId, userId);
    if (oldRecord.card_id && oldRecord.card_id !== targetCardId) {
      await AccountSettlement.syncBalanceSnapshot(oldRecord.card_id, userId);
    }

    return Account.findById(id, userId);
  }

  /**
   * 借记卡冲正
   * 原支出 -> 冲正收入，原收入 -> 冲正支出
   * 含余额校验（冲正收入时相当于扣款，需校验余额）+ 事务保护
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

    // 余额校验：冲正收入(direction=1)会创建支出冲正流水，相当于从账户扣回这笔钱，
    // 若后续已被消费导致余额不足，应拒绝冲正；冲正支出(direction=0)相当于退款，总是允许。
    const validation = await AccountSettlement.validate({
      card_id: original.card_id,
      user_id: userId,
      direction: reverseDirection,
      amount: original.amount,
      exchange_rate: original.exchange_rate,
      currency: original.currency,
    });
    if (!validation.valid) {
      throw new Error(`冲正失败：${validation.message}`);
    }

    // 事务执行核心操作：创建冲正流水 + 软删原流水，保证原子性
    const reverseId = idUtils.billId();
    const conn = await db.getPool().getConnection();
    try {
      await conn.beginTransaction();

      // 行级锁：重新获取原流水并校验，防止并发重复冲正
      const [lockedRows] = await conn.execute(
        `SELECT id, reversed_id FROM ${this.tableName}
         WHERE id = ? AND user_id = ? AND is_deleted = 0
         FOR UPDATE`,
        [id, userId]
      );
      if (lockedRows.length === 0) {
        throw new Error('原流水已被删除或冲正');
      }
      if (lockedRows[0].reversed_id) {
        throw new Error('该流水已被冲正，无法重复冲正');
      }

      // 创建冲正流水
      await conn.execute(`
        INSERT INTO ${this.tableName} 
        (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
         exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
      `, [
        reverseId, userId, reverseDirection, original.category_id,
        original.pay_type, original.pay_method, original.amount, original.currency,
        original.exchange_rate, now.substring(0, 10),
        reverseRemark || `冲正：${original.remark || '交易撤销'}`,
        original.card_id, now, now, original.id
      ]);

      // 标记原流水为已删除
      await conn.execute(
        `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
        [now, id]
      );

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw new Error(`借记卡冲正失败：${e.message}`);
    } finally {
      conn.release();
    }

    // 事务外操作（快照同步，失败不回滚核心数据）
    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(`[借记卡冲正] ${original.card_alias || original.card_id} ${original.direction === 0 ? '支出' : '收入'} ${original.amount} -> 冲正${reverseDirection === 1 ? '收入' : '支出'}，原流水已标记删除`);

    return Account.findById(id, userId);
  }

  /**
   * 转账冲正（自转 / 提现）
   * 仅处理借记卡/虚拟账户间的转账冲正
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
    const groupRecords = await Account.findByTransferGroup(current.transfer_group_id, userId);

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

    return Account.findById(id, userId);
  }
}

module.exports = DebitAccount;
