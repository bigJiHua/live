# 测试目录 (tests)

本目录包含项目的单元测试和集成测试文件。

## 测试框架

项目使用 **Jest** 作为测试框架。

## 目录结构

```
tests/
├─ setup.js           # 测试全局设置
├─ unit/             # 单元测试
│  └─ example.test.js
└─ integration/      # 集成测试
    └─ example.test.js
```

## 运行测试

```bash
# 运行所有测试
npm test

# 运行单元测试
npm run test:unit

# 运行集成测试
npm run test:integration

# 生成测试覆盖率报告
npm run test:coverage

# 监听模式（文件变更时自动运行测试）
npm run test:watch
```

## 测试示例

```javascript
const sum = require('./sum');

describe('数学运算', () => {
  test('加法运算', () => {
    expect(sum(1, 2)).toBe(3);
  });
});
```

## 编写测试

1. 测试文件以 `.test.js` 或 `.spec.js` 结尾
2. 使用 `describe()` 组织测试用例
3. 使用 `test()` 或 `it()` 定义单个测试
4. 使用 `expect()` 断言结果

## 注意事项

- 测试环境使用独立的测试数据库
- 避免在测试中修改生产数据
- 测试应该是独立的、可重复的
