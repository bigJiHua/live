/**
 * 网络信息获取工具
 * 使用 ip.sb 获取用户真实 IP 和网络信息
 */

import axios from "axios";

// 缓存网络信息
let cachedNetworkInfo = null;
let cacheTimestamp = null;
const CACHE_DURATION = 30 * 60 * 1000; // 30分钟缓存，避免频繁请求

/**
 * 从 ip.sb 获取网络信息
 * ip.sb 提供的信息包括：
 * - IP 地址
 * - 国家、城市、地区
 * - ISP、ASN
 * - 时区
 * - 经纬度
 */
export async function getNetworkInfo() {
  // 检查缓存
  if (
    cachedNetworkInfo &&
    cacheTimestamp &&
    Date.now() - cacheTimestamp < CACHE_DURATION
  ) {
    return cachedNetworkInfo;
  }

  try {
    // 使用 ip.sb 的 JSON 接口
    const response = await axios.get("https://api.ip.sb/geoip", {
      timeout: 5000,
      headers: {
        Accept: "application/json",
      },
    });

    if (response.data) {
      const networkInfo = {
        ip: response.data.ip || "",
        country: response.data.country || "",
        countryCode: response.data.country_code || "",
        region: response.data.region || "",
        city: response.data.city || "",
        isp: response.data.isp || "",
        asn: response.data.asn || "",
        timezone: response.data.timezone || "",
        latitude: response.data.latitude || null,
        longitude: response.data.longitude || null,
        // 添加本地时间戳
        timestamp: Date.now(),
      };

      // 更新缓存
      cachedNetworkInfo = networkInfo;
      cacheTimestamp = Date.now();

      return networkInfo;
    }

    throw new Error("无效的响应数据");
  } catch (error) {
    console.error("获取网络信息失败:", error);

    // 返回基础信息（即使失败也返回本地可获取的信息）
    return {
      ip: "unknown",
      error: error.message,
      timestamp: Date.now(),
    };
  }
}

/**
 * 获取本地网络信息（无需请求外部服务）
 */
export function getLocalNetworkInfo() {
  const connection =
    navigator.connection ||
    navigator.mozConnection ||
    navigator.webkitConnection;

  return {
    online: navigator.onLine,
    connectionType: connection ? connection.effectiveType : "unknown",
    downlink: connection ? connection.downlink : null,
    rtt: connection ? connection.rtt : null,
    saveData: connection ? connection.saveData : false,
  };
}

/**
 * 获取完整的网络信息（外部 + 本地）
 */
export async function getFullNetworkInfo() {
  const [externalInfo, localInfo] = await Promise.all([
    getNetworkInfo().catch(() => ({ ip: "unknown" })),
    Promise.resolve(getLocalNetworkInfo()),
  ]);

  return {
    external: externalInfo,
    local: localInfo,
  };
}

/**
 * 清除网络信息缓存
 */
export function clearNetworkCache() {
  cachedNetworkInfo = null;
  cacheTimestamp = null;
}
