# API 请求头说明文档

## 概述

本项目在所有 API 请求中自动携带设备信息、浏览器指纹和网络信息，便于后端进行用户识别、安全验证和数据分析。

## 安装的依赖

```bash
npm install ua-parser-js
```

## 新增文件

1. **`src/utils/fingerprint.js`** - 浏览器指纹生成工具
2. **`src/utils/network.js`** - 网络信息获取工具
3. **`src/views/User/DeviceInfo.vue`** - 设备信息展示和测试页面

## 自动携带的请求头

### 1. 认证信息

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `Authorization` | 用户认证 Token | `Bearer eyPIn_Token_Example_123456` |

### 2. User-Agent 解析信息

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-Client-Browser` | 浏览器信息（JSON） | `{"name":"Chrome","version":"120.0.0"}` |
| `X-Client-OS` | 操作系统信息（JSON） | `{"name":"Windows","version":"10"}` |
| `X-Client-Device` | 设备信息（JSON） | `{"type":"desktop","model":"","vendor":""}` |
| `X-Client-UA` | 完整 User-Agent 字符串 | `Mozilla/5.0 (Windows NT 10.0; Win64; x64)...` |

### 3. 浏览器指纹信息

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-Fingerprint-Hash` | 综合指纹 Hash | `1a2b3c4d5e` |
| `X-Fingerprint-Canvas` | Canvas 指纹 Hash | `f6g7h8i9j0` |

指纹包含以下特征：
- Canvas 绘制指纹
- 屏幕分辨率
- 色彩深度
- 像素比
- 时区
- 语言
- 平台
- WebGL 指纹
- 可用字体
- 触摸支持
- Cookie 支持
- 存储支持
- WebRTC 支持

### 4. 网络信息（异步获取）

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-Client-IP` | 用户真实 IP 地址 | `1.2.3.4` |
| `X-Client-Country` | 国家/地区 | `China` |
| `X-Client-City` | 城市 | `Beijing` |
| `X-Client-ISP` | 运营商 | `China Telecom` |
| `X-Client-Timezone` | 时区 | `Asia/Shanghai` |
| `X-Client-Connection-Type` | 本地连接类型 | `4g` / `wifi` |

网络信息来源：
- 外部：通过 `https://api.ip.sb/geoip` 获取
- 本地：通过 `navigator.connection` API 获取

### 5. 其他元数据

| 请求头名称 | 说明 | 示例值 |
|-----------|------|--------|
| `X-Client-Timestamp` | 请求时间戳 | `1711234567890` |
| `X-Client-Language` | 浏览器语言 | `zh-CN` |
| `X-Client-Timezone-Offset` | 时区偏移（分钟） | `-480` |

## 后端接收示例

### Node.js Express 示例

```javascript
app.use((req, res, next) => {
  // 读取所有客户端信息
  const clientInfo = {
    auth: req.headers.authorization,
    browser: JSON.parse(req.headers['x-client-browser'] || '{}'),
    os: JSON.parse(req.headers['x-client-os'] || '{}'),
    device: JSON.parse(req.headers['x-client-device'] || '{}'),
    ua: req.headers['x-client-ua'],
    fingerprint: {
      hash: req.headers['x-fingerprint-hash'],
      canvas: req.headers['x-fingerprint-canvas'],
    },
    network: {
      ip: req.headers['x-client-ip'],
      country: req.headers['x-client-country'],
      city: req.headers['x-client-city'],
      isp: req.headers['x-client-isp'],
      timezone: req.headers['x-client-timezone'],
      connectionType: req.headers['x-client-connection-type'],
    },
    meta: {
      timestamp: req.headers['x-client-timestamp'],
      language: req.headers['x-client-language'],
      timezoneOffset: req.headers['x-client-timezone-offset'],
    },
  };

  console.log('客户端信息:', clientInfo);

  // 可以将 clientInfo 保存到数据库或进行安全验证
  req.clientInfo = clientInfo;

  next();
});
```

### 安全验证建议

1. **指纹验证**：对比请求的指纹和数据库中存储的指纹，检测异常登录
2. **IP 变化检测**：如果用户 IP 跨越地域，发送安全提醒
3. **设备一致性**：检查设备信息是否与历史记录一致
4. **频率限制**：基于 IP 和指纹的组合进行请求频率限制

## 缓存机制

### 浏览器指纹缓存
- 指纹生成后会缓存在内存中，避免重复计算
- 使用 `clearFingerprintCache()` 可以清除缓存

### 网络信息缓存
- 网络信息缓存 5 分钟，避免频繁请求外部 API
- 使用 `clearNetworkCache()` 可以清除缓存
- 支持下拉刷新手动更新网络信息

## 测试页面

访问 `/user/device-info` 页面可以查看：

1. **User-Agent 解析信息**
   - 浏览器、操作系统、设备类型等

2. **浏览器指纹**
   - Canvas 指纹、屏幕信息、时区、语言等

3. **网络信息**
   - IP 地址、地理位置、运营商、本地网络状态等

4. **测试请求**
   - 发送测试请求查看所有携带的请求头

## 注意事项

1. **网络信息异步获取**：网络信息获取不阻塞请求，可能在第一次请求时尚未获取到
2. **隐私合规**：请确保在隐私政策中告知用户会收集这些信息
3. **错误处理**：如果外部 API 失败，不会影响正常业务请求
4. **CORS 配置**：后端需要配置 CORS 允许这些自定义请求头

## 数据隐私

这些信息仅用于：
- 安全验证和防欺诈
- 用户体验优化
- 设备识别和统计

所有信息都会经过脱敏处理，不会收集用户的敏感隐私数据。
