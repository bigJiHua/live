import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { validateAiCapabilities, validateAiResult, validateAiDraft, validateAiSettings } from '../src/utils/aiContract.js';

const apiRequire = createRequire(new URL('../../api/package.json', import.meta.url));
const { createAiService } = apiRequire('./src/modules/ai/service');
const { createAiRouter } = apiRequire('./src/modules/ai/api');
const express = apiRequire('express');

test('settings HTTP contract keeps Key server-side and applies only to current user', async () => {
  const env = { AI_MODE: 'mock', NODE_ENV: 'development', AI_SYNTHETIC_DATA: 'true', AI_ALLOW_SESSION_SETTINGS: 'true' };
  const app = express(); app.use(express.json());
  let connectionChecks = 0;
  const secret = 'synthetic-http-key';
  app.use('/ai', createAiRouter({ env, service: createAiService({ env,
    checkConnection: async settings => { assert.equal(settings.apiKey, secret); connectionChecks++; },
    chat: async () => ({ role: 'assistant', content: '真实调用流程的测试替身回答' }),
  }), guard: (req, res, next) => { if (!req.headers['x-test-user']) return res.sendStatus(401); req.userId = req.headers['x-test-user']; next(); } }));
  const server = await new Promise(resolve => { const instance = app.listen(0, '127.0.0.1', () => resolve(instance)); });
  const base = `http://127.0.0.1:${server.address().port}/ai`;
  const send = (path, method, data, user = 'a') => fetch(`${base}${path}`, { method, headers: { 'Content-Type': 'application/json', 'x-test-user': user }, body: data ? JSON.stringify({ data }) : undefined });
  try {
    assert.equal((await fetch(`${base}/settings`)).status, 401);
    const initial = validateAiSettings((await (await send('/settings','GET')).json()).data);
    assert.equal(initial.configured, false);
    assert.equal((await send('/settings','PUT', { apiUrl: 'https://evil.test', apiKey: secret, model: 'deepseek-flash' })).status, 400);
    assert.equal(connectionChecks, 0);
    const response = await send('/settings','PUT', { apiUrl: 'https://api.deepseek.com/v1', apiKey: secret, model: 'deepseek-flash' });
    const body = await response.text(); assert.ok(!body.includes(secret));
    const profile = validateAiSettings(JSON.parse(body).data); assert.equal(profile.configured, true);
    assert.equal(validateAiSettings((await (await send('/settings','GET',null,'b')).json()).data).configured, false);
    const capabilities = validateAiCapabilities((await (await send('/capabilities','GET')).json()).data);
    assert.equal(capabilities.mode, 'deepseek'); assert.equal(capabilities.dataSource, 'synthetic');
    const result = validateAiResult((await (await send('/chat','POST',{ message: '你好', scene: 'chat', consent: true, connectionId: profile.connectionId })).json()).data);
    assert.equal(result.mode,'deepseek'); assert.equal(result.source,'synthetic');
    assert.equal((await send('/chat','POST',{ message: '你好', scene:'chat', consent:true, connectionId:profile.connectionId },'b')).status,409);
    assert.equal(validateAiSettings((await (await send('/settings','DELETE',{})).json()).data).configured,false);
    assert.equal((await send('/chat','POST',{ message: '你好', scene:'chat', consent:true, connectionId:profile.connectionId })).status,409);
    assert.throws(() => validateAiSettings({ ...profile, apiKey: secret }));
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('Web rejects incompatible versions or formal-write capabilities', () => {
  for (const capabilities of [{ contractVersion: 2 }, { contractVersion: 1, formalWriteEnabled: true }]) {
    assert.throws(() => validateAiCapabilities(capabilities));
  }
  assert.throws(() => validateAiResult({ contractVersion: 1, formalWriteEnabled: true }));
});

test('chat HTTP contract supports inline edit, review, follow-up and cancellation', async () => {
  const env = { AI_MODE: 'mock' };
  const app = express(); app.use(express.json());
  app.use('/ai', createAiRouter({ env, service: createAiService({ env }), guard: (req, _, next) => { req.userId = req.headers['x-test-user'] || 'chat_user'; next(); } }));
  const server = await new Promise(resolve => { const instance = app.listen(0, '127.0.0.1', () => resolve(instance)); });
  const base = `http://127.0.0.1:${server.address().port}/ai`;
  const send = (path, method, data, user = 'chat_user') => fetch(`${base}${path}`, { method, headers: { 'Content-Type': 'application/json', 'x-test-user': user }, body: JSON.stringify({ data }) });
  try {
    const result = validateAiResult((await (await send('/chat', 'POST', { message: '昨天午饭32元，支付宝付款', scene: 'chat', consent: false })).json()).data);
    let draft = result.draft;
    const fields = Object.fromEntries(['direction', 'amount', 'currency', 'transDate', 'categoryId', 'accountId', 'payMethod', 'remark'].map(key => [key, draft[key]]));
    fields.amount = '35'; fields.accountId = 'mock_cash'; fields.payMethod = '现金';
    assert.equal((await send(`/drafts/${draft.id}`, 'PUT', { version: 1, fields }, 'other_user')).status, 404);
    draft = validateAiDraft((await (await send(`/drafts/${draft.id}`, 'PUT', { version: 1, fields })).json()).data);
    assert.equal(draft.amount, '35'); assert.equal(draft.version, 2);
    assert.equal((await send(`/drafts/${draft.id}`, 'PUT', { version: 1, fields })).status, 409);
    draft = validateAiDraft((await (await send(`/drafts/${draft.id}/action`, 'POST', { version: 2, action: 'review' })).json()).data);
    assert.equal(draft.status, 'reviewed'); assert.equal(draft.formalWriteEnabled, false);
    const follow = validateAiResult((await (await send('/chat', 'POST', { message: '刚才这笔草稿多少钱？', scene: 'chat', consent: false, conversationId: result.conversationId })).json()).data);
    assert.match(follow.answer, /35元/); assert.equal(follow.conversationId, result.conversationId);
    assert.equal((await send('/chat', 'POST', { message: '你好', scene: 'chat', consent: false, conversationId: result.conversationId }, 'other_user')).status, 404);
    draft = validateAiDraft((await (await send(`/drafts/${draft.id}/action`, 'POST', { version: 3, action: 'discard' })).json()).data);
    assert.equal(draft.status, 'discarded');
    assert.equal((await send(`/drafts/${draft.id}`, 'PUT', { version: 4, fields })).status, 409);
    assert.equal((await send(`/drafts/${draft.id}/confirm`, 'POST', {})).status, 404);
    for (const invalid of [{ ...draft, formalWriteEnabled: true }, { ...draft, status: 'committed' }, { ...draft, version: 0 }]) assert.throws(() => validateAiDraft(invalid));
  } finally { await new Promise(resolve => server.close(resolve)); }
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
