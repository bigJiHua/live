<template>
  <div class="page-calendar">
    <!-- 顶部月份选择 -->
    <div class="header">
      <van-icon name="arrow-left" @click="prevMonth" />
      <span class="month-title" @click="showMonthPicker = true">
        {{ currentYear }}年{{ currentMonth + 1 }}月
        <van-icon name="arrow-down" />
      </span>
      <div class="header-actions">
        <van-icon :name="showAirplane ? 'eye-o' : 'closed-eye'" @click="toggleAirplane" />
        <van-icon name="arrow" @click="nextMonth" />
      </div>
    </div>

    <!-- 提醒横幅 -->
    <div v-if="reminders.length > 0" class="reminder-banner" :class="{ 'has-reminders': reminders.length > 0 }">
      <div class="reminder-header">
        <van-icon name="bell" />
        <span>待办提醒</span>
        <span class="reminder-count">{{ reminders.length }}个</span>
      </div>
      <div class="reminder-list">
        <div
          v-for="item in reminders"
          :key="item.id"
          class="reminder-item"
          :class="'reminder-' + getReminderBannerLevel(item)"
        >
          <span class="reminder-content">{{ item.content }}</span>
          <span class="reminder-date">{{ dayjs(item.happen_date).format("MM/DD") }}</span>
        </div>
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
        :class="{
          empty: !day.day,
          today: day.isToday,
          selected: day.date === selectedDate,
          'has-event': day.count > 0,
          'has-reminder-red': day.reminderFlash?.level === 'red',
          'has-reminder-yellow': day.reminderFlash?.level === 'yellow',
          'has-reminder-green': day.reminderFlash?.level === 'green',
        }"
        @click="selectDate(day)"
      >
        <span class="day-number">{{ day.day }}</span>
        <div v-if="showAirplane && day.hasAirplane" class="airplane-icon">✈️</div>
        <div
          v-else-if="day.count > 0"
          class="event-dot"
          :class="{ overdue: day.hasOverdue }"
        ></div>
      </div>
    </div>

    <!-- 加载中 -->
    <div class="calendar-loading" v-else>
      <van-loading size="32px">加载中...</van-loading>
    </div>

    <!-- 添加按钮 -->
    <div class="add-btn" @click="goToAddEvent">
      <van-icon name="plus" />
    </div>

    <!-- 选中日期的事件列表 -->
    <div class="event-list">
      <div class="event-list-header">
        <span>{{ formatSelectedDate }}</span>
        <span class="event-count" v-if="selectedEvents.length > 0">
          {{ selectedEvents.length }}个计划
        </span>
      </div>

      <van-pull-refresh v-model="refreshing" @refresh="loadSelectedDateEvents">
        <van-empty
          v-if="!loadingEvents && selectedEvents.length === 0"
          description="暂无计划"
          image="search"
        />
        <div v-else-if="loadingEvents" class="loading-wrap">
          <van-loading size="24px">加载中...</van-loading>
        </div>

        <div v-else class="event-items">
          <div
            v-for="event in selectedEvents"
            :key="event.id"
            class="event-item"
            :class="{ completed: event.status === '已完成' }"
            @click="showEventActions(event)"
          >
            <div class="event-content">
              <div class="event-title">{{ event.content }}</div>
              <div class="event-meta">
                <van-tag v-if="event.event_type" size="small" type="primary">
                  {{ getEventTypeName(event.event_type) }}
                </van-tag>
                <van-tag v-if="event.priority === 1" size="small" type="danger"
                  >高优</van-tag
                >
                <van-tag v-if="event.is_recurring" size="small" type="success"
                  >每年</van-tag
                >
              </div>
              <div class="event-remark" v-if="event.remark">
                {{ event.remark }}
              </div>
            </div>
            <van-icon name="arrow" class="arrow-icon" />
          </div>
        </div>
      </van-pull-refresh>
    </div>

    <!-- 事件操作弹出菜单 -->
    <van-action-sheet
      v-model:show="showActions"
      :actions="actionOptions"
      cancel-text="取消"
      @select="onActionSelect"
    />

    <!-- 编辑事件弹窗 -->
    <van-popup
      v-model:show="showEditPopup"
      position="bottom"
      round
      close-on-click-overlay
    >
      <div class="add-event-popup">
        <div class="popup-header">
          <span class="popup-title">编辑事件</span>
        </div>

        <van-field
          v-model="editEvent.content"
          label="事件内容"
          placeholder="请输入事件内容"
          :maxlength="100"
        />

        <van-field
          v-model="editEvent.remark"
          label="备注"
          placeholder="选填（最多50字）"
          :maxlength="50"
        />

        <van-field name="event_type" label="事件类型">
          <template #input>
            <van-radio-group v-model="editEvent.event_type" direction="horizontal">
              <van-radio name="schedule">日程</van-radio>
              <van-radio name="birthday">生日</van-radio>
              <van-radio name="anniversary">纪念日</van-radio>
              <van-radio name="countdown">倒数日</van-radio>
            </van-radio-group>
          </template>
        </van-field>

        <van-field name="priority" label="优先级">
          <template #input>
            <van-radio-group v-model="editEvent.priority" direction="horizontal">
              <van-radio :name="1">高</van-radio>
              <van-radio :name="2">中</van-radio>
              <van-radio :name="3">低</van-radio>
            </van-radio-group>
          </template>
        </van-field>

        <van-field name="is_recurring" label="每年重复">
          <template #input>
            <van-switch v-model="editEvent.is_recurring" />
          </template>
        </van-field>

        <van-field name="need_remind" label="开启提醒">
          <template #input>
            <van-switch v-model="editEvent.need_remind" />
          </template>
        </van-field>

        <van-field
          v-if="editEvent.need_remind"
          v-model="editEvent.remind_days"
          label="提前天数"
          type="number"
          placeholder="0"
        >
          <template #input>
            <van-stepper v-model="editEvent.remind_days" min="0" max="30" />
          </template>
        </van-field>

        <div class="popup-actions">
          <van-button size="large" round @click="showEditPopup = false"
            >取消</van-button
          >
          <van-button
            size="large"
            round
            type="primary"
            :loading="submitting"
            @click="handleEditEvent"
          >
            保存修改
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 添加事件弹窗 -->
    <van-popup
      v-model:show="showAddPopup"
      position="bottom"
      round
      close-on-click-overlay
    >
      <div class="add-event-popup">
        <div class="popup-header">
          <span class="popup-title">添加事件</span>
          <span class="popup-date">{{ formatSelectedDate }}</span>
        </div>

        <van-field
          v-model="newEvent.content"
          label="事件内容"
          placeholder="请输入事件内容"
          :maxlength="100"
        />
        <van-field
          v-model="newEvent.remark"
          label="备注"
          placeholder="选填"
          :maxlength="50"
        />

        <van-field label="事件类型">
          <template #input>
            <van-radio-group
              v-model="newEvent.event_type"
              direction="horizontal"
            >
              <van-radio name="schedule">日程</van-radio>
              <van-radio name="birthday">生日</van-radio>
              <van-radio name="anniversary">纪念</van-radio>
              <van-radio name="countdown">倒数</van-radio>
            </van-radio-group>
          </template>
        </van-field>

        <van-field label="优先级">
          <template #input>
            <van-radio-group v-model="newEvent.priority" direction="horizontal">
              <van-radio :name="1">高</van-radio>
              <van-radio :name="2">中</van-radio>
              <van-radio :name="3">低</van-radio>
            </van-radio-group>
          </template>
        </van-field>

        <van-field label="每年重复">
          <template #input>
            <van-switch v-model="newEvent.is_recurring" />
          </template>
        </van-field>

        <van-field label="开启提醒">
          <template #input>
            <van-switch v-model="newEvent.need_remind" />
          </template>
        </van-field>

        <van-field
          v-if="newEvent.need_remind"
          v-model="newEvent.remind_days"
          label="提前天数"
          type="number"
        >
          <template #input>
            <van-stepper v-model="newEvent.remind_days" min="0" max="30" />
          </template>
        </van-field>

        <div class="popup-actions">
          <van-button size="large" round @click="showAddPopup = false"
            >取消</van-button
          >
          <van-button
            size="large"
            round
            type="primary"
            :loading="submitting"
            @click="handleAddEvent"
          >
            确认
          </van-button>
        </div>
      </div>
    </van-popup>

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
import { ref, computed, onMounted, watch } from "vue";
import { showToast, showConfirmDialog, showSuccessToast } from "vant";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";

import {
  getCalendarMonth,
  getTodoList,
  createTodo,
  updateTodo,
  deleteTodo,
  getReminders,
} from "@/utils/api/todo";

dayjs.locale("zh-cn");

const weekDays = ["日", "一", "二", "三", "四", "五", "六"];

// 当前年月
const today = dayjs();
const currentYear = ref(today.year());
const currentMonth = ref(today.month());

// 选择
const selectedDate = ref(today.format("YYYY-MM-DD"));
const showMonthPicker = ref(false);
const showAirplane = ref(false); // 默认隐藏小飞机
const pickerSelectedValues = ref([
  `${new Date().getFullYear()}年`,
  `${new Date().getMonth() + 1}月`,
]);
const loading = ref(false);
const refreshing = ref(false);

// 切换小飞机显示
const toggleAirplane = () => {
  showAirplane.value = !showAirplane.value;
};

// 数据
const calendarData = ref({});
const selectedEvents = ref([]);
const loadingEvents = ref(false);

// 提醒数据
const reminders = ref([]);
const reminderLoading = ref(false);

// 计算单个事件的提醒等级
const calcReminderLevel = (item) => {
  // 只处理 need_remind = 1 的事件
  if (!item.need_remind) return null;

  const eventDate = dayjs(item.happen_date).startOf("day");
  const today = dayjs().startOf("day");
  const daysUntilEvent = eventDate.diff(today, "day");

  // 事件已过不闪烁
  if (daysUntilEvent < 0) return null;

  // 计算提醒开始日期
  let reminderStartDate;
  let useCustomRemind = false;

  if (item.remind_time) {
    const customRemindDate = dayjs(item.remind_time).startOf("day");
    // 计算 remind_time 距离事件的天数
    const daysFromRemindToEvent = eventDate.diff(customRemindDate, "day");
    // 如果 remind_time 距离事件 >= 10天，使用自定义提醒时间
    if (daysFromRemindToEvent >= 10) {
      reminderStartDate = customRemindDate;
      useCustomRemind = true;
    }
  }

  // 如果没有自定义 remind_time 或 时间 < 10天，使用系统默认（事件前10天）
  if (!useCustomRemind) {
    reminderStartDate = eventDate.subtract(10, "day");
  }

  // 如果提醒开始时间还没到，不闪烁
  if (today.isBefore(reminderStartDate)) return null;

  // 计算当前时间距离事件的天数，用于决定颜色
  // 使用系统规则：
  // >= 10天前提醒：绿色
  // >= 5天且 < 10天前提醒：黄色
  // < 5天前提醒：红色
  if (daysUntilEvent >= 10) {
    return { level: "green", days: daysUntilEvent };
  }
  if (daysUntilEvent >= 5) {
    return { level: "yellow", days: daysUntilEvent };
  }
  return { level: "red", days: daysUntilEvent };
};

// 获取提醒颜色等级（用于日历格子）
const getReminderLevel = (happenDate) => {
  const item = reminders.value.find((r) => r.happen_date === happenDate);
  if (!item) return null;
  return calcReminderLevel(item);
};

// 横幅用：直接返回颜色等级
const getReminderBannerLevel = (item) => {
  const daysUntil = dayjs(item.happen_date).diff(dayjs().startOf("day"), "day");
  if (daysUntil >= 10) return "green";
  if (daysUntil >= 5) return "yellow";
  return "red";
};

// 加载提醒数据
const loadReminders = async () => {
  reminderLoading.value = true;
  try {
    // 使用 scope=all 获取 30 天周期的提醒，保证显示即时准确
    const res = await getReminders({ scope: "all" });
    reminders.value = res.data || [];
  } catch {
    reminders.value = [];
  } finally {
    reminderLoading.value = false;
  }
};

// 弹窗
const showAddPopup = ref(false);
const showEditPopup = ref(false);
const showActions = ref(false);
const submitting = ref(false);
const newEvent = ref({
  content: "",
  event_type: "schedule",
  priority: 2,
  is_recurring: false,
  need_remind: false,
  remind_days: 0,
  remark: "",
});
const editEvent = ref({});
const currentEvent = ref({});
const actionOptions = ref([]);

// 日历天列表
const calendarDays = computed(() => {
  const days = [];
  const firstDay = dayjs()
    .year(currentYear.value)
    .month(currentMonth.value)
    .date(1);
  const daysInMonth = firstDay.daysInMonth();
  const startWeekday = firstDay.day();
  const monthData = calendarData.value?.days || [];
  const todayStr = dayjs().format("YYYY-MM-DD");

  // 空白填充
  for (let i = 0; i < startWeekday; i++) {
    days.push({ day: null });
  }

  // 日期
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = firstDay.date(d).format("YYYY-MM-DD");
    const dayData = monthData.find((m) => m.date === dateStr);
    const list = dayData?.list || [];
    // 判断是否有小飞机：该天有任意日程且其中包含 content === "1" 的记录
    const hasAirplane = list.some((item) => item?.content === "1");
    // 检查该日期是否有提醒需要闪烁
    const reminderItem = reminders.value.find((r) => r.happen_date === dateStr);
    const reminderFlash = reminderItem ? calcReminderLevel(reminderItem) : null;
    days.push({
      day: d,
      date: dateStr,
      isToday: dateStr === todayStr,
      count: dayData?.count || 0,
      hasOverdue: dayData?.hasOverdue || false,
      hasAirplane,
      reminderFlash,
    });
  }
  return days;
});

// 选中日期显示
const formatSelectedDate = computed(() => {
  return dayjs(selectedDate.value).format("MM月DD日 dddd");
});

// 事件类型名称
const getEventTypeName = (type) => {
  const map = {
    schedule: "日程",
    birthday: "生日",
    anniversary: "纪念日",
    countdown: "倒数日",
  };
  return map[type] || type;
};

// 加载月数据
const loadCalendarMonth = async () => {
  loading.value = true;
  try {
    const res = await getCalendarMonth({
      year: currentYear.value,
      month: currentMonth.value + 1,
    });
    console.log(res.data);
    
    calendarData.value = res.data || res;
  } catch {
    showToast("加载日历失败");
  } finally {
    loading.value = false;
  }
};

// 加载当天事件
const loadSelectedDateEvents = async () => {
  if (!selectedDate.value) return;
  loadingEvents.value = true;
  try {
    // 后端筛选：happen_date 参数
    const res = await getTodoList({
      happen_date: selectedDate.value,
    });
    // 后端返回格式：{ data: [...] } 或 { data: { list: [...] } }
    let list = res.data;
    // 如果是 { list: [...] } 格式
    if (list && Array.isArray(list.list)) {
      list = list.list;
    }
    // 确保是数组，并按 happen_date 过滤（前端二次过滤确保安全）
    selectedEvents.value = (Array.isArray(list) ? list : []).filter(
      (item) => item.happen_date === selectedDate.value
    );
  } catch {
    showToast("加载事件失败");
  } finally {
    loadingEvents.value = false;
    refreshing.value = false;
  }
};

// 上月
const prevMonth = () => {
  currentMonth.value === 0
    ? ((currentMonth.value = 11), currentYear.value--)
    : currentMonth.value--;
};

// 下月
const nextMonth = () => {
  currentMonth.value === 11
    ? ((currentMonth.value = 0), currentYear.value++)
    : currentMonth.value++;
};

// 选择日期
const selectDate = (day) => {
  if (!day?.date) return;
  selectedDate.value = day.date;
  loadSelectedDateEvents();
};

// 选择年月
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText) - 1;
  pickerSelectedValues.value = [yearText, monthText];
  showMonthPicker.value = false;
};

// 月份选择器列
const pickerColumns = computed(() => {
  const years = [];
  const startYear = new Date().getFullYear() - 10;
  const endYear = new Date().getFullYear() + 5;
  for (let i = startYear; i <= endYear; i++) {
    years.push({ text: `${i}年`, value: `${i}年` });
  }
  const months = Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: `${i + 1}月`,
  }));
  return [years, months];
});

// 去添加
const goToAddEvent = () => {
  newEvent.value = {
    content: "",
    event_type: "schedule",
    priority: 2,
    is_recurring: false,
    need_remind: false,
    remind_days: 0,
    remark: "",
  };
  showAddPopup.value = true;
};

// 添加事件
const handleAddEvent = async () => {
  if (!newEvent.value.content?.trim()) return showToast("请输入内容");
  submitting.value = true;
  try {
    await createTodo({
      content: newEvent.value.content.trim(),
      happen_date: selectedDate.value,
      event_type: newEvent.value.event_type,
      priority: newEvent.value.priority,
      is_recurring: newEvent.value.is_recurring,
      need_remind: newEvent.value.need_remind ? 1 : 0,
      remind_days: newEvent.value.remind_days || 0,
      remark: newEvent.value.remark?.trim() || "",
    });
    showSuccessToast("添加成功");
    showAddPopup.value = false;
    loadSelectedDateEvents();
    loadCalendarMonth();
    loadReminders();
  } catch (e) {
    showToast(e.message || "添加失败");
  } finally {
    submitting.value = false;
  }
};

// 显示事件操作菜单
const showEventActions = (event) => {
  currentEvent.value = event;
  const isCompleted = event.status === "已完成";
  actionOptions.value = [
    {
      name: isCompleted ? "标记未完成" : "标记完成",
      callback: () => toggleEventComplete(event),
    },
    {
      name: "编辑",
      callback: () => openEditPopup(event),
    },
    {
      name: "删除",
      color: "#ee0a24",
      callback: () => handleDelete(event),
    },
  ];
  showActions.value = true;
};

// 操作选中
const onActionSelect = (action) => {
  if (action.callback) {
    action.callback();
  }
};

// 标记完成/未完成
const toggleEventComplete = async (event) => {
  try {
    await showConfirmDialog({
      title: "确认操作",
      message: event.status === "已完成" ? "确定标记为未完成？" : "确定标记为已完成？",
    });
    const newStatus = event.status === "已完成" ? "待完成" : "已完成";
    await updateTodo(event.id, { status: newStatus });
    event.status = newStatus;
    showSuccessToast("操作成功");
    loadCalendarMonth();
  } catch (e) {
    if (e !== "cancel") showToast("操作失败");
  }
};

// 打开编辑弹窗
const openEditPopup = (event) => {
  editEvent.value = {
    content: event.content,
    remark: event.remark || "",
    event_type: event.event_type || "schedule",
    priority: event.priority || 2,
    is_recurring: !!event.is_recurring,
    need_remind: !!event.need_remind,
    remind_days: event.remind_days || 0,
  };
  showEditPopup.value = true;
};

// 保存编辑
const handleEditEvent = async () => {
  if (!editEvent.value.content?.trim()) {
    return showToast("请输入事件内容");
  }
  submitting.value = true;
  try {
    await updateTodo(currentEvent.value.id, {
      content: editEvent.value.content.trim(),
      remark: editEvent.value.remark?.trim() || "",
      event_type: editEvent.value.event_type,
      priority: editEvent.value.priority,
      is_recurring: editEvent.value.is_recurring ? 1 : 0,
      need_remind: editEvent.value.need_remind ? 1 : 0,
      remind_days: editEvent.value.remind_days || 0,
    });
    showSuccessToast("保存成功");
    showEditPopup.value = false;
    loadSelectedDateEvents();
    loadCalendarMonth();
    loadReminders();
  } catch (e) {
    showToast(e.message || "保存失败");
  } finally {
    submitting.value = false;
  }
};

// 删除
const handleDelete = async (event) => {
  try {
    await showConfirmDialog({
      title: "确认删除",
      message: "确定要删除这条计划吗？",
      confirmButtonColor: "#ee0a24",
    });
    await deleteTodo(event.id);
    showSuccessToast("删除成功");
    loadSelectedDateEvents();
    loadCalendarMonth();
    loadReminders();
  } catch (e) {
    if (e !== "cancel") showToast("删除失败");
  }
};

// 监听月份
watch([currentYear, currentMonth], loadCalendarMonth);

onMounted(() => {
  loadCalendarMonth();
  loadSelectedDateEvents();
  loadReminders();
});
</script>

<style scoped>
.page-calendar {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 120px;
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

/* 提醒横幅 */
.reminder-banner {
  background: #fff;
  padding: 12px 16px;
  margin: 8px 0;
}
.reminder-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 10px;
}
.reminder-header .van-icon {
  color: #07c160;
}
.reminder-count {
  font-size: 12px;
  font-weight: 400;
  color: #969799;
}
.reminder-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.reminder-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 14px;
  font-size: 13px;
}
.reminder-item.reminder-red {
  background: rgba(238, 10, 36, 0.1);
  color: #ee0a24;
  animation: flash-red 1s infinite;
}
.reminder-item.reminder-yellow {
  background: rgba(255, 151, 106, 0.1);
  color: #ff976a;
  animation: flash-orange 1.5s infinite;
}
.reminder-item.reminder-green {
  background: rgba(7, 193, 96, 0.1);
  color: #07c160;
  animation: flash-green 2s infinite;
}
.reminder-content {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reminder-date {
  font-weight: 600;
}
@keyframes flash-red {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
@keyframes flash-orange {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
@keyframes flash-green {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

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

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: #fff;
  padding: 8px 0 12px;
}
.calendar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: #fff;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}
.day-cell.empty {
  pointer-events: none;
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
.day-cell.selected {
  background: #e6f9ed;
}
.day-cell.selected.today .day-number {
  background: #07c160;
  color: #fff;
}
.day-cell.selected:not(.today) .day-number {
  color: #07c160;
  font-weight: 600;
}
.day-cell.has-reminder-red {
  animation: cell-flash-red 1s infinite;
}
.day-cell.has-reminder-yellow {
  animation: cell-flash-yellow 1.5s infinite;
}
.day-cell.has-reminder-green {
  animation: cell-flash-green 2s infinite;
}
@keyframes cell-flash-red {
  0%, 100% { background: rgba(238, 10, 36, 0.15); }
  50% { background: rgba(238, 10, 36, 0.05); }
}
@keyframes cell-flash-yellow {
  0%, 100% { background: rgba(255, 151, 106, 0.12); }
  50% { background: rgba(255, 151, 106, 0.04); }
}
@keyframes cell-flash-green {
  0%, 100% { background: rgba(7, 193, 96, 0.1); }
  50% { background: rgba(7, 193, 96, 0.03); }
}

.event-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #07c160;
  position: absolute;
  bottom: 0px;
}
.event-dot.overdue {
  background: #ee0a24;
}
.airplane-icon {
  position: absolute;
  bottom: 0px;
  font-size: 12px;
}

.add-btn {
  position: fixed;
  left: 50%;
  bottom: 70px;
  transform: translateX(-50%);
  width: 52px;
  height: 52px;
  background: #07c160;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(7, 193, 96, 0.25);
  z-index: 99;
}

.event-list {
  position: relative;
  margin-top: 8px;
  background: #fff;
  padding: 16px;
  border-radius: 16px 16px 0 0;
  min-height: 200px;
  z-index: 10;
}
.event-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}
.event-count {
  font-size: 13px;
  color: #969799;
  font-weight: 400;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.event-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.event-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 10px;
  cursor: pointer;
}
.event-item.completed {
  opacity: 0.6;
}
.event-item.completed .event-title {
  text-decoration: line-through;
}
.arrow-icon {
  color: #969799;
  font-size: 16px;
  flex-shrink: 0;
}
.event-content {
  flex: 1;
  min-width: 0;
}
.event-title {
  font-size: 14px;
  color: #323233;
  word-break: break-all;
}
.event-meta {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.event-remark {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
}

.add-event-popup {
  padding: 16px;
}
.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.popup-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.popup-date {
  font-size: 14px;
  color: #969799;
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
