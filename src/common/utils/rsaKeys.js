/**
 * RSA 密钥管理（安全键盘字符级加密专用）
 *
 * - 进程启动时惰性生成 2048 位 RSA 密钥对，私钥仅存服务端内存
 * - 公钥通过握手接口下发给前端安全键盘（JSEncrypt 逐字符加密）
 * - 登录时用私钥解出用户密码明文再做 bcrypt 校验
 *
 * ⚠️ 注意：密钥对为进程内存级，重启后公钥变化；前端每次登录都先握手取公钥，
 * 所以不受影响。若未来多实例部署需改为持久化（如 DB/文件），单机可接受。
 */
const crypto = require("crypto");

let privateKey = null;
let publicKey = null;
let initialized = false;

/** 生成（或复用）密钥对，返回 publicKey */
function ensureKeys() {
  if (initialized) return publicKey;
  const { privateKey: priv, publicKey: pub } = crypto.generateKeyPairSync(
    "rsa",
    {
      modulusLength: 2048,
      publicKeyEncoding: { type: "spki", format: "pem" },
      privateKeyEncoding: { type: "pkcs8", format: "pem" },
    }
  );
  privateKey = priv;
  publicKey = pub;
  initialized = true;
  return publicKey;
}

/** 获取 PEM 公钥（供握手接口下发） */
function getPublicKey() {
  return ensureKeys();
}

/**
 * 解密 RSA 字符数组，拼接为明文
 * @param {Array<string>} chars - JSEncrypt 逐字符加密后的密文数组
 * @returns {string} 拼接明文
 */
function decryptChars(chars) {
  if (!Array.isArray(chars) || chars.length === 0) return "";
  ensureKeys();
  const dec = crypto.privateDecrypt;
  const joined = chars
    .map((c) => {
      try {
        return dec(
          { key: privateKey, padding: crypto.constants.RSA_PKCS1_PADDING },
          Buffer.from(c, "base64")
        ).toString("utf8");
      } catch {
        return "";
      }
    })
    .join("");
  return joined;
}

/**
 * 智能解析密码字段：
 * - 若为数组 → 视为 RSA 逐字符密文，解密拼接
 * - 若为字符串 → 原样返回（兼容旧版明文/AES 整包）
 */
function resolvePassword(raw) {
  if (Array.isArray(raw)) return decryptChars(raw);
  return typeof raw === "string" ? raw : "";
}

module.exports = { getPublicKey, decryptChars, resolvePassword };
