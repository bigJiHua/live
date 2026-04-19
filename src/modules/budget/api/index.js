const express = require('express');
const router = express.Router();
const budgetController = require('../controller');
const createValidator = require('../../../common/middleware/validate');
const BudgetRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 预算 CRUD ==========

// 获取所有预算
router.get('/list', budgetController.list);

// 获取预算统计
router.get('/stats', budgetController.stats);

// 获取单个预算
router.get('/:id', createValidator(BudgetRules.idParam), budgetController.getOne);

// 创建预算
router.post('/', createValidator(BudgetRules.create), budgetController.create);

// 更新预算
router.put('/:id', createValidator(BudgetRules.idParam), createValidator(BudgetRules.update), budgetController.update);

// 删除预算（硬删除）
router.delete('/:id', createValidator(BudgetRules.idParam), budgetController.delete);

module.exports = router;
