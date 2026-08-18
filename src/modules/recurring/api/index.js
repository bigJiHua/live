const express = require("express");
const router = express.Router();
const recurringController = require("../controller");
const authGuard = require("../../../common/middleware/authGuard");
const pinLockGuard = require("../../../common/middleware/pinLockGuard");

router.use(authGuard);

router.get("/list", recurringController.list);
router.get("/installments", recurringController.installments);
router.get("/summary", recurringController.summary);
router.get("/:id", recurringController.detail);
router.post("/", recurringController.create);
router.put("/:id", recurringController.update);
router.put("/:id/month-status", recurringController.updateMonthStatus);
// 分期入账：触发月份直接入账（后端校验账单周期 + 防重复）
router.post("/:id/enter", recurringController.enterInstallment);
// 分期中止：仅未入账月份可中止，已入账月份需用户去账单手动冲正
router.post("/:id/abort", recurringController.abortInstallment);
router.delete("/:id", pinLockGuard, recurringController.delete);

module.exports = router;
