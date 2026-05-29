# 变更日志

## 2026-05-29（深夜 — 定位模块 + 邮箱脱敏）

### 🆕 新增

| 端点 | 鉴权 | 说明 |
|------|------|------|
| `GET /api/v1/geo/network` | 无 | IP/网络信息采集（登录前调用），ip.sb → ipinfo.io 逐级回退 |
| `GET /api/v1/geo/ip` | authGuard | IP 定位（高德 IP → ip.sb → ipinfo.io），内网 IP 自动过滤不传高德 |
| `GET /api/v1/geo/regeo?lng=&lat=` | authGuard | 逆地理编码（高德 regeo 优先 → Nominatim 回退），省市区结构化拼接 |

### 🔧 修改

| 文件 | 变更内容 |
|------|----------|
| `src/api/index.js` | 注册 `/geo` 路由 |
| `src/modules/auth/controller/index.js` | `getCurrentUser` 邮箱脱敏：`ab****cd@xxx.com`（local-part ≤3 字符不处理） |
| `.env` | 新增 `AMAP_KEY` / `IPINFO_TOKEN` 配置项 |
| `.env.example` | 新增同上占位 |
| `package.json` | 新增依赖 `axios`（HTTP 代理转发第三方 API） |

### 🔐 安全

- 所有第三方 API Key（高德、ipinfo）仅存 `process.env`，前端不可访问
- `/geo/ip`、`/geo/regeo` 需 JWT 鉴权（authGuard）
- `/geo/network` 通过 `securityCheck` 头校验（`X-Requested-With` + 时间戳）

### 📊 统计

- 新增 2 个模块文件（controller + router）
- 修改 5 个文件
- 新增 1 个 npm 依赖
