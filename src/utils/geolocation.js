/**
 * 地理位置获取工具
 * 支持浏览器 Geolocation API 和 IP 定位回退
 */

import axios from "axios";

/**
 * 格式化经纬度为十进制格式（如 22.54321,113.876544）
 */
function formatCoord(lat, lng, decimals = 6) {
  return `${lat.toFixed(decimals)}, ${lng.toFixed(decimals)}`;
}

/**
 * 浏览器原生定位
 * @returns {Promise<{success: boolean, address: string, lat: number, lng: number, error?: string}>}
 */
export function getBrowserLocation() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve({ success: false, address: "", error: "浏览器不支持定位" });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const { latitude, longitude } = position.coords;
        const coordStr = formatCoord(latitude, longitude);
        
        // 尝试获取地址（可选）
        let address = coordStr;
        try {
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
          
          if (res.data?.display_name) {
            address = simplifyAddress(res.data.display_name) || coordStr;
          }
        } catch (err) {
          console.log("逆地理编码失败，使用坐标:", err.message);
        }

        resolve({
          success: true,
          address: address,
          lat: latitude,
          lng: longitude,
        });
      },
      (error) => {
        console.error("浏览器定位失败:", error);
        let errorMsg = "定位失败";
        // 检查错误类型
        if (error.code === 1) {
          errorMsg = "用户拒绝授权定位";
        } else if (error.code === 2) {
          errorMsg = "位置不可用";
        } else if (error.code === 3) {
          errorMsg = "定位超时";
        }
        resolve({ success: false, address: "", error: errorMsg });
      },
      {
        enableHighAccuracy: true, // 启用高精度定位
        timeout: 15000,
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
 * 获取两种定位选项供用户选择
 * @returns {Promise<{browser: {name: string, lat: number, lng: number, error?: string}, ip: {name: string, lat: number, lng: number, error?: string}}>}
 */
export async function getLocationOptions() {
  // 并行获取浏览器定位和IP定位
  const [browserLoc, ipLoc] = await Promise.all([
    getBrowserLocation(),
    getIpLocation().catch(() => ({ success: false, address: "" }))
  ]);

  const result = {
    browser: {
      name: browserLoc.success ? (browserLoc.address || formatCoord(browserLoc.lat, browserLoc.lng)) : (browserLoc.error || "定位失败"),
      lat: browserLoc.lat || 0,
      lng: browserLoc.lng || 0,
      error: browserLoc.error,
    },
    ip: {
      name: ipLoc.success ? (ipLoc.address || "未知") : "未知",
      lat: ipLoc.lat || 0,
      lng: ipLoc.lng || 0,
    },
  };

  return result;
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
