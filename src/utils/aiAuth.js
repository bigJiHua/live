// Presentation cache only. The server verifies conversation, provider and TTL.
// Legacy domain arrays are retained for old cached entries; new grants cover all domains.
const KEY = 'finance_ai_grant_v1';
const memory = { value: null };

function readCache() {
  try {
    const raw = globalThis.localStorage?.getItem(KEY);
    const value = raw ? JSON.parse(raw) : null;
    return value && typeof value === 'object' ? value : null;
  } catch { return null; }
}

function writeCache(value) {
  try { globalThis.localStorage?.setItem(KEY, JSON.stringify(value)); } catch { /* 环境不支持时退内存态 */ }
}

export function getCachedAiGrant() {
  return readCache() || memory.value;
}

export function setCachedAiGrant(grant) {
  const value = grant?.granted ? { granted: true, providerHost: grant.providerHost, domains: [...(grant.domains || [])], grantedAt: grant.grantedAt ?? Date.now() } : { granted: false };
  if (grant?.contractVersion === 2 && grant.granted) Object.assign(value, { contractVersion: 2, conversationId: grant.conversationId,
    access: grant.access, lifetime: grant.lifetime, expiresAt: grant.expiresAt });
  memory.value = value;
  writeCache(value);
  return value;
}

export function clearCachedAiGrant() {
  memory.value = { granted: false };
  try { globalThis.localStorage?.removeItem(KEY); } catch { /* ignore */ }
}
