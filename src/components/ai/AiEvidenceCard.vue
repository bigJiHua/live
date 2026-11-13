<template>
  <section class="evidence-card" aria-label="查询数据">
    <div class="heading"><span><van-icon name="bar-chart-o" /> {{ isSummary ? '收支汇总' : '流水明细' }}</span><span>只读</span></div>
    <p class="range">{{ data.startDate }} — {{ data.endDate }}</p>
    <p v-if="incomplete" class="note">{{ data.unpricedCount || '部分' }}笔记录的币种或汇率未核实，未计入人民币金额；这里的金额只代表已计入的部分。</p>
    <template v-if="isSummary">
      <div class="totals"><div><span>{{ incomplete ? '已计入收入' : '收入' }}</span><strong>{{ moneyText(data.income) }}</strong></div><div><span>{{ incomplete ? '已计入支出' : '支出' }}</span><strong>{{ moneyText(data.expense) }}</strong></div></div>
      <ul class="rows"><li v-for="(row, index) in data.categories.slice(0, 4)" :key="index"><span>{{ row.category }}<small>{{ row.direction === 0 ? '支出' : '收入' }} · {{ row.count }}笔</small><small v-if="row.incomplete">{{ row.unpricedCount }}笔未计入</small></span><strong>{{ moneyText(row.amount) }}</strong></li></ul>
      <details><summary>查看统计口径 · {{ data.count }}笔</summary><p>{{ data.policy }}</p><p>分类最多返回30项，汇总金额来自范围内全部符合条件的记录。</p></details>
    </template>
    <template v-else>
      <!-- 2026-11-07 收纳：明细默认折叠，短列表（≤5条）自动展开，修复流水卡撑爆消息流 -->
      <details class="detail-rows" :open="data.rows.length <= 5">
        <summary>查看 {{ data.rows.length }} 条明细</summary>
        <ul class="rows"><li v-for="(row, index) in data.rows" :key="index"><span>{{ row.category }}<small>{{ row.date }} · {{ row.direction === 0 ? '支出' : '收入' }}</small><small v-if="row.currency !== 'CNY' || row.amountCNY == null">原金额：{{ row.currency || '币种待核实' }} {{ row.amount ?? '金额待核实' }}</small><small v-if="row.remark">{{ row.remark }}</small></span><strong>{{ moneyText(row.amountCNY) }}</strong></li></ul>
        <p class="note">展示{{ data.rows.length }}条，共{{ data.total }}条。{{ data.hasMore ? '还有未展示的记录，不能以这些明细推算总额。' : '' }}</p>
      </details>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ evidence: { type: Object, required: true } });
const data = computed(() => props.evidence.result);
const isSummary = computed(() => props.evidence.tool === 'query_finance_summary');
const incomplete = computed(() => data.value.incomplete === true || Number(data.value.unpricedCount || 0) > 0);
const moneyText = value => value == null ? '未计入' : `¥ ${value}`;
</script>
<style scoped>
.evidence-card{margin-top:10px;border:1px solid var(--theme-border);border-radius:13px;padding:13px;background:var(--theme-bg-secondary);font-size:11px}.heading{display:flex;justify-content:space-between;gap:8px}.heading .van-icon{color:var(--van-primary-color);margin-right:4px}.heading>span:last-child{font-size:9px;color:var(--theme-text-secondary)}.range{font-size:10px;color:var(--theme-text-secondary);margin:10px 0 12px}.totals{display:grid;grid-template-columns:1fr 1fr;gap:12px}.totals span{display:block;color:var(--theme-text-secondary);font-size:10px;margin-bottom:5px}.totals strong{font-size:20px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.rows{list-style:none;margin:12px 0 0;padding:0}.rows li{display:flex;justify-content:space-between;align-items:center;gap:10px;border-top:1px solid var(--theme-border);padding:10px 0}.rows small{display:block;color:var(--theme-text-secondary);font-size:9px;line-height:1.6;margin-top:3px;overflow-wrap:anywhere}.rows strong{white-space:nowrap;font-size:11px;font-variant-numeric:tabular-nums}.rows span{min-width:0}details{border-top:1px solid var(--theme-border);padding-top:10px;font-size:9px;line-height:1.7;color:var(--theme-text-secondary)}summary{cursor:pointer}.detail-rows>summary{color:var(--van-primary-color);font-size:10px;margin-bottom:3px}.detail-rows[open]>summary{margin-bottom:2px}.note{font-size:9px;line-height:1.7;color:var(--theme-text-secondary);margin-bottom:0}@media(max-width:380px){.evidence-card{padding:12px}.totals strong{font-size:18px}}
@media(max-width:600px){.evidence-card{margin-top:10px;padding:12px;border-radius:11px}.range{margin:8px 0 10px}.rows{margin-top:10px}.rows li{gap:9px;padding:9px 0}}
.evidence-card{padding:var(--ai-card-padding,10px);font-size:var(--ai-body-size,13px);line-height:1.55}
.heading>span:last-child,.range,.totals span,.rows small,details,.note{font-size:var(--ai-label-size,11px)}
.range{margin:6px 0 8px}.totals{gap:var(--ai-card-gap,6px)}.totals strong{font-size:var(--ai-money-size,22px)}
.rows{margin-top:8px}.rows li{gap:8px;padding:7px 0}.rows strong{font-size:var(--ai-body-size,13px);white-space:normal;overflow-wrap:anywhere;text-align:right}
details{padding-top:8px;line-height:1.55}.detail-rows>summary{font-size:var(--ai-label-size,11px);min-height:28px;display:flex;align-items:center}
summary:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
</style>
