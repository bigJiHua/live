# 工具类 (Utils)

工具类包含可重用的通用功能,如加密、日期处理、邮件发送等,提供简洁的接口供其他模块调用。

## 设计原则

1. **纯函数**: 尽量使用纯函数,相同的输入产生相同的输出
2. **无副作用**: 避免修改外部状态
3. **易于测试**: 函数功能单一,易于单元测试
4. **错误处理**: 提供清晰的错误信息

## 文件说明

### crypto.js
**加密工具类**

提供加密相关的工具函数。

#### generatePin(length = 6)
**生成随机 PIN 码**

生成指定长度的数字 PIN 码。

**参数:**
- `length`: PIN 码长度,默认 6 位

**返回:**
- `string`: 生成的 PIN 码

**使用示例:**
```javascript
const { generatePin } = require('../utils/crypto');

// 生成 6 位 PIN 码
const pin = generatePin();
console.log(pin); // "123456"

// 生成 4 位 PIN 码
const pin4 = generatePin(4);
console.log(pin4); // "1234"
```

---

#### hashPin(pin)
**加密 PIN 码**

使用 bcrypt 加密 PIN 码。

**参数:**
- `pin`: 明文 PIN 码

**返回:**
- `Promise<string>`: 加密后的 PIN 码哈希

**使用示例:**
```javascript
const { hashPin } = require('../utils/crypto');

const pin = '123456';
const hashedPin = await hashPin(pin);
console.log(hashedPin);
// "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

---

#### verifyPinHash(pin, pinHash)
**验证 PIN 码哈希**

验证明文 PIN 码是否与哈希匹配。

**参数:**
- `pin`: 明文 PIN 码
- `pinHash`: 加密后的 PIN 码哈希

**返回:**
- `Promise<boolean>`: 是否匹配

**使用示例:**
```javascript
const { verifyPinHash } = require('../utils/crypto');

const pin = '123456';
const isValid = await verifyPinHash(pin, storedHash);
if (isValid) {
  console.log('PIN 码正确');
} else {
  console.log('PIN 码错误');
}
```

---

#### generateRandomString(length = 32)
**生成随机字符串**

生成指定长度的随机字母数字字符串。

**参数:**
- `length`: 字符串长度,默认 32 位

**返回:**
- `string`: 随机字符串

**使用示例:**
```javascript
const { generateRandomString } = require('../utils/crypto');

const randomStr = generateRandomString(16);
console.log(randomStr); // "aB3dEf7gH9iJ2kLm"
```

---

#### generateUUID()
**生成 UUID v4**

生成符合 UUID v4 标准的唯一标识符。

**返回:**
- `string`: UUID 字符串

**使用示例:**
```javascript
const { generateUUID } = require('../utils/crypto');

const id = generateUUID();
console.log(id); // "550e8400-e29b-41d4-a716-446655440000"
```

---

### mailer.js
**邮件发送工具类**

提供邮件发送功能,使用 Nodemailer 发送各类通知邮件。

#### 配置邮件发送器

在使用邮件功能前,需要配置环境变量:

```env
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your-email@gmail.com
EMAIL_PASS=your-email-app-password
```

**Gmail 配置提示:**
1. 启用两步验证
2. 生成应用专用密码 (不是登录密码)
3. 使用应用专用密码作为 `EMAIL_PASS`

---

#### sendVerificationEmail(email, code)
**发送验证邮件**

发送包含验证码的邮箱验证邮件。

**参数:**
- `email`: 收件人邮箱
- `code`: 验证码 (6位数字)

**返回:**
- `Promise<boolean>`: 是否发送成功

**邮件模板:**
```
主题: 验证您的邮箱

您好!

您的验证码是:

123456

验证码有效期为 10 分钟,请尽快使用。

如果这不是您的操作,请忽略此邮件。
```

**使用示例:**
```javascript
const { sendVerificationEmail } = require('../utils/mailer');
const { generatePin } = require('../utils/crypto');

// 生成验证码并发送
const code = generatePin();
await sendVerificationEmail('user@example.com', code);
```

---

#### sendResetPasswordEmail(email, resetToken)
**发送重置密码邮件**

发送包含重置密码链接的邮件。

**参数:**
- `email`: 收件人邮箱
- `resetToken`: 重置令牌

**返回:**
- `Promise<boolean>`: 是否发送成功

**使用示例:**
```javascript
const { sendResetPasswordEmail } = require('../utils/mailer');

// 生成重置令牌并发送邮件
const resetToken = generateRandomString(32);
await sendResetPasswordEmail('user@example.com', resetToken);
```

---

#### sendWelcomeEmail(email, username)
**发送欢迎邮件**

发送新用户注册欢迎邮件。

**参数:**
- `email`: 收件人邮箱
- `username`: 用户名

**返回:**
- `Promise<boolean>`: 是否发送成功

**使用示例:**
```javascript
const { sendWelcomeEmail } = require('../utils/mailer');

await sendWelcomeEmail('user@example.com', '张三');
```

---

## 在项目中使用

### 在控制器中使用

```javascript
const { generatePin, hashPin, verifyPinHash } = require('../utils/crypto');
const { sendVerificationEmail } = require('../utils/mailer');

class SecurityController {
  async setPin(req, res) {
    const { pin, confirmPassword } = req.body;

    // 加密 PIN 码
    const pinHash = await hashPin(pin);

    // 存储到数据库
    await User.update(req.userId, { pinHash });

    res.json({ message: 'PIN 码设置成功' });
  }

  async verifyPin(req, res) {
    const { pin } = req.body;
    const user = await User.findById(req.userId);

    // 验证 PIN 码
    const isValid = await verifyPinHash(pin, user.pinHash);

    if (isValid) {
      req.session.pinVerified = true;
      res.json({ verified: true });
    } else {
      res.status(401).json({ message: 'PIN 码错误' });
    }
  }

  async sendVerificationCode(req, res) {
    const { email } = req.body;

    // 生成验证码
    const code = generatePin();

    // 发送邮件
    await sendVerificationEmail(email, code);

    // 保存验证码到数据库或 Redis
    await saveVerificationCode(email, code);

    res.json({ message: '验证码已发送' });
  }
}
```

## 扩展工具类

可以创建更多的工具类:

### 日期工具类 (dateUtils.js)
```javascript
function formatDate(date) {
  return date.toISOString().split('T')[0];
}

function getStartOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function getEndOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

module.exports = {
  formatDate,
  getStartOfMonth,
  getEndOfMonth
};
```

### 验证工具类 (validator.js)
```javascript
function isEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isPhone(phone) {
  return /^1[3-9]\d{9}$/.test(phone);
}

function isPin(pin) {
  return /^\d{6}$/.test(pin);
}

module.exports = {
  isEmail,
  isPhone,
  isPin
};
```

### 响应工具类 (response.js)
```javascript
function success(res, data, message = '成功') {
  res.json({ success: true, message, data });
}

function error(res, message, statusCode = 500) {
  res.status(statusCode).json({ success: false, message });
}

function paginated(res, data, pagination, message = '成功') {
  res.json({ success: true, message, data, pagination });
}

module.exports = {
  success,
  error,
  paginated
};
```

## 安全注意事项

1. **密码加密**: 始终使用 bcrypt 加密密码和 PIN 码
2. **不要存储明文**: 永远不要在数据库中存储明文密码
3. **验证码有效期**: 验证码应该有过期时间
3. **随机性**: 使用加密安全的随机数生成器
4. **邮件安全**: 不要在邮件中发送敏感信息

## 性能优化

1. **连接池**: 邮件发送器使用连接池
2. **异步发送**: 邮件发送可以异步进行
3. **缓存**: 邮件模板可以缓存
4. **批量发送**: 支持批量邮件发送
