<template>
  <div class="page-salary-calendar">
    <!-- 顶部月份选择 -->
    <div class="header">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="month-title" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth + 1 }}月
        <van-icon name="arrow-down" />
      </span>
      <div class="header-actions">
        <van-icon name="setting-o" @click="goJobSetting" />
        <van-icon name="bars" @click="goStat" />
        <van-icon name="arrow" @click="nextMonth" />
      </div>
    </div>

    <!-- 顶部统计栏 -->
    <div class="stat-bar">
      <div class="stat-item main">
        <span class="stat-label">月总收入</span>
        <span class="stat-value income"
          >¥{{ monthData.total_income || "0.00" }}</span
        >
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-label">正式</span>
        <span class="stat-value blue"
          >¥{{ monthData.formal_total || "0.00" }}</span
        >
      </div>
      <div class="stat-item">
        <span class="stat-label">兼职</span>
        <span class="stat-value orange"
          >¥{{ monthData.parttime_total || "0.00" }}</span
        >
      </div>
    </div>

    <!-- 日历主体（共享 CalendarGrid 组件） -->
    <div class="calendar-grid-wrap">
      <CalendarGrid
        v-if="!loading"
        :year="currentYear"
        :month="currentMonth"
        :primary="'var(--theme-primary)'"
        variant="salary"
        :dataset="calendarDataset"
        :show-stat="false"
        :card="false"
        :show-header="false"
        @select="onSalarySelect"
      />
      <div v-else class="calendar-loading">
        <van-loading size="32px">加载中...</van-loading>
      </div>
    </div>

    <!-- 月份选择器 -->
    <app-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        title="选择月份"
        v-model="pickerSelectedValues"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
      />
    </app-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from "vue";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { getSalaryMonth, getJobList } from "@/utils/api/work";
import CalendarGrid from "@/components/calendar/CalendarGrid.vue";

const router = useRouter();

// 当前年月
const today = dayjs();
const currentYear = ref(today.year());
const currentMonth = ref(today.month());
const showMonthPicker = ref(false);
const pickerSelectedValues = ref([
  `${new Date().getFullYear()}年`,
  `${new Date().getMonth() + 1}月`,
]);
const loading = ref(false);

// 工作信息
const formalJob = ref(null);
const parttimeJobs = ref([]);

// 月度数据
const monthData = ref({
  formal_total: "0.00",
  parttime_total: "0.00",
  total_income: "0.00",
  daily_list: [],
});

// 月份选择器列
const pickerColumns = computed(() => {
  const years = [];
  const months = [];
  const now = new Date();
  const currentYear = now.getFullYear();

  for (let y = currentYear - 5; y <= currentYear + 5; y++) {
    years.push({ text: `${y}年`, value: `${y}年` });
  }
  for (let m = 1; m <= 12; m++) {
    months.push({ text: `${m}月`, value: `${m}月` });
  }

  return [years, months];
});

// 选择年月
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText) - 1;
  pickerSelectedValues.value = [yearText, monthText];
  showMonthPicker.value = false;
  loadMonthData();
};

// 上月
const prevMonth = () => {
  if (currentMonth.value === 0) {
    currentMonth.value = 11;
    currentYear.value--;
  } else {
    currentMonth.value--;
  }
  loadMonthData();
};

// 下月
const nextMonth = () => {
  if (currentMonth.value === 11) {
    currentMonth.value = 0;
    currentYear.value++;
  } else {
    currentMonth.value++;
  }
  loadMonthData();
};

// 判断日期是否在兼职时间段内
const isParttimeWorking = (dateStr) => {
  if (!dateStr) return false;
  return parttimeJobs.value.some((job) => {
    if (!job.join_date) return false;
    if (job.join_date > dateStr) return false;
    if (job.leave_date && job.leave_date < dateStr) return false;
    return true;
  });
};

// 判断正式工是否在职
const isFormalWorking = (dateStr) => {
  if (!formalJob.value || formalJob.value.status !== 1) return false;
  if (formalJob.value.join_date > dateStr) return false;
  if (formalJob.value.leave_date && formalJob.value.leave_date < dateStr)
    return false;
  return true;
};

// 日历天列表
// 后端数据 → CalendarGrid 的 dataset（按 YYYY-MM-DD 索引，含全部日期）
const calendarDataset = computed(() => {
  const map = {};
  const firstDay = dayjs()
    .year(currentYear.value)
    .month(currentMonth.value)
    .date(1);
  const daysInMonth = firstDay.daysInMonth();
  const dailyList = monthData.value.daily_list || [];

  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = firstDay.date(d).format("YYYY-MM-DD");
    const formalWorking = isFormalWorking(dateStr);
    const parttimeWorking = isParttimeWorking(dateStr);
    const notWorking = !formalWorking && !parttimeWorking;
    const dayRecord = dailyList.find((item) => item.date === dateStr);

    let formalIncome = 0;
    let parttimeTotal = 0;
    let parttimeCount = 0;
    if (dayRecord) {
      if (dayRecord.formal?.income) {
        formalIncome = parseFloat(dayRecord.formal.income) || 0;
      }
      if (dayRecord.parttimes?.length > 0) {
        dayRecord.parttimes.forEach((p) => {
          parttimeTotal += parseFloat(p.income) || 0;
        });
        parttimeCount = dayRecord.parttimes.length;
      }
    }

    map[dateStr] = {
      isWorkingDay: formalWorking || parttimeWorking,
      notWorking,
      formalIncome,
      parttimeTotal,
      parttimeCount,
    };
  }
  return map;
});

// 选择日期
const onSalarySelect = (date) => {
  const cell = calendarDataset.value[date];
  if (cell && cell.notWorking) return;
  router.push(`/work/salary-day?date=${date}`);
};

// 去工作设置
const goJobSetting = () => {
  router.push("/work/job-setting");
};

// 去统计页
const goStat = () => {
  router.push("/work/salary-stat");
};

// 加载工作信息
const loadJobInfo = async () => {
  try {
    const res = await getJobList();
    const list = Array.isArray(res.data) ? res.data : [];
    // 根据 job_type 分离正式工和兼职
    formalJob.value = list.find((item) => item.job_type === "formal") || null;
    parttimeJobs.value = list.filter((item) => item.job_type === "parttime");
  } catch (e) {
    console.error("加载工作信息失败", e);
  }
};

// 加载月度数据
const loadMonthData = async () => {
  loading.value = true;
  try {
    const res = await getSalaryMonth({
      year: currentYear.value,
      month: currentMonth.value + 1,
    });
    const data = res.data || {};
    monthData.value = {
      formal_total: data.formal_total || "0.00",
      parttime_total: data.parttime_total || "0.00",
      total_income: data.total_income || "0.00",
      daily_list: data.daily_list || [],
    };
  } catch (e) {
    console.error("加载月度数据失败", e);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadJobInfo();
  loadMonthData();
});

// 每次页面激活时重新加载数据（从其他页面返回时）
onActivated(() => {
  loadJobInfo();
  loadMonthData();
});
</script>

<style scoped>
.page-salary-calendar {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: var(--theme-bg-secondary);
}

.header .van-icon {
  font-size: 18px;
  padding: 6px;
  color: var(--van-green, #07c160);
}

.month-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions .van-icon {
  color: var(--theme-text-tertiary);
}

/* 统计栏 */
.stat-bar {
  display: flex;
  align-items: center;
  background: var(--theme-bg-secondary);
  padding: 16px;
  margin-bottom: 8px;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-item.main {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.stat-value.income {
  color: var(--van-green, #07c160);
}

.stat-value.blue {
  color: var(--theme-primary);
}

.stat-value.orange {
  color: var(--van-orange, #ff976a);
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: var(--theme-border);
}

/* 星期 */
.weekdays {
  display: flex;
  background: var(--theme-bg-secondary);
  padding: 12px 0;
  border: 1px solid var(--theme-border);
}

.weekday {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: var(--theme-text-tertiary);
}

/* 日历 */
.calendar-grid-wrap {
  background: var(--theme-bg-secondary);
  border: 1px solid var(--theme-border);
  border-top: none;
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: var(--theme-bg-secondary);
  padding: 0; /* ❗去掉 padding */
}

/* 核心修复 */
.day-cell {
  width: 100%;
  height: calc(100vw / 7); /* 👈 关键 */
  max-height: 80px; /* 可选，防止平板太大 */

  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  position: relative;
  cursor: pointer;
  gap: 2px;
}
.calendar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: var(--theme-bg-secondary);
}

.day-cell.empty {
  pointer-events: none;
}

.day-cell.not-working {
  opacity: 0.4;
  cursor: not-allowed;
}

.day-number {
  font-size: 15px;
  color: var(--theme-text-primary);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-cell.today .day-number {
  background: var(--van-green, #07c160);
  color: #fff;
  border-radius: 50%;
}

.day-amounts {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.amount {
  font-size: 10px;
  font-weight: 500;
}

.amount.fulltime {
  color: var(--theme-primary);
}

.amount.parttime {
  color: var(--van-orange, #ff976a);
}

.day-working {
  font-size: 10px;
  color: var(--theme-text-tertiary);
}

/* 颜色区分 */
.day-cell.has-fulltime .day-number {
  color: var(--theme-primary);
}

.day-cell.has-parttime .day-number {
  color: var(--van-orange, #ff976a);
}

/* .day-cell.has-both .day-number {
  color: var(--van-green, #07c160);
} */
</style>
