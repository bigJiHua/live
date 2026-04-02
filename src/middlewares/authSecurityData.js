const CryptoJS = require("crypto-js");
const db = require("../config/db");

/**
 * 安全解密中间件
 * 处理逻辑：验证指纹 -> 检查有效期 -> 执行 AES 解密 -> 挂载上下文
 */
const decryptWithSecurity = async (req, res, next) => {
  const fp = req.headers["x-fp-id"];
  const scene = req.headers["x-scene"] || "login";
  const payload = req.body?._p;

  // 非加密请求直接放行
  if (!fp || !payload) return next();

  try {
    // 1. 获取设备加密元数据
    const [rows] = await db.execute(
      "SELECT * FROM device_crypto WHERE fingerprint = ? AND scene = ? LIMIT 1",
      [fp, scene]
    );

    if (rows.length === 0) {
      return res.status(401).json({ message: "安全隧道未建立" });
    }
    const device = rows[0];

    // 2. 检查锁定状态 (由 login 逻辑更新 fail_count)
    if (
      device.locked_until &&
      Date.now() < new Date(device.locked_until).getTime()
    ) {
      return res.status(423).json({ message: "设备环境异常，请稍后再试" });
    }

    // 3. 检查 AES 有效期 (增加 30,000ms 宽限期解决网络延迟竞争)
    const now = Date.now();
    const expireTime = new Date(device.aes_expires_at).getTime();
    if (now > expireTime + 30000) {
      return res.status(401).json({ message: "安全隧道已过期" });
    }

    // 4. 执行 AES 解密 (CBC 模式)
    const keyHex = CryptoJS.enc.Utf8.parse(device.aes_key);
    // 密文结构：前32位Hex为IV + 剩余部分为密文
    const ivHex = CryptoJS.enc.Hex.parse(payload.substring(0, 32));
    const cipherText = payload.substring(32);

    const decrypted = CryptoJS.AES.decrypt(cipherText, keyHex, {
      iv: ivHex,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7,
    });

    const decryptedStr = decrypted.toString(CryptoJS.enc.Utf8);

    if (!decryptedStr) {
      throw new Error("MALFORMED_UTF8"); // 可能是 Key 错误
    }
    // 5. 挂载数据
    try {
      // 这里后续引用方式为 req.body.data
      req.body = JSON.parse(decryptedStr);
    } catch (e) {
      throw new Error("INVALID_JSON");
    }

    req.device = device; // 挂载设备信息供 Controller 使用
    next();
  } catch (error) {
    console.error(`[Security Decrypt Error]: ${error.message}`);

    // 失败记录审计
    await db.execute(
      "UPDATE device_crypto SET fail_count = fail_count + 1 WHERE fingerprint = ?",
      [fp]
    );

    const errorMsg =
      error.message === "MALFORMED_UTF8"
        ? "安全校验未通过(KEY_ERR)"
        : "请求数据非法";
    return res.say(errorMsg, 400);
  }
};

module.exports = decryptWithSecurity;
