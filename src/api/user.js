const express = require("express");
const router = express.Router();
const authController = require("../controllers/authController");
const authGuard = require("../middlewares/authGuard");
const UserRules = require("../Rules/user");
const createValidator = require("../middlewares/validate");
const emailCodeRateLimit = require("../middlewares/emailCodeRateLimit");

// 所有用户路由都需要认证
router.use(authGuard);

// 【已启用】
// 更新用户信息
router.put(
  "/profile",
  createValidator(UserRules.profile),
  authController.updateProfile
);

// 获取用户设置
router.get("/settings", authController.getSettings);

// 更新用户设置
router.put("/settings", authController.updateSettings);

// 【启用】发送邮箱验证码（增加频率限制中间件）
router.post(
  "/email/send-code",
  emailCodeRateLimit,
  authController.sendEmailCode
);

// 【启用】修改邮箱（需要验证码）
router.put(
  "/email/change",
  createValidator(UserRules.emailChange),
  authController.changeEmail
);

// 【启用】修改密码（需要邮箱验证码）
router.put(
  "/password/change",
  createValidator(UserRules.passwordChangeWithCode),
  authController.changePasswordWithCode
);

module.exports = router;
