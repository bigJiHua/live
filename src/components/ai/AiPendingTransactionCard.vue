<template>
  <section class="pending-transaction-card" aria-label="待补记账">
    <div class="heading"><strong>继续补充记账</strong><span>{{ expired ? '需要重新核验' : '待补充' }}</span></div>
    <p v-if="expired" class="notice">自动续接时间已过，确认原交易后可以继续补充。</p>
    <p v-else class="notice">保留原金额和日期，选择账户后再核对。</p>
    <div v-for="item in remainingItems" :key="item.index" class="pending-item">
      <div><strong>{{ item.fields.direction === 1 ? '收入' : '支出' }} {{ item.fields.amount }} 元</strong><span class="date">{{ item.fields.transDate }}</span></div>
      <p v-if="item.fields.remark" class="remark">{{ item.fields.remark }}</p>
      <p class="missing">待补齐：{{ item.missingFields.join('、') }}</p>
      <button type="button" :disabled="disabled || unavailable" @click="emit('resume', item.index)">{{ expired ? '继续补充这笔' : '选择账户并补充' }}</button>
    </div>
    <p v-if="unavailable" class="notice" role="status">待补状态暂时无法读取，请稍后重新打开聊天。</p>
    <p class="footnote">只核验本站数据；生成草稿后仍需你确认入账。</p>
  </section>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
const props = defineProps({ task: { type: Object, required: true }, completedIndices: { type: Array, default: () => [] }, disabled: Boolean, unavailable: Boolean });
const emit = defineEmits(['resume']);
const clock = ref(Date.now());
let timer;
const expired = computed(() => props.task.status === 'expired' || props.task.expiresAt <= clock.value);
const remainingItems = computed(() => props.task.items.filter(item => !props.completedIndices.includes(item.index)));
onMounted(() => { timer = setInterval(() => { clock.value = Date.now(); }, 15000); });
onBeforeUnmount(() => clearInterval(timer));
</script>

<style scoped>
.pending-transaction-card{margin-top:var(--ai-card-gap,6px);padding:var(--ai-card-padding,10px);border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);font-size:var(--ai-body-size,11px);line-height:1.55}
.heading,.pending-item>div{display:flex;justify-content:space-between;gap:10px;align-items:baseline}.heading span,.date,.footnote{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px)}
.notice,.missing,.remark,.footnote{margin:5px 0;overflow-wrap:anywhere}.pending-item{padding:8px 0;border-top:1px solid var(--theme-border)}.pending-item button{font:inherit;min-height:34px;padding:6px 12px;border:0;border-radius:7px;background:var(--van-primary-color);color:#fff;cursor:pointer}.pending-item button:disabled{opacity:.5;cursor:default}.pending-item button:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
</style>
