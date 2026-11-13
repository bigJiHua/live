// Production adapters are kept outside the controller so tests inject services
// while importing the same state and feature modules used by the page.
import { showConfirmDialog, showImagePreview } from 'vant';
import { getAiCapabilities, recognizeAiImage, listAiConversations, getAiConversationMessages,
  getAiConversationSync, subscribeAiConversation, deleteAiConversation, getAiDraft,
  getAiBusinessReceipt, getAiBusinessSession, createAiBusinessSession, getAiProposal,
  createAiTransactionDraft, cancelAiTransactionCandidate, getAiTransactionCandidate, getAiLocalDraftReceipt,
  getAiPendingTransaction, resumeAiPendingTransaction,
  getAiGrant, putAiGrant, deleteAiGrant, streamAiBusiness,
  getAiSettings, configureAi, fetchAiModels, getAiPreferences, putAiPreference } from '@/utils/api/ai';
import { getAiProviderLabel } from '@/utils/aiContract';
import { getCachedAiGrant, setCachedAiGrant, clearCachedAiGrant } from '@/utils/aiAuth';
import { restoreAiBusinessHistory } from '@/utils/aiBusinessHistory';
import { applyAiReply } from '@/utils/aiMessages';
import { createConversationSync, mergeSyncMessages } from '@/utils/aiConversationSync';
import { prepareAiImage } from '@/utils/aiImage';
import { validateAiAuthorizationRequest } from '@/utils/aiBusinessContract';
import { getAiFinancialGate } from '@/utils/aiFinancialCheck';
import { createAiRequestId, createReceiptPoll } from '@/utils/aiTurn';
import { readDraft, saveDraft, readTurns, addTurn, updateTurn, dropTurn, clearConversation,
  readLastConversation, saveLastConversation, clearLastConversation } from '@/utils/aiChatJournal';

export const assistantServices = {
  showConfirmDialog, showImagePreview, getAiCapabilities, recognizeAiImage,
  listAiConversations, getAiConversationMessages, getAiConversationSync, subscribeAiConversation,
  deleteAiConversation, getAiDraft, getAiBusinessReceipt, getAiBusinessSession, createAiBusinessSession,
  getAiProposal, createAiTransactionDraft, cancelAiTransactionCandidate, getAiTransactionCandidate,
  getAiLocalDraftReceipt, getAiGrant, putAiGrant, deleteAiGrant, streamAiBusiness,
  getAiPendingTransaction, resumeAiPendingTransaction,
  getAiSettings, configureAi, fetchAiModels, getAiPreferences, putAiPreference,
  getAiProviderLabel, getCachedAiGrant, setCachedAiGrant, clearCachedAiGrant,
  restoreAiBusinessHistory, applyAiReply, createConversationSync, mergeSyncMessages, prepareAiImage,
  validateAiAuthorizationRequest, getAiFinancialGate, createAiRequestId, createReceiptPoll,
  readDraft, saveDraft, readTurns, addTurn, updateTurn, dropTurn, clearConversation,
  readLastConversation, saveLastConversation, clearLastConversation,
};
