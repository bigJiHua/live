import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { validateAiCapabilities, validateAiResult, validateAiDraft, validateAiSettings, validateAiBalance,
  validateAiConversations, validateAiMessages, validateAiModels, validateAiOcr } from '../src/utils/aiContract.js';
import { createFixtureService, createDraftStore, testEnv, serve, scriptedChat, toolCall, draftInput,
  withFakeDatabase, io, apiRequire } from './helpers/aiApiFixture.mjs';
import { restoreAiHistory } from '../src/utils/aiHistory.js';
import { getAiDraftDisplay } from '../src/utils/aiDraftState.js';
import { AI_IMAGE_LIMITS, prepareAiImage, validateAiImageDataUrl, validateAiImageFile } from '../src/utils/aiImage.js';
import { applyAiReply, collapseAiDraftCards } from '../src/utils/aiMessages.js';

after(() => {
  assert.equal(io.forbiddenDatabaseCalls, 0, 'No test may fall back to a real database');
  assert.equal(io.forbiddenProviderCalls, 0, 'No test may fall back to a real provider');
});

test('settings HTTP contract checks the normalized provider URL and keeps the Key server-side', async () => {
  const env = testEnv({ AI_ALLOW_SESSION_SETTINGS: 'true' });
  let connectionChecks = 0;
  const secret = 'fixture-http-key';
  const app = await serve(createFixtureService({ env,
    checkConnection: async settings => {
      assert.equal(settings.apiKey, secret);
      assert.equal(settings.baseUrl, 'https://api.deepseek.com/v1');
      connectionChecks++;
    },
    chat: scriptedChat([{ role: 'assistant', content: '你好，可以查询账本和整理草稿。' }]),
  }), env);
  try {
    assert.equal((await fetch(`${app.base}/settings`)).status, 401);
    assert.equal(validateAiSettings((await (await app.send('/settings')).json()).data).configured, false);
    assert.equal((await app.send('/settings', 'PUT', { apiUrl: 'http://evil.test', apiKey: secret, model: 'deepseek-flash' })).status, 400);
    assert.equal(connectionChecks, 0);
    const response = await app.send('/settings', 'PUT', { apiUrl: 'https://api.deepseek.com/v1/', apiKey: secret, model: 'deepseek-flash' });
    assert.equal(response.status, 200);
    const body = await response.text(); assert.ok(!body.includes(secret));
    const profile = validateAiSettings(JSON.parse(body).data);
    assert.equal(profile.configured, true); assert.equal(connectionChecks, 1);
    assert.equal(validateAiSettings((await (await app.send('/settings', 'GET', undefined, 'b')).json()).data).configured, false);
    const capabilities = validateAiCapabilities((await (await app.send('/capabilities')).json()).data);
    assert.equal(capabilities.mode, 'deepseek'); assert.equal(capabilities.dataSource, 'user-readonly');
    assert.equal(capabilities.requiresConsent, true);
    assert.equal((await app.send('/chat', 'POST', { message: '你好', scene: 'chat', consent: false })).status, 403);
    const result = validateAiResult((await (await app.send('/chat', 'POST', {
      message: '你好', scene: 'chat', consent: true, connectionId: profile.connectionId,
    })).json()).data);
    assert.equal(result.mode, 'deepseek'); assert.equal(result.source, 'user-readonly');
    assert.equal((await app.send('/chat', 'POST', { message: '你好', scene: 'chat', consent: true, connectionId: profile.connectionId }, 'b')).status, 409);
    assert.equal(validateAiSettings((await (await app.send('/settings', 'DELETE', {})).json()).data).configured, false);
    assert.equal((await app.send('/chat', 'POST', { message: '你好', scene: 'chat', consent: true, connectionId: profile.connectionId })).status, 409);
    assert.throws(() => validateAiSettings({ ...profile, apiKey: secret }));
  } finally { await app.close(); }
});

test('Web rejects incompatible versions or model formal-write capabilities', () => {
  for (const capabilities of [{ contractVersion: 2 }, { contractVersion: 1, formalWriteEnabled: true }]) {
    assert.throws(() => validateAiCapabilities(capabilities));
  }
  assert.throws(() => validateAiResult({ contractVersion: 1, formalWriteEnabled: true }));
});

test('balance and model contracts keep the Key server-side and reject malformed data', () => {
  const balance = { isAvailable: true, currency: 'CNY', totalBalance: '110.00', grantedBalance: '10.00', toppedUpBalance: '100.00' };
  assert.equal(validateAiBalance(balance).totalBalance, '110.00');
  for (const bad of [{ ...balance, apiKey: 'leak' }, { ...balance, isAvailable: 'yes' },
    { ...balance, currency: 'EUR' }, { ...balance, totalBalance: '110.001' }]) assert.throws(() => validateAiBalance(bad));
  assert.deepEqual(validateAiModels({ models: ['deepseek-flash'] }).models, ['deepseek-flash']);
  assert.throws(() => validateAiModels({ models: ['deepseek-flash'], apiKey: 'leak' }));
});

test('query and draft HTTP responses use the authenticated repository and DeepSeek contract', async () => {
  const input = draftInput();
  const service = createFixtureService({ chat: scriptedChat([
    toolCall('query_finance_summary', { startDate: '2026-11-01', endDate: '2026-11-06' }),
    { role: 'assistant', content: '范围内支出51.20元。' },
    toolCall('list_record_choices', {}), toolCall('propose_transaction', input),
    { role: 'assistant', content: '请核对聊天里的草稿。' },
  ]) });
  const app = await serve(service);
  try {
    const capability = validateAiCapabilities((await (await app.send('/capabilities')).json()).data);
    assert.equal(capability.mode, 'deepseek'); assert.equal(capability.dataSource, 'user-readonly');
    assert.equal(capability.requiresConsent, true);
    for (const [scene, message] of [['query', '本月收支情况'], ['draft', '昨天午饭32元，现金账户付款']]) {
      const response = await app.send('/chat', 'POST', { message, scene, consent: true });
      assert.equal(response.status, 200);
      const result = validateAiResult((await response.json()).data);
      assert.equal(result.source, 'user-readonly'); assert.equal(result.mode, 'deepseek');
      if (scene === 'draft') {
        assert.equal(result.draft.amount, '32');
        assert.equal(result.draft.status, 'pending'); assert.equal(result.draft.needsReview, true);
        const draft = validateAiDraft((await (await app.send(`/drafts/${result.draft.id}`)).json()).data);
        assert.equal(draft.originalText, message); assert.equal(draft.accountId, 'fixture_cash');
      } else {
        assert.equal(result.trace[0].result.expense, '51.20'); assert.equal(result.draft, null);
      }
    }
    assert.equal((await app.send('/chat', 'POST', { message: '查询', scene: 'query', consent: true, userId: 'other' })).status, 400);
  } finally { await app.close(); }
});

test('reviewed drafts return to pending after edits and require another review before commit', async () => {
  const input = draftInput();
  const app = await serve(createFixtureService({ chat: scriptedChat([
    toolCall('propose_transaction', input), { role: 'assistant', content: '请核对草稿。' },
    messages => {
      const snapshot = messages.find(message => message.content?.startsWith('[应用提供的最近草稿状态'));
      assert.ok(snapshot?.content.includes('"amount":"35"'));
      return { role: 'assistant', content: '这份草稿是35元，尚未入账。' };
    },
  ]) }));
  try {
    const result = validateAiResult((await (await app.send('/chat', 'POST', { message: '午饭32元，现金账户付款', scene: 'chat', consent: true })).json()).data);
    let draft = result.draft;
    assert.equal((await app.send(`/drafts/${draft.id}/commit`, 'POST', { version: draft.version })).status, 409);
    assert.equal((await app.send(`/drafts/${draft.id}`, 'PUT', { version: 1, fields: input }, 'other')).status, 404);
    draft = validateAiDraft((await (await app.send(`/drafts/${draft.id}/action`, 'POST', { version: 1, action: 'review' })).json()).data);
    assert.equal(draft.status, 'reviewed');
    draft = validateAiDraft((await (await app.send(`/drafts/${draft.id}`, 'PUT', { version: 2, fields: { ...input, amount: '35' } })).json()).data);
    assert.equal(draft.status, 'pending'); assert.equal(draft.version, 3);
    assert.equal((await app.send(`/drafts/${draft.id}/commit`, 'POST', { version: 3 })).status, 409);
    assert.equal((await app.send(`/drafts/${draft.id}`, 'PUT', { version: 2, fields: input })).status, 409);
    draft = validateAiDraft((await (await app.send(`/drafts/${draft.id}/action`, 'POST', { version: 3, action: 'review' })).json()).data);
    assert.equal(draft.status, 'reviewed'); assert.equal(draft.needsReview, true);
    const follow = validateAiResult((await (await app.send('/chat', 'POST', { message: '草稿多少钱？', scene: 'chat', consent: true, conversationId: result.conversationId })).json()).data);
    assert.match(follow.answer, /35元/);
    assert.equal((await app.send('/chat', 'POST', { message: '你好', scene: 'chat', consent: true, conversationId: result.conversationId }, 'other')).status, 404);
    assert.ok(validateAiConversations((await (await app.send('/conversations')).json()).data).length);
    assert.ok(validateAiMessages((await (await app.send(`/conversations/${result.conversationId}/messages`)).json()).data).length);
    draft = validateAiDraft((await (await app.send(`/drafts/${draft.id}/action`, 'POST', { version: 4, action: 'discard' })).json()).data);
    assert.equal(draft.status, 'discarded');
    assert.equal((await app.send(`/drafts/${draft.id}/commit`, 'POST', { version: 5 })).status, 409);
    assert.equal((await app.send(`/drafts/${draft.id}/confirm`, 'POST', {})).status, 404);
  } finally { await app.close(); }
});

test('the actual SQL draft store resets reviewed status when changing financial fields', async () => {
  const row = { id: randomUUID(), user_id: 'a', direction: 0, amount: '32', currency: 'CNY',
    trans_date: '2026-11-05', category_id: 'fixture_food', account_id: 'fixture_cash', pay_method: '现金',
    remark: '午饭', status: 'reviewed', version: 2, missing_fields: [], expires_at: Date.now() + 60000 };
  const adapter = { async execute(sql, params) {
    if (sql.trim().startsWith('SELECT')) return [[structuredClone(row)]];
    assert.match(sql, /UPDATE bus_ai_draft SET/);
    const setClause = sql.split(/\bSET\b/)[1].split(/\bWHERE\b/)[0];
    const columns = ['direction', 'amount', 'currency', 'trans_date', 'category_id', 'account_id', 'pay_method', 'remark'];
    columns.forEach((column, index) => { row[column] = params[index]; });
    row.missing_fields = JSON.parse(params[8]); row.version++;
    const statusAssignment = setClause.match(/status\s*=\s*'([^']+)'/);
    if (statusAssignment) row.status = statusAssignment[1];
    return [{ affectedRows: 1 }];
  } };
  await withFakeDatabase(adapter, async () => {
    const draft = await createDraftStore().update('a', row.id, 2, { ...draftInput(), amount: '35', missingFields: [] });
    assert.equal(draft.amount, '35'); assert.equal(draft.status, 'pending');
    assert.equal(draft.version, 3);
  });
});

test('actual commit HTTP response is accepted only with a completed state and record receipt', async () => {
  const app = await serve(createFixtureService({ chat: scriptedChat([
    toolCall('propose_transaction', draftInput()), { role: 'assistant', content: '请核对草稿。' },
  ]) }));
  const transactions = { begin: 0, commit: 0, rollback: 0, release: 0, records: [] };
  const conn = { async beginTransaction() { transactions.begin++; }, async commit() { transactions.commit++; },
    async rollback() { transactions.rollback++; }, release() { transactions.release++; },
    async execute(sql, params) {
      assert.match(sql, /FOR UPDATE/);
      if (sql.includes('FROM bus_category')) {
        assert.deepEqual(params, ['fixture_food', 'a']);
        return [[{ id: 'fixture_food', name: '餐饮', type: 'expense' }]];
      }
      assert.match(sql, /FROM card_base/);
      assert.deepEqual(params, ['fixture_cash', 'a']);
      return [[{ id: 'fixture_cash', name: '现金账户', type: 'debit' }]];
    },
  };
  try {
    const result = validateAiResult((await (await app.send('/chat', 'POST', { message: '午饭32元，现金账户付款', scene: 'chat', consent: true })).json()).data);
    const reviewed = validateAiDraft((await (await app.send(`/drafts/${result.draft.id}/action`, 'POST', { version: 1, action: 'review' })).json()).data);
    await withFakeDatabase({ async getConnection() { return conn; } }, async () => {
      assert.equal((await app.send(`/drafts/${reviewed.id}/commit`, 'POST', { version: reviewed.version, amount: '1' })).status, 400);
      const response = await app.send(`/drafts/${reviewed.id}/commit`, 'POST', { version: reviewed.version });
      assert.equal(response.status, 200);
      const committed = validateAiDraft((await response.json()).data);
      assert.equal(committed.status, 'committed'); assert.equal(committed.needsReview, false);
      assert.equal(committed.committedRecordId, 'FIXTURE_RECORD_1'); assert.equal(committed.formalWriteEnabled, false);
      const restored = validateAiDraft((await (await app.send(`/drafts/${reviewed.id}`)).json()).data);
      assert.equal(restored.status, 'committed'); assert.equal(restored.needsReview, false);
      assert.equal(restored.committedRecordId, committed.committedRecordId);
      const history = (await (await app.send(`/conversations/${result.conversationId}/messages`)).json()).data;
      const cards = await restoreAiHistory(history, async id =>
        (await (await app.send(`/drafts/${id}`)).json()).data, () => committed.expiresAt + 1);
      assert.equal(cards[1].draft.committedRecordId, committed.committedRecordId);
      assert.equal(getAiDraftDisplay(cards[1].draft, committed.expiresAt + 1).statusLabel, '已入账');
      assert.equal((await app.send(`/drafts/${reviewed.id}/commit`, 'POST', { version: committed.version })).status, 409);
      for (const invalid of [{ ...committed, committedRecordId: null }, { ...committed, committedRecordId: '' },
        { ...committed, committedRecordId: ' ' }, { ...committed, needsReview: true }, { ...committed, formalWriteEnabled: true }]) {
        assert.throws(() => validateAiDraft(invalid));
      }
      for (const status of ['pending', 'reviewed', 'discarded']) {
        assert.throws(() => validateAiDraft({ ...reviewed, status, needsReview: false }));
      }
    }, async (payload, transaction) => {
      assert.equal(transaction, conn); assert.equal(payload.amount, 32); assert.equal(payload.userId, 'a');
      transactions.records.push(payload);
      return { id: 'FIXTURE_RECORD_1' };
    });
    assert.deepEqual({ ...transactions, records: transactions.records.length }, { begin: 1, commit: 1, rollback: 0, release: 1, records: 1 });
  } finally { await app.close(); }
});

test('history reconstructs the latest editable card from the API instead of implying formal entry', async () => {
  const app = await serve(createFixtureService({ chat: scriptedChat([
    toolCall('propose_transaction', draftInput({ accountId: null, payMethod: null })),
    { role: 'assistant', content: '请选择账户和支付方式，再核对草稿。' },
  ]) }));
  try {
    const result = validateAiResult((await (await app.send('/chat', 'POST', {
      message: '午饭32元', scene: 'chat', consent: true,
    })).json()).data);
    const fields = draftInput({ amount: '35', accountId: null, payMethod: null });
    await app.send(`/drafts/${result.draft.id}`, 'PUT', { version: 1, fields });
    const history = (await (await app.send(`/conversations/${result.conversationId}/messages`)).json()).data;
    const restored = await restoreAiHistory(history, async id => (await (await app.send(`/drafts/${id}`)).json()).data);
    assert.deepEqual(restored.map(item => item.role), ['user', 'assistant']);
    assert.equal(restored[1].draft.id, result.draft.id); assert.equal(restored[1].draft.version, 2);
    assert.equal(restored[1].draft.amount, '35'); assert.equal(restored[1].draft.status, 'pending');
    assert.deepEqual(restored[1].draft.missingFields, ['账户', '支付方式']);
    assert.ok(!restored[1].text.includes('流水页查看结果'));
  } finally { await app.close(); }
});

test('history distinguishes missing or expired proposals from temporary read failures', async () => {
  const id = randomUUID();
  const entries = [{ role: 'assistant', content: '请核对草稿。', draftId: id },
    { role: 'assistant', content: '这份草稿。', draftId: id }];
  let calls = 0;
  const missing = await restoreAiHistory(entries, async () => { calls++; throw { status: 404 }; });
  assert.equal(calls, 1);
  assert.equal(missing.length, 1);
  assert.ok(missing.every(item => !item.draft && item.draftNotice.includes('已过期或不存在') && !item.text));
  const failed = await restoreAiHistory(entries, async () => { throw { status: 503 }; });
  assert.ok(failed.every(item => !item.draft && item.draftNotice.includes('读取失败') && !item.draftNotice.includes('已过期') && !item.text));
  const draft = { ...draftInput(), id, version: 1, expiresAt: 1000, status: 'pending',
    needsReview: true, formalWriteEnabled: false, missingFields: [], choices: { accounts: [], categories: [] } };
  const expired = await restoreAiHistory(entries, async () => draft, () => 1001);
  assert.ok(expired.every(item => !item.draft && item.draftNotice.includes('已过期或不存在') && !item.text));
  await assert.rejects(restoreAiHistory([{ ...entries[0], draftId: 'not-an-id' }], async () => {
    throw new Error('Invalid IDs must not be requested');
  }), /历史消息不符合协议/);
});

test('committed and discarded cards retain their final status after the proposal expiry', () => {
  assert.deepEqual(getAiDraftDisplay({ status: 'committed', expiresAt: 1000 }, 2000),
    { expired: false, statusLabel: '已入账', validityLabel: '回执已保存' });
  assert.equal(getAiDraftDisplay({ status: 'discarded', expiresAt: 1000 }, 2000).statusLabel, '已取消');
  for (const status of ['pending', 'reviewed']) {
    assert.equal(getAiDraftDisplay({ status, expiresAt: 1000 }, 2000).expired, true);
  }
});

test('historical card reads are bounded to the API read pool capacity', async () => {
  const entries = Array.from({ length: 5 }, () => ({ role: 'assistant', content: '草稿', draftId: randomUUID() }));
  let active = 0; let maximum = 0;
  await restoreAiHistory(entries, async id => {
    active++; maximum = Math.max(maximum, active);
    await Promise.resolve(); active--;
    return { ...draftInput(), id, version: 1, expiresAt: Date.now() + 60000, status: 'pending',
      needsReview: true, formalWriteEnabled: false, missingFields: [], choices: { accounts: [], categories: [] } };
  });
  assert.equal(maximum, 2);
});

function ocrItem(overrides = {}) {
  return { direction: 0, amount: '32.00', currency: 'CNY', transDate: '2026-11-05', merchant: '餐厅',
    payMethod: '微信', accountHint: '尾号1234', categoryHint: '餐饮', remark: '午饭', confidence: 'high',
    transactionStatus: 'completed', transactionKind: 'ordinary',
    evidence: { dateText: '2026年11月5日 12:00', amountText: '￥32.00', statusText: '支付成功', kindText: '消费' }, ...overrides };
}
function ocrResult(overrides = {}) {
  return { documentType: 'payment', items: [ocrItem()], warnings: [], truncated: false, ...overrides };
}
async function tinyPng() {
  const bytes = await apiRequire('sharp')({ create: { width: 2, height: 2, channels: 3, background: '#fff' } }).png().toBuffer();
  return { bytes, dataUrl: 'data:image/png;base64,' + bytes.toString('base64') };
}

test('OCR contracts accept nullable uncertainty, special transactions and bounded statements', () => {
  assert.equal(validateAiOcr(ocrResult()).items[0].amount, '32.00');
  const unknown = ocrResult({ documentType: 'unknown', items: [], warnings: ['无法识别'] });
  assert.deepEqual(validateAiOcr(unknown), unknown);
  const nullable = ocrItem(Object.fromEntries(['direction', 'amount', 'currency', 'transDate', 'merchant',
    'payMethod', 'accountHint', 'categoryHint', 'remark'].map(field => [field, null])));
  nullable.evidence = { dateText: null, amountText: null, statusText: null, kindText: null };
  assert.equal(validateAiOcr(ocrResult({ items: [nullable] })).items[0].amount, null);
  const statement = ocrResult({ documentType: 'statement', truncated: true,
    items: Array.from({ length: 20 }, () => ocrItem({ transactionStatus: 'pending', transactionKind: 'repayment', confidence: 'low' })) });
  assert.equal(validateAiOcr(statement).items.length, 20);
  const result = { contractVersion: 1, mode: 'deepseek', formalWriteEnabled: false, trace: [],
    conversationId: randomUUID(), answer: '请核对原图', draft: null, ocr: statement };
  assert.equal(validateAiResult(result).draft, null);
  assert.equal(validateAiMessages([{ role: 'assistant', content: result.answer, ocr: statement }])[0].ocr, statement);
});

test('OCR contracts reject malformed financial fields, evidence and unbounded output', () => {
  const badItems = [
    { direction: '0' }, { amount: 32 }, { amount: '0' }, { amount: '-32' }, { amount: '1e5' },
    { amount: '1,000' }, { amount: '01' }, { amount: '1.00001' }, { amount: '10000000000' },
    { transDate: '2026-02-30' }, { transDate: '2026-11-31' }, { transDate: '11-05' }, { transDate: 20261105 },
    { currency: 'cny' }, { payMethod: '未知银行' }, { confidence: 'certain' },
    { transactionStatus: 'success' }, { transactionKind: 'loan' }, { merchant: '字'.repeat(101) },
    { accountHint: '字'.repeat(81) }, { remark: '字'.repeat(256) }, { evidence: null },
    { evidence: { dateText: null, amountText: null, statusText: null } },
    { evidence: { dateText: '字'.repeat(81), amountText: null, statusText: null, kindText: null } },
  ];
  for (const item of badItems) assert.throws(() => validateAiOcr(ocrResult({ items: [ocrItem(item)] })), /不符合协议/);
  assert.equal(validateAiOcr(ocrResult({ items: [ocrItem({ transDate: '2024-02-29', amount: '0.0001' })] })).items[0].amount, '0.0001');
  for (const ocr of [ocrResult({ items: Array.from({ length: 21 }, () => ocrItem()) }),
    ocrResult({ warnings: Array(11).fill('提示') }), ocrResult({ warnings: ['字'.repeat(201)] }),
    ocrResult({ truncated: 'true' }), ocrResult({ documentType: 'invoice' })]) {
    assert.throws(() => validateAiOcr(ocr));
    assert.throws(() => validateAiMessages([{ role: 'assistant', content: '结果', ocr }]));
  }
  const capability = { contractVersion: 1, formalWriteEnabled: false, mode: 'deepseek', enabled: true,
    requiresConsent: true, chatEnabled: true, draftEditingEnabled: true, ocrEnabled: true, ocrModel: 'deepseek-flash' };
  assert.equal(validateAiCapabilities(capability).ocrModel, 'deepseek-flash');
  for (const invalid of [{ ...capability, ocrEnabled: 'true' }, { ...capability, ocrModel: '' }]) assert.throws(() => validateAiCapabilities(invalid));
});

test('expired or missing historical drafts retain the OCR evidence for comparison', async () => {
  const id = randomUUID();
  const ocr = ocrResult();
  const entry = { role: 'assistant', content: '请选择账户并核对', draftId: id, ocr };
  const missing = await restoreAiHistory([entry], async () => { throw { status: 404 }; });
  assert.deepEqual(missing[0].ocr, ocr); assert.equal(missing[0].draft, undefined);
  const draft = { ...draftInput(), id, version: 1, expiresAt: 1000, status: 'pending', needsReview: true,
    formalWriteEnabled: false, missingFields: [], choices: { accounts: [], categories: [] } };
  const expired = await restoreAiHistory([entry], async () => draft, () => 1001);
  assert.deepEqual(expired[0].ocr, ocr); assert.match(expired[0].draftNotice, /已过期或不存在/);
  const statement = ocrResult({ documentType: 'statement' });
  const result = await restoreAiHistory([{ role: 'assistant', content: '账单识别', ocr: statement }], async () => {
    throw new Error('A statement must not request or invent a draft');
  });
  assert.equal(result[0].draft, undefined); assert.deepEqual(result[0].ocr, statement);
});

test('image attachments enforce size, MIME, signatures and canonical data URLs before provider calls', async () => {
  const { bytes, dataUrl } = await tinyPng();
  assert.equal(validateAiImageDataUrl(dataUrl), dataUrl);
  await validateAiImageFile(new File([bytes], 'receipt.png', { type: 'image/png' }));
  await assert.rejects(validateAiImageFile({ type: 'image/png', size: AI_IMAGE_LIMITS.originalBytes + 1 }), /8MB/);
  await assert.rejects(validateAiImageFile(new File([bytes], 'receipt.jpg', { type: 'image/jpeg' })), /不符/);
  await assert.rejects(validateAiImageFile(new File(['<svg/>'], 'receipt.svg', { type: 'image/svg+xml' })), /JPEG/);
  await assert.rejects(validateAiImageFile(new File([], 'empty.png', { type: 'image/png' })), /8MB/);
  for (const invalid of ['https://example.test/image.png', dataUrl.replace('image/png', 'image/jpeg'),
    dataUrl + '$', dataUrl.slice(0, -1), 'data:image/png;base64,' + 'A'.repeat(AI_IMAGE_LIMITS.dataUrlBytes),
    'data:image/png;base64,iVBORw0KGgp=']) assert.throws(() => validateAiImageDataUrl(invalid));
  const signatures = [{ type: 'image/jpeg', bytes: [255, 216, 255, 0] },
    { type: 'image/webp', bytes: [82, 73, 70, 70, 4, 0, 0, 0, 87, 69, 66, 80] }];
  for (const signature of signatures) {
    await validateAiImageFile(new File([new Uint8Array(signature.bytes)], 'image', { type: signature.type }));
    validateAiImageDataUrl(`data:${signature.type};base64,` + Buffer.from(signature.bytes).toString('base64'));
  }
});

test('attachment preprocessing limits dimensions, strips metadata and performs no worker CDN request', async () => {
  const { bytes, dataUrl } = await tinyPng();
  const file = new File([bytes], 'receipt.png', { type: 'image/png' });
  let compressionCalls = 0;
  const dependencies = { compress: async (input, options) => {
    compressionCalls++; assert.equal(input, file); assert.equal(options.maxWidthOrHeight, 2400);
    assert.equal(options.useWebWorker, false); assert.equal(options.preserveExif, false);
    assert.equal(options.maxSizeMB, 1.4); return file;
  }, readDimensions: async () => ({ width: 1200, height: 2400 }), readDataUrl: async () => dataUrl };
  const prepared = await prepareAiImage(file, dependencies);
  assert.equal(prepared.name, 'receipt.png'); assert.equal(prepared.dataUrl, dataUrl); assert.equal(compressionCalls, 1);
  for (const dimensions of [{ width: 2401, height: 2 }, { width: 0, height: 2 }, { width: 2, height: NaN }]) {
    await assert.rejects(prepareAiImage(file, { ...dependencies, readDimensions: async () => dimensions }), /无法处理/);
  }
});

test('Web OCR sends only the explicit encrypted-request payload after image consent', async () => {
  const { dataUrl } = await tinyPng();
  const source = await readFile(new URL('../src/utils/api/ai.js', import.meta.url), 'utf8');
  const calls = [];
  const result = { contractVersion: 1, mode: 'deepseek', formalWriteEnabled: false, trace: [], draft: null,
    conversationId: randomUUID(), answer: '请核对原图', ocr: ocrResult({ documentType: 'statement' }) };
  const recognize = Function('request', 'validateAiImageDataUrl', 'validateAiResult', 'validateAiOcr',
    source.replace(/^import[\s\S]*?;\s*/gm, '').replace(/^export /gm, '') + '\nreturn recognizeAiImage;')(
    { post: async (...args) => { calls.push(args); return { data: result }; } }, validateAiImageDataUrl, validateAiResult, validateAiOcr);
  await assert.rejects(recognize({ image: { dataUrl }, consent: false }), /同意/);
  await assert.rejects(recognize({ image: { dataUrl: 'https://example.test/image.png' }, consent: true }), /图片/);
  assert.equal(calls.length, 0);
  const conversationId = randomUUID(); const connectionId = randomUUID();
  assert.equal(await recognize({ image: { dataUrl, name: 'private.png', accountId: 'DO_NOT_SEND' },
    message: '这是账单', consent: true, conversationId, connectionId, userId: 'DO_NOT_SEND' }), result);
  assert.deepEqual(calls, [['/ai/ocr', { image: { dataUrl }, message: '这是账单', consent: true,
    conversationId, connectionId }, { timeout: 120000 }]]);
});

test('OCR HTTP results restore as draft cards or statement evidence without real I/O', async () => {
  const { dataUrl } = await tinyPng();
  let imageCalls = 0;
  const responses = [ocrResult(), ocrResult({ documentType: 'statement', items: [ocrItem(), ocrItem({ amount: '12' })] })];
  const app = await serve(createFixtureService({ chat: async () => { throw new Error('OCR must not call chat'); },
    recognizeImage: async (config, image) => {
      assert.equal(config.apiKey, 'fixture-provider-key'); assert.match(image.dataUrl, /^data:image\/jpeg;base64,/);
      return responses[imageCalls++];
    } }));
  try {
    assert.equal((await app.send('/ocr', 'POST', { image: { dataUrl }, consent: false })).status, 403);
    assert.equal(imageCalls, 0);
    const paymentResponse = await app.send('/ocr', 'POST', { image: { dataUrl }, consent: true });
    assert.equal(paymentResponse.status, 200);
    const payment = validateAiResult((await paymentResponse.json()).data);
    assert.equal(payment.ocr.documentType, 'payment'); assert.equal(payment.draft.accountId, null);
    assert.equal(payment.draft.status, 'pending'); assert.equal(payment.draft.needsReview, true);
    const statementResponse = await app.send('/ocr', 'POST', { image: { dataUrl }, consent: true, conversationId: payment.conversationId });
    assert.equal(statementResponse.status, 200);
    const statement = validateAiResult((await statementResponse.json()).data);
    assert.equal(statement.draft, null); assert.equal(statement.ocr.items.length, 2);
    const history = (await (await app.send(`/conversations/${payment.conversationId}/messages`)).json()).data;
    const restored = await restoreAiHistory(history, async id => (await (await app.send(`/drafts/${id}`)).json()).data);
    assert.equal(restored[1].draft.id, payment.draft.id); assert.equal(restored[1].ocr.items[0].amount, '32.00');
    assert.equal(restored[3].draft, undefined); assert.equal(restored[3].ocr.documentType, 'statement');
    assert.equal((await app.send(`/conversations/${payment.conversationId}/messages`, 'GET', undefined, 'other')).status, 404);
    assert.equal(imageCalls, 2);
  } finally { await app.close(); }
});

function displayDraft(overrides = {}) {
  return { ...draftInput(), id: randomUUID(), version: 1, expiresAt: Date.now() + 60000, status: 'pending',
    needsReview: true, formalWriteEnabled: false, missingFields: [], choices: { accounts: [], categories: [] }, ...overrides };
}
function replyWithDraft(draft, overrides = {}) {
  return { contractVersion: 1, mode: 'deepseek', formalWriteEnabled: false, conversationId: randomUUID(),
    answer: '已更新草稿，请核对', trace: [], draft, ...overrides };
}

test('same-ID chat replies update the original OCR card while keeping user turns and original image evidence', () => {
  const original = displayDraft({ source: 'image-ocr', accountId: null, missingFields: ['账户'] });
  const ocr = ocrResult();
  const anchor = { id: 2, role: 'assistant', text: '请核对图片', draft: original, ocr };
  const entry = { id: 4, role: 'assistant', pending: true };
  const messages = [{ id: 1, role: 'user', imagePreview: 'local-preview', text: '识别图片' }, anchor,
    { id: 3, role: 'user', text: '选择现金账户' }, entry];
  const updated = { ...original, version: 2, amount: '35', accountId: 'fixture_cash', missingFields: [] };
  const visible = applyAiReply(messages, entry, replyWithDraft(updated));
  assert.equal(visible.length, 3); assert.equal(visible[1], anchor);
  assert.equal(anchor.draft, updated); assert.equal(anchor.ocr, ocr); assert.equal(anchor.draftId, original.id);
  assert.equal(visible[0].imagePreview, 'local-preview'); assert.equal(visible[2].text, '选择现金账户');
  assert.equal(visible.filter(item => item.draft).length, 1);
  assert.ok(!visible.some(item => item.text === '已更新草稿，请核对'));
});

test('same-ID replies retain actual query evidence without another card or repeated completion text', () => {
  const draft = displayDraft();
  const anchor = { role: 'assistant', draft, ocr: ocrResult() };
  const entry = { role: 'assistant', pending: true };
  const query = { tool: 'query_finance_summary', readOnly: true, result: { income: '10', expense: '20', count: 2 } };
  const result = replyWithDraft({ ...draft, version: 2 }, { trace: [query,
    { tool: 'propose_transaction', readOnly: true, result: {} }] });
  const visible = applyAiReply([anchor, { role: 'user', text: '查收支并补账户' }, entry], entry, result);
  assert.equal(visible.length, 3); assert.equal(visible[2], entry);
  assert.equal(entry.text, ''); assert.equal(entry.draft, null); assert.equal(entry.ocr, null);
  assert.deepEqual(entry.evidence, [query]); assert.equal(anchor.draft.version, 2);
  assert.equal(visible.filter(item => item.draft).length, 1);
});

test('card consolidation preserves distinct drafts, ordinary answers and errors, and never downgrades a newer version', () => {
  const first = displayDraft({ version: 3 }); const second = displayDraft();
  const messages = [{ role: 'assistant', draft: first }, { role: 'assistant', text: '普通回答' },
    { role: 'assistant', draft: second }, { role: 'assistant', text: '较旧草稿说明', draft: { ...first, version: 2 } },
    { role: 'assistant', draftId: first.id, error: true, text: '请求失败' }];
  const visible = collapseAiDraftCards(messages);
  assert.equal(visible.length, 4); assert.equal(visible[0].draft.version, 3);
  assert.equal(visible[1].text, '普通回答'); assert.equal(visible[2].draft.id, second.id);
  assert.equal(visible[3].text, '请求失败');
});

test('historical repeated IDs restore one latest card and original OCR while retaining query cards and new warnings', async () => {
  const draft = displayDraft({ version: 3, amount: '35' });
  const ocr = ocrResult({ warnings: ['原图片需核对'] });
  const nextOcr = ocrResult({ items: [ocrItem({ amount: '999' })], truncated: true, warnings: ['这张图片已有草稿'] });
  const query = { tool: 'query_transactions', readOnly: true, result: { rows: [], total: 0, hasMore: false } };
  const entries = [{ role: 'user', content: '识别图片' },
    { role: 'assistant', content: '请核对草稿', draftId: draft.id, ocr },
    { role: 'user', content: '补现金账户' },
    { role: 'assistant', content: '已更新', draftId: draft.id },
    { role: 'assistant', content: '已更新并查询', draftId: draft.id, trace: [query], ocr: nextOcr }];
  let reads = 0;
  const visible = await restoreAiHistory(entries, async id => { reads++; assert.equal(id, draft.id); return draft; });
  assert.equal(reads, 1); assert.equal(visible.length, 4);
  assert.equal(visible.filter(item => item.draft).length, 1);
  assert.equal(visible[1].draft.version, 3); assert.equal(visible[1].draft.amount, '35');
  assert.equal(visible[1].ocr.items[0].amount, '32.00'); assert.equal(visible[1].ocr.truncated, true);
  assert.deepEqual(visible[1].ocr.warnings, ['这张图片已有草稿', '原图片需核对']);
  assert.equal(visible[2].text, '补现金账户'); assert.deepEqual(visible[3].evidence, [query]);
  assert.ok(!visible.some(item => item.text.includes('已更新')));
});

test('a repeated history card failure shows one independent notice and can later recover into the same OCR anchor', async () => {
  const draft = displayDraft(); const ocr = ocrResult();
  const entries = [{ role: 'assistant', content: '已更新', draftId: draft.id },
    { role: 'assistant', content: '已更新', draftId: draft.id, ocr }];
  const restored = await restoreAiHistory(entries, async () => { throw { status: 503 }; });
  assert.equal(restored.length, 1); assert.equal(restored[0].draftId, draft.id);
  assert.equal(restored[0].ocr, ocr); assert.match(restored[0].draftNotice, /读取失败/); assert.equal(restored[0].text, '');
  const anchor = restored[0]; const entry = { role: 'assistant', pending: true };
  const visible = applyAiReply([...restored, { role: 'user', text: '重新读取草稿' }, entry], entry, replyWithDraft({ ...draft, version: 2 }));
  assert.equal(visible.length, 2); assert.equal(visible[0], anchor);
  assert.equal(anchor.draft.version, 2); assert.equal(anchor.draftNotice, ''); assert.equal(anchor.ocr, ocr);
  assert.throws(() => validateAiMessages([{ role: 'assistant', content: '更新', draftId: draft.id,
    trace: [{ tool: 'query_transactions', readOnly: false, result: {} }] }]));
});

test('duplicate historical terminal cards retain a single final state and committed receipt after expiry', async () => {
  for (const status of ['committed', 'discarded']) {
    const draft = displayDraft({ status, expiresAt: 1000, needsReview: status !== 'committed',
      ...(status === 'committed' ? { committedRecordId: 'receipt-1' } : {}) });
    const visible = await restoreAiHistory([{ role: 'assistant', content: '请核对', draftId: draft.id, ocr: ocrResult() },
      { role: 'assistant', content: '已更新', draftId: draft.id }], async () => draft, () => 2000);
    assert.equal(visible.length, 1); assert.equal(visible[0].draft.status, status);
    assert.ok(!visible[0].draftNotice);
    if (status === 'committed') {
      assert.equal(visible[0].draft.committedRecordId, 'receipt-1'); assert.equal(visible[0].draft.needsReview, false);
    }
  }
});
