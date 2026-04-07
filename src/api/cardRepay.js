const express = require('express');
const router = express.Router();
const cardRepayController = require('../controllers/cardRepayController');
const createValidator = require('../middlewares/validate');
const CardRules = require('../Rules/card');
const authGuard = require('../middlewares/authGuard');
const pinLockGuard = require('../middlewares/pinLockGuard');

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
