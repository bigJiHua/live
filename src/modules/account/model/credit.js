const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');
const { toCNY } = require('../../../common/utils/currency');
const AccountSettlement = require('../service/settlement');
const CardBill = require('../../card/model/bill');
const CreditCore = require('../../card/core/CreditCore');
const ForeignRegister = require('../../card/model/foreignRegister');
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

  /** 外币消费登记随流水创建/更新/币种切换而同步（痛点4） */
  static async #syncForeignRegister(id, userId, { cardId, currency, amount, exchangeRate }, executor = db) {
    // C5 审计：登记失败不允许被吞掉——若登记未建成，pending 占额/对账页将缺失，
    // 且流水已入事务，会造成"已记账但登记丢失"。失败直接抛出，外层事务回滚。
    if (currency && currency !== 'CNY') {
      await ForeignRegister.ensurePending({
        userId,
        cardId,
        accountId: id,
        currency,
        foreignAmount: amount,
        registeredRate: exchangeRate || 0
      }, executor);
    } else {
      // 改为人民币或未知：移除外币登记（使用同一事务连接，跟随外层事务回滚，H4 审计）
      await ForeignRegister.deleteByAccountId(id, userId, executor);
    }
  }

  /**
   * 创建信用卡消费记录
   * 独立实现，仅处理信用卡消费（direction=0），含事务；账单一律交由 CreditCore.syncCardBills 全量重算
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
    // P0-1 审计：CATEGORY_REPAY 为还款专用分类，信用卡消费传入会导致该笔不计入账单、额度逃逸。
    //         金额必须 > 0，负数消费不进账单但会留脏数据。
    if (categoryId === 'CATEGORY_REPAY') {
      throw new Error('信用卡消费不允许使用还款分类（CATEGORY_REPAY）');
    }
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      throw new Error('信用卡消费金额必须大于 0');
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

      // H9 审计：事务内行级锁（锁卡片；若属于共享池则锁池行），串行化并发消费。
      // 锁后再校验额度（读到的都是已提交的最新账本），避免两笔并发同时通过校验。
      const [lockedCard] = await conn.execute(
        'SELECT id, share_pool_id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
        [cardId, userId]
      );
      if (!lockedCard[0]) throw new Error('卡片不存在');
      if (lockedCard[0].share_pool_id) {
        await conn.execute(
          'SELECT id FROM card_credit_pool WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
          [lockedCard[0].share_pool_id, userId]
        );
      }

      // ===== 清算中心验证（额度校验）—— 必须在锁内、事务内执行 =====
      const settlementResult = await AccountSettlement.validate({
        card_id: cardId,
        user_id: userId,
        direction: direction,
        amount: amount,
        exchange_rate: exchangeRate || 1,
        currency: currency || 'CNY',
        account_type: finalAccountType
      }, conn);
      if (!settlementResult.valid) {
        throw new Error(settlementResult.message);
      }

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
      // 外币消费：先登记 pending（事务内、重算前），使 syncCardBills 重算时登记已存在
      // （pending 状态被 CreditCore 跳过计入，对账完成后再重算计入实际汇率，P2 审计修复）
      await this.#syncForeignRegister(id, userId, {
        cardId,
        currency,
        amount,
        exchangeRate
      }, conn);
      // [CreditCore] 统一重算（账本全量重算，幂等）
      await CreditCore.syncCardBills(cardId, userId, conn);
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
    // 修复隐患：已冲正的流水不允许 update，防止重复冲正
    if (oldRecord.reversed_id) {
      throw new Error('该流水已被冲正，无法修改');
    }

    // P0-1 审计：信用卡消费 update 同样禁止改为还款分类；金额必须 > 0
    if (categoryId !== undefined && categoryId === 'CATEGORY_REPAY') {
      throw new Error('信用卡消费不允许使用还款分类（CATEGORY_REPAY）');
    }
    if (amount !== undefined) {
      const amt = Number(amount);
      if (!Number.isFinite(amt) || amt <= 0) {
        throw new Error('信用卡消费金额必须大于 0');
      }
    }

    // H2 审计：重算条件必须覆盖所有影响账单的字段（金额/日期/卡片/币种/汇率/方向/分类），
    // 此前仅金额/日期变更触发重算，改币种/汇率/方向/分类/换卡会绕过账单同步。
    const targetCardId = cardId !== undefined ? cardId : oldRecord.card_id;
    const isAmountChanged = amount !== undefined;
    const isDateChanged = transDate !== undefined;
    const isCardChanged = cardId !== undefined && cardId !== oldRecord.card_id;
    const isCurrencyRateChanged = currency !== undefined || exchangeRate !== undefined;
    const isDirectionChanged = direction !== undefined && direction !== oldRecord.direction;
    const isCategoryChanged = categoryId !== undefined && categoryId !== oldRecord.category_id;
    const needsBillSync = isAmountChanged || isDateChanged || isCardChanged || isCurrencyRateChanged || isDirectionChanged || isCategoryChanged;

    if (needsBillSync) {
      // 信用卡流水变更 → 事务内：锁卡 + 校验额度 + 更新流水 + 外币登记 + 重算目标卡/旧卡
      const conn = await db.getPool().getConnection();
      try {
        await conn.beginTransaction();

        // H9：锁目标卡（可能为新卡）+ 若为共享池则锁池行，串行化并发
        const [lockedCard] = await conn.execute(
          'SELECT id, share_pool_id FROM card_base WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
          [targetCardId, userId]
        );
        if (!lockedCard[0]) throw new Error('卡片不存在');
        if (lockedCard[0].share_pool_id) {
          await conn.execute(
            'SELECT id FROM card_credit_pool WHERE id = ? AND user_id = ? AND is_deleted = 0 FOR UPDATE',
            [lockedCard[0].share_pool_id, userId]
          );
        }

        // C4：无论变更哪类影响账单的字段（金额/币种/汇率/换卡/方向/分类），一律在锁内重新校验额度。
        //    - 同卡修改：按「差额」校验（excludeAmountCNY = 原流水折算 CNY），避免把原消费重复计入而错误拒绝
        //    - 换卡：原流水在旧卡聚合中，目标卡不含它 → excludeAmountCNY = 0
        //    - 改币种/汇率：新 CNY 折算可能超限，必须校验
        const newDirection = direction !== undefined ? direction : oldRecord.direction;
        if (newDirection === 1) {
          throw new Error('信用卡不能登记收入，还款请使用储蓄卡');
        }
        const originalCNY = toCNY(Number(oldRecord.amount || 0), oldRecord.currency || 'CNY', oldRecord.exchange_rate || 1);
        const settlementResult = await AccountSettlement.validate({
          card_id: targetCardId,
          user_id: userId,
          direction: newDirection,
          amount: amount !== undefined ? amount : oldRecord.amount,
          exchange_rate: (exchangeRate !== undefined ? exchangeRate : oldRecord.exchange_rate) || 1,
          currency: (currency !== undefined ? currency : oldRecord.currency) || 'CNY',
          account_type: 'credit',
          excludeAmountCNY: isCardChanged ? 0 : originalCNY
        }, conn);
        if (!settlementResult.valid) {
          throw new Error(settlementResult.message);
        }

        // 更新流水
        await conn.execute(query, params);

        // 外币登记随消费金额/币种/卡片变更而同步（事务内、重算前；使用目标卡 ID）
        await this.#syncForeignRegister(id, userId, {
          cardId: targetCardId,
          currency: currency !== undefined ? currency : oldRecord.currency,
          amount: amount !== undefined ? amount : oldRecord.amount,
          exchangeRate: exchangeRate !== undefined ? exchangeRate : oldRecord.exchange_rate
        }, conn);

        // 重算目标卡全部账单；换卡时旧卡也一并重算
        await CreditCore.syncCardBills(targetCardId, userId, conn);
        if (isCardChanged) {
          await CreditCore.syncCardBills(oldRecord.card_id, userId, conn);
        }

        await conn.commit();
      } catch (e) {
        await conn.rollback();
        throw e;
      } finally {
        conn.release();
      }

      // 事务外：同步余额快照（目标卡 + 换卡时的旧卡）
      await AccountSettlement.syncBalanceSnapshot(targetCardId, userId);
      if (isCardChanged && oldRecord.card_id) {
        await AccountSettlement.syncBalanceSnapshot(oldRecord.card_id, userId);
      }

      return Account.findById(id, userId);
    }

    // 仅备注/支付方式等不影响账单的字段变更
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

      // 4c2. 冲正连带删除外币登记（同一事务，C5 审计：避免 pending 占额残留/额度快照滞后）
      //      必须早于 syncCardBills，使 pending 占额在重算时已释放
      await ForeignRegister.deleteByAccountId(id, userId, conn);

      // 4c. [CreditCore] 重算账单（消费已冲正并标记删除，账本重算即反映额度恢复）
      await CreditCore.syncCardBills(original.card_id, userId, conn);

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
      // P2-8 审计：本卡还款方式已移除，但历史数据可能存在 account_id IS NULL 的旧记录。
      //     优先按 account_id 精确匹配；匹配不到时回退按流水 card_id + 金额 + 时间兜底匹配，
      //     保证撤销路径与 CardRepay.delete 语义一致（都能撤销本卡还款旧数据）。
      let repayRows;
      try {
        [repayRows] = await conn.execute(
          'SELECT * FROM card_repay WHERE account_id = ? AND is_deleted = 0 LIMIT 1',
          [id]
        );
      } catch (e) {
        repayRows = [];
      }
      if (repayRows.length === 0) {
        [repayRows] = await conn.execute(
          `SELECT * FROM card_repay
           WHERE card_id = ? AND user_id = ? AND account_id IS NULL AND is_deleted = 0
             AND repay_time = ? AND repay_amount = ?
           ORDER BY create_time DESC LIMIT 1`,
          [original.card_id, userId, now.substring(0, 10), original.amount]
        );
      }

      if (repayRows.length === 0) {
        throw new Error('未找到关联的还款记录');
      }

      const repayRecord = repayRows[0];
      creditCardId = repayRecord.card_id;

      await conn.execute(
        'UPDATE card_repay SET is_deleted = 1, update_time = ? WHERE id = ?',
        [now, repayRecord.id]
      );

      // [CreditCore] 重建账单（事务内，避免"撤销已提交但重算失败"的半完成态，P3 审计）
      await CreditCore.syncCardBills(creditCardId, userId, conn);

      await conn.commit();

      console.log(`[信用卡还款撤销] card_repay ${repayRecord.id} 已软删除，卡片=${creditCardId}`);
    } catch (e) {
      await conn.rollback();
      throw e;
    } finally {
      conn.release();
    }

    // ===== 2. 事务外：同步来源卡余额快照 =====

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

    // M3 审计：原流水已软删除，findById(id) 返回 null；应返回新冲正流水
    return Account.findById(reverseId, userId);
  }
}

module.exports = CreditAccount;
