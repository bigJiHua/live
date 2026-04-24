// 工具函数测试示例
const { encrypt, decrypt } = require('../../src/common/utils/crypto');

describe('工具函数测试', () => {
  describe('加密/解密', () => {
    test('encrypt 应该返回加密后的字符串', () => {
      const result = encrypt('test-string');
      expect(result).toBeDefined();
      expect(typeof result).toBe('string');
    });

    test('decrypt 应该能解密加密后的字符串', () => {
      const original = 'hello-world';
      const encrypted = encrypt(original);
      const decrypted = decrypt(encrypted);
      expect(decrypted).toBe(original);
    });

    test('相同的输入应该产生不同的加密结果（因为有随机 salt）', () => {
      const input = 'test';
      const result1 = encrypt(input);
      const result2 = encrypt(input);
      expect(result1).not.toBe(result2);
    });
  });
});
