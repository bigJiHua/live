const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");
const { toCNY } = require("../../../common/utils/currency");
const CreditCore = require("../../card/core/CreditCore");

/**
 * 账户清算中心
 * 支持多币种，按 card_id + currency 存储余额
 */
class AccountSettlement {
  /**
   * 计算余额（从 account 表汇总，换算成人民币价值）
   * CNY（人民币）：金额 * 1（1:1 不变）
   * 外币：金额 * 汇率 / 100（前端传来的是"100外币兑人民币价格"）
   * 注意：跳过 reversed_id 不为空的流水（已被冲正，不应计入余额）
   */
  static async calculateBalance(cardId, userId, executor = db) {
    const query = `
      SELECT 
        currency,
        direction,
        amount,
        exchange_rate
      FROM account
      WHERE card_id = ? AND user_id = ? AND is_deleted = 0 AND reversed_id IS NULL
    `;
    const [rows] = await executor.execute(query, [cardId, userId]);

    let income = 0;
    let expense = 0;

    for (const row of rows) {
      const amount = parseFloat(row.amount) || 0;
      const cnyAmount = toCNY(amount, row.currency, row.exchange_rate);

      if (row.direction === 1) {
        income += cnyAmount;
      } else {
        expense += cnyAmount;
      }
    }

    return {
      income: Math.round(income * 100) / 100,
      expense: Math.round(expense * 100) / 100,
      balance: Math.round((income - expense) * 100) / 100,
      totalCount: rows.length,
    };
  }

  /**
   * 获取余额快照
   */
  static async getBalanceSnapshot(cardId, userId) {
    const query = `
      SELECT * FROM account_balance 
      WHERE card_id = ? AND user_id = ? AND is_deleted = 0
    `;
    const [rows] = await db.execute(query, [cardId, userId]);
    return rows[0] || null;
  }

  /**
   * 获取用户所有账户余额
   */
  static async getAllBalances(userId) {
    const query = `
      SELECT card_id,
             SUM(CASE WHEN direction = 1 THEN 
               CASE WHEN currency = 'CNY' THEN amount 
               ELSE ROUND(amount * exchange_rate / 100, 2) END 
             ELSE 0 END) as income,
             SUM(CASE WHEN direction = 0 THEN 
               CASE WHEN currency = 'CNY' THEN amount 
               ELSE ROUND(amount * exchange_rate / 100, 2) END 
             ELSE 0 END) as expense,
             COUNT(*) as total_count
      FROM account
      WHERE user_id = ? AND is_deleted = 0 AND card_id IS NOT NULL
      GROUP BY card_id
    `;
    const [rows] = await db.execute(query, [userId]);

    return rows.map((row) => ({
      card_id: row.card_id,
      income: parseFloat(row.income || 0),
      expense: parseFloat(row.expense || 0),
      balance: parseFloat(row.income || 0) - parseFloat(row.expense || 0),
      total_count: parseInt(row.total_count || 0),
    }));
  }

  /**
   * 验证交易
   * - 信用卡（credit）：验证额度不能超限
   * - 虚拟账户（xxxx=现金，yyyy=余额）：需要验证余额
   * - 储蓄卡（debit）：需要验证余额
   */
  static async validate(record, executor = db) {
    const { card_id, user_id, direction, amount, exchange_rate, currency, excludeAmountCNY = 0 } =
      record;
    // 转换后的金额（人民币）
    const amountNum = parseFloat(amount) || 0;
    const amountInCNY = toCNY(amountNum, currency, exchange_rate);

    // 虚拟账户（xxxx=现金，yyyy=余额）
    if (card_id === "xxxx" || card_id === "yyyy") {
      const balanceInfo = await this.calculateBalance(card_id, user_id, executor);
      const currentBalance = balanceInfo.balance;

      // 收入总是允许
      if (direction === 1) {
        return { valid: true, message: "收入允许", currentBalance };
      }
      // 无收支记录不能支出
      if (balanceInfo.totalCount === 0) {
        return {
          valid: false,
          message: "该账户尚无收支记录，请先登记一笔收入",
          currentBalance,
        };
      }
      // 余额不足不能支出
      if (currentBalance < amountInCNY) {
        return {
          valid: false,
          message: `余额不足，当前 ${currentBalance}，需要 ${amountInCNY}`,
          currentBalance,
        };
      }
      return { valid: true, message: "验证通过", currentBalance };
    }

    // 查询实体卡片类型（P2-10：使用事务连接，保证锁内读到己写入/最新状态）
    const [cardRows] = await executor.execute(
      "SELECT card_type, credit_limit, temp_limit FROM card_base WHERE id = ? AND is_deleted = 0",
      [card_id]
    );
    const cardType = cardRows[0]?.card_type;
    const creditLimit = parseFloat(cardRows[0]?.credit_limit) || 0;
    const tempLimit = parseFloat(cardRows[0]?.temp_limit) || 0;

    // 信用卡：验证额度不能超限（使用权威口径 CreditCore.aggregate，支持共享池/外币登记）
    if (cardType === "credit") {
      // CreditCore.aggregate 返回结构：{ totalCreditLimit, ..., cards: [...] }
      // P2-10：传入 executor（事务连接），锁内校验读到一致账本
      const agg = await CreditCore.aggregate(user_id, executor);
      const list = Array.isArray(agg) ? agg : agg.cards || [];
      // 从聚合结果中找到本卡归属的分组（独立卡 / 共享池）
      let group = list.find((g) => g.shared === false && g.cardId === card_id);
      if (!group) {
        group = list.find(
          (g) =>
            g.shared === true &&
            Array.isArray(g.cards) &&
            g.cards.some((c) => c.cardId === card_id)
        );
      }

      // 兜底：若聚合未命中（极端异常），回退到 card_base 静态额度，避免误拦截
      const totalLimit = group
        ? (parseFloat(group.creditLimit) || 0) + (parseFloat(group.tempLimit) || 0)
        : creditLimit + tempLimit;
      const availLimit = group ? parseFloat(group.avail) || 0 : totalLimit;

      // 收入（还款）总是允许
      if (direction === 1) {
        return {
          valid: true,
          message: "信用卡还款允许",
          currentBalance: availLimit,
        };
      }

      // C4：usedLimitAfter = 当前负债(含原流水) − 原流水折算(同卡修改时) + 新金额折算。
      //     同卡改金额/币种/汇率时按差额校验，避免把原消费重复计入而错误拒绝；
      //     换卡时原流水不在目标卡聚合中，excludeAmountCNY 传 0（不抵扣）。
      const usedLimitAfter = totalLimit - availLimit + Number(amountInCNY) - Number(excludeAmountCNY || 0);

      // 检查是否超额度
      if (usedLimitAfter > totalLimit + 1e-9) {
        console.log(
          `信用卡限额：额度 ${totalLimit}，可用 ${availLimit}，本次 ${amountInCNY}`
        );

        return {
          valid: false,
          message: `登记失败！超过信用卡额度！可用 ¥${availLimit.toFixed(
            2
          )}，本次 ¥${amountInCNY.toFixed(2)}`,
          currentBalance: availLimit,
        };
      }

      return { valid: true, message: "验证通过", currentBalance: availLimit };
    }

    // 储蓄卡
    const balanceInfo = await this.calculateBalance(card_id, user_id, executor);
    const currentBalance = balanceInfo.balance;

    // 收入总是允许
    if (direction === 1) {
      return { valid: true, message: "收入允许", currentBalance };
    }

    // 储蓄卡：必须有收支记录才能支出
    if (balanceInfo.totalCount === 0) {
      return {
        valid: false,
        message: "余额不足！",
        currentBalance,
      };
    }

    // 储蓄卡：余额不足不能支出
    if (currentBalance < amountInCNY) {
      return {
        valid: false,
        message: `余额不足，当前 ${currentBalance}，需要 ${amountInCNY}`,
        currentBalance,
      };
    }

    return { valid: true, message: "验证通过", currentBalance };
  }

  /**
   * 同步余额快照
   * 跳过信用卡类型（信用卡使用 card_bill 而非 account_balance 记录余额）
   */
  static async syncBalanceSnapshot(cardId, userId, executor = db) {
    // 检查是否为信用卡，信用卡不写入 account_balance
    const [cardRows] = await executor.execute(
      "SELECT card_type FROM card_base WHERE id = ? AND is_deleted = 0",
      [cardId]
    );
    if (cardRows[0]?.card_type === "credit") {
      console.log(`[余额快照] 跳过信用卡 ${cardId}，不写入 account_balance`);
      return null;
    }

    const balanceInfo = await this.calculateBalance(cardId, userId, executor);
    const now = String(Date.now());
    const id = idUtils.billId();

    // 使用 INSERT ... ON DUPLICATE KEY UPDATE 防止重复
    await executor.execute(
      `INSERT INTO account_balance (id, user_id, card_id, balance, update_time, is_deleted) 
       VALUES (?, ?, ?, ?, ?, 0)
       ON DUPLICATE KEY UPDATE
         balance = VALUES(balance),
         update_time = VALUES(update_time),
         is_deleted = 0`,
      [id, userId, cardId, balanceInfo.balance, now]
    );

    return this.getBalanceSnapshot(cardId, userId);
  }

  /**
   * 初始化卡片余额
   */
  static async initCardBalance(cardId, userId) {
    const existing = await this.getBalanceSnapshot(cardId, userId);
    if (!existing) {
      await this.syncBalanceSnapshot(cardId, userId);
    }
    return this.getBalanceSnapshot(cardId, userId);
  }

  /**
   * 重建所有余额
   */
  static async rebuildAllBalances(userId) {
    const balances = await this.getAllBalances(userId);
    for (const item of balances) {
      await this.syncBalanceSnapshot(item.card_id, userId);
    }
    return balances;
  }
}

module.exports = AccountSettlement;
