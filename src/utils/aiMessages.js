const QUERY_TOOLS = ['query_finance_summary', 'query_transactions'];

export function getAiQueryEvidence(trace = []) {
  return trace.filter(item => item.readOnly === true && QUERY_TOOLS.includes(item.tool));
}

function mergeOcr(original, next) {
  if (!original) return next;
  if (!next) return original;
  // Keep the original image evidence, including when the proposal has been edited.
  return { ...original, truncated: original.truncated || next.truncated,
    warnings: [...new Set([...next.warnings, ...original.warnings])].slice(0, 10) };
}

export function collapseAiDraftCards(messages) {
  const anchors = new Map();
  const visible = [];
  for (const item of messages) {
    const draftId = item.role === 'assistant' && (item.draftId || item.draft?.id);
    if (!draftId || item.pending || item.error) { visible.push(item); continue; }
    item.draftId = draftId;
    const anchor = anchors.get(draftId);
    if (!anchor) { anchors.set(draftId, item); visible.push(item); continue; }
    if (item.draft && (!anchor.draft || item.draft.version >= anchor.draft.version)) {
      anchor.draft = item.draft;
      anchor.draftNotice = '';
    }
    anchor.ocr = mergeOcr(anchor.ocr, item.ocr);
    if (!anchor.draft && item.draftNotice) anchor.draftNotice = item.draftNotice;
    // Completion phrases belong to the updated card, not a second assistant bubble.
    item.text = ''; item.draft = null; item.ocr = null; item.draftNotice = '';
    if (item.evidence?.length) visible.push(item);
  }
  return visible;
}

export function applyAiReply(messages, entry, result) {
  Object.assign(entry, { pending: false, error: false, text: result.answer, draft: result.draft || null,
    draftId: result.draft?.id || null, draftNotice: '', ocr: result.ocr || null,
    evidence: getAiQueryEvidence(result.trace) });
  delete entry.requestText; delete entry.requestImage;
  return collapseAiDraftCards(messages);
}
