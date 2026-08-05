<template>
  <div class="page-events">
    <div class="header-card">
      <div class="header-title">固定事件</div>
      <div class="header-stats">
        <span>{{ stats.total }} 项总计</span>
        <span>{{ stats.expenseCount }} 项支出 · {{ stats.eventCount }} 项事件</span>
      </div>
    </div>

    <div class="section" v-if="monthly.length">
      <div class="section-title">每月</div>
      <div v-for="item in monthly" :key="item.id" class="event-card" :class="{ inactive: !item.is_active }">
        <div class="event-head" @click="toggleExpand(item)">
          <div>
            <div class="event-name">{{ item.name }}</div>
            <div class="event-meta">
              <span>每月{{ item.day_of_cycle }}号</span>
              <span v-if="item.category_name"> · {{ item.category_name }}</span>
              <span v-if="item.end_date" class="end-hint"> · 至 {{ item.end_date }}</span>
            </div>
          </div>
          <div class="event-right">
            <span v-if="Number(item.amount) > 0" class="event-amount">￥{{ formatAmount(item.amount) }}</span>
            <span v-else class="event-amount tag">事件</span>
            <van-icon :name="expandedId === item.id ? 'arrow-up' : 'arrow-down'" class="expand-icon" />
          </div>
        </div>

        <div v-if="expandedId === item.id" class="expand-body">
          <van-divider />
          <div class="toggle-row">
            <span>{{ item.is_active ? '已启用' : '已停用' }}</span>
            <van-switch :model-value="!!item.is_active" size="20px" @update:model-value="(v) => toggleActive(item, v)" />
          </div>

          <app-field v-model="editForm.name" label="名称" maxlength="100" />
          <app-field v-if="Number(item.amount) > 0" v-model="editForm.amount" label="金额" type="number" />
          <app-field v-else label="金额" class="expense-off">
            <span>仅事件提醒</span>
          </app-field>
          <app-field :label="item.cycle === 'year' ? '几号' : '每月几号'">
            <van-stepper v-model="editForm.day_of_cycle" min="1" max="31" />
          </app-field>
          <app-field v-if="item.cycle === 'year'" label="每年几月">
            <van-stepper v-model="editForm.month_of_cycle" min="1" max="12" />
          </app-field>
          <app-field
            v-model="editForm.end_date" label="失效日期" readonly is-link
            :placeholder="editForm.end_date || '选填'" @click="openEndDatePicker(item)"
          />
          <app-field v-model="editForm.remark" label="备注" placeholder="选填" maxlength="100" />

          <div class="month-records-section">
            <div class="mr-title">月度处理记录</div>
            <div v-for="m in getMonthRecords(item)" :key="m.month" class="mr-row">
              <span class="mr-month">{{ m.month }}</span>
              <app-tag :type="m.status === 'done' ? 'success' : m.status === 'skipped' ? 'danger' : 'warning'" size="small">
                {{ m.status === 'done' ? '已处理' : m.status === 'skipped' ? '已跳过' : '待处理' }}
              </app-tag>
              <span v-if="m.amount > 0" class="mr-amount">￥{{ formatAmount(m.amount) }}</span>
              <app-button size="mini" plain @click="toggleMonthStatus(item, m)">切换状态</app-button>
            </div>
          </div>

          <div class="expand-actions">
            <app-button round block type="primary" :loading="savingId === item.id" @click="handleSave(item)">保存修改</app-button>
            <app-button round block type="danger" @click="handleDelete(item)">删除此项</app-button>
          </div>
        </div>
      </div>
    </div>

    <div class="section" v-if="yearly.length">
      <div class="section-title">每年</div>
      <div v-for="item in yearly" :key="item.id" class="event-card" :class="{ inactive: !item.is_active }">
        <div class="event-head" @click="toggleExpand(item)">
          <div>
            <div class="event-name">{{ item.name }}</div>
            <div class="event-meta">
              <span>每年{{ item.month_of_cycle || '' }}月{{ item.day_of_cycle }}号</span>
              <span v-if="item.category_name"> · {{ item.category_name }}</span>
              <span v-if="item.end_date" class="end-hint"> · 至 {{ item.end_date }}</span>
            </div>
          </div>
          <div class="event-right">
            <span v-if="Number(item.amount) > 0" class="event-amount">￥{{ formatAmount(item.amount) }}</span>
            <span v-else class="event-amount tag">事件</span>
            <van-icon :name="expandedId === item.id ? 'arrow-up' : 'arrow-down'" class="expand-icon" />
          </div>
        </div>

        <div v-if="expandedId === item.id" class="expand-body">
          <van-divider />
          <div class="toggle-row">
            <span>{{ item.is_active ? '已启用' : '已停用' }}</span>
            <van-switch :model-value="!!item.is_active" size="20px" @update:model-value="(v) => toggleActive(item, v)" />
          </div>
          <app-field v-model="editForm.name" label="名称" maxlength="100" />
          <app-field v-if="Number(item.amount) > 0" v-model="editForm.amount" label="金额" type="number" />
          <app-field v-else label="金额" class="expense-off">
            <span>仅事件提醒</span>
          </app-field>
          <app-field label="每年几月">
            <van-stepper v-model="editForm.month_of_cycle" min="1" max="12" />
          </app-field>
          <app-field label="几号">
            <van-stepper v-model="editForm.day_of_cycle" min="1" max="31" />
          </app-field>
          <app-field
            v-model="editForm.end_date" label="失效日期" readonly is-link
            :placeholder="editForm.end_date || '选填'" @click="openEndDatePicker(item)"
          />
          <app-field v-model="editForm.remark" label="备注" placeholder="选填" maxlength="100" />

          <div class="month-records-section">
            <div class="mr-title">月度处理记录</div>
            <div v-for="m in getMonthRecords(item)" :key="m.month" class="mr-row">
              <span class="mr-month">{{ m.month }}</span>
              <app-tag :type="m.status === 'done' ? 'success' : m.status === 'skipped' ? 'danger' : 'warning'" size="small">
                {{ m.status === 'done' ? '已处理' : m.status === 'skipped' ? '已跳过' : '待处理' }}
              </app-tag>
              <span v-if="m.amount > 0" class="mr-amount">￥{{ formatAmount(m.amount) }}</span>
              <app-button size="mini" plain @click="toggleMonthStatus(item, m)">切换状态</app-button>
            </div>
          </div>

          <div class="expand-actions">
            <app-button round block type="primary" :loading="savingId === item.id" @click="handleSave(item)">保存修改</app-button>
            <app-button round block type="danger" @click="handleDelete(item)">删除此项</app-button>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && list.length === 0" description="暂无固定事件" />

    <!-- 失效日期选择器 -->
    <app-popup v-model:show="showEndDatePicker" position="bottom" round>
      <van-date-picker v-model="endDatePickerValue" title="选择失效日期" :min-date="minDate"
        @confirm="onEndDateConfirm" @cancel="showEndDatePicker = false" />
    </app-popup>

    <!-- 新增事件表单 -->
    <app-popup v-model:show="showAddForm" position="bottom" round close-on-click-overlay style="height:80vh;overflow-y:auto">
      <div class="form-panel">
        <div class="form-title">新增固定事件</div>
        <app-field v-model="addForm.name" label="名称" placeholder="如 话费、房租、生日" maxlength="100" />
        <app-field label="金额">
          <van-switch v-model="addForm.is_expense" size="20px" />
        </app-field>
        <app-field v-if="addForm.is_expense" v-model="addForm.amount" type="number" placeholder="0.00" />
        <app-field v-else class="expense-off-hint">
          <span>仅事件提醒，不计入支出</span>
        </app-field>
        <app-field label="周期">
          <van-radio-group v-model="addForm.cycle" direction="horizontal">
            <van-radio name="month">每月</van-radio>
            <van-radio name="year">每年</van-radio>
          </van-radio-group>
        </app-field>
        <app-field v-if="addForm.cycle === 'year'" label="每年几月">
          <van-stepper v-model="addForm.month_of_cycle" min="1" max="12" />
        </app-field>
        <app-field :label="addForm.cycle === 'year' ? '几号' : '每月几号'">
          <van-stepper v-model="addForm.day_of_cycle" min="1" max="31" />
        </app-field>
        <app-field
          v-model="addForm.end_date" label="失效日期" readonly is-link
          placeholder="选填" @click="openAddEndDatePicker"
        />
        <app-field v-if="addForm.is_expense" v-model="addForm.categoryLabel" label="分类" readonly is-link
          placeholder="选择支出分类" @click="showAddCategoryPicker = true" />
        <app-field v-model="addForm.remark" label="备注" placeholder="选填" maxlength="100" />
        <app-field label="提前提醒">
          <van-stepper v-model="addForm.remind_days" min="0" max="30" />
        </app-field>
        <app-field label="启用">
          <van-switch v-model="addForm.is_active" />
        </app-field>
        <div class="form-actions">
          <app-button round block @click="showAddForm = false">取消</app-button>
          <app-button round block type="primary" :loading="adding" @click="handleAdd">保存</app-button>
        </div>
      </div>
    </app-popup>

    <app-popup v-model:show="showAddCategoryPicker" position="bottom" round>
      <van-picker title="选择分类" :columns="categoryColumns" @confirm="onAddCategoryConfirm" @cancel="showAddCategoryPicker = false" />
    </app-popup>

    <app-popup v-model:show="showAddEndDatePicker" position="bottom" round>
      <van-date-picker v-model="addEndDatePickerValue" title="选择失效日期" :min-date="minDate"
        @confirm="onAddEndDateConfirm" @cancel="showAddEndDatePicker = false" />
    </app-popup>

    <div class="footer-actions">
      <app-button round block type="default" @click="goRecurring">按月管理</app-button>
      <app-button round block type="primary" icon="plus" @click="openAdd">新增事件</app-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showSuccessToast } from 'vant'
import { getRecurringList, createRecurring, updateRecurring, deleteRecurring, updateRecurringMonthStatus } from '@/utils/api/recurring'
import { categoryApi } from '@/utils/api/category'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const expandedId = ref('')
const savingId = ref('')
const editForm = ref({})

const showEndDatePicker = ref(false)
const endDatePickerValue = ref([new Date().getFullYear(), new Date().getMonth() + 1, new Date().getDate()])
const minDate = new Date(2020, 0, 1)

// 新增表单
const showAddForm = ref(false)
const adding = ref(false)
const categories = ref([])
const showAddCategoryPicker = ref(false)
const showAddEndDatePicker = ref(false)
const addEndDatePickerValue = ref([new Date().getFullYear(), new Date().getMonth() + 1, new Date().getDate()])

const defaultAddForm = () => ({
  name: '', amount: '', is_expense: true, cycle: 'month',
  day_of_cycle: 1, month_of_cycle: 1, end_date: '',
  category_id: '', categoryLabel: '', remark: '', remind_days: 0, is_active: true,
})
const addForm = reactive(defaultAddForm())

const categoryColumns = computed(() => [
  { text: '未分类', value: '' },
  ...categories.value.map(c => ({ text: c.name, value: c.id })),
])

const monthly = computed(() => list.value.filter(item => item.cycle !== 'year'))
const yearly = computed(() => list.value.filter(item => item.cycle === 'year'))
const stats = computed(() => {
  const total = list.value.length
  const expenseCount = list.value.filter(item => Number(item.amount) > 0).length
  return { total, expenseCount, eventCount: total - expenseCount }
})

const formatAmount = (value) => Number(value || 0).toFixed(2)

const loadData = async () => {
  loading.value = true
  try {
    const [listRes, catRes] = await Promise.all([
      getRecurringList({ includeInactive: 1, excludeInstallment: 1 }),
      categoryApi.list('expense').catch(() => ({ data: [] })),
    ])
    list.value = listRes.data || []
    categories.value = catRes.data || []
  } catch (e) {
    showToast(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const toggleExpand = (item) => {
  if (expandedId.value === item.id) { expandedId.value = ''; return }
  expandedId.value = item.id
  const itemAmount = Number(item.amount || 0)
  editForm.value = {
    name: item.name || '', amount: itemAmount > 0 ? String(itemAmount) : '',
    day_of_cycle: item.day_of_cycle || 1, month_of_cycle: item.month_of_cycle || 1,
    cycle: item.cycle || 'month', end_date: item.end_date || '', remark: item.remark || '',
  }
}

const toggleActive = async (item, active) => {
  try {
    await updateRecurring(item.id, { is_active: active ? 1 : 0 })
    item.is_active = active ? 1 : 0
    showSuccessToast(active ? '已启用' : '已停用')
  } catch (e) { showToast(e.message || '操作失败') }
}

const getMonthRecords = (item) => {
  const records = item.month_records || {}
  const now = new Date()
  const thisMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  const nextMonthStr = `${nextMonth.getFullYear()}-${String(nextMonth.getMonth() + 1).padStart(2, '0')}`
  const allMonths = [...new Set([...Object.keys(records), thisMonth, nextMonthStr])]
  return allMonths.sort().reverse().slice(0, 14).map(month => ({
    month,
    status: records[month]?.status || 'pending',
    amount: records[month]?.amount !== undefined ? Number(records[month].amount) : Number(item.amount || 0),
    done_time: records[month]?.done_time || null,
  }))
}

const toggleMonthStatus = async (item, m) => {
  const nextStatus = m.status === 'done' ? 'pending' : 'done'
  try {
    await updateRecurringMonthStatus(item.id, { month: m.month, status: nextStatus })
    showSuccessToast(`${m.month} 已更新`)
    await loadData()
    // 保持展开，更新已展开项的 editForm
    const refreshed = list.value.find(i => i.id === item.id)
    if (refreshed) {
      const itemAmount = Number(refreshed.amount || 0)
      editForm.value = {
        name: refreshed.name || '', amount: itemAmount > 0 ? String(itemAmount) : '',
        day_of_cycle: refreshed.day_of_cycle || 1, month_of_cycle: refreshed.month_of_cycle || 1,
        cycle: refreshed.cycle || 'month', end_date: refreshed.end_date || '', remark: refreshed.remark || '',
      }
      expandedId.value = item.id
    }
  } catch (e) { showToast(e.message || '操作失败') }
}

const handleSave = async (item) => {
  savingId.value = item.id
  try {
    await updateRecurring(item.id, {
      name: editForm.value.name?.trim(),
      amount: Number(item.amount) > 0 ? Number(editForm.value.amount) : 0,
      day_of_cycle: Number(editForm.value.day_of_cycle),
      month_of_cycle: item.cycle === 'year' ? Number(editForm.value.month_of_cycle) : null,
      end_date: editForm.value.end_date?.trim() || null,
      remark: editForm.value.remark?.trim() || '',
    })
    showSuccessToast('保存成功')
    await loadData()
    expandedId.value = ''
  } catch (e) { showToast(e.message || '保存失败') } finally { savingId.value = '' }
}

const handleDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: '确认删除', message: `确定删除「${item.name}」？`,
      confirmButtonColor: '#ee0a24',
    })
    await deleteRecurring(item.id)
    showSuccessToast('已删除')
    expandedId.value = '';
    await loadData()
  } catch (e) { if (e !== 'cancel') showToast('删除失败') }
}

// 新增事件
const openAdd = () => {
  Object.assign(addForm, defaultAddForm())
  showAddForm.value = true
}

const onAddCategoryConfirm = ({ selectedOptions }) => {
  addForm.category_id = selectedOptions?.[0]?.value || ''
  addForm.categoryLabel = selectedOptions?.[0]?.text || ''
  showAddCategoryPicker.value = false
}

const openAddEndDatePicker = () => {
  if (addForm.end_date) {
    const [y, m, d] = addForm.end_date.split('-').map(Number)
    addEndDatePickerValue.value = [y, m, d]
  }
  showAddEndDatePicker.value = true
}

const onAddEndDateConfirm = ({ selectedValues }) => {
  const [year, month, day] = selectedValues
  addForm.end_date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  showAddEndDatePicker.value = false
}

const handleAdd = async () => {
  if (!addForm.name.trim()) return showToast('请输入名称')
  if (addForm.is_expense && (!addForm.amount || Number(addForm.amount) <= 0)) return showToast('请输入金额')
  adding.value = true
  try {
    await createRecurring({
      name: addForm.name.trim(),
      amount: addForm.is_expense ? Number(addForm.amount) : 0,
      category_id: addForm.is_expense ? (addForm.category_id || null) : null,
      cycle: addForm.cycle, day_of_cycle: Number(addForm.day_of_cycle),
      month_of_cycle: addForm.cycle === 'year' ? Number(addForm.month_of_cycle) : null,
      end_date: addForm.end_date?.trim() || null,
      remind_days: Number(addForm.remind_days || 0),
      remark: addForm.remark?.trim() || '',
      is_active: addForm.is_active ? 1 : 0,
    })
    showSuccessToast('创建成功')
    showAddForm.value = false
    await loadData()
  } catch (e) { showToast(e.message || '创建失败') } finally { adding.value = false }
}

const openEndDatePicker = () => {
  if (editForm.value.end_date) {
    const [y, m, d] = editForm.value.end_date.split('-').map(Number)
    endDatePickerValue.value = [y, m, d]
  }
  showEndDatePicker.value = true
}

const onEndDateConfirm = ({ selectedValues }) => {
  const [year, month, day] = selectedValues
  editForm.value.end_date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  showEndDatePicker.value = false
}

const goRecurring = () => router.push('/finance/recurring')

onMounted(() => loadData())
</script>

<style scoped>
.page-events { min-height: 100vh; padding: 12px 16px 110px; background: var(--theme-bg-primary); }
.header-card { background: linear-gradient(135deg, var(--theme-primary) 0%, var(--theme-primary-grad) 100%); border-radius: 12px; padding: 20px; margin-bottom: 16px; color: #fff; }
.header-title { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
.header-stats { display: flex; gap: 16px; font-size: 13px; opacity: 0.85; }
.section { margin-bottom: 14px; }
.section-title { font-size: 13px; font-weight: 600; color: var(--theme-text-tertiary); margin-bottom: 8px; padding-left: 4px; }
.event-card { background: var(--theme-bg-secondary); border-radius: 8px; padding: 14px; margin-bottom: 8px; }
.event-card.inactive { opacity: 0.55; }
.event-head { display: flex; align-items: center; justify-content: space-between; cursor: pointer; }
.event-name { font-size: 16px; font-weight: 600; color: var(--theme-text-primary); margin-bottom: 4px; }
.event-meta { font-size: 12px; color: var(--theme-text-tertiary); }
.end-hint { color: #ee0a24; }
.event-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.event-amount { font-size: 18px; font-weight: 700; color: #ee0a24; }
.event-amount.tag { font-size: 13px; font-weight: 400; color: var(--van-purple, #7232dd); background: rgba(114, 50, 221, 0.1); padding: 2px 10px; border-radius: 10px; }
.expand-icon { color: var(--theme-text-tertiary); font-size: 14px; }
.expand-body { margin-top: 4px; }
.toggle-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; font-size: 14px; color: var(--theme-text-primary); }
.expense-off, .expense-off-hint { color: var(--theme-text-tertiary); font-size: 13px; }
.month-records-section { margin-top: 12px; }
.mr-title { font-size: 13px; font-weight: 600; color: var(--theme-text-secondary); margin-bottom: 6px; }
.mr-row { display: flex; align-items: center; gap: 8px; padding: 8px 0; border: 1px solid var(--theme-border); font-size: 13px; }
.mr-month { color: var(--theme-text-primary); font-weight: 500; min-width: 64px; }
.mr-amount { color: #ee0a24; font-weight: 500; }
.expand-actions { display: flex; gap: 12px; margin-top: 16px; }
.expand-actions .van-button { flex: 1; }
.footer-actions { position: fixed; bottom: 24px; left: 16px; right: 16px; display: flex; gap: 12px; }
.footer-actions .van-button { flex: 1; }
.form-panel { padding: 18px 16px 22px; }
.form-title { text-align: center; font-weight: 600; margin-bottom: 12px; }
.form-actions { display: flex; gap: 12px; margin-top: 18px; }
.form-actions .van-button { flex: 1; }
</style>
