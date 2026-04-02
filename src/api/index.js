/**
 * API 统一路由入口
 * 说明: 将所有路由模块集中管理,统一 API 版本
 */
const express = require("express");
const router = express.Router();

// 导入子路由模块
const authRouter = require("./auth");
const securityRouter = require("./security");
const accountRouter = require("./account");
const userRouter = require("./user");
const databaseRouter = require("./database");
const authGuard = require("../middlewares/authGuard");

// 注册子路由

router.use("/auth", authRouter); // 【已启用】
router.use("/security", securityRouter);
router.use("/account", accountRouter);
router.use("/user", authGuard, userRouter); // 【已启用】
router.use("/database", databaseRouter); // 【已启用】

module.exports = router;
