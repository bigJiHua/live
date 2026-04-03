const express = require("express");
const router = express.Router();
const createValidator = require("../middlewares/validate");
const authController = require("../controllers/authController");
const authGuard = require("../middlewares/authGuard");
const accountLockCheck = require("../middlewares/accountLockCheck");
const AuthRules = require("../Rules/auth");
/**
 *   非权限接口部分
 */
// 用户注册
// router.post('/register', authController.register);

// 【已启用】
// 获取AES临时加密密钥
router.get("/handshake", authController.getHandshake);
// 【已启用】
// 用户登录 (前置检查：账户锁定状态)
router.post(
  "/login",
  createValidator(AuthRules.login),
  async (req, res, next) => {
    await accountLockCheck(req, res, next);
  },
  authController.login
);
// 【已启用】
// 检查是否已有管理员 (首次注册使用)
router.get("/check-admin", authController.checkAdmin);
// 【已启用】
// 管理员首次注册 (只能注册一次)
router.post("/admin-register", authController.adminRegister);

// 刷新 Token
router.post("/refresh", authController.refreshToken);

/**
 * 权限接口部分
 */
// 获取用户信息 (需要认证)
router.get("/me", authGuard, authController.getCurrentUser);

module.exports = router;
