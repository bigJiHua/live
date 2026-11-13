import { restoreAiHistory } from './aiHistory.js';
import { collapseAiDraftCards } from './aiMessages.js';
import { validateAiBusinessMessages, validateAiProposal } from './aiBusinessContract.js';
import { isAiAuthorizationFailure } from './aiTurn.js';

// History contains an old snapshot. Only a current server read can restore an actionable card.
export async function restoreAiBusinessHistory(entries, loadDraft, loadProposal, loadCandidate, loadPendingTask) {
  entries = validateAiBusinessMessages(entries.map(entry => ({ ...entry, content: entry.content ?? entry.text,
    ...(entry.draft?.id && !entry.draftId ? { draftId: entry.draft.id } : {}) })), { allowUnavailableChart: true });
  // Only explicit persisted links collapse an authorization/retry predecessor.
  // Equal wording still identifies independent user requests.
  const parents = new Map(entries.flatMap(entry => [
    ...(entry.requestId && entry.retryOf ? [[entry.requestId, entry.retryOf]] : []),
    ...(entry.requestId && entry.continuedBy ? [[entry.continuedBy, entry.requestId]] : []),
  ]));
  const superseded = new Set(parents.values());
  const predecessors = requestId => {
    const result = [], seen = new Set([requestId]);
    while (parents.has(requestId) && !seen.has(parents.get(requestId))) {
      requestId = parents.get(requestId); seen.add(requestId); result.push(requestId);
    }
    return result;
  };
  // The same saved message can contain both draftIds and candidateIds.
  // A draft occupies the original message; every candidate then needs its own
  // entry, including the first candidate saved alongside that draft.
  const list = [];
  for (const entry of entries) {
    if (entry.requestId && superseded.has(entry.requestId)) continue;
    const draftIds = [...new Set([entry.draftId || entry.draft?.id, ...(entry.draftIds || [])].filter(Boolean))];
    const primaryCandidateId = entry.inputCandidate?.sourceCandidateId || entry.inputCandidate?.id;
    const candidateIds = [...new Set([primaryCandidateId, ...(entry.candidateIds || [])].filter(Boolean))];
    const original = { ...entry };
    if (draftIds.length) {
      original.draftId = draftIds[0];
      delete original.inputCandidate; delete original.financialCheck; delete original.financialChoices;
    } else if (candidateIds.length && !primaryCandidateId) {
      original.inputCandidate = { sourceCandidateId: candidateIds[0] };
    }
    list.push(original);
    const identity = { messageId: entry.messageId, requestId: entry.requestId };
    for (const draftId of draftIds.slice(1)) list.push({ ...identity, role: 'assistant', content: '', draftId });
    for (const cid of candidateIds.slice(draftIds.length ? 0 : 1)) list.push({ ...identity, role: 'assistant', content: '', inputCandidate: { sourceCandidateId: cid } });
  }
  const normalized = validateAiBusinessMessages(list.map(entry => ({ ...entry, content: entry.content ?? entry.text,
    ...(entry.draft?.id && !entry.draftId ? { draftId: entry.draft.id } : {}) })), { allowUnavailableChart: true });
  let currentPendingTask = null, pendingReadFailed = false;
  if (normalized.some(entry => entry.pendingTask)) {
    try {
      if (!loadPendingTask) throw new Error('No pending task reader');
      currentPendingTask = (await loadPendingTask()).pendingTask;
    } catch { pendingReadFailed = true; }
  }
  const proposalIds = [...new Set(normalized.filter(entry => entry.role === 'assistant' && !entry.draftId)
    .map(entry => entry.proposalId || entry.proposal?.id).filter(Boolean))];
  const proposals = new Map(), proposalErrors = new Map(), drafts = new Map(), candidates = new Map();
  const candidateIds = [...new Set(normalized.filter(entry => entry.role === 'assistant' && !entry.draftId)
    .map(entry => entry.inputCandidate?.sourceCandidateId || entry.inputCandidate?.id).filter(Boolean))];
  for (let index = 0; index < candidateIds.length; index += 2) {
    await Promise.all(candidateIds.slice(index, index + 2).map(async id => {
      try { candidates.set(id, loadCandidate ? await loadCandidate(id) : null); }
      catch { candidates.set(id, null); }
    }));
  }
  for (let index = 0; index < proposalIds.length; index += 2) {
    await Promise.all(proposalIds.slice(index,index+2).map(async id => {
      try { proposals.set(id, validateAiProposal(await loadProposal(id))); }
      catch (error) { proposalErrors.set(id, error?.status); proposals.set(id, null); }
    }));
  }
  const cachedDraft = id => {
    if (!drafts.has(id)) drafts.set(id, Promise.resolve().then(() => loadDraft(id)));
    return drafts.get(id);
  };
  const restored = [], anchors = new Map(), candidateAnchors = new Map();
  const originalMessages = new Map();
  for (const entry of normalized) if (entry.role === 'user' && entry.requestId) {
    const text = entry.content || entry.text || '';
    originalMessages.set(entry.requestId, originalMessages.has(entry.requestId) &&
      originalMessages.get(entry.requestId) !== text ? null : text);
  }
  for (const source of normalized) {
    const [item] = await restoreAiHistory([source], cachedDraft);
    if (source.retryOf) item.retryOf = source.retryOf;
    const previous = predecessors(source.requestId);
    if (previous.length) item.supersededRequestIds = previous;
    if (source.datasets) item.datasets = source.datasets;
    if (source.chart || source.chartNotice) {
      item.chart = source.chart || null;
      item.chartNotice = source.chartNotice || '';
    }
    if (source.toolTrace) item.toolTrace = source.toolTrace;
    if (source.operationOutcome) item.operationOutcome = source.operationOutcome;
    if (source.pendingTask) {
      item.pendingTask = pendingReadFailed ? source.pendingTask : currentPendingTask?.taskId === source.pendingTask.taskId ? currentPendingTask : null;
      item.pendingTaskUnavailable = pendingReadFailed;
    }
    // A failure owns only its request's original message, including paged or
    // interleaved history. Missing identity must never borrow adjacent wording.
    if (source.failure && (source.failure.requestId || source.requestId)) {
      const requestId = source.failure.requestId || source.requestId;
      item.error = true;
      item.requestId = requestId;
      const message = originalMessages.get(requestId) || '';
      item._failedPersistedRequestId = requestId;
      item.turn = { requestId, state: 'failed', message, request: { message } };
      if (source.authorizationRequest || isAiAuthorizationFailure(source.failure.code)) {
        if (source.authorizationRequest) item.authorizationRequest = source.authorizationRequest;
        item.lastError = { code: source.failure.code || 'AI_GRANT_REQUIRED' }; item.error = false;
        item.turn.state = 'awaiting_authorization';
      }
    }
    const candidateId = !source.draftId && (source.inputCandidate?.sourceCandidateId || source.inputCandidate?.id);
    if (candidateId) {
      const latest = candidates.get(candidateId);
      // Keep identity even when a temporary read failure hides the card, so a
      // later read-only state snapshot can recover it after the cursor advances.
      item.candidateId = candidateId;
      item.text = '';
      if (!latest) { item.financialClosed = true; item.draftNotice = '记账候选已过期或暂时无法读取，请重新核查。'; }
      else if (latest.draft) {
        try { item.draft = await cachedDraft(latest.draft.id); item.draftId = item.draft.id; item.financialClosed = true; }
        catch { item.financialClosed = true; item.draftNotice = '草稿暂时无法读取，请稍后重新打开历史查看。'; }
      } else if (['discarded','expired','failed','consumed'].includes(latest.inputCandidate?.status)) {
        item.financialClosed = true; item.draftNotice = latest.inputCandidate.status==='discarded'?'这份记账候选已取消。':'这份记账候选已结束，请重新核查。';
      } else {
        item.inputCandidate = latest.inputCandidate; item.financialCheck = latest.financialCheck;
        item.financialChoices = latest.choices || latest.financialChoices || latest.inputCandidate?.choices;
      }
      item._syncCandidateResolved = Boolean(latest && (!latest.draft || item.draft));
      const anchor=candidateAnchors.get(candidateId);
      if(anchor) {
        if(item.ocr) {if(!anchor.ocr)anchor.ocr=item.ocr;delete item.ocr;}
        if(item.draft&&!anchor.draft) {anchor.draft=item.draft;anchor.draftId=item.draftId;anchor.financialClosed=true;delete anchor.inputCandidate;delete anchor.financialCheck;}
        delete item.inputCandidate;delete item.financialCheck;delete item.financialChoices;
        if(!item.draft) {delete item.draftNotice;item.financialClosed=true;}
      } else candidateAnchors.set(candidateId,item);
    }
    const proposalId = !source.draftId && (source.proposalId || source.proposal?.id);
    if (proposalId) {
      item.proposalId = proposalId; item.text = '';
      const proposal = proposals.get(proposalId);
      if (proposal) {
        const anchor = anchors.get(proposalId);
        if (anchor) { if (anchor.proposal.version <= proposal.version) anchor.proposal = proposal; }
        else { item.proposal = proposal; anchors.set(proposalId,item); }
      } else {
        const status = proposalErrors.get(proposalId);
        item.draftNotice = status === 410 ? '这条业务提案已过期，请重新发起生成。'
          : status === 404 ? '这条业务提案已不存在，无法再执行。'
          : '业务草稿已过期、不存在或暂时无法读取，请稍后重新打开历史查看。';
      }
    }
    restored.push(item);
  }
  return collapseAiDraftCards(restored);
}
