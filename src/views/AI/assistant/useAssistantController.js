import { effectScope } from 'vue';
import { createAssistantState } from './composables/createAssistantState.js';
import { useAssistantProfile } from './composables/useAssistantProfile.js';
import { useAssistantImages } from './composables/useAssistantImages.js';
import { useAssistantActivities, STAGE_LABELS } from './composables/useAssistantActivities.js';
import { useAssistantViewport } from './composables/useAssistantViewport.js';
import { useAssistantArtifacts } from './composables/useAssistantArtifacts.js';
import { useAssistantAuthorization } from './composables/useAssistantAuthorization.js';
import { useAssistantTurn } from './composables/useAssistantTurn.js';
import { useAssistantJournal } from './composables/useAssistantJournal.js';
import { useAssistantSync } from './composables/useAssistantSync.js';
import { useAssistantConversation } from './composables/useAssistantConversation.js';

// The only composition root: modules import no sibling modules. Cross-feature
// callbacks are wired here, and all reactive effects have one disposal boundary.
export function useAssistantController({ services, route, router }) {
  const scope = effectScope(true);
  let active = true, initialized = false, sequence = 0;
  const runtime = { isActive: () => active, nextId: () => ++sequence };
  const adapters = { ...services, route, router, STAGE_LABELS };
  let state, profile, images, activities, viewport, artifacts, authorization, turn, journal, sync, conversation;
  function invalidateRequests() {
    turn.invalidateRequests();
    artifacts.invalidateArtifactActions();
    activities.stopActivityTicker();
  }
  scope.run(() => {
    state = createAssistantState(adapters);
    const options = actions => ({ state, services: adapters, runtime, actions });
    viewport = useAssistantViewport(options({}));
    images = useAssistantImages(options({}));
    profile = useAssistantProfile(options({}));
    activities = useAssistantActivities(options({
      hasFocusEntry: viewport.hasFocusEntry, tryFocusSent: viewport.tryFocusSent,
    }));
    artifacts = useAssistantArtifacts(options({
      bottom: viewport.bottom, finishActivities: activities.finishActivities,
      finishTurn: (...args) => journal.finishTurn(...args),
    }));
    authorization = useAssistantAuthorization(options({
      retryTurn: (...args) => turn.retryTurn(...args), finishTurn: (...args) => journal.finishTurn(...args),
    }));
    turn = useAssistantTurn(options({
      clearImage: images.clearImage, resizeInput: viewport.resizeInput,
      setFocusEntry: viewport.setFocusEntry, hasFocusEntry: viewport.hasFocusEntry,
      tryFocusSent: viewport.tryFocusSent, nearBottom: viewport.nearBottom, bottom: viewport.bottom,
      journalTurn: (...args) => journal.journalTurn(...args),
      settleTurn: (...args) => journal.settleTurn(...args), finishTurn: (...args) => journal.finishTurn(...args),
      applyFinancialFields: artifacts.applyFinancialFields, applyBusinessReply: artifacts.applyBusinessReply,
      recoverPendingTask: artifacts.recoverPendingTask,
      pushActivity: activities.pushActivity, trackStage: activities.trackStage,
      trackTool: activities.trackTool, trackReasoning: activities.trackReasoning,
      trackReasoningStatus: activities.trackReasoningStatus, sealReasoningStatus: activities.sealReasoningStatus,
      trackReasoningDelta: activities.trackReasoningDelta, clearLiveReasoning: activities.clearLiveReasoning,
      sealActivities: activities.sealActivities, openGrantDialog: authorization.openGrantDialog,
    }));
    journal = useAssistantJournal(options({
      stopSyncPolling: (...args) => sync.stopSyncPolling(...args),
      startSyncPolling: (...args) => sync.startSyncPolling(...args), refreshGrant: authorization.refreshGrant,
      resizeInput: viewport.resizeInput, bottom: viewport.bottom,
      applyReceipt: turn.applyReceipt, startReceiptPoll: turn.startReceiptPoll,
    }));
    sync = useAssistantSync(options({
      restoreBusinessHistory: journal.restoreBusinessHistory, nearBottom: viewport.nearBottom, bottom: viewport.bottom,
      invalidateRequests, stopAllPolling: turn.stopAllPolling, stopReceiptPoll: turn.stopReceiptPoll,
      settleSyncedTurn: turn.settleSyncedTurn,
      startReceiptPoll: turn.startReceiptPoll, settleTurn: journal.settleTurn,
      clearImage: images.clearImage, releaseImages: images.releaseImages,
    }));
    conversation = useAssistantConversation(options({
      loadComposerProfile: profile.loadComposerProfile,
      requestedConversationId: journal.requestedConversationId,
      restoreBusinessHistory: journal.restoreBusinessHistory, appendJournal: journal.appendJournal,
      discardDraft: journal.discardDraft,
      bottom: viewport.bottom, resizeInput: viewport.resizeInput,
      clearImage: images.clearImage, releaseImages: images.releaseImages,
      stopAllPolling: turn.stopAllPolling, stopSyncPolling: sync.stopSyncPolling,
      invalidateRequests,
      startSyncPolling: sync.startSyncPolling, seedSyncCursor: sync.seedSyncCursor, resetSyncCursor: sync.resetSyncCursor,
    }));
    // Draft restoration must be active before capability loading changes identity.
    journal.initialize();
    sync.initialize();
  });

  async function initialize() {
    if (!active || initialized) return;
    initialized = true;
    viewport.initialize();
    return conversation.initialize();
  }
  function dispose() {
    if (!active) return;
    active = false;
    artifacts.invalidateArtifactActions();
    conversation.dispose(); turn.dispose(); sync.dispose(); authorization.dispose();
    images.dispose(); activities.dispose(); viewport.dispose(); journal.dispose();
    scope.stop();
  }
  return {
    ...state, ...profile, ...images, ...activities, ...viewport,
    ...artifacts, ...authorization, ...turn, ...journal, ...sync, ...conversation,
    examples: ['今天去赵一鸣买了8.5的零食', '这个月的钱主要花在哪？', '明天下午三点提醒我拿快递'],
    initialize, dispose,
  };
}
