const express = require("express");
const router = express.Router();
const securityController = require("../controller");
const authGuard = require("../../../common/middleware/authGuard");
const createValidator = require("../../../common/middleware/validate");
const AuthRules = require("../../auth/Rules");

// 获取 PIN状态
router.get("/pin/check", authGuard, securityController.PinStatus);
// 验证 PIN
router.post(
  "/pin/verify",
  authGuard,
  createValidator(AuthRules.verifyPin),
  securityController.verifyPin
);

// 设置 PIN
router.post(
  "/pin/set",
  authGuard,
  createValidator(AuthRules.setPin),
  securityController.setPin
);

// 修改 PIN
router.post(
  "/pin/change",
  authGuard,
  createValidator(AuthRules.changePin),
  securityController.changePin
);
// 【未启用】
// 重置 PIN
router.post(
  "/pin/reset",
  authGuard,
  createValidator(AuthRules.resetPin),
  securityController.resetPin
);

module.exports = router;
