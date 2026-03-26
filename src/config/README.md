# 配置层 (Config)

配置层包含应用程序的配置文件,如数据库连接、JWT 配置、环境变量管理等。

## 设计原则

1. **环境隔离**: 不同环境使用不同配置
2. **安全性**: 敏感信息存储在环境变量中
3. **集中管理**: 所有配置集中在一处
4. **懒加载**: 按需初始化连接和资源

## 环境变量

在项目根目录创建 `.env` 文件:

```env
# 服务器配置
NODE_ENV=development
PORT=3000

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=
DB_NAME=life_manager

# JWT 配置
JWT_SECRET=your-super-secret-jwt-key
JWT_EXPIRES_IN=7d
JWT_REFRESH_EXPIRES_IN=30d

# Session 配置
SESSION_SECRET=your-super-secret-session-key

# 邮件配置
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your-email@gmail.com
EMAIL_PASS=your-email-app-password

# 前端 URL
FRONTEND_URL=http://localhost:3000

# 应用名称
APP_NAME=生活管理系统
```

**重要提示:**
- `.env` 文件包含敏感信息,不要提交到版本控制系统
- `.env.example` 提供配置模板,可以提交到版本控制系统

## 文件说明

### db.js
**数据库配置文件**

提供数据库连接池和表结构初始化功能。

#### 配置项

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DB_HOST` | 数据库主机 | localhost |
| `DB_PORT` | 数据库端口 | 3306 |
| `DB_USER` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | - |
| `DB_NAME` | 数据库名称 | life_manager |

#### 主要功能

**1. 数据库连接池**

使用连接池管理数据库连接,提高性能:

```javascript
const pool = mysql.createPool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  enableKeepAlive: true,
  keepAliveInitialDelay: 0,
});
```

**2. 初始化数据库表**

自动创建所需的数据库表:

```javascript
const tables = [
  // users 表
  `CREATE TABLE IF NOT EXISTS users (...)`,
  // categories 表
  `CREATE TABLE IF NOT EXISTS categories (...)`,
  // transactions 表
  `CREATE TABLE IF NOT EXISTS transactions (...)`
];
```

**3. 执行查询**

提供简化的查询接口:

```javascript
const { execute } = require('../config/db');

// 执行查询
const [rows] = await execute('SELECT * FROM users WHERE id = ?', [userId]);

// 插入数据
const [result] = await execute(
  'INSERT INTO users (username, email) VALUES (?, ?)',
  ['张三', 'zhangsan@example.com']
);
```

#### 使用示例

```javascript
const { initPool, execute, initTables } = require('../config/db');

// 初始化连接池 (首次使用时自动调用)
initPool();

// 初始化表结构
await initTables();

// 执行查询
const [users] = await execute('SELECT * FROM users');

// 执行带参数的查询
const [user] = await execute('SELECT * FROM users WHERE id = ?', [userId]);
```

#### 表结构

**users 表:**
```sql
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  pin_hash VARCHAR(255),
  settings JSON,
  email_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

**categories 表:**
```sql
CREATE TABLE categories (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  name VARCHAR(50) NOT NULL,
  type ENUM('income', 'expense') NOT NULL,
  color VARCHAR(7) DEFAULT '#52c41a',
  icon VARCHAR(10),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

**transactions 表:**
```sql
CREATE TABLE transactions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  amount DECIMAL(10, 2) NOT NULL,
  type ENUM('income', 'expense') NOT NULL,
  category_id INT,
  description TEXT,
  date DATE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
  INDEX idx_user_date (user_id, date),
  INDEX idx_user_type (user_id, type),
  INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
```

---

### jwt.js
**JWT 配置文件**

提供 JWT (JSON Web Token) 的配置和工具函数。

#### 配置项

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `JWT_SECRET` | JWT 签名密钥 | your-secret-key |
| `JWT_EXPIRES_IN` | Token 过期时间 | 7d |
| `JWT_REFRESH_EXPIRES_IN` | 刷新 Token 过期时间 | 30d |

**安全提示:**
- `JWT_SECRET` 必须使用强随机字符串
- 生产环境必须修改默认密钥
- 不同环境使用不同的密钥

#### 主要功能

**1. 生成 Token**

```javascript
const { generateToken } = require('../config/jwt');

// 生成普通 Token
const token = generateToken({ userId: 1, email: 'user@example.com' });

// 生成自定义过期时间的 Token
const token = generateToken(
  { userId: 1 },
  '1h'  // 1小时过期
);
```

**2. 验证 Token**

```javascript
const { verifyToken } = require('../config/jwt');

try {
  const decoded = verifyToken(token);
  console.log(decoded);  // { userId: 1, email: 'user@example.com', iat: 123, exp: 456 }
} catch (error) {
  console.log('Token 无效');
}
```

**3. 解码 Token**

```javascript
const { decodeToken } = require('../config/jwt');

// 不验证签名,只解码
const decoded = decodeToken(token);
```

**4. 检查 Token 是否过期**

```javascript
const { isTokenExpired } = require('../config/jwt');

if (isTokenExpired(token)) {
  console.log('Token 已过期');
}
```

**5. 生成访问 Token 和刷新 Token**

```javascript
const { generateAccessToken, generateRefreshToken } = require('../config/jwt');

// 生成访问 Token (短有效期)
const accessToken = generateAccessToken({ userId: 1 });

// 生成刷新 Token (长有效期)
const refreshToken = generateRefreshToken({ userId: 1 });
```

#### 使用示例

**在认证控制器中使用:**

```javascript
const { generateToken, verifyToken, isTokenExpired } = require('../config/jwt');

class AuthController {
  async login(req, res) {
    const { email, password } = req.body;

    // 验证用户
    const user = await User.findByEmail(email);
    const isValid = await bcrypt.compare(password, user.password);

    if (!isValid) {
      return res.status(401).json({ message: '密码错误' });
    }

    // 生成 Token
    const token = generateToken({
      userId: user.id,
      email: user.email
    });

    res.json({
      message: '登录成功',
      token,
      user: {
        id: user.id,
        username: user.username,
        email: user.email
      }
    });
  }

  async refreshToken(req, res) {
    const { token } = req.body;

    // 检查 Token 是否过期
    if (isTokenExpired(token)) {
      return res.status(401).json({ message: 'Token 已过期' });
    }

    // 验证 Token
    const decoded = verifyToken(token);
    const user = await User.findById(decoded.userId);

    // 生成新 Token
    const newToken = generateToken({
      userId: user.id,
      email: user.email
    });

    res.json({
      message: 'Token 刷新成功',
      token: newToken
    });
  }
}
```

**在中间件中使用:**

```javascript
const { verifyToken } = require('../config/jwt');

const authGuard = (req, res, next) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ message: '未提供认证 Token' });
  }

  const token = authHeader.substring(7);

  try {
    const decoded = verifyToken(token);
    req.userId = decoded.userId;
    req.userEmail = decoded.email;
    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({ message: 'Token 已过期' });
    }
    return res.status(401).json({ message: 'Token 无效' });
  }
};
```

## 配置最佳实践

1. **使用环境变量**: 所有敏感配置通过环境变量管理
2. **默认值**: 为非敏感配置提供合理的默认值
3. **验证配置**: 启动时验证必需的配置项
4. **文档化**: 在 `.env.example` 中提供配置说明
5. **环境隔离**: 不同环境使用不同的 `.env` 文件

## 配置验证

可以在 `server.js` 中添加配置验证:

```javascript
const requiredEnvVars = [
  'DB_HOST',
  'DB_USER',
  'DB_PASSWORD',
  'DB_NAME',
  'JWT_SECRET'
];

const missingEnvVars = requiredEnvVars.filter(varName => !process.env[varName]);

if (missingEnvVars.length > 0) {
  console.error(`缺少必需的环境变量: ${missingEnvVars.join(', ')}`);
  process.exit(1);
}
```

## 多环境配置

可以使用不同的 `.env` 文件:

```env
# .env.development
NODE_ENV=development
DB_HOST=localhost
JWT_SECRET=dev-secret-key

# .env.production
NODE_ENV=production
DB_HOST=production-db.com
JWT_SECRET=prod-secret-key
```

在启动时加载对应环境的配置:

```javascript
require('dotenv').config({
  path: `.env.${process.env.NODE_ENV || 'development'}`
});
```
