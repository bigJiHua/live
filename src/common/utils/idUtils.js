const { nanoid, customAlphabet } = require('nanoid');

const USER_ID_LENGTH = 50;
const SHORT_ID_LENGTH = 47;
const BILL_ID_LENGTH = 50;
const BILL_ID_ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const generateBillId = customAlphabet(BILL_ID_ALPHABET, BILL_ID_LENGTH);

/**
 * 奠基级 ID 工具类
 */
const idUtils = {
  // 1. 标准用户 ID：对齐当前 varchar(50) 主键/用户字段
  userId: () => nanoid(USER_ID_LENGTH),

  // 2. 短 ID：预留 3 位业务前缀空间，避免 BC_ 前缀超过 varchar(50)
  shortId: () => nanoid(SHORT_ID_LENGTH),

  // 3. 纯数字/大写 ID：对齐当前 varchar(50) 业务主键/外键字段
  billId: () => generateBillId()
};

module.exports = idUtils;
