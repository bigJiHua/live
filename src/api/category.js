const express = require("express");
const router = express.Router();
const categoryController = require("../controllers/categoryController");
const createValidator = require("../middlewares/validate");
const CategoryRules = require("../Rules/category");
const authGuard = require("../middlewares/authGuard");
const pinLockGuard = require("../middlewares/pinLockGuard");

// 所有分类路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 分类管理 ==========

// 获取分类列表（支持按类型筛选 ?type=income/expense/asset/fixed/bank）
router.get("/", categoryController.getList);

// 获取单个分类详情
router.get("/:id", categoryController.getById);

// 创建分类（自动分配sort）
router.post(
  "/",
  createValidator(CategoryRules.create),
  categoryController.create
);

// 更新分类
router.put(
  "/:id",
  createValidator(CategoryRules.update),
  categoryController.update
);

// 删除分类
router.delete("/:id", categoryController.delete);

module.exports = router;
