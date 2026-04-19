const express = require('express');
const router = express.Router();
const fixedAssetController = require('../controller');
const authGuard = require('../../../common/middleware/authGuard');
const pinLockGuard = require('../../../common/middleware/pinLockGuard');

// 所有路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 固定资产 CRUD ==========

// 获取所有固定资产（自动触发折旧巡检）
router.get('/list', fixedAssetController.list);

// 获取回收站
router.get('/recycle-bin', fixedAssetController.recycleBin);

// 获取单个资产详情
router.get('/:id', fixedAssetController.getOne);

// 创建固定资产
router.post('/', fixedAssetController.create);

// 更新固定资产
router.put('/:id', fixedAssetController.update);

// 变更资产状态
router.put('/:id/status', fixedAssetController.changeStatus);

// 删除资产（软删除）
router.delete('/:id', fixedAssetController.delete);

// 恢复删除的资产
router.put('/restore/:id', fixedAssetController.restore);

// 永久删除资产
router.delete('/permanent/:id', fixedAssetController.permanentDelete);

// 手动触发折旧巡检
router.post('/depreciate', fixedAssetController.depreciate);

module.exports = router;
