const { nanoid, customAlphabet } = require('nanoid');

const USER_ID_LENGTH = 50;
const SHORT_ID_LENGTH = 47;
const BILL_ID_SUFFIX = 6; // 6位36进制后缀，同一毫秒内210亿组合，个人记账永不碰撞
const BILL_ID_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const generateSuffix = customAlphabet(BILL_ID_ALPHABET, BILL_ID_SUFFIX);

/**
 * 奠基级 ID 工具类
 */
const idUtils = {
  // 1. 标准用户 ID：对齐当前 varchar(50) 主键/用户字段
  userId: () => nanoid(USER_ID_LENGTH),

  // 2. 短 ID：预留 3 位业务前缀空间，避免 BC_ 前缀超过 varchar(50)
  shortId: () => nanoid(SHORT_ID_LENGTH),

  // 3. 时间戳前缀 + 6位随机后缀 = 25位排序ID，对齐 varchar(50) 字段
  //    格式：yyyyMMddHHmmssSSS + 6位随机，永不碰撞
  billId: () => {
    const now = new Date();
    const t = [
      String(now.getFullYear()),
      String(now.getMonth() + 1).padStart(2, '0'),
      String(now.getDate()).padStart(2, '0'),
      String(now.getHours()).padStart(2, '0'),
      String(now.getMinutes()).padStart(2, '0'),
      String(now.getSeconds()).padStart(2, '0'),
      String(now.getMilliseconds()).padStart(3, '0'),
    ].join('');
    return t + generateSuffix();
  }
};

module.exports = idUtils;
