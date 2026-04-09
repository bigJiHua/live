const express = require("express");
const router = express.Router();
const cardBillController = require("../controllers/cardBillController");
const createValidator = require("../middlewares/validate");
const CardRules = require("../Rules/card");
const authGuard = require("../middlewares/authGuard");
const pinLockGuard = require("../middlewares/pinLockGuard");

// 所有账单路由都需要认证 + PIN 验证
router.use(authGuard);
router.use(pinLockGuard);

// ========== 账单管理 ==========

// 获取账单列表
router.get("/", cardBillController.getList);

// 获取单个账单详情
router.get("/:id", cardBillController.getById);

// 获取卡片最新账单
router.get("/card/:cardId/latest", cardBillController.getLatestByCardId);

// 重建账单（从流水重新计算）
router.post("/card/:cardId/rebuild", cardBillController.rebuild);

// 创建账单
router.post(
  "/",
  createValidator(CardRules.createBill),
  cardBillController.create
);

// 更新账单
router.put(
  "/:id",
  createValidator(CardRules.updateBill),
  cardBillController.update
);

// 删除账单
router.delete("/:id", cardBillController.delete);

module.exports = router;
