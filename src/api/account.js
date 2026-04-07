const express = require('express');
const router = express.Router();
const financeController = require('../controllers/financeController');
const authGuard = require('../middlewares/authGuard');
const pinLockGuard = require('../middlewares/pinLockGuard');

// 所有账务流水路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 收支记录 ==========

// 获取收支列表
router.get('/transactions', financeController.getTransactions);

// 获取单条收支详情
router.get('/transactions/:id', financeController.getTransactionById);

// 创建收支记录
router.post('/transactions', financeController.createTransaction);

// 更新收支记录
router.put('/transactions/:id', financeController.updateTransaction);

// 删除收支记录
router.delete('/transactions/:id', financeController.deleteTransaction);

// ========== 本月收支统计 ==========

// 获取本月收支统计
router.get('/month-stats', financeController.getMonthStats);

// ========== 总资产统计 ==========

// 获取预估总资产
router.get('/total-assets', financeController.getTotalAssets);

// 获取资产明细（可按类型筛选）
router.get('/asset-detail', financeController.getAssetDetail);

// ========== 旧接口（保持兼容）==========

// 获取财务报表
router.get('/report', financeController.getFinanceReport);

// 计算投资回报率 (IRR)
router.post('/calculate-irr', financeController.calculateIRR);

module.exports = router;
