/**
 * 外币消费登记/对账模型（痛点4）
 * - 外币消费流水(account, currency != CNY) 创建时自动登记一条 pending 记录
 * - 用户还款对账后调用 reconcile()，录入银行 App 实际结算汇率/人民币
 * - CreditCore.#computeMonths 仅把「已对账(reconciled)」的人民币计入账单；
 *   pending（未对账）不计入账单金额，但按登记汇率占用可用额度
 * 汇率口径：与全局一致 = 每100外币等值人民币（见 currency.js toCNY = amount*rate/100）
 *
 * 可靠性（H4 审计）：
 * - ensurePending 已存在时 UPDATE 金额/汇率/卡片（此前只 return，流水变更后登记不更新）
 * - 全部写方法支持 executor 事务连接（此前 deleteByAccountId 用全局连接，无法跟随外层事务回滚）
 */
const db = require("../../../common/config/db");
const idUtils = require("../../../common/utils/idUtils");
const { toCNY } = require("../../../common/utils/currency");

const TABLE = "card_foreign_register";

class ForeignRegister {
  static nowStr() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  }

  /** 消费流水创建时自动登记一条 pending（幂等：account_id 唯一，已存在则同步金额/汇率/卡片） */
  static async ensurePending({
    userId,
    cardId,
    accountId,
    currency,
    foreignAmount,
    registeredRate,
  }, executor = db) {
    // C7：查询不限定 is_deleted——uk_account_id 唯一键对软删行也生效，
    // 若软删行占位，INSERT 会撞唯一键。命中任意行（含软删）一律 UPDATE 恢复并更新字段。
    const [exist] = await executor.execute(
      `SELECT id FROM ${TABLE} WHERE account_id = ? AND user_id = ? LIMIT 1`,
      [accountId, userId]
    );
    const now = this.nowStr();
    const regRate = Number(registeredRate || 0);
    const regRmb = toCNY(Number(foreignAmount || 0), currency, regRate);
    if (exist[0]) {
      // H4：流水金额/币种/汇率/卡片变更时，同步更新已存在的登记，避免旧登记残留在对账页
      await executor.execute(
        `UPDATE ${TABLE} SET
           is_deleted = 0, status = 'pending',
           card_id = ?, currency = ?, foreign_amount = ?, registered_rate = ?, registered_rmb = ?, update_time = ?
         WHERE id = ?`,
        [cardId, currency, Number(foreignAmount || 0), regRate, regRmb, now, exist[0].id]
      );
      return exist[0].id;
    }
    const id = idUtils.shortId();
    await executor.execute(
      `INSERT INTO ${TABLE}
       (id, user_id, card_id, account_id, currency, foreign_amount, registered_rate, registered_rmb, status, create_time, update_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, 0)`,
      [
        id, userId, cardId, accountId, currency,
        Number(foreignAmount || 0), regRate, regRmb, now, now,
      ]
    );
    return id;
  }

  /** 对账：录入银行 App 实际结算汇率 / 人民币（或修改） */
  static async reconcile(id, userId, { actualRate, actualRmb, settleDate, remark }, executor = db) {
    // P1-5 审计：模型层兜底——实际汇率必须 > 0；传入的人民币金额也必须 > 0
    const rate = Number(actualRate || 0);
    if (!Number.isFinite(rate) || rate <= 0) {
      throw new Error('实际汇率必须大于 0');
    }
    if (actualRmb !== undefined && actualRmb !== null && actualRmb !== '') {
      const rmb = Number(actualRmb);
      if (!Number.isFinite(rmb) || rmb <= 0) {
        throw new Error('实际人民币金额必须大于 0');
      }
    }
    // actualRmb 优先用传入；未传则按 foreign_amount * rate / 100 反算
    const [row] = await executor.execute(
      `SELECT foreign_amount, currency FROM ${TABLE} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    if (!row[0]) throw new Error("外币登记记录不存在");
    const rmb =
      actualRmb !== undefined && actualRmb !== null && actualRmb !== ""
        ? Number(actualRmb)
        : toCNY(Number(row[0].foreign_amount || 0), row[0].currency, rate);
    // 结算日期：以「点击确认对账受理那一刻」为结算日（无需前端选择）。
    // 后端统一用当前日期写入 settle_date；若外部仍显式传了日期则优先用传入值（兼容历史调用）。
    const settleDateValue =
      settleDate && String(settleDate).trim() ? String(settleDate).trim() : this.nowStr().substring(0, 10);
    await executor.execute(
      `UPDATE ${TABLE}
       SET actual_rate = ?, actual_rmb = ?, settle_date = ?, status = 'reconciled',
           remark = COALESCE(?, remark), update_time = ?
       WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [rate, rmb, settleDateValue, remark || null, this.nowStr(), id, userId]
    );
    return this.findById(id, userId, executor);
  }

  static async findById(id, userId, executor = db) {
    const [rows] = await executor.execute(
      `SELECT * FROM ${TABLE} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static async findByAccountId(accountId, userId, executor = db) {
    const [rows] = await executor.execute(
      `SELECT * FROM ${TABLE} WHERE account_id = ? AND user_id = ? AND is_deleted = 0 LIMIT 1`,
      [accountId, userId]
    );
    return rows[0] || null;
  }

  static async findPendingByCard(cardId, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${TABLE} WHERE card_id = ? AND user_id = ? AND status = 'pending' AND is_deleted = 0 ORDER BY create_time DESC`,
      [cardId, userId]
    );
    return rows;
  }

  /** 用户维度全部待对账（供专用登记页） */
  static async findPendingByUser(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${TABLE} WHERE user_id = ? AND status = 'pending' AND is_deleted = 0 ORDER BY create_time DESC`,
      [userId]
    );
    return rows;
  }

  static async findAllByUser(userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${TABLE} WHERE user_id = ? AND is_deleted = 0 ORDER BY create_time DESC`,
      [userId]
    );
    return rows;
  }

  /**
   * 找出历史外币消费流水（痛点4 补充：对账页仅基于 card_foreign_register 已登记数据，
   * 未登记/早期流水找不到）。从 account 账本扫描该用户全部信用卡外币消费
   * （direction=0、非还款分类、未冲正、currency != CNY），LEFT JOIN 登记表带出登记状态，
   * 支持按卡与交易时间范围过滤，供用户逐笔登记/对账。
   * @param {string} userId
   * @param {{cardId?:string, startDate?:string, endDate?:string}} filters
   * @returns {Promise<Array<{account_id,card_id,currency,amount,exchange_rate,trans_date,reg_id,reg_status,registered_rate,registered_rmb,actual_rmb,settle_date}>>}
   */
  static async findForeignExpenseHistory(userId, { cardId, startDate, endDate } = {}) {
    const where = [
      'a.user_id = ?',
      'a.is_deleted = 0',
      'a.direction = 0',
      'a.reversed_id IS NULL',
      "a.currency IS NOT NULL AND a.currency != 'CNY'",
      "c.card_type = 'credit'",
      '(a.category_id IS NULL OR a.category_id != ?)',
    ];
    const params = [userId, 'CATEGORY_REPAY'];
    if (cardId) {
      where.push('a.card_id = ?');
      params.push(cardId);
    }
    if (startDate) {
      where.push('a.trans_date >= ?');
      params.push(startDate);
    }
    if (endDate) {
      where.push('a.trans_date <= ?');
      params.push(endDate);
    }
    const [rows] = await db.execute(
      `SELECT a.id AS account_id, a.card_id, a.currency, a.amount, a.exchange_rate, a.trans_date,
              fr.id AS reg_id, fr.status AS reg_status, fr.registered_rate, fr.registered_rmb,
              fr.actual_rmb, fr.settle_date
       FROM account a
       JOIN card_base c ON a.card_id = c.id AND c.user_id = a.user_id AND c.is_deleted = 0
       LEFT JOIN ${TABLE} fr ON fr.account_id = a.id AND fr.user_id = a.user_id AND fr.is_deleted = 0
       WHERE ${where.join(' AND ')}
       ORDER BY a.trans_date DESC, a.create_time DESC`,
      params
    );
    return rows;
  }

  /** 冲正/删除流水时连带删除登记（支持事务连接，跟随外层事务回滚） */
  static async deleteByAccountId(accountId, userId, executor = db) {
    await executor.execute(
      `UPDATE ${TABLE} SET is_deleted = 1, update_time = ? WHERE account_id = ? AND user_id = ?`,
      [this.nowStr(), accountId, userId]
    );
    return true;
  }

  static async delete(id, userId, executor = db) {
    await executor.execute(
      `UPDATE ${TABLE} SET is_deleted = 1, update_time = ? WHERE id = ? AND user_id = ?`,
      [this.nowStr(), id, userId]
    );
    return true;
  }
}

module.exports = ForeignRegister;
