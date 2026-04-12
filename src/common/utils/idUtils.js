const { nanoid, customAlphabet } = require('nanoid');

/**
 * 奠基级 ID 工具类
 */
const idUtils = {
  // 1. 标准 ID (21位，包含大小写字母和数字)
  // 例子：V1StGXR8_Z5jdHi6B-myT
  userId: () => nanoid(),

  // 2. 短 ID (用于邀请码或不那么敏感的场景，10位)
  shortId: () => nanoid(10),

  // 3. 纯数字/大写 ID (用于财务流水号，更规整)
  // 例子：20260328ABC123
  billId: () => {
    const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    const generate = customAlphabet(alphabet, 12);
    return generate();
  }
};

module.exports = idUtils;