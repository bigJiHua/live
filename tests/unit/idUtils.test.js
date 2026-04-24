// ID 生成工具测试示例
const { generateId, generateUniqueId } = require('../../src/common/utils/idUtils');

describe('ID 生成工具测试', () => {
  describe('generateId', () => {
    test('generateId 应该返回一个字符串', () => {
      const id = generateId();
      expect(typeof id).toBe('string');
    });

    test('generateId 生成的 ID 长度应该正确', () => {
      const id = generateId();
      expect(id.length).toBeGreaterThan(0);
    });

    test('generateId 每次调用应该生成不同的 ID', () => {
      const id1 = generateId();
      const id2 = generateId();
      expect(id1).not.toBe(id2);
    });
  });

  describe('generateUniqueId', () => {
    test('generateUniqueId 应该返回一个字符串', () => {
      const id = generateUniqueId();
      expect(typeof id).toBe('string');
    });

    test('generateUniqueId 生成的 ID 应该包含前缀', () => {
      const id = generateUniqueId('TEST');
      expect(id.startsWith('TEST')).toBe(true);
    });
  });
});
