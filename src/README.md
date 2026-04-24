# 源代码目录 (src)

本目录包含应用程序的所有源代码。

## 目录结构

```
src/
├─ api/              # API 路由层
├─ common/           # 公共模块（配置、中间件、工具类）
│  ├─ config/       # 配置文件
│  ├─ controller/    # 公共控制器
│  ├─ middleware/    # 中间件（认证、PIN 验证、错误处理等）
│  ├─ model/        # 公共模型
│  └─ utils/        # 工具类
├─ modules/          # 业务模块
│  ├─ account/       # 账务模块
│  ├─ asset/        # 资产模块
│  ├─ auth/         # 认证模块
│  ├─ budget/       # 预算模块
│  ├─ card/         # 银行卡模块
│  ├─ category/     # 分类模块
│  ├─ fixed_asset/  # 固定资产模块
│  ├─ moment/       # 时刻模块
│  ├─ security/    # 安全模块
│  ├─ todo/        # 待办模块
│  ├─ upload/      # 上传模块
│  ├─ user/        # 用户模块
│  └─ work/        # 工作模块
├─ init/            # 初始化脚本
└─ app.js           # Express 应用实例
```

## 模块说明

| 目录 | 说明 |
|------|------|
| `api/` | 所有 API 路由入口，统一注册到 `/api/v1` 前缀 |
| `common/` | 公共模块，供其他模块复用 |
| `modules/` | 按业务功能划分的模块，每个模块包含完整的 MVC 结构 |
| `init/` | 应用程序初始化脚本 |

## 架构设计

项目采用 **模块化架构**：

1. **API 层** (`api/`)：定义 HTTP 端点
2. **业务模块层** (`modules/`)：包含控制器、模型、路由、规则
3. **公共层** (`common/`)：配置、中间件、工具类

每个业务模块通常包含：
- `api/` - 路由定义
- `controller/` - 业务逻辑
- `model/` - 数据模型
- `rules/` - 验证规则
