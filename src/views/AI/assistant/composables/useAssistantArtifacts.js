// Business artifacts keep their identity, version and local draft receipt guards.
// This compatibility module also retains candidate actions for future card wiring.
export function useAssistantArtifacts({ state, services, runtime, actions }) {
  const { messages, conversationId, transferBusy, draftBusy, cardWork } = state;
  const { createAiTransactionDraft, cancelAiTransactionCandidate, getAiTransactionCandidate,
    getAiLocalDraftReceipt, getAiDraft, getAiFinancialGate, createAiRequestId,
    getAiPendingTransaction, resumeAiPendingTransaction } = services;
  const { bottom, finishTurn, finishActivities } = actions;
  // A conversation boundary releases this action even if its request ignores
  // cancellation; a late result/finally cannot own the next action's locks.
  let candidateRun = null;
  function currentEntry(entry, id) {
    return runtime.isActive() && conversationId.value === id && messages.value.includes(entry);
  }
  function setCardWorking(item, kind, value) {
    if (value && !currentEntry(item, conversationId.value)) return;
    const key = String(item.id) + '|' + kind;
    if (value) cardWork.value[key] = true;
    else delete cardWork.value[key];
  }
  function updateCard(entry, kind, value) {
    if (!currentEntry(entry, conversationId.value) || !value?.id || entry[kind]?.id !== value.id ||
      (entry[kind]?.version || 0) > (value.version || 0)) return;
    entry[kind] = value;
    if (kind === 'draft') entry.draftId = value.id;
  }
  function updateProposal(entry, proposal) { updateCard(entry, 'proposal', proposal); }
  function updateDraft(entry, draft) { updateCard(entry, 'draft', draft); }
  function beginCandidateAction(entry) {
    const run = { entry };
    candidateRun = run; draftBusy.value = true; setCardWorking(entry, 'candidate', true);
    return run;
  }
  function finishCandidateAction(run) {
    if (candidateRun !== run) return false;
    candidateRun = null; draftBusy.value = false; setCardWorking(run.entry, 'candidate', false);
    return true;
  }
  function invalidateArtifactActions() {
    if (candidateRun) finishCandidateAction(candidateRun);
  }
  function applyBusinessReply(entry, result) {
    conversationId.value = result.conversationId;
    finishTurn(entry);
    // Keep both groups when only some entries have all required fields.
    const multiCards = result.drafts || result.candidates ? [
      ...(result.drafts || []).map(draft => ({ draft })), ...(result.candidates || []),
    ] : [];
    if (multiCards.length) result = { ...result, draft: null, inputCandidate: null,
      financialCheck: null, financialChoices: null, proposal: null, ...multiCards[0] };
    Object.assign(entry, { pending: false, error: false, text: result.answer, datasets: result.datasets || [], chart: result.chart || null, chartNotice: result.chartNotice || '', proposal: result.draft ? null : result.proposal || null, draft: result.draft || null, draftId: result.draft?.id || null,
      inputCandidate: result.inputCandidate || null, financialCheck: result.financialCheck || null,
      financialChoices: result.financialChoices || null, financialClosed: Boolean(result.draft), toolTrace: result.toolTrace || [], promptVersion: result.promptVersion || null,
      operationOutcome: result.operationOutcome || null });
    entry.pendingTask = result.pendingTask || null;
    entry.pendingTaskClosed = false;
    entry.pendingTaskUnavailable = false;
    entry.businessArtifactId = result.draft?.id || result.inputCandidate?.sourceCandidateId || result.inputCandidate?.id || null;
    delete entry.lastError;
    delete entry.providerDetail;
    delete entry.failure;
    delete entry.authorizationRequest;
    delete entry.authorizationDenied;
    delete entry.receiptNotice;
    // 思考只来自成功终态后核验过的reasoning事件，不读取未定义的result.thinking。
    entry.thinking = entry.thinking || '';
    entry.usage = result.request?.usage || null;
    finishActivities(entry, entry.usage);
    if (entry.turn) { entry.turn.state = 'completed'; entry.turn.preview = null; }
    applyFinancialFields(entry, result);
    if (entry.proposal) {
      const previous = messages.value.find(item => item !== entry && item.proposal?.id === result.proposal.id);
      if (previous) { previous.proposal = result.proposal; entry.proposal = null; entry.text = ''; }
    }
    if (multiCards.length) {
      const index = messages.value.indexOf(entry);
      let offset = 1;
      for (const card of multiCards.slice(1)) {
        const id = card.draft?.id || card.inputCandidate?.sourceCandidateId || card.inputCandidate?.id;
        const previous = messages.value.find(item => item !== entry &&
          (item.businessArtifactId === id || item.draft?.id === id || candidateId(item) === id));
        if (previous) {
          if (card.draft && (!previous.draft || previous.draft.version <= card.draft.version)) {
            previous.draft = card.draft; previous.draftId = card.draft.id;
          } else if (!card.draft && !previous.financialClosed) applyFinancialFields(previous, card);
          continue;
        }
        // 2026-11-10 B4：拆卡消息弱化锚——续卡不再重复完整发言人行，只显示「财务助手 · 续 N」
        messages.value.splice(index + offset, 0, { id: runtime.nextId(), role: 'assistant', requestId: entry.requestId, text: '', sentAt: Date.now(), continuation: offset + 1,
          businessArtifactId: id, ...card, ...(card.draft ? { draftId: card.draft.id, financialClosed: true } : {}) });
        offset++;
      }
    }
  }
  function applyFinancialFields(entry, result) {
    if (Object.hasOwn(result, 'pendingTask')) entry.pendingTask = result.pendingTask;
    if (result.inputCandidate) entry.inputCandidate = result.inputCandidate;
    if (result.financialCheck) entry.financialCheck = result.financialCheck;
    if (result.financialChoices) entry.financialChoices = result.financialChoices;
    else if (result.inputCandidate?.choices) entry.financialChoices = result.inputCandidate.choices;
    if (result.draft) { entry.financialClosed = true; entry.financialError = ''; }
  }
  function candidateId(entry) { return entry.inputCandidate?.sourceCandidateId || entry.inputCandidate?.id; }
  function financialChoices(entry) { return entry.financialChoices || entry.inputCandidate?.choices || { accounts: [], categories: [] }; }
  function financialInputOpen(entry) { return !entry.draft && !entry.financialClosed &&
    !['discarded','expired','failed','consumed'].includes(entry.inputCandidate?.status) &&
    Boolean(entry.inputCandidate || entry.financialCheck) && (!entry.turn?.preview || entry.turn.state === 'needs_input'); }
  function pendingTaskVisible(entry) {
    if (!entry.pendingTask || entry.pendingTaskClosed || entry.draft || entry.pending || entry.turn?.state === 'uncertain' || financialInputOpen(entry)) return false;
    if (entry.pendingTask.items.every(item => entry.pendingCompletedIndices?.includes(item.index))) return false;
    const index = messages.value.indexOf(entry);
    return entry.pendingTaskMember || !messages.value.slice(index + 1).some(item => item.pendingTask?.taskId === entry.pendingTask.taskId && !item.pendingTaskMember && !item.pendingTaskClosed);
  }
  function completePendingItem(taskId, itemIndex) {
    for (const item of messages.value) if (item.pendingTask?.taskId === taskId) {
      item.pendingCompletedIndices = [...new Set([...(item.pendingCompletedIndices || []), itemIndex])];
      item.pendingTaskClosed = item.pendingTask.items.every(member => item.pendingCompletedIndices.includes(member.index));
    }
  }
  async function recoverPendingTask(entry) {
    const id = conversationId.value, requestId = entry.requestId;
    if (!getAiPendingTransaction || !id || !currentEntry(entry, id) || entry.turn?.state !== 'failed') return;
    try {
      const result = await getAiPendingTransaction(id);
      if (!currentEntry(entry, id) || entry.requestId !== requestId || entry.turn?.state !== 'failed') return;
      entry.pendingTask = result.pendingTask || null; entry.pendingTaskUnavailable = false;
    } catch { /* The original failure and its read-only receipt controls remain available. */ }
  }
  async function resumePendingTask(entry, itemIndex) {
    const id = conversationId.value, task = entry.pendingTask;
    if (!task || !resumeAiPendingTransaction || !currentEntry(entry, id) || transferBusy.value || entry.turn?.state === 'uncertain') return;
    const run = beginCandidateAction(entry), current = () => candidateRun === run && currentEntry(entry, id) && entry.pendingTask?.taskId === task.taskId;
    entry.financialError = '';
    try {
      const result = await resumeAiPendingTransaction({ conversationId: id, taskId: task.taskId, revision: task.revision, itemIndex });
      if (!current()) return;
      let target = entry;
      if (task.items.length > 1 && !entry.pendingTaskMember) {
        const id = result.inputCandidate?.id || result.inputCandidate?.sourceCandidateId;
        target = messages.value.find(item => id && candidateId(item) === id) || messages.value.find(item => result.draft && item.draft?.id === result.draft.id);
        if (!target) {
          messages.value.push({ id: runtime.nextId(), role: 'assistant', text: '', pendingTaskMember: true,
            pendingTask: { ...(result.pendingTask || task), items: task.items.filter(item => item.index === itemIndex) } });
          target = messages.value[messages.value.length - 1];
        }
        entry.pendingTask = result.pendingTask || task;
      }
      applyFinancialFields(target, { ...result, ...(target.pendingTaskMember ? { pendingTask: { ...(result.pendingTask || task), items: task.items.filter(item => item.index === itemIndex) } } : {}) });
      target.pendingTaskUnavailable = false; target.pendingTaskClosed = false;
      target.financialClosed = Boolean(result.draft); target.financialChoices = result.choices || result.financialChoices || {};
      if (result.draft) { target.draft = result.draft; target.draftId = result.draft.id; completePendingItem(task.taskId, itemIndex); }
      else { target.inputCandidate = result.inputCandidate; target.localDraftStatus = ''; delete target.localDraftRequestId; }
      if (target.turn) { target.turn.state = result.draft ? 'completed' : 'needs_input'; target.turn.preview = null; }
      target.error = false; target.text = '';
    } catch (error) {
      if (current()) entry.financialError = error?.message || '待补状态尚未确认，请稍后重新读取。';
      if (current() && getAiPendingTransaction && ['AI_PENDING_CONFLICT', 'AI_PENDING_ENDED'].includes(error?.code)) {
        try {
          const latest = await getAiPendingTransaction(id);
          if (current()) {
            entry.pendingTask = latest.pendingTask?.taskId === task.taskId ? latest.pendingTask : null;
            entry.financialClosed = true; entry.financialCheck = null;
          }
        } catch { /* A rejected recovery never starts another POST. */ }
      }
    } finally { if (finishCandidateAction(run) && currentEntry(entry, id)) await bottom(); }
  }
  function enableFinancialCorrection(entry) { if (entry.turn) entry.turn.state = 'needs_input'; }
  function applyFinancialCheck(entry, result) {
    applyFinancialFields(entry, result);
    entry.inputCandidate = { ...(entry.inputCandidate || {}), ...(result.inputCandidate || {}), fields: result.fields };
    entry.financialChoices = result.choices; entry.financialError = '';
  }
  async function createFinancialDraft(entry, options) {
    const id = conversationId.value;
    if (!currentEntry(entry, id) || transferBusy.value || entry.localDraftStatus === 'uncertain' || !getAiFinancialGate(entry.financialCheck,Date.now(),entry.inputCandidate?.fields).ready) return;
    const run = beginCandidateAction(entry), current = () => candidateRun === run && currentEntry(entry, id);
    entry.financialError = '';
    if (entry.localDraftStatus === 'failed') delete entry.localDraftRequestId;
    entry.localDraftRequestId ||= createAiRequestId();
    try {
      const draft = await createAiTransactionDraft({ ...options, requestId: entry.localDraftRequestId,
        ...(conversationId.value ? { conversationId: conversationId.value } : {}) });
      if (!current()) return;
      entry.draft = draft; entry.draftId = draft.id; entry.financialClosed = true; entry.text = '';
      entry.localDraftStatus = 'completed';
      if (entry.pendingTask) completePendingItem(entry.pendingTask.taskId, entry.inputCandidate?.pendingTaskIndex ?? entry.pendingTask.items[0].index);
      if (draft.conversationId) conversationId.value = draft.conversationId;
      if (entry.turn) { entry.turn.state = 'completed'; entry.turn.preview = null; }
    } catch (error) { if (current()) { entry.localDraftStatus = 'uncertain'; entry.financialError = error?.message || '草稿尚未确认生成，请读取状态。不会再次请求 AI。'; } }
    finally { if (finishCandidateAction(run) && currentEntry(entry, id)) await bottom(); }
  }
  async function cancelFinancialCandidate(entry) {
    const id = conversationId.value;
    if (!currentEntry(entry, id) || transferBusy.value) return;
    const run = beginCandidateAction(entry), current = () => candidateRun === run && currentEntry(entry, id);
    entry.financialError = '';
    try {
      if (candidateId(entry)) await cancelAiTransactionCandidate(candidateId(entry));
      if (!current()) return;
      entry.financialClosed = true; entry.financialCheck = null; entry.inputCandidate = null; entry.text = '记账候选已取消。';
      if (entry.turn) { entry.turn.state = 'cancelled'; entry.turn.preview = null; }
    } catch (error) { if (current()) entry.financialError = error?.message || '取消未得到确认，核查卡仍保留。'; }
    finally { if (finishCandidateAction(run) && currentEntry(entry, id)) await bottom(); }
  }
  async function recoverFinancialCandidate(entry) {
    const id = conversationId.value;
    if (!currentEntry(entry, id) || transferBusy.value || (!candidateId(entry) && !entry.localDraftRequestId)) return;
    const run = beginCandidateAction(entry), current = () => candidateRun === run && currentEntry(entry, id);
    try {
      if (entry.localDraftRequestId) {
        const receipt = await getAiLocalDraftReceipt(entry.localDraftRequestId);
        if (!current()) return;
        if (receipt.result?.draft) {
          // A receipt is an old snapshot; current state prevents a committed/canceled card from reviving.
          const draft = await getAiDraft(receipt.result.draft.id);
          if (!current()) return;
          entry.draft = draft; entry.draftId = draft.id; entry.financialClosed = true;
          entry.localDraftStatus = 'completed'; entry.financialError = ''; entry.text = '';
          if (entry.turn) { entry.turn.state = 'completed'; entry.turn.preview = null; }
          return;
        }
        entry.localDraftStatus = receipt.status === 'failed' ? 'failed' : 'uncertain';
        if (entry.localDraftStatus === 'uncertain') { entry.financialError = '本次草稿尚未确认结束，请稍后手动读取状态；不会自动重发。'; return; }
      }
      if (!candidateId(entry)) { entry.financialError = '本次生成已停止，请核对输入后再明确生成。'; return; }
      const result = await getAiTransactionCandidate(candidateId(entry));
      if (!current()) return;
      applyFinancialFields(entry, result);
      if (result.draft) { entry.draft = result.draft; entry.draftId = result.draft.id; entry.text = ''; }
      else if (['discarded','expired','failed','consumed'].includes(result.inputCandidate?.status)) {
        entry.financialClosed = true; entry.financialCheck = null;
        entry.text = result.inputCandidate.status==='discarded'?'记账候选已取消。':'这份记账候选已结束，请重新核查。';
      }
      entry.financialError = '';
    } catch (error) { if (current()) entry.financialError = error?.message || '候选状态暂时无法读取。'; }
    finally { if (finishCandidateAction(run) && currentEntry(entry, id)) await bottom(); }
  }

  return { applyBusinessReply, applyFinancialFields, setCardWorking, updateProposal, updateDraft, invalidateArtifactActions, candidateId,
    financialChoices, financialInputOpen, enableFinancialCorrection, applyFinancialCheck,
    createFinancialDraft, cancelFinancialCandidate, recoverFinancialCandidate,
    pendingTaskVisible, recoverPendingTask, resumePendingTask };
}
