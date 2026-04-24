const crypto = require("crypto");
const net = require("net");

const securityCheck = (req, res, next) => {
  try {
    // 跳过静态文件请求（图片、字体等）
    if (req.path.includes("/uploads/") || req.path.includes("/temp/")) {
      return next();
    }

    const h = req.headers;
    /* ================== 1. 严格头校验 ================== */
    if (h["x-requested-with"] !== "XMLHttpRequest") {
      console.warn('拦截api直接请求')
      return res.say("非法请求！", 400);
    }

    /* ================== 2. 时间戳与重放攻击校验 ================== */
    const ts = Number(h["x-client-timestamp"]);
    const now = Date.now();
    if (!ts || Math.abs(now - ts) > 2 * 60 * 1000) {
      console.warn('请求失效，请检查系统时间')
      return res.say("非法请求！", 400);
    }

    /* ================== 3. IP 格式规整化 ================== */
    const clientIp = h["x-client-ip"] || req.ip;
    if (clientIp && net.isIP(clientIp) === 0) {
      console.warn('客户端IP异常')
      return res.say("非法请求！", 400);
    }

    /* ================== 4. 指纹与设备一致性 ================== */
    const fp = h["x-fingerprint-hash"];
    const ua = h["user-agent"] || "";
    const customUa = h["x-user-agent-custom"] || "";
    
    if (fp && customUa && ua !== customUa) {
      console.warn('设备环境异常')
      return res.say("非法请求！", 400);
    }

    /* ================== 5. 增强签名校验 (加盐) ================== */
    const sign = h["x-sign"];
    if (sign) {
      const token = (h.authorization || "").replace("Bearer ", "");
      const salt = process.env.APP_SECURITY_SALT || "jihau_standard_salt";
      const raw = `${req.method}|${req.originalUrl}|${ts}|${token}|${salt}`;
      const serverSign = crypto.createHash("md5").update(raw).digest("hex");

      if (sign !== serverSign) {
        console.warn("安全签名验证失败");
        return res.say("非法请求！", 400);
      }
    }

    /* ================== 6. 挂载安全上下文 ================== */
    req.security = {
      ip: clientIp,
      fingerprint: fp,
      deviceDesc: `${h["x-device-model"] || "Unknown"} (${h["x-os-name"] || "Unknown"})`,
      timestamp: ts,
      isMobile: h["x-device-type"] === "mobile",
    };

    next();
  } catch (err) {
    console.error("Security Middleware Error:", err);
    return res.status(500).json({ status: 500, message: "Server Security Error" });
  }
};

module.exports = { securityCheck };
