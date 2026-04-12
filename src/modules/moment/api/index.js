const express = require("express");
const router = express.Router();
const momentController = require("../controller");
const authGuard = require("../../../common/middleware/authGuard");

// 所有动态路由都需要认证
router.use(authGuard);

// 【已启用】创建动态
router.post("/", momentController.create);

// 【已启用】查询所有动态（必须在 /:id 前面）
router.get("/list", momentController.list);

// 【已启用】查询今日动态（含追加）
router.get("/today", momentController.today);

// 【已启用】查询单条动态（必须在 /batch 后面）
router.get("/:id", momentController.detail);

// 【已启用】批量获取动态详情
router.post("/batch", momentController.batchDetail);

// 【已启用】更新动态
router.put("/:id", momentController.update);

// 【已启用】删除动态
router.delete("/:id", momentController.delete);

module.exports = router;
