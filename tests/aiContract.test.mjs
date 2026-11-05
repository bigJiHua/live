import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { validateAiCapabilities, validateAiResult } from '../src/utils/aiContract.js';

const apiRequire = createRequire(new URL('../../api/package.json', import.meta.url));
const { createAiService } = apiRequire('./src/modules/ai/service');
const { createAiRouter } = apiRequire('./src/modules/ai/api');
const express = apiRequire('express');

test('Web rejects incompatible versions or formal-write capabilities', () => {
  for (const capabilities of [{ contractVersion: 2 }, { contractVersion: 1, formalWriteEnabled: true }]) {
    assert.throws(() => validateAiCapabilities(capabilities));
  }
  assert.throws(() => validateAiResult({ contractVersion: 1, formalWriteEnabled: true }));
});

test('actual API HTTP responses match Web validators for query and draft', async () => {
  const app = express();
  app.use(express.json());
  const env = { AI_MODE: 'mock' };
  app.use('/api/v1/ai', createAiRouter({ env, service: createAiService({ env }),
    guard: (req, _, next) => { req.userId = 'synthetic_contract_user'; next(); } }));
  const server = await new Promise(resolve => { const instance = app.listen(0, '127.0.0.1', () => resolve(instance)); });
  const base = `http://127.0.0.1:${server.address().port}/api/v1/ai`;
  try {
    const capability = validateAiCapabilities((await (await fetch(`${base}/capabilities`)).json()).data);
    assert.equal(capability.mode, 'mock');
    assert.equal(capability.requiresConsent, false);
    for (const [scene, message] of [['query', '本月收支情况'], ['draft', '昨天午饭32元，支付宝付款']]) {
      const response = await fetch(`${base}/chat`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ data: { message, scene, consent: false } }) });
      assert.equal(response.status, 200);
      const result = validateAiResult((await response.json()).data);
      assert.equal(result.source, 'synthetic');
      if (scene === 'draft') {
        assert.equal(result.draft.amount, '32');
        assert.equal(result.draft.formalWriteEnabled, false);
        const draft = (await (await fetch(`${base}/drafts/${result.draft.id}`)).json()).data;
        assert.equal(draft.originalText, message);
      } else {
        assert.equal(result.trace[0].result.expense, '632.00');
        assert.equal(result.draft, null);
      }
    }
  } finally { await new Promise(resolve => server.close(resolve)); }
});
