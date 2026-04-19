// 1. 环境变量必须最先加载
require("dotenv").config();
const { securityCheck } = require("./src/middlewares/securityCheck");
const decryptWithSecurity = require("./src/middlewares/authSecurityData");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const path = require("path");

// 创建 Express 实例
const app = express();

// --- 中间件层 ---
// 安全头
app.use(
  helmet({
    crossOriginResourcePolicy: { policy: "cross-origin" },
  })
);
const allowedOrigin = [
  "http://192.168.0.103:5173",
  "http://192.168.0.103:3000",
  "http://192.168.0.103:3001",
  "http://localhost:5173",
  "http://localhost:3000",
  "http://localhost:3001",
];
app.use(
  cors({
    origin: allowedOrigin, // 指定前端地址
    credentials: true, // 允许携带 cookie
    // methods: ["GET", "POST", "OPTIONS", "PATCH"], // 必须包含你使用的方法
    allowedHeaders: [
      "Content-Type",
      "Authorization",
      "viewportwidth",
      "viewportheight",
      "pixelratio",
      "navigatorplatform",
    ],
  })
);
app.use(morgan(process.env.NODE_ENV === "production" ? "combined" : "dev")); // 日志
app.use(express.json()); // JSON 解析
app.use(express.urlencoded({ extended: true })); // URL 编码解析
// 封装统一应答方法 res.say('话',状态码)
app.use(async (req, res, next) => {
  res.say = function (err, status) {
    res.status(status === undefined ? 206 : status).send({
      status: status,
      message: err instanceof Error ? err.message : err,
    });
  };
  // 解密前端数据
  await decryptWithSecurity(req, res, next);
});

// --- 路由层 ---
const apiRouter = require("./src/api");
app.use("/api/v1", securityCheck, apiRouter); // 【已启用】
app.use("/api/public", express.static(path.join(__dirname, "public")));// 【已启用】
// app.use("/api", apiRouter); // 【已启用】
// --- 静态资源与健康检查 ---
// 根路由 - 管理员首次注册页面
// app.get("/", (req, res) => {
//   res.redirect("http://192.168.0.103:5173/register");
// });

// --- 错误处理 ---
const {
  errorHandler,
  notFoundHandler,
} = require("./src/middlewares/errorMiddleware");

// 404 处理
app.use(notFoundHandler);

// 全局错误处理
app.use(errorHandler);

// --- 启动逻辑 ---
if (require.main === module) {
  const PORT = process.env.PORT || 3001;
  
  // 启动服务
  app.listen(PORT, async () => {
    // 初始化系统（创建管理员等）
    if (process.env.INIT_ENABLE === 'true') {
      const init = require('./src/init');
      await init();
    }
    
    console.log(`🚀 服务已启动: http://localhost:${PORT}`);
    console.log(`🌍 环境: ${process.env.NODE_ENV || "dev"}`);
    console.log(`📡 API 地址: http://localhost:${PORT}/api`);
  });
}

module.exports = app;
