// One explicit confirmation owns one provider request. Recovery only reads its receipt.
export function createAiRequestId() {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

// 只读回执轮询（D-012 允许的「只读回执恢复」自动化）：只 GET 回执，绝不重发 POST。
// status=processing/error 继续等，failed/completed 终止；超过 maxAttempts 交还用户手动处理。
// Cover the server's 45s process lease plus restart time. Bound every read,
// including requests stuck before the HTTP client's own timeout begins.
export function createReceiptPoll({ fetchReceipt, intervalMs = 3000, maxAttempts = 30, timeoutMs = 12000,
  delay = (fn, ms) => setTimeout(fn, ms) } = {}) {
  let cancelled = false;
  let attempts = 0;
  let cancelRead;
  const stop = () => { cancelled = true; cancelRead?.(); };
  async function tick(onEvent) {
    if (cancelled) return;
    attempts += 1;
    let status = 'error';
    let receipt = null;
    let timer;
    try {
      receipt = await Promise.race([
        Promise.resolve().then(() => cancelled ? null : fetchReceipt()),
        new Promise((_, reject) => {
          cancelRead = () => reject(new Error('Receipt polling stopped'));
          timer = setTimeout(() => reject(new Error('Receipt read timed out')), timeoutMs);
        }),
      ]);
      if (cancelled) return;
      if (receipt?.result) { onEvent({ type: 'completed', receipt }); return; }
      status = receipt?.status === 'failed' ? 'failed' : 'processing';
    } catch (error) {
      if (error?.kind === 'protocol' || error?.code === 'AI_RESPONSE_INVALID') {
        if (!cancelled) onEvent({ type: 'invalid', error });
        stop(); return;
      }
      status = 'error';
    } finally { clearTimeout(timer); cancelRead = null; }
    if (cancelled) return;
    onEvent({ type: status, attempts, ...(status === 'failed' ? { receipt } : {}) });
    if (status !== 'processing' && status !== 'error') return;
    if (attempts >= maxAttempts) { onEvent({ type: 'exhausted', attempts }); return; }
    delay(() => tick(onEvent), intervalMs);
  }
  // start 返回 tick 的 Promise（测试可 await；运行时忽略即可）
  return { start: onEvent => tick(onEvent), stop };
}

export const AI_DOMAINS = ['finance', 'account', 'card', 'budget', 'todo', 'work', 'asset', 'fixedAsset', 'fund', 'installment'];
export const AI_DOMAIN_LABELS = { finance: '收支', account: '账户', card: '信用卡', budget: '预算', todo: '待办', work: '工作日记', asset: '资产', fixedAsset: '固定资产', fund: '基金', installment: '分期' };

export function isAiAuthorizationFailure(code) {
  return code === 'AI_GRANT_REQUIRED' || code === 'AI_DOMAIN_NOT_GRANTED';
}

