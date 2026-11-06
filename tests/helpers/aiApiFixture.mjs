import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';

export const apiRequire = createRequire(new URL('../../../api/package.json', import.meta.url));
export const io = { forbiddenDatabaseCalls: 0, forbiddenProviderCalls: 0 };
let databaseAdapter;
let ledgerAdapter;

const denyDatabase = () => {
  io.forbiddenDatabaseCalls++;
  throw new Error('AI contract tests must not access a real database');
};
const denyProvider = () => {
  io.forbiddenProviderCalls++;
  throw new Error('AI contract tests must not access a real provider');
};
function replaceModule(name, exports) {
  const filename = apiRequire.resolve(name);
  apiRequire.cache[filename] = { id: filename, filename, loaded: true, exports };
}

// Install these before importing any API service, including its default router.
replaceModule('./src/common/config/db', {
  execute: (...args) => databaseAdapter?.execute ? databaseAdapter.execute(...args) : denyDatabase(),
  getConnection: (...args) => databaseAdapter?.getConnection ? databaseAdapter.getConnection(...args) : denyDatabase(),
});
replaceModule('mysql2/promise', { createPool: denyDatabase });
replaceModule('axios', { get: denyProvider, post: denyProvider });
replaceModule('./src/modules/account/model/debit', {
  create: (...args) => ledgerAdapter ? ledgerAdapter(...args) : denyDatabase(),
});
replaceModule('./src/modules/account/model/credit', { create: denyDatabase });

export const { createAiService } = apiRequire('./src/modules/ai/service');
export const { createAiRouter } = apiRequire('./src/modules/ai/api');
export const { createDraftStore } = apiRequire('./src/modules/ai/service/drafts');
export const { AiError } = apiRequire('./src/modules/ai/service/errors');
const express = apiRequire('express');

export function testEnv(extra = {}) {
  return { NODE_ENV: 'test', AI_MODE: 'deepseek', AI_SYNTHETIC_DATA: 'false',
    DEEPSEEK_API_KEY: 'fixture-provider-key', AI_DB_HOST: 'fixture.invalid', AI_DB_NAME: 'fixture',
    AI_DB_USER: 'fixture_reader', AI_DB_PASSWORD: 'fixture-password', ...extra };
}
export function createTestRepository() {
  return {
    async summary(userId, range) {
      assert.ok(userId);
      return { ...range, currency: 'CNY', income: '95.00', expense: '51.20', count: 2,
        categories: [{ direction: 0, category: '餐饮', amount: '51.20', count: 1 }],
        policy: '契约测试查询结果' };
    },
    async transactions(userId, range) {
      assert.ok(userId);
      return { ...range, rows: [], total: 0, hasMore: false };
    },
    async choices(userId) {
      assert.ok(userId);
      return { accounts: [{ id: 'fixture_cash', name: '现金账户', type: 'debit' }],
        categories: [{ id: 'fixture_food', name: '餐饮', type: 'expense' },
          { id: 'fixture_salary', name: '工资', type: 'income' }] };
    },
  };
}
export function createMemoryDraftStore() {
  const records = new Map();
  const key = (userId, id) => `${userId}|${id}`;
  const conflict = () => new AiError('草稿版本已变化，请重新核对', 409, 'AI_DRAFT_CONFLICT');
  return {
    async save(userId, input) {
      const draft = { ...structuredClone(input), id: randomUUID(), version: 1, status: 'pending',
        expiresAt: Date.now() + 15 * 60 * 1000 };
      records.set(key(userId, draft.id), draft);
      return structuredClone(draft);
    },
    async get(userId, id) {
      const draft = records.get(key(userId, id));
      if (!draft || (draft.status !== 'committed' && draft.expiresAt <= Date.now())) throw new AiError('草稿不存在或已过期', 404);
      return structuredClone(draft);
    },
    async update(userId, id, version, fields) {
      const draft = await this.get(userId, id);
      if (['discarded', 'committed'].includes(draft.status) || draft.version !== version) throw conflict();
      const next = { ...draft, ...structuredClone(fields), id, version: version + 1, status: 'pending' };
      records.set(key(userId, id), next);
      return structuredClone(next);
    },
    async act(userId, id, version, action) {
      const draft = await this.get(userId, id);
      if (['discarded', 'committed'].includes(draft.status) || draft.version !== version) throw conflict();
      if (action === 'review' && draft.missingFields.length) throw new AiError('请先补齐草稿');
      draft.status = action === 'review' ? 'reviewed' : 'discarded';
      draft.version++;
      records.set(key(userId, id), draft);
      return structuredClone(draft);
    },
    async commitInTx(_, userId, id, version) {
      const draft = await this.get(userId, id);
      if (draft.status !== 'reviewed' || draft.version !== version) throw conflict();
      records.set(key(userId, id), { ...draft, status: 'committed', version: version + 1 });
    },
    async setRecordId(userId, id, recordId) {
      const draft = await this.get(userId, id);
      records.set(key(userId, id), { ...draft, committedRecordId: recordId });
    },
  };
}
export function createMemoryConversationStore() {
  const records = new Map();
  return {
    async open(userId, id) {
      if (id) {
        const record = records.get(id);
        if (!record || record.userId !== userId) throw new AiError('对话不存在或已过期', 404, 'AI_CONVERSATION_EXPIRED');
        return { ...structuredClone(record), history: record.history.map(({ role, content }) => ({ role, content })) };
      }
      const record = { id: randomUUID(), userId, history: [], latestDraftId: null };
      records.set(record.id, record);
      return structuredClone(record);
    },
    async append(userId, id, { message, answer, draft, ocr }) {
      await this.open(userId, id);
      const record = records.get(id);
      record.history.push({ role: 'user', content: message }, { role: 'assistant', content: answer,
        draftId: draft?.id || null, ...(ocr ? { ocr: structuredClone(ocr) } : {}) });
      record.history = record.history.slice(-10);
      if (draft) record.latestDraftId = draft.id;
      else if (ocr) record.latestDraftId = null;
    },
    async clearUser(userId) {
      for (const [id, record] of records) if (record.userId === userId) records.delete(id);
    },
    async list(userId) {
      return [...records.values()].filter(record => record.userId === userId).map(record => ({
        id: record.id, title: record.history[0]?.content || '新对话', messageCount: record.history.length,
      }));
    },
    async messages(userId, id) { await this.open(userId, id); return structuredClone(records.get(id).history); },
    async remove(userId, id) { await this.open(userId, id); records.delete(id); },
  };
}
export function scriptedChat(steps) {
  let index = 0;
  return async (...args) => {
    assert.ok(index < steps.length, 'Unexpected model call');
    const step = steps[index++];
    return typeof step === 'function' ? step(...args) : structuredClone(step);
  };
}
export function toolCall(name, args) {
  return { role: 'assistant', content: null, tool_calls: [{ id: randomUUID(), type: 'function',
    function: { name, arguments: JSON.stringify(args) } }] };
}
export function draftInput(overrides = {}) {
  return { direction: 0, amount: '32', currency: 'CNY', transDate: '2026-11-05',
    categoryId: 'fixture_food', accountId: 'fixture_cash', payMethod: '现金', remark: '午饭', ...overrides };
}
export function createFixtureService(options = {}) {
  assert.ok(options.chat, 'Every test must inject its model response');
  return createAiService({ env: testEnv(), repository: createTestRepository(),
    draftStore: createMemoryDraftStore(), conversationStore: createMemoryConversationStore(),
    checkConnection: denyProvider, recognizeImage: denyProvider,
    now: () => new Date('2026-11-06T08:00:00Z'), ...options });
}
export async function serve(service, env = testEnv()) {
  const app = express(); app.use(express.json());
  app.use('/api/v1/ai', createAiRouter({ env, service, guard: (req, res, next) => {
    if (!req.headers['x-test-user']) return res.status(401).json({ status: 401 });
    req.userId = req.headers['x-test-user']; next();
  } }));
  const server = await new Promise(resolve => {
    const instance = app.listen(0, '127.0.0.1', () => resolve(instance));
  });
  const base = `http://127.0.0.1:${server.address().port}/api/v1/ai`;
  return {
    base,
    send: (path, method = 'GET', data, user = 'a') => fetch(`${base}${path}`, {
      method, headers: { 'Content-Type': 'application/json', 'x-test-user': user },
      body: data === undefined ? undefined : JSON.stringify({ data }),
    }),
    async close() {
      await new Promise(resolve => server.close(resolve));
      await service.close?.();
    },
  };
}
export async function withFakeDatabase(adapter, work, writeLedger) {
  assert.equal(databaseAdapter, undefined, 'Database fixture scopes cannot overlap');
  databaseAdapter = adapter; ledgerAdapter = writeLedger;
  try { return await work(); }
  finally { databaseAdapter = undefined; ledgerAdapter = undefined; }
}
