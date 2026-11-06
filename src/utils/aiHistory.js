import { validateAiMessages, validateAiDraft } from './aiContract.js';
import { getAiDraftDisplay } from './aiDraftState.js';
import { collapseAiDraftCards, getAiQueryEvidence } from './aiMessages.js';

const unavailable = '这份草稿已过期或不存在，无法恢复卡片。';

export async function restoreAiHistory(entries, loadDraft, now = Date.now) {
  validateAiMessages(entries);
  const drafts = new Map();
  const ids = [...new Set(entries.filter(entry => entry.role === 'assistant' && entry.draftId).map(entry => entry.draftId))];
  // Keep historical cards within the API's small read-only connection pool.
  for (let index = 0; index < ids.length; index += 2) {
    await Promise.all(ids.slice(index, index + 2).map(async id => {
      try { drafts.set(id, { draft: validateAiDraft(await loadDraft(id)) }); }
      catch (error) { drafts.set(id, { error }); }
    }));
  }
  return collapseAiDraftCards(entries.map(entry => {
    const item = { role: entry.role, text: entry.content, ...(entry.ocr ? { ocr: entry.ocr } : {}),
      ...(entry.trace ? { evidence: getAiQueryEvidence(entry.trace) } : {}) };
    if (entry.role !== 'assistant' || !entry.draftId) return item;
    item.draftId = entry.draftId;
    const { draft, error } = drafts.get(entry.draftId);
    if (draft) {
      if (getAiDraftDisplay(draft, now()).expired) {
        item.draftNotice = unavailable;
      } else {
        item.draft = draft;
      }
    } else {
      const status = error?.status ?? error?.response?.status;
      item.draftNotice = status === 404 ? unavailable : '草稿读取失败，暂时无法恢复卡片，请稍后重新打开历史对话。';
    }
    // A missing card must not revive an old model claim such as "已更新".
    item.text = '';
    return item;
  }));
}
