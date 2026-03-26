const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const authGuard = require('../middlewares/authGuard');

// 用户注册
router.post('/register', authController.register);

// 用户登录
router.post('/login', authController.login);

// 检查是否已有管理员 (首次注册使用)
router.get('/check-admin', authController.checkAdmin);

// 管理员首次注册 (只能注册一次)
router.post('/admin-register', authController.adminRegister);

// 获取用户信息 (需要认证)
router.get('/me', authGuard, authController.getCurrentUser);

// 刷新 Token
router.post('/refresh', authController.refreshToken);

module.exports = router;
