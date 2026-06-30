const crypto = require("crypto");

// 与 JWT_SECRET 共用或单独配置
const SECRET = process.env.SHARE_TOKEN_SECRET || process.env.JWT_SECRET || "live-share-fallback";

/**
 * 生成分享 token
 * @param {{ diaryId: string, vs: number, pw: string|number, expireHours: number }} opts
 * @returns {string} base64url 编码的加密 token
 */
function generateShareToken({ diaryId, vs, pw, expireHours = 24 }) {
  const payload = JSON.stringify({
    did: diaryId,        // diary id
    vs,                   // 批次号
    pw: String(pw),       // 密码熵
    exp: Date.now() + expireHours * 3600 * 1000,
  });

  const key = crypto.createHash("sha256").update(SECRET).digest();
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);

  let encrypted = cipher.update(payload, "utf8", "base64url");
  encrypted += cipher.final("base64url");
  const tag = cipher.getAuthTag().toString("base64url");

  // 格式: iv.密文.tag
  return `${iv.toString("base64url")}.${encrypted}.${tag}`;
}

/**
 * 验证并解析分享 token
 * @param {string} token
 * @returns {{ did: string, vs: number, pw: string, exp: number }}
 * @throws 解密失败或格式错误
 */
function verifyShareToken(token) {
  if (!token || typeof token !== "string") {
    throw new Error("INVALID_TOKEN");
  }

  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("MALFORMED_TOKEN");

  const [ivB64, encrypted, tagB64] = parts;

  const key = crypto.createHash("sha256").update(SECRET).digest();
  const iv = Buffer.from(ivB64, "base64url");
  const tag = Buffer.from(tagB64, "base64url");

  const decipher = crypto.createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);

  let decrypted = decipher.update(encrypted, "base64url", "utf8");
  decrypted += decipher.final("utf8");

  const parsed = JSON.parse(decrypted);

  // 必填字段校验
  if (!parsed.did || parsed.vs === undefined || parsed.pw === undefined || !parsed.exp) {
    throw new Error("INVALID_PAYLOAD");
  }

  return parsed;
}

module.exports = { generateShareToken, verifyShareToken };
