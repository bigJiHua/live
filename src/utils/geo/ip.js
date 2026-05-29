/**
 * IP 定位 — 后端代理，IP/GPS 独立，互不绑架
 */
import axios from "axios";
import { getAuthSecurityHeaders } from "../securityHeaders";

export async function getIpLocation() {
  try {
    const { data } = await axios.get("/api/v1/geo/ip", {
      timeout: 8000,
      headers: getAuthSecurityHeaders(),
    });
    if (data.status === 200 && data.data?.address) {
      const loc = data.data;
      return { success: true, type: "ip", address: loc.address, lat: loc.lat || 0, lng: loc.lng || 0 };
    }
    throw new Error("IP 定位无结果");
  } catch (err) {
    console.error("[IP定位]", err);
    return { success: false, type: "ip", address: "", lat: 0, lng: 0, error: err.message };
  }
}
