const express = require('express');
const router = express.Router();
const cardController = require('../controller');
const createValidator = require('../../../common/middleware/validate');
const CardRules = require('../rules');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');


// 所有卡片路由都需要认证 + PIN 验证
router.use(authGuard);

// ========== 卡片管理 ==========

// 获取卡片列表
router.get('/', cardController.getList);

// ========== 共享额度池（痛点2） ==========
router.get('/pool', cardController.listPools);
router.post('/pool', cardController.createPool);
router.put('/pool/:id', cardController.updatePool);
router.delete('/pool/:id', pinLockGuard, cardController.deletePool);
router.post('/pool/assign', cardController.assignCardPool);

// 获取单个卡片详情
router.get('/:id', cardController.getById);

// 创建卡片
router.post('/', createValidator(CardRules.createCard), cardController.create);

// 批量更新排序（必须在 /:id 之前，避免被 :id 捕获）
router.put('/sort', cardController.updateSortBatch);

// 更新卡片
router.put('/:id', createValidator(CardRules.updateCard), cardController.update);

// 删除卡片
router.delete('/:id', pinLockGuard, cardController.delete);

module.exports = router;
