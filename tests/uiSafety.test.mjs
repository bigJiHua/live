import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { getAiProviderLabel, validateAiCapabilities } from '../src/utils/aiContract.js';

const capability = { contractVersion: 1, formalWriteEnabled: false, mode: 'deepseek', enabled: true,
  requiresConsent: true, chatEnabled: true, draftEditingEnabled: true };

test('界面供应商文案取 capability.provider，缺省/未知回退 DeepSeek', () => {
  assert.equal(getAiProviderLabel({ ...capability, provider: 'api.deepseek.com' }), 'DeepSeek');
  assert.equal(getAiProviderLabel({ ...capability, provider: 'provider.test' }), 'provider.test');
  assert.equal(getAiProviderLabel({ ...capability, provider: 'unknown' }), 'DeepSeek');
  assert.equal(getAiProviderLabel({ ...capability }), 'DeepSeek');
  assert.equal(getAiProviderLabel({ ...capability, enabled: false }), '未启用');
  assert.equal(getAiProviderLabel({ ...capability, mode: 'mock' }), '模拟测试');
  assert.equal(getAiProviderLabel(null), '未启用');
  assert.equal(validateAiCapabilities({ ...capability, provider: 'provider.test' }).provider, 'provider.test');
});

test('登录页非演示模式默认留空，不预填任何凭据', async () => {
  const source = await readFile(new URL('../src/views/Auth/Login.vue', import.meta.url), 'utf8');
  // 只校验结构：演示分支取环境变量，非演示分支必须是空字符串（断言里不写任何真实凭据字面量）。
  assert.match(source, /VITE_LOGIN_USERNAME\s*\|\|\s*""\s*:\s*""/);
  assert.match(source, /VITE_LOGIN_PASSWORD\s*\|\|\s*""\s*:\s*""/);
});
