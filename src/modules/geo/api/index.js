/**
 * 地理位置代理路由
 * 将第三方 API Key 收口到服务端，前端不直接暴露 Key
 *
 * ┌──────────────────┬──────────┬─────────────────────────────┐
 * │ 端点             │ 鉴权     │ 说明                        │
 * ├──────────────────┼──────────┼─────────────────────────────┤
 * │ /geo/network     │ 无       │ 客户端上下文采集（登录前）   │
 * │ /geo/ip          │ authGuard│ IP 定位（登录后）           │
 * │ /geo/regeo       │ authGuard│ 逆地理编码（登录后）        │
 * └──────────────────┴──────────┴─────────────────────────────┘
 */
const express = require("express");
const router = express.Router();
const ctrl = require("../controller");
const authGuard = require("../../../common/middleware/authGuard");

// 无需鉴权 — 客户端上下文采集时调用（登录前）
router.get("/network", ctrl.getNetworkInfo);

// 需要鉴权 — 日记添加等业务场景
router.get("/ip", authGuard, ctrl.getIpLocation);
router.get("/regeo", authGuard, ctrl.getReverseGeocode);

module.exports = router;
