<template>
  <div class="ai-page">
    <section class="app-card ai-intro">
      <div class="ai-heading">
        <span class="ai-icon"><van-icon name="chat-o" /></span>
        <div><h1>财务 AI 测试</h1><p>查询账本 · 理解一句话记账</p></div>
        <span class="ai-badge">测试版</span>
      </div>
      <p class="ai-boundary">本版支持查询和生成草稿。草稿不计入余额、账单或报表。</p>
      <van-loading v-if="initializing" size="18">正在连接站点…</van-loading>
      <p v-else-if="!capability?.enabled" class="ai-state">{{ capability?.message || error || 'AI 尚未启用' }}</p>
      <p v-else class="ai-state" :class="{ 'ai-mock': capability.mode === 'mock' }">
        {{ capability.mode === 'mock' ? '模拟联调 · 使用虚构样例，不读取真实账本' : 'DeepSeek · 使用本站点的只读查询' }}
      </p>
      <button v-if="!capability?.enabled && !initializing" class="ai-retry" @click="loadCapabilities">重新检查</button>
    </section>

    <section class="app-card ai-compose">
      <div class="ai-tabs" role="tablist" aria-label="AI 功能">
        <button role="tab" :aria-selected="scene === 'query'" :class="{ active: scene === 'query' }"
          :disabled="busy" @click="changeScene('query')">财务查询</button>
        <button role="tab" :aria-selected="scene === 'draft'" :class="{ active: scene === 'draft' }"
          :disabled="busy" @click="changeScene('draft')">记账草稿</button>
      </div>
      <label class="ai-input-label" for="ai-message">{{ scene === 'query' ? '想了解什么？' : '说说这笔收支' }}</label>
      <textarea id="ai-message" v-model="message" maxlength="1500" rows="4" :disabled="busy"
        :placeholder="scene === 'query' ? '例如：这个月的收支情况怎么样？' : '例如：昨天午饭花了32元，用支付宝付的'" />
      <div class="ai-input-meta"><span>{{ scene === 'draft' ? '仅支持一笔普通人民币收支' : '每次查询范围最多93天' }}</span><span>{{ message.length }}/1500</span></div>
      <div class="ai-examples">
        <button v-for="example in examples" :key="example" :disabled="busy" @click="message = example">{{ example }}</button>
      </div>
      <label v-if="capability?.requiresConsent" class="ai-consent">
        <input v-model="consent" type="checkbox" :disabled="busy" />
        <span>同意将本次输入及必要的查询结果、账户别名和分类发送至 DeepSeek。</span>
      </label>
      <app-button block type="primary" :loading="busy" :disabled="!canSend" @click="send">
        {{ scene === 'query' ? '查询并分析' : '生成记账草稿' }}
      </app-button>
      <p v-if="busy" class="ai-wait" role="status">正在处理，本次不会写入账本…</p>
      <p v-if="error" class="ai-error" role="alert">{{ error }}</p>
    </section>

    <template v-if="result">
      <section class="app-card ai-answer">
        <div class="ai-section-title"><h2>AI 回答</h2><button @click="clearResult">清空结果</button></div>
        <p class="ai-answer-text">{{ result.answer }}</p>
        <p v-if="result.trace.length === 0" class="ai-note">本次没有取得账本查询证据，以上仅为文字说明。</p>
      </section>

      <section v-if="result.draft" class="app-card ai-draft">
        <div class="ai-section-title"><h2>记账草稿</h2><span class="ai-badge">未入账</span></div>
        <div class="ai-draft-amount"><small>{{ result.draft.direction === 0 ? '支出' : '收入' }}</small><strong>¥ {{ result.draft.amount }}</strong></div>
        <dl class="ai-fields">
          <div><dt>日期</dt><dd>{{ result.draft.transDate }}</dd></div>
          <div><dt>分类</dt><dd>{{ result.draft.categoryName || '待选择' }}</dd></div>
          <div><dt>账户</dt><dd>{{ result.draft.accountName || '待选择' }}</dd></div>
          <div><dt>支付方式</dt><dd>{{ result.draft.payMethod || '待选择' }}</dd></div>
          <div><dt>备注</dt><dd>{{ result.draft.remark || '无' }}</dd></div>
        </dl>
        <div class="ai-original"><span>原始输入</span><p>{{ result.draft.originalText }}</p></div>
        <p v-if="result.draft.missingFields.length" class="ai-note">需要补齐：{{ result.draft.missingFields.join('、') }}。</p>
        <p class="ai-note">请核对金额、日期和账户。测试版不提供确认入账，草稿15分钟后过期，服务重启后清除。</p>
      </section>

      <section v-if="evidence.length" class="app-card ai-evidence">
        <h2>查询依据</h2>
        <p class="ai-note">以下是服务端实际返回的数据，便于核对 AI 回答。</p>
        <div v-for="(item, index) in evidence" :key="index" class="ai-evidence-item">
          <p class="ai-range">{{ item.result.startDate }} — {{ item.result.endDate }}</p>
          <template v-if="item.tool === 'query_finance_summary'">
            <div class="ai-totals"><div><span>收入</span><strong>¥ {{ item.result.income }}</strong></div><div><span>支出</span><strong>¥ {{ item.result.expense }}</strong></div></div>
            <p class="ai-note">{{ item.result.count }}笔 · {{ item.result.policy }}</p>
            <ul class="ai-category-list"><li v-for="(category, i) in item.result.categories" :key="i"><span>{{ category.category }} · {{ category.direction === 0 ? '支出' : '收入' }}</span><b>¥ {{ category.amount }}</b></li></ul>
            <p class="ai-note">分类明细最多展示30项，汇总金额来自全部符合范围的记录。</p>
          </template>
          <template v-else>
            <ul class="ai-category-list"><li v-for="(row, i) in item.result.rows" :key="i"><span>{{ row.date }} · {{ row.category }}<small>{{ row.remark }}</small></span><b>¥ {{ row.amountCNY }}</b></li></ul>
            <p class="ai-note">本次展示{{ item.result.rows.length }}条，共{{ item.result.total }}条。{{ item.result.hasMore ? '还有未展示的记录，不能以这些明细推算总额。' : '' }}</p>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getAiCapabilities, askAi } from '@/utils/api/ai';
defineOptions({ name: 'AiAssistant' });
const capability = ref(null);
const initializing = ref(true);
const scene = ref('query');
const message = ref('');
const consent = ref(false);
const busy = ref(false);
const error = ref('');
const result = ref(null);
const examples = computed(() => scene.value === 'query'
  ? ['这个月的收支情况怎么样？', '查看本月消费明细']
  : ['昨天午饭花了32元，用支付宝付的', '今天工资到账8000元']);
const canSend = computed(() => capability.value?.enabled && !busy.value && message.value.trim() &&
  (!capability.value.requiresConsent || consent.value));
const evidence = computed(() => (result.value?.trace || []).filter(item =>
  ['query_finance_summary', 'query_transactions'].includes(item.tool)));
async function loadCapabilities() {
  initializing.value = true;
  capability.value = null;
  error.value = '';
  consent.value = false;
  try { capability.value = await getAiCapabilities(); }
  catch (exception) { error.value = exception?.message || '无法连接 AI 接口'; }
  finally { initializing.value = false; }
}
function changeScene(value) {
  scene.value = value;
  message.value = '';
  clearResult();
}
function clearResult() { result.value = null; error.value = ''; }
async function send() {
  if (!canSend.value) return;
  busy.value = true;
  clearResult();
  try { result.value = await askAi({ message: message.value.trim(), scene: scene.value, consent: consent.value }); }
  catch (exception) { error.value = exception?.message || 'AI 请求失败'; }
  finally { busy.value = false; }
}
onMounted(loadCapabilities);
</script>

<style scoped>
.ai-page{max-width:720px;margin:auto;padding:14px 12px 40px;color:var(--theme-text-primary)}
.ai-page .app-card{padding:20px;margin:0 0 14px}.ai-heading{display:flex;align-items:center;gap:12px}
.ai-icon{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;color:var(--van-primary-color);background:var(--van-primary-color-light);font-size:24px}
h1{font-size:20px;margin:0 0 4px}h2{font-size:16px;margin:0}.ai-heading p{font-size:12px;color:var(--theme-text-secondary);margin:0}
.ai-badge{margin-left:auto;white-space:nowrap;padding:4px 8px;border-radius:6px;background:var(--van-primary-color-light);color:var(--van-primary-color);font-size:11px}
.ai-boundary,.ai-note{font-size:12px;line-height:1.7;color:var(--theme-text-secondary)}.ai-state{font-size:12px;color:var(--van-primary-color);margin-bottom:0}.ai-mock{color:var(--van-orange,#ed6a0c)}
.ai-tabs{display:flex;gap:6px;padding:4px;background:var(--theme-bg-secondary);border-radius:12px;margin-bottom:20px}.ai-tabs button{flex:1;padding:10px;border:0;background:transparent;border-radius:9px;font-size:14px;color:var(--theme-text-secondary)}
.ai-tabs button.active{background:var(--theme-bg-primary);color:var(--van-primary-color);box-shadow:0 2px 8px #00000008;font-weight:600}
.ai-input-label{display:block;font-size:14px;margin-bottom:10px}textarea{display:block;box-sizing:border-box;width:100%;resize:vertical;min-height:110px;max-height:300px;border:1px solid var(--theme-border);border-radius:12px;padding:12px;background:var(--theme-bg-secondary);color:inherit;font:inherit;line-height:1.65;font-size:14px}textarea:focus{outline:2px solid var(--van-primary-color);outline-offset:1px}
.ai-input-meta{display:flex;justify-content:space-between;gap:8px;font-size:11px;color:var(--theme-text-secondary);margin:8px 0 14px}.ai-examples{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:20px}.ai-examples button,.ai-retry{font-size:12px;border:1px solid var(--theme-border);padding:7px 10px;border-radius:20px;background:transparent;color:var(--theme-text-secondary)}
button{cursor:pointer}button:disabled{cursor:default;opacity:.55}.ai-consent{display:flex;align-items:flex-start;gap:8px;font-size:12px;line-height:1.7;margin-bottom:16px;color:var(--theme-text-secondary)}.ai-consent input{margin-top:4px;accent-color:var(--van-primary-color)}
.ai-error{color:var(--van-danger-color);font-size:13px;line-height:1.6;margin-bottom:0}.ai-wait{font-size:12px;color:var(--theme-text-secondary)}.ai-section-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.ai-section-title button{border:0;background:none;color:var(--theme-text-secondary);font-size:12px}
.ai-answer-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.8;margin-bottom:0}.ai-draft-amount{display:flex;align-items:baseline;gap:10px;margin:20px 0}.ai-draft-amount small{color:var(--theme-text-secondary)}.ai-draft-amount strong{font-size:30px;letter-spacing:-.7px;font-variant-numeric:tabular-nums}
.ai-fields{margin:0}.ai-fields div{display:flex;justify-content:space-between;gap:16px;padding:10px 0;border-top:1px solid var(--theme-border);font-size:13px}.ai-fields dt{flex-shrink:0;color:var(--theme-text-secondary)}.ai-fields dd{margin:0;text-align:right;overflow-wrap:anywhere}
.ai-original{background:var(--theme-bg-secondary);border-radius:10px;padding:12px;margin-top:14px;font-size:12px;overflow-wrap:anywhere}.ai-original span{color:var(--theme-text-secondary)}.ai-original p{margin:5px 0 0;line-height:1.7}
.ai-evidence-item+.ai-evidence-item{border-top:1px solid var(--theme-border);margin-top:20px;padding-top:12px}.ai-range{font-size:12px;color:var(--theme-text-secondary)}.ai-totals{display:grid;grid-template-columns:1fr 1fr;gap:12px}.ai-totals div{padding:12px;background:var(--theme-bg-secondary);border-radius:10px;min-width:0}.ai-totals span{display:block;font-size:12px;color:var(--theme-text-secondary);margin-bottom:6px}.ai-totals strong{font-size:18px;overflow-wrap:anywhere}
.ai-category-list{list-style:none;padding:0;margin:12px 0}.ai-category-list li{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-top:1px solid var(--theme-border);font-size:12px}.ai-category-list small{display:block;color:var(--theme-text-secondary);margin-top:4px}.ai-category-list b{white-space:nowrap;font-variant-numeric:tabular-nums}
@media(max-width:380px){.ai-page .app-card{padding:16px}.ai-examples button{font-size:11px}.ai-heading{gap:8px}h1{font-size:18px}.ai-totals strong{font-size:16px}}
</style>
