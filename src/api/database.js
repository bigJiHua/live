const express = require('express');
const router = express.Router();
const db = require('../common/config/db');
const authGuard = require('../common/middleware/authGuard');
const pinLockGuard = require('../common/middleware/pinLockGuard');

function sendDbError(res, error, context = '数据库管理接口异常') {
  console.error(context, error);
  return res.status(500).json({
    status: 500,
    message: '数据库管理接口异常，请查看服务端日志'
  });
}

function safeTableName(tableName) {
  if (!/^[A-Za-z0-9_]+$/.test(tableName || '')) {
    const err = new Error('非法表名');
    err.status = 400;
    throw err;
  }
  return `\`${tableName}\``;
}

function assertReadOnlySql(sql) {
  const normalized = String(sql || '').trim();
  if (!/^(select|show|describe|desc|explain)\b/i.test(normalized)) {
    const err = new Error('仅允许执行只读 SQL');
    err.status = 400;
    throw err;
  }
}

async function requireAdmin(req, res, next) {
  try {
    const [rows] = await db.execute(
      'SELECT identity FROM user_info WHERE id = ? AND is_deleted = 0 LIMIT 1',
      [req.userId]
    );

    if (rows[0]?.identity !== 'admin') {
      return res.status(403).json({ status: 403, message: '无权限访问数据库管理功能' });
    }

    next();
  } catch (error) {
    return sendDbError(res, error, '数据库管理权限检查失败');
  }
}

router.use(authGuard, requireAdmin);

// 获取所有数据库
router.get('/databases', async (req, res) => {
  try {
    const [rows] = await db.execute('SHOW DATABASES');
    res.json({ databases: rows });
  } catch (error) {
    sendDbError(res, error, '获取数据库列表失败');
  }
});

// 获取当前数据库的所有表
router.get('/tables', async (req, res) => {
  try {
    console.log('正在获取数据库表列表...');
    const [rows] = await db.execute('SHOW TABLES');
    console.log('获取到', rows.length, '个表');
    res.json({ tables: rows });
  } catch (error) {
    sendDbError(res, error, '获取表列表失败');
  }
});

// 获取表结构
router.get('/table/:tableName/structure', async (req, res) => {
  try {
    const { tableName } = req.params;
    const safeName = safeTableName(tableName);
    const [rows] = await db.execute(`DESCRIBE ${safeName}`);
    res.json({ structure: rows });
  } catch (error) {
    const status = error.status || 500;
    if (status < 500) return res.status(status).json({ status, message: error.message });
    sendDbError(res, error, '获取表结构失败');
  }
});

// 获取表数据
router.get('/table/:tableName/data', async (req, res) => {
  try {
    const { tableName } = req.params;
    const { page = 1, limit = 20 } = req.query;
    const offset = (page - 1) * limit;
    const safeName = safeTableName(tableName);

    const [rows] = await db.execute(
      `SELECT * FROM ${safeName} LIMIT ? OFFSET ?`,
      [parseInt(limit), parseInt(offset)]
    );

    // 获取总数
    const [count] = await db.execute(`SELECT COUNT(*) as total FROM ${safeName}`);

    res.json({
      data: rows,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total: count[0].total,
        totalPages: Math.ceil(count[0].total / limit)
      }
    });
  } catch (error) {
    const status = error.status || 500;
    if (status < 500) return res.status(status).json({ status, message: error.message });
    sendDbError(res, error, '获取表数据失败');
  }
});

// 执行 SQL 查询
router.post('/execute', async (req, res) => {
  try {
    const { sql } = req.body;

    if (!sql) {
      return res.status(400).json({ error: 'SQL 语句不能为空' });
    }

    assertReadOnlySql(sql);
    console.log('执行 SQL:', sql);

    const [rows, fields] = await db.execute(sql);

    res.json({
      success: true,
      data: rows,
      fields: fields ? fields.map(field => ({
        name: field.name,
        type: field.type,
        flags: field.flags
      })) : null,
      rowCount: Array.isArray(rows) ? rows.length : 0
    });
  } catch (error) {
    const status = error.status || 500;
    if (status < 500) {
      return res.status(status).json({ success: false, message: error.message });
    }
    sendDbError(res, error, '执行 SQL 失败');
  }
});

// 获取表的索引
router.get('/table/:tableName/indexes', async (req, res) => {
  try {
    const { tableName } = req.params;
    const safeName = safeTableName(tableName);
    const [rows] = await db.execute(`SHOW INDEX FROM ${safeName}`);
    res.json({ indexes: rows });
  } catch (error) {
    const status = error.status || 500;
    if (status < 500) return res.status(status).json({ status, message: error.message });
    sendDbError(res, error, '获取表索引失败');
  }
});

// 获取表创建语句
router.get('/table/:tableName/show-create', async (req, res) => {
  try {
    const { tableName } = req.params;
    const safeName = safeTableName(tableName);
    const [rows] = await db.execute(`SHOW CREATE TABLE ${safeName}`);
    res.json({ createStatement: rows[0]['Create Table'] });
  } catch (error) {
    const status = error.status || 500;
    if (status < 500) return res.status(status).json({ status, message: error.message });
    sendDbError(res, error, '获取建表语句失败');
  }
});

router.delete('/table/:tableName', pinLockGuard, (req, res) => {
  res.status(403).json({
    success: false,
    message: '已禁用在线删表操作'
  });
});

router.delete('/table/:tableName/data', pinLockGuard, (req, res) => {
  res.status(403).json({
    success: false,
    message: '已禁用在线清表操作'
  });
});

module.exports = router;
