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
        <van-icon name="arrow" @click="nextMonth" />
      </div>
    </div>

    <!-- 日历主体（共享 CalendarGrid 组件） -->
    <div class="calendar-grid-wrap">
      <CalendarGrid
        v-if="!loading"
        :year="currentYear"
        :month="currentMonth"
        :selected-date="selectedDate"
        :primary="'var(--theme-primary)'"
        variant="todo"
        :dataset="calendarDataset"
        :reminder-banner="todoReminderBanner"
        :card="false"
        :show-header="false"
        collapsible
        :default-expanded="true"
        @select="onDaySelect"
        @go-today="goToday"
      >
        <template #reminder-action>
          <van-icon
            class="dc-eye-btn"
            :name="showAirplane ? 'eye-o' : 'closed-eye'"
            @click.stop="toggleAirplane"
          />
        </template>
      </CalendarGrid>
      <div v-else class="calendar-loading">
        <van-loading size="32px">加载中...</van-loading>
      </div>
    </div>

    <!-- 选中日期的事件列表 -->
    <div class="event-list">
      <div class="event-list-header">
        <span>{{ formatSelectedDate }}</span>
        <span class="event-count" v-if="selectedEvents.length > 0">
          {{ selectedEvents.length }}个计划
        </span>
        <button class="add-btn" @click="goToAddEvent">
          <van-icon name="plus" />
          <span>添加</span>
        </button>
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

        <!-- 消费分期专区 -->
        <div v-else-if="installmentEvents.length > 0" class="event-section">
          <div class="event-section-header">
            <van-icon name="bill-o" color="var(--van-danger-color, #ee0a24)" />
            <span>消费分期</span>
          </div>
          <div v-for="event in installmentEvents" :key="event.id" class="event-item inst-item" @click="goInstallmentList">
            <div class="event-content">
              <div class="event-title">{{ event.content }}</div>
              <div class="event-meta">
                <app-tag type="danger" size="small">消费分期</app-tag>
                <span
                  class="inst-tag"
                  :class="event.is_void ? 'void' : event.month_overdue ? 'overdue' : event.month_status === 'entered' ? 'entered' : event.month_status === 'entering' ? 'entering' : event.month_status === 'done' ? 'done' : 'pending'"
                >{{ event.is_void ? '超过期限' : event.month_status === 'entered' ? '已入账' : event.month_overdue ? '逾期未还' : event.month_status === 'entering' ? '入账中' : event.month_status === 'done' ? '已还' : '待入账' }}</span>
                <span class="event-amount">￥{{ Number(event.amount || 0).toFixed(2) }}</span>
              </div>
            </div>
            <van-icon name="arrow" class="arrow-icon" />
          </div>
        </div>

        <!-- 其他计划 -->
        <div v-if="otherEvents.length > 0" class="event-section">
          <div class="event-section-header" v-if="installmentEvents.length > 0">
            <van-icon name="todo-list-o" color="var(--van-blue, #1989fa)" />
            <span>计划</span>
          </div>
          <div class="event-items">
            <div
              v-for="event in otherEvents"
              :key="event.id"
              class="event-item"
              :class="{ completed: event.status === '已完成', recurring: event.source === 'recurring' }"
              @click="showEventActions(event)"
            >
              <div class="event-content">
                <div class="event-title">{{ event.content }}</div>
                <div class="event-meta">
                  <app-tag v-if="event.source === 'recurring' && Number(event.amount) > 0" size="small" type="warning">
                    固定支出
                  </app-tag>
                  <app-tag v-else-if="event.source === 'recurring'" size="small" :color="'var(--van-purple, #7232dd)'" text-color="#fff">
                    事件提醒
                  </app-tag>
                  <app-tag v-if="event.event_type && event.event_type !== 'fixed_expense'" size="small" type="primary">
                    {{ getEventTypeName(event.event_type) }}
                  </app-tag>
                  <app-tag v-if="event.priority === 1" size="small" type="danger">高优</app-tag>
                  <app-tag v-if="event.source === 'recurring' && event.cycle === 'year'" size="small" type="success">每年</app-tag>
                  <app-tag v-if="event.source !== 'recurring' && event.is_recurring" size="small" type="success">每年</app-tag>
                  <span v-if="event.source === 'recurring' && Number(event.amount) > 0" class="event-amount">
                    ￥{{ Number(event.amount || 0).toFixed(2) }}
                  </span>
                </div>
              <div class="event-remark" v-if="event.remark">
                {{ event.remark }}
              </div>
            </div>
            <van-icon :name="event.source === 'recurring' ? 'cash-back-record' : 'arrow'" class="arrow-icon" />
          </div>
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
    <app-popup
      v-model:show="showEditPopup"
      position="bottom"
      round
      close-on-click-overlay
    >
      <div class="add-event-popup">
        <div class="popup-header">
          <span class="popup-title">编辑事件</span>
        </div>

        <app-field
          v-model="editEvent.content"
          label="事件内容"
          placeholder="请输入事件内容"
          :maxlength="100"
        />

        <app-field
          v-model="editEvent.remark"
          label="备注"
          placeholder="选填（最多50字）"
          :maxlength="50"
        />

        <app-field name="event_type" label="事件类型">
          <van-radio-group v-model="editEvent.event_type" direction="horizontal">
            <van-radio name="schedule">日程</van-radio>
            <van-radio name="birthday">生日</van-radio>
            <van-radio name="anniversary">纪念日</van-radio>
            <van-radio name="countdown">倒数日</van-radio>
          </van-radio-group>
        </app-field>

        <app-field name="priority" label="优先级">
          <van-radio-group v-model="editEvent.priority" direction="horizontal">
            <van-radio :name="1">高</van-radio>
            <van-radio :name="2">中</van-radio>
            <van-radio :name="3">低</van-radio>
          </van-radio-group>
        </app-field>

        <app-field name="is_recurring" label="每年重复">
          <van-switch v-model="editEvent.is_recurring" />
        </app-field>

        <app-field name="need_remind" label="开启提醒">
          <van-switch v-model="editEvent.need_remind" />
        </app-field>

        <app-field
          v-if="editEvent.need_remind"
          v-model="editEvent.remind_days"
          label="提前天数"
          type="number"
          placeholder="0"
        >
          <van-stepper v-model="editEvent.remind_days" min="0" max="30" />
        </app-field>

        <div class="popup-actions">
          <app-button size="large" round @click="showEditPopup = false"
            >取消</app-button>
          <app-button
            size="large"
            round
            type="primary"
            :loading="submitting"
            @click="handleEditEvent"
          >
            保存修改
          </app-button>
        </div>
      </div>
    </app-popup>

    <!-- 添加事件弹窗 -->
    <app-popup
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

        <app-field
          v-model="newEvent.content"
          label="事件内容"
          placeholder="请输入事件内容"
          :maxlength="100"
          required
        >
          <template #right-icon>
            <span class="field-required-tip">必填</span>
          </template>
        </app-field>
        <app-field
          v-model="newEvent.remark"
          label="备注"
          placeholder="选填"
          :maxlength="50"
        />

        <app-field label="事件类型">
          <van-radio-group
            v-model="newEvent.event_type"
            direction="horizontal"
          >
            <van-radio name="schedule">日程</van-radio>
            <van-radio name="birthday">生日</van-radio>
            <van-radio name="anniversary">纪念</van-radio>
            <van-radio name="countdown">倒数</van-radio>
          </van-radio-group>
        </app-field>

        <app-field label="优先级">
          <van-radio-group v-model="newEvent.priority" direction="horizontal">
            <van-radio :name="1">高</van-radio>
            <van-radio :name="2">中</van-radio>
            <van-radio :name="3">低</van-radio>
          </van-radio-group>
        </app-field>

        <app-field label="每年重复">
          <van-switch v-model="newEvent.is_recurring" />
        </app-field>

        <app-field label="开启提醒">
          <van-switch v-model="newEvent.need_remind" />
        </app-field>

        <app-field
          v-if="newEvent.need_remind"
          v-model="newEvent.remind_days"
          label="提前天数"
          type="number"
        >
          <van-stepper v-model="newEvent.remind_days" min="0" max="30" />
        </app-field>

        <div class="popup-actions">
          <app-button size="large" round @click="showAddPopup = false"
            >取消</app-button>
          <app-button
            size="large"
            round
            type="primary"
            :loading="submitting"
            @click="handleAddEvent"
          >
            确认
          </app-button>
        </div>
      </div>
    </app-popup>

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
import { ref, computed, onMounted, onActivated, watch } from "vue";
import { showToast, showConfirmDialog, showSuccessToast } from "vant";
import { useRouter } from "vue-router";
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
import { updateRecurringMonthStatus } from "@/utils/api/recurring";
import CalendarGrid from "@/components/calendar/CalendarGrid.vue";

dayjs.locale("zh-cn");
const router = useRouter();

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

const installmentEvents = computed(() => selectedEvents.value.filter(e => e.category_id === 'installment'))
const otherEvents = computed(() => selectedEvents.value.filter(e => e.category_id !== 'installment'))

// 分期事件点击：跳转到分期列表（入账由系统按账单周期自动触发）
const goInstallmentList = () => {
  router.push("/credit-center/installment/list")
}

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

  // 后端已按 remind_days 算出 remind_time（还款日/事件日 - remind_days 天）。
  // 只要存在 remind_time 就用它作为提醒开始日（尊重用户设置的提前天数），
  // 避免"提前3天却按默认10天提前闪烁"的问题。
  if (item.remind_time) {
    const customRemindDate = dayjs(Number(item.remind_time)).startOf("day");
    // 防御：remind_time 晚于事件日（异常数据）时不采用
    if (!customRemindDate.isAfter(eventDate)) {
      reminderStartDate = customRemindDate;
    }
  }

  // 没有 remind_time 或异常时，回退到系统默认（事件前10天）
  if (!reminderStartDate) {
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

// 横幅用：全量展示未完成待办（含今天及以前未完成/逾期 + 今天之后）。
// 着色基于 happen_date 与今天的关系：今天及以前未完成→红（紧迫/逾期）；
// 今天之后按距离 daysUntil 分级（>=10绿 / >=5黄 / <5红）。
const getReminderBannerLevel = (item) => {
  const daysUntil = dayjs(item.happen_date).startOf("day").diff(dayjs().startOf("day"), "day");
  if (daysUntil < 0 || daysUntil === 0) return "red"; // 今天及以前未完成
  if (daysUntil >= 10) return "green";
  if (daysUntil >= 5) return "yellow";
  return "red";
};

// 加载提醒数据（按月：只取当前查看月的未完成事项）
const loadReminders = async () => {
  reminderLoading.value = true;
  try {
    const res = await getReminders({
      year: currentYear.value,
      month: currentMonth.value + 1,
    });
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

// 后端数据 → CalendarGrid 的 dataset（按 YYYY-MM-DD 索引）
const calendarDataset = computed(() => {
  const map = {};
  const put = (dateStr, patch) => {
    map[dateStr] = { ...(map[dateStr] || {}), ...patch };
  };
  (calendarData.value?.days || []).forEach((d) => {
    const list = d.list || [];
    const hasAirplane = showAirplane.value && list.some((i) => i?.content === "1");
    const hasRealEvent = list.some((i) => i?.content !== "1");
    put(d.date, {
      eventCount: hasRealEvent ? d.count || list.length || 1 : 0,
      overdue: !!(d.hasOverdue || list.some((i) => i?.is_overdue)),
      airplane: hasAirplane,
    });
  });
  // 临期提醒可能落在无事件的日期，按天取最高级别闪烁（沿用原 need_remind + remind_time 语义）
  const order = { red: 3, yellow: 2, green: 1 };
  (reminders.value || []).forEach((r) => {
    const lv = calcReminderLevel(r)?.level;
    if (!lv) return;
    const prev = map[r.happen_date]?.reminder;
    if (!prev || order[lv] > order[prev]) put(r.happen_date, { reminder: lv });
  });
  return map;
});

// 提醒横幅数据（交给 CalendarGrid 渲染）：全量未完成待办逐条列出
const todoReminderBanner = computed(() =>
  (reminders.value || [])
    .filter((r) => r.content !== "1")
    .map((r) => ({
      date: r.happen_date,
      level: getReminderBannerLevel(r),
      content: r.content,
    }))
);

// 选中日期显示
const formatSelectedDate = computed(() => {
  return dayjs(selectedDate.value).format("YYYY年M月D日 dddd");
});

// 事件类型名称
const getEventTypeName = (type) => {
  const map = {
    schedule: "日程",
    birthday: "生日",
    anniversary: "纪念日",
    countdown: "倒数日",
    fixed_expense: "固定支出",
  };
  return map[type] || type;
};

const getSelectedRecurringEvents = () => {
  const dayData = (calendarData.value?.days || []).find(
    (item) => item.date === selectedDate.value
  );
  return (dayData?.list || []).filter((item) => item.source === "recurring");
};

const syncSelectedRecurringEvents = () => {
  const todos = selectedEvents.value.filter((item) => item.source !== "recurring");
  selectedEvents.value = [...todos, ...getSelectedRecurringEvents()];
};

// 加载月数据
const loadCalendarMonth = async () => {
  loading.value = true;
  try {
    const res = await getCalendarMonth({
      year: currentYear.value,
      month: currentMonth.value + 1,
    });    
    calendarData.value = res.data || res;
    syncSelectedRecurringEvents();
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
    const todoEvents = (Array.isArray(list) ? list : []).filter(
      (item) => item.happen_date === selectedDate.value
    );
    selectedEvents.value = [...todoEvents, ...getSelectedRecurringEvents()];
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
const onDaySelect = (date) => {
  selectedDate.value = date;
  loadSelectedDateEvents();
};

// 回到今天（「今」圆环按钮）
const goToday = () => {
  const now = dayjs();
  currentYear.value = now.year();
  currentMonth.value = now.month();
  selectedDate.value = now.format("YYYY-MM-DD");
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
  if (event.source === "recurring") {
    // 分期期次走三态（未入账/入账待还/已还），不可经此菜单手动标记完成/待处理，
    // 避免覆盖已入账状态、与已建消费流水矛盾。
    if (event.category_id === "installment") {
      actionOptions.value = [
        {
          name: "管理固定支出",
          callback: () => router.push("/finance/recurring"),
        },
      ];
      showActions.value = true;
      return;
    }
    actionOptions.value = [
      {
        name: event.month_status === "done" ? "标记待处理" : "标记已处理",
        callback: () => toggleRecurringComplete(event),
      },
      {
        name: "管理固定支出",
        callback: () => router.push("/finance/recurring"),
      },
    ];
    showActions.value = true;
    return;
  }

  // 信用卡还款提醒由 card_bill 派生，只读：禁止编辑/删除/标记完成（避免用假 id 操作 todo 表）
  if (event.source === "card_bill") {
    actionOptions.value = [
      {
        name: "查看账单",
        callback: () => router.push(`/card/bill/detail?id=${event.bill_id}`),
      },
    ];
    showActions.value = true;
    return;
  }

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
      color: "var(--van-danger-color, #ee0a24)",
      callback: () => handleDelete(event),
    },
  ];
  showActions.value = true;
};

const toggleRecurringComplete = async (event) => {
  try {
    const nextStatus = event.month_status === "done" ? "pending" : "done";
    await updateRecurringMonthStatus(event.recurring_id, {
      month: event.happen_date.substring(0, 7),
      status: nextStatus,
      amount: event.amount,
    });
    showSuccessToast("操作成功");
    await loadCalendarMonth();
    loadSelectedDateEvents();
    loadReminders();
  } catch (e) {
    showToast(e.message || "操作失败");
  }
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
      confirmButtonColor: "var(--van-danger-color, #ee0a24)",
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

// 监听月份：切到哪月，日历网格 + 横幅提醒都按该月重载
watch([currentYear, currentMonth], () => {
  loadCalendarMonth();
  loadReminders();
});

onMounted(() => {
  loadCalendarMonth();
  loadSelectedDateEvents();
  loadReminders();
});

// keep-alive 缓存：从其他页（如创建分期）返回日历时不会触发 onMounted，
// 此处 onActivated 强制重载，确保新建的循环/分期事件能立即出现在日历。
onActivated(() => {
  loadCalendarMonth();
  loadSelectedDateEvents();
  loadReminders();
});
</script>

<style scoped>
.page-calendar {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 120px;
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

/* 提醒横幅 */
.reminder-banner {
  background: var(--theme-bg-secondary);
  padding: 12px 16px;
  margin: 8px 0;
}
.reminder-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 10px;
}
.reminder-header .van-icon {
  color: var(--van-green, #07c160);
}
.reminder-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--theme-text-tertiary);
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
  background: var(--van-danger-bg, rgba(238, 10, 36, 0.1));
  color: var(--van-danger-color, #ee0a24);
  animation: flash-red 1s infinite;
}
.reminder-item.reminder-yellow {
  background: var(--van-orange-bg, rgba(255, 151, 106, 0.1));
  color: var(--van-orange, #ff976a);
  animation: flash-orange 1.5s infinite;
}
.reminder-item.reminder-green {
  background: var(--van-green-bg, rgba(7, 193, 96, 0.1));
  color: var(--van-green, #07c160);
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

.calendar-grid-wrap {
  background: var(--theme-bg-secondary);
  border-top: 1px solid var(--theme-border);
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  background: var(--theme-bg-secondary);
  padding: 8px 0 12px;
}
.calendar-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: var(--theme-bg-secondary);
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
.day-cell.selected {
  background: rgba(7, 193, 96, 0.12);
}
.day-cell.selected.today .day-number {
  background: var(--van-green, #07c160);
  color: #fff;
}
.day-cell.selected:not(.today) .day-number {
  color: var(--van-green, #07c160);
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
  background: var(--van-green, #07c160);
  position: absolute;
  bottom: 0px;
}
.event-dot.overdue {
  background: var(--van-danger-color, #ee0a24);
}
.airplane-icon {
  position: absolute;
  bottom: 0px;
  font-size: 12px;
}

.add-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 1px solid var(--theme-primary);
  background: rgba(var(--theme-primary-rgb, 7, 193, 96), 0.08);
  color: var(--theme-primary);
  border-radius: 16px;
  font-size: 13px;
  font-weight: 600;
  padding: 4px 12px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.add-btn:active { opacity: 0.6; }
.dc-eye-btn {
  font-size: 16px;
  color: var(--theme-primary);
  padding: 2px;
}

.event-list {
  position: relative;
  margin-top: 8px;
  background: var(--theme-bg-secondary);
  padding: 16px;
  border-radius: 16px 16px 0 0;
  min-height: 200px;
  z-index: 10;
}
.event-list-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
}
.event-count {
  margin-left: 8px;
  font-size: 13px;
  color: var(--theme-text-tertiary);
  font-weight: 400;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.event-section { margin-bottom: 14px; }
.event-section-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 14px; font-weight: 600; color: var(--theme-text-primary);
  padding: 4px 0 10px;
}
.inst-item { border-left: 3px solid var(--van-danger-color, #ee0a24); padding-left: 9px; }

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
  background: var(--theme-bg-primary);
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
  color: var(--theme-text-tertiary);
  font-size: 16px;
  flex-shrink: 0;
}
.event-content {
  flex: 1;
  min-width: 0;
}
.event-title {
  font-size: 14px;
  color: var(--theme-text-primary);
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
  color: var(--theme-text-tertiary);
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
/* 添加弹窗内输入框占位小字：深色主题下加深一档，避免太浅 */
.add-event-popup :deep(.app-field__input::placeholder) {
  color: var(--theme-text-secondary);
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
  color: var(--theme-text-primary);
}
.popup-date {
  font-size: 14px;
  color: var(--theme-text-secondary);
}
.popup-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
.popup-actions .app-btn {
  flex: 1;
}
.field-required-tip {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
  padding: 0 8px;
  border-radius: 4px;
  color: var(--theme-danger);
  border: 1px solid currentColor;
  background: rgba(238, 10, 36, 0.08);
}

/* 分期日历条目状态（内联，替代 app-tag） */
.inst-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}
.inst-tag.pending { color: #1989fa; background: rgba(25, 137, 250, 0.1); }
.inst-tag.entering { color: var(--van-warning-color, #ff976a); background: rgba(255, 151, 106, 0.12); }
.inst-tag.entered { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); }
.inst-tag.overdue { color: var(--van-danger-color, #ee0a24); background: rgba(238, 10, 36, 0.1); }
.inst-tag.done { color: var(--van-success-color, #07c160); background: rgba(7, 193, 96, 0.1); }
.inst-tag.void { color: var(--theme-text-tertiary); background: rgba(0, 0, 0, 0.05); }
</style>
