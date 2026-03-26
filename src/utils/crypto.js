/**
 * 加密工具类
 */

/**
 * 生成随机 PIN 码
 * @param {number} length - PIN 码长度，默认 6
 * @returns {string} 生成的 PIN 码
 */
function generatePin(length = 6) {
  const min = Math.pow(10, length - 1);
  const max = Math.pow(10, length) - 1;
  return Math.floor(min + Math.random() * (max - min + 1)).toString();
}

/**
 * 验证 PIN 码哈希
 * @param {string} pin - 明文 PIN 码
 * @param {string} pinHash - 哈希后的 PIN 码
 * @returns {Promise<boolean>} 是否匹配
 */
async function verifyPinHash(pin, pinHash) {
  const bcrypt = require('bcryptjs');
  return bcrypt.compare(pin, pinHash);
}

/**
 * 加密 PIN 码
 * @param {string} pin - 明文 PIN 码
 * @returns {Promise<string>} 哈希后的 PIN 码
 */
async function hashPin(pin) {
  const bcrypt = require('bcryptjs');
  return bcrypt.hash(pin, 10);
}

/**
 * 生成随机字符串
 * @param {number} length - 字符串长度
 * @returns {string} 随机字符串
 */
function generateRandomString(length = 32) {
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * characters.length));
  }
  return result;
}

/**
 * 生成 UUID v4
 * @returns {string} UUID
 */
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

module.exports = {
  generatePin,
  verifyPinHash,
  hashPin,
  generateRandomString,
  generateUUID,
};
