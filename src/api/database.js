const express = require('express');
const router = express.Router();
const db = require('../config/db');

// 获取所有数据库
router.get('/databases', async (req, res) => {
  try {
    const [rows] = await db.execute('SHOW DATABASES');
    res.json({ databases: rows });
  } catch (error) {
    res.status(500).json({ error: error.message });
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
    console.error('获取表列表失败:', error);
    res.status(500).json({
      error: error.message,
      code: error.code,
      errno: error.errno
    });
  }
});

// 获取表结构
router.get('/table/:tableName/structure', async (req, res) => {
  try {
    const { tableName } = req.params;
    const [rows] = await db.execute(`DESCRIBE ${tableName}`);
    res.json({ structure: rows });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 获取表数据
router.get('/table/:tableName/data', async (req, res) => {
  try {
    const { tableName } = req.params;
    const { page = 1, limit = 20 } = req.query;
    const offset = (page - 1) * limit;

    const [rows] = await db.execute(
      `SELECT * FROM ${tableName} LIMIT ? OFFSET ?`,
      [parseInt(limit), parseInt(offset)]
    );

    // 获取总数
    const [count] = await db.execute(`SELECT COUNT(*) as total FROM ${tableName}`);

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
    res.status(500).json({ error: error.message });
  }
});

// 执行 SQL 查询
router.post('/execute', async (req, res) => {
  try {
    const { sql } = req.body;

    if (!sql) {
      return res.status(400).json({ error: 'SQL 语句不能为空' });
    }

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
    res.json({
      success: false,
      error: error.message,
      code: error.code,
      sqlState: error.sqlState
    });
  }
});

// 获取表的索引
router.get('/table/:tableName/indexes', async (req, res) => {
  try {
    const { tableName } = req.params;
    const [rows] = await db.execute(`SHOW INDEX FROM ${tableName}`);
    res.json({ indexes: rows });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 获取表创建语句
router.get('/table/:tableName/show-create', async (req, res) => {
  try {
    const { tableName } = req.params;
    const [rows] = await db.execute(`SHOW CREATE TABLE ${tableName}`);
    res.json({ createStatement: rows[0]['Create Table'] });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// 删除表
router.delete('/table/:tableName', async (req, res) => {
  try {
    const { tableName } = req.params;
    console.log('删除表:', tableName);

    const [result] = await db.execute(`DROP TABLE ${tableName}`);

    res.json({
      success: true,
      message: `表 ${tableName} 已删除`
    });
  } catch (error) {
    console.error('删除表失败:', error);
    res.status(500).json({
      success: false,
      error: error.message,
      code: error.code
    });
  }
});

// 清空表数据
router.delete('/table/:tableName/data', async (req, res) => {
  try {
    const { tableName } = req.params;
    console.log('清空表数据:', tableName);

    const [result] = await db.execute(`DELETE FROM ${tableName}`);

    res.json({
      success: true,
      message: `表 ${tableName} 数据已清空`,
      affectedRows: result.affectedRows
    });
  } catch (error) {
    console.error('清空表数据失败:', error);
    res.status(500).json({
      success: false,
      error: error.message,
      code: error.code
    });
  }
});

module.exports = router;
