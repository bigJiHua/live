// Local fallback for the AI chat page: the draft being typed and turns that
// never reached the server (or whose failure has not been written yet). Failed
// terminal turns are also saved in server history; this local record only
// covers requests that failed before the server could persist them and expires
// with the 24h receipt window. Images are never stored.
const KEY = 'ai_chat_state_v1';
const LAST_KEY = 'ai_chat_last_conversation_v1';
const TTL = 24 * 60 * 60 * 1000;
const MAX_TURNS = 8;

function storage() {
  try { return typeof localStorage === 'undefined' ? null : localStorage; } catch { return null; }
}
function readAll(now) {
  const store = storage();
  if (!store) return {};
  let value;
  try { value = JSON.parse(store.getItem(KEY) || '{}'); } catch { return {}; }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  const state = {};
  for (const [id, entry] of Object.entries(value)) {
    if (!entry || typeof entry !== 'object') continue;
    if (now - Number(entry.updatedAt || 0) > TTL) continue;
    state[id] = entry;
  }
  return state;
}
function writeAll(state) {
  const store = storage();
  if (!store) return;
  try { store.setItem(KEY, JSON.stringify(state)); } catch { /* storage full or blocked */ }
}
function entryOf(state, id, now) {
  return state[id] && typeof state[id] === 'object' ? state[id] : { draft: '', turns: [], updatedAt: now };
}
function save(id, patch, now = Date.now()) {
  if (typeof id !== 'string' || !id) return;
  const state = readAll(now);
  state[id] = { ...entryOf(state, id, now), ...patch, updatedAt: now };
  writeAll(state);
}
export function readDraft(id) {
  if (typeof id !== 'string' || !id) return '';
  const entry = readAll(Date.now())[id];
  return entry && typeof entry.draft === 'string' ? entry.draft : '';
}
export function saveDraft(id, draft) {
  if (typeof draft !== 'string') return;
  save(id, { draft });
}
export function readTurns(id, now = Date.now()) {
  if (typeof id !== 'string' || !id) return [];
  const entry = readAll(now)[id];
  if (!entry || !Array.isArray(entry.turns)) return [];
  return entry.turns.filter(turn => turn && typeof turn.requestId === 'string' &&
    now - Number(turn.at || 0) <= TTL).slice(-MAX_TURNS);
}
export function addTurn(id, turn, now = Date.now()) {
  if (!turn || typeof turn.requestId !== 'string') return;
  const turns = readTurns(id, now).filter(item => item.requestId !== turn.requestId);
  turns.push({ ...turn, at: now });
  save(id, { turns: turns.slice(-MAX_TURNS) }, now);
}
export function updateTurn(id, requestId, patch, now = Date.now()) {
  const turns = readTurns(id, now);
  const index = turns.findIndex(turn => turn.requestId === requestId);
  if (index < 0) return;
  turns[index] = { ...turns[index], ...patch, at: turns[index].at || now };
  save(id, { turns }, now);
}
export function dropTurn(id, requestId, now = Date.now()) {
  const turns = readTurns(id, now).filter(turn => turn.requestId !== requestId);
  save(id, { turns }, now);
}
export function clearConversation(id) {
  if (typeof id !== 'string' || !id) return;
  const state = readAll(Date.now());
  if (!state[id]) return;
  delete state[id];
  writeAll(state);
}
// Remember which conversation was open so re-entering /ai without a URL id
// resumes it instead of jumping back to today's default session.
export function readLastConversation() {
  try { return storage()?.getItem(LAST_KEY) || ''; } catch { return ''; }
}
export function saveLastConversation(id) {
  if (typeof id !== 'string' || !id) return;
  try { storage()?.setItem(LAST_KEY, id); } catch { /* storage blocked */ }
}
export function clearLastConversation(id) {
  try {
    const store = storage();
    if (store && (!id || store.getItem(LAST_KEY) === id)) store.removeItem(LAST_KEY);
  } catch { /* storage blocked */ }
}
