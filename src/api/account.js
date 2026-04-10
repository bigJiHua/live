const express = require('express');
const router = express.Router();
const accountController = require('../controllers/accountController');
const createValidator = require('../middlewares/validate');
const AccountRules = require('../Rules/account');
const authGuard = require('../middlewares/authGuard');
const pinLockGuard = require('../middlewares/pinLockGuard');

// 所有账务流水路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 收支记录 ==========

// 获取收支列表
router.get('/', accountController.getList);

// 获取单条收支详情
router.get('/:id', accountController.getById);

// 创建收支记录
router.post('/', createValidator(AccountRules.create), accountController.create);

// 修改收支备注（仅备注）
router.patch('/:id/remark', accountController.updateRemark);

// 冲正流水 - 借记卡
router.post('/:id/reverse/debit', accountController.reverseDebit);

// 冲正流水 - 信用卡消费
router.post('/:id/reverse/credit-expense', accountController.reverseCreditExpense);

// 冲正流水 - 信用卡还款撤销
router.post('/:id/reverse/credit-repay', accountController.reverseCreditRepay);

// ========== 本月收支统计 ==========

// 获取本月收支统计
router.get('/stats/month', accountController.getMonthStats);

module.exports = router;
