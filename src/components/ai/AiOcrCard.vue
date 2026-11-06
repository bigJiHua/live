<template>
  <section v-if="!hasDraft || ocr.warnings.length || ocr.truncated || hasEvidence(ocr.items[0])" class="ocr-card" :class="{ compact: hasDraft }" aria-label="图片识别结果">
    <div v-if="!hasDraft" class="heading"><strong>{{ titles[ocr.documentType] }}</strong><span>待核对</span></div>
    <p v-if="!hasDraft" class="note">请对照原图核对以下识别信息。</p>
    <ul v-if="ocr.warnings.length" class="warnings"><li v-for="(warning, index) in ocr.warnings" :key="index">{{ warning }}</li></ul>
    <p v-if="ocr.truncated" class="warning">图片内容较多，当前仅展示已识别的部分，请核对原图。</p>
    <template v-if="!hasDraft">
      <div v-if="ocr.items.length" class="table-wrap">
        <table><thead><tr><th>交易信息</th><th>金额</th><th>日期</th><th>图片中的状态</th></tr></thead>
          <tbody><tr v-for="(item, index) in ocr.items" :key="index">
            <td><strong>{{ item.merchant || '交易对象未识别' }}</strong><small v-if="item.payMethod">{{ item.payMethod }}</small><small v-if="item.accountHint">账户线索：{{ item.accountHint }}</small><small v-if="item.categoryHint">分类线索：{{ item.categoryHint }}</small><small v-if="item.remark">{{ item.remark }}</small><details v-if="hasEvidence(item)"><summary>识别依据</summary><template v-for="field in evidenceFields" :key="field.key"><p v-if="item.evidence[field.key]">{{ field.label }}：{{ item.evidence[field.key] }}</p></template></details></td>
            <td class="amount"><strong>{{ item.amount === null ? '未识别' : (item.currency || '币种待核对') + ' ' + item.amount }}</strong><small>{{ item.direction === 0 ? '支出' : item.direction === 1 ? '收入' : '收支未识别' }}</small></td>
            <td>{{ item.transDate || '未识别' }}</td>
            <td><span>{{ statuses[item.transactionStatus] }}</span><small>{{ kinds[item.transactionKind] }}</small><small>{{ confidence[item.confidence] }}</small></td>
          </tr></tbody>
        </table>
      </div>
      <p v-else class="note">没有识别到可核对的交易，请换一张清晰图片，或用文字描述。</p>
    </template>
    <details v-else-if="hasEvidence(ocr.items[0])"><summary>识别依据</summary><template v-for="field in evidenceFields" :key="field.key"><p v-if="ocr.items[0].evidence[field.key]">{{ field.label }}：{{ ocr.items[0].evidence[field.key] }}</p></template></details>
  </section>
</template>

<script setup>
defineProps({ ocr: { type: Object, required: true }, hasDraft: Boolean });
const titles = { payment: '支付截图识别', statement: '账单图片识别', unknown: '图片识别结果' };
const statuses = { completed: '已完成', pending: '未完成', failed: '失败', refunded: '已退款', unknown: '未识别' };
const kinds = { ordinary: '普通收支', transfer: '转账', repayment: '还款', refund: '退款', installment: '分期', investment: '投资', balance: '余额信息', unknown: '类型待核对' };
const confidence = { high: '较清晰', medium: '需要核对', low: '不清晰' };
const evidenceFields = [{ key: 'dateText', label: '日期原文' }, { key: 'amountText', label: '金额原文' },
  { key: 'statusText', label: '交易状态原文' }, { key: 'kindText', label: '交易类型原文' }];
function hasEvidence(item) { return !!item?.evidence && evidenceFields.some(field => item.evidence[field.key]); }
</script>

<style scoped>
.ocr-card{margin-top:16px;padding:16px;border:1px solid var(--theme-border);border-radius:14px;background:var(--theme-bg-secondary);font-size:12px}.heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.heading strong{font-size:13px}.heading span,.note{color:var(--theme-text-secondary);font-size:11px;line-height:1.7}.warnings{padding-left:18px;margin:12px 0;color:var(--van-warning-color,#d58a13);line-height:1.7}.warning{color:var(--van-warning-color,#d58a13);font-size:11px;line-height:1.7}.table-wrap{overflow-x:auto;margin-top:12px}table{border-collapse:collapse;width:100%;min-width:480px;text-align:left}th{font-size:10px;color:var(--theme-text-secondary);font-weight:500;padding:8px}td{padding:12px 8px;vertical-align:top;border-top:1px solid var(--theme-border);line-height:1.6;overflow-wrap:anywhere}td:first-child{min-width:140px;max-width:240px}small{display:block;color:var(--theme-text-secondary);font-size:10px;margin-top:4px}.amount{white-space:nowrap}.note:last-child{margin-bottom:0}
.compact{margin-top:10px;padding:10px 14px}details{font-size:11px;line-height:1.7;color:var(--theme-text-secondary);margin-top:8px}details:first-child{margin-top:0}summary{cursor:pointer}details p{margin:5px 0;white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:600px){
  .table-wrap{overflow:visible}
  table,tbody{display:block;width:100%;min-width:0}
  thead{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}
  tbody tr{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px;padding:14px 0;border-top:1px solid var(--theme-border)}
  td{min-width:0;padding:0;border-top:0}
  td:first-child{min-width:0;max-width:none}
  .amount{white-space:normal;text-align:right}
  td:nth-child(4){text-align:right}
  td:nth-child(3)::before,td:nth-child(4)::before{display:block;margin-bottom:4px;font-size:10px;color:var(--theme-text-secondary)}
  td:nth-child(3)::before{content:'日期'}
  td:nth-child(4)::before{content:'交易状态'}
}
</style>
