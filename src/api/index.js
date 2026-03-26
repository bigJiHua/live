const express = require('express');
const router = express.Router();

// 导入各个路由模块
const authRouter = require('./auth');
const securityRouter = require('./security');
const accountRouter = require('./account');
const userRouter = require('./user');
const databaseRouter = require('./database');

// 注册子路由
// 用户登录
router.use('/auth', authRouter);
/* 权限路由 */
router.use('/security', securityRouter);
router.use('/account', accountRouter);
router.use('/user', userRouter);
router.use('/database', databaseRouter);
/* 权限路由 */
// API 版本信息
router.get('/', (req, res) => {
  res.json({
    version: 'v1',
    message: '生活管理系统 API',
    endpoints: {
      auth: '/api/v1/auth',
      security: '/api/v1/security',
      account: '/api/v1/account',
      user: '/api/v1/user',
      database: '/api/v1/database'
    },
    Update_at: '2026年3月26日'
  });
});

module.exports = router;
