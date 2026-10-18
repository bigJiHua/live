import CryptoJS from "crypto-js";

/**
 * 财务平台专用 AES-256-CBC 加密工具
 * 采用 32字节(256位) Key + 16字节 IV
 */
export const aesUtil = {
  /**
   * 加密：将明文转为 [IV(32位Hex)][密文]
   * @param {string} data - 待加密字符串
   * @param {string} key - 32位字符串密钥
   */
  encrypt(data, key) {
    if (!data || !key) return data;

    // 🔒 安全整改 P2-22（2026-10-08）：补异常兜底。
    //   `CryptoJS.enc.Hex.parse(key)` 对**非法长度/非 hex 字符**的 key 会抛错，
    //   原实现没有 try/catch，异常会一路冒泡到 `request.js:76` 的
    //   `encrypt(...)` 调用处，变成未捕获异常（整页交互瘫掉，用户只看到"没反应"）。
    //   这里改为「返回原文由后端按明文处理」，保证功能不中断。
    try {
      // 1. 格式化 Key 为 WordArray（Hex 解析：64位hex字符 → 32字节 = AES-256）
      const keyHex = CryptoJS.enc.Hex.parse(key);

      // 2. 生成随机 IV (16字节)
      const iv = CryptoJS.lib.WordArray.random(16);

      // 3. 执行加密
      const encrypted = CryptoJS.AES.encrypt(data, keyHex, {
        iv: iv,
        mode: CryptoJS.mode.CBC,
        padding: CryptoJS.pad.Pkcs7
      });

      // 4. 返回：IV 的 Hex 字符串 (32位) + 密文 Base64
      //    后端解密时截取前 32 位即可获得 IV
      return iv.toString() + encrypted.toString();
    } catch (err) {
      // 密钥不可用（未握手 / 握手失败 / 非法 key）⇒ 明确告警后降级明文，
      // 不能静默吞掉，否则线上排查会毫无线索。
      console.warn('[aes] 加密失败，已降级为明文提交（请检查握手是否成功）:', err?.message);
      return data;
    }
  },

  /**
   * 解密：用于处理后端返回的加密数据
   * @param {string} cipherTextWithIv - 包含 IV 的密文
   * @param {string} key - 密钥
   */
  decrypt(cipherTextWithIv, key) {
    if (!cipherTextWithIv || !key) return cipherTextWithIv;

    try {
      const keyHex = CryptoJS.enc.Hex.parse(key);

      // 1. 提取前 32 位 Hex 字符作为 IV
      const iv = CryptoJS.enc.Hex.parse(cipherTextWithIv.substring(0, 32));
      
      // 2. 提取真正的密文
      const actualCipherText = cipherTextWithIv.substring(32);

      // 3. 解密
      const decrypted = CryptoJS.AES.decrypt(actualCipherText, keyHex, {
        iv: iv,
        mode: CryptoJS.mode.CBC,
        padding: CryptoJS.pad.Pkcs7
      });

      return decrypted.toString(CryptoJS.enc.Utf8);
    } catch (e) {
      console.error("AES 解密失败:", e);
      return null;
    }
  }
};