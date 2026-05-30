/**
 * IP 定位 — 前端直调 ip.sb / ipinfo.io（不需要 Key）
 * 直调才能拿到用户的真实 IP，走后端拿的是服务器 IP
 */
import axios from "axios";

export async function getIpLocation() {
  // 1. ip.sb（直调，浏览器出口 IP = 用户公网 IP）
  try {
    const res = await axios.get("https://api.ip.sb/geoip", {
      timeout: 5000,
      headers: { Accept: "application/json" },
    });
    const { country, region, city, latitude, longitude } = res.data || {};
    if (country && region && city) {
      return {
        success: true, type: "ip",
        address: `${region} ${city}`,
        lat: latitude || 0, lng: longitude || 0,
      };
    }
    throw new Error("ip.sb 数据不完整");
  } catch (e1) {
    console.warn("[IP定位] ip.sb 失败:", e1.message);
  }

  // 2. ipinfo.io（直调，免费限额）
  try {
    const res = await axios.get("https://ipinfo.io", { timeout: 5000 });
    const d = res.data || {};
    const address = [d.region, d.city].filter(Boolean).join(" ");
    let lat = 0, lng = 0;
    if (d.loc) {
      const parts = d.loc.split(",").map(Number);
      lat = parts[0] || 0;
      lng = parts[1] || 0;
    }
    return { success: !!address, type: "ip", address: address || "未知", lat, lng };
  } catch (e2) {
    console.error("[IP定位] ipinfo.io 也失败:", e2.message);
    return { success: false, type: "ip", address: "", lat: 0, lng: 0, error: e2.message };
  }
}
