# 地理位置代理 API

> **更新日期**: 2026-06-30

将第三方 API Key 收口到服务端，前端不直接暴露 Key。提供 IP 定位、逆地理编码和客户端网络信息采集功能。

## 接口列表

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/geo/network` | 无 | 客户端上下文采集（登录前可用） |
| GET | `/geo/ip` | authGuard | IP 定位（登录后） |
| GET | `/geo/regeo` | authGuard | 逆地理编码（登录后） |

---

## 接口详情

### 1. 获取网络信息

**路径**：`GET /api/v1/geo/network`

**鉴权**：无需登录，可用于登录前的客户端上下文采集。

**说明**：获取客户端公网 IP 的网络归属信息，按 `ip.sb` → `ipinfo.io` 优先级依次尝试。

**响应**：
```json
{
  "status": 200,
  "data": {
    "ip": "1.2.3.4",
    "country": "China",
    "countryCode": "CN",
    "region": "Guangdong",
    "city": "Shenzhen",
    "isp": "China Telecom",
    "asn": "AS4134",
    "timezone": "Asia/Shanghai",
    "latitude": 22.5431,
    "longitude": 114.0579,
    "source": "ip.sb"
  }
}
```

**容错**：内网 IP 不传参给上游，让 ip.sb 使用服务端出口 IP；两个服务都失败时返回 `status: 500`。

---

### 2. IP 定位

**路径**：`GET /api/v1/geo/ip`

**鉴权**：需要 authGuard（JWT 登录态）。

**说明**：通过高德 IP 定位 API 获取当前位置的省/市/坐标。

**响应**：
```json
{
  "status": 200,
  "data": {
    "address": "广东省 深圳市",
    "lat": 22.5431,
    "lng": 114.0579,
    "source": "amap"
  }
}
```

**内网处理**：检测到内网 IP 直接返回空结果，不请求高德。

**失败兜底**：高德失败或无法定位时返回 `source: "none"` 的空结果，不报错。

---

### 3. 逆地理编码

**路径**：`GET /api/v1/geo/regeo?lng=116.397&lat=39.908`

**鉴权**：需要 authGuard（JWT 登录态）。

**说明**：将 GPS 坐标转为中文地址。

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `lng` | number | ✅ | 经度 |
| `lat` | number | ✅ | 纬度 |

**响应**：
```json
{
  "status": 200,
  "data": {
    "address": "北京市东城区天安门广场",
    "source": "amap"
  }
}
```

**降级策略**：高德优先 → 失败则回退 [Nominatim](https://nominatim.openstreetmap.org)（OpenStreetMap）。

---

## 依赖的外部服务

| 服务 | 用途 | 所需环境变量 |
|------|------|------------|
| [ip.sb](https://ip.sb) | 第一优先级 IP 信息查询 | 无（免费） |
| [ipinfo.io](https://ipinfo.io) | 第二优先级 IP 信息查询 | `IPINFO_TOKEN`（可选） |
| [高德 IP 定位](https://restapi.amap.com/v3/ip) | IP → 省/市/坐标 | `AMAP_KEY` |
| [高德逆地理编码](https://restapi.amap.com/v3/geocode/regeo) | GPS → 地址 | `AMAP_KEY` |
| [Nominatim](https://nominatim.openstreetmap.org) | 逆地理编码兜底 | 无（免费） |

---

## 目录结构

```
geo/
├── api/
│   └── index.js        # 路由注册（3 个端点）
└── controller/
    └── index.js        # 控制器（网络信息 / IP定位 / 逆地理编码）
```

---

## 更新日志

### 2026-06-30
- 补充 README.md 文档
