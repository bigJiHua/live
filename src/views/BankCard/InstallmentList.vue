<template>
  <div class="page-inst-list">
    <div class="header-card">
      <div class="header-title">分期列表</div>
      <div class="header-sub">{{ stats.total }}笔分期 · 未入账{{ stats.pending }}期 · 已入账{{ stats.entered }}期</div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" success-text="刷新成功" class="inst-pull">
    <div v-for="item in installments" :key="item.id" class="inst-card" :class="{ ended: item.is_active === 0 }">
      <div class="inst-head" @click="toggleExpand(item)">
        <div>
          <div class="inst-name">
            {{ item.name }}
            <span v-if="item.is_active === 0" class="ended-badge">已结束</span>
          </div>
          <div class="inst-meta">
            {{ parseAccount(item.account_id)?.card_name || '' }}
            · {{ item.repeat_count }}期
          </div>
          <div class="inst-amount-row">
            <span>每期</span>
            <span class="inst-amount">￥{{ formatAmount(item.amount) }}</span>
            <span class="inst-fee-tip">（含利息）</span>
          </div>
        </div>
        <div class="inst-actions">
          <van-button v-if="item.is_active !== 0" size="mini" type="danger" plain class="abort-btn" @click.stop="openAbort(item)">中止分期</van-button>
          <van-icon :name="expandedId === item.id ? 'arrow-up' : 'arrow-down'" class="expand-arrow" />
        </div>
      </div>

      <div v-if="expandedId === item.id" class="expand-body">
        <van-divider />
        <div class="info-row"><span>分期总额</span><span>￥{{ formatAmount(parseAccount(item.account_id)?.original_amount) }}</span></div>
        <div class="info-row"><span>利息</span><span>￥{{ formatAmount(parseAccount(item.account_id)?.fee) }}</span></div>
        <div class="info-row"><span>期数</span><span>{{ item.repeat_count }}期</span></div>
        <div class="info-row"><span>账单日</span><span>{{ parseAccount(item.account_id)?.billing_day || '?' }}号</span></div>

        <div class="months-section">
          <div class="ms-title">各期状态（系统按账单周期自动入账）</div>
          <div v-for="m in getMonthRecords(item)" :key="m.month" class="ms-row">
            <span class="ms-date">{{ m.month }}</span>
            <span class="ms-amount">￥{{ formatAmount(m.amount) }}</span>
            <span class="ms-status" :class="statusClass(m)">{{ tagText(m) }}</span>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && installments.length === 0" description="暂无分期记录" />
    </van-pull-refresh>

    <!-- 分期中止弹窗 -->
    <van-popup v-model:show="abortShow" position="bottom" round>
      <div class="abort-panel">
        <div class="abort-head">
          <span>中止分期</span>
          <van-icon name="cross" @click="abortShow = false" />
        </div>
        <div class="abort-tip">勾选需中止的期次（默认已选中所有「待入账」期次，可手动增删）。已入账、逾期未还、超过期限的期次不可选中。</div>
        <div v-if="abortMonths.length" class="abort-list">
          <van-checkbox
            v-for="m in abortMonths"
            :key="m.month"
            v-model="m.checked"
            :disabled="m.disabled"
            class="abort-item"
            :class="{ 'is-disabled': m.disabled }"
          >
            <span class="ai-month">{{ m.month }}</span>
            <span class="ai-amount">￥{{ formatAmount(m.amount) }}</span>
            <span class="ai-tag" :class="'tag-' + m.statusKey">{{ m.statusLabel }}</span>
          </van-checkbox>
        </div>
        <van-empty v-else description="该分期暂无可中止期次（均已入账/逾期/超期）" />
        <div class="abort-actions">
          <van-button block type="danger" :disabled="!hasCheckedAbort" @click="confirmAbort">确认中止所选期次</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getInstallments, deleteRecurring, abortInstallment } from '@/utils/api/recurring'

const installments = ref([])
const loading = ref(false)
const expandedId = ref('')
const abortShow = ref(false)
const abortTarget = ref(null)
const abortMonths = ref([])

const stats = computed(() => {
  let total = installments.value.length, pending = 0, entered = 0
  installments.value.forEach(item => {
    const records = item.month_records || {}
    Object.values(records).forEach(r => {
      if (r.status === 'pending') pending++
      if (r.status === 'entered') entered++
    })
  })
  return { total, pending, entered }
})

const formatAmount = (v) => Number(v || 0).toFixed(2)
const parseAccount = (raw) => { try { return JSON.parse(raw) } catch { return null } }

// 三态展示：优先用后端返回的 effectiveStatus（已还判定），否则用原始 status
const getMonthRecords = (item) => {
  const records = item.month_records || {}
  return Object.keys(records).sort().slice(0, 60).map(m => {
    const rec = records[m] || {}
    const eff = rec.effectiveStatus || rec.status || 'pending'
    return {
      month: m,
      rawStatus: rec.status || 'pending',
      status: eff,
      overdue: !!rec.overdue,
      amount: rec.amount !== undefined ? Number(rec.amount) : Number(item.amount || 0),
    }
  })
}

const statusClass = (m) => {
  if (m.rawStatus === 'entering') return 'st-entering'
  if (m.rawStatus === 'void') return 'st-void'
  if (m.status === 'done') return 'st-done'
  if (m.overdue) return 'st-overdue'
  if (m.status === 'entered') return 'st-entered'
  return 'st-pending'
}
const tagText = (m) => {
  if (m.rawStatus === 'entering') return '入账中'
  if (m.rawStatus === 'void') return '超过期限'
  if (m.status === 'done') return '已还'
  if (m.overdue) return '逾期未还'
  if (m.status === 'entered') return '已入账'
  return '待入账'
}

const toggleExpand = (item) => {
  expandedId.value = expandedId.value === item.id ? '' : item.id
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getInstallments()
    installments.value = res.data || []
  } catch (e) { showToast(e.message || '加载失败') } finally { loading.value = false }
}

const refreshing = ref(false)
const onRefresh = async () => {
  try {
    const res = await getInstallments()
    installments.value = res.data || []
  } catch (e) { showToast(e.message || '刷新失败') } finally { refreshing.value = false }
}

onMounted(() => loadData())

// 中止入口：弹出全部期次，有效（待入账/入账中）期次默认选中，可手动取消/改选；
// 已入账/逾期/超过期限的期次列出但禁用（不可选中），由用户挑选具体要中止的期次
const openAbort = (item) => {
  abortTarget.value = item
  abortMonths.value = getMonthRecords(item).map((m) => {
    const entered = m.status === 'entered' || m.status === 'done' || m.overdue
    const expired = m.rawStatus === 'void'
    const canAbort = (m.rawStatus === 'pending' || m.rawStatus === 'entering') && !entered && !expired
    return {
      month: m.month,
      amount: m.amount,
      checked: canAbort, // 有效（待入账）期次默认选中，用户可手动调整
      disabled: !canAbort,
      statusKey: expired ? 'void' : m.overdue ? 'overdue' : m.status === 'done' ? 'done' : entered ? 'entered' : 'pending',
      statusLabel: expired ? '超过期限' : m.overdue ? '逾期未还' : m.status === 'done' ? '已还' : entered ? '已入账' : '待入账',
    }
  })
  abortShow.value = true
}
const hasCheckedAbort = computed(() => abortMonths.value.some(m => m.checked))

const confirmAbort = async () => {
  const months = abortMonths.value.filter(m => m.checked).map(m => m.month)
  if (!months.length) { showToast('请至少选择一个待入账期次'); return }
  try {
    const res = await abortInstallment(abortTarget.value.id, months)
    const { aborted = [], skipped = [], finished = false } = res.data || {}
    if (aborted.length && skipped.length) {
      showToast(`已中止${aborted.length}期；${skipped.length}期跳过：${skipped.map(s => s.reason).join('；')}`)
    } else if (aborted.length) {
      showToast(finished ? `已中止全部${aborted.length}期，分期结束` : `已中止${aborted.length}期`)
    } else if (skipped.length) {
      showToast(`未能中止：${skipped.map(s => s.reason).join('；')}`)
    } else {
      showToast('未选中可中止期次')
    }
    abortShow.value = false
    loadData()
  } catch (e) { showToast(e.message || '中止失败') }
}

// 删除入口保留（极端情况），默认不暴露
const handleDelete = async (item) => {
  try {
    await deleteRecurring(item.id)
    showToast('已删除')
    loadData()
  } catch (e) { showToast(e.message || '删除失败') }
}
</script>

<style scoped>
.page-inst-list { min-height: 100vh; padding: 12px 16px 100px; background: var(--theme-bg-primary); }
.header-card { background: linear-gradient(135deg, var(--van-danger-color, #ee0a24) 0%, var(--van-danger-grad, #d91a4a) 100%); border-radius: 12px; padding: 20px; margin-bottom: 16px; color: #fff; }
.header-title { font-size: 20px; font-weight: 700; }
.header-sub { font-size: 13px; opacity: 0.85; margin-top: 6px; }

.inst-card { background: var(--theme-bg-secondary); border-radius: 8px; padding: 14px; margin-bottom: 8px; }
.inst-head { display: flex; justify-content: space-between; align-items: center; cursor: pointer; }
.inst-name { font-size: 15px; font-weight: 600; color: var(--theme-text-primary); }
.inst-meta { font-size: 12px; color: var(--theme-text-tertiary); margin-top: 2px; }
.inst-amount-row { display: flex; align-items: baseline; gap: 2px; margin-top: 6px; font-size: 13px; color: var(--theme-text-primary); }
.inst-amount { font-size: 16px; font-weight: 700; color: var(--van-danger-color, #ee0a24); }
.inst-fee-tip { font-size: 11px; color: var(--theme-text-tertiary); }
.inst-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.abort-btn { border-color: var(--van-danger-color, #ee0a24); color: var(--van-danger-color, #ee0a24); }
.expand-arrow { color: var(--theme-text-tertiary); }

/* 已结束分期：整体灰色 */
.inst-card.ended { opacity: 0.6; filter: grayscale(1); }
.inst-card.ended .inst-head { cursor: default; }
.ended-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  color: #fff;
  background: #969799;
  border-radius: 8px;
  vertical-align: middle;
}

.expand-body { margin-top: 4px; }
.info-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: var(--theme-text-secondary); }

.months-section { margin-top: 12px; }
.ms-title { font-size: 13px; font-weight: 600; color: var(--theme-text-secondary); margin-bottom: 6px; }
.ms-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.ms-date { color: var(--theme-text-primary); font-weight: 500; min-width: 64px; }
.ms-amount { color: var(--van-danger-color, #ee0a24); font-weight: 500; }
.ms-status { margin-left: auto; font-size: 12px; font-weight: 500; }
.ms-status.st-pending { color: #1989fa; background: rgba(25, 137, 250, 0.1); padding: 1px 6px; border-radius: 4px; }
.ms-status.st-entering { color: var(--van-warning-color, #ff976a); background: rgba(255, 151, 106, 0.12); padding: 1px 6px; border-radius: 4px; }
.ms-status.st-entered { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); padding: 1px 6px; border-radius: 4px; }
.ms-status.st-overdue { color: var(--van-danger-color, #ee0a24); background: rgba(238, 10, 36, 0.1); padding: 1px 6px; border-radius: 4px; }
.ms-status.st-done { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); padding: 1px 6px; border-radius: 4px; }
.ms-status.st-void { color: var(--theme-text-tertiary); background: rgba(0, 0, 0, 0.05); padding: 1px 6px; border-radius: 4px; }

.abort-panel { padding: 16px 16px 24px; max-height: 70vh; overflow-y: auto; }
.abort-head { display: flex; justify-content: space-between; align-items: center; font-size: 16px; font-weight: 600; color: var(--theme-text-primary); }
.abort-tip { font-size: 12px; color: var(--theme-text-tertiary); margin: 8px 0 12px; line-height: 1.5; }
.abort-list { display: flex; flex-direction: column; gap: 4px; }
.abort-item { padding: 8px 0; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.abort-item.is-disabled { opacity: 0.55; }
.ai-month { font-weight: 500; color: var(--theme-text-primary); }
.ai-amount { color: var(--van-danger-color, #ee0a24); }
.ai-tag { margin-left: auto; font-size: 12px; padding: 1px 6px; border-radius: 4px; }
.ai-tag.tag-pending { color: #1989fa; background: rgba(25, 137, 250, 0.1); }
.ai-tag.tag-entered { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); }
.ai-tag.tag-overdue { color: var(--van-danger-color, #ee0a24); background: rgba(238, 10, 36, 0.1); }
.ai-tag.tag-done { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); }
.ai-tag.tag-void { color: var(--theme-text-tertiary); background: rgba(0, 0, 0, 0.05); }
.abort-actions { margin-top: 16px; }
</style>
