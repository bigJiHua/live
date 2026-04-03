# 控制器层 (Controllers)

控制器层负责处理业务逻辑，接收请求参数，调用服务和模型层，并返回响应结果。

## 文件说明

### authController.js
**认证控制器**

处理用户认证相关的业务逻辑。

**主要方法:**
- `register()` - 用户注册
- `login()` - 用户登录
- `getCurrentUser()` - 获取当前用户信息
- `refreshToken()` - 刷新 Token
- `updateProfile()` - 更新用户资料
- `changePassword()` - 修改密码
- `getSettings()` - 获取用户设置
- `updateSettings()` - 更新用户设置

---

### financeController.js
**财务控制器**

处理账务管理相关的业务逻辑。

**主要方法:**
- `getTransactions()` - 获取账务流水列表 (支持分页和过滤)
- `getTransactionById()` - 获取账务流水详情
- `createTransaction()` - 创建账务流水
- `updateTransaction()` - 更新账务流水
- `deleteTransaction()` - 删除账务流水
- `getCategories()` - 获取分类列表
- `createCategory()` - 创建分类
- `updateCategory()` - 更新分类
- `deleteCategory()` - 删除分类
- `getFinanceReport()` - 获取财务报表
- `calculateIRR()` - 计算投资回报率 (IRR)

---

### securityController.js
**安全控制器**

处理 PIN 码管理和安全相关的业务逻辑。

**主要方法:**
- `verifyPin()` - 验证 PIN 码
- `setPin()` - 设置 PIN 码
- `changePin()` - 修改 PIN 码
- `resetPin()` - 重置 PIN 码
- `sendVerificationCode()` - 发送验证码 (邮箱验证)

---

### momentController.js
**动态(朋友圈)控制器**

处理动态发布、查询、编辑、删除等业务逻辑。

**主要方法:**
- `create()` - 发布动态（自动聚合到当日主动态）
- `list()` - 获取动态列表（content 截取前20字符作为标题）
- `today()` - 获取今日动态（含子动态详情）
- `detail()` - 获取动态详情
- `batchDetail()` - 批量获取子动态详情
- `update()` - 更新动态
- `delete()` - 删除动态

**父子动态模型:**
- 主动态：每日的首条动态，`parent_id = id`
- 子动态：当日后续动态，`parent_id` 指向主动态 ID
- 列表接口返回 `children` 为 ID 数组

---

### uploadController.js
**文件上传控制器**

处理文件上传、管理、搜索等业务逻辑。

**主要方法:**
- `upload()` - 单文件上传（图片自动压缩为 WebP，SVG 除外）
- `uploadMultiple()` - 多文件上传
- `list()` - 查询附件列表
- `search()` - 搜索附件（按类型和关键词）
- `edit()` - 编辑附件（remark 和 tags）
- `batchDelete()` - 批量删除附件（检查是否被 moment 引用）

**文件分类:**
| busType | 说明 |
|---------|------|
| `post` | 动态图片 |
| `product` | 资产图片 |
| `bank` | 银行 Icon |
| `other` | 其他资源 |

**存储规则:**
- 文件名：统一生成 `时间戳_随机8位.webp`
- 路径：`{busType}/{year}/{month}/{day}/{filename}`
- 图片自动压缩为 WebP（质量 50%）
- 图片自动生成缩略图（质量 30%，最大 200x200）
- SVG 不转换格式，保留原样

**校验规则:**
- remark 最大 50 字符
- tags 总长度不超过 100 字符

---

## 统一响应格式

```javascript
// 成功响应
{ status: 200, message: "操作成功", data: {...} }

// 错误响应
{ status: 400/500, message: "错误描述" }
```

## 与其他层的关系

```
API 路由 → 控制器 → 服务层/模型层 → 数据库
           ↓
      验证参数
           ↓
      协调业务
           ↓
      格式化响应
```
