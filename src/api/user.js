const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const authGuard = require('../middlewares/authGuard');

// 所有用户路由都需要认证
router.use(authGuard);

// 更新用户信息
router.put('/profile', authController.updateProfile);

// 修改密码
router.post('/password/change', authController.changePassword);

// 获取用户设置
router.get('/settings', authController.getSettings);

// 更新用户设置
router.put('/settings', authController.updateSettings);

module.exports = router;
