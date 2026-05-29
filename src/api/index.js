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
const dashboardRouter = require("../modules/dashboard/api");
const resourceRouter = require("../modules/resource/api");
const bankCategoryRouter = require("../modules/bankCategory/api");
const flowRouter = require("../modules/flow/api");
const dataManagerRouter = require("../modules/dataManager/api");
const fundRouter = require("../modules/fund/api");
const recurringRouter = require("../modules/recurring/api");
const geoRouter = require("../modules/geo/api");

// 注册子路由
router.use("/auth", authRouter); // 认证模块
router.use("/security", securityRouter); // 安全设置模块
// 注册业务路由
router.use("/account", accountRouter); // 账户收支
router.use("/accountBalance", accountBalanceRouter); // 账户余额
router.use("/category", categoryRouter); // 分类管理
router.use("/card/bill", cardBillRouter); // 卡片账单
router.use("/card/repay", cardRepayRouter); // 卡片还款
router.use("/card", cardRouter); // 卡片管理
router.use("/user", userRouter); // 用户管理
router.use("/database", databaseRouter); // 数据库只读管理（管理员）
router.use("/upload", uploadRouter); // 文件上传
router.use("/moment", momentRouter); // 动态/日记
router.use("/bank", bankRouter); // 银行分类
router.use("/asset", assetRouter); // 资产快照与登记
router.use("/todo", todoRouter); // 待办日程
router.use("/work", workRouter); // 工作与工资
router.use("/fixedAsset", fixedAssetRouter); // 固定资产管理
router.use("/budget", budgetRouter); // 预算管理
// router.use("/dashboard", dashboardRouter); // 仪表盘（桌面端新增）【未启用】
router.use("/resource", resourceRouter); // 文件资源管理（桌面端新增）
router.use("/bank-category", bankCategoryRouter); // 银行分类管理（桌面端新增）
router.use("/flow", flowRouter); // 流水详情/日历（桌面端新增）
router.use("/data-manager", dataManagerRouter); // 数据管理（备份/导出/导入）
router.use("/fund", fundRouter); // 理财投资模块
router.use("/recurring", recurringRouter); // 固定周期支出提醒
router.use("/geo", geoRouter); // 地理位置代理（IP定位 / 逆地理编码）

module.exports = router;
