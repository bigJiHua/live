<template>
  <div class="page-inst-list">
    <div class="header-card">
      <div class="header-title">分期列表</div>
      <div class="header-sub">{{ stats.total }}笔分期 · 待还{{ stats.pending }}期</div>
    </div>

    <div v-for="item in installments" :key="item.id" class="inst-card">
      <div class="inst-head" @click="toggleExpand(item)">
        <div>
          <div class="inst-name">{{ item.name }}</div>
          <div class="inst-meta">
            {{ parseAccount(item.account_id)?.card_name || '' }}
            · {{ item.repeat_count }}期
          </div>
          <div class="inst-amount-row">
            <span>每期</span>
            <span class="inst-amount">￥{{ formatAmount(item.amount) }}</span>
            <span class="inst-fee-tip">（已包含手续费）</span>
          </div>
        </div>
        <div class="inst-actions">
          <van-icon name="delete-o" class="del-icon" @click.stop="handleDelete(item)" />
          <van-icon :name="expandedId === item.id ? 'arrow-up' : 'arrow-down'" class="expand-arrow" />
        </div>
      </div>

      <div v-if="expandedId === item.id" class="expand-body">
        <van-divider />
        <div class="info-row"><span>分期总额</span><span>￥{{ formatAmount(parseAccount(item.account_id)?.original_amount) }}</span></div>
        <div class="info-row"><span>手续费</span><span>￥{{ formatAmount(parseAccount(item.account_id)?.fee) }}</span></div>
        <div class="info-row"><span>期数</span><span>{{ item.repeat_count }}期</span></div>
        <div class="info-row"><span>账单日</span><span>{{ parseAccount(item.account_id)?.billing_day || '?' }}号</span></div>

        <div class="months-section">
          <div class="ms-title">各期状态</div>
          <div v-for="m in getMonthRecords(item)" :key="m.month" class="ms-row">
            <span class="ms-date">{{ m.month }}</span>
            <app-tag :type="m.status === 'done' ? 'success' : m.status === 'skipped' ? 'danger' : 'warning'" size="small">
              {{ m.status === 'done' ? '已还' : m.status === 'skipped' ? '跳过' : '待还' }}
            </app-tag>
            <span class="ms-amount">￥{{ formatAmount(m.amount) }}</span>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && installments.length === 0" description="暂无分期记录" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { getInstallments, deleteRecurring } from '@/utils/api/recurring'

const installments = ref([])
const loading = ref(false)
const expandedId = ref('')

const stats = computed(() => {
  let total = installments.value.length, pending = 0
  installments.value.forEach(item => {
    const records = item.month_records || {}
    Object.values(records).forEach(r => { if (r.status === 'pending') pending++ })
  })
  return { total, pending }
})

const formatAmount = (v) => Number(v || 0).toFixed(2)
const parseAccount = (raw) => { try { return JSON.parse(raw) } catch { return null } }

const getMonthRecords = (item) => {
  const records = item.month_records || {}
  return Object.keys(records).sort().reverse().slice(0, 12).map(m => ({
    month: m, status: records[m]?.status || 'pending',
    amount: records[m]?.amount !== undefined ? Number(records[m].amount) : Number(item.amount || 0),
  }))
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

onMounted(() => loadData())

const handleDelete = async (item) => {
  try {
    await showConfirmDialog({ title: '确认删除', message: `确定要删除分期「${item.name}」吗？此操作不可恢复。` })
  } catch { return }
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
.del-icon { color: var(--van-danger-color, #ee0a24); font-size: 18px; }
.expand-arrow { color: var(--theme-text-tertiary); }

.expand-body { margin-top: 4px; }
.info-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: var(--theme-text-secondary); }

.months-section { margin-top: 12px; }
.ms-title { font-size: 13px; font-weight: 600; color: var(--theme-text-secondary); margin-bottom: 6px; }
.ms-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; border: 1px solid var(--theme-border); font-size: 13px; }
.ms-date { color: var(--theme-text-primary); font-weight: 500; min-width: 64px; }
.ms-amount { color: var(--van-danger-color, #ee0a24); font-weight: 500; margin-left: auto; }
</style>
