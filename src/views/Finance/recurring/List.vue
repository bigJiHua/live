<template>
  <div class="page-recurring">
    <div class="summary-card">
      <div class="month-row">
        <van-icon name="arrow-left" @click="changeMonth(-1)" />
        <span>{{ monthTitle }}</span>
        <van-icon name="arrow" @click="changeMonth(1)" />
      </div>
      <div class="summary-main">
        <div>
          <div class="summary-label">本月固定支出</div>
          <div class="summary-amount">￥{{ formatAmount(summary.totalAmount) }}</div>
        </div>
        <div class="summary-stats">
          <span>{{ summary.total || 0 }}项</span>
          <span>{{ summary.pending || 0 }}项待处理</span>
        </div>
      </div>
    </div>

    <div class="category-card" v-if="summary.categoryStats?.length">
      <div class="section-title">组成</div>
      <div v-for="item in summary.categoryStats" :key="item.category_id || item.category_name" class="category-row">
        <span>{{ item.category_name }}</span>
        <span v-if="item.category_name === '事件提醒'">{{ item.count }}件</span>
        <span v-else>￥{{ formatAmount(item.amount) }}</span>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="loadData">
      <van-empty v-if="!loading && list.length === 0" description="暂无固定支出" />
      <div v-else class="recurring-list">
        <div v-for="item in list" :key="item.id" class="recurring-item" :class="{ inactive: !item.is_active }">
          <div class="item-head">
            <div>
              <div class="item-title">{{ item.name }}</div>
              <div class="item-sub">
              {{ item.cycle === 'year' ? '每年' : '每月' }}{{ item.cycle === 'year' && item.month_of_cycle ? item.month_of_cycle + '月' : '' }}{{ item.day_of_cycle }}号
              <span v-if="item.repeat_count" class="installment-badge"> · 第{{ doneCount(item) }}/{{ item.repeat_count }}期</span>
              <span v-if="item.category_name"> · {{ item.category_name }}</span>
            </div>
            </div>
            <div v-if="Number(item.month_amount) > 0" class="item-amount">￥{{ formatAmount(item.month_amount) }}</div>
            <div v-else class="item-amount event-only">仅提醒</div>
          </div>
          <div class="item-footer">
            <template v-if="!isInstallment(item)">
            <div class="tag-row">
              <van-tag :type="item.month_status === 'done' ? 'success' : item.month_status === 'skipped' ? 'danger' : 'warning'">
                {{ item.month_status === 'done' ? '已处理' : item.month_status === 'skipped' ? '已跳过' : '待处理' }}
              </van-tag>
              <van-tag v-if="item.cycle === 'year'" type="primary" size="small">年</van-tag>
              <van-tag v-if="item.repeat_count" color="#7232dd" text-color="#fff" size="small">{{ doneCount(item) }}/{{ item.repeat_count }}期</van-tag>
              <van-tag v-if="!item.is_active" type="default">已停用</van-tag>
              <span v-if="item.end_date" class="end-date">至 {{ item.end_date }}</span>
              <span class="due-date">{{ item.happen_date }}</span>
            </div>
            <div class="actions">
              <van-button v-if="item.month_status !== 'skipped'" size="mini" plain type="primary" @click="toggleDone(item)">
                {{ item.month_status === 'done' ? '设为待处理' : '设为已处理' }}
              </van-button>
              <van-tag v-else type="danger" size="small">已跳过</van-tag>
              <van-button size="mini" plain @click="openEdit(item)">编辑</van-button>
              <van-button size="mini" plain type="danger" @click="handleSkipMonth(item)" v-if="item.month_status !== 'skipped'">跳过本月</van-button>
            </div>
            </template>
            <span v-else class="due-date">{{ item.happen_date }}</span>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup v-model:show="showForm" position="bottom" round close-on-click-overlay>
      <div class="form-panel">
        <div class="form-title">编辑当月 — {{ editingMonth }}</div>
        <van-field v-model="form.name" label="名称" readonly />
        <van-field v-if="form.is_expense" v-model="form.amount" label="本月金额" type="number" placeholder="0.00" />
        <van-field v-else class="expense-off-hint">
          <template #input><span>仅事件提醒，不计入支出</span></template>
        </van-field>
        <van-field v-model="form.remark" label="本月备注" placeholder="选填" maxlength="100" />
        <van-button v-if="editingSkipped" round block type="warning" @click="restoreMonth" style="margin-bottom:10px">恢复事件（取消跳过）</van-button>
        <div class="form-actions">
          <van-button round block @click="showForm = false">取消</van-button>
          <van-button round block type="primary" :loading="submitting" @click="handleSubmit">保存</van-button>
        </div>
      </div>
    </van-popup>



  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import {
  getRecurringList,
  getRecurringSummary,
  updateRecurringMonthStatus,
} from '@/utils/api/recurring'

const currentMonth = ref(dayjs().startOf('month'))
const list = ref([])
const summary = ref({ totalAmount: 0, total: 0, pending: 0, categoryStats: [] })
const loading = ref(false)
const refreshing = ref(false)
const submitting = ref(false)
const showForm = ref(false)
const editingMonth = ref('')
const editingSkipped = ref(false)

const monthKey = computed(() => currentMonth.value.format('YYYY-MM'))
const monthTitle = computed(() => currentMonth.value.format('YYYY年M月'))

const defaultForm = () => ({ name: '', amount: '', is_expense: true, remark: '' })
const form = ref(defaultForm())

const formatAmount = (value) => Number(value || 0).toFixed(2)

const loadData = async () => {
  loading.value = true
  try {
    const [listRes, summaryRes] = await Promise.all([
      getRecurringList({ month: monthKey.value, includeInactive: 1 }),
      getRecurringSummary({ month: monthKey.value }),
    ])
    list.value = listRes.data || []
    summary.value = summaryRes.data || { totalAmount: 0, total: 0, pending: 0, categoryStats: [] }
  } catch (error) {
    showToast(error.message || '加载失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const changeMonth = (step) => {
  currentMonth.value = currentMonth.value.add(step, 'month')
  loadData()
}

const openEdit = (item) => {
  const itemAmount = Number(item.amount || 0)
  const monthRecord = item.month_record || {}
  const monthAmount = monthRecord.amount !== undefined ? Number(monthRecord.amount) : itemAmount
  editingMonth.value = item.happen_date || monthKey.value
  editingSkipped.value = item.month_status === 'skipped'
  form.value = {
    name: item.name || '',
    amount: itemAmount > 0 ? String(monthAmount) : '0',
    is_expense: itemAmount > 0,
    remark: monthRecord.remark || '',
  }
  showForm.value = true
}

const restoreMonth = async () => {
  const item = list.value.find(i => i.name === form.value.name.trim())
  if (!item) return showToast('数据异常')
  try {
    await updateRecurringMonthStatus(item.id, { month: monthKey.value, status: 'pending' })
    showSuccessToast('已恢复为待处理')
    showForm.value = false
    loadData()
  } catch (e) {
    showToast(e.message || '操作失败')
  }
}

const isInstallment = (item) => {
  if (item.category_id === 'installment') return true
  try { const a = JSON.parse(item.account_id || '{}'); return a.type === 'installment' } catch { return false }
}

const doneCount = (item) => {
  const records = item.month_records
  if (!records) return 0
  if (typeof records === 'object' && !Array.isArray(records)) {
    return Object.values(records).filter(r => r && r.status === 'done').length
  }
  return 0
}

const handleSubmit = async () => {
  if (!form.value.name.trim()) return showToast('请输入名称')
  if (form.value.is_expense && (!form.value.amount || Number(form.value.amount) <= 0)) return showToast('请输入金额')

  // 从列表中找到当前正在编辑的项
  const item = list.value.find(i => i.name === form.value.name.trim())
  if (!item) return showToast('数据异常')

  submitting.value = true
  try {
    await updateRecurringMonthStatus(item.id, {
      month: monthKey.value,
      status: item.month_status === 'skipped' ? 'pending' : item.month_status,
      amount: form.value.is_expense ? Number(form.value.amount) : 0,
      remark: form.value.remark?.trim() || '',
    })
    showSuccessToast('保存成功')
    showForm.value = false
    loadData()
  } catch (error) {
    showToast(error.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const toggleDone = async (item) => {
  const status = item.month_status === 'done' ? 'pending' : 'done'
  try {
    await updateRecurringMonthStatus(item.id, {
      month: monthKey.value,
      status,
      amount: item.month_amount,
    })
    showSuccessToast('已更新')
    loadData()
  } catch (error) {
    showToast(error.message || '更新失败')
  }
}

const handleSkipMonth = async (item) => {
  try {
    await showConfirmDialog({
      title: '跳过本月',
      message: `确定跳过「${item.name}」本月？跳过后不再计入统计。`,
      confirmButtonColor: '#ee0a24',
    })
    await updateRecurringMonthStatus(item.id, { month: monthKey.value, status: 'skipped' })
    showSuccessToast('已跳过')
    loadData()
  } catch (error) {
    if (error !== 'cancel') showToast('操作失败')
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.page-recurring {
  min-height: 100vh;
  padding: 12px 16px 96px;
  background: #f7f8fa;
}

.summary-card,
.category-card,
.recurring-item {
  background: #fff;
  border-radius: 8px;
  padding: 14px;
}

.summary-card {
  margin-bottom: 12px;
}

.month-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: #646566;
  font-size: 14px;
  margin-bottom: 12px;
}

.summary-main {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.summary-label,
.item-sub,
.due-date,
.end-date {
  color: #969799;
  font-size: 12px;
}

.end-date {
  color: #ee0a24;
  font-size: 11px;
}

.installment-badge {
  color: #7232dd;
  font-weight: 500;
}

.summary-amount {
  color: #ee0a24;
  font-size: 28px;
  font-weight: 700;
  margin-top: 4px;
}

.summary-stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: right;
  font-size: 12px;
  color: #646566;
}

.category-card {
  margin-bottom: 12px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.category-row,
.item-head,
.item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.category-row {
  font-size: 13px;
  padding: 6px 0;
  color: #646566;
}

.recurring-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recurring-item.inactive {
  opacity: 0.58;
}

.item-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 5px;
}

.item-amount {
  color: #ee0a24;
  font-size: 18px;
  font-weight: 700;
}

.item-amount.event-only {
  color: #1989fa;
  font-size: 13px;
  font-weight: 400;
}

.expense-off-hint {
  color: #969799;
  font-size: 13px;
}

.item-footer {
  margin-top: 12px;
  gap: 8px;
}

.tag-row,
.actions,
.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.form-panel {
  padding: 18px 16px 22px;
}

.form-title {
  text-align: center;
  font-weight: 600;
  margin-bottom: 12px;
}

.form-actions {
  margin-top: 18px;
}

.form-actions .van-button {
  flex: 1;
}
</style>
