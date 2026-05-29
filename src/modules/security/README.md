# 安全模块 API（PIN 码管理）

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pin/check` | 检查 PIN 状态 |
| POST | `/pin/verify` | 验证系统 PIN |
| POST | `/pin/route-verify` | 风险路由 PIN 验证（返回一次性 Token） |
| POST | `/pin/set` | 设置 PIN 码 |
| POST | `/pin/change` | 修改/关闭 PIN 码 |
| POST | `/pin/reset` | 重置 PIN（未启用） |

---

## 接口详情

### 1. 检查 PIN 状态

**路径**：`GET /api/v1/security/pin/check`

**认证**：需要 JWT

**响应**：
- 已设置 PIN：`{ "status": 200, "message": "状态正常！" }`
- 未设置 PIN：`{ "status": 400, "message": "请先设置 PIN 码", "ismessage": true }`

---

### 2. 验证系统 PIN

**路径**：`POST /api/v1/security/pin/verify`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pin` | string | ✅ | 6位纯数字 PIN 码 |

**响应**：
- 成功：`{ "status": 200, "message": "验证成功", "verified": true }`
- 失败：`{ "status": 400, "message": "PIN 码错误", "errorCount": 1, "remainingAttempts": 4 }`
- 锁定（连续5次错误）：`{ "status": 401, "message": "连续输入错误超过5次，账户已被锁定", "forceLogout": true, "locked": true }`

**安全机制**：
- 连续错误超过5次 → 自动锁定账户并强制退出
- 密码使用 bcrypt 加密存储

---

### 3. 风险路由 PIN 验证

**路径**：`POST /api/v1/security/pin/route-verify`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pin` | string | ✅ | 6位纯数字 PIN 码 |
| `challengeId` | string | ✅ | 挑战ID（由 pinLockGuard 下发） |
| `requestUrl` | string | ✅ | 原始请求URL |
| `method` | string | ✅ | 原始请求方法 |

**响应（成功）**：
```json
{
  "code": 8301,
  "status": 200,
  "message": "验证成功",
  "data": {
    "action_type": "pin_verify",
    "token": "xxx",
    "headerName": "X-Pin-Token",
    "expiresIn": 300
  }
}
```

**说明**：
- 验证通过后返回一次性 Token，有效期可配置（默认5分钟）
- Token 通过 `X-Pin-Token` 请求头传递给原始风险请求
- 最多连续错误次数可配置，超出后触发系统临时锁定

---

### 4. 设置 PIN 码

**路径**：`POST /api/v1/security/pin/set`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pin` | string | ✅ | 6位纯数字 PIN 码 |

**说明**：仅未设置 PIN 时可调用，已设置则返回错误。

---

### 5. 修改/关闭 PIN 码

**路径**：`POST /api/v1/security/pin/change`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `oldPin` | string | ✅ | 原 PIN 码（6位纯数字） |
| `newPin` | string | ✅ | 新 PIN 码（6位纯数字）；输入 `000000` 表示关闭 PIN |

**说明**：
- `newPin = "000000"` → 关闭 PIN 功能
- 其他值 → 修改 PIN 码

---

### 6. 重置 PIN（未启用）

**路径**：`POST /api/v1/security/pin/reset`

**认证**：需要 JWT

**请求体** `req.body.data`：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `verificationCode` | string | ❌ | 邮箱验证码（预留） |
| `newPin` | string | ✅ | 新 PIN 码（6位纯数字） |

**说明**：当前标记为未启用，验证码校验逻辑待接入。

---

## 安全规则

| 规则 | 说明 |
|------|------|
| PIN 格式 | 必须为6位纯数字 |
| 错误次数限制 | 连续5次错误 → 账户锁定 + 强制退出 |
| 风险路由验证 | 最多 N 次错误 → 系统临时锁定（可配置） |
| 密码加密 | bcrypt 加密存储 |
| 验证日志 | 所有验证记录写入 `security_verify_log` 表 |

---

## 更新日志

### 5月28日
- 补充完整 API 文档
