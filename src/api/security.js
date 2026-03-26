const express = require('express');
const router = express.Router();
const securityController = require('../controllers/securityController');
const authGuard = require('../middlewares/authGuard');

// 验证 PIN 码
router.post('/pin/verify', authGuard, securityController.verifyPin);

// 设置 PIN 码
router.post('/pin/set', authGuard, securityController.setPin);

// 修改 PIN 码
router.post('/pin/change', authGuard, securityController.changePin);

// 重置 PIN 码 (需要邮箱验证)
router.post('/pin/reset', securityController.resetPin);

module.exports = router;
