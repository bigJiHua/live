/**
 * 地理位置代理控制器
 * 将第三方 API 调用收口到服务端，Key 不暴露给前端
 */
const axios = require("axios");

const IPINFO_TOKEN = process.env.IPINFO_TOKEN || "";
const AMAP_KEY = process.env.AMAP_KEY || "";

/* ================================================================
 *  内部工具
 * ================================================================ */

/** 从 org 字段提取 ASN */
function extractAsn(org) {
  if (!org) return "";
  const m = org.match(/^AS\d+/);
  return m ? m[0] : "";
}

/** 从 org 字段提取 ISP 名称 */
function extractIsp(org) {
  if (!org) return "";
  return org.replace(/^AS\d+\s*/, "");
}

/** 从高德 rectangle 提取中心点 */
function getCenterFromRectangle(rectangle) {
  if (!rectangle || typeof rectangle !== "string") return { lat: null, lng: null };
  const parts = rectangle.split(";");
  if (parts.length !== 2) return { lat: null, lng: null };
  const [lng1, lat1] = parts[0].split(",").map(Number);
  const [lng2, lat2] = parts[1].split(",").map(Number);
  if (isNaN(lng1) || isNaN(lat1) || isNaN(lng2) || isNaN(lat2)) return { lat: null, lng: null };
  return { lng: (lng1 + lng2) / 2, lat: (lat1 + lat2) / 2 };
}

/* ================================================================
 *  单源请求
 * ================================================================ */

/** ip.sb（可传入指定 IP 查询） */
async function fetchIpSb(ip) {
  const url = ip ? `https://api.ip.sb/geoip/${ip}` : "https://api.ip.sb/geoip";
  const { data } = await axios.get(url, {
    timeout: 5000,
    headers: { Accept: "application/json" },
  });
  return {
    ip: data.ip || ip || "",
    country: data.country || "",
    countryCode: data.country_code || "",
    region: data.region || "",
    city: data.city || "",
    isp: data.isp || "",
    asn: data.asn || "",
    timezone: data.timezone || "",
    latitude: data.latitude || null,
    longitude: data.longitude || null,
  };
}

/** ipinfo.io */
async function fetchIpInfo() {
  const params = IPINFO_TOKEN ? { token: IPINFO_TOKEN } : {};
  const { data } = await axios.get("https://ipinfo.io", { timeout: 5000, params });
  const loc = data.loc ? data.loc.split(",").map(Number) : [];
  return {
    ip: data.ip || "",
    country: data.country || "",
    countryCode: data.country || "",
    region: data.region || "",
    city: data.city || "",
    isp: extractIsp(data.org),
    asn: extractAsn(data.org),
    timezone: data.timezone || "",
    latitude: loc[0] || null,
    longitude: loc[1] || null,
  };
}

/** 高德 IP 定位 */
async function fetchAmapIp(clientIp) {
  if (!AMAP_KEY) throw new Error("AMAP_KEY 未配置");
  const params = { key: AMAP_KEY, output: "JSON" };
  if (clientIp) params.ip = clientIp;

  const { data } = await axios.get("https://restapi.amap.com/v3/ip", {
    timeout: 5000,
    params,
  });

  if (data.status !== "1") throw new Error(data.info || "高德 IP 定位失败");

  const { province, city, adcode, rectangle } = data;
  // 高德对无法定位的 IP 可能返回空数组 []，防御处理
  const p = typeof province === "string" ? province.trim() : "";
  const c = typeof city === "string" ? city.trim() : "";

  if (!p && !c) throw new Error("高德无法定位此 IP（内网/国外）");

  const center = getCenterFromRectangle(rectangle);
  return { province: p, city: c, adcode: adcode || "", lat: center.lat, lng: center.lng };
}

/** 高德逆地理编码 */
async function fetchAmapRegeo(lng, lat) {
  if (!AMAP_KEY) throw new Error("AMAP_KEY 未配置");
  const { data } = await axios.get("https://restapi.amap.com/v3/geocode/regeo", {
    timeout: 5000,
    params: { key: AMAP_KEY, location: `${lng},${lat}`, output: "JSON" },
  });
  if (data.status !== "1") throw new Error(data.info || "逆地理编码失败");
  const ac = data.regeocode?.addressComponent || {};
  // 结构化省市区
  const province = ac.province || "";
  const city = ac.city || ac.district || "";
  const district = ac.district && ac.city ? ac.district : "";
  // 省市区拼接（高德返回 [] 空数组时防御）
  const addr = [province, city, district].filter(Boolean).map(s =>
    typeof s === "string" ? s : ""
  ).filter(Boolean).join("");
  return {
    address: addr || data.regeocode?.formatted_address || "",
    province, city, district,
  };
}

/* ================================================================
 *  对外接口
 * ================================================================ */

/**
 * GET /api/v1/geo/network
 * 获取网络信息（ip.sb 优先 → ipinfo.io 回退）
 * 用客户端真实 IP 查询，而非服务器出口 IP
 */
exports.getNetworkInfo = async (req, res) => {
  const rawIp = req.security?.ip || req.ip || "";
  const clientIp = rawIp.replace(/^::ffff:/, "");
  const isLan = /^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|127\.|0\.)/.test(clientIp);
  const queryIp = isLan ? null : clientIp; // 内网→不传，让 ip.sb 用出口 IP

  try {
    // 1. ip.sb
    try {
      const info = await fetchIpSb(queryIp);
      // 用客户端 IP 覆盖 ip.sb 返回的 IP（ip.sb 可能返回服务端 IP）
      if (clientIp && !isLan) info.ip = clientIp;
      return res.json({ status: 200, data: { ...info, source: "ip.sb" } });
    } catch (e1) {
      console.warn("[geo/network] ip.sb 失败:", e1.message);
    }

    // 2. ipinfo.io
    try {
      const info = await fetchIpInfo();
      if (clientIp && !isLan) info.ip = clientIp;
      return res.json({ status: 200, data: { ...info, source: "ipinfo.io" } });
    } catch (e2) {
      console.error("[geo/network] ipinfo.io 也失败:", e2.message);
      return res.json({ status: 500, message: "所有 IP 服务不可用" });
    }
  } catch (err) {
    console.error("[geo/network]", err);
    return res.json({ status: 500, message: err.message });
  }
};

/**
 * GET /api/v1/geo/ip
 * 高德 IP 定位（需要客户端公网 IP，内网/失败则返回空）
 */
exports.getIpLocation = async (req, res) => {
  const rawIp = req.security?.ip || req.ip || "";
  const clientIp = rawIp.replace(/^::ffff:/, "");
  const isLan = /^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|127\.|0\.)/.test(clientIp);

  if (isLan) {
    return res.json({ status: 200, data: { address: "", lat: null, lng: null, source: "none", reason: "内网IP" } });
  }

  try {
    const loc = await fetchAmapIp(clientIp);
    if (loc.province || loc.city) {
      const address = [loc.province, loc.city].filter(Boolean).join(" ");
      return res.json({ status: 200, data: { address, lat: loc.lat, lng: loc.lng, source: "amap" } });
    }
  } catch (e) {
    console.warn("[geo/ip] 高德失败:", e.message);
  }

  return res.json({ status: 200, data: { address: "", lat: null, lng: null, source: "none" } });
};

/**
 * GET /api/v1/geo/regeo?lng=116.397&lat=39.908
 * 逆地理编码（GPS → 地址，高德优先）
 */
exports.getReverseGeocode = async (req, res) => {
  try {
    const { lng, lat } = req.query;
    if (!lng || !lat) {
      return res.json({ status: 400, message: "缺少 lng / lat 参数" });
    }

    // 高德逆地理编码
    if (AMAP_KEY) {
      try {
        const result = await fetchAmapRegeo(lng, lat);
        return res.json({ status: 200, data: { address: result.address, source: "amap" } });
      } catch (e) {
        console.warn("[geo/regeo] 高德失败:", e.message);
      }
    }

    // 回退 Nominatim
    try {
      const { data } = await axios.get("https://nominatim.openstreetmap.org/reverse", {
        timeout: 5000,
        params: { lat, lon: lng, format: "json", "accept-language": "zh" },
        headers: { "Accept-Language": "zh" },
      });
      const address = data?.display_name || "";
      return res.json({ status: 200, data: { address, source: "nominatim" } });
    } catch (e) {
      return res.json({ status: 200, data: { address: "", source: "none" } });
    }
  } catch (err) {
    console.error("[geo/regeo]", err);
    return res.json({ status: 500, message: err.message });
  }
};
