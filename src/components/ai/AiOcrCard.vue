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
.ocr-card{margin-top:var(--ai-card-gap,6px);padding:var(--ai-card-padding,10px);border:1px solid var(--theme-border);border-radius:10px;background:var(--theme-bg-secondary);min-width:0;font-size:var(--ai-body-size,11px);line-height:1.55}
button,input,select,textarea,summary,a{font:inherit}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--van-primary-color);outline-offset:2px}
button:disabled,input:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:default}

.heading{display:flex;align-items:center;justify-content:space-between;gap:var(--ai-card-gap,6px)}.heading strong{font-size:var(--ai-title-size,11px);min-width:0;overflow-wrap:anywhere}.heading span,.note{color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);line-height:1.55}.heading span{flex-shrink:0}.note{margin:var(--ai-card-gap,6px) 0}
.warnings{padding-left:15px;margin:var(--ai-card-gap,6px) 0;color:var(--van-warning-color,#d58a13);font-size:var(--ai-body-size,11px);font-weight:500;line-height:1.55}.warnings li+li{margin-top:3px}.warning{color:var(--van-warning-color,#d58a13);font-size:var(--ai-body-size,11px);font-weight:500;line-height:1.55;margin:var(--ai-card-gap,6px) 0;overflow-wrap:anywhere}
.table-wrap{overflow-x:auto;margin-top:var(--ai-card-gap,6px)}table{border-collapse:collapse;width:100%;min-width:460px;text-align:left;font-size:var(--ai-body-size,11px)}th{font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary);font-weight:500;padding:5px var(--ai-card-gap,6px)}td{padding:var(--ai-card-gap,6px);vertical-align:top;border-top:1px solid var(--theme-border);line-height:1.5;overflow-wrap:anywhere}td:first-child{min-width:130px;max-width:240px}
small{display:block;color:var(--theme-text-secondary);font-size:var(--ai-label-size,10px);margin-top:2px;overflow-wrap:anywhere}.amount{white-space:nowrap;font-variant-numeric:tabular-nums}.amount strong{font-size:var(--ai-body-size,11px);font-weight:650}.note:last-child{margin-bottom:0}
.compact{margin-top:var(--ai-card-gap,6px);padding:calc(var(--ai-card-padding,10px) * .7) var(--ai-card-padding,10px)}details{font-size:var(--ai-body-size,11px);line-height:1.55;color:var(--theme-text-secondary);margin-top:var(--ai-card-gap,6px)}details:first-child{margin-top:0}summary{cursor:pointer;min-height:28px;padding:2px 0}details p{margin:3px 0;white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:600px){.table-wrap{overflow:visible}table,tbody{display:block;width:100%;min-width:0}thead{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}tbody tr{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:var(--ai-card-gap,6px);padding:var(--ai-card-gap,6px) 0;border-top:1px solid var(--theme-border)}td{min-width:0;padding:0;border-top:0}td:first-child{min-width:0;max-width:none}.amount{white-space:normal;text-align:right}td:nth-child(4){text-align:right}td:nth-child(3)::before,td:nth-child(4)::before{display:block;margin-bottom:2px;font-size:var(--ai-label-size,10px);color:var(--theme-text-secondary)}td:nth-child(3)::before{content:'日期'}td:nth-child(4)::before{content:'交易状态'}summary{min-height:32px}}
</style>
