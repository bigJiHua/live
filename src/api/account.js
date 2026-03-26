const express = require('express');
const router = express.Router();
const financeController = require('../controllers/financeController');
const authGuard = require('../middlewares/authGuard');
const pinLockGuard = require('../middlewares/pinLockGuard');

// 所有账务流水路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// 获取账务流水列表
router.get('/transactions', financeController.getTransactions);

// 获取单个账务流水详情
router.get('/transactions/:id', financeController.getTransactionById);

// 创建账务流水
router.post('/transactions', financeController.createTransaction);

// 更新账务流水
router.put('/transactions/:id', financeController.updateTransaction);

// 删除账务流水
router.delete('/transactions/:id', financeController.deleteTransaction);

// 获取分类列表
router.get('/categories', financeController.getCategories);

// 创建分类
router.post('/categories', financeController.createCategory);

// 更新分类
router.put('/categories/:id', financeController.updateCategory);

// 删除分类
router.delete('/categories/:id', financeController.deleteCategory);

// 获取财务报表
router.get('/report', financeController.getFinanceReport);

// 计算投资回报率 (IRR)
router.post('/calculate-irr', financeController.calculateIRR);

module.exports = router;
