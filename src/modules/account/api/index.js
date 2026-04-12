const express = require('express');
const router = express.Router();
const accountController = require('../controller');
const createValidator = require('../../../common/middleware/validate');
const AccountRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有账务流水路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 收支记录 ==========

// 获取收支列表
router.get('/', accountController.getList);

// 根据卡片获取流水
router.get('/list', accountController.getByCard);

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

// ========== 统计 ==========

// 获取本月收支统计
router.get('/stats/month', accountController.getMonthStats);

// 获取全量统计（总资产、卡片数量、欠款等）
router.get('/stats/all', accountController.getAllStats);

module.exports = router;
