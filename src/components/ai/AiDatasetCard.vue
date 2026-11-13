<template>
  <section class="data-card" :class="{ open }" :aria-label="dataset.title">
    <!-- 2026-11-08：行头即原「按需读取」轨迹行（数据徽标+标题+N条），点击在本卡内嵌套展开详细数据。
         开合为逐卡独立（组件自持状态），原「点任意一张=全组展开」的整组语义废弃。 -->
    <button type="button" class="trace-row" :aria-expanded="open" @click="toggleCard">
      <span class="trace-badge data" :class="{ warn: headBadge !== '账本数据' }">{{ headBadge }}</span>
      <strong class="trace-title">{{ dataset.title }}</strong>
      <span class="trace-records">{{ display.recordsLabel }}</span>
      <van-icon name="arrow-down" class="trace-chevron" />
    </button>
    <!-- 口径/范围与异常提示随展开体呈现；收起态仅一行（负责人 2026-11-08 拍板） -->
    <div v-if="open" class="trace-body">
      <p v-if="display.scopeLabel || display.countLabel" class="data-coverage">{{ [display.scopeLabel, display.countLabel].filter(Boolean).join('；') }}。</p>
      <p v-if="display.notes.length" class="data-limits" role="status">{{ display.notes.join('；') }}。</p>
      <p v-if="dataset.userPolicy" class="policy">{{ dataset.userPolicy }}</p>
      <dl v-if="display.summaryItems.length" class="summary"><div v-for="item in display.summaryItems" :key="item.key"><dt>{{ item.label }}</dt><dd :class="{ 'summary-neg': item.neg }">{{ item.value }}</dd></div></dl>
      <button class="toggle" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起数据' : `查看 ${display.recordsLabel}数据` }}</button>
      <div v-if="expanded" class="table-wrap"><table><thead><tr><th v-for="column in dataset.columns" :key="column.key">{{ column.label }}</th></tr></thead><tbody><tr v-for="(row, index) in dataset.rows" :key="index"><td v-for="column in dataset.columns" :key="column.key">{{ row[column.key] ?? '—' }}</td></tr></tbody></table><p v-if="!dataset.rows.length" class="policy">{{ display.emptyMessage }}</p></div>
      <small>数据时间 {{ display.dataTimeLabel || dataset.asOf }}</small>
    </div>
  </section>
</template>
<script setup>
import { computed, ref } from 'vue';
import { getAiDatasetDisplay } from '@/utils/aiDatasetDisplay';
const props = defineProps({ dataset: { type: Object, required: true },
  // 行头徽标优先用 display.badge（账本数据/数据不完整/部分数据），badge prop 仅作覆盖
  badge: { type: String, default: '' },
  // 传 open 时为受控渲染（SSR/测试用），不传（null）则组件自持逐卡独立开合
  open: { type: Boolean, default: null } });
const emit = defineEmits(['toggle']);
const innerOpen = ref(false);
const open = computed(() => (props.open === null ? innerOpen.value : props.open));
function toggleCard() { if (props.open === null) innerOpen.value = !innerOpen.value; else emit('toggle'); }
const expanded = ref(false);
const display = computed(() => getAiDatasetDisplay(props.dataset));

const headBadge = computed(() => props.badge || display.value.badge);
</script>
<style scoped>
.data-card{border-radius:0;background:transparent;min-width:0;font-size:var(--ai-body-size,11px);line-height:1.55}
button,input,select,textarea,summary,a{font:inherit}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
button:disabled,input:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:default}
.trace-row{display:flex;align-items:center;gap:5px;width:100%;min-height:22px;padding:1px 0;border:0;background:none;font:inherit;text-align:left;cursor:pointer}
.trace-row:active{background:var(--theme-bg-tertiary)}
.trace-badge{flex-shrink:0;font-size:8px;line-height:1;padding:2px 4px;border-radius:3px;opacity:.8}
.trace-badge.data{color:var(--van-primary-color);background:var(--theme-primary-light,rgba(25,137,250,.08))}
.trace-badge.data.warn{color:var(--van-warning-color,#ff976a);background:rgba(255,151,106,.1)}
.trace-title{min-width:0;flex:1;overflow-wrap:anywhere;font-size:10.5px;color:var(--theme-text-secondary);font-weight:500}
.trace-records{font-variant-numeric:tabular-nums;opacity:.8;flex-shrink:0;font-size:9.5px;color:var(--theme-text-tertiary)}
.trace-chevron{flex-shrink:0;color:var(--theme-text-tertiary);font-size:10px;transition:transform .2s}
.data-card.open .trace-chevron{transform:rotate(180deg)}
.data-limits,.data-coverage{margin:0;padding:var(--ai-card-gap,6px) 0 0;color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);line-height:1.55;white-space:normal;overflow-wrap:anywhere}

.trace-body{padding:0 0 4px}
.policy,small{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);line-height:1.55;white-space:pre-wrap;overflow-wrap:anywhere}.policy{margin:var(--ai-card-gap,6px) 0 0}
.summary{display:flex;gap:var(--ai-card-gap,6px) 10px;flex-wrap:wrap;margin:var(--ai-card-gap,6px) 0 0}.summary>div{min-width:0}.summary dt{font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary)}.summary dd{margin:2px 0 0;font-size:var(--ai-body-size,11px);font-weight:550;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.summary dd.summary-neg{color:var(--van-danger-color)}
.toggle{border:0;min-height:32px;padding:3px 0;color:var(--van-primary-color);background:none;font-size:var(--ai-body-size,11px);cursor:pointer}
.table-wrap{overflow:auto;max-height:240px;margin:3px 0 var(--ai-card-gap,6px)}table{border-collapse:collapse;width:100%;font-size:var(--ai-body-size,11px);white-space:nowrap}th,td{text-align:left;padding:5px var(--ai-card-gap,6px);border-bottom:1px solid var(--theme-border);font-variant-numeric:tabular-nums}th{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);font-weight:500}
small{display:block;margin-top:3px}
@media(max-width:600px){.trace-row{min-height:26px}.toggle{min-height:36px}}
</style>
