<template>
  <div class="chat-page">
    <header class="chat-header">
      <router-link to="/finance" class="back-link" aria-label="返回账本"><van-icon name="arrow-left" /></router-link>
      <div class="chat-title"><span class="title-main"><strong>财务助手</strong><button type="button" class="mode-flag" aria-label="修改对话模式" @click="sessionSettingsOpen = true">{{ modeLabel }}</button></span><span class="provider-tag">{{ initializing ? '连接中' : providerName }}</span></div>
      <div class="header-actions"><button class="new-chat" :disabled="busy" aria-label="新对话" @click="newConversation"><van-icon name="plus" /><span class="btn-text">新对话</span></button><button class="history-button" aria-label="历史记录" @click="openHistory"><van-icon name="clock-o" /><span class="btn-text">历史</span></button><button class="settings-button" aria-label="设置" @click="sessionSettingsOpen = true"><van-icon name="setting-o" /></button></div>
    </header>

    <div ref="scrollArea" class="chat-scroll" role="log" aria-label="聊天记录" :aria-live="chatActivity ? 'off' : 'polite'" :aria-busy="chatActivity" @scroll="onScrollAreaScroll">
      <div class="chat-content">
        <section v-if="!messages.length" class="welcome">
          <div class="welcome-icon"><van-icon name="chat-o" /></div>
          <h1>聊聊你的账本</h1>
          <p>随口说一句，AI 会按需查找数据、整理归类。<br />信息不够会接着问，草稿由你核对后再入库。</p>
          <div class="suggestions"><button v-for="example in examples" :key="example" :disabled="!capability?.enabled || busy" @click="send(example)">{{ example }}<van-icon name="arrow" /></button></div>
        </section>
        <AssistantMessage v-for="item in messages" :key="item.id" :item="item"
          :busy="busy" :transfer-busy="transferBusy" :activity-clock="activityClock"
          :can-regenerate="canRegenerate(item)" :can-reask="canReask(item)"
          :conversation-id="conversationId" :financial-input-open="financialInputOpen(item)" :financial-choices="financialChoices(item)"
          :pending-task-visible="pendingTaskVisible(item)"
          @toggle-activities="toggleActivities" @preview-image="previewImage"
          @retry-turn="retryTurn" @recover-reply="recoverReply" @open-grant-dialog="openGrantDialog"
          @reject-authorization="rejectAuthorization" @regenerate="regenerate" @reask-turn="reaskTurn"
          @update-proposal="updateProposal" @update-draft="updateDraft" @card-working="setCardWorking"
          @apply-financial-check="applyFinancialCheck" @create-financial-draft="createFinancialDraft"
          @cancel-financial-candidate="cancelFinancialCandidate" @recover-financial-candidate="recoverFinancialCandidate"
          @resume-pending-task="resumePendingTask"
          @reveal-progress="revealProgress" @reveal-complete="revealComplete" />
      </div>
    </div>

    <p class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ replyAnnouncement }}</p>
    <AssistantComposer ref="composerRef" v-model="input" :capability="capability"
      :initializing="initializing" :connection-error="connectionError" :settings-notice="settingsNotice"
      :image-attachment="imageAttachment" :image-busy="imageBusy" :image-error="imageError"
      v-model:image-consent="imageConsent" v-model:image-consent-remembered="imageConsentRemembered"
      :provider-name="providerName" :busy="busy" :transfer-busy="transferBusy" :can-send="canSend"
      :streaming-active="streamingActive" :request-active="requestActive" :is-narrow="isNarrow"
      v-model:model-picker-open="modelPickerOpen" :model-label="modelLabel" :model-options="modelOptions"
      :model-loading="modelLoading" :model-error="modelError" :current-model="currentModel" :settings-busy="settingsBusy"
      :thinking-supported="thinkingSupported" :thinking-required="thinkingRequired" :thinking-enabled="thinkingEnabled"
      :web-search-available="webSearchAvailable" :web-search-enabled="webSearchEnabled"
      :jump-visible="pendingJumpVisible" :jump-pending="chatActivity"
      @send="send()" @stop="stopStream" @preview-image="previewImage" @clear-image="clearImage" @select-image="selectImage"
      @open-model-picker="openModelPicker" @switch-model="switchModel" @toggle-thinking="toggleThinking" @toggle-web-search="toggleWebSearch"
      @open-settings="openModelSettings" @jump="bottom" />
    <AiSessionSettingsDialog v-model:show="sessionSettingsOpen" :mode="aiMode" :busy="settingsUiBusy" :provider-label="providerName" :business-enabled="businessEnabled" :auth-granted="authGranted" :grant-expires-at="authGranted ? grant?.expiresAt ?? null : null" :thinking-enabled="thinkingEnabled" :thinking-supported="thinkingSupported" :thinking-required="thinkingRequired" :web-search-enabled="webSearchEnabled" :web-search-available="webSearchAvailable" @update:mode="aiMode = $event" @manage-auth="openGrantDialog()" @open-model-settings="openModelSettingsFromSession" @open-budget-settings="openBudgetSettingsFromSession" @open-personalization="openPersonalizationFromSession" @toggle-thinking="toggleThinking" />

    <AiAuthDialog :show="authDialogOpen" :busy="transferBusy" :error="authError" :access="pendingGrantEntry?.authorizationRequest?.access || (aiMode === 'operate' ? 'operate' : 'read')" :reason="pendingGrantEntry?.authorizationRequest?.reason" :mode="authDialogMode" @save="grantAuth" @revoke="revokeAuth" @reject="rejectAuthorization(pendingGrantEntry)" @close="authDialogOpen = false" />
    <AssistantHistory v-model:show="historyOpen" :is-narrow="isNarrow" :list="historyList"
      :loading="historyLoading" :error="historyError" :current-conversation-id="conversationId" :busy="busy"
      @new-conversation="newConversation" @resume="resumeConversation" @remove="removeConversation" />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import AiSessionSettingsDialog from '@/components/ai/AiSessionSettingsDialog.vue';
import AiAuthDialog from '@/components/ai/AiCapabilityAuthorizationDialog.vue';
import AssistantMessage from './assistant/components/AssistantMessage.vue';
import AssistantComposer from './assistant/components/AssistantComposer.vue';
import AssistantHistory from './assistant/components/AssistantHistory.vue';
import { assistantServices } from './assistant/assistantServices.js';
import { useAssistantController } from './assistant/useAssistantController.js';

defineOptions({ name: 'AiAssistant' });
const assistant = useAssistantController({ services: assistantServices, route: useRoute(), router: useRouter() });
const {
  capability, initializing, connectionError, messages, conversationId, input,
  busy, transferBusy, settingsUiBusy, businessEnabled, canSend, aiMode, modeLabel,
  sessionSettingsOpen, settingsBusy, settingsNotice,
  grant, authGranted, authDialogOpen, authDialogMode, authError, pendingGrantEntry,
  imageAttachment, imageConsent, imageConsentRemembered, imageBusy, imageError,
  streamingActive, requestActive, activityClock, scrollArea, composerRef, isNarrow,
  chatActivity, replyAnnouncement, pendingJumpVisible,
  providerName, currentModel, modelLabel, modelPickerOpen, modelOptions, modelLoading, modelError,
  thinkingEnabled, thinkingSupported, thinkingRequired, webSearchEnabled, webSearchAvailable, toggleWebSearch,
  historyOpen, historyList, historyLoading, historyError, examples,
  send, stopStream, canRegenerate, regenerate, canReask, reaskTurn, retryTurn, recoverReply,
  updateProposal, updateDraft, setCardWorking, financialInputOpen, financialChoices, applyFinancialCheck,
  createFinancialDraft, cancelFinancialCandidate, recoverFinancialCandidate,
  pendingTaskVisible, resumePendingTask,
  grantAuth, revokeAuth, openGrantDialog, rejectAuthorization,
  clearImage, selectImage, previewImage, toggleActivities, bottom, onScrollAreaScroll, revealProgress, revealComplete,
  openModelPicker, switchModel, toggleThinking, openModelSettings, openModelSettingsFromSession, openPersonalizationFromSession, openBudgetSettingsFromSession,
  settingsChanged, newConversation, openHistory, resumeConversation, removeConversation,
} = assistant;

onMounted(assistant.initialize);
onBeforeUnmount(assistant.dispose);
</script>

<style scoped>
.header-actions{margin-left:auto;display:flex;align-items:center;gap:6px;flex-shrink:0}.settings-button{display:grid;place-items:center;width:32px;height:32px;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:17px}.history-button{display:inline-flex;align-items:center;gap:4px;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:11px;padding:6px 9px}
.chat-page{--ai-body-size:14px;--ai-label-size:11px;--ai-title-size:13px;--ai-money-size:24px;--ai-card-padding:12px;--ai-card-gap:8px;--van-popover-background:var(--theme-bg-secondary);--van-popover-arrow-color:var(--theme-bg-secondary);--van-popover-radius:12px;height:100%;display:flex;flex-direction:column;overflow:hidden;color:var(--theme-text-primary);background:var(--theme-bg-primary)}
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}.chat-header{flex-shrink:0;display:flex;align-items:center;gap:8px;height:54px;padding:5px 14px;border-bottom:1px solid var(--theme-border)}
.back-link{position:relative;color:var(--theme-text-secondary);display:grid;place-items:center;width:30px;height:32px;font-size:19px}
.back-link::after{content:'';position:absolute;inset:-6px -8px;border-radius:10px}.chat-title{display:flex;align-items:center;gap:8px;min-width:0}.chat-title .title-main{display:inline-flex;align-items:center;gap:6px;min-width:0}.chat-title strong{font-size:15px}.chat-title .mode-flag{flex-shrink:0;font-size:11px;line-height:1;color:var(--van-primary-color);background:var(--van-primary-color-light);padding:3px 7px;border-radius:5px;white-space:nowrap;border:0;font:inherit;cursor:pointer}
.chat-title .mode-flag:hover{filter:brightness(1.12)}.chat-title .provider-tag{font-size:9px;color:var(--van-primary-color);background:var(--van-primary-color-light);padding:2px 6px;border-radius:5px;white-space:nowrap}.new-chat{border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:11px;padding:6px 9px}
.chat-scroll{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain}.chat-content{max-width:760px;margin:auto;padding:20px 16px 24px}.welcome{max-width:460px;margin:6vh auto 24px}.welcome-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:16px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:24px}.welcome h1{font-size:24px;letter-spacing:-1px;margin:16px 0 10px}.welcome p{font-size:13px;line-height:1.8;color:var(--theme-text-secondary);margin:0}.suggestions{display:grid;gap:8px;margin-top:18px}.suggestions button{display:flex;align-items:center;justify-content:space-between;text-align:left;padding:11px 13px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font-size:12px}
@media(max-width:600px){
  .chat-page{--ai-body-size:13px;--ai-title-size:12px;--ai-money-size:22px;--ai-card-padding:10px;--ai-card-gap:6px}
  .chat-header{height:auto;min-height:44px;padding:calc(env(safe-area-inset-top) + 5px) 10px 5px;gap:6px}
  .back-link{width:28px;flex-shrink:0}
  .chat-title{flex-direction:column;align-items:flex-start;flex-shrink:0;gap:0;white-space:nowrap}
  .chat-title strong{font-size:13px}
  .chat-title .title-main{gap:5px}
  .chat-title .mode-flag{font-size:10px;padding:2px 6px}
  .chat-title .provider-tag{font-size:9px;padding:2px 0;line-height:1.2}
  .header-actions{gap:4px;min-width:0}
  .btn-text{display:none}
  .history-button,.new-chat{width:34px;height:34px;justify-content:center;padding:0;font-size:18px;flex-shrink:0}
  .settings-button{width:34px;height:34px;flex-shrink:0}
  .chat-content{padding:12px 10px 16px}
  .welcome{margin:3vh auto 18px}
  .welcome h1{font-size:18px;letter-spacing:-.4px;margin:12px 0 6px}
  .welcome p{font-size:12px;line-height:1.6}
  .welcome-icon{width:36px;height:36px;border-radius:11px;font-size:18px}
  .suggestions{margin-top:12px;gap:6px}
  .suggestions button{min-height:40px;padding:8px 10px;border-radius:9px;font-size:12px;line-height:1.5}
  /* 2026-11-12：标题两行间距收紧——去掉此前为触控加的 min-height:28px（会把第一行撑高） */
  .provider-tag{margin-top:1px}
}
button:focus-visible,.back-link:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.suggestions .van-icon{color:var(--theme-text-secondary)}
</style>
