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
    const failureRequestId = entry.failure ? (entry.failure.requestId || entry.requestId) : null;
    const item = { role: entry.role, text: entry.content, ...(entry.createdAt ? { createdAt: entry.createdAt } : {}), ...(entry.ocr ? { ocr: entry.ocr } : {}),
      ...(Number.isSafeInteger(entry.sentAt) && entry.sentAt > 0 ? { sentAt: entry.sentAt } : {}),
      ...(entry.messageId ? { messageId: entry.messageId } : {}), ...(entry.requestId ? { requestId: entry.requestId } : {}),
      ...(entry.trace ? { evidence: getAiQueryEvidence(entry.trace) } : {}),
      ...(failureRequestId ? { requestId: failureRequestId, failure: entry.failure } : {}),
      // 追加164：历史恢复带思考内容（折叠展示，仅 assistant 消息有）
      ...(entry.role === 'assistant' && entry.thinking ? { thinking: entry.thinking } : {}) };
    // 服务端保存的失败/中止轮次：显示服务端文案，并保留重试/读取回执入口。
    if (entry.role === 'assistant' && failureRequestId) {
      item.error = true;
      item.turn = { requestId: failureRequestId, state: 'failed' };
      if (entry.failure?.providerDetail) item.providerDetail = entry.failure.providerDetail;
    }
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
