const express = require("express");
const router = express.Router();
const busBankController = require("../controller/bankController");
const authGuard = require("../../../common/middleware/authGuard");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");

// 所有银行分类路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 银行分类管理 ==========

// 获取银行分类列表（自动初始化虚拟银行分类）
router.get("/", busBankController.getList);

// 获取单个银行分类详情
router.get("/:id", busBankController.getById);

// 初始化虚拟银行分类
router.post("/init-virtual", busBankController.initVirtual);

// 创建银行分类
router.post("/", busBankController.create);

// 更新银行分类
router.put("/:id", busBankController.update);

// 删除银行分类
router.delete("/:id", busBankController.delete);

module.exports = router;
