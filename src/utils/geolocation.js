/**
 * 地理位置获取工具
 *
 * ┌──────────┬──────────────────────────────────────────────┐
 * │ 精确定位  │ 纯浏览器 GPS，不请求后端，需 GPS 授权        │
 * │ 高德定位  │ GPS → 后端 /geo/regeo（高德逆地理），需授权  │
 * │ 大致位置  │ 直调 ip.sb，≤5次免授权，>5次强制 GPS 授权    │
 * └──────────┴──────────────────────────────────────────────┘
 */
import axios from "axios";
import { getAuthSecurityHeaders } from "./securityHeaders";

const COORD_DECIMALS = 6;
const IP_USAGE_KEY = "ip_location_usage";
const IP_USAGE_LIMIT = 5;

/* ================================================================
 *  权限 — 直接触发浏览器原生授权弹窗
 * ================================================================ */

/**
 * 强制 GPS 授权 — 循环弹窗直到用户同意
 *
 * navigator.geolocation.getCurrentPosition() 会直接触发：
 *   Chrome / Safari / Android WebView / iPhone 系统级定位授权弹窗
 *
 * code=1 PERMISSION_DENIED → 用户点了"拒绝"，浏览器不会再弹，停止
 * code=2 POSITION_UNAVAILABLE / code=3 TIMEOUT → 重试
 * 最多重试 5 轮
 */
export function ensureGeoGranted() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      return resolve({ granted: false, lat: 0, lng: 0, reason: "unsupported" });
    }

    let retries = 0;
    const maxRetries = 5;

    function attempt() {
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve({
          granted: true,
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
        }),
        (err) => {
          // 用户明确拒绝 → 立即停止
          if (err.code === 1) {
            console.log("[geo] 用户拒绝授权");
            return resolve({ granted: false, lat: 0, lng: 0, reason: "denied" });
          }

          retries++;
          if (retries >= maxRetries) {
            console.log(`[geo] ${maxRetries} 次重试后放弃`);
            return resolve({ granted: false, lat: 0, lng: 0, reason: "max_retries" });
          }

          console.log(`[geo] 第 ${retries} 次失败 (code=${err.code})，${500 * retries}ms 后重试…`);
          setTimeout(attempt, 500 * retries); // 递增延迟 500ms/1000ms/1500ms…
        },
        {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 0, // 不用缓存，强制实时位置
        }
      );
    }

    attempt();
  });
}

/* ================================================================
 *  大致位置频控（存储在 session，页面关闭重置）
 * ================================================================ */

function getIpUsageCount() {
  try {
    const raw = sessionStorage.getItem(IP_USAGE_KEY);
    if (!raw) return 0;
    const data = JSON.parse(raw);
    // 当天有效
    const today = new Date().toDateString();
    if (data.date !== today) return 0;
    return data.count || 0;
  } catch {
    return 0;
  }
}

function incIpUsageCount() {
  const count = getIpUsageCount() + 1;
  sessionStorage.setItem(IP_USAGE_KEY, JSON.stringify({
    date: new Date().toDateString(),
    count,
  }));
  return count;
}

/* ================================================================
 *  格式化工具
 * ================================================================ */

function formatCoord(lat, lng) {
  return `${lat.toFixed(COORD_DECIMALS)}, ${lng.toFixed(COORD_DECIMALS)}`;
}

/* ================================================================
 *  精确定位 — 纯浏览器 GPS，不调后端
 * ================================================================ */
export async function getBrowserLocation() {
  const perm = await ensureGeoGranted();
  if (!perm.granted) {
    return {
      success: false,
      address: "",
      lat: 0,
      lng: 0,
      error: perm.reason === "denied"
        ? "请在浏览器设置中允许定位权限"
        : perm.reason === "unsupported" ? "浏览器不支持定位" : "获取位置失败",
    };
  }
  return {
    success: true,
    address: formatCoord(perm.lat, perm.lng),
    lat: perm.lat,
    lng: perm.lng,
  };
}

/* ================================================================
 *  高德定位 — GPS + 后端高德逆地理编码
 * ================================================================ */
export async function getAmapLocation() {
  // 1. 强制 GPS 授权
  const perm = await ensureGeoGranted();
  if (!perm.granted) {
    return {
      success: false,
      address: "",
      lat: 0,
      lng: 0,
      error: perm.reason === "denied" ? "请在浏览器设置中允许定位权限" : "获取位置失败",
    };
  }

  // 2. GPS 坐标 → 后端高德逆地理
  try {
    const { data } = await axios.get("/api/v1/geo/regeo", {
      params: { lng: perm.lng, lat: perm.lat },
      timeout: 5000,
      headers: getAuthSecurityHeaders(),
    });
    if (data.status === 200 && data.data?.address) {
      // 后端已拼好省市区
      return { success: true, address: data.data.address, lat: perm.lat, lng: perm.lng };
    }
  } catch (err) {
    console.log("[高德定位] 逆地理失败，回退坐标:", err.message);
  }

  // 3. 逆地理失败，返回坐标
  return { success: true, address: formatCoord(perm.lat, perm.lng), lat: perm.lat, lng: perm.lng };
}

/* ================================================================
 *  大致位置 — 浏览器直调 ip.sb
 * ================================================================ */
export async function getIpLocation() {
  const count = incIpUsageCount();

  // >5 次：强制 GPS 授权，拒绝则拦截
  if (count > IP_USAGE_LIMIT) {
    const perm = await ensureGeoGranted();
    if (!perm.granted) {
      return {
        success: false,
        address: "",
        lat: 0,
        lng: 0,
        error: "已超过每日 IP 定位次数，请授权定位后重试",
      };
    }
  }

  // 直调 ip.sb
  try {
    const res = await axios.get("https://api.ip.sb/geoip", {
      timeout: 5000,
      headers: { Accept: "application/json" },
    });
    const { country, region, city, latitude, longitude } = res.data || {};
    if (country && region && city) {
      return {
        success: true,
        address: `${region} ${city}`,
        lat: latitude || 0,
        lng: longitude || 0,
      };
    }
    // ip.sb 返回不完整，回退 ipinfo.io
    throw new Error("ip.sb 数据不完整");
  } catch (e1) {
    console.warn("[大致位置] ip.sb 失败:", e1.message);
    try {
      const res = await axios.get("https://ipinfo.io", { timeout: 5000 });
      const d = res.data || {};
      const { city, region, loc } = d;
      const address = [region, city].filter(Boolean).join(" ");
      let lat = 0, lng = 0;
      if (loc) {
        const parts = loc.split(",").map(Number);
        lat = parts[0] || 0;
        lng = parts[1] || 0;
      }
      return { success: !!address, address: address || "未知", lat, lng };
    } catch (e2) {
      console.error("[大致位置] ipinfo.io 也失败:", e2.message);
      return { success: false, address: "", lat: 0, lng: 0 };
    }
  }
}

/* ================================================================
 *  综合定位（向后兼容旧调用）
 * ================================================================ */
export async function getLocation() {
  const loc = await getBrowserLocation();
  if (loc.success) return loc.address;
  const ip = await getIpLocation();
  return ip.success ? ip.address : "未知位置";
}

export async function getFullLocation() {
  const loc = await getBrowserLocation();
  if (loc.success) return { name: loc.address, lat: loc.lat, lng: loc.lng };
  const ip = await getIpLocation();
  if (ip.success) return { name: ip.address, lat: ip.lat, lng: ip.lng };
  return { name: "未知位置", lat: 0, lng: 0 };
}

export async function getLocationOptions() {
  const [browser, amap, ip] = await Promise.all([
    getBrowserLocation().catch(() => ({ success: false, address: "" })),
    getAmapLocation().catch(() => ({ success: false, address: "" })),
    getIpLocation().catch(() => ({ success: false, address: "" })),
  ]);
  return {
    browser: {
      name: browser.success ? browser.address : (browser.error || "定位失败"),
      lat: browser.lat || 0,
      lng: browser.lng || 0,
      error: browser.error,
    },
    amap: {
      name: amap.success ? amap.address : (amap.error || "高德定位失败"),
      lat: amap.lat || 0,
      lng: amap.lng || 0,
      error: amap.error,
    },
    ip: {
      name: ip.success ? ip.address : (ip.error || "未知"),
      lat: ip.lat || 0,
      lng: ip.lng || 0,
      error: ip.error,
    },
  };
}
