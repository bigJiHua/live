/**
 * CreditCore 测试专用 mock 数据库
 * ============================================================
 * 用法：
 *   1. jest.mock('../../src/common/config/db', () => require('./helpers/creditMockDb').createMockDb());
 *   2. 在测试中 const db = require('../../src/common/config/db');
 *   3. 用 db._when(fragments, result, matcher?) 按 SQL 片段注册返回数据；
 *   4. 用 db._reset() 在 beforeEach 清空路由与调用记录。
 *
 * 匹配规则：
 *   - fragments 为字符串或字符串数组，SQL 文本必须包含全部片段（子串匹配，含换行视为普通字符）。
 *   - matcher(params) 可选，接收 SQL 参数数组，用于按 card_id 区分不同卡片。
 *   - result 传「行数组」rows，内部自动包装为 [rows]（mysql2 execute 返回 [rows, fields]），
 *     未命中任何路由时返回 [[]]（空表），模拟"查无数据"。
 *
 * 事务连接：
 *   - getConnection() 返回单例 conn（execute 走同一 routes，beginTransaction/commit/rollback/release 为 jest.fn）。
 */
function createMockDb() {
  const routes = [];

  const makeExecutor = (label) =>
    jest.fn(async (sql, params = []) => {
      // 第一轮：优先匹配非 fallback 路由（按注册顺序）
      for (const r of routes) {
        if (r.fallback) continue;
        if (r.when(sql, params)) return r.result;
      }
      // 第二轮：匹配 fallback 兜底路由
      for (const r of routes) {
        if (!r.fallback) continue;
        if (r.when(sql, params)) return r.result;
      }
      return [[]];
    });

  const db = {
    execute: null,
    getConnection: jest.fn(),
    _routes: routes,
    _conn: null,
    _getPool: null,
    getPool: jest.fn(),
    _reset() {
      routes.length = 0;
      this.execute.mockClear();
      this.getConnection.mockClear();
      if (this._conn) {
        this._conn.execute.mockClear();
        this._conn.beginTransaction.mockClear();
        this._conn.commit.mockClear();
        this._conn.rollback.mockClear();
        this._conn.release.mockClear();
      }
      this._conn = null;
    },
    /**
     * 注册 SQL 路由
     * @param {string|string[]} fragments  SQL 必须包含的片段
     * @param {Array} result              返回 [rows, fields]
     * @param {(params)=>boolean} [matcher] 按参数过滤（如 p[0]===cardId）
     */
    _when(fragments, result, matcher) {
      const frags = Array.isArray(fragments) ? fragments : [fragments];
      routes.push({
        when: (sql, params) =>
          frags.every((f) => sql.includes(f)) && (!matcher || matcher(params)),
        result: [result], // 包装为 [rows, fields]，对齐 mysql2 execute 返回
      });
    },
    /**
     * 注册兜底路由（仅在无任何非 fallback 路由命中时生效），用于 SUM 等聚合查询默认返回 0
     */
    _fallback(fragments, result, matcher) {
      const frags = Array.isArray(fragments) ? fragments : [fragments];
      routes.push({
        fallback: true,
        when: (sql, params) =>
          frags.every((f) => sql.includes(f)) && (!matcher || matcher(params)),
        result: [result],
      });
    },
  };
  db.execute = makeExecutor('db');

  db.getConnection.mockImplementation(async () => {
    if (!db._conn) {
      db._conn = {
        execute: makeExecutor('conn'),
        beginTransaction: jest.fn(),
        commit: jest.fn(),
        rollback: jest.fn(),
        release: jest.fn(),
      };
    }
    return db._conn;
  });

  // 兼容 db.getPool().getConnection()（credit.js/debit.js 事务路径）
  db.getPool.mockImplementation(() => ({
    getConnection: db.getConnection,
  }));

  return db;
}

module.exports = { createMockDb };
