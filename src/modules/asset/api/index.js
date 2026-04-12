const express = require('express');
const router = express.Router();
const assetController = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 资产快照 ==========

// 获取首页资产数据（最新快照 + 最新登记）
router.get('/home', assetController.getHomeAsset);

// 获取资产快照列表
router.get('/snapshot/list', assetController.getSnapshots);

// ========== 资产登记 ==========

// 获取用户登记列表
router.get('/register/list', assetController.getRegisters);

// 用户登记资产
router.post('/register', assetController.createRegister);

// 更新登记记录
router.put('/register/:id', assetController.updateRegister);

// 删除登记记录
router.delete('/register/:id', assetController.deleteRegister);

module.exports = router;
