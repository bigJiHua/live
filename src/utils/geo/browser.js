/**
 * 精确定位 — 纯浏览器 GPS，不调后端
 */
import { safeRequestGps } from "./permission";

function formatCoord(lat, lng) {
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}

export async function getBrowserLocation() {
  const result = await safeRequestGps();

  if (!result.granted) {
    const msg =
      result.reason === "denied"
        ? "定位权限已被拒绝，请点击地址栏左侧锁图标 → 位置 → 允许"
        : result.reason === "unsupported"
        ? "浏览器不支持定位"
        : "获取位置失败";
    return { success: false, type: "gps", address: "", lat: 0, lng: 0, error: msg };
  }

  return {
    success: true,
    type: "gps",
    address: formatCoord(result.lat, result.lng),
    lat: result.lat,
    lng: result.lng,
  };
}
