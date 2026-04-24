# 公共模块层 (Common)

本目录包含应用程序的公共模块，供其他模块复用。

## 目录结构

```
common/
├─ config/        # 配置文件
├─ controller/    # 公共控制器
├─ middleware/    # 中间件
├─ model/         # 公共数据模型
└─ utils/         # 工具类
```

## 模块说明

| 目录 | 说明 |
|------|------|
| `config/` | 数据库连接、JWT 配置等 |
| `controller/` | 公共控制器 |
| `middleware/` | 公共中间件（认证、PIN 验证、错误处理等） |
| `model/` | 公共数据模型 |
| `utils/` | 工具类（加密、ID 生成、邮件发送等） |

## 使用说明

其他模块通过 require 引入公共模块：

```javascript
const db = require('../../common/config/db');
const authGuard = require('../../common/middleware/authGuard');
const idUtils = require('../../common/utils/idUtils');
```
