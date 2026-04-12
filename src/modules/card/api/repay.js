const express = require('express');
const router = express.Router();
const cardRepayController = require('../controller/repayController');
const createValidator = require('../../../common/middleware/validate');
const CardRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有还款路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 还款记录管理 ==========

// 获取还款记录列表
router.get('/', cardRepayController.getList);

// 获取单个还款记录详情
router.get('/:id', cardRepayController.getById);

// 创建还款记录
router.post('/', createValidator(CardRules.createRepay), cardRepayController.create);

// 更新还款记录
router.put('/:id', createValidator(CardRules.updateRepay), cardRepayController.update);

// 删除还款记录
router.delete('/:id', cardRepayController.delete);

module.exports = router;
