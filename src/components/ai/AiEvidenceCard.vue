<template>
  <section class="evidence-card" aria-label="查询数据">
    <div class="heading"><span><van-icon name="bar-chart-o" /> {{ isSummary ? '收支汇总' : '流水明细' }}</span><span>只读</span></div>
    <p class="range">{{ data.startDate }} — {{ data.endDate }}</p>
    <template v-if="isSummary">
      <div class="totals"><div><span>收入</span><strong>¥ {{ data.income }}</strong></div><div><span>支出</span><strong>¥ {{ data.expense }}</strong></div></div>
      <ul class="rows"><li v-for="(row, index) in data.categories.slice(0, 4)" :key="index"><span>{{ row.category }}<small>{{ row.direction === 0 ? '支出' : '收入' }} · {{ row.count }}笔</small></span><strong>¥ {{ row.amount }}</strong></li></ul>
      <details><summary>查看统计口径 · {{ data.count }}笔</summary><p>{{ data.policy }}</p><p>分类最多返回30项，汇总金额来自范围内全部符合条件的记录。</p></details>
    </template>
    <template v-else>
      <ul class="rows"><li v-for="(row, index) in data.rows" :key="index"><span>{{ row.category }}<small>{{ row.date }} · {{ row.direction === 0 ? '支出' : '收入' }}</small><small v-if="row.remark">{{ row.remark }}</small></span><strong>¥ {{ row.amountCNY }}</strong></li></ul>
      <p class="note">展示{{ data.rows.length }}条，共{{ data.total }}条。{{ data.hasMore ? '还有未展示的记录，不能以这些明细推算总额。' : '' }}</p>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ evidence: { type: Object, required: true } });
const data = computed(() => props.evidence.result);
const isSummary = computed(() => props.evidence.tool === 'query_finance_summary');
</script>
<style scoped>
.evidence-card{margin-top:16px;border:1px solid var(--theme-border);border-radius:16px;padding:18px;background:var(--theme-bg-secondary);font-size:12px}.heading{display:flex;justify-content:space-between;gap:8px}.heading .van-icon{color:var(--van-primary-color);margin-right:4px}.heading>span:last-child{font-size:10px;color:var(--theme-text-secondary)}.range{font-size:11px;color:var(--theme-text-secondary);margin:12px 0 16px}.totals{display:grid;grid-template-columns:1fr 1fr;gap:12px}.totals span{display:block;color:var(--theme-text-secondary);font-size:11px;margin-bottom:7px}.totals strong{font-size:21px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.rows{list-style:none;margin:16px 0 0;padding:0}.rows li{display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid var(--theme-border);padding:12px 0}.rows small{display:block;color:var(--theme-text-secondary);font-size:10px;line-height:1.6;margin-top:4px;overflow-wrap:anywhere}.rows strong{white-space:nowrap;font-size:12px;font-variant-numeric:tabular-nums}.rows span{min-width:0}details{border-top:1px solid var(--theme-border);padding-top:12px;font-size:10px;line-height:1.7;color:var(--theme-text-secondary)}summary{cursor:pointer}.note{font-size:10px;line-height:1.7;color:var(--theme-text-secondary);margin-bottom:0}@media(max-width:380px){.evidence-card{padding:14px}.totals strong{font-size:18px}}
</style>
