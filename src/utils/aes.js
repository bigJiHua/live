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

    // 1. 格式化 Key 为 WordArray
    const keyHex = CryptoJS.enc.Utf8.parse(key);

    // 2. 生成随机 IV (16字节)
    const iv = CryptoJS.lib.WordArray.random(16);

    // 3. 执行加密
    const encrypted = CryptoJS.AES.encrypt(data, keyHex, {
      iv: iv,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7
    });

    // 4. 返回：IV 的 Hex 字符串 (32位) + 密文 Base64
    // 后端解密时截取前 32 位即可获得 IV
    return iv.toString() + encrypted.toString();
  },

  /**
   * 解密：用于处理后端返回的加密数据
   * @param {string} cipherTextWithIv - 包含 IV 的密文
   * @param {string} key - 密钥
   */
  decrypt(cipherTextWithIv, key) {
    if (!cipherTextWithIv || !key) return cipherTextWithIv;

    try {
      const keyHex = CryptoJS.enc.Utf8.parse(key);

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