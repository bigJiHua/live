# Utils 工具模块

本目录包含项目所有的工具函数、API 接口封装和请求处理模块。

## 目录结构

```
src/utils/
├── api/                    # API 接口封装
│   ├── auth.js             # 认证接口
│   ├── security.js         # 安全接口（PIN 验证）
│   ├── account.js          # 账务接口
│   ├── asset.js           # 资产接口
│   ├── budget.js          # 预算接口
│   ├── card.js            # 卡类接口
│   ├── category.js        # 分类接口
│   ├── fixedAsset.js      # 固定资产接口
│   ├── moment.js          # 流水接口
│   ├── todo.js            # 待办接口
│   ├── upload.js          # 上传接口
│   ├── user.js            # 用户接口
│   └── work.js            # 工作接口
├── request/                # HTTP 请求模块
│   ├── index.js           # 主入口
│   ├── config.js          # 基础配置
│   ├── core.js            # 核心实例
│   ├── client.js          # 客户端上下文
│   ├── handshake.js       # 握手 & AES Key 管理
│   ├── crypto.js          # 加密工具
│   ├── helpers.js         # 辅助函数
│   ├── pin.js             # PIN 码验证管理器
│   └── interceptors/      # 拦截器
│       ├── request.js     # 请求拦截器
│       └── response.js    # 响应拦截器
├── aes.js                 # AES 加密工具
├── device-hash.js         # 设备指纹生成
├── env.js                 # 环境变量工具
├── fileRequest.js         # 文件上传请求
├── geolocation.js         # 地理位置工具
├── index.js               # 统一导出
└── network.js             # 网络信息获取
```

## 模块说明

### API 接口层 (api/)

所有 API 接口的封装，采用统一的请求处理方式。

```javascript
// 从统一入口导入
import { authApi, securityApi, accountApi, userApi } from '@/utils'

// 直接导入具体模块
import { login } from '@/utils/api/auth'
import { verifyPin } from '@/utils/api/security'
```

| 模块 | 说明 |
|------|------|
| auth.js | 登录、注册、登出等认证接口 |
| security.js | PIN 码设置、验证、锁定等安全接口 |
| account.js | 账单、流水等账务接口 |
| user.js | 用户信息、资料修改接口 |
| asset.js | 资产管理接口 |
| budget.js | 预算管理接口 |
| card.js | 银行卡管理接口 |
| category.js | 分类管理接口 |
| fixedAsset.js | 固定资产接口 |
| todo.js | 待办事项接口 |
| upload.js | 文件上传接口 |
| work.js | 工资管理接口 |
| moment.js | 流水记录接口 |

### 请求模块 (request/)

模块化的 HTTP 请求封装，提供以下功能：

- 统一配置管理
- 请求/响应拦截
- 客户端上下文收集
- AES 加密传输
- PIN 码验证
- 握手认证

```javascript
import { request, helpers } from '@/utils/request'

// 发送请求
const res = await request.post('/api/v1/user/profile', data)
```

### 工具函数

| 文件 | 说明 |
|------|------|
| aes.js | AES 加密/解密工具 |
| device-hash.js | 浏览器指纹生成 |
| env.js | 环境变量读取 |
| fileRequest.js | 文件上传请求封装 |
| geolocation.js | 地理位置获取 |
| network.js | 网络信息获取（IP、运营商等） |

---

## API 请求头说明

本项目在所有 API 请求中自动携带设备信息、浏览器指纹和网络信息，便于后端进行用户识别、安全验证和数据分析。

### 自动携带的请求头

#### 1. 认证信息

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `Authorization` | 用户认证 Token | `Bearer eyJhbGciOiJIUzI1NiIs...` |

#### 2. 客户端指纹信息

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-Fingerprint-Hash` | 综合指纹 Hash | `1a2b3c4d5e` |
| `X-Client-Timestamp` | 请求时间戳 | `1711234567890` |
| `X-User-Agent-Custom` | 自定义 User-Agent | `Mozilla/5.0...` |
| `X-Device-Model` | 设备型号 | `Unknown` |
| `X-Os-Name` | 操作系统 | `Windows 10` |
| `X-Device-Type` | 设备类型 | `desktop` / `mobile` |

#### 3. 加密相关

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-FP-ID` | 指纹 ID（加密请求时） | `1a2b3c4d5e` |

#### 4. 标准请求头

| 请求头名称 | 说明 |
|-----------|------|
| `Content-Type` | `application/json` |
| `X-Requested-With` | `XMLHttpRequest` |

### 浏览器指纹

指纹包含以下特征：

- Canvas 绘制指纹
- 屏幕分辨率、色彩深度、像素比
- 时区、语言
- 平台、WebGL 指纹
- 可用字体、触摸支持
- Cookie、存储、WebRTC 支持

### 网络信息

网络信息通过以下方式获取：

- **外部 IP**：通过 IP 查询 API 获取
- **本地连接**：`navigator.connection` API

### 数据用途

这些信息仅用于：

- 安全验证和防欺诈
- 用户体验优化
- 设备识别和统计

---

## 使用示例

### 发送 API 请求

```javascript
import { authApi, accountApi } from '@/utils'

// 登录
const loginRes = await authApi.login({
  username: 'example',
  password: '123456'
})

// 获取账单列表
const billsRes = await accountApi.getBills({ page: 1, size: 10 })
```

### 使用请求模块

```javascript
import { request, helpers } from '@/utils/request'

// 发送自定义请求
const res = await request.get('/api/v1/data')

// 使用辅助函数
if (helpers.isWriteOperation('POST')) {
  // 处理写操作
}
```

### 工具函数

```javascript
import { aesUtil } from '@/utils/aes'
import { generateBrowserFingerprint } from '@/utils/device-hash'
import { getFullNetworkInfo } from '@/utils/network'

// AES 加密
const encrypted = aesUtil.encrypt('Hello World', 'key')

// 生成设备指纹
const fp = generateBrowserFingerprint()

// 获取网络信息
const netInfo = await getFullNetworkInfo()
```

---

## 注意事项

1. **PIN 验证**：部分敏感接口（如账务操作）需要 PIN 码验证，模块会自动处理
2. **加密传输**：写操作（POST、PUT、DELETE）默认使用 AES 加密
3. **缓存机制**：指纹和网络信息都有缓存，避免重复计算
4. **隐私合规**：所有信息收集均符合隐私政策规范
