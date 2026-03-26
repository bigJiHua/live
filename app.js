// 1. 环境变量必须最先加载
require('dotenv').config();

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const path = require('path');

// 创建 Express 实例
const app = express();

// --- 中间件层 ---
app.use(helmet()); // 安全头
app.use(cors()); // 跨域
app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev')); // 日志
app.use(express.json()); // JSON 解析
app.use(express.urlencoded({ extended: true })); // URL 编码解析

// --- 路由层 ---
const apiRouter = require('./src/api');
app.use('/api/v1', apiRouter);

// --- 静态资源与健康检查 ---
app.use('/public', express.static(path.join(__dirname, 'public')));
app.get('/health', (req, res) => res.json({ status: 'ok' }));

// 根路由 - 管理员首次注册页面
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin-register.html'));
});

// 数据库连接测试
app.get('/test-db', async (req, res) => {
  try {
    const { execute } = require('./src/config/db');
    const [rows] = await execute('SELECT 1 as test');
    res.json({
      status: 'ok',
      message: '数据库连接成功',
      result: rows
    });
  } catch (error) {
    console.error('数据库连接测试失败:', error);
    res.status(500).json({
      status: 'error',
      message: '数据库连接失败',
      error: error.message,
      code: error.code,
      errno: error.errno
    });
  }
});

// --- 错误处理 ---
const { errorHandler, notFoundHandler } = require('./src/middlewares/errorMiddleware');

// 404 处理
app.use(notFoundHandler);

// 全局错误处理
app.use(errorHandler);

// --- 启动逻辑 ---
if (require.main === module) {
  const PORT = process.env.PORT || 3001;
  app.listen(PORT, () => {
    console.log(`🚀 服务已启动: http://localhost:${PORT}`);
    console.log(`🌍 环境: ${process.env.NODE_ENV || 'dev'}`);
    console.log(`📡 API 地址: http://localhost:${PORT}/api/v1`);
  });
}

module.exports = app;
