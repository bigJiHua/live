<template>
  <footer class="composer-area">
    <button v-if="jumpVisible" type="button" class="pending-jump" aria-label="回到最新消息" @click="emit('jump')">
      <template v-if="jumpPending"><i /><i /><i /></template>
      <van-icon v-else name="arrow-down" />
    </button>
    <div class="composer-inner">
      <div v-if="!initializing && !capability?.enabled" class="connection-state" role="alert">
        {{ capability?.message || connectionError || 'AI 尚未启用' }}
        <button type="button" @click="emit('open-settings')">模型设置</button>
      </div>
      <p v-if="settingsNotice" class="connection-state" role="status">{{ settingsNotice }}</p>
      <div v-if="imageAttachment" class="attachment-preview">
        <button type="button" class="preview-image-button" aria-label="查看待发送图片" @click="emit('preview-image', imageAttachment.dataUrl)">
          <img :src="imageAttachment.dataUrl" alt="待发送图片预览" />
        </button>
        <div><strong>{{ imageAttachment.name }}</strong><span>请先确认图片清晰，并遮挡无需识别的个人信息。</span></div>
        <button type="button" :disabled="busy" aria-label="移除图片" @click="emit('clear-image')"><van-icon name="cross" /></button>
      </div>
      <p v-if="imageBusy" class="connection-state" role="status">正在处理图片…</p>
      <p v-if="imageError" class="image-error" role="alert">{{ imageError }}</p>
      <div v-if="imageAttachment && !imageConsentRemembered" class="consent">
        <label class="consent-main" for="ai-img-consent">
          <input id="ai-img-consent" v-model="consentValue" type="checkbox" :disabled="busy" />
          <span>同意将这张图片及说明发送至 {{ providerName }}，用于识别。</span>
        </label>
        <button type="button" class="consent-remember" :disabled="busy" @click="emit('update:imageConsentRemembered', true)">本会话不再询问</button>
      </div>
      <form class="composer" @submit.prevent="emit('send')">
        <input ref="imageFileInput" class="sr-file" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="emit('select-image', $event)" />
        <textarea ref="inputArea" v-model="inputValue" aria-label="消息" rows="1" maxlength="1500"
          :placeholder="imageAttachment ? '可补充这张图片的说明…' : '发消息，或说说这笔收支…'"
          :disabled="!capability?.enabled" @input="resizeInput" @keydown="onKeydown" />
        <div class="composer-bar">
          <div class="bar-group">
            <button v-if="capability?.ocrEnabled" type="button" class="bar-button attach-button" :disabled="busy || !capability?.enabled" aria-label="上传支付截图或账单图片" @click="imageFileInput?.click()"><van-icon name="photo-o" /></button>
            <button v-if="capability?.enabled" type="button" class="bar-button pill-toggle" :class="{ on: webSearchEnabled, unavailable: !webSearchAvailable }" :disabled="busy || !webSearchAvailable" :aria-pressed="webSearchEnabled" :aria-label="webSearchAvailable ? '联网搜索' : '联网搜索（站点未配置搜索源）'" @click="emit('toggle-web-search')"><van-icon name="search" /><span class="pill-text">联网</span></button>
          </div>
          <div class="bar-group bar-right">
            <van-popover v-if="capability?.enabled" v-model:show="pickerVisible" placement="top-start" trigger="manual" :offset="[4, 8]">
              <template #reference>
                <button type="button" class="bar-button model-chip" :disabled="busy" aria-label="选择模型" @click="emit('open-model-picker')"><span class="chip-text">{{ modelLabel }}</span><van-icon class="chip-caret" name="arrow-down" /></button>
              </template>
              <div class="model-pop">
                <button v-if="thinkingSupported || thinkingRequired" type="button" class="model-pop-row" :class="{ on: thinkingEnabled }" :disabled="transferBusy || thinkingRequired" :aria-pressed="thinkingEnabled" aria-label="深度思考" @click="emit('toggle-thinking')">
                  <van-icon name="bulb-o" /><span class="row-text">深度思考</span><span class="row-state">{{ thinkingRequired ? '固定开启' : thinkingEnabled ? '开' : '关' }}</span><van-icon :name="thinkingEnabled ? 'checked' : 'circle'" class="row-check" />
                </button>
                <div class="model-pop-sep">可用模型</div>
                <p v-if="modelLoading" class="model-note" role="status">正在获取模型列表…</p>
                <p v-else-if="modelError" class="model-note" role="alert">{{ modelError }}</p>
                <div v-else-if="modelOptions.length" class="model-pop-list">
                  <button v-for="model in modelOptions" :key="model" type="button" class="model-pop-item" :class="{ active: model === currentModel }" :disabled="settingsBusy" @click="emit('switch-model', model)"><span class="model-name">{{ model }}</span><van-icon v-if="model === currentModel" name="success" class="item-check" /></button>
                </div>
                <p v-else class="model-note">厂商未返回任何模型。</p>
                <div class="model-pop-foot"><button type="button" class="pop-foot-btn" @click="emit('open-settings')"><van-icon name="setting-o" /><span>模型设置</span></button></div>
              </div>
            </van-popover>
            <span v-if="modelValue.length > 1200" class="char-count" :class="{ warn: modelValue.length > 1400 }">{{ modelValue.length }}/1500</span>
            <button v-if="streamingActive || requestActive" type="button" class="send-button stop-button" aria-label="停止生成" @click="emit('stop')"><van-icon name="stop-circle-o" /></button>
            <button v-else type="submit" class="send-button" :disabled="!canSend" aria-label="发送消息"><van-icon :name="busy ? 'ellipsis' : 'arrow-up'" /></button>
          </div>
        </div>
      </form>
      <p class="composer-note"><span class="keyboard-hint">Enter 发送 · Shift + Enter 换行</span></p>
    </div>
  </footer>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';

const props = defineProps({
  modelValue: { type: String, default: '' },
  capability: { type: Object, default: null },
  initializing: Boolean,
  connectionError: { type: String, default: '' },
  settingsNotice: { type: String, default: '' },
  imageAttachment: { type: Object, default: null },
  imageBusy: Boolean,
  imageError: { type: String, default: '' },
  imageConsent: Boolean,
  imageConsentRemembered: Boolean,
  providerName: { type: String, default: '' },
  busy: Boolean,
  transferBusy: Boolean,
  canSend: Boolean,
  streamingActive: Boolean,
  requestActive: Boolean,
  modelPickerOpen: Boolean,
  modelLabel: { type: String, default: '选择模型' },
  modelOptions: { type: Array, default: () => [] },
  modelLoading: Boolean,
  modelError: { type: String, default: '' },
  currentModel: { type: String, default: '' },
  settingsBusy: Boolean,
  thinkingSupported: Boolean,
  thinkingRequired: Boolean,
  thinkingEnabled: Boolean,
  webSearchAvailable: Boolean,
  webSearchEnabled: Boolean,
  isNarrow: Boolean,
  jumpVisible: Boolean,
  jumpPending: Boolean,
});
const emit = defineEmits([
  'update:modelValue', 'update:imageConsent', 'update:imageConsentRemembered', 'update:modelPickerOpen',
  'send', 'stop', 'preview-image', 'clear-image', 'select-image', 'open-model-picker',
  'switch-model', 'toggle-thinking', 'toggle-web-search', 'open-settings', 'jump',
]);
const inputArea = ref(null);
const imageFileInput = ref(null);
const inputValue = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const consentValue = computed({ get: () => props.imageConsent, set: value => emit('update:imageConsent', value) });
const pickerVisible = computed({ get: () => props.modelPickerOpen, set: value => emit('update:modelPickerOpen', value) });

function resizeInput() {
  const element = inputArea.value;
  if (!element) return;
  element.style.height = 'auto';
  element.style.height = `${Math.min(element.scrollHeight, 144)}px`;
}
function onKeydown(event) {
  if (props.isNarrow || event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return;
  event.preventDefault();
  emit('send');
}
function clearFile() { if (imageFileInput.value) imageFileInput.value.value = ''; }
watch(() => props.modelValue, () => nextTick(resizeInput));
onMounted(resizeInput);
defineExpose({ resizeInput, clearFile });
</script>

<style scoped>
button{font:inherit;cursor:pointer}button:disabled{opacity:.45;cursor:default}
button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
.composer-area{flex-shrink:0;position:relative;border-top:1px solid var(--theme-border);background:var(--theme-bg-primary);padding:10px 16px max(10px,env(safe-area-inset-bottom))}
.composer-inner{max-width:712px;margin:auto}
.composer{display:flex;flex-direction:column;border:1px solid var(--theme-border);border-radius:16px;background:var(--theme-bg-secondary);box-shadow:0 3px 14px #00000004}
textarea{display:block;width:100%;min-width:0;min-height:40px;max-height:144px;box-sizing:border-box;padding:9px 14px;border:0;background:none;resize:none;outline:none;color:inherit;font:inherit;font-size:14px;line-height:22px}
.composer-bar{display:flex;align-items:center;gap:6px;padding:5px 7px 7px}
.bar-group{display:flex;align-items:center;gap:4px;min-width:0}.bar-right{margin-left:auto}
.bar-button{display:inline-flex;align-items:center;gap:4px;min-width:0;height:30px;padding:0 7px;border:0;border-radius:8px;background:transparent;color:var(--theme-text-secondary);font:inherit;font-size:12px;white-space:nowrap;cursor:pointer}
.bar-button:disabled{opacity:.45;cursor:default}.bar-button .van-icon{font-size:15px}
.chip-text{min-width:0;overflow:hidden;text-overflow:ellipsis}.chip-caret{font-size:11px;opacity:.75}
.model-chip{max-width:180px}.pill-toggle{border-radius:999px;border:1px dashed var(--theme-border);background:transparent;box-sizing:border-box}.pill-toggle.on{border-color:var(--van-primary-color);color:var(--van-primary-color)}.pill-toggle.unavailable{opacity:.45}.pill-toggle .pill-text{font-size:12px}.attach-button{width:30px;padding:0;justify-content:center}.attach-button .van-icon{font-size:17px}
.send-button{flex-shrink:0;display:grid;place-items:center;width:30px;height:30px;border:0;border-radius:9px;background:var(--van-primary-color);color:white;font-size:17px}.stop-button{background:var(--van-danger-color)}
/* 2026-11-11：加载/错误态与列表区等高（列表 max-height 190px），浮层高度恒定不再弹跳 */
.model-note{font-size:10px;color:var(--theme-text-secondary);text-align:center;padding:10px 12px;margin:0;min-height:190px;display:flex;align-items:center;justify-content:center;box-sizing:border-box}
/* 2026-11-11：浮层内容挂在 body 层，.chat-page 上的 --van-popover-background 够不着导致透明背景；
   面板自带背景/描边/阴影，宽度 220px 容纳长模型名 */
.model-pop{width:220px;background:var(--theme-bg-secondary);border:1px solid var(--theme-border);border-radius:12px;box-shadow:0 6px 24px rgba(0,0,0,.4);overflow:hidden}
.model-name{min-width:0;overflow-wrap:anywhere;text-align:left}
.model-pop-row{display:flex;align-items:center;gap:6px;width:100%;min-height:34px;padding:6px 12px;border:0;background:none;font:inherit;font-size:11px;color:var(--theme-text-primary);text-align:left;cursor:pointer}
.model-pop-row:active{background:var(--theme-bg-tertiary)}.model-pop-row .row-text{flex:1;min-width:0}
.model-pop-row .row-state{font-size:10px;color:var(--theme-text-secondary)}.model-pop-row .row-check{color:var(--theme-text-secondary)}
.model-pop-row.on{color:var(--van-primary-color)}.model-pop-row.on .row-check{color:var(--van-primary-color)}
.model-pop-sep{padding:4px 12px 2px;font-size:9px;color:var(--theme-text-secondary);border-top:1px solid var(--theme-border);margin-top:2px}
.model-pop-list{max-height:190px;overflow-y:auto;overscroll-behavior:contain}
.model-pop-item{display:flex;align-items:center;gap:6px;width:100%;min-height:34px;padding:6px 12px;border:0;background:none;font:inherit;font-size:11px;color:var(--theme-text-primary);text-align:left;cursor:pointer}
.model-pop-item:active{background:var(--theme-bg-tertiary)}.model-pop-item.active{color:var(--van-primary-color)}.model-pop-item .item-check{color:var(--van-primary-color)}
.model-pop-foot{border-top:1px solid var(--theme-border);margin-top:2px;padding:2px 0}
.pop-foot-btn{display:flex;align-items:center;gap:6px;width:100%;min-height:32px;padding:4px 12px;border:0;background:none;font:inherit;font-size:11px;color:var(--theme-text-secondary);text-align:left;cursor:pointer}
.pop-foot-btn:active{background:var(--theme-bg-tertiary)}
.attachment-preview{display:flex;align-items:center;gap:10px;padding:8px 10px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);margin-bottom:8px}
.preview-image-button{flex-shrink:0;padding:0;border:0;background:none;cursor:zoom-in}
.attachment-preview img{display:block;width:52px;height:64px;object-fit:contain;border-radius:6px}
.attachment-preview div{min-width:0;flex:1}.attachment-preview strong{display:block;font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.attachment-preview span{display:block;font-size:10px;line-height:1.6;color:var(--theme-text-secondary);margin-top:3px}
.attachment-preview>button:last-child{padding:6px;border:0;background:none;color:var(--theme-text-secondary)}
.image-error{font-size:11px;line-height:1.6;color:var(--van-danger-color);margin:0 0 8px}
.composer-note{font-size:9px;color:var(--theme-text-secondary);margin:6px 6px 0;line-height:1.5}.keyboard-hint{float:right}
.connection-state{font-size:11px;line-height:1.6;color:var(--theme-text-secondary);margin:0 0 8px}.connection-state button{border:0;background:none;color:var(--van-primary-color)}
.consent{display:flex;align-items:flex-start;gap:6px;font-size:10px;color:var(--theme-text-secondary);margin-bottom:8px;line-height:1.6}
.consent input{accent-color:var(--van-primary-color);margin:2px 0 0}.consent .consent-main{display:flex;align-items:flex-start;gap:6px;flex:1;min-width:0;cursor:pointer}
.consent-remember{flex-shrink:0;border:0;background:none;color:var(--van-primary-color);font:inherit;font-size:10px;padding:2px 4px;cursor:pointer;white-space:nowrap}
.char-count{flex-shrink:0;font-size:10px;color:var(--theme-text-tertiary);font-variant-numeric:tabular-nums}.char-count.warn{color:var(--van-warning-color)}
.sr-file{position:absolute;left:-9999px;top:0;width:1px;height:1px;margin:0;padding:0;border:0;overflow:hidden;opacity:0;pointer-events:none}
.pending-jump{position:absolute;top:-28px;left:50%;transform:translateX(-50%);display:flex;align-items:center;gap:4px;height:28px;padding:0 14px;border:1px solid var(--theme-border);border-radius:999px;background:var(--theme-bg-secondary);box-shadow:0 2px 8px rgba(0,0,0,.08);color:var(--van-primary-color);cursor:pointer;z-index:3}
/* 圆点样式仅作用于 pending 占位符（裸 <i>）；van-icon 是真图标，不能被圆点化压毁 */
.pending-jump i:not(.van-icon){width:5px;height:5px;background:var(--van-primary-color);border-radius:50%;animation:pulse 1.1s infinite}
.pending-jump i:not(.van-icon):nth-child(2){animation-delay:.15s}.pending-jump i:not(.van-icon):nth-child(3){animation-delay:.3s}
.pending-jump .van-icon{font-size:15px;line-height:1}
@keyframes pulse{50%{opacity:.25;transform:translateY(-3px)}}
@media(max-width:600px){
  textarea{font-size:16px;line-height:22px;min-height:42px;padding:10px 12px}textarea::placeholder{font-size:13px}
  .composer-area{padding:6px 10px max(6px,env(safe-area-inset-bottom))}.composer{border-radius:11px}
  .composer-bar{padding:2px 4px 4px;gap:4px}.bar-group{gap:3px}.bar-button{height:34px;padding:0 6px;font-size:11px}
  .bar-button .van-icon{font-size:15px}.attach-button .van-icon{font-size:16px}
  .send-button{width:34px;height:34px;font-size:16px;border-radius:8px}.model-chip{max-width:140px}.composer-note{margin:0}
  .attachment-preview{gap:8px;padding:6px 8px;margin-bottom:6px;border-radius:9px}.attachment-preview img{width:40px;height:48px}
  .consent{margin-bottom:6px;line-height:1.5}.keyboard-hint{display:none}.char-count{font-size:10px}
  .consent-remember{min-height:32px;padding:4px 6px}
}
</style>
