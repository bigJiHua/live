const mysql = require('mysql2/promise');
require('dotenv').config();

// 数据库连接池
let pool = null;

/**
 * 初始化数据库连接池
 */
function initPool() {
  if (pool) {
    return pool;
  }

  pool = mysql.createPool({
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'live',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
    enableKeepAlive: true,
    keepAliveInitialDelay: 0,
  });

  return pool;
}

/**
 * 获取数据库连接池
 */
function getPool() {
  if (!pool) {
    initPool();
  }
  return pool;
}

/**
 * 执行查询（简化版）
 */
async function execute(query, params = []) {
  const pool = getPool();
  return pool.execute(query, params);
}

module.exports = {
  initPool,
  getPool,
  execute,
};
