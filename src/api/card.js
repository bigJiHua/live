const express = require('express');
const router = express.Router();
const cardController = require('../controllers/cardController');
const createValidator = require('../middlewares/validate');
const CardRules = require('../Rules/card');
const authGuard = require('../middlewares/authGuard');
const pinLockGuard = require('../middlewares/pinLockGuard');

// 所有卡片路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 卡片管理 ==========

// 获取卡片列表
router.get('/', cardController.getList);

// 获取单个卡片详情
router.get('/:id', cardController.getById);

// 创建卡片
router.post('/', createValidator(CardRules.createCard), cardController.create);

// 更新卡片
router.put('/:id', createValidator(CardRules.updateCard), cardController.update);

// 删除卡片
router.delete('/:id', cardController.delete);

module.exports = router;
