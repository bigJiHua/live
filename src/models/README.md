# 数据模型层 (Models)

数据模型层负责与数据库交互，封装所有的数据库操作，包括 CRUD 操作和复杂查询。

## 文件说明

### User.js
**用户模型**

管理用户相关的数据库操作。

**表结构:** `user_info`

**主要方法:**
- `findById(id)` - 通过 ID 查找用户
- `findByEmail(email)` - 通过邮箱查找用户
- `create({ nickname, email, loginPwd, salt })` - 创建用户
- `update(id, updates)` - 更新用户信息
- `delete(id)` - 删除用户

---

### Transaction.js
**账务流水模型**

管理账务流水相关的数据库操作。

**表结构:** `account`

**主要方法:**
- `findAll(filters, page, limit)` - 查找所有流水 (支持分页和过滤)
- `findById(id, userId)` - 通过 ID 查找流水
- `create({ userId, amount, type, categoryId, ... })` - 创建流水
- `update(id, userId, updates)` - 更新流水
- `delete(id, userId)` - 删除流水
- `getStats(userId, { startDate, endDate })` - 获取统计信息

---

### Category.js
**分类模型**

管理分类相关的数据库操作。

**表结构:** `bus_category`

**主要方法:**
- `findAll(filters)` - 查找所有分类
- `findById(id, userId)` - 通过 ID 查找分类
- `create({ userId, name, type, iconName, color, ... })` - 创建分类
- `update(id, userId, updates)` - 更新分类
- `delete(id, userId)` - 删除分类

---

### Moment.js
**动态(朋友圈)模型**

管理动态相关的数据库操作。

**表结构:** `moment`

**主要方法:**
- `create({ userId, parentId, content, imgUrl, mood, location, visibleType })` - 创建动态
- `findById(id)` - 获取动态详情
- `findByIds(ids, userId)` - 批量获取动态
- `findByUser(userId, { page, pageSize })` - 获取用户动态列表（只返回主动态）
- `findTodayMain(userId)` - 获取今日主动态
- `findTodayWithChildren(userId)` - 获取今日动态（含子动态）
- `update(id, userId, updates)` - 更新动态
- `delete(id, userId)` - 删除动态

**父子动态模型:**
- 主动态：`parent_id = id`（当日首条）
- 子动态：`parent_id = 父动态ID`

---

### sys_attachment.js
**文件附件模型**

管理文件上传、附件相关的数据库操作。

**表结构:** `sys_attachment`

**主要方法:**
- `create({ userId, busType, remark, tags, fileName, filePath, fileSize, fileExt })` - 创建附件记录
- `findById(id)` - 通过 ID 查询附件
- `findByUser(userId, { busType, limit, offset })` - 查询用户附件列表
- `search(userId, { busType, key, limit, offset })` - 搜索附件（按类型和关键词）
- `update(id, userId, { remark, tags })` - 更新附件
- `delete(id, userId)` - 删除附件
- `batchDelete(ids, userId)` - 批量删除附件
- `isReferencedInMoment(filePath)` - 检查附件是否被 moment 引用
- `checkReferencesBeforeDelete(ids)` - 批量检查引用

**存储字段:**
- `remark` - 图片说明（最大50字符）
- `tags` - 标签（JSON数组，最大100字符）
- `file_name` - 文件名（统一生成，不保留原始文件名）
- `file_path` - 存储路径

---

### UserLog.js
**用户日志模型**

管理用户操作日志。

**主要方法:**
- `create({ userId, type, content, ip })` - 创建日志
- `findByUser(userId, { limit, offset })` - 查询用户日志

---

### UserPin.js
**用户 PIN 码模型**

管理用户 PIN 码相关的数据库操作。

**主要方法:**
- `findByUserId(userId)` - 查询用户 PIN 信息
- `create({ userId, pinHash, ... })` - 创建 PIN 记录
- `update(userId, updates)` - 更新 PIN 信息

---

## 数据库连接

所有模型都使用统一的数据库连接:

```javascript
const db = require('../config/db');

// 执行查询
const [rows] = await db.execute(query, params);
```

## 安全注意事项

1. **参数化查询**: 始终使用参数化查询防止 SQL 注入
2. **用户隔离**: 确保查询包含用户 ID，防止跨用户数据访问
3. **数据验证**: 在操作前验证数据的有效性
4. **外键约束**: 利用数据库外键保证数据完整性

## 性能优化

1. **索引优化**: 为常用查询字段创建索引
2. **查询优化**: 只查询需要的字段，避免 SELECT *
3. **批量操作**: 使用批量插入/更新减少数据库请求
4. **连接池**: 使用数据库连接池提高性能
