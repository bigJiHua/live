# 工具类 (Utils)

工具类包含可重用的通用功能，如加密、ID生成、邮件发送等，提供简洁的接口供其他模块调用。

## 文件说明

### crypto.js
**加密工具类**

提供加密相关的工具函数。

#### generatePin(length = 6)
生成指定长度的数字 PIN 码。

#### hashPin(pin)
使用 bcrypt 加密 PIN 码。

#### verifyPinHash(pin, pinHash)
验证明文 PIN 码是否与哈希匹配。

#### generateRandomString(length = 32)
生成指定长度的随机字母数字字符串。

---

### idUtils.js
**ID 生成工具类**

提供各种 ID 生成功能。

#### idUtils.userId()
生成 50 位用户 ID，对齐当前库中 `varchar(50)` 的主键/用户字段。

#### idUtils.shortId()
生成 47 位短 ID，用于带业务前缀的轻量编号；例如 `BC_` 前缀后总长刚好不超过 `varchar(50)`。

#### idUtils.billId()
生成 50 位数字/大写字母业务 ID，对齐当前库中 `varchar(50)` 的业务主键/外键字段。

#### generateUUID()
生成 UUID v4 格式的唯一标识符。

---

### mailer.js
**邮件发送工具类**

提供邮件发送功能，使用 Nodemailer 发送各类通知邮件。

#### 配置环境变量:
```env
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your-email@gmail.com
EMAIL_PASS=your-email-app-password
```

#### sendVerificationEmail(email, code)
发送包含验证码的邮箱验证邮件。

#### sendResetPasswordEmail(email, resetToken)
发送重置密码邮件。

#### sendWelcomeEmail(email, username)
发送新用户注册欢迎邮件。

---

## 设计原则

1. **纯函数**: 尽量使用纯函数，相同的输入产生相同的输出
2. **无副作用**: 避免修改外部状态
3. **易于测试**: 函数功能单一，易于单元测试
4. **错误处理**: 提供清晰的错误信息

## 安全注意事项

1. **密码加密**: 始终使用 bcrypt 加密密码和 PIN 码
2. **不要存储明文**: 永远不要在数据库中存储明文密码
3. **验证码有效期**: 验证码应该有过期时间
4. **随机性**: 使用加密安全的随机数生成器

---

## 更新日志

### 5月28日
- 优化 `idUtils.js` — 统一 ID 生成策略，`userId()` 50位 / `shortId()` 47位 / `billId()` 50位，对齐数据库 `varchar(50)` 字段。
