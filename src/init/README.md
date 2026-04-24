# 初始化脚本 (init)

本目录包含应用程序启动时的初始化脚本。

## 文件说明

### index.js
**系统初始化模块**

完整的初始化流程：

1. 检查数据库是否存在，不存在则自动创建
2. 检查表数量是否正确（共 25 张表）
3. 检查表字段是否完整
4. 创建管理员账户（仅在 `INIT_ENABLE=true` 时）
5. 清理 .env 配置（删除敏感信息）

**环境变量：**
- `INIT_ENABLE`: 是否启用初始化功能
- `INIT_ADMIN_EMAIL`: 管理员邮箱
- `INIT_ADMIN_PASSWORD`: 管理员密码

---

### autoDatabase.js
**数据库自动初始化脚本**

自动检测并初始化数据库：

1. 检测 `mysql/live.sql` 文件
2. 自动创建数据库（如不存在）
3. 导入表结构和初始数据

**使用：**
```javascript
const { initDatabase } = require('./init/autoDatabase');
await initDatabase();
```

---

## 执行顺序

在 `server.js` 启动时，初始化脚本会首先执行，确保数据库和应用程序环境正确配置。
