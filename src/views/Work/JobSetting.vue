<template>
  <div class="page-job-setting">
    <!-- 正式工区块 -->
    <div class="job-section">
      <div class="section-header">
        <span class="section-title">正式工作</span>
        <van-tag :type="formalJob?.status === 1 ? 'success' : 'danger'">
          {{ formalJob?.status === 1 ? '在职' : '已离职' }}
        </van-tag>
      </div>

      <div class="job-card" :class="{ disabled: formalJob?.status !== 1 }">
        <!-- 有正式工数据 -->
        <div v-if="formalJob" class="job-display">
          <div class="job-info-grid">
            <div class="info-item">
              <span class="label">公司</span>
              <span class="value">{{ formalJob.company || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">月工资</span>
              <span class="value">¥{{ formalJob.base_salary || 0 }}</span>
            </div>
            <div class="info-item">
              <span class="label">出勤天数</span>
              <span class="value">{{ formalJob.base_work_days || 22 }}天</span>
            </div>
            <div class="info-item">
              <span class="label">日薪</span>
              <span class="value">¥{{ calculatedFormalDaily }}</span>
            </div>
            <div class="info-item">
              <span class="label">入职日期</span>
              <span class="value">{{ formalJob.join_date || '-' }}</span>
            </div>
            <div class="info-item" v-if="formalJob.status === 0">
              <span class="label">离职日期</span>
              <span class="value">{{ formalJob.leave_date || '-' }}</span>
            </div>
          </div>
          <div class="job-actions">
            <van-button size="small" type="primary" @click="editFormal">编辑</van-button>
          </div>
        </div>

        <!-- 无正式工数据 -->
        <div v-else class="empty-state">
          <van-empty description="暂无正式工作" />
          <van-button type="primary" size="small" @click="createFormal">创建正式工作</van-button>
        </div>
      </div>

      <!-- 正式工编辑弹窗 -->
      <van-popup v-model:show="showFormalPopup" position="bottom" round :close-on-click-overlay="false">
        <div class="edit-popup">
          <div class="popup-header">
            <span class="popup-title">{{ formalJob?.id ? '编辑正式工作' : '创建正式工作' }}</span>
            <van-icon name="cross" @click="closeFormalPopup" />
          </div>

          <van-form ref="formalFormRef">
            <van-cell-group inset>
              <van-field
                v-model="formalForm.company"
                label="公司名称"
                placeholder="请输入公司名称"
              />
              <van-field
                v-model="formalForm.base_salary"
                label="月基本工资"
                type="number"
                placeholder="请输入月工资"
              />
              <van-field
                v-model="formalForm.base_work_days"
                label="月应出勤天数"
                type="number"
                placeholder="默认22天"
              />
              <van-field
                label="日薪（自动）"
                :model-value="'¥' + calculatedFormalDaily"
                disabled
              />
            </van-cell-group>

            <van-cell-group inset title="补贴（元/月）">
              <van-field v-model="formalForm.subsidy_meal" label="餐补" type="number" />
              <van-field v-model="formalForm.subsidy_traffic" label="交通补贴" type="number" />
              <van-field v-model="formalForm.subsidy_post" label="岗位补贴" type="number" />
            </van-cell-group>

            <van-cell-group inset title="扣除（元/月）">
              <van-field v-model="formalForm.social" label="个人社保" type="number" />
              <van-field v-model="formalForm.fund" label="个人公积金" type="number" />
              <van-field v-model="formalForm.tax_rate" label="个税税率" type="number" suffix="%" />
            </van-cell-group>

            <van-cell-group inset>
              <van-field
                v-model="formalForm.join_date"
                label="入职日期"
                type="date"
                placeholder="请选择"
              />
              <!-- 已离职时显示重新入职按钮 -->
              <div v-if="formalJob?.status === 0" class="leave-btn-wrapper">
                <van-button
                  type="primary"
                  block
                  round
                  @click="rejoinJob"
                >
                  重新入职
                </van-button>
              </div>
              <!-- 在职时显示离职登记 -->
              <van-field
                v-if="formalJob?.status === 1"
                v-model="formalForm.leave_date"
                label="离职日期"
                type="date"
                placeholder="选择离职日期"
                :min-date="minLeaveDate"
                :max-date="maxLeaveDate"
              />
              <div v-if="formalJob?.status === 1" class="leave-btn-wrapper">
                <van-button
                  type="danger"
                  block
                  round
                  @click="confirmLeave"
                >
                  登记离职
                </van-button>
              </div>
            </van-cell-group>
          </van-form>

          <div class="popup-actions">
            <van-button size="large" round @click="closeFormalPopup">取消</van-button>
            <van-button size="large" round type="primary" :loading="saving" @click="saveFormal">保存</van-button>
          </div>
        </div>
      </van-popup>
    </div>

    <!-- 兼职工区块 -->
    <div class="job-section">
      <div class="section-header">
        <span class="section-title">兼职工作</span>
        <van-button size="small" type="primary" plain @click="addParttime">
          + 添加兼职
        </van-button>
      </div>

      <div v-if="parttimeList.length === 0" class="empty-card">
        <van-empty description="暂无兼职工作" />
      </div>

      <div
        v-for="job in parttimeList"
        :key="job.id"
        class="job-card parttime"
        :class="{ disabled: !isParttimeActive(job) }"
      >
        <div class="job-display">
          <div class="job-info-grid">
            <div class="info-item">
              <span class="label">单位</span>
              <span class="value">{{ job.company || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">时薪</span>
              <span class="value">¥{{ job.hourly_wage || 0 }}/小时</span>
            </div>
            <div class="info-item">
              <span class="label">开始日期</span>
              <span class="value">{{ job.join_date || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">结束日期</span>
              <span class="value">{{ job.leave_date || '长期' }}</span>
            </div>
            <div class="info-item">
              <span class="label">状态</span>
              <van-tag :type="isParttimeActive(job) ? 'success' : 'warning'" size="small">
                {{ isParttimeActive(job) ? '在职' : '已结束' }}
              </van-tag>
            </div>
          </div>
          <div class="job-actions">
            <van-button size="small" @click="editParttime(job)">编辑</van-button>
            <van-button size="small" type="danger" plain @click="removeParttime(job.id)">删除</van-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 兼职编辑弹窗 -->
    <van-popup v-model:show="showParttimePopup" position="bottom" round :close-on-click-overlay="false">
      <div class="edit-popup">
        <div class="popup-header">
          <span class="popup-title">{{ editingParttimeId ? '编辑兼职' : '添加兼职' }}</span>
          <van-icon name="cross" @click="closeParttimePopup" />
        </div>

        <van-form>
          <van-cell-group inset>
            <van-field
              v-model="parttimeForm.company"
              label="单位名称"
              placeholder="请输入单位名称"
            />
            <van-field
              v-model="parttimeForm.hourly_wage"
              label="时薪"
              type="number"
              placeholder="元/小时"
            />
          </van-cell-group>

          <van-cell-group inset title="补贴（元/天）">
            <van-field v-model="parttimeForm.subsidy_meal" label="餐补" type="number" />
            <van-field v-model="parttimeForm.subsidy_traffic" label="交通补贴" type="number" />
            <van-field v-model="parttimeForm.subsidy_post" label="岗位补贴" type="number" />
          </van-cell-group>

          <van-cell-group inset>
            <van-field
              v-model="parttimeForm.join_date"
              label="开始日期"
              type="date"
              placeholder="请选择"
            />
            <van-field
              v-model="parttimeForm.leave_date"
              label="结束日期"
              type="date"
              placeholder="不填表示长期"
            />
          </van-cell-group>
        </van-form>

        <div class="popup-actions">
          <van-button size="large" round @click="closeParttimePopup">取消</van-button>
          <van-button size="large" round type="primary" :loading="saving" @click="saveParttime">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { getJobList, saveJob, updateJob, deleteJob } from '@/utils/api/work'

const saving = ref(false)
const loading = ref(false)

// 离职日期范围
const minLeaveDate = computed(() => new Date())
const maxLeaveDate = computed(() => {
  const date = new Date()
  date.setDate(date.getDate() + 45)
  return date
})

// 正式工
const formalJob = ref(null)
const showFormalPopup = ref(false)

const formalForm = ref({
  company: '',
  base_salary: '',
  base_work_days: 22,
  subsidy_meal: '',
  subsidy_traffic: '',
  subsidy_post: '',
  social: '',
  fund: '',
  tax_rate: '',
  join_date: '',
  leave_date: '',
  status: 1,
})

// 计算正式工日薪
const calculatedFormalDaily = computed(() => {
  if (!formalForm.value.base_salary || !formalForm.value.base_work_days) {
    return '0.00'
  }
  return (parseFloat(formalForm.value.base_salary) / parseFloat(formalForm.value.base_work_days)).toFixed(2)
})

// 兼职列表
const parttimeList = ref([])
const showParttimePopup = ref(false)
const editingParttimeId = ref('')

const parttimeForm = ref({
  company: '',
  hourly_wage: '',
  subsidy_meal: '',
  subsidy_traffic: '',
  subsidy_post: '',
  join_date: '',
  leave_date: '',
})

// 判断兼职是否在职
const isParttimeActive = (job) => {
  if (!job.join_date) return false
  const today = new Date().toISOString().split('T')[0]
  if (job.join_date > today) return false
  if (job.leave_date && job.leave_date < today) return false
  return true
}

// 加载工作列表
const loadJobList = async () => {
  loading.value = true
  try {
    const res = await getJobList()
    const list = Array.isArray(res.data) ? res.data : []

    // 根据 job_type 分离正式工和兼职
    formalJob.value = list.find(item => item.job_type === 'formal') || null
    parttimeList.value = list.filter(item => item.job_type === 'parttime')
  } catch (e) {
    console.error('加载工作列表失败', e)
  } finally {
    loading.value = false
  }
}

// 创建正式工
const createFormal = () => {
  formalForm.value = {
    company: '',
    base_salary: '',
    base_work_days: 22,
    subsidy_meal: '',
    subsidy_traffic: '',
    subsidy_post: '',
    social: '',
    fund: '',
    tax_rate: '',
    join_date: '',
    leave_date: '',
    status: 1,
  }
  showFormalPopup.value = true
}

// 编辑正式工
const editFormal = () => {
  if (!formalJob.value) return
  formalForm.value = {
    ...formalJob.value,
    base_salary: formalJob.value.base_salary || '',
    base_work_days: formalJob.value.base_work_days || 22,
    subsidy_meal: formalJob.value.subsidy_meal || '',
    subsidy_traffic: formalJob.value.subsidy_traffic || '',
    subsidy_post: formalJob.value.subsidy_post || '',
    social: formalJob.value.social || '',
    fund: formalJob.value.fund || '',
    tax_rate: formalJob.value.tax_rate || '',
  }
  showFormalPopup.value = true
}

// 关闭正式工弹窗
const closeFormalPopup = () => {
  showFormalPopup.value = false
}

// 登记离职
const confirmLeave = async () => {
  try {
    await showConfirmDialog({
      title: '确认离职',
      message: '确定要登记离职吗？',
    })
    // 如果没有选择离职日期，默认今天
    if (!formalForm.value.leave_date) {
      formalForm.value.leave_date = new Date().toISOString().split('T')[0]
    }
    formalForm.value.status = 0
    // 保存
    await saveFormal()
  } catch {
    // 用户取消
  }
}

// 重新入职
const rejoinJob = async () => {
  try {
    await showConfirmDialog({
      title: '确认重新入职',
      message: '确定要重新入职吗？',
    })
    // 清空离职日期，状态改为在职
    formalForm.value.leave_date = ''
    formalForm.value.status = 1
    // 保存
    await saveFormal()
  } catch {
    // 用户取消
  }
}

// 保存正式工
const saveFormal = async () => {
  if (!formalForm.value.company) return showToast('请输入公司名称')
  if (!formalForm.value.base_salary) return showToast('请输入月基本工资')

  saving.value = true
  try {
    // 重新入职：传递 null 或空字符串让后端覆写
    const leaveDate = formalForm.value.leave_date === '' ? null : (formalForm.value.leave_date || null)

    const data = {
      job_type: 'formal',
      company: formalForm.value.company,
      base_salary: parseFloat(formalForm.value.base_salary) || 0,
      base_work_days: parseInt(formalForm.value.base_work_days) || 22,
      subsidy_meal: parseFloat(formalForm.value.subsidy_meal) || 0,
      subsidy_traffic: parseFloat(formalForm.value.subsidy_traffic) || 0,
      subsidy_post: parseFloat(formalForm.value.subsidy_post) || 0,
      social: parseFloat(formalForm.value.social) || 0,
      fund: parseFloat(formalForm.value.fund) || 0,
      tax_rate: parseFloat(formalForm.value.tax_rate) || 0,
      join_date: formalForm.value.join_date || null,
      leave_date: leaveDate,
      status: formalForm.value.status,
    }

    if (formalJob.value?.id) {
      await updateJob(formalJob.value.id, data)
    } else {
      await saveJob(data)
    }

    showToast('保存成功')
    showFormalPopup.value = false
    loadJobList()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 添加兼职
const addParttime = () => {
  editingParttimeId.value = ''
  parttimeForm.value = {
    company: '',
    hourly_wage: '',
    subsidy_meal: '',
    subsidy_traffic: '',
    subsidy_post: '',
    join_date: '',
    leave_date: '',
  }
  showParttimePopup.value = true
}

// 编辑兼职
const editParttime = (job) => {
  editingParttimeId.value = job.id
  parttimeForm.value = {
    company: job.company || '',
    hourly_wage: job.hourly_wage || '',
    subsidy_meal: job.subsidy_meal || '',
    subsidy_traffic: job.subsidy_traffic || '',
    subsidy_post: job.subsidy_post || '',
    join_date: job.join_date || '',
    leave_date: job.leave_date || '',
  }
  showParttimePopup.value = true
}

// 关闭兼职弹窗
const closeParttimePopup = () => {
  showParttimePopup.value = false
}

// 保存兼职
const saveParttime = async () => {
  if (!parttimeForm.value.company) return showToast('请输入单位名称')
  if (!parttimeForm.value.hourly_wage) return showToast('请输入时薪')

  saving.value = true
  try {
    const data = {
      job_type: 'parttime',
      company: parttimeForm.value.company,
      hourly_wage: parseFloat(parttimeForm.value.hourly_wage) || 0,
      subsidy_meal: parseFloat(parttimeForm.value.subsidy_meal) || 0,
      subsidy_traffic: parseFloat(parttimeForm.value.subsidy_traffic) || 0,
      subsidy_post: parseFloat(parttimeForm.value.subsidy_post) || 0,
      join_date: parttimeForm.value.join_date || '',
      leave_date: parttimeForm.value.leave_date || '',
    }

    if (editingParttimeId.value) {
      await updateJob(editingParttimeId.value, data)
    } else {
      await saveJob(data)
    }

    showToast('保存成功')
    showParttimePopup.value = false
    loadJobList()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 删除兼职
const removeParttime = async (id) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除这条兼职记录吗？',
      confirmButtonColor: '#ee0a24',
    })
    await deleteJob(id)
    showToast('删除成功')
    loadJobList()
  } catch (e) {
    if (e !== 'cancel') showToast('删除失败')
  }
}

onMounted(() => {
  loadJobList()
})
</script>

<style scoped>
.page-job-setting {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 16px;
  padding-bottom: 40px;
}

.job-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}

.job-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}

.job-card.disabled {
  opacity: 0.6;
}

.job-card.parttime {
  border-left: 4px solid #ff976a;
}

.job-card.formal {
  border-left: 4px solid #1989fa;
}

.job-display {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.job-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  flex: 1;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-item .label {
  font-size: 12px;
  color: #969799;
}

.info-item .value {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}

.job-actions {
  display: flex;
  gap: 8px;
}

.empty-state {
  text-align: center;
  padding: 16px 0;
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

.popup-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.popup-actions .van-button {
  flex: 1;
}

.empty-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
}

/* 登记离职按钮 */
.leave-btn-wrapper {
  padding: 16px 16px 8px;
}

.leave-btn-wrapper .van-button {
  font-weight: 600;
}
</style>
