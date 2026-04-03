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
    // 强制要求 X-Requested-With，过滤掉 90% 的直接浏览器访问
    if (h["x-requested-with"] !== "XMLHttpRequest") {
      console.warn('拦截api直接请求')
      return res.say("非法请求！", 400);
      // return res.status(400).json({ status: 400, message: "非法请求！" });
    }

    /* ================== 2. 时间戳与重放攻击校验 ================== */
    const ts = Number(h["x-client-timestamp"]);
    const now = Date.now();
    // 缩短至 2 分钟。财务数据对实时性要求高，5 分钟太长了
    if (!ts || Math.abs(now - ts) > 2 * 60 * 1000) {
      console.warn('请求失效，请检查系统时间')
      return res.say("非法请求！", 400);
      // return res
      //   .status(400)
      //   .json({ status: 400, message: "请求失效，请检查系统时间" });
    }

    /* ================== 3. IP 格式规整化 ================== */
    const clientIp = h["x-client-ip"] || req.ip;
    // 使用内置 net.isIP，支持 IPv4 和 IPv6，且不会出正则 Bug
    if (clientIp && net.isIP(clientIp) === 0) {
      console.warn('客户端IP异常')
      return res.say("非法请求！", 400);
      // return res.status(400).json({ status: 400, message: "客户端IP异常" });
    }

    /* ================== 4. 指纹与设备一致性 ================== */
    const fp = h["x-fingerprint-hash"];
    const ua = h["user-agent"] || "";
    const customUa = h["x-user-agent-custom"] || "";
    
    if (!fp || ua !== customUa) {
      console.warn('设备环境异常')
      return res.say("非法请求！", 400);
      // return res.status(400).json({ status: 400, message: "设备环境异常" });
    }

    /* ================== 5. 增强签名校验 (加盐) ================== */
    const sign = h["x-sign"];
    if (sign) {
      const token = (h.authorization || "").replace("Bearer ", "");
      // 必须加上服务器端的 Secret，增加破解难度
      const salt = process.env.APP_SECURITY_SALT || "jihau_standard_salt";
      const raw = `${req.method}|${req.originalUrl}|${ts}|${token}|${salt}`;
      const serverSign = crypto.createHash("md5").update(raw).digest("hex");

      if (sign !== serverSign) {
        console.warn("安全签名验证失败");
        return res.say("非法请求！", 400);
      }
    }

    /* ================== 6. 挂载安全上下文 ================== */
    // 将解析好的数据挂载到 req.security，后续存入 user_info 或 card_log 非常方便
    req.security = {
      ip: clientIp,
      fingerprint: fp,
      deviceDesc: `${h["x-device-model"] || "Unknown"} (${
        h["x-os-name"] || "Unknown"
      })`,
      timestamp: ts,
      isMobile: h["x-device-type"] === "mobile",
    };

    next();
  } catch (err) {
    console.error("Security Middleware Error:", err);
    return res
      .status(500)
      .json({ status: 500, message: "Server Security Error" });
  }
};

module.exports = { securityCheck };
