/**
 * API 统一路由入口
 * 说明: 将所有路由模块集中管理,统一 API 版本
 */
const express = require("express");
const router = express.Router();

// 导入子路由模块
const authRouter = require("../modules/auth/api");
const securityRouter = require("../modules/security/api");
const accountRouter = require("../modules/account/api");
const accountBalanceRouter = require("../modules/account/api/balance");
const categoryRouter = require("../modules/category/api");
const cardRouter = require("../modules/card/api");
const cardBillRouter = require("../modules/card/api/bill");
const cardRepayRouter = require("../modules/card/api/repay");
const userRouter = require("../modules/user/api");
const databaseRouter = require("./database");
const uploadRouter = require("../modules/upload/api");
const momentRouter = require("../modules/moment/api");
const bankRouter = require("../modules/category/api/bank");
const assetRouter = require("../modules/asset/api");
const todoRouter = require("../modules/todo/api");
const workRouter = require("../modules/work/api");
const fixedAssetRouter = require("../modules/fixed_asset/api");
const budgetRouter = require("../modules/budget/api");

// 注册子路由
router.use("/auth", authRouter);       // 【已启用】认证模块
router.use("/security", securityRouter); // 【已启用】安全模块
router.use("/account", accountRouter);   // 【已启用】账户收支
router.use("/accountBalance", accountBalanceRouter); // 账户余额
router.use("/category", categoryRouter); // 【已启用】分类管理
router.use("/card/bill", cardBillRouter);   // 卡片账单
router.use("/card/repay", cardRepayRouter);  // 卡片还款
router.use("/card", cardRouter);         // 【已启用】卡片管理
router.use("/user", userRouter);         // 【已启用】用户管理
router.use("/database", databaseRouter); // 【已启用】临时数据库操控
router.use("/upload", uploadRouter);     // 【已启用】文件上传
router.use("/moment", momentRouter);      // 【已启用】动态/日记
router.use("/bank", bankRouter);          // 银行分类
router.use("/asset", assetRouter);       // 【已启用】资产快照与登记
router.use("/todo", todoRouter);          // 待办日程
router.use("/work", workRouter);           // 工作与工资
router.use("/fixedAsset", fixedAssetRouter); // 固定资产管理
router.use("/budget", budgetRouter);          // 预算管理

module.exports = router;
