const express = require('express');
const router = express.Router();
const accountController = require('../controller');
const debitController = require('../controller/debit');
const creditController = require('../controller/credit');
const createValidator = require('../../../common/middleware/validate');
const AccountRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');

// 所有账务流水路由都需要认证
router.use(authGuard);

// ========== 收支记录（通用查询） ==========

// 获取收支列表
router.get('/', accountController.getList);

// 根据卡片获取流水
router.get('/list', accountController.getByCard);

// 获取转账明细列表（需在 /:id 之前注册，避免被 :id 捕获）
router.get('/transfer/list', accountController.getTransferList);

// 获取本月收支统计（需在 /:id 之前注册）
router.get('/stats/month', accountController.getMonthStats);

// 获取全量统计（总资产、卡片数量、欠款等，需在 /:id 之前注册）
router.get('/stats/all', accountController.getAllStats);

// 所有银行卡近 N 个月支出/收入笔数（按卡聚合，需在 /:id 之前注册）
router.get('/stats/cards-flow', accountController.getCardsFlowStats);

// 获取单条收支详情
router.get('/:id', accountController.getById);

// 修改收支备注（仅备注）
router.patch('/:id/remark', accountController.updateRemark);

// ========== 借记卡写入（/account/debit/**） ==========

// 创建借记卡收支记录（普通收支/转账/提现/还款支出流水）
router.post('/debit', createValidator(AccountRules.create), debitController.create);

// 更新借记卡收支记录
router.put('/debit/:id', createValidator(AccountRules.update), debitController.update);

// 借记卡冲正
router.post('/debit/:id/reverse', debitController.reverseDebit);

// 转账冲正（自转/提现撤销）
router.post('/debit/:id/reverse/transfer', debitController.reverseTransfer);

// ========== 信用卡写入（/account/credit/**） ==========

// 创建信用卡消费记录
router.post('/credit', createValidator(AccountRules.create), creditController.create);

// 更新信用卡消费记录
router.put('/credit/:id', createValidator(AccountRules.update), creditController.update);

// 信用卡消费冲正
router.post('/credit/:id/reverse/expense', creditController.reverseCreditExpense);

// 信用卡还款冲正
router.post('/credit/:id/reverse/repay', creditController.reverseCreditRepay);

module.exports = router;
