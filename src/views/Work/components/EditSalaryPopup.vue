<template>
  <van-popup
    :show="show"
    position="bottom"
    round
    close-on-click-overlay
    @update:show="$emit('update:show', $event)"
  >
    <div class="edit-popup">
      <div class="popup-header">
        <span class="popup-title">编辑工资</span>
        <span class="popup-date">{{ formatDate(date) }}</span>
      </div>

      <!-- 工作类型选择 -->
      <van-tabs v-model:active="activeType" shrink>
        <van-tab title="正式工作" name="fulltime" />
        <van-tab title="兼职工作" name="parttime" />
      </van-tabs>

      <!-- 正式工作编辑 -->
      <div v-if="activeType === 'fulltime'" class="edit-content">
        <van-cell-group inset>
          <van-field label="基本日薪" :model-value="'¥' + localFulltime.dailySalary" disabled readonly />
          <van-field label="补贴合计" :model-value="'¥' + localFulltime.subsidyTotal" disabled readonly />
          <van-field label="社保扣除" :model-value="'-¥' + localFulltime.socialSecurity" disabled readonly />
          <van-field label="公积金扣除" :model-value="'-¥' + localFulltime.housingFund" disabled readonly />
          <van-field label="个税扣除" :model-value="'-¥' + localFulltime.tax" disabled readonly />
        </van-cell-group>

        <van-cell-group inset title="手动调整">
          <van-field
            v-model="localFulltime.customSubsidy"
            label="当日补贴"
            type="number"
            placeholder="覆盖自动计算值"
            @update:model-value="calcFulltimeActual"
          >
            <template #button>
              <span class="yuan">元</span>
            </template>
          </van-field>
          <van-field
            v-model="localFulltime.customDeduction"
            label="当日扣款"
            type="number"
            placeholder="如罚款、缺勤等"
            @update:model-value="calcFulltimeActual"
          >
            <template #button>
              <span class="yuan">元</span>
            </template>
          </van-field>
        </van-cell-group>

        <div class="preview-card">
          <span class="preview-label">当日实收（预览）</span>
          <span class="preview-value">¥{{ fulltimeActual }}</span>
        </div>
      </div>

      <!-- 兼职工作编辑 -->
      <div v-else class="edit-content">
        <van-cell-group inset>
          <van-field label="兼职日薪" :model-value="'¥' + localParttime.dailySalary" disabled readonly />
          <van-field label="补贴合计" :model-value="'¥' + localParttime.subsidyTotal" disabled readonly />
        </van-cell-group>

        <van-cell-group inset title="手动调整">
          <van-field
            v-model="localParttime.customSubsidy"
            label="当日补贴"
            type="number"
            placeholder="覆盖自动计算值"
            @update:model-value="calcParttimeActual"
          >
            <template #button>
              <span class="yuan">元</span>
            </template>
          </van-field>
          <van-field
            v-model="localParttime.customDeduction"
            label="当日扣款"
            type="number"
            placeholder="如罚款、缺勤等"
            @update:model-value="calcParttimeActual"
          >
            <template #button>
              <span class="yuan">元</span>
            </template>
          </van-field>
        </van-cell-group>

        <div class="preview-card">
          <span class="preview-label">当日实收（预览）</span>
          <span class="preview-value">¥{{ parttimeActual }}</span>
        </div>
      </div>

      <!-- 按钮 -->
      <div class="popup-actions">
        <van-button size="large" round @click="handleReset">重置</van-button>
        <van-button size="large" round type="primary" :loading="saving" @click="handleSave">
          保存
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import dayjs from 'dayjs'
import { showToast } from 'vant'
import { saveSalaryDay } from '@/utils/api/work'

const props = defineProps({
  show: Boolean,
  date: String,
  fulltimeData: Object,
  parttimeData: Object,
  jobInfo: Object,
})

const emit = defineEmits(['update:show', 'success'])

const activeType = ref('fulltime')
const saving = ref(false)

// 本地编辑数据
const localFulltime = ref({
  dailySalary: '0.00',
  subsidyTotal: '0.00',
  socialSecurity: '0.00',
  housingFund: '0.00',
  tax: '0.00',
  customSubsidy: '',
  customDeduction: '',
})

const localParttime = ref({
  dailySalary: '0.00',
  subsidyTotal: '0.00',
  customSubsidy: '',
  customDeduction: '',
})

// 计算实收
const fulltimeActual = computed(() => {
  const base = parseFloat(localFulltime.value.dailySalary) || 0
  const subsidy = parseFloat(localFulltime.value.customSubsidy) ||
    parseFloat(localFulltime.value.subsidyTotal) || 0
  const socialSecurity = parseFloat(localFulltime.value.socialSecurity) || 0
  const housingFund = parseFloat(localFulltime.value.housingFund) || 0
  const tax = parseFloat(localFulltime.value.tax) || 0
  const deduction = parseFloat(localFulltime.value.customDeduction) || 0

  const actual = base + subsidy - socialSecurity - housingFund - tax - deduction
  return actual.toFixed(2)
})

const parttimeActual = computed(() => {
  const base = parseFloat(localParttime.value.dailySalary) || 0
  const subsidy = parseFloat(localParttime.value.customSubsidy) ||
    parseFloat(localParttime.value.subsidyTotal) || 0
  const deduction = parseFloat(localParttime.value.customDeduction) || 0

  const actual = base + subsidy - deduction
  return actual.toFixed(2)
})

const calcFulltimeActual = () => {
  // 触发响应式更新
}

const calcParttimeActual = () => {
  // 触发响应式更新
}

// 格式化日期
const formatDate = (dateStr) => {
  return dayjs(dateStr).format('YYYY年MM月DD日')
}

// 监听数据变化
watch(() => props.fulltimeData, (val) => {
  if (val) {
    localFulltime.value = {
      dailySalary: val.dailySalary || '0.00',
      subsidyTotal: val.subsidyTotal || '0.00',
      socialSecurity: val.socialSecurity || '0.00',
      housingFund: val.housingFund || '0.00',
      tax: val.tax || '0.00',
      customSubsidy: '',
      customDeduction: val.otherDeduction ? String(val.otherDeduction) : '',
    }
  }
}, { immediate: true })

watch(() => props.parttimeData, (val) => {
  if (val) {
    localParttime.value = {
      dailySalary: val.dailySalary || '0.00',
      subsidyTotal: val.subsidyTotal || '0.00',
      customSubsidy: '',
      customDeduction: val.otherDeduction ? String(val.otherDeduction) : '',
    }
  }
}, { immediate: true })

// 保存
const handleSave = async () => {
  saving.value = true
  try {
    const data = {
      date: props.date,
    }

    if (activeType.value === 'fulltime') {
      data.type = 'fulltime'
      data.subsidies = parseFloat(localFulltime.value.customSubsidy) || 0
      data.deductions = parseFloat(localFulltime.value.customDeduction) || 0
    } else {
      data.type = 'parttime'
      data.subsidies = parseFloat(localParttime.value.customSubsidy) || 0
      data.deductions = parseFloat(localParttime.value.customDeduction) || 0
    }

    await saveSalaryDay(data)
    showToast('保存成功')
    emit('update:show', false)
    emit('success')
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 重置
const handleReset = () => {
  if (activeType.value === 'fulltime') {
    localFulltime.value.customSubsidy = ''
    localFulltime.value.customDeduction = ''
  } else {
    localParttime.value.customSubsidy = ''
    localParttime.value.customDeduction = ''
  }
}
</script>

<style scoped>
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

.popup-date {
  font-size: 14px;
  color: #969799;
}

.edit-content {
  margin-top: 16px;
}

.yuan {
  color: #969799;
  font-size: 14px;
}

.preview-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 16px;
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
