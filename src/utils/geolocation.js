/**
 * 地理位置获取工具
 * 支持浏览器 Geolocation API 和 IP 定位回退
 */

import axios from "axios";

/**
 * 浏览器原生定位
 * @returns {Promise<{success: boolean, address: string, lat: number, lng: number}>}
 */
export function getBrowserLocation() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve({ success: false, address: "" });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        // 使用逆地理编码获取地址
        try {
          // 使用免费的 Nominatim 地理编码服务
          const res = await axios.get(
            `https://nominatim.openstreetmap.org/reverse`,
            {
              params: {
                lat: latitude,
                lon: longitude,
                format: "json",
                "accept-language": "zh",
              },
              headers: {
                "Accept-Language": "zh",
              },
              timeout: 5000,
            }
          );

          const address = res.data?.display_name || "";
          // 简化地址：只取省-市-区
          const simpleAddress = simplifyAddress(address);

          resolve({
            success: true,
            address: simpleAddress,
            lat: latitude,
            lng: longitude,
            fullAddress: address,
          });
        } catch (err) {
          console.error("逆地理编码失败:", err);
          resolve({
            success: true,
            address: `${latitude.toFixed(2)}°, ${longitude.toFixed(2)}°`,
            lat: latitude,
            lng: longitude,
          });
        }
      },
      (error) => {
        console.error("浏览器定位失败:", error);
        resolve({ success: false, address: "" });
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 300000, // 缓存5分钟
      }
    );
  });
}

/**
 * IP 定位（回退方案）
 * @returns {Promise<{success: boolean, address: string, lat?: number, lng?: number}>}
 */
export async function getIpLocation() {
  try {
    // 使用 ip.sb 获取 IP 信息
    const res = await axios.get("https://api.ip.sb/geoip", {
      timeout: 5000,
    });

    const { country, region, city, latitude, longitude } = res.data || {};
    if (country && region && city) {
      const address = `${region} ${city}`;
      return {
        success: true,
        address,
        lat: latitude,
        lng: longitude,
      };
    }

    throw new Error("IP 定位信息不完整");
  } catch (err) {
    console.error("IP 定位失败:", err);
    return { success: false, address: "" };
  }
}

/**
 * 综合定位：优先浏览器定位，失败则 IP 定位
 * @returns {Promise<string>} 地址字符串
 */
export async function getLocation() {
  // 1. 先尝试浏览器定位
  const browserLoc = await getBrowserLocation();
  if (browserLoc.success && browserLoc.address) {
    return browserLoc.address;
  }

  // 2. 回退到 IP 定位
  const ipLoc = await getIpLocation();
  if (ipLoc.success && ipLoc.address) {
    return ipLoc.address;
  }

  // 3. 都失败，返回默认
  return "未知位置";
}

/**
 * 综合定位：优先浏览器定位，失败则 IP 定位
 * @returns {Promise<{name: string, lat: number, lng: number}>} 完整位置对象
 */
export async function getFullLocation() {
  // 1. 先尝试浏览器定位
  const browserLoc = await getBrowserLocation();
  if (browserLoc.success && browserLoc.address) {
    return {
      name: browserLoc.address,
      lat: browserLoc.lat,
      lng: browserLoc.lng,
    };
  }

  // 2. 回退到 IP 定位
  const ipLoc = await getIpLocation();
  if (ipLoc.success && ipLoc.address) {
    return {
      name: ipLoc.address,
      lat: ipLoc.lat || 0,
      lng: ipLoc.lng || 0,
    };
  }

  // 3. 都失败，返回默认
  return {
    name: "未知位置",
    lat: 0,
    lng: 0,
  };
}

/**
 * 简化地址，只保留省-市-区
 */
function simplifyAddress(fullAddress) {
  if (!fullAddress) return "";

  // 常见格式：中国 XX省 XX市 XX区 XX街道
  const patterns = [
    /([^省]+省)?([^市]+市)?([^区]+区)?/,
    /([^省]+省)?([^市]+市)?([^县]+县)?/,
  ];

  for (const pattern of patterns) {
    const match = fullAddress.match(pattern);
    if (match) {
      const parts = [match[1], match[2], match[3]].filter(Boolean);
      if (parts.length > 0) {
        return parts.join("");
      }
    }
  }

  // 如果匹配失败，截取前30个字符
  return fullAddress.slice(0, 30);
}
