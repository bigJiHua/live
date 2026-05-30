// 1. 环境变量必须最先加载
require("dotenv").config();
const { securityCheck } = require("./src/common/middleware/securityCheck");
const decryptWithSecurity = require("./src/common/middleware/authSecurityData");
const {
  pinSecurityGuard,
} = require("./src/common/middleware/pinSecurityGuard");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const path = require("path");

// 创建 Express 实例
const app = express();

// 信任 nginx 反代，否则 req.ip 永远是 127.0.0.1
app.set("trust proxy", true);

/* ================== 小程序跳过设备验证 ================== */
// 小程序请求携带 x-xcx-skip-key 头部，匹配则跳过设备级别的安全校验
// app.use((req, res, next) => {
//   const skipKey = req.headers['x-xcx-skip-key'];
//   if (skipKey && skipKey === process.env.XCX_SKIP_KEY) {
//     req.xcxBypass = true;
//   }
//   next();
// });

// --- 中间件层 ---
// 安全头
app.use(
  helmet({
    crossOriginResourcePolicy: { policy: "cross-origin" },
  })
);
const allowedOrigin = [
  process.env.FRONTEND_URL,
  "http://192.168.1.4:5173",
  "http://192.168.1.4:5174",
  "http://192.168.0.103:5173",
  "http://192.168.0.103:5174",
  "http://localhost:5173",
  "http://localhost:5174",
  "http://127.0.0.1:5173",
  "http://127.0.0.1:5174",
].filter(Boolean);
app.use(
  cors({
    origin: allowedOrigin,
    credentials: true, // 允许携带 cookie
    methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowedHeaders: [
      "Content-Type",
      "Authorization",
      "viewportwidth",
      "viewportheight",
      "pixelratio",
      "navigatorplatform",
      "X-Requested-With",
      "x-client-timestamp",
      "x-fingerprint-hash",
      "x-user-agent-custom",
      "x-device-model",
      "x-os-name",
      "x-device-type",
      "X-FP-ID",
      "x-route-verify-token",
    ],
  })
);
app.use(morgan(process.env.NODE_ENV === "production" ? "combined" : "dev")); // 日志
app.use(express.json()); // JSON 解析
app.use(express.urlencoded({ extended: true })); // URL 编码解析
// 封装统一应答方法 res.say('话',状态码)
app.use(async (req, res, next) => {
  res.say = function (err, status) {
    const httpStatus = status === undefined ? 206 : status;
    const isServerError = httpStatus >= 500;
    const message =
      err instanceof Error &&
      isServerError &&
      process.env.NODE_ENV !== "development"
        ? "服务器内部错误"
        : err instanceof Error
        ? err.message
        : err;

    res.status(httpStatus).send({
      status: status,
      message,
    });
  };
  // 解密前端数据
  await decryptWithSecurity(req, res, next);
});

// --- PIN 安全验证中间件（在路由之后，拦截已认证的请求） ---
app.use(pinSecurityGuard);

// --- 路由层 ---
const apiRouter = require("./src/api");
app.use("/api/v1", securityCheck, apiRouter); // 【已启用】
app.use("/api/public", express.static(path.join(__dirname, "public"))); // 【已启用】

// --- 错误处理 ---
const {
  errorHandler,
  notFoundHandler,
} = require("./src/common/middleware/errorMiddleware");

// 404 处理
app.use(notFoundHandler);

// 全局错误处理
app.use(errorHandler);

// --- 启动逻辑 ---
if (require.main === module) {
  const PORT = process.env.PORT || 3001;

  // 启动服务
  app.listen(PORT, async () => {
    // 数据库连接检查
    const { getPool } = require("./src/common/config/db");
    const dbConfig = {
      host: process.env.DB_HOST || "localhost",
      port: process.env.DB_PORT || 3306,
      database: process.env.DB_NAME || "live",
      user:    process.env.DB_USER || "root",
    };
    try {
      const pool = getPool();
      const conn = await pool.getConnection();
      const [rows] = await conn.query("SELECT VERSION() AS version, NOW() AS server_time");
      conn.release();
      console.log(`🗄️  数据库连接成功: ${dbConfig.user}@${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
      console.log(`    MySQL 版本: ${rows[0].version}  |  服务器时间: ${rows[0].server_time}`);
    } catch (err) {
      console.error(`❌ 数据库连接失败: ${err.message}`);
      console.error(`   目标: ${dbConfig.user}@${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
    }

    // 初始化系统（始终执行：日常启动跑增量迁移，INIT_ENABLE=true 时走全量备份+同步）
    // 设置 INIT_SKIP=true 可跳过整个初始化流程，用于排查问题或紧急重启
    if (process.env.INIT_SKIP === "true") {
      console.log("⏭️  跳过系统初始化（INIT_SKIP=true）");
    } else {
      const init = require("./src/init");
      await init();
    }

    console.log(`🚀 服务已启动: http://localhost:${PORT}`);
    console.log(`🌍 环境: ${process.env.NODE_ENV || "dev"}`);
    console.log(`📡 API 地址: http://localhost:${PORT}/api`);
  });
}

module.exports = app;
