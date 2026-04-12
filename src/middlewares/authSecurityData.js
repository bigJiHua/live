const CryptoJS = require("crypto-js");
const db = require("../common/config/db");

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
      return res.say("安全隧道未建立", 401);
    }
    const device = rows[0];

    // 2. 检查锁定状态
    if (
      device.locked_until &&
      Date.now() < new Date(device.locked_until).getTime()
    ) {
      return res.say("设备环境异常，请稍后再试", 423);
    }

    // 3. 执行 AES 解密 (CBC 模式)
    const keyHex = CryptoJS.enc.Utf8.parse(device.aes_key);
    const ivHex = CryptoJS.enc.Hex.parse(payload.substring(0, 32));
    const cipherText = payload.substring(32);

    const decrypted = CryptoJS.AES.decrypt(cipherText, keyHex, {
      iv: ivHex,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7,
    });

    const decryptedStr = decrypted.toString(CryptoJS.enc.Utf8);

    if (!decryptedStr) {
      throw new Error("MALFORMED_UTF8");
    }

    // 4. 挂载数据
    try {
      req.body = JSON.parse(decryptedStr);
      console.log(req.body.data);
      
    } catch (e) {
      throw new Error("INVALID_JSON");
    }

    req.device = device;
    next();
  } catch (error) {
    console.error(`[Security Decrypt Error]: ${error.message}`);

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
