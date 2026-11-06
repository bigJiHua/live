<template>
  <div class="chat-page">
    <header class="chat-header">
      <router-link to="/finance" class="back-link" aria-label="返回账本"><van-icon name="arrow-left" /></router-link>
      <div class="chat-title"><strong>财务助手</strong><span>{{ initializing ? '连接中' : providerLabel }}</span></div>
      <div class="header-actions"><button class="history-button" aria-label="历史记录" :disabled="busy" @click="openHistory"><van-icon name="clock-o" /> 历史</button><button class="settings-button" aria-label="连接设置" :disabled="busy" @click="settingsOpen = true"><van-icon name="setting-o" /></button><button class="new-chat" :disabled="busy" @click="newConversation"><van-icon name="plus" /> 新对话</button></div>
    </header>

    <div ref="scrollArea" class="chat-scroll" role="log" aria-label="聊天记录" aria-live="polite">
      <div class="chat-content">
        <section v-if="!messages.length" class="welcome">
          <div class="welcome-icon"><van-icon name="chat-o" /></div>
          <h1>聊聊你的账本</h1>
          <p>查收支，或说说刚发生的一笔交易。<br />记账草稿会出现在聊天里，供你修改和核对。</p>
          <div class="suggestions"><button v-for="example in examples" :key="example" :disabled="!capability?.enabled || busy" @click="send(example)">{{ example }}<van-icon name="arrow" /></button></div>
        </section>
        <article v-for="item in messages" :key="item.id" class="chat-message" :class="[item.role, { 'card-only': (item.draft || item.ocr) && !item.pending && !item.error }]">
          <span v-if="item.role === 'assistant'" class="avatar" aria-hidden="true"><van-icon name="chat-o" /></span>
          <div class="message-body">
            <span v-if="(!item.draft && !item.ocr) || item.pending || item.error" class="speaker">{{ item.role === 'user' ? '你' : '财务助手' }}</span>
            <button v-if="item.imagePreview" type="button" class="chat-image-button" aria-label="查看已发送图片" @click="previewImage(item.imagePreview)"><img :src="item.imagePreview" class="chat-image" alt="发送的支付截图或账单图片" /></button>
            <div v-if="item.pending" class="thinking" role="status" aria-label="正在回复"><i /><i /><i /></div>
            <p v-else-if="item.text && ((!item.draft && !item.ocr) || item.error)" class="message-text" :class="{ 'message-error': item.error }">{{ item.text }}</p>
            <p v-if="item.draftNotice" class="draft-notice" role="status">{{ item.draftNotice }}</p>
            <button v-if="item.error" class="retry" :disabled="busy" @click="retry(item)">{{ item.requestImage ? '重试图片识别' : '重试这条消息' }}</button>
            <p v-if="item.error && item.requestImage" class="image-retry-note">重试会再次将这张图片发送至 AI 服务进行识别。</p>
            <AiEvidenceCard v-for="(evidence, index) in item.evidence || []" :key="index" :evidence="evidence" />
            <AiDraftCard v-if="item.draft" :draft="item.draft" :disabled="busy" @change="updateDraft" @working="draftBusy = $event" />
            <AiOcrCard v-if="item.ocr" :ocr="item.ocr" :has-draft="Boolean(item.draft)" />
          </div>
        </article>
      </div>
    </div>

    <footer class="composer-area">
      <div class="composer-inner">
        <p v-if="initializing" class="connection-state" role="status">正在连接助手…</p>
        <div v-else-if="!capability?.enabled" class="connection-state" role="alert">{{ capability?.message || connectionError || 'AI 尚未启用' }} <button @click="settingsOpen = true">连接设置</button></div>
        <p v-if="settingsNotice" class="connection-state" role="status">{{ settingsNotice }}</p>
        <div v-if="imageAttachment" class="attachment-preview">
          <button type="button" class="preview-image-button" aria-label="查看待发送图片" @click="previewImage(imageAttachment.dataUrl)"><img :src="imageAttachment.dataUrl" alt="待发送图片预览" /></button>
          <div><strong>{{ imageAttachment.name }}</strong><span>请先确认图片清晰，并遮挡无需识别的个人信息。</span></div>
          <button type="button" :disabled="busy" aria-label="移除图片" @click="clearImage"><van-icon name="cross" /></button>
        </div>
        <p v-if="imageBusy" class="connection-state" role="status">正在处理图片…</p>
        <p v-if="imageError" class="image-error" role="alert">{{ imageError }}</p>
        <label v-if="imageAttachment" class="consent"><input v-model="imageConsent" type="checkbox" :disabled="busy" /><span>同意将这张图片及说明发送至 {{ providerLabel }}，用于识别。</span></label>
        <label v-else-if="capability?.requiresConsent" class="consent"><input v-model="consent" type="checkbox" :disabled="busy" /><span>同意将对话和必要的查询数据发送至 {{ providerLabel }}。</span></label>
        <form class="composer" @submit.prevent="send()">
          <input ref="imageFileInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="selectImage" />
          <button v-if="capability?.ocrEnabled" type="button" class="attach-button" :disabled="busy || !capability?.enabled" aria-label="上传支付截图或账单图片" @click="imageFileInput?.click()"><van-icon name="photo-o" /></button>
          <textarea ref="inputArea" v-model="input" aria-label="消息" rows="1" maxlength="1500" :placeholder="imageAttachment ? '可补充这张图片的说明…' : '发消息，或说说这笔收支…'" :disabled="!capability?.enabled || busy" @input="resizeInput" @keydown="onKeydown" />
          <button type="submit" class="send-button" :disabled="!canSend" aria-label="发送消息"><van-icon :name="busy ? 'ellipsis' : 'arrow-up'" /></button>
        </form>
        <p class="composer-note"><span class="keyboard-hint">Enter 发送 · Shift + Enter 换行</span></p>
      </div>
    </footer>
    <AiSettingsDialog v-model:show="settingsOpen" :data-source="capability?.dataSource" @configured="settingsChanged" @working="settingsBusy = $event" />
    <van-popup v-model:show="historyOpen" position="bottom" round teleport="body" :style="{ maxHeight: '70%' }">
      <div class="history-sheet">
        <div class="history-head"><strong>历史对话</strong><button aria-label="关闭历史" @click="historyOpen = false"><van-icon name="cross" /></button></div>
        <p v-if="historyLoading" class="history-note" role="status">加载中…</p>
        <p v-else-if="historyError" class="history-note" role="alert">{{ historyError }}</p>
        <p v-else-if="!historyList.length" class="history-note">还没有历史对话。</p>
        <ul v-else class="history-list">
          <li v-for="item in historyList" :key="item.id">
            <button class="history-item" @click="resumeConversation(item)">
              <span class="history-title">{{ item.title }}</span>
              <span class="history-meta">{{ item.messageCount }} 条 · {{ shortTime(item.updatedAt) }}</span>
            </button>
            <button class="history-del" aria-label="删除对话" @click="removeConversation(item)"><van-icon name="delete-o" /></button>
          </li>
        </ul>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { showConfirmDialog, showImagePreview } from 'vant';
import { getAiCapabilities, askAi, recognizeAiImage, listAiConversations, getAiConversationMessages, deleteAiConversation, getAiDraft } from '@/utils/api/ai';
import { getAiProviderLabel } from '@/utils/aiContract';
import { restoreAiHistory } from '@/utils/aiHistory';
import { applyAiReply } from '@/utils/aiMessages';
import { prepareAiImage } from '@/utils/aiImage';
import AiDraftCard from '@/components/ai/AiDraftCard.vue';
import AiEvidenceCard from '@/components/ai/AiEvidenceCard.vue';
import AiSettingsDialog from '@/components/ai/AiSettingsDialog.vue';
import AiOcrCard from '@/components/ai/AiOcrCard.vue';

defineOptions({ name: 'AiAssistant' });
const capability = ref(null);
const initializing = ref(true);
const connectionError = ref('');
const input = ref('');
const consent = ref(false);
const imageAttachment = ref(null);
const imageConsent = ref(false);
const imageBusy = ref(false);
const imageError = ref('');
const imageFileInput = ref(null);
const chatBusy = ref(false);
const draftBusy = ref(false);
const settingsBusy = ref(false);
const historyLoading = ref(false);
const settingsOpen = ref(false);
const settingsNotice = ref('');
const busy = computed(() => chatBusy.value || draftBusy.value || settingsBusy.value || historyLoading.value || imageBusy.value);
const providerLabel = computed(() => getAiProviderLabel(capability.value));
const messages = ref([]);
const conversationId = ref();
const scrollArea = ref(null);
const inputArea = ref(null);
let sequence = 0;
const examples = ['这个月的收支情况怎么样？', '昨天午饭花了32元，用支付宝付的', '查看本月消费明细'];
const allowed = computed(() => capability.value?.enabled && !busy.value &&
  (imageAttachment.value ? capability.value.ocrEnabled === true && imageConsent.value : !capability.value.requiresConsent || consent.value));
const canSend = computed(() => allowed.value && (input.value.trim() || imageAttachment.value));
async function bottom() { await nextTick(); if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight; }
function resizeInput() { const el = inputArea.value; if (el) { el.style.height = 'auto'; el.style.height = `${Math.min(el.scrollHeight, 144)}px`; } }
function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) { event.preventDefault(); send(); }
}
function clearImage() {
  imageAttachment.value = null; imageConsent.value = false; imageError.value = '';
  if (imageFileInput.value) imageFileInput.value.value = '';
}
function previewImage(dataUrl) { showImagePreview({ images: [dataUrl], closeable: true }); }
async function selectImage(event) {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file || busy.value || capability.value?.ocrEnabled !== true) return;
  clearImage(); imageBusy.value = true;
  try { imageAttachment.value = await prepareAiImage(file); }
  catch (error) { imageError.value = error?.message || '无法处理图片，请换一张清晰截图'; }
  finally { imageBusy.value = false; }
}
async function loadCapabilities() {
  initializing.value = true;
  connectionError.value = '';
  try { capability.value = await getAiCapabilities(); }
  catch (error) { capability.value = null; connectionError.value = error?.message || '助手连接失败'; }
  finally { initializing.value = false; }
}
async function settingsChanged(notice) {
  messages.value = []; conversationId.value = undefined; input.value = ''; consent.value = false;
  clearImage();
  settingsNotice.value = notice;
  await loadCapabilities(); nextTick(resizeInput);
}
function newConversation() {
  if (busy.value) return;
  messages.value = []; conversationId.value = undefined; consent.value = false; input.value = '';
  clearImage();
  settingsNotice.value = '';
  nextTick(resizeInput);
}
async function requestReply(text, entry, image = null) {
  chatBusy.value = true;
  const requestConsent = image ? true : consent.value;
  await bottom();
  try {
    const options = { message: text, conversationId: conversationId.value, consent: requestConsent, connectionId: capability.value.connectionId };
    const result = image ? await recognizeAiImage({ ...options, image: { dataUrl: image.dataUrl } }) : await askAi(options);
    conversationId.value = result.conversationId;
    messages.value = applyAiReply(messages.value, entry, result);
  } catch (error) {
    Object.assign(entry, { pending: false, error: true, text: error?.message || '这条消息暂时没有发送成功', requestText: text, requestImage: image });
  } finally { chatBusy.value = false; await bottom(); }
}
async function send(example) {
  const text = (typeof example === 'string' ? example : input.value).trim();
  const image = imageAttachment.value;
  if (!allowed.value || (!text && !image)) return;
  settingsNotice.value = '';
  messages.value.push({ id: ++sequence, role: 'user', text: text || '识别这张图片', ...(image ? { imagePreview: image.dataUrl } : {}) });
  messages.value.push({ id: ++sequence, role: 'assistant', pending: true });
  const entry = messages.value[messages.value.length - 1];
  input.value = ''; clearImage(); nextTick(resizeInput);
  await requestReply(text, entry, image);
}
async function retry(entry) {
  if (busy.value || !capability.value?.enabled || (entry.requestImage ? capability.value.ocrEnabled !== true :
      capability.value.requiresConsent && !consent.value)) return;
  // A failed earlier turn must be retried before adding later turns to the server context.
  if (entry !== messages.value[messages.value.length - 1]) {
    input.value = entry.requestText || '';
    if (entry.requestImage) { imageAttachment.value = entry.requestImage; imageConsent.value = false; imageError.value = ''; }
    else clearImage();
    nextTick(resizeInput); return;
  }
  entry.pending = true; entry.error = false;
  await requestReply(entry.requestText, entry, entry.requestImage);
}
function updateDraft(draft) {
  for (const item of messages.value) if (item.draft?.id === draft.id) item.draft = draft;
}
// ===== 追加163 扩展：历史对话（列表 / 恢复 / 删除）=====
const historyOpen = ref(false);
const historyList = ref([]);
const historyError = ref('');
async function openHistory() {
  if (busy.value) return;
  historyOpen.value = true; historyLoading.value = true; historyError.value = '';
  try { historyList.value = await listAiConversations(); }
  catch (error) { historyError.value = error?.message || '历史对话读取失败，请稍后重试'; }
  finally { historyLoading.value = false; }
}
function shortTime(value) {
  const d = new Date(value);
  return isNaN(d) ? '' : `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
// 恢复：把历史消息灌回聊天视图并绑定 conversationId（后端按库重建上下文，跨重启可续聊）
async function resumeConversation(item) {
  if (busy.value) return;
  historyLoading.value = true; historyError.value = '';
  try {
    const list = await getAiConversationMessages(item.id);
    const restored = await restoreAiHistory(list, getAiDraft);
    messages.value = restored.map(entry => ({ ...entry, id: ++sequence }));
    conversationId.value = item.id;
    clearImage();
    consent.value = capability.value?.requiresConsent ? consent.value : false;
    historyOpen.value = false;
    nextTick(() => { bottom(); resizeInput(); });
  } catch (error) { historyError.value = error?.message || '无法恢复这段对话，请稍后重试'; }
  finally { historyLoading.value = false; }
}
async function removeConversation(item) {
  try {
    await showConfirmDialog({ title: '删除对话', message: `删除「${item.title}」？不可恢复。`, confirmButtonText: '删除', cancelButtonText: '取消' });
  } catch { return; }
  try {
    await deleteAiConversation(item.id);
    historyList.value = historyList.value.filter(entry => entry.id !== item.id);
    if (conversationId.value === item.id) { conversationId.value = undefined; messages.value = []; clearImage(); }
  } catch { /* 删除失败保留列表 */ }
}
onMounted(loadCapabilities);
</script>

<style scoped>
.attach-button{flex-shrink:0;display:grid;place-items:center;width:34px;height:36px;border:0;background:transparent;color:var(--theme-text-secondary);font-size:22px}.attachment-preview{display:flex;align-items:center;gap:12px;padding:10px 12px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);margin-bottom:10px}.preview-image-button{flex-shrink:0;padding:0;border:0;background:none;cursor:zoom-in}.attachment-preview img{display:block;width:60px;height:72px;object-fit:contain;border-radius:6px}.attachment-preview div{min-width:0;flex:1}.attachment-preview strong{display:block;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.attachment-preview span{display:block;font-size:11px;line-height:1.6;color:var(--theme-text-secondary);margin-top:5px}.attachment-preview>button:last-child{padding:7px;border:0;background:none;color:var(--theme-text-secondary)}.chat-image-button{display:block;padding:0;border:0;background:none;cursor:zoom-in;margin-bottom:8px;max-width:100%}.chat-image{display:block;max-width:min(100%,320px);max-height:360px;width:auto;height:auto;object-fit:contain;border-radius:12px;border:1px solid var(--theme-border)}.image-error{font-size:12px;line-height:1.6;color:var(--van-danger-color);margin:0 0 10px}.image-retry-note{font-size:10px;line-height:1.6;color:var(--theme-text-secondary);margin:0 0 10px}
.card-only .message-body :deep(.draft-card){margin-top:0}.card-only .message-body :deep(.evidence-card + .draft-card){margin-top:16px}
.draft-notice{margin:0 0 10px;font-size:12px;line-height:1.7;color:var(--theme-text-secondary)}
.header-actions{margin-left:auto;display:flex;align-items:center;gap:8px;flex-shrink:0}.settings-button{display:grid;place-items:center;width:34px;height:34px;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:18px}.history-button{display:inline-flex;align-items:center;gap:4px;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:12px;padding:8px 10px}
.history-sheet{padding:16px 16px max(12px,env(safe-area-inset-bottom))}.history-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.history-head strong{font-size:15px}.history-head button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:16px;cursor:pointer}.history-note{font-size:12px;color:var(--theme-text-secondary);text-align:center;padding:20px 0;margin:0}.history-list{list-style:none;margin:0;padding:0;max-height:52vh;overflow-y:auto}.history-list li{display:flex;align-items:center;gap:6px;border-bottom:1px solid var(--theme-border)}.history-item{flex:1;min-width:0;display:flex;flex-direction:column;gap:3px;padding:12px 4px;border:0;background:none;color:var(--theme-text-primary);font:inherit;text-align:left;cursor:pointer}.history-title{font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-meta{font-size:10px;color:var(--theme-text-secondary)}.history-del{border:0;background:none;color:var(--theme-text-secondary);padding:10px;font-size:17px;cursor:pointer;flex-shrink:0}
.chat-page{height:100%;display:flex;flex-direction:column;overflow:hidden;color:var(--theme-text-primary);background:var(--theme-bg-primary)}
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}.chat-header{flex-shrink:0;display:flex;align-items:center;gap:12px;height:64px;padding:0 24px;border-bottom:1px solid var(--theme-border)}
.back-link{color:var(--theme-text-secondary);display:grid;place-items:center;width:32px;height:36px;font-size:20px}.chat-title{display:flex;align-items:center;gap:10px}.chat-title strong{font-size:16px}.chat-title span{font-size:10px;color:var(--van-primary-color);background:var(--van-primary-color-light);padding:3px 7px;border-radius:5px}.new-chat{margin-left:auto;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:12px;padding:8px 10px}
.chat-scroll{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain}.chat-content{max-width:760px;margin:auto;padding:28px 24px 32px}.welcome{max-width:460px;margin:8vh auto 30px}.welcome-icon{display:grid;place-items:center;width:54px;height:54px;border-radius:18px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:28px}.welcome h1{font-size:28px;letter-spacing:-1px;margin:22px 0 12px}.welcome p{font-size:14px;line-height:1.9;color:var(--theme-text-secondary);margin:0}.suggestions{display:grid;gap:10px;margin-top:28px}.suggestions button{display:flex;align-items:center;justify-content:space-between;text-align:left;padding:15px 16px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font-size:13px}.suggestions .van-icon{color:var(--theme-text-secondary)}
.chat-message{display:flex;align-items:flex-start;gap:12px;margin-bottom:28px}.avatar{flex-shrink:0;display:grid;place-items:center;width:30px;height:30px;border-radius:10px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:18px}.message-body{min-width:0;flex:1}.speaker{display:block;font-size:11px;color:var(--theme-text-secondary);margin:2px 0 8px}.message-text{margin:0;font-size:14px;line-height:1.85;white-space:pre-wrap;overflow-wrap:anywhere}.chat-message.user{justify-content:flex-end}.user .message-body{flex:0 1 auto;max-width:85%}.user .speaker{text-align:right}.user .message-text{padding:12px 16px;border-radius:16px 16px 4px 16px;background:var(--van-primary-color-light)}.message-error{color:var(--van-danger-color)}.retry{background:none;border:0;padding:8px 0;color:var(--van-primary-color);font-size:12px}.thinking{display:flex;gap:5px;padding:10px 0}.thinking i{width:6px;height:6px;background:var(--theme-text-secondary);border-radius:50%;animation:pulse 1.1s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}@keyframes pulse{50%{opacity:.25;transform:translateY(-3px)}}
.composer-area{flex-shrink:0;border-top:1px solid var(--theme-border);background:var(--theme-bg-primary);padding:16px 24px max(10px,env(safe-area-inset-bottom))}.composer-inner{max-width:712px;margin:auto}.composer{display:flex;align-items:flex-end;gap:10px;padding:10px 10px 10px 16px;border:1px solid var(--theme-border);border-radius:18px;background:var(--theme-bg-secondary);box-shadow:0 3px 14px #00000004}.composer:focus-within{border-color:var(--van-primary-color)}textarea{width:100%;min-width:0;min-height:36px;max-height:144px;box-sizing:border-box;padding:6px 0;border:0;background:none;resize:none;outline:none;color:inherit;font:inherit;font-size:14px;line-height:24px}.send-button{flex-shrink:0;display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:11px;background:var(--van-primary-color);color:white;font-size:20px}.composer-note{font-size:10px;color:var(--theme-text-secondary);margin:9px 4px 0;line-height:1.5}.keyboard-hint{float:right}.connection-state{font-size:12px;line-height:1.6;color:var(--theme-text-secondary);margin:0 0 10px}.connection-state button{border:0;background:none;color:var(--van-primary-color)}.consent{display:flex;align-items:flex-start;gap:7px;font-size:11px;color:var(--theme-text-secondary);margin-bottom:10px;line-height:1.6}.consent input{accent-color:var(--van-primary-color);margin:2px 0 0}
@media(max-width:600px){.chat-header{height:56px;padding:0 12px;gap:6px}.back-link{width:24px;flex-shrink:0}.chat-title{flex-direction:column;align-items:flex-start;flex-shrink:0;gap:2px;white-space:nowrap}.chat-title strong{font-size:14px}.chat-title span{font-size:9px;padding:2px 5px;line-height:1.2}.header-actions{gap:6px}.header-actions button{white-space:nowrap}.history-button,.new-chat{padding:8px}.chat-content{padding:24px 14px}.welcome{margin-top:6vh}.welcome h1{font-size:25px}.chat-message{gap:9px;margin-bottom:24px}.avatar{width:26px;height:26px;font-size:16px}.composer-area{padding:12px 12px max(10px,env(safe-area-inset-bottom))}.keyboard-hint{display:none}.user .message-body{max-width:90%}}
@media(prefers-reduced-motion:reduce){.thinking i{animation:none}}
</style>
