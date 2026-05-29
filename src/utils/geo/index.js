/**
 * 地理位置模块统一入口
 *
 *   精确定位 → browser.js  (纯浏览器 GPS)
 *   高德定位 → amap.js     (GPS + 高德逆地理)
 *   大致位置 → ip.js       (后端代理 IP 定位)
 */
import { getBrowserLocation } from "./browser";
import { getIpLocation } from "./ip";

export { getBrowserLocation } from "./browser";
export { getAmapLocation } from "./amap";
export { getIpLocation } from "./ip";
export { getGeoPermissionState } from "./permission";

/**
 * 综合定位：GPS 优先 → IP 兜底（顺序执行，不并发 GPS）
 */
export async function getFullLocation() {
  const gps = await getBrowserLocation();
  if (gps.success) return { name: gps.address, lat: gps.lat, lng: gps.lng, type: "gps" };

  const ip = await getIpLocation();
  if (ip.success) return { name: ip.address, lat: ip.lat, lng: ip.lng, type: "ip" };

  return { name: "未知位置", lat: 0, lng: 0, type: "unknown" };
}
