<template>
  <div class="page-salary-day">
    <!-- 顶部日期选择 -->
    <div class="header">
      <van-icon name="arrow-left" @click="goPrevDay" />
      <span class="date-title">
        <van-icon name="calendar-o" />
        {{ currentDateStr }}
      </span>
      <van-icon name="arrow" @click="goNextDay" />
    </div>

    <!-- 状态提示 -->
    <div class="status-hint" v-if="statusHint">
      <van-icon name="info-o" />
      <span>{{ statusHint }}</span>
    </div>

    <!-- 正式工资卡片 -->
    <div
      class="salary-card fulltime"
      v-if="salaryData.formal && salaryData.formal.day_salary > 0"
    >
      <div class="card-header">
        <div class="card-title-row">
          <span class="card-title">{{ salaryData.formal.company || '正式工作' }}</span>
          <van-tag type="primary" v-if="salaryData.formal.status === 1">已确认</van-tag>
        </div>
      </div>

      <div class="salary-items">
        <div class="salary-item">
          <span class="item-label">日薪</span>
          <span class="item-value">¥{{ salaryData.formal.day_salary }}</span>
        </div>
        <div class="salary-item">
          <span class="item-label">社保扣除</span>
          <span class="item-value deduction">-¥{{ salaryData.formal.social }}</span>
        </div>
        <div class="salary-item">
          <span class="item-label">公积金扣除</span>
          <span class="item-value deduction">-¥{{ salaryData.formal.fund }}</span>
        </div>
        <div class="salary-item">
          <span class="item-label">个税扣除</span>
          <span class="item-value deduction">-¥{{ salaryData.formal.tax }}</span>
        </div>
        <div class="salary-item" v-if="salaryData.formal.cut > 0">
          <span class="item-label">其他扣款</span>
          <span class="item-value deduction">-¥{{ salaryData.formal.cut }}</span>
        </div>
        <van-divider style="margin: 8px 0" />
        <div class="salary-item total">
          <span class="item-label">当日实收</span>
          <span class="item-value income">¥{{ formalActualIncome }}</span>
        </div>
      </div>

      <div class="card-actions">
        <van-button size="small" type="primary" @click="editFormal">编辑</van-button>
      </div>
    </div>

    <!-- 兼职工资卡片列表 -->
    <div
      v-for="(item, index) in salaryData.parttimes"
      :key="item.job_id"
      class="salary-card parttime"
    >
      <div class="card-header">
        <div class="card-title-row">
          <span class="card-title">{{ item.company || '兼职' }}</span>
          <van-tag type="warning" v-if="item.status === 1">已确认</van-tag>
        </div>
      </div>

      <div class="salary-items">
        <div class="salary-item">
          <span class="item-label">时薪</span>
          <span class="item-value">¥{{ item.hourly_wage }}/小时</span>
        </div>
        <div class="salary-item">
          <span class="item-label">工作小时</span>
          <span class="item-value">{{ item.work_hours || 0 }} 小时</span>
        </div>
        <div class="salary-item">
          <span class="item-label">日薪</span>
          <span class="item-value">¥{{ item.day_salary }}</span>
        </div>
        <div class="salary-item">
          <span class="item-label">补贴</span>
          <span class="item-value">¥{{ item.subsidy }}</span>
        </div>
        <div class="salary-item" v-if="item.cut > 0">
          <span class="item-label">扣款</span>
          <span class="item-value deduction">-¥{{ item.cut }}</span>
        </div>
        <van-divider style="margin: 8px 0" />
        <div class="salary-item total">
          <span class="item-label">当日实收</span>
          <span class="item-value income">¥{{ item.income }}</span>
        </div>
      </div>

      <div class="card-actions">
        <van-button size="small" type="primary" @click="editParttime(index)">编辑</van-button>
      </div>
    </div>

    <!-- 正式工作待提交提示 -->
    <div class="pending-hint" v-if="formalJob?.status === 1 && !salaryData.formal?.id && parttimeJobs.length > 0">
      <van-icon name="clock-o" />
      <span>正式工资待提交</span>
    </div>

    <!-- 无工作提示 -->
    <van-empty
      v-if="!formalJob?.status && (!salaryData.parttimes || salaryData.parttimes.length === 0)"
      :description="statusHint || '当日无工资记录'"
    />

    <!-- 当日总计 -->
    <div class="total-card" v-if="totalIncome > 0">
      <div class="total-row">
        <span class="total-label">今日总收入</span>
        <span class="total-value">¥{{ totalIncome }}</span>
      </div>
      <!-- 提交正式工资按钮 -->
      <div class="submit-formal" v-if="formalJob?.status === 1 && !salaryData.formal?.id">
        <van-button
          size="small"
          type="primary"
          block
          :loading="submittingFormal"
          @click="submitFormalSalary"
        >
          提交正式工资
        </van-button>
      </div>
    </div>

    <!-- 删除按钮区域 -->
    <div class="delete-section" v-if="salaryData.formal || (salaryData.parttimes && salaryData.parttimes.length > 0)">
      <van-button
        size="small"
        type="default"
        :loading="deleting"
        @click="confirmDelete"
      >
        删除今日薪酬
      </van-button>
    </div>

    <!-- 编辑正式工弹窗 -->
    <van-popup :show="showEditFormal" position="bottom" round :close-on-click-overlay="false">
      <div class="edit-popup">
        <div class="popup-header">
          <span class="popup-title">编辑正式工资</span>
          <van-icon name="cross" @click="showEditFormal = false" />
        </div>

        <van-cell-group inset>
          <van-field label="日薪" :model-value="'¥' + salaryData.formal?.day_salary" disabled readonly />
          <van-field label="社保" :model-value="'-¥' + salaryData.formal?.social" disabled readonly />
          <van-field label="公积金" :model-value="'-¥' + salaryData.formal?.fund" disabled readonly />
          <van-field label="个税" :model-value="'-¥' + salaryData.formal?.tax" disabled readonly />
        </van-cell-group>

        <van-cell-group inset title="手动调整">
          <van-field
            v-model="editFormalData.cut"
            label="当日扣款"
            type="number"
            placeholder="如迟到、请假等"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
        </van-cell-group>

        <div class="preview-card">
          <span class="preview-label">实收（预览）</span>
          <span class="preview-value">¥{{ formalPreviewIncome }}</span>
        </div>

        <div class="popup-actions">
          <van-button size="large" round @click="showEditFormal = false">取消</van-button>
          <van-button size="large" round type="primary" :loading="saving" @click="saveFormal">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 编辑兼职弹窗 -->
    <van-popup :show="showEditParttime" position="bottom" round :close-on-click-overlay="false">
      <div class="edit-popup">
        <div class="popup-header">
          <span class="popup-title">编辑兼职 - {{ editingParttimeIndex !== -1 ? salaryData.parttimes[editingParttimeIndex]?.company : '' }}</span>
          <van-icon name="cross" @click="showEditParttime = false" />
        </div>

        <van-cell-group inset>
          <van-field
            label="时薪"
            :model-value="'¥' + (editingParttimeIndex !== -1 ? salaryData.parttimes[editingParttimeIndex]?.hourly_wage : 0) + '/小时'"
            disabled
            readonly
          />
          <van-field
            label="日薪"
            :model-value="'¥' + (editingParttimeIndex !== -1 ? salaryData.parttimes[editingParttimeIndex]?.day_salary : 0)"
            disabled
            readonly
          />
        </van-cell-group>

        <van-cell-group inset title="手动调整">
          <van-field
            v-model="editParttimeData.work_hours"
            label="工作小时"
            type="number"
          >
            <template #button><span class="yuan">小时</span></template>
          </van-field>
          <van-field
            v-model="editParttimeData.subsidy"
            label="当日补贴"
            type="number"
            placeholder="覆盖自动值"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
          <van-field
            v-model="editParttimeData.cut"
            label="当日扣款"
            type="number"
            placeholder="如迟到、请假等"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
        </van-cell-group>

        <div class="preview-card">
          <span class="preview-label">实收（预览）</span>
          <span class="preview-value">¥{{ parttimePreviewIncome }}</span>
        </div>

        <div class="popup-actions">
          <van-button size="large" round @click="showEditParttime = false">取消</van-button>
          <van-button size="large" round type="primary" :loading="saving" @click="saveEditingParttime">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import dayjs from 'dayjs'
import { getSalaryDay, saveSalaryDay, deleteSalaryDay, getJobList } from '@/utils/api/work'

const route = useRoute()

const currentDate = ref(route.query.date || dayjs().format('YYYY-MM-DD'))
const currentDateStr = computed(() => dayjs(currentDate.value).format('MM月DD日 dddd'))

// 状态
const loading = ref(false)
const saving = ref(false)
const submittingFormal = ref(false)
const deleting = ref(false)
const statusHint = ref('')

// 工作信息
const formalJob = ref(null)
const parttimeJobs = ref([])

// 工资数据
const salaryData = ref({
  formal: null,
  parttimes: [],
  total_income: '0.00',
})

// 编辑弹窗
const showEditFormal = ref(false)
const showEditParttime = ref(false)
const editingParttimeIndex = ref(-1)

const editFormalData = ref({
  cut: '',
})

const editParttimeData = ref({
  work_hours: 8,
  subsidy: '',
  cut: '',
})

// 预览计算
const formalPreviewIncome = computed(() => {
  if (!salaryData.value.formal) return '0.00'
  const base = parseFloat(salaryData.value.formal.day_salary) || 0
  const social = parseFloat(salaryData.value.formal.social) || 0
  const fund = parseFloat(salaryData.value.formal.fund) || 0
  const tax = parseFloat(salaryData.value.formal.tax) || 0
  const cut = parseFloat(editFormalData.value.cut) || 0

  return (base - social - fund - tax - cut).toFixed(2)
})

const parttimePreviewIncome = computed(() => {
  const idx = editingParttimeIndex.value
  if (idx === -1 || !salaryData.value.parttimes[idx]) return '0.00'

  const item = salaryData.value.parttimes[idx]
  const hourly = parseFloat(item.hourly_wage) || 0
  const hours = parseFloat(editParttimeData.value.work_hours) || 0
  const daySalary = hourly * hours
  const subsidy = parseFloat(editParttimeData.value.subsidy) || 0
  const cut = parseFloat(editParttimeData.value.cut) || 0

  return (daySalary + subsidy - cut).toFixed(2)
})

// 总收入
const totalIncome = computed(() => {
  // 使用前端计算的正式工实收
  let total = parseFloat(formalActualIncome.value) || 0
  if (salaryData.value.parttimes) {
    salaryData.value.parttimes.forEach(item => {
      total += parseFloat(item.income) || 0
    })
  }
  return total.toFixed(2)
})

// 正式工当日实收（前端计算）
const formalActualIncome = computed(() => {
  const f = salaryData.value.formal
  if (!f) return '0.00'
  const daySalary = parseFloat(f.day_salary) || 0
  const social = parseFloat(f.social) || 0
  const fund = parseFloat(f.fund) || 0
  const tax = parseFloat(f.tax) || 0
  const cut = parseFloat(f.cut) || 0
  return (daySalary - social - fund - tax - cut).toFixed(2)
})

// 上一天
const goPrevDay = () => {
  currentDate.value = dayjs(currentDate.value).subtract(1, 'day').format('YYYY-MM-DD')
}

// 下一天
const goNextDay = () => {
  currentDate.value = dayjs(currentDate.value).add(1, 'day').format('YYYY-MM-DD')
}

// 加载工作信息
const loadJobInfo = async () => {
  try {
    const res = await getJobList()
    const list = Array.isArray(res.data) ? res.data : []
    // 根据 job_type 分离正式工和兼职
    formalJob.value = list.find(item => item.job_type === 'formal') || null
    parttimeJobs.value = list.filter(item => item.job_type === 'parttime')
  } catch (e) {
    console.error('加载工作信息失败', e)
  }
}

// 加载工资数据
const loadSalaryData = async () => {
  loading.value = true
  try {
    // 构建 work_hours 参数
    const params = {
      work_date: currentDate.value,
    }

    const res = await getSalaryDay(params)
    const data = res.data || {}

    salaryData.value = {
      formal: data.formal ? { 
        ...data.formal, 
        job_id: data.formal.job_id || data.formal.id,
      } : null,
      parttimes: (data.parttimes || []).map((p, idx) => ({ 
        ...p, 
        id: p.job_id || p.id || idx,  // 确保有 id
        job_id: p.job_id || p.id,      // 确保有 job_id
      })),
      total_income: data.total_income || '0.00',
    }

    // 状态提示
    if (!formalJob.value && parttimeJobs.value.length === 0) {
      statusHint.value = '请先设置工作信息'
    } else if (!formalJob.value?.status && parttimeJobs.value.length === 0) {
      statusHint.value = '当日不计薪（已离职）'
    } else {
      statusHint.value = ''
    }
  } catch (e) {
    console.error('加载工资数据失败', e)
    statusHint.value = '加载失败'
  } finally {
    loading.value = false
  }
}

// 计算兼职收入
// 编辑正式工
const editFormal = () => {
  editFormalData.value = {
    cut: '',
  }
  showEditFormal.value = true
}

// 保存正式工
const saveFormal = async () => {
  if (!salaryData.value.formal) return

  saving.value = true
  try {
    await saveSalaryDay({
      job_id: salaryData.value.formal.job_id,
      work_date: currentDate.value,
      cut: parseFloat(editFormalData.value.cut) || 0,
    })
    showToast('保存成功')
    showEditFormal.value = false
    loadSalaryData()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 提交正式工资（首次提交）
const submitFormalSalary = async () => {
  if (!formalJob.value) return

  submittingFormal.value = true
  try {
    // 只提交日薪，补贴不纳入每日薪酬
    await saveSalaryDay({
      job_id: formalJob.value.id,
      work_date: currentDate.value,
    })
    showToast('提交成功')
    loadSalaryData()
  } catch (e) {
    showToast(e.message || '提交失败')
  } finally {
    submittingFormal.value = false
  }
}

// 确认删除
const confirmDelete = async () => {
  if (!salaryData.value.formal && (!salaryData.value.parttimes || salaryData.value.parttimes.length === 0)) return

  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除今日的所有薪酬记录吗？',
    })
    await deleteSalary()
  } catch {
    // 用户取消
  }
}

// 删除今日薪酬
const deleteSalary = async () => {
  deleting.value = true
  try {
    await deleteSalaryDay({
      work_date: currentDate.value,
    })
    showToast('删除成功')
    loadSalaryData()
  } catch (e) {
    showToast(e.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

// 编辑兼职
const editParttime = (index) => {
  editingParttimeIndex.value = index
  const item = salaryData.value.parttimes[index]
  editParttimeData.value = {
    work_hours: item.work_hours || 8,
    subsidy: '',
    cut: '',
  }
  showEditParttime.value = true
}

// 保存编辑的兼职
const saveEditingParttime = async () => {
  const idx = editingParttimeIndex.value
  if (idx === -1) return

  saving.value = true
  try {
    const item = salaryData.value.parttimes[idx]
    // 备用：从 parttimeJobs 查找 job_id
    const jobId = item.job_id || item.id || parttimeJobs.value[idx]?.id
    if (!jobId) {
      showToast('无法获取工作信息')
      saving.value = false
      return
    }
    await saveSalaryDay({
      job_id: jobId,
      work_date: currentDate.value,
      work_hours: parseFloat(editParttimeData.value.work_hours) || 8,
      subsidy_meal: parseFloat(editParttimeData.value.subsidy) || 0,
      cut: parseFloat(editParttimeData.value.cut) || 0,
    })
    showToast('保存成功')
    showEditParttime.value = false
    loadSalaryData()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 监听日期变化
watch(currentDate, () => {
  loadSalaryData()
})

onMounted(() => {
  loadJobInfo()
  loadSalaryData()
})

// 监听路由参数变化，重新加载数据
watch(() => route.query.date, (newDate) => {
  if (newDate) {
    currentDate.value = newDate
    loadJobInfo()
    loadSalaryData()
  }
})
</script>

<style scoped>
.page-salary-day {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 16px;
  padding-bottom: 40px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  margin-bottom: 12px;
}

.header .van-icon {
  font-size: 18px;
  color: #07c160;
  padding: 6px;
}

.date-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: #fff7e6;
  color: #ff976a;
  font-size: 13px;
  border-radius: 12px;
  margin-bottom: 12px;
}

/* 工资卡片 */
.salary-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}

.salary-card.fulltime {
  border-left: 4px solid #1989fa;
}

.salary-card.parttime {
  border-left: 4px solid #ff976a;
}

.card-header {
  margin-bottom: 12px;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}

.salary-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.salary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.salary-item.hours-row {
  gap: 8px;
}

.item-label {
  font-size: 14px;
  color: #646566;
}

.item-value {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}

.item-value.deduction {
  color: #ee0a24;
}

.item-value.income {
  font-size: 18px;
  color: #07c160;
  font-weight: 700;
}

.salary-item.total {
  padding-top: 8px;
}

.unit {
  font-size: 14px;
  color: #969799;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

/* 待提交提示 */
.pending-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  margin: 8px 0;
  background: #fff7e6;
  color: #ff976a;
  font-size: 13px;
  border-radius: 8px;
}

.pending-hint .van-icon {
  font-size: 16px;
}

/* 总计卡片 */
.total-card {
  background: linear-gradient(135deg, #07c160, #06ad56);
  border-radius: 12px;
  padding: 16px;
  color: #fff;
  margin-top: 12px;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-label {
  font-size: 14px;
  opacity: 0.9;
}

.total-value {
  font-size: 24px;
  font-weight: 700;
}

.submit-formal {
  margin-top: 12px;
}

.submit-formal .van-button {
  background: #fff;
  color: #07c160;
  border: 1px solid #fff;
}

/* 删除区域 */
.delete-section {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-bottom: 20px;
}

.delete-section .van-button {
  color: #ee0a24;
}

/* 编辑弹窗 */
.edit-popup {
  padding: 16px;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.popup-title {
  font-size: 17px;
  font-weight: 600;
  color: #323233;
}

.yuan {
  color: #969799;
  font-size: 14px;
}

.preview-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #07c160, #06ad56);
  border-radius: 12px;
  color: #fff;
}

.preview-label {
  font-size: 14px;
  opacity: 0.9;
}

.preview-value {
  font-size: 24px;
  font-weight: 700;
}

.popup-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.popup-actions .van-button {
  flex: 1;
}
</style>
