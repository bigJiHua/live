/**
 * 高德定位 — GPS + 后端高德逆地理编码
 */
import axios from "axios";
import { safeRequestGps } from "./permission";
import { getAuthSecurityHeaders } from "../securityHeaders";

function formatCoord(lat, lng) {
  return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}

export async function getAmapLocation() {
  const gps = await safeRequestGps();

  if (!gps.granted) {
    return {
      success: false,
      type: "gps",
      address: "",
      lat: 0,
      lng: 0,
      error:
        gps.reason === "denied"
          ? "定位权限已被拒绝，请点击地址栏左侧锁图标 → 位置 → 允许"
          : gps.reason === "unsupported"
          ? "浏览器不支持定位"
          : "获取位置失败",
    };
  }

  // GPS 成功 → 后端高德逆地理
  try {
    const { data } = await axios.get("/api/v1/geo/regeo", {
      params: { lng: gps.lng, lat: gps.lat },
      timeout: 5000,
      headers: getAuthSecurityHeaders(),
    });
    if (data.status === 200 && data.data?.address) {
      return { success: true, type: "gps", address: data.data.address, lat: gps.lat, lng: gps.lng };
    }
  } catch (err) {
    console.log("[高德] 逆地理失败:", err.message);
  }

  // 逆地理失败，坐标兜底
  return { success: true, type: "gps", address: formatCoord(gps.lat, gps.lng), lat: gps.lat, lng: gps.lng };
}
