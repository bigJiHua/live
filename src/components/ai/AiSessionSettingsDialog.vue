<template>
  <van-popup :show="show" position="right" teleport="body" class="session-settings-popup" :style="{ width: '100%', height: '100%' }" @update:show="close">
    <!-- 2026-11-11：只优化移动端，全屏面板为唯一形态（不做桌面窄抽屉分支） -->
    <section role="dialog" aria-modal="true" aria-labelledby="ai-session-settings-title" class="session-sheet" @keydown.esc.prevent="close">
      <div class="sheet-head">
        <div><h2 id="ai-session-settings-title">设置</h2><p>{{ providerLabel }}</p></div>
        <button aria-label="关闭设置" @click="close"><van-icon name="cross" /></button>
      </div>
      <div class="sheet-body">
        <p class="group-label"><span>对话模式</span><span>影响 AI 可执行的动作范围</span></p>
        <div class="mode-list">
          <button v-for="option in modeOptions" :key="option.value" type="button" class="mode-item" :class="{ on: mode === option.value, 'mode-item-operate': option.value === 'operate' && mode === option.value }" :disabled="busy" :aria-pressed="mode === option.value" @click="pickMode(option.value)">
            <span class="mi-main"><strong class="mi-name">{{ option.label }}</strong><span class="mi-desc">{{ option.desc }}</span></span>
            <van-icon name="success" class="mi-check" />
          </button>
        </div>
        <template v-if="businessEnabled">
          <p class="group-label"><span>能力授权</span></p>
          <div class="row-card">
            <button type="button" class="row-line" :disabled="busy" @click="emit('manageAuth')">
              <van-icon name="shield-o" class="rl-icon" />
              <span class="rl-main"><strong class="rl-title">数据能力授权</strong><span class="rl-sub">允许 AI 读取账本数据；操作模式需单独授权。</span></span>
              <span class="pill" :class="{ off: !authGranted }">{{ authStateLabel }}</span>
              <van-icon name="arrow" class="rl-arrow" />
            </button>
          </div>
        </template>
        <p class="group-label"><span>个性化</span></p>
        <div class="row-card">
          <button type="button" class="row-line" :disabled="busy" @click="emit('openPersonalization')">
            <van-icon name="user-o" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">AI 个性化</strong><span class="rl-sub">查看和编辑记忆、用户画像与自定义规则。</span></span>
            <van-icon name="arrow" class="rl-arrow" />
          </button>
        </div>
        <p class="group-label"><span>模型</span></p>
        <div class="row-card">
          <button type="button" class="row-line" :disabled="busy" @click="emit('openModelSettings')">
            <van-icon name="setting-o" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">模型设置</strong><span class="rl-sub">接入商、API Key 与模型切换。</span></span>
            <van-icon name="arrow" class="rl-arrow" />
          </button>
          <button type="button" class="row-line budget-settings-entry" :disabled="busy" @click="emit('openBudgetSettings')">
            <van-icon name="balance-list-o" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">额度与超时</strong><span class="rl-sub">调整调用额度、Token 上限与等待时间。</span></span>
            <van-icon name="arrow" class="rl-arrow" />
          </button>
          <div class="thinking-line">
            <van-icon name="bulb-o" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">深度思考</strong><span class="rl-sub">{{ thinkingHint }}</span></span>
            <van-switch :model-value="thinkingEnabled" size="22" :disabled="busy || !thinkingSupported || thinkingRequired" @update:model-value="emit('toggleThinking')" />
          </div>
          <div class="thinking-line web-line">
            <van-icon name="search" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">联网搜索</strong><span class="rl-sub">{{ webSearchAvailable ? '开启后 AI 可检索公开网页并标注来源' : '站点尚未配置联网搜索源；改用 DeepSeek 或智谱接入商即可使用' }}</span></span>
            <van-switch :model-value="webSearchEnabled" size="22" :disabled="busy || !webSearchAvailable" @update:model-value="emit('toggleWebSearch')" />
          </div>
          <p class="web-billing-danger">联网搜索功能收费，具体参照供应商；一般而言该项为单独收费功能。</p>
        </div>
        <!-- 2026-11-11：全局审计日志唯一入口收在会话设置（全局枢纽），从接入商表单撤出；样式与行卡统一 -->
        <p class="group-label"><span>诊断</span></p>
        <div class="row-card">
          <button type="button" class="row-line" :disabled="busy" @click="traceOpen = true">
            <van-icon name="records-o" class="rl-icon" />
            <span class="rl-main"><strong class="rl-title">调用与错误日志</strong><span class="rl-sub">全链路审计：思考 / 调用 / 工具 / 门控。</span></span>
            <van-icon name="arrow" class="rl-arrow" />
          </button>
        </div>
      </div>
    </section>
    <AiTraceLogDrawer v-model:show="traceOpen" />
  </van-popup>
</template>
<script setup>
import { computed, ref } from 'vue';
import AiTraceLogDrawer from './AiTraceLogDrawer.vue';
import { useDialogBackGuard } from '@/composables/useDialogBackGuard';
const props = defineProps({
  show: Boolean,
  mode: { type: String, default: 'analysis' },
  busy: Boolean,
  providerLabel: { type: String, default: '' },
  businessEnabled: Boolean,
  authGranted: Boolean,
  grantExpiresAt: { type: Number, default: null },
  thinkingEnabled: Boolean,
  thinkingSupported: Boolean,
  thinkingRequired: Boolean,
  webSearchEnabled: Boolean,
  webSearchAvailable: Boolean,
});
const emit = defineEmits(['update:show', 'update:mode', 'manageAuth', 'openModelSettings', 'openBudgetSettings', 'openPersonalization', 'toggleThinking', 'toggleWebSearch']);
const traceOpen = ref(false);
const modeOptions = [
  { value: 'chat', label: '闲聊', desc: '纯聊天，不读取账本数据，适合随便聊聊。' },
  { value: 'analysis', label: '分析', desc: '可读取账本数据回答问题、做统计与归类。' },
  { value: 'operate', label: '操作', desc: '可代你记账、改账，需先完成能力授权。' },
];
function pickMode(value) { if (props.busy || value === props.mode) return; emit('update:mode', value); }
function close() { emit('update:show', false); }
// 返回拦截：设置面板开着时按返回只关面板、路由留在聊天界面（不退 /ai 回主页）
useDialogBackGuard(() => props.show, close);
const authStateLabel = computed(() => {
  if (!props.authGranted) return '未授权';
  const date = props.grantExpiresAt ? new Date(props.grantExpiresAt) : null;
  return date && !isNaN(date) ? `已授权 · 至 ${date.getMonth() + 1}/${date.getDate()}` : '已授权';
});
const thinkingHint = computed(() => props.thinkingRequired ? '当前模型始终启用思考' : props.thinkingSupported ? '开启后回答更深入，耗时和用量增加' : '当前模型不支持思考开关');
</script>
<style scoped>
.session-settings-popup{background:var(--theme-bg-primary);color:var(--theme-text-primary)}
.session-sheet{display:flex;flex-direction:column;height:100%;box-sizing:border-box}
.sheet-head{flex-shrink:0;width:100%;max-width:560px;margin:0 auto;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:calc(env(safe-area-inset-top,0px) + 14px) 16px 12px;border-bottom:1px solid var(--theme-border);box-sizing:border-box}
.sheet-head h2{font-size:17px;margin:0}
.sheet-head p{font-size:11px;color:var(--theme-text-secondary);margin:4px 0 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sheet-head button{border:0;background:none;color:var(--theme-text-secondary);padding:6px;font-size:17px;cursor:pointer}
.sheet-body{flex:1;overflow-y:auto;width:100%;max-width:560px;margin:0 auto;padding:12px 16px 28px;box-sizing:border-box}
.group-label{display:flex;justify-content:space-between;gap:8px;font-size:11px;color:var(--theme-text-tertiary);margin:16px 2px 8px}
.group-label:first-child{margin-top:4px}
.mode-list{display:flex;flex-direction:row;gap:8px}
.mode-item{flex:1 1 0;min-width:0;display:flex;flex-direction:column;align-items:flex-start;gap:6px;position:relative;text-align:left;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);padding:12px 14px;cursor:pointer;color:inherit;font:inherit;box-sizing:border-box}
.mode-item:disabled{opacity:.5;cursor:default}
.mode-item.on{border-color:var(--van-primary-color);background:var(--van-primary-color-light,rgba(58,102,224,.08))}
/* 2026-11-10 一致性规范：操作=写库模式，选中态用警示橙，与头部徽章同语义 */
.mode-item.mode-item-operate{border-color:var(--van-warning-color,#ee9a01);background:rgba(238,154,1,.08)}
.mode-item.mode-item-operate .mi-name{color:var(--van-warning-color,#ee9a01)}
.mode-item.mode-item-operate .mi-check{background:var(--van-warning-color,#ee9a01);border-color:var(--van-warning-color,#ee9a01)}
.mi-main{min-width:0}
.mi-name{display:block;font-size:14px;font-weight:600;color:var(--theme-text-primary);padding-right:22px}
.mi-desc{display:block;font-size:11px;line-height:1.55;color:var(--theme-text-secondary);margin-top:2px}
.mi-check{position:absolute;top:11px;right:11px;flex-shrink:0;width:18px;height:18px;border-radius:50%;border:1.5px solid var(--theme-border);display:grid;place-items:center;font-size:11px;color:transparent;transition:all .15s}
.mode-item.on .mi-check{background:var(--van-primary-color);border-color:var(--van-primary-color);color:#fff}
.row-card{border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);overflow:hidden}
.row-line{display:flex;align-items:center;gap:10px;width:100%;min-height:52px;padding:12px 14px;border:0;background:none;color:inherit;font:inherit;text-align:left;cursor:pointer;box-sizing:border-box}
.row-line:disabled{opacity:.5;cursor:default}
.budget-settings-entry{border-top:1px solid var(--theme-border)}
.rl-icon{flex-shrink:0;width:20px;font-size:16px;color:var(--theme-text-secondary)}
.rl-main{flex:1;min-width:0}
.rl-title{display:block;font-size:13.5px;font-weight:500;color:var(--theme-text-primary)}
.rl-sub{display:block;font-size:11px;line-height:1.5;color:var(--theme-text-secondary);margin-top:2px}
.pill{flex-shrink:0;font-size:10.5px;line-height:1;padding:3px 8px;border-radius:999px;color:var(--van-success-color,#07c160);background:rgba(7,193,96,.12);white-space:nowrap}
.pill.off{color:var(--theme-text-secondary);background:var(--theme-bg-tertiary)}
.rl-arrow{flex-shrink:0;color:var(--theme-text-tertiary);font-size:13px}
.thinking-line{display:flex;align-items:center;gap:10px;padding:12px 14px;border-top:1px solid var(--theme-border)}.web-billing-danger{margin:0;padding:0 14px 12px;font-size:12px;line-height:1.6;color:var(--van-danger-color);font-weight:600}
button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
@media(max-width:600px){
  .sheet-head{padding:calc(env(safe-area-inset-top,0px) + 12px) 14px 10px}
  .sheet-head h2{font-size:15px}
  .sheet-body{padding:10px 14px max(24px,env(safe-area-inset-bottom))}
  .mode-item{padding:10px 11px}
  .mi-check{top:9px;right:9px}
  .mi-name{font-size:13.5px;padding-right:20px}
  .row-line,.thinking-line{padding:11px 12px;min-height:48px}
}
</style>
