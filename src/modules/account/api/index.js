const express = require('express');
const router = express.Router();
const accountController = require('../controller');
const createValidator = require('../../../common/middleware/validate');
const AccountRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');

// 所有账务流水路由都需要认证
router.use(authGuard);

// ========== 收支记录 ==========

// 获取收支列表
router.get('/', accountController.getList);

// 根据卡片获取流水
router.get('/list', accountController.getByCard);

// 获取单条收支详情
router.get('/:id', accountController.getById);

// 创建收支记录
router.post('/', createValidator(AccountRules.create), accountController.create);

// 更新收支记录
router.put('/:id', createValidator(AccountRules.update), accountController.update);

// 修改收支备注（仅备注）
router.patch('/:id/remark', accountController.updateRemark);

// 冲正流水 - 借记卡
router.post('/:id/reverse/debit', accountController.reverseDebit);

// 冲正流水 - 信用卡消费
router.post('/:id/reverse/credit-expense', accountController.reverseCreditExpense);

// 冲正流水 - 信用卡还款撤销
router.post('/:id/reverse/credit-repay', accountController.reverseCreditRepay);

// 冲正流水 - 转账撤销（自转/提现）
router.post('/:id/reverse/transfer', accountController.reverseTransfer);

// 转账明细列表
router.get('/transfer/list', accountController.getTransferList);

// ========== 统计 ==========

// 获取本月收支统计
router.get('/stats/month', accountController.getMonthStats);

// 获取全量统计（总资产、卡片数量、欠款等）
router.get('/stats/all', accountController.getAllStats);

module.exports = router;
