// One read-only subscription per visible conversation. Persisted message cursors
// and fresh entity/activity snapshots recover missed updates after reconnecting.
export function createConversationSync({ subscribe, pull, apply, deleted, onError = () => {}, isVisible = () => true,
  setTimer = setTimeout, clearTimer = clearTimeout, fallbackMs = 15000 } = {}) {
  let generation = 0, conversation = '', cursor = 0, controller, reconnectTimer, fallbackTimer, chain = Promise.resolve();
  let snapshotOrder = 0, appliedSnapshotOrder = 0;
  const current = (id, revision) => Boolean(id && id === conversation && revision === generation);
  const invalid = error => error?.kind === 'protocol' || error?.code === 'AI_RESPONSE_INVALID';
  function stopInvalid(error, id) { stop(); onError(error, id); }
  function stop() {
    generation++; conversation = ''; controller?.abort(); controller = null;
    clearTimer(reconnectTimer); clearTimer(fallbackTimer);
  }
  async function receive(state, id, revision, order = ++snapshotOrder) {
    const task = chain.catch(() => {}).then(async () => {
      if (!current(id, revision) || state.conversationId !== id) return;
      const context = { conversationId: id, isCurrent: () => current(id, revision) };
      // An earlier GET may arrive after a newer subscription snapshot. Replay
      // its persisted messages, but never roll live activities/entities/links back.
      const incoming = order < appliedSnapshotOrder ? { ...state, activities: [], retryLinks: [],
        entities: { drafts: [], proposals: [], candidates: [] } } : state;
      if (await apply(incoming, context) !== false && context.isCurrent()) {
        cursor = Math.max(cursor, state.lastId);
        appliedSnapshotOrder = Math.max(appliedSnapshotOrder, order);
      }
    });
    chain = task;
    return task;
  }
  async function refresh() {
    const id = conversation, revision = generation;
    if (!id || !isVisible()) return;
    const order = ++snapshotOrder;
    try { await receive(await pull(id, cursor, { signal: controller?.signal }), id, revision, order); }
    catch (error) {
      if (!current(id, revision)) return;
      if (error?.code === 'AI_CONVERSATION_EXPIRED') { stop(); deleted(id); }
      else if (invalid(error)) stopInvalid(error, id);
    }
  }
  function start(id, since = 0) {
    stop(); conversation = id; cursor = since; chain = Promise.resolve(); snapshotOrder = 0; appliedSnapshotOrder = 0;
    const revision = generation;
    controller = new AbortController();
    const signal = controller.signal;
    let failures = 0;
    const connect = async () => {
      if (!current(id, revision) || !isVisible()) return;
      try {
        await subscribe(id, cursor, { signal, onEvent: async ({ event, data }) => {
          if (!current(id, revision)) return;
          if (event === 'deleted') { stop(); deleted(id); }
          else if (event === 'sync') { await receive(data, id, revision); failures = 0; }
          else if (event === 'error') throw Object.assign(Error('同步连接暂不可用'), data);
        } });
      } catch (error) {
        if (!current(id, revision)) return;
        if (error?.code === 'AI_CONVERSATION_EXPIRED') { stop(); deleted(id); return; }
        if (invalid(error)) { stopInvalid(error, id); return; }
        failures++;
        await refresh();
      }
      if (current(id, revision)) reconnectTimer = setTimer(connect, Math.min(30000, 1000 * 2 ** Math.min(failures, 5)));
    };
    const fallback = async () => {
      await refresh();
      if (current(id, revision)) fallbackTimer = setTimer(fallback, fallbackMs);
    };
    fallbackTimer = setTimer(fallback, fallbackMs);
    void connect();
  }
  return { start, stop, refresh, get cursor() { return cursor; } };
}

const artifactId = entry => entry.draftId || entry.draft?.id || entry.proposalId || entry.proposal?.id || entry.inputCandidate?.sourceCandidateId || entry.inputCandidate?.id || entry.candidateId || '';
// User and assistant in one request are distinct; multiple artifact cards in one
// assistant message are distinct too. Text never serves as an identity.
export function mergeSyncMessages(existing, incoming, protect = () => false) {
  const result = [...existing];
  for (const next of incoming) {
    if (next.requestId && result.some(entry => entry.role === next.role &&
      entry.supersededRequestIds?.includes(next.requestId))) continue;
    const sameTurn = entry => entry.role === next.role &&
      (next.requestId && entry.requestId ? entry.requestId === next.requestId
        : next.messageId && entry.messageId === next.messageId);
    let index = result.findIndex(entry => sameTurn(entry) && artifactId(entry) === artifactId(next));
    if (index < 0 && next.supersededRequestIds?.length) index = result.findIndex(entry =>
      entry.role === next.role && next.supersededRequestIds.includes(entry.requestId));
    // The first final card occupies the remote activity bubble. Subsequent
    // artifacts keep their own identity, including when the frame is replayed.
    if (index < 0 && next.role === 'assistant') index = result.findIndex(entry => sameTurn(entry) &&
      !artifactId(entry) && !entry.messageId && (entry.pending || entry.remote));
    // Local candidate conversion also persists a standalone draft message.
    // Fold that empty confirmation into the existing card across request IDs.
    const confirmation = index < 0 && next.role === 'assistant' && artifactId(next) && !next.text;
    if (confirmation) index = result.findIndex(entry => entry.role === 'assistant' && artifactId(entry) === artifactId(next));
    if (index < 0) result.push({ ...next });
    else {
      const local = result[index];
      if (local.requestId === next.requestId && local._syncActivityStatus && next._syncActivityStatus) {
        const previousTime = Number(local._syncActivityUpdatedAt), nextTime = Number(next._syncActivityUpdatedAt);
        const rank = status => ({ queued: 0, running: 1, failed: 2, completed: 3 })[status] ?? -1;
        if (Number.isFinite(previousTime) && Number.isFinite(nextTime) &&
          (nextTime < previousTime || nextTime === previousTime && rank(next._syncActivityStatus) < rank(local._syncActivityStatus))) continue;
      }
      const preserved = {};
      if (local.requestId && next.requestId && local.requestId !== next.requestId && !confirmation)
        for (const key of ['messageId','createdAt','failure','authorizationRequest','authorizationDenied','lastError','providerDetail','receiptNotice','continuedBy']) delete local[key];
      const predecessorIds = [...new Set([...(local.supersededRequestIds || []), ...(next.supersededRequestIds || [])])];
      if (predecessorIds.length) preserved.supersededRequestIds = predecessorIds;
      // A history echo is stamped when saved. Keep the actual local send time,
      // including when a retry replaces its predecessor with a new request ID.
      if (local.sentAt) preserved.sentAt = local.sentAt;
      // A local stream owns the in-flight state until its final event/receipt.
      if (!local.remote && local.requestId === next.requestId && local.turn?.state === 'sending' && next.remote)
        for (const key of ['turn','pending','error','text','lastError','authorizationRequest','authorizationDenied']) preserved[key] = local[key];
      // The validated local receipt may contain a longer reply than older
      // persisted history. Confirm its identity without replacing that prose.
      if (!local.remote && local.requestId === next.requestId && local.turn?.state === 'completed' && next.remote &&
        !artifactId(local) && !artifactId(next))
        for (const key of ['text','turn','pending','error','lastError']) preserved[key] = local[key];
      if (confirmation) for (const key of ['text','requestId','messageId','createdAt','turn']) preserved[key] = local[key];
      if (protect(local)) for (const key of ['draft','draftId','draftNotice','proposal','proposalId','inputCandidate','financialCheck','financialChoices','financialClosed','pendingTask','pendingTaskClosed','pendingCompletedIndices']) preserved[key] = local[key];
      for (const key of ['draft','proposal']) if (local[key]?.version > (next[key]?.version || 0)) preserved[key] = local[key];
      // A server snapshot confirms a locally rendered result without deleting
      // its original reply, progress details, or image preview.
      Object.assign(local, next, preserved, { id: local.id,
        remote: !confirmation && local.requestId !== next.requestId ? next.remote === true : local.remote === true,
        ...(!local.remote && local.requestId === next.requestId && !local.pending && next.remote && next.pending ? { pending: false, text: local.text, turn: local.turn } : {}) });
      for (let other = result.length - 1; other >= 0; other--) if (result[other] !== local &&
        result[other].role === local.role && predecessorIds.includes(result[other].requestId)) result.splice(other, 1);
    }
  }
  return orderSyncTurns(result, existing);
}

// A sync frame can contain only the assistant half of a local request. Sort
// whole requests so its new messageId never moves the reply above its user.
// A stored messageId reflects append order, which can differ from send order
// after interrupted requests. Original send timestamps and stable positions
// keep earlier requests earlier even when their final receipt lands last.
function orderSyncTurns(messages, existing) {
  const previous = new Map();
  const turnKey = entry => entry.requestId ? `request:${entry.requestId}` : entry;
  for (const [index, entry] of existing.entries()) {
    const key = turnKey(entry);
    if (!previous.has(key)) previous.set(key, index);
  }
  const groups = new Map();
  for (const [index, entry] of messages.entries()) {
    const key = turnKey(entry);
    if (!groups.has(key)) groups.set(key, { entries: [], index, previous: previous.get(key) });
    groups.get(key).entries.push({ entry, index });
  }
  const ordered = [...groups.values()].map(group => {
    const timeOf = value => {
      const numeric = Number(value);
      return Number.isFinite(numeric) ? numeric : typeof value === 'string' ? Date.parse(value) || 0 : 0;
    };
    const times = group.entries.map(({ entry }) => timeOf(entry.sentAt || entry.createdAt)).filter(value => value > 0);
    return { ...group, time: times.length ? Math.min(...times) : 0 };
  });
  ordered.sort((a, b) => {
    if (a.time && b.time) return a.time - b.time || a.index - b.index;
    if (a.previous !== undefined && b.previous !== undefined) return a.previous - b.previous;
    return a.index - b.index;
  });
  return ordered.flatMap(group => group.entries.sort((a, b) => {
    if (a.entry.role !== b.entry.role) return a.entry.role === 'user' ? -1 : b.entry.role === 'user' ? 1 : 0;
    const left = Number(a.entry.messageId), right = Number(b.entry.messageId);
    return left && right ? left - right || a.index - b.index : a.index - b.index;
  }).map(({ entry }) => entry));
}
