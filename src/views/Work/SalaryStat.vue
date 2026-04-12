<template>
  <div class="page-salary-stat">
    <!-- 顶部月份选择 -->
    <div class="header">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="month-title" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth }}月
        <van-icon name="arrow-down" />
      </span>
      <van-icon name="arrow" @click="nextMonth" />
    </div>

    <!-- 顶部统计卡片 -->
    <div class="stat-summary">
      <div class="summary-main">
        <span class="summary-label">月度总收入</span>
        <span class="summary-value">¥{{ monthData.total_income || '0.00' }}</span>
      </div>
      <div class="summary-details">
        <div class="detail-item">
          <span class="detail-label">正式工资</span>
          <span class="detail-value blue">¥{{ monthData.formal_total || '0.00' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">兼职收入</span>
          <span class="detail-value orange">¥{{ monthData.parttime_total || '0.00' }}</span>
        </div>
      </div>
    </div>

    <!-- 每日明细列表 -->
    <div class="daily-list">
      <div class="list-header">
        <span>每日明细</span>
        <span class="work-days">{{ validDays }}个计薪日</span>
      </div>

      <div
        v-for="item in monthData.daily_list"
        :key="item.date"
        class="daily-item"
        @click="goToDay(item.date)"
      >
        <div class="item-left">
          <span class="item-date">{{ formatDate(item.date) }}</span>
          <div class="item-tags">
            <van-tag v-if="item.formal?.income > 0" type="primary" size="small">
              正式 ¥{{ item.formal.income }}
            </van-tag>
            <van-tag v-if="item.parttimes?.length > 0" type="warning" size="small">
              兼职×{{ item.parttimes.length }}
            </van-tag>
          </div>
        </div>
        <div class="item-right">
          <span class="item-amount">¥{{ item.day_total || '0.00' }}</span>
          <van-icon name="arrow" class="arrow-icon" />
        </div>
      </div>

      <van-empty v-if="monthData.daily_list?.length === 0 && !loading" description="暂无工资记录" />
    </div>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        title="选择月份"
        v-model="pickerSelectedValues"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { getSalaryMonth } from '@/utils/api/work'

const router = useRouter()

const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)
const showMonthPicker = ref(false)
const pickerSelectedValues = ref([
  `${new Date().getFullYear()}年`,
  `${new Date().getMonth() + 1}月`,
])
const loading = ref(false)

// 月度数据
const monthData = ref({
  formal_total: '0.00',
  parttime_total: '0.00',
  total_income: '0.00',
  daily_list: [],
})

const validDays = computed(() => {
  return monthData.value.daily_list?.filter(item => item.day_total > 0).length || 0
})

// 月份选择器列
const pickerColumns = computed(() => {
  const years = []
  const months = []
  const now = new Date()
  const currentYear = now.getFullYear()

  for (let y = currentYear - 5; y <= currentYear + 5; y++) {
    years.push({ text: `${y}年`, value: `${y}年` })
  }
  for (let m = 1; m <= 12; m++) {
    months.push({ text: `${m}月`, value: `${m}月` })
  }

  return [years, months]
})

// 选择年月
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text
  const monthText = selectedOptions[1].text
  currentYear.value = parseInt(yearText)
  currentMonth.value = parseInt(monthText)
  pickerSelectedValues.value = [yearText, monthText]
  showMonthPicker.value = false
  loadMonthData()
}

// 上月
const prevMonth = () => {
  if (currentMonth.value === 1) {
    currentMonth.value = 12
    currentYear.value--
  } else {
    currentMonth.value--
  }
  loadMonthData()
}

// 下月
const nextMonth = () => {
  if (currentMonth.value === 12) {
    currentMonth.value = 1
    currentYear.value++
  } else {
    currentMonth.value++
  }
  loadMonthData()
}

// 格式化日期
const formatDate = (dateStr) => {
  return dayjs(dateStr).format('MM/DD')
}

// 跳转到当日详情
const goToDay = (date) => {
  router.push(`/work/salary-day?date=${date}`)
}

// 加载月度数据
const loadMonthData = async () => {
  loading.value = true
  try {
    const res = await getSalaryMonth({
      year: currentYear.value,
      month: currentMonth.value,
    })
    const data = res.data || {}

    monthData.value = {
      formal_total: data.formal_total || '0.00',
      parttime_total: data.parttime_total || '0.00',
      total_income: data.total_income || '0.00',
      daily_list: data.daily_list || [],
    }
  } catch (e) {
    console.error('加载月度数据失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMonthData()
})
</script>

<style scoped>
.page-salary-stat {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #fff;
}

.header .van-icon {
  font-size: 18px;
  padding: 6px;
  color: #07c160;
}

.month-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 统计汇总卡片 */
.stat-summary {
  background: linear-gradient(135deg, #07c160, #06ad56);
  margin: 12px 16px;
  border-radius: 16px;
  padding: 20px;
  color: #fff;
}

.summary-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.summary-label {
  font-size: 13px;
  opacity: 0.85;
}

.summary-value {
  font-size: 32px;
  font-weight: 700;
}

.summary-details {
  display: flex;
  gap: 20px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 12px;
  opacity: 0.75;
}

.detail-value {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.detail-value.blue {
  color: #a0cfff;
}

.detail-value.orange {
  color: #ffd0a8;
}

/* 每日明细 */
.daily-list {
  margin: 12px 16px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  border-bottom: 1px solid #f2f3f5;
}

.work-days {
  font-size: 13px;
  font-weight: 400;
  color: #969799;
}

.daily-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
}

.daily-item:last-child {
  border-bottom: none;
}

.item-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-date {
  font-size: 14px;
  color: #323233;
  font-weight: 500;
}

.item-tags {
  display: flex;
  gap: 4px;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-amount {
  font-size: 15px;
  font-weight: 600;
  color: #07c160;
}

.arrow-icon {
  color: #969799;
  font-size: 14px;
}
</style>
