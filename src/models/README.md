# 数据模型层 (Models)

数据模型层负责与数据库交互,封装所有的数据库操作,包括 CRUD 操作和复杂查询。

## 设计原则

1. **数据封装**: 封装数据库操作细节
2. **静态方法**: 使用静态方法提供清晰的操作接口
3. **参数验证**: 在模型层进行基本的数据验证
4. **关联处理**: 处理表之间的关联关系
5. **事务支持**: 支持数据库事务操作

## 文件说明

### User.js
**用户模型**

管理用户相关的数据库操作。

**表结构:**
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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**主要方法:**
- `findByEmail(email)` - 通过邮箱查找用户
  - 参数: 邮箱地址
  - 返回: 用户对象或 null

- `findById(id)` - 通过 ID 查找用户
  - 参数: 用户 ID
  - 返回: 用户对象或 null

- `create({ username, email, password, pinHash })` - 创建用户
  - 参数: 用户信息
  - 返回: 新创建的用户对象

- `update(id, updates)` - 更新用户信息
  - 参数: 用户 ID 和更新数据
  - 返回: 更新后的用户对象

- `delete(id)` - 删除用户
  - 参数: 用户 ID
  - 返回: 是否删除成功

**使用示例:**
```javascript
const User = require('../models/User');

// 查找用户
const user = await User.findByEmail('user@example.com');

// 创建用户
const newUser = await User.create({
  username: 'testuser',
  email: 'test@example.com',
  password: 'hashedPassword'
});

// 更新用户
await User.update(userId, { username: 'newname' });

// 删除用户
await User.delete(userId);
```

---

### Transaction.js
**账务流水模型**

管理账务流水相关的数据库操作。

**表结构:**
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
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);
```

**主要方法:**
- `findAll(filters, page, limit)` - 查找所有流水 (支持分页和过滤)
  - 参数: 过滤条件、页码、每页数量
  - 返回: `{ rows: [...], count: total }`

- `findById(id, userId)` - 通过 ID 查找流水
  - 参数: 流水 ID 和用户 ID
  - 返回: 流水对象或 null

- `create({ userId, amount, type, categoryId, description, date })` - 创建流水
  - 参数: 流水信息
  - 返回: 新创建的流水对象

- `update(id, userId, updates)` - 更新流水
  - 参数: 流水 ID、用户 ID 和更新数据
  - 返回: 更新后的流水对象

- `delete(id, userId)` - 删除流水
  - 参数: 流水 ID 和用户 ID
  - 返回: 是否删除成功

- `getStats(userId, { startDate, endDate })` - 获取统计信息
  - 参数: 用户 ID 和日期范围
  - 返回: `{ income, expense, balance }`

**使用示例:**
```javascript
const Transaction = require('../models/Transaction');

// 查询流水 (支持分页和过滤)
const result = await Transaction.findAll({
  userId: 1,
  type: 'income',
  startDate: '2024-01-01',
  endDate: '2024-12-31'
}, 1, 20);

// 创建流水
const transaction = await Transaction.create({
  userId: 1,
  amount: 100.00,
  type: 'expense',
  categoryId: 1,
  description: '午餐',
  date: '2024-03-26'
});

// 获取统计
const stats = await Transaction.getStats(1, {
  startDate: '2024-01-01',
  endDate: '2024-03-31'
});
// 返回: { income: 5000, expense: 3000, balance: 2000 }
```

---

### Category.js
**分类模型**

管理分类相关的数据库操作。

**表结构:**
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
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**主要方法:**
- `findAll(filters)` - 查找所有分类
  - 参数: 过滤条件
  - 返回: 分类数组

- `findById(id, userId)` - 通过 ID 查找分类
  - 参数: 分类 ID 和用户 ID
  - 返回: 分类对象或 null

- `create({ userId, name, type, color, icon })` - 创建分类
  - 参数: 分类信息
  - 返回: 新创建的分类对象

- `update(id, userId, updates)` - 更新分类
  - 参数: 分类 ID、用户 ID 和更新数据
  - 返回: 更新后的分类对象

- `delete(id, userId)` - 删除分类
  - 参数: 分类 ID 和用户 ID
  - 返回: 是否删除成功

- `createDefaultCategories(userId)` - 创建默认分类
  - 参数: 用户 ID
  - 返回: 创建的分类数组

**使用示例:**
```javascript
const Category = require('../models/Category');

// 查询分类
const categories = await Category.findAll({
  userId: 1,
  type: 'expense'
});

// 创建分类
const category = await Category.create({
  userId: 1,
  name: '餐饮',
  type: 'expense',
  color: '#ff4d4f',
  icon: '🍔'
});

// 创建默认分类
await Category.createDefaultCategories(userId);
```

## 数据库连接

所有模型都使用统一的数据库连接:

```javascript
const db = require('../config/db');

// 执行查询
const [rows] = await db.execute(query, params);
```

## 查询构建技巧

### 动态 WHERE 子句
```javascript
let whereClause = 'WHERE 1=1';
const params = [];

if (filters.userId) {
  whereClause += ' AND user_id = ?';
  params.push(filters.userId);
}

if (filters.type) {
  whereClause += ' AND type = ?';
  params.push(filters.type);
}
```

### 分页查询
```javascript
const offset = (page - 1) * limit;
const query = `
  SELECT * FROM table
  WHERE user_id = ?
  LIMIT ? OFFSET ?
`;
const [rows] = await db.execute(query, [userId, limit, offset]);
```

### 关联查询
```javascript
const query = `
  SELECT t.*, c.name as category_name, c.color as category_color
  FROM transactions t
  LEFT JOIN categories c ON t.category_id = c.id
  WHERE t.user_id = ?
`;
```

## 安全注意事项

1. **参数化查询**: 始终使用参数化查询防止 SQL 注入
2. **用户隔离**: 确保查询包含用户 ID,防止跨用户数据访问
3. **数据验证**: 在操作前验证数据的有效性
4. **外键约束**: 利用数据库外键保证数据完整性

## 性能优化

1. **索引优化**: 为常用查询字段创建索引
2. **查询优化**: 只查询需要的字段,避免 SELECT *
3. **批量操作**: 使用批量插入/更新减少数据库请求
4. **连接池**: 使用数据库连接池提高性能
