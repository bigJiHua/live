const express = require('express');
const router = express.Router();
const accountBalanceController = require('../controller/balanceController');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有余额路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 账户余额管理 ==========

// 获取所有账户余额列表
router.get('/', accountBalanceController.getList);

// 获取单张卡/账户余额
router.get('/:cardId', accountBalanceController.getByCardId);

// 初始化虚拟账户（现金、微信、支付宝）
router.post('/init-virtual', accountBalanceController.initVirtual);

// 重建所有账户余额（从收支表重新计算）
router.post('/rebuild', accountBalanceController.rebuild);

module.exports = router;
