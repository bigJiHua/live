const db = require('../../../common/config/db');
const idUtils = require('../../../common/utils/idUtils');

class Fund {
  static tableName = 'fund';
  static tableHistory = 'bus_fund_history';

  static numberExpr(alias, field) {
    return `COALESCE(CAST(NULLIF(${alias}.${field}, '') AS DECIMAL(16,4)), 0)`;
  }

  static legacySnapshotExpr(historyAlias = 'h', fundAlias = 'f') {
    const base = this.numberExpr(fundAlias, 'invest');
    const profit = this.numberExpr(historyAlias, 'net_value');
    const capital = this.numberExpr(historyAlias, 'market_val');
    return `(${capital} = ${base} AND ABS(${profit}) > ABS(${base}) * 0.5)`;
  }

  static historyProfitExpr(historyAlias = 'h', fundAlias = 'f') {
    const base = this.numberExpr(fundAlias, 'invest');
    const profit = this.numberExpr(historyAlias, 'net_value');
    return `CASE WHEN ${this.legacySnapshotExpr(historyAlias, fundAlias)} THEN ${profit} - ${base} ELSE ${profit} END`;
  }

  static historyCapitalExpr(historyAlias = 'h', fundAlias = 'f') {
    const capital = this.numberExpr(historyAlias, 'market_val');
    return `CASE WHEN ${this.legacySnapshotExpr(historyAlias, fundAlias)} THEN 0 ELSE ${capital} END`;
  }

  static aggregateSelect() {
    const principalExpr = `COALESCE(CAST(NULLIF(f.invest, '') AS DECIMAL(16,4)), 0)`;
    const capitalExpr = `COALESCE(a.capital_sum, 0)`;
    const profitExpr = `COALESCE(a.profit_sum, 0)`;
    const currentPrincipalExpr = `(${principalExpr} + ${capitalExpr})`;
    const currentMarketExpr = `(${currentPrincipalExpr} + ${profitExpr})`;

    return `
      SELECT
        f.id,
        f.user_id,
        f.fund_name,
        f.share,
        f.fund_account,
        f.trade_account,
        f.sell_org,
        f.fund_company,
        f.buy_date,
        ROUND(${profitExpr}, 4) AS net_value,
        ROUND(${currentPrincipalExpr}, 2) AS invest,
        ROUND(${currentMarketExpr}, 2) AS market_val,
        CONCAT(ROUND(CASE WHEN ${currentPrincipalExpr} > 0 THEN (${profitExpr} / ${currentPrincipalExpr}) * 100 ELSE 0 END, 2), '%') AS rate,
        f.create_time,
        f.is_deleted,
        f.net_value AS base_net_value,
        f.market_val AS base_market_val,
        ROUND(${principalExpr}, 2) AS base_invest,
        ROUND(${capitalExpr}, 2) AS capital_delta,
        ROUND(${profitExpr}, 4) AS profit_delta
      FROM ${this.tableName} f
      LEFT JOIN (
        SELECT
          h.fund_id,
          COALESCE(SUM(${this.historyProfitExpr('h', 'hf')}), 0) AS profit_sum,
          COALESCE(SUM(${this.historyCapitalExpr('h', 'hf')}), 0) AS capital_sum
        FROM ${this.tableHistory} h
        JOIN ${this.tableName} hf ON h.fund_id = hf.id
        GROUP BY h.fund_id
      ) a ON a.fund_id = f.id
    `;
  }

  static async findAll(userId) {
    const [rows] = await db.execute(
      `${this.aggregateSelect()} WHERE f.user_id = ? AND f.is_deleted = 0 ORDER BY f.create_time DESC`,
      [userId]
    );
    return rows;
  }

  static async findById(id, userId) {
    const [rows] = await db.execute(
      `${this.aggregateSelect()} WHERE f.id = ? AND f.user_id = ? AND f.is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static async findRawById(id, userId) {
    const [rows] = await db.execute(
      `SELECT * FROM ${this.tableName} WHERE id = ? AND user_id = ? AND is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static toNull(val) {
    return val === undefined ? null : val;
  }

  static async create({ userId, fundName, share, fundAccount, tradeAccount, sellOrg, fundCompany, buyDate, netValue, invest, marketVal, rate }) {
    const id = idUtils.billId();
    const now = Date.now().toString();
    await db.execute(
      `INSERT INTO ${this.tableName} (id, user_id, fund_name, share, fund_account, trade_account, sell_org, fund_company, buy_date, net_value, invest, market_val, rate, create_time, is_deleted)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`,
      [id, userId, fundName, share, fundAccount, tradeAccount, sellOrg, fundCompany, buyDate, netValue, invest, marketVal, rate, now].map(this.toNull)
    );
    return this.findById(id, userId);
  }

  static async update(id, userId, updates) {
    const allowedFields = ['fund_name', 'share', 'fund_account', 'trade_account', 'sell_org', 'fund_company', 'buy_date', 'net_value', 'invest', 'market_val', 'rate'];
    const fields = [];
    const params = [];
    Object.keys(updates).forEach(key => {
      if (allowedFields.includes(key) && updates[key] !== undefined) {
        fields.push(`${key} = ?`);
        params.push(updates[key]);
      }
    });
    if (fields.length === 0) return this.findById(id, userId);
    params.push(id, userId);
    await db.execute(
      `UPDATE ${this.tableName} SET ${fields.join(', ')} WHERE id = ? AND user_id = ?`,
      params
    );
    return this.findById(id, userId);
  }

  static async delete(id, userId) {
    const [result] = await db.execute(
      `UPDATE ${this.tableName} SET is_deleted = 1 WHERE id = ? AND user_id = ?`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }

  static async getHistory(fundId, userId, options = 180) {
    const query = typeof options === 'object' && options !== null ? options : { limit: options };
    const params = [fundId, userId];
    const where = ['h.fund_id = ?', 'f.user_id = ?', 'f.is_deleted = 0'];

    if (query.startDate) {
      where.push('h.record_date >= ?');
      params.push(query.startDate);
    }
    if (query.endDate) {
      where.push('h.record_date <= ?');
      params.push(query.endDate);
    }

    const limit = Number(query.limit);
    const limitSql = Number.isInteger(limit) && limit > 0 ? ' LIMIT ?' : '';
    if (limitSql) params.push(limit);

    const [rows] = await db.execute(
      `SELECT
         h.id,
         h.fund_id,
         ROUND(${this.historyProfitExpr('h', 'f')}, 4) AS net_value,
         ROUND(${this.historyCapitalExpr('h', 'f')}, 2) AS market_val,
         h.record_date,
         h.create_time
       FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE ${where.join(' AND ')}
       ORDER BY h.record_date ASC${limitSql}`,
      params
    );
    return rows;
  }

  static async findHistoryById(id, userId) {
    const [rows] = await db.execute(
      `SELECT h.* FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE h.id = ? AND f.user_id = ? AND f.is_deleted = 0`,
      [id, userId]
    );
    return rows[0] || null;
  }

  static async getLatestHistory(fundId, userId) {
    const [rows] = await db.execute(
      `SELECT h.* FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE h.fund_id = ? AND f.user_id = ? AND f.is_deleted = 0
       ORDER BY h.record_date DESC, h.create_time DESC LIMIT 1`,
      [fundId, userId]
    );
    return rows[0] || null;
  }

  static async getHistorySummary(fundId, userId) {
    const [rows] = await db.execute(
      `SELECT
         COALESCE(SUM(${this.historyProfitExpr('h', 'f')}), 0) AS profit_sum,
         COALESCE(SUM(${this.historyCapitalExpr('h', 'f')}), 0) AS capital_sum
       FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE h.fund_id = ? AND f.user_id = ? AND f.is_deleted = 0`,
      [fundId, userId]
    );
    return rows[0] || { profit_sum: 0, capital_sum: 0 };
  }

  static async getHistorySummaryBefore(fundId, userId, beforeDate) {
    const [rows] = await db.execute(
      `SELECT
         COALESCE(SUM(${this.historyProfitExpr('h', 'f')}), 0) AS profit_sum,
         COALESCE(SUM(${this.historyCapitalExpr('h', 'f')}), 0) AS capital_sum
       FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE h.fund_id = ? AND f.user_id = ? AND f.is_deleted = 0 AND h.record_date < ?`,
      [fundId, userId, beforeDate]
    );
    return rows[0] || { profit_sum: 0, capital_sum: 0 };
  }

  static async getAllHistory(userId, limit = 180) {
    const [rows] = await db.execute(
      `SELECT
         h.id,
         h.fund_id,
         ROUND(${this.historyProfitExpr('h', 'f')}, 4) AS net_value,
         ROUND(${this.historyCapitalExpr('h', 'f')}, 2) AS market_val,
         h.record_date,
         h.create_time
       FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE f.user_id = ? AND f.is_deleted = 0
       ORDER BY h.record_date ASC LIMIT ?`,
      [userId, limit]
    );
    return rows;
  }

  static async addHistory({ fundId, netValue, marketVal, recordDate }) {
    const id = idUtils.billId();
    const now = Date.now().toString();
    await db.execute(
      `INSERT INTO ${this.tableHistory} (id, fund_id, net_value, market_val, record_date, create_time)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [id, fundId, netValue, marketVal, recordDate, now].map(this.toNull)
    );
    return id;
  }

  static async updateHistory(id, userId, { netValue, marketVal }) {
    const fields = []; const params = [];
    if (netValue !== undefined) { fields.push('net_value = ?'); params.push(netValue); }
    if (marketVal !== undefined) { fields.push('market_val = ?'); params.push(marketVal); }
    if (fields.length === 0) return false;
    params.push(id, userId);
    const [result] = await db.execute(
      `UPDATE ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       SET ${fields.join(', ')}
       WHERE h.id = ? AND f.user_id = ? AND f.is_deleted = 0`,
      params
    );
    return result.affectedRows > 0;
  }

  static async deleteHistory(id, userId) {
    const [result] = await db.execute(
      `DELETE h FROM ${this.tableHistory} h
       JOIN ${this.tableName} f ON h.fund_id = f.id
       WHERE h.id = ? AND f.user_id = ? AND f.is_deleted = 0`,
      [id, userId]
    );
    return result.affectedRows > 0;
  }
}

module.exports = Fund;
