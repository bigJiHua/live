const idUtils = require('../../src/common/utils/idUtils');

describe('ID 生成工具测试', () => {
  test('userId 生成 50 位字符串', () => {
    const id = idUtils.userId();

    expect(typeof id).toBe('string');
    expect(id.length).toBe(50);
  });

  test('shortId 为业务前缀预留 3 位空间', () => {
    const id = idUtils.shortId();

    expect(typeof id).toBe('string');
    expect(id.length).toBe(47);
    expect(`BC_${id}`.length).toBe(50);
  });

  test('billId 生成 50 位大写字母数字 ID', () => {
    const id = idUtils.billId();

    expect(typeof id).toBe('string');
    expect(id.length).toBe(50);
    expect(id).toMatch(/^[0-9A-Z]+$/);
  });

  test('连续生成的业务 ID 不应重复', () => {
    const id1 = idUtils.billId();
    const id2 = idUtils.billId();

    expect(id1).not.toBe(id2);
  });
});
