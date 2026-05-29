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
        <span>￥{{ formatAmount(item.amount) }}</span>
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
                每月{{ item.day_of_cycle }}号
                <span v-if="item.category_name"> · {{ item.category_name }}</span>
              </div>
            </div>
            <div class="item-amount">￥{{ formatAmount(item.month_amount) }}</div>
          </div>
          <div class="item-footer">
            <div class="tag-row">
              <van-tag :type="item.month_status === 'done' ? 'success' : 'warning'">
                {{ item.month_status === 'done' ? '已处理' : item.month_status === 'skipped' ? '已跳过' : '待处理' }}
              </van-tag>
              <van-tag v-if="!item.is_active" type="default">已停用</van-tag>
              <span class="due-date">{{ item.happen_date }}</span>
            </div>
            <div class="actions">
              <van-button size="mini" plain type="primary" @click="toggleDone(item)">
                {{ item.month_status === 'done' ? '设为待处理' : '已处理' }}
              </van-button>
              <van-button size="mini" plain @click="openEdit(item)">编辑</van-button>
              <van-button size="mini" plain type="danger" @click="handleDelete(item)">删除</van-button>
            </div>
          </div>
        </div>
      </div>
    </van-pull-refresh>

    <van-popup v-model:show="showForm" position="bottom" round close-on-click-overlay>
      <div class="form-panel">
        <div class="form-title">{{ editingId ? '编辑固定支出' : '新增固定支出' }}</div>
        <van-field v-model="form.name" label="名称" placeholder="如 话费、剪头发" maxlength="100" />
        <van-field v-model="form.amount" label="金额" type="number" placeholder="0.00" />
        <van-field label="每月日期">
          <template #input>
            <van-stepper v-model="form.day_of_cycle" min="1" max="31" />
          </template>
        </van-field>
        <van-field label="提前提醒">
          <template #input>
            <van-stepper v-model="form.remind_days" min="0" max="30" />
          </template>
        </van-field>
        <van-field
          v-model="categoryLabel"
          label="分类"
          readonly
          is-link
          placeholder="选择支出分类"
          @click="showCategoryPicker = true"
        />
        <van-field v-model="form.remark" label="备注" placeholder="选填" maxlength="100" />
        <van-field label="启用">
          <template #input>
            <van-switch v-model="form.is_active" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button round block @click="showForm = false">取消</van-button>
          <van-button round block type="primary" :loading="submitting" @click="handleSubmit">保存</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <van-button
      class="fab-add"
      round
      type="primary"
      icon="plus"
      @click="openCreate"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import dayjs from 'dayjs'
import { categoryApi } from '@/utils/api/category'
import {
  getRecurringList,
  getRecurringSummary,
  createRecurring,
  updateRecurring,
  deleteRecurring,
  updateRecurringMonthStatus,
} from '@/utils/api/recurring'

const router = useRouter()
const currentMonth = ref(dayjs().startOf('month'))
const list = ref([])
const summary = ref({ totalAmount: 0, total: 0, pending: 0, categoryStats: [] })
const loading = ref(false)
const refreshing = ref(false)
const submitting = ref(false)
const showForm = ref(false)
const showCategoryPicker = ref(false)
const editingId = ref('')
const categories = ref([])

const monthKey = computed(() => currentMonth.value.format('YYYY-MM'))
const monthTitle = computed(() => currentMonth.value.format('YYYY年M月'))

const defaultForm = () => ({
  name: '',
  amount: '',
  category_id: '',
  day_of_cycle: 1,
  remind_days: 0,
  remark: '',
  is_active: true,
})

const form = ref(defaultForm())

const categoryColumns = computed(() => [
  { text: '未分类', value: '' },
  ...categories.value.map(item => ({ text: item.name, value: item.id })),
])

const categoryLabel = computed(() => {
  const item = categories.value.find(category => category.id === form.value.category_id)
  return item ? item.name : ''
})

const formatAmount = (value) => Number(value || 0).toFixed(2)

const loadCategories = async () => {
  try {
    const res = await categoryApi.list('expense')
    categories.value = res.data || []
  } catch {
    categories.value = []
  }
}

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

const openCreate = () => {
  editingId.value = ''
  form.value = defaultForm()
  showForm.value = true
}

const openEdit = (item) => {
  editingId.value = item.id
  form.value = {
    name: item.name || '',
    amount: String(item.amount || ''),
    category_id: item.category_id || '',
    day_of_cycle: item.day_of_cycle || 1,
    remind_days: item.remind_days || 0,
    remark: item.remark || '',
    is_active: !!item.is_active,
  }
  showForm.value = true
}

const onCategoryConfirm = ({ selectedOptions }) => {
  form.value.category_id = selectedOptions?.[0]?.value || ''
  showCategoryPicker.value = false
}

const handleSubmit = async () => {
  if (!form.value.name.trim()) return showToast('请输入名称')
  if (!form.value.amount || Number(form.value.amount) <= 0) return showToast('请输入金额')

  submitting.value = true
  const payload = {
    name: form.value.name.trim(),
    amount: Number(form.value.amount),
    category_id: form.value.category_id || null,
    cycle: 'month',
    day_of_cycle: Number(form.value.day_of_cycle || 1),
    remind_days: Number(form.value.remind_days || 0),
    remark: form.value.remark?.trim() || '',
    is_active: form.value.is_active ? 1 : 0,
  }

  try {
    if (editingId.value) {
      await updateRecurring(editingId.value, payload)
    } else {
      await createRecurring(payload)
    }
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

const handleDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定删除「${item.name}」？`,
      confirmButtonColor: '#ee0a24',
    })
    await deleteRecurring(item.id)
    showSuccessToast('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') showToast('删除失败')
  }
}

onMounted(() => {
  loadCategories()
  loadData()
})
</script>

<style scoped>
.page-recurring {
  min-height: 100vh;
  padding: 12px 16px 96px;
  background: #f7f8fa;
}

.fab-add {
  position: fixed;
  right: 20px;
  bottom: 36px;
  z-index: 100;
  width: 44px;
  height: 44px;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.35);
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
.due-date {
  color: #969799;
  font-size: 12px;
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
