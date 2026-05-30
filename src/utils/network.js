/**
 * 网络信息获取工具
 *
 * 策略：浏览器 GPS 优先（仅一次）→ 直调 ip.sb（用户真实 IP）
 */
import axios from "axios";

// 缓存
let cachedNetworkInfo = null;
let cacheTimestamp = null;
const CACHE_DURATION = 30 * 60 * 1000;

// GPS 一次性标记
let gpsTried = false;
let gpsDenied = false;

/** 静默尝试 GPS（仅一次，被拒后不再重试） */
async function tryGpsQuietly() {
  if (gpsTried && gpsDenied) return null;
  if (!navigator.geolocation) { gpsTried = true; gpsDenied = true; return null; }

  let permissionState = "prompt";
  if (navigator.permissions) {
    try { permissionState = (await navigator.permissions.query({ name: "geolocation" })).state; } catch {}
  }
  if (permissionState === "denied") { gpsTried = true; gpsDenied = true; return null; }

  gpsTried = true;
  return new Promise((resolve) => {
    const timer = setTimeout(() => { gpsDenied = true; resolve(null); }, 3000);
    navigator.geolocation.getCurrentPosition(
      (pos) => { clearTimeout(timer); resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }); },
      () => { clearTimeout(timer); gpsDenied = true; resolve(null); },
      { enableHighAccuracy: false, timeout: 3000, maximumAge: 600000 }
    );
  });
}

export async function getNetworkInfo() {
  if (cachedNetworkInfo && cacheTimestamp && Date.now() - cacheTimestamp < CACHE_DURATION) {
    return cachedNetworkInfo;
  }

  // 并行：前端 GPS + 前端直调 ip.sb（浏览器出口 IP = 用户真实 IP）
  const gpsPromise = tryGpsQuietly();
  const ipPromise = fetchFromIpSb();

  const gps = await gpsPromise;

  if (gps) {
    const ipData = await ipPromise;
    const info = {
      ...ipData,
      latitude: gps.lat, longitude: gps.lng,
      source: "gps+ip", timestamp: Date.now(),
    };
    cachedNetworkInfo = info; cacheTimestamp = Date.now();
    return info;
  }

  const info = {
    ...(await ipPromise),
    source: "ip", timestamp: Date.now(),
  };
  cachedNetworkInfo = info; cacheTimestamp = Date.now();
  return info;
}

/** 直调 ip.sb → ipinfo.io 回退 */
async function fetchFromIpSb() {
  // 1. ip.sb
  try {
    const { data } = await axios.get("https://api.ip.sb/geoip", {
      timeout: 5000, headers: { Accept: "application/json" },
    });
    return {
      ip: data.ip || "", country: data.country || "", countryCode: data.country_code || "",
      region: data.region || "", city: data.city || "", isp: data.isp || "", asn: data.asn || "",
      timezone: data.timezone || "", latitude: data.latitude || null, longitude: data.longitude || null,
    };
  } catch (e1) {
    console.warn("[network] ip.sb 失败，回退 ipinfo.io:", e1.message);
  }
  // 2. ipinfo.io
  try {
    const { data } = await axios.get("https://ipinfo.io", { timeout: 5000 });
    const loc = data.loc ? data.loc.split(",").map(Number) : [];
    return {
      ip: data.ip || "", country: data.country || "", countryCode: data.country || "",
      region: data.region || "", city: data.city || "",
      isp: (data.org || "").replace(/^AS\d+\s*/, ""),
      asn: (data.org || "").match(/^AS\d+/)?.at(0) || "",
      timezone: data.timezone || "", latitude: loc[0] || null, longitude: loc[1] || null,
    };
  } catch (e2) {
    console.error("[network] ipinfo.io 也失败:", e2.message);
    return { ip: "unknown", country: "", countryCode: "", region: "", city: "", isp: "", asn: "", timezone: "", latitude: null, longitude: null };
  }
}

export function getLocalNetworkInfo() {
  const c = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  return {
    online: navigator.onLine, connectionType: c ? c.effectiveType : "unknown",
    downlink: c ? c.downlink : null, rtt: c ? c.rtt : null, saveData: c ? c.saveData : false,
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
  cachedNetworkInfo = null; cacheTimestamp = null;
}
