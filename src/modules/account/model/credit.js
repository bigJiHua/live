const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const AccountSettlement = require('../service/settlement');
const CardBill = require('../../card/model/bill');
const AssetSnapshot = require('../../asset/model/snapshot');
const Account = require('./index'); // 仅用于调用只读查询方法（findById 等），不调用写入方法

/**
 * 信用卡账务模型 - 独立实现，不与借记卡共用写入方法
 *
 * 处理范围：
 * 1. 信用卡消费创建（事务 + 账单同步）
 * 2. 信用卡消费更新（账单回滚重同步）
 * 3. 信用卡消费冲正
 * 4. 信用卡还款冲正
 */
class CreditAccount {
  static tableName = 'account';

  /**
   * 创建信用卡消费记录
   * 独立实现，仅处理信用卡消费（direction=0），含事务 + CardBill.syncFromExpense
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

    // 信用卡模块拦截：仅处理信用卡
    if (finalAccountType !== 'credit') {
      throw new Error('信用卡接口仅支持信用卡流水，借记卡请使用 /account/debit');
    }
    // 信用卡不能收入
    if (direction === 1) {
      throw new Error('信用卡不能登记收入，还款请使用储蓄卡');
    }

    // ===== 清算中心验证（额度校验）=====
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

    const query = `
      INSERT INTO ${this.tableName} 
      (id, user_id, direction, category_id, pay_type, pay_method, account_type, amount, currency, exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, transfer_group_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `;

    // 信用卡消费：事务内创建流水 + 同步账单额度
    const conn = await db.getPool().getConnection();
    try {
      await conn.beginTransaction();
      await conn.execute(query, [
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
      await CardBill.syncFromExpense(cardId, userId, amount, transDate, conn, currency || 'CNY', exchangeRate || 1);
      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }

    // 信用卡余额快照同步（syncBalanceSnapshot 对信用卡会跳过，保留调用保持一致性）
    await AccountSettlement.syncBalanceSnapshot(cardId, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return Account.findById(id, userId);
  }

  /**
   * 更新信用卡消费记录
   * 独立实现，处理金额/日期变更时的账单回滚 + 重同步
   * 修复：增加 reversed_id 校验，防止已冲正流水被 update 导致双重回滚
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

    fields.push('update_time = ?');
    params.push(now);
    params.push(id, userId);

    const query = `
      UPDATE ${this.tableName} 
      SET ${fields.join(', ')}
      WHERE id = ? AND user_id = ? AND is_deleted = 0
    `;

    // 校验：本接口仅处理信用卡消费流水
    const oldRecord = await Account.findById(id, userId);
    if (!oldRecord) throw new Error('原流水不存在');
    if (oldRecord.account_type !== 'credit') {
      throw new Error('该流水为借记卡流水，请使用借记卡接口（/account/debit）更新');
    }
    // 修复隐患：已冲正的流水不允许 update，防止 rollbackExpense 双重回滚
    if (oldRecord.reversed_id) {
      throw new Error('该流水已被冲正，无法修改');
    }

    const isAmountChanged = amount !== undefined;
    const isDateChanged = transDate !== undefined;
    const needsBillSync = isAmountChanged || isDateChanged;

    if (needsBillSync && oldRecord.direction === 0) {
      // 信用卡消费金额/日期变更 → 事务内：回滚旧账单 + 更新流水 + 同步新账单
      const conn = await db.getPool().getConnection();
      try {
        await conn.beginTransaction();

        // 1. 回滚旧的消费金额（账单不存在则回滚整个事务）
        const rollbackResult = await CardBill.rollbackExpense(
          oldRecord.card_id, userId,
          oldRecord.amount,
          oldRecord.trans_date,
          conn,
          oldRecord.currency,
          oldRecord.exchange_rate
        );
        if (!rollbackResult) {
          throw new Error(
            `旧账单回滚失败，原消费日期 ${oldRecord.trans_date} 对应账单周期不存在`
          );
        }

        // 2. 更新流水
        await conn.execute(query, params);

        // 3. 重新同步新金额/新日期到账单
        const [updatedRows] = await conn.execute(
          `SELECT amount, trans_date, card_id FROM ${this.tableName}
           WHERE id = ? AND user_id = ? AND is_deleted = 0`,
          [id, userId]
        );
        const updated = updatedRows[0];
        if (updated) {
          const newCurrency = currency !== undefined ? currency : oldRecord.currency;
          const newRate = exchangeRate !== undefined ? exchangeRate : oldRecord.exchange_rate;
          await CardBill.syncFromExpense(
            updated.card_id, userId,
            updated.amount,
            updated.trans_date,
            conn,
            newCurrency,
            newRate
          );
        }

        await conn.commit();
      } catch (e) {
        await conn.rollback();
        throw e;
      } finally {
        conn.release();
      }

      // 事务外：同步余额快照
      if (oldRecord.card_id) {
        await AccountSettlement.syncBalanceSnapshot(oldRecord.card_id, userId);
      }

      return Account.findById(id, userId);
    }

    // 非金额/日期变更，或信用卡收入方向（理论上信用卡不能收入，此处防御性处理）
    await db.execute(query, params);

    let updated;
    if (cardId || amount !== undefined || direction !== undefined) {
      updated = await Account.findById(id, userId);
      if (updated?.card_id) {
        await AccountSettlement.syncBalanceSnapshot(updated.card_id, userId);
      }
    }

    return updated || Account.findById(id, userId);
  }

  /**
   * 信用卡消费冲正
   * 原支出 -> 冲正收入 + 恢复账单额度
   */
  static async reverseCreditExpenseById(id, userId, reverseRemark) {
    const now = String(Date.now());

    // ===== 1. 查询原流水 =====
    const [rows] = await db.execute(
      `SELECT 
          a.*,
          cb.card_type,
          cb.alias as card_alias,
          cb.last4_no,
          cb.bill_day
       FROM ${this.tableName} a
       LEFT JOIN card_base cb ON a.card_id = cb.id
       WHERE a.id = ? AND a.user_id = ? AND a.is_deleted = 0`,
      [id, userId]
    );
    const original = rows[0];

    // ===== 2. 基础校验 =====
    if (!original) throw new Error('原流水不存在或已被删除');
    if (original.reversed_id) throw new Error('该流水已被冲正，无法重复冲正');

    // 仅限信用卡
    if (original.card_type !== 'credit') {
      throw new Error('该接口仅支持信用卡消费冲正');
    }
    if (original.direction !== 0) {
      throw new Error('该接口仅支持信用卡支出流水冲正');
    }

    // 旧版转账记录拦截
    if (original.pay_type === '转账' && !original.transfer_group_id) {
      throw new Error('该转账流水为早期旧数据，缺少转账关联信息，无法自动冲正。如需撤销请手动创建相反方向的收支记录');
    }

    // ===== 3. 账单周期校验 =====

    // 校验账单日配置
    const billDay = Number(original.bill_day);
    if (!Number.isInteger(billDay) || billDay < 1 || billDay > 31) {
      throw new Error(
        `卡片 [${original.card_alias || original.card_id}] 账单日配置异常`
      );
    }

    // 计算原消费所属账单周期
    const billMonth = CardBill.getBillMonthByDate(original.trans_date, billDay);

    // 检查对应账单是否存在
    const bill = await CardBill.findByCardAndMonth(original.card_id, billMonth);
    if (!bill) {
      throw new Error(
        `该消费属于 ${billMonth} 账单周期，但该周期账单不存在，无法冲正`
      );
    }

    // 检查该账单周期内是否已有还款
    const [repayRows] = await db.execute(
      `SELECT id, repay_amount, repay_time
       FROM card_repay
       WHERE card_id = ?
         AND user_id = ?
         AND (bill_month = ? OR bill_id = ?)
         AND is_deleted = 0
       LIMIT 1`,
      [original.card_id, userId, billMonth, bill.id]
    );

    if (repayRows.length > 0) {
      // 仅当该账单已“完全还清”（待还金额≈0）时才禁止冲正。
      // 若仍有未还欠款(need_repay>0)，说明账单尚未结清，允许冲正本期内的消费
      // （例如还款后新增的消费、或还款后发生的退款），避免“还过款就一律锁死”的问题。
      const needRepay = parseFloat(bill.need_repay) || 0;
      if (needRepay < 0.01) {
        const repay = repayRows[0];
        throw new Error(
          `该消费属于 ${billMonth} 账单周期，该周期已还清（还款 ${repay.repay_amount}元），继续冲正将产生溢缴差额，请先撤销还款后再冲正此笔交易`
        );
      }
    }

    // ===== 4. 事务执行核心操作 =====
    const reverseId = idUtils.billId();
    const conn = await db.getPool().getConnection();
    try {
      await conn.beginTransaction();

      // 4a. 行级锁：重新获取原流水并校验，防止并发重复冲正
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

      // 4b. 创建冲正流水
      await conn.execute(`
        INSERT INTO ${this.tableName} 
        (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
         exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
      `, [
        reverseId, userId, 1, original.category_id,
        original.pay_type, original.pay_method, original.amount, original.currency,
        original.exchange_rate, now.substring(0, 10),
        reverseRemark || `冲正：${original.remark || '交易撤销'}`,
        original.card_id, now, now, original.id
      ]);

      // 4c. 标记原流水为已删除
      await conn.execute(
        `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
        [now, id]
      );

      // 4c. 恢复账单额度（使用事务连接保证一致性）
      const rolledBackBill = await CardBill.rollbackExpense(
        original.card_id, userId, original.amount, original.trans_date, conn,
        original.currency, original.exchange_rate
      );
      if (!rolledBackBill) {
        throw new Error(`该消费属于 ${billMonth} 账单周期，但账单回滚失败`);
      }

      await conn.commit();
    } catch (e) {
      await conn.rollback();
      throw new Error(`信用卡消费冲正失败：${e.message}`);
    } finally {
      conn.release();
    }

    // ===== 5. 事务外操作（快照同步，失败不回滚核心数据）=====
    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    console.log(
      `[信用卡消费冲正] ${original.card_alias || original.card_id} ` +
      `支出${original.amount} -> 冲正收入 + 恢复账单额度，` +
      `账单周期=${billMonth}，原流水已标记删除`
    );

    return Account.findById(reverseId, userId);
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

    // ===== 1. 事务内：行锁校验 + 创建冲正流水 + 软删原流水 + 软删 card_repay =====
    const reverseId = idUtils.billId();
    const conn = await db.getPool().getConnection();
    let creditCardId;
    try {
      await conn.beginTransaction();

      // 1a. 行级锁：重新校验原流水，防并发重复冲正
      const [lockedRows] = await conn.execute(
        `SELECT id, reversed_id, category_id FROM ${this.tableName}
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
      if (lockedRows[0].category_id !== 'CATEGORY_REPAY') {
        throw new Error('该接口仅支持信用卡还款流水');
      }

      // 1b. 创建冲正流水（收入方向，恢复余额）
      await conn.execute(`
        INSERT INTO ${this.tableName} 
        (id, user_id, direction, category_id, pay_type, pay_method, amount, currency, 
         exchange_rate, trans_date, remark, card_id, create_time, update_time, is_deleted, reversed_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
      `, [
        reverseId, userId, 1, original.category_id,
        original.pay_type, original.pay_method, original.amount, original.currency,
        original.exchange_rate, now.substring(0, 10),
        reverseRemark || `还款撤销：${original.remark || '交易撤销'}`,
        original.card_id, now, now, original.id
      ]);

      // 1c. 标记原流水为已删除
      await conn.execute(
        `UPDATE ${this.tableName} SET is_deleted = 1, update_time = ? WHERE id = ?`,
        [now, id]
      );

      // 1d. 查询并软删除 card_repay 记录
      const [repayRows] = await conn.execute(
        'SELECT * FROM card_repay WHERE account_id = ? AND is_deleted = 0 LIMIT 1',
        [id]
      );

      if (repayRows.length === 0) {
        throw new Error('未找到关联的还款记录');
      }

      const repayRecord = repayRows[0];
      creditCardId = repayRecord.card_id;

      await conn.execute(
        'UPDATE card_repay SET is_deleted = 1, update_time = ? WHERE id = ?',
        [now, repayRecord.id]
      );

      await conn.commit();

      console.log(`[信用卡还款撤销] card_repay ${repayRecord.id} 已软删除，卡片=${creditCardId}`);
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }

    // ===== 2. 事务外：重建账单 + 同步快照 =====
    // 重建信用卡账单（全量从流水聚合，内含信用卡余额快照同步但信用卡会被跳过）
    await CardBill.rebuildBillFromAccount(creditCardId, userId);

    // 同步来源卡余额快照：还款流水挂在来源卡(original.card_id)上，
    // 冲正后来源卡余额已恢复，必须更新 account_balance 快照，否则前端读快照会显示旧余额。
    // 注意：信用卡本身的 syncBalanceSnapshot 会被跳过（信用卡用 card_bill 而非 account_balance），
    // 真正需要更新的是来源卡（借记卡/余额/现金）的快照。
    await AccountSettlement.syncBalanceSnapshot(original.card_id, userId);

    // 系统自动检查并记录资产快照
    setImmediate(() => {
      AssetSnapshot.autoSaveSnapshot(userId).catch(err => {
        console.error(`[资产快照] 自动快照失败:`, err.message);
      });
    });

    return Account.findById(id, userId);
  }
}

module.exports = CreditAccount;
