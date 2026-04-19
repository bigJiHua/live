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

    <!-- 星期标题 -->
    <div class="weekdays">
      <div v-for="day in weekDays" :key="day" class="weekday">{{ day }}</div>
    </div>

    <!-- 日历主体 -->
    <div class="calendar-grid" v-if="!loading">
      <div
        v-for="(day, index) in calendarDays"
        :key="index"
        class="day-cell"
        :class="getDayCellClass(day)"
        @click="selectDate(day)"
      >
        <span class="day-number">{{ day.day }}</span>
        <!-- 有工资记录 -->
        <div v-if="day.hasRecord" class="day-amounts">
          <span v-if="day.formalIncome > 0" class="amount fulltime"
            >¥{{ day.formalIncome }}</span
          >
          <span v-if="day.parttimeCount > 0" class="amount parttime"
            >¥{{ day.parttimeTotal }}</span
          >
        </div>
        <!-- 计薪日但无记录 -->
        <span
          v-else-if="day.isWorkingDay && !day.notWorking"
          class="day-working"
          >计薪</span
        >
      </div>
    </div>

    <div class="calendar-loading" v-else>
      <van-loading size="32px">加载中...</van-loading>
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
import { ref, computed, onMounted, onActivated } from "vue";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { getSalaryMonth, getJobList } from "@/utils/api/work";

const router = useRouter();
const weekDays = ["日", "一", "二", "三", "四", "五", "六"];

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
const calendarDays = computed(() => {
  const days = [];
  const firstDay = dayjs()
    .year(currentYear.value)
    .month(currentMonth.value)
    .date(1);
  const daysInMonth = firstDay.daysInMonth();
  const startWeekday = firstDay.day();
  const dailyList = monthData.value.daily_list || [];
  const todayStr = dayjs().format("YYYY-MM-DD");

  // 空白填充
  for (let i = 0; i < startWeekday; i++) {
    days.push({ day: null, empty: true });
  }

  // 日期
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = firstDay.date(d).format("YYYY-MM-DD");
    const dayRecord = dailyList.find((item) => item.date === dateStr);
    const isToday = dateStr === todayStr;

    // 正式工是否在职
    const formalWorking = isFormalWorking(dateStr);
    // 兼职是否在职
    const parttimeWorking = isParttimeWorking(dateStr);
    // 不计薪（都不在职）
    const notWorking = !formalWorking && !parttimeWorking;

    // 当日收入
    let formalIncome = 0;
    let parttimeTotal = 0;
    let parttimeCount = 0;

    if (dayRecord) {
      // 确保数值转换
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

    days.push({
      day: d,
      date: dateStr,
      isToday,
      isWorkingDay: formalWorking || parttimeWorking,
      notWorking,
      formalIncome,
      parttimeTotal,
      parttimeCount,
      hasRecord: formalIncome > 0 || parttimeTotal > 0,
    });
  }
  return days;
});

// 获取日期格子样式
const getDayCellClass = (day) => {
  if (!day.day) return "empty";
  const classes = [];

  if (day.isToday) classes.push("today");
  if (day.notWorking) classes.push("not-working");
  else if (day.hasRecord) {
    if (day.formalIncome > 0 && day.parttimeCount > 0) {
      classes.push("has-both");
    } else if (day.formalIncome > 0) {
      classes.push("has-fulltime");
    } else if (day.parttimeTotal > 0) {
      classes.push("has-parttime");
    }
  } else if (day.isWorkingDay) {
    classes.push("working-day");
  }

  return classes.join(" ");
};

// 选择日期
const selectDate = (day) => {
  if (!day?.date || day.empty || day.notWorking) return;
  router.push(`/work/salary-day?date=${day.date}`);
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions .van-icon {
  color: #969799;
}

/* 统计栏 */
.stat-bar {
  display: flex;
  align-items: center;
  background: #fff;
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
  color: #969799;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}

.stat-value.income {
  color: #07c160;
}

.stat-value.blue {
  color: #1989fa;
}

.stat-value.orange {
  color: #ff976a;
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: #ebedf0;
}

/* 星期 */
.weekdays {
  display: flex;
  background: #fff;
  padding: 12px 0;
  border-bottom: 1px solid #f2f3f5;
}

.weekday {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: #969799;
}

/* 日历 */
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: #fff;
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
  background: #fff;
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
  color: #323233;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.day-cell.today .day-number {
  background: #07c160;
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
  color: #1989fa;
}

.amount.parttime {
  color: #ff976a;
}

.day-working {
  font-size: 10px;
  color: #969799;
}

/* 颜色区分 */
.day-cell.has-fulltime .day-number {
  color: #1989fa;
}

.day-cell.has-parttime .day-number {
  color: #ff976a;
}

/* .day-cell.has-both .day-number {
  color: #07c160;
} */
</style>
