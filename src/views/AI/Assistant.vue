<template>
  <div class="chat-page">
    <header class="chat-header">
      <router-link to="/finance" class="back-link" aria-label="返回账本"><van-icon name="arrow-left" /></router-link>
      <div class="chat-title"><strong>财务助手</strong><span>{{ initializing ? '连接中' : capability?.mode === 'mock' ? '模拟测试' : capability?.mode === 'deepseek' ? 'DeepSeek' : '未启用' }}</span></div>
      <button class="new-chat" :disabled="busy" @click="newConversation"><van-icon name="plus" /> 新对话</button>
    </header>

    <div ref="scrollArea" class="chat-scroll" role="log" aria-label="聊天记录" aria-live="polite">
      <div class="chat-content">
        <section v-if="!messages.length" class="welcome">
          <div class="welcome-icon"><van-icon name="chat-o" /></div>
          <h1>聊聊你的账本</h1>
          <p>查收支，或说说刚发生的一笔交易。<br />记账草稿会出现在聊天里，供你修改和核对。</p>
          <div class="suggestions"><button v-for="example in examples" :key="example" :disabled="!capability?.enabled || busy" @click="send(example)">{{ example }}<van-icon name="arrow" /></button></div>
        </section>
        <article v-for="item in messages" :key="item.id" class="chat-message" :class="item.role">
          <span v-if="item.role === 'assistant'" class="avatar" aria-hidden="true"><van-icon name="chat-o" /></span>
          <div class="message-body">
            <span class="speaker">{{ item.role === 'user' ? '你' : '财务助手' }}</span>
            <div v-if="item.pending" class="thinking" role="status" aria-label="正在回复"><i /><i /><i /></div>
            <p v-else class="message-text" :class="{ 'message-error': item.error }">{{ item.text }}</p>
            <button v-if="item.error" class="retry" :disabled="busy" @click="retry(item)">重试这条消息</button>
            <AiEvidenceCard v-for="(evidence, index) in item.evidence || []" :key="index" :evidence="evidence" />
            <AiDraftCard v-if="item.draft" :draft="item.draft" :disabled="busy" @change="updateDraft" @feedback="text => feedback(item, text)" @working="draftBusy = $event" />
          </div>
        </article>
      </div>
    </div>

    <footer class="composer-area">
      <div class="composer-inner">
        <p v-if="initializing" class="connection-state" role="status">正在连接助手…</p>
        <div v-else-if="!capability?.enabled" class="connection-state" role="alert">{{ capability?.message || connectionError || 'AI 尚未启用' }} <button @click="loadCapabilities">重新连接</button></div>
        <label v-if="capability?.requiresConsent" class="consent"><input v-model="consent" type="checkbox" :disabled="busy" /><span>同意将对话和必要的查询数据发送至 DeepSeek。</span></label>
        <form class="composer" @submit.prevent="send()">
          <textarea ref="inputArea" v-model="input" aria-label="消息" rows="1" maxlength="1500" placeholder="发消息，或说说这笔收支…" :disabled="!capability?.enabled || busy" @input="resizeInput" @keydown="onKeydown" />
          <button type="submit" class="send-button" :disabled="!canSend" aria-label="发送消息"><van-icon :name="busy ? 'ellipsis' : 'arrow-up'" /></button>
        </form>
        <p class="composer-note">{{ capability?.mode === 'mock' ? '虚构样例 · ' : '' }}草稿须核对，当前不入账。<span class="keyboard-hint">Enter 发送 · Shift + Enter 换行</span></p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { getAiCapabilities, askAi } from '@/utils/api/ai';
import AiDraftCard from '@/components/ai/AiDraftCard.vue';
import AiEvidenceCard from '@/components/ai/AiEvidenceCard.vue';

defineOptions({ name: 'AiAssistant' });
const capability = ref(null);
const initializing = ref(true);
const connectionError = ref('');
const input = ref('');
const consent = ref(false);
const chatBusy = ref(false);
const draftBusy = ref(false);
const busy = computed(() => chatBusy.value || draftBusy.value);
const messages = ref([]);
const conversationId = ref();
const scrollArea = ref(null);
const inputArea = ref(null);
let sequence = 0;
const examples = ['这个月的收支情况怎么样？', '昨天午饭花了32元，用支付宝付的', '查看本月消费明细'];
const allowed = computed(() => capability.value?.enabled && !busy.value && (!capability.value.requiresConsent || consent.value));
const canSend = computed(() => allowed.value && input.value.trim());
async function bottom() { await nextTick(); if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight; }
function resizeInput() { const el = inputArea.value; if (el) { el.style.height = 'auto'; el.style.height = `${Math.min(el.scrollHeight, 144)}px`; } }
function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) { event.preventDefault(); send(); }
}
async function loadCapabilities() {
  initializing.value = true;
  connectionError.value = '';
  try { capability.value = await getAiCapabilities(); }
  catch (error) { capability.value = null; connectionError.value = error?.message || '助手连接失败'; }
  finally { initializing.value = false; }
}
function newConversation() {
  if (busy.value) return;
  messages.value = []; conversationId.value = undefined; consent.value = false; input.value = '';
  nextTick(resizeInput);
}
async function requestReply(text, entry) {
  chatBusy.value = true;
  await bottom();
  try {
    const result = await askAi({ message: text, conversationId: conversationId.value, consent: consent.value });
    conversationId.value = result.conversationId;
    Object.assign(entry, { pending: false, error: false, text: result.answer, draft: result.draft,
      evidence: result.trace.filter(item => ['query_finance_summary', 'query_transactions'].includes(item.tool)) });
  } catch (error) {
    Object.assign(entry, { pending: false, error: true, text: error?.message || '这条消息暂时没有发送成功', requestText: text });
  } finally { chatBusy.value = false; await bottom(); }
}
async function send(example) {
  const text = (typeof example === 'string' ? example : input.value).trim();
  if (!allowed.value || !text) return;
  messages.value.push({ id: ++sequence, role: 'user', text });
  messages.value.push({ id: ++sequence, role: 'assistant', pending: true });
  const entry = messages.value[messages.value.length - 1];
  input.value = ''; nextTick(resizeInput);
  await requestReply(text, entry);
}
async function retry(entry) {
  if (!allowed.value) return;
  // A failed earlier turn must be retried before adding later turns to the server context.
  if (entry !== messages.value[messages.value.length - 1]) {
    input.value = entry.requestText; nextTick(resizeInput); return;
  }
  entry.pending = true; entry.error = false;
  await requestReply(entry.requestText, entry);
}
function updateDraft(draft) {
  for (const item of messages.value) if (item.draft?.id === draft.id) item.draft = draft;
}
function feedback(item, text) {
  if (messages.value.includes(item)) messages.value.push({ id: ++sequence, role: 'assistant', text });
}
onMounted(loadCapabilities);
</script>

<style scoped>
.chat-page{height:100%;display:flex;flex-direction:column;overflow:hidden;color:var(--theme-text-primary);background:var(--theme-bg-primary)}
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}.chat-header{flex-shrink:0;display:flex;align-items:center;gap:12px;height:64px;padding:0 24px;border-bottom:1px solid var(--theme-border)}
.back-link{color:var(--theme-text-secondary);display:grid;place-items:center;width:32px;height:36px;font-size:20px}.chat-title{display:flex;align-items:center;gap:10px}.chat-title strong{font-size:16px}.chat-title span{font-size:10px;color:var(--van-primary-color);background:var(--van-primary-color-light);padding:3px 7px;border-radius:5px}.new-chat{margin-left:auto;border:1px solid var(--theme-border);border-radius:9px;background:transparent;color:var(--theme-text-secondary);font-size:12px;padding:8px 10px}
.chat-scroll{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain}.chat-content{max-width:760px;margin:auto;padding:28px 24px 32px}.welcome{max-width:460px;margin:8vh auto 30px}.welcome-icon{display:grid;place-items:center;width:54px;height:54px;border-radius:18px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:28px}.welcome h1{font-size:28px;letter-spacing:-1px;margin:22px 0 12px}.welcome p{font-size:14px;line-height:1.9;color:var(--theme-text-secondary);margin:0}.suggestions{display:grid;gap:10px;margin-top:28px}.suggestions button{display:flex;align-items:center;justify-content:space-between;text-align:left;padding:15px 16px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);color:var(--theme-text-primary);font-size:13px}.suggestions .van-icon{color:var(--theme-text-secondary)}
.chat-message{display:flex;align-items:flex-start;gap:12px;margin-bottom:28px}.avatar{flex-shrink:0;display:grid;place-items:center;width:30px;height:30px;border-radius:10px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:18px}.message-body{min-width:0;flex:1}.speaker{display:block;font-size:11px;color:var(--theme-text-secondary);margin:2px 0 8px}.message-text{margin:0;font-size:14px;line-height:1.85;white-space:pre-wrap;overflow-wrap:anywhere}.chat-message.user{justify-content:flex-end}.user .message-body{flex:0 1 auto;max-width:85%}.user .speaker{text-align:right}.user .message-text{padding:12px 16px;border-radius:16px 16px 4px 16px;background:var(--van-primary-color-light)}.message-error{color:var(--van-danger-color)}.retry{background:none;border:0;padding:8px 0;color:var(--van-primary-color);font-size:12px}.thinking{display:flex;gap:5px;padding:10px 0}.thinking i{width:6px;height:6px;background:var(--theme-text-secondary);border-radius:50%;animation:pulse 1.1s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}@keyframes pulse{50%{opacity:.25;transform:translateY(-3px)}}
.composer-area{flex-shrink:0;border-top:1px solid var(--theme-border);background:var(--theme-bg-primary);padding:16px 24px max(10px,env(safe-area-inset-bottom))}.composer-inner{max-width:712px;margin:auto}.composer{display:flex;align-items:flex-end;gap:10px;padding:10px 10px 10px 16px;border:1px solid var(--theme-border);border-radius:18px;background:var(--theme-bg-secondary);box-shadow:0 3px 14px #00000004}.composer:focus-within{border-color:var(--van-primary-color)}textarea{width:100%;min-width:0;min-height:36px;max-height:144px;box-sizing:border-box;padding:6px 0;border:0;background:none;resize:none;outline:none;color:inherit;font:inherit;font-size:14px;line-height:24px}.send-button{flex-shrink:0;display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:11px;background:var(--van-primary-color);color:white;font-size:20px}.composer-note{font-size:10px;color:var(--theme-text-secondary);margin:9px 4px 0;line-height:1.5}.keyboard-hint{float:right}.connection-state{font-size:12px;line-height:1.6;color:var(--theme-text-secondary);margin:0 0 10px}.connection-state button{border:0;background:none;color:var(--van-primary-color)}.consent{display:flex;align-items:flex-start;gap:7px;font-size:11px;color:var(--theme-text-secondary);margin-bottom:10px;line-height:1.6}.consent input{accent-color:var(--van-primary-color);margin:2px 0 0}
@media(max-width:600px){.chat-header{height:56px;padding:0 12px;gap:6px}.chat-content{padding:24px 14px}.welcome{margin-top:6vh}.welcome h1{font-size:25px}.chat-message{gap:9px;margin-bottom:24px}.avatar{width:26px;height:26px;font-size:16px}.composer-area{padding:12px 12px max(10px,env(safe-area-inset-bottom))}.keyboard-hint{display:none}.chat-title{gap:7px}.user .message-body{max-width:90%}}
@media(prefers-reduced-motion:reduce){.thinking i{animation:none}}
</style>
