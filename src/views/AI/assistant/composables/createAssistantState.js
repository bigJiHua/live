import { ref, computed, watch } from 'vue';

const MODE_STORAGE_KEY = 'finance_ai_mode_v1';
function readMode() {
  try {
    const value = typeof localStorage === 'undefined' ? '' : localStorage.getItem(MODE_STORAGE_KEY);
    return ['chat', 'analysis', 'operate'].includes(value) ? value : 'analysis';
  } catch { return 'analysis'; }
}

// One state graph per page. Modules share these refs; they never create a second
// conversation, message list or busy gate. The controller owns its effect scope.
export function createAssistantState(services) {
  const defaults = {
    capability: null, initializing: true, connectionError: '',
    messages: [], conversationId: undefined, input: '', consent: false,
    imageAttachment: null, imageConsent: false, imageConsentRemembered: false,
    imageBusy: false, imageError: '',
    chatBusy: false, draftBusy: false, cardWork: {}, settingsBusy: false,
    grantBusy: false, historyLoading: false,
    sessionSettingsOpen: false, settingsNotice: '',
    grant: services.getCachedAiGrant(), grantClock: Date.now(),
    authDialogOpen: false, authDialogMode: 'create', authError: '', pendingGrantEntry: null,
    aiMode: readMode(), streamingActive: false, requestActive: false,
    scrollArea: null, composerRef: null, scrolledAway: false, isNarrow: false,
    replyAnnouncement: '', activityClock: Date.now(), replyReveals: {},
    profile: null, modelPickerOpen: false, modelOptions: [], modelLoading: false, modelError: '',
    thinkingEnabled: false, thinkingSupported: false, thinkingRequired: false,
    webSearchEnabled: false, webSearchAvailable: false,
    historyOpen: false, historyList: [], historyError: '',
  };
  const state = Object.fromEntries(Object.entries(defaults).map(([key, value]) => [key, ref(value)]));
  const { capability, messages, conversationId, grant, grantClock, input,
    initializing, chatBusy, draftBusy, cardWork, settingsBusy, grantBusy,
    historyLoading, imageBusy, imageAttachment, imageConsent, imageConsentRemembered,
    streamingActive, requestActive, aiMode, scrolledAway } = state;

  state.transferBusy = computed(() => initializing.value || chatBusy.value || draftBusy.value ||
    Object.values(cardWork.value).some(Boolean) || settingsBusy.value || grantBusy.value ||
    historyLoading.value || imageBusy.value);
  state.settingsUiBusy = computed(() => settingsBusy.value || grantBusy.value || historyLoading.value || imageBusy.value);
  state.busy = computed(() => state.transferBusy.value || messages.value.some(item => item.turn?.state === 'ready'));
  state.businessEnabled = computed(() => capability.value?.businessVersion === 3);
  state.authGranted = computed(() => state.businessEnabled.value && grant.value?.granted === true &&
    grant.value.providerHost === (capability.value?.provider || '') && grant.value.conversationId === conversationId.value &&
    (grant.value.expiresAt == null || grant.value.expiresAt > grantClock.value));
  state.providerLabel = computed(() => services.getAiProviderLabel(capability.value));
  state.modeLabel = computed(() => ({ chat: '闲聊', analysis: '分析', operate: '操作' }[aiMode.value] || aiMode.value));
  state.allowed = computed(() => capability.value?.enabled && state.businessEnabled.value && !state.busy.value &&
    (imageAttachment.value ? capability.value.ocrEnabled === true && (imageConsent.value || imageConsentRemembered.value) : true));
  state.canSend = computed(() => Boolean(state.allowed.value && (input.value.trim() || imageAttachment.value)));
  state.pendingJumpVisible = computed(() => scrolledAway.value);
  state.chatActivity = computed(() => streamingActive.value || requestActive.value
    || messages.value.some(item => item.pending || state.replyReveals.value[item.id]));
  watch(aiMode, value => {
    try { if (typeof localStorage !== 'undefined') localStorage.setItem(MODE_STORAGE_KEY, value); }
    catch { /* Storage may be unavailable in private mode. */ }
  });
  return state;
}
