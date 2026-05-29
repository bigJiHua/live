/**
 * 网络信息获取工具
 *
 * 策略：浏览器 GPS 优先（仅一次）→ 回退 ip.sb（后端代理）
 * 用于登录/API请求时的客户端上下文采集
 */
import axios from "axios";
import { getSecurityHeaders } from "./securityHeaders";

// 缓存
let cachedNetworkInfo = null;
let cacheTimestamp = null;
const CACHE_DURATION = 30 * 60 * 1000; // 30分钟

// GPS 一次性标记：被拒后本次会话不再尝试
let gpsTried = false;
let gpsDenied = false;

/**
 * 静默尝试 GPS（仅一次，被拒后不再重试）
 */
async function tryGpsQuietly() {
  if (gpsTried && gpsDenied) return null;
  if (!navigator.geolocation) { gpsTried = true; gpsDenied = true; return null; }

  // 先查权限，denied 直接跳过
  let permissionState = "prompt";
  if (navigator.permissions) {
    try {
      const r = await navigator.permissions.query({ name: "geolocation" });
      permissionState = r.state;
    } catch {}
  }
  if (permissionState === "denied") {
    gpsTried = true;
    gpsDenied = true;
    return null;
  }

  gpsTried = true;
  return new Promise((resolve) => {
    const timer = setTimeout(() => { gpsDenied = true; resolve(null); }, 3000);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        clearTimeout(timer);
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      () => {
        clearTimeout(timer);
        gpsDenied = true;
        resolve(null);
      },
      { enableHighAccuracy: false, timeout: 3000, maximumAge: 600000 }
    );
  });
}

export async function getNetworkInfo() {
  if (cachedNetworkInfo && cacheTimestamp && Date.now() - cacheTimestamp < CACHE_DURATION) {
    return cachedNetworkInfo;
  }

  const gpsPromise = tryGpsQuietly();
  const ipPromise = axios.get("/api/v1/geo/network", {
    timeout: 5000,
    headers: getSecurityHeaders(),
  }).catch(() => ({ data: null }));

  const gps = await gpsPromise;

  if (gps) {
    const ipRes = await ipPromise;
    const ipData = ipRes?.data?.status === 200 ? ipRes.data.data : {};
    const info = {
      ip: ipData.ip || "",
      country: ipData.country || "",
      countryCode: ipData.countryCode || "",
      region: ipData.region || "",
      city: ipData.city || "",
      isp: ipData.isp || "",
      asn: ipData.asn || "",
      timezone: ipData.timezone || "",
      latitude: gps.lat,
      longitude: gps.lng,
      source: "gps+ip",
      timestamp: Date.now(),
    };
    cachedNetworkInfo = info;
    cacheTimestamp = Date.now();
    return info;
  }

  const ipRes = await ipPromise;
  if (ipRes?.data?.status === 200 && ipRes.data.data) {
    const info = { ...ipRes.data.data, timestamp: Date.now() };
    cachedNetworkInfo = info;
    cacheTimestamp = Date.now();
    return info;
  }

  return { ip: "unknown", timestamp: Date.now() };
}

export function getLocalNetworkInfo() {
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  return {
    online: navigator.onLine,
    connectionType: connection ? connection.effectiveType : "unknown",
    downlink: connection ? connection.downlink : null,
    rtt: connection ? connection.rtt : null,
    saveData: connection ? connection.saveData : false,
  };
}

export async function getFullNetworkInfo() {
  const [externalInfo, localInfo] = await Promise.all([
    getNetworkInfo().catch(() => ({ ip: "unknown" })),
    Promise.resolve(getLocalNetworkInfo()),
  ]);
  return { external: externalInfo, local: localInfo };
}

export function clearNetworkCache() {
  cachedNetworkInfo = null;
  cacheTimestamp = null;
}
