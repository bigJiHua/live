<template>
  <div class="page-cal-demo" :style="themeVars">
    <h2 class="demo-h2">① 默认版本（自研组件）</h2>
    <!-- 顶部说明 -->
    <section class="intro-card">
      <h3 class="sec-title">小米风格日历组件 Demo</h3>
      <p class="sec-desc">
        点击日期选中 · 点击「展开/收起」切换全月/单周视图 · 月份左右切换
      </p>
    </section>

    <!-- 日历卡片 -->
    <section class="cal-card">
      <!-- 月份导航 -->
      <div class="cal-header">
        <button class="nav-btn" @click="prevMonth">
          <van-icon name="arrow-left" />
        </button>
        <div class="cal-title" @click="openPicker('default')">
          <span class="title-text">{{ currentYear }}年{{ currentMonth + 1 }}月</span>
          <van-icon name="arrow-down" class="title-arrow" />
        </div>
        <button class="nav-btn" @click="nextMonth">
          <van-icon name="arrow" />
        </button>
      </div>

      <!-- 星期标题 -->
      <div class="cal-weekdays">
        <div
          v-for="(w, i) in weekDays"
          :key="w"
          class="cal-wd"
          :class="{ weekend: i === 0 || i === 6 }"
        >{{ w }}</div>
      </div>

      <!-- 日历网格（带高度动画） -->
      <div
        class="cal-body"
        :class="{ collapsed: !expanded }"
        :style="{ height: gridHeight + 'px' }"
      >
        <div
          class="cal-grid"
          :style="{ transform: `translateY(${gridOffset}px)`, transition: 'transform 0.36s cubic-bezier(0.4,0,0.2,1)' }"
        >
          <div
            v-for="(cell, i) in calendarCells"
            :key="i"
            class="cal-cell"
            :class="cellClasses(cell)"
            @click="cell.day && selectDate(cell)"
          >
            <span class="cell-num">{{ cell.day || '' }}</span>
            <!-- 今日小圆点（非当日选中时额外指示） -->
            <span v-if="cell.isToday && !cell.isSelected" class="today-pip"></span>
            <!-- 事件指示点 -->
            <span
              v-if="cell.dot"
              class="event-pip"
              :class="cell.dotColor"
            ></span>
          </div>
        </div>
      </div>

      <!-- 展开/收起按钮 -->
      <div class="cal-toggle" @click="expanded = !expanded">
        <van-icon :name="expanded ? 'arrow-up' : 'arrow-down'" />
        <span>{{ expanded ? '收起' : '展开' }}</span>
      </div>
    </section>

    <!-- 选中信息 -->
    <section class="sel-card">
      <div class="sel-date">
        <span class="sel-day">{{ selectedDayNum }}</span>
        <div class="sel-info">
          <span class="sel-full">{{ selectedFull }}</span>
          <span class="sel-weekday">{{ selectedWeekday }}</span>
        </div>
      </div>
      <div class="sel-tag">
        <app-tag v-if="isToday" type="success">今天</app-tag>
        <app-tag v-if="isWeekend" type="warning">周末</app-tag>
      </div>
    </section>

    <!-- 配色方案（复用 UI Showcase 风格） -->
    <section class="intro-card">
      <h3 class="sec-title">实时调色预览</h3>
      <div class="color-row">
        <input type="color" v-model="customPrimary" />
        <span class="hex-label">{{ customPrimary.toUpperCase() }}</span>
        <span class="color-tip">← 拖动改主色，日历实时跟随</span>
      </div>
    </section>
    <!-- ════════════ 适配版区块（本地 mock 数据） ════════════ -->

    <!-- ② 待办 / 日程日历 -->
    <h2 class="demo-h2">② 适配版 · 待办 / 日程日历</h2>
    <section class="demo-block">
      <h3 class="demo-block-title">
        日程与提醒 <span class="demo-tag">Todo Calendar</span>
      </h3>
      <p class="demo-block-desc">
        事件圆点（绿色普通 / 红色逾期）+ 临期提醒闪烁（红/黄/绿）+ 出差小飞机标记；点击日期查看当日事件清单（类型 / 优先级 / 固定支出 / 每年 标签）。
      </p>

      <CalendarGrid
        :year="todoYear"
        :month="todoMonth"
        :selectedDate="todoSelected"
        :primary="customPrimary"
        variant="todo"
        :dataset="todoDataset"
        :reminderBanner="todoReminder"
        collapsible
        :defaultExpanded="true"
        @prev="todoPrev"
        @next="todoNext"
        @select="todoSelect"
        @go-today="demoGoToday('todo')"
        @title="openPicker('todo')"
      />

      <div class="adp-detail">
        <div class="adp-detail-head">
          <span>{{ todoSelectedLabel }}</span>
          <app-tag v-if="todoSelectedEvents.length" type="success">{{ todoSelectedEvents.length }} 项</app-tag>
        </div>
        <div v-if="!todoSelectedEvents.length" class="adp-empty">当天暂无日程</div>
        <div v-for="(ev, i) in todoSelectedEvents" :key="i" class="ev-item">
          <div class="ev-top">
            <span class="ev-title">{{ ev.title }}</span>
          </div>
          <div class="ev-tags">
            <app-tag type="primary">{{ ev.type }}</app-tag>
            <app-tag
              :type="ev.priority === '紧急' ? 'danger' : ev.priority === '高' ? 'warning' : 'default'"
            >优先级·{{ ev.priority }}</app-tag>
            <app-tag v-if="ev.fixed" type="default">固定支出</app-tag>
            <app-tag v-if="ev.yearly" type="default">每年</app-tag>
          </div>
        </div>
      </div>
    </section>

    <!-- ③ 收支流水日历 -->
    <h2 class="demo-h2">③ 适配版 · 收支流水日历</h2>
    <section class="demo-block">
      <h3 class="demo-block-title">
        每日收支 <span class="demo-tag">Finance Flow</span>
      </h3>
      <p class="demo-block-desc">
        顶部月统计（结余 / 收入 / 支出）+ 每日金额（收入红 / 支出绿）；点击日期查看当日流水明细（支出 / 收入 双栏）。
      </p>

      <CalendarGrid
        :year="flowYear"
        :month="flowMonth"
        :selectedDate="flowSelected"
        :primary="customPrimary"
        variant="flow"
        :dataset="flowDataset"
        :stat="flowStat"
        @prev="flowPrev"
        @next="flowNext"
        @select="flowSelect"
        @go-today="demoGoToday('flow')"
        @title="openPicker('flow')"
      />
    </section>

    <!-- ④ 工资日历 -->
    <h2 class="demo-h2">④ 适配版 · 工资日历</h2>
    <section class="demo-block">
      <h3 class="demo-block-title">
        正式 / 兼职薪资 <span class="demo-tag">Work Salary</span>
      </h3>
      <p class="demo-block-desc">
        顶部月统计（月总收入 / 正式 / 兼职）+ 每日金额（正式蓝 / 兼职橙）+ 计薪日标记（有正式工作但无记录）+ 非工作日置灰。
      </p>

      <CalendarGrid
        :year="salYear"
        :month="salMonth"
        :selectedDate="salSelected"
        :primary="customPrimary"
        variant="salary"
        :dataset="salDataset"
        :stat="salStat"
        @prev="salPrev"
        @next="salNext"
        @select="salSelect"
        @title="openPicker('sal')"
      />

      <div class="adp-detail salary">
        <div v-if="salDetail.has" class="sal-head">
          <span>{{ salSelectedLabel }}</span>
          <app-tag type="primary">计薪日</app-tag>
        </div>
        <div v-else class="adp-empty">该日无薪资记录</div>
        <div v-if="salDetail.formal" class="sal-row">
          <span class="sal-cat">正式工资</span>
          <span class="sal-amt blue">¥{{ salDetail.formal }}</span>
        </div>
        <div v-if="salDetail.parttime" class="sal-row">
          <span class="sal-cat">兼职（{{ salDetail.parttimeCount }} 笔）</span>
          <span class="sal-amt orange">¥{{ salDetail.parttime }}</span>
        </div>
        <div v-if="salDetail.isWorkingDay && !salDetail.has" class="sal-working-note">本月计薪日，暂无明细</div>
      </div>
    </section>

    <!-- 月份选择器弹窗 -->
    <AppPopup v-model:show="pickerOpen" position="bottom" round>
      <div class="mp">
        <div class="mp-head">
          <button class="mp-nav" @click="pickerYear--"><van-icon name="arrow-left" /></button>
          <span class="mp-year" @click="pickerGotoToday">选择月份 · {{ pickerYear }}年</span>
          <button class="mp-nav" @click="pickerYear++"><van-icon name="arrow" /></button>
        </div>
        <div class="mp-grid">
          <button
            v-for="m in 12"
            :key="m"
            class="mp-month"
            :class="{ active: (m - 1) === pickerMonth }"
            @click="confirmMonth(m - 1)"
          >{{ m }}月</button>
        </div>
        <div class="mp-tip">点击年份可回到今天 · 点击月份切换</div>
      </div>
    </AppPopup>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import dayjs from 'dayjs'
import { abbrMoney } from '@/utils/abbrMoney'

// ── 基础状态 ──────────────────────────────────────────────────────────────────
const today = dayjs()
const currentYear  = ref(today.year())
const currentMonth = ref(today.month())
const selectedDate = ref(today.format('YYYY-MM-DD'))
const expanded     = ref(true)
const customPrimary = ref('#3a66e0')

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

// ── 动态 CSS 变量（实时主色） ──────────────────────────────────────────────────
// 通过 style 绑定到根节点，让日历颜色跟随调色器
const themeVars = computed(() => ({
  '--cal-primary':   customPrimary.value,
  '--cal-primary-bg': customPrimary.value + '18',
  '--cal-primary-ring': customPrimary.value + '40',
}))

// ── 日历单元格计算 ──────────────────────────────────────────────────────────────
// 生成包含前置空白、日期、后置空白的完整 6 行 × 7 列网格（固定 42 格）
// 保证折叠/展开时高度变化平稳
const calendarCells = computed(() => {
  const first     = dayjs().year(currentYear.value).month(currentMonth.value).date(1)
  const daysTotal = first.daysInMonth()
  const startWd   = first.day() // 0=周日

  const cells = []
  // 上月补空
  for (let i = 0; i < startWd; i++) {
    cells.push({ day: 0, date: '', isToday: false, isSelected: false, row: 0 })
  }
  // 本月日期
  const todayStr = today.format('YYYY-MM-DD')
  for (let d = 1; d <= daysTotal; d++) {
    const dateStr = first.date(d).format('YYYY-MM-DD')
    cells.push({
      day: d,
      date: dateStr,
      isToday:    dateStr === todayStr,
      isSelected: dateStr === selectedDate.value,
      row: Math.floor((startWd + d - 1) / 7),
      // Demo 演示用事件点：每月逢 5 的倍数日期有小绿点
      dot: d % 5 === 0,
      dotColor: d % 10 === 0 ? 'dot-orange' : 'dot-green',
    })
  }
  // 下月补空（补齐 42 格 = 6 行）
  while (cells.length < 42) {
    cells.push({ day: 0, date: '', isToday: false, isSelected: false, row: 0 })
  }
  return cells
})

// ── 折叠/展开：计算网格高度 ───────────────────────────────────────────────────
// 行高固定 52px（含间距），折叠时只显示选中日期所在那一行
const ROW_H = 60
const selectedRow = computed(() => {
  const cell = calendarCells.value.find(c => c.isSelected)
  return cell ? cell.row : 0
})
const totalRows   = computed(() => Math.ceil(calendarCells.value.length / 7))
const gridHeight  = computed(() => (expanded.value ? totalRows.value : 1) * ROW_H)
// 展开时不需要位移，只有折叠时向上推到选中行
const gridOffset  = computed(() => expanded.value ? 0 : -(selectedRow.value * ROW_H))

// ── 选中日期显示信息 ───────────────────────────────────────────────────────────
const selectedDayNum  = computed(() => dayjs(selectedDate.value).date())
const selectedFull    = computed(() => dayjs(selectedDate.value).format('YYYY年MM月DD日'))
const selectedWeekday = computed(() => '星期' + weekDays[dayjs(selectedDate.value).day()])
const isToday         = computed(() => selectedDate.value === today.format('YYYY-MM-DD'))
const isWeekend       = computed(() => [0, 6].includes(dayjs(selectedDate.value).day()))

// ── 单元格 class 计算 ────────────────────────────────────────────────────────
const cellClasses = (cell) => ({
  empty:      !cell.day,
  today:       cell.isToday,
  selected:    cell.isSelected,
  'today-selected': cell.isToday && cell.isSelected,
})

// ── 操作 ─────────────────────────────────────────────────────────────────────
const selectDate = (cell) => {
  if (!cell.day) return
  selectedDate.value = cell.date
}

const prevMonth = () => {
  if (currentMonth.value === 0) {
    currentMonth.value = 11
    currentYear.value--
  } else {
    currentMonth.value--
  }
}

const nextMonth = () => {
  if (currentMonth.value === 11) {
    currentMonth.value = 0
    currentYear.value++
  } else {
    currentMonth.value++
  }
}

const goToday = () => {
  currentYear.value  = today.year()
  currentMonth.value = today.month()
  selectedDate.value = today.format('YYYY-MM-DD')
}

// ════════════════════════════════════════════════════════════════════════
//  适配版区块（本地 mock 数据，不影响 web 其它页面）
// ════════════════════════════════════════════════════════════════════════
import CalendarGrid from '@/components/calendar/CalendarGrid.vue'

const td = dayjs() // demo 基准“今天”

// 通用：月份前进 / 后退
const stepMonth = (yRef, mRef, delta) => {
  const d = td.year(yRef.value).month(mRef.value).date(1).add(delta, 'month')
  yRef.value = d.year()
  mRef.value = d.month()
}
// ── ② 待办 / 日程日历 ─────────────────────────────────────────────────────
const todoYear      = ref(td.year())
const todoMonth     = ref(td.month())
const todoSelected  = ref(td.format('YYYY-MM-DD'))

const todoDataset = computed(() => {
  const map = {}
  const y = todoYear.value
  const m = todoMonth.value
  const dim = td.year(y).month(m).daysInMonth()
  const put = (d, obj) => {
    if (d >= 1 && d <= dim) {
      map[td.year(y).month(m).date(d).format('YYYY-MM-DD')] = obj
    }
  }
  put(td.date() - 2,  { eventCount: 1, overdue: true })        // 逾期
  put(td.date() + 3,  { eventCount: 2, reminder: 'red' })      // 紧急提醒
  put(td.date() + 8,  { eventCount: 1, reminder: 'yellow' })   // 临近提醒
  put(td.date() + 15, { eventCount: 1, reminder: 'green' })    // 充裕提醒
  put(td.date() + 12, { airplane: true })                      // 出差（小飞机）
  put(td.date() + 20, { eventCount: 3 })                       // 多事件
  return map
})

const REMIND_CONTENT = { red: '信用卡还款', yellow: '生日礼物采购', green: '年度体检预约' }
const todoReminder = computed(() =>
  Object.entries(todoDataset.value)
    .filter(([, v]) => v.reminder)
    .map(([date, v]) => ({ date, level: v.reminder, content: REMIND_CONTENT[v.reminder] }))
)

const todoSelectedLabel = computed(() => dayjs(todoSelected.value).format('M月D日'))
const todoSelectedEvents = computed(() => {
  const v = todoDataset.value[todoSelected.value]
  if (!v) return []
  const list = []
  if (v.airplane)   list.push({ title: '出差 · 上海',     type: '日程',     priority: '中', fixed: false, yearly: false })
  if (v.overdue)    list.push({ title: '缴纳物业费',       type: '固定支出', priority: '紧急', fixed: true,  yearly: false })
  if (v.eventCount) {
    list.push({ title: '朋友生日',         type: '生日',     priority: '中', fixed: false, yearly: true })
    if (v.eventCount > 2) list.push({ title: '项目里程碑评审', type: '日程', priority: '高', fixed: false, yearly: false })
  }
  return list
})

const todoPrev    = () => stepMonth(todoYear, todoMonth, -1)
const todoNext    = () => stepMonth(todoYear, todoMonth, 1)
const todoSelect  = (date) => { todoSelected.value = date }

// 「今」圆环按钮：回到今天（年份/月份/选中日复位到今天）
const demoGoToday = (target) => {
  const y = td.year()
  const m = td.month()
  const s = td.format('YYYY-MM-DD')
  if (target === 'todo') { todoYear.value = y; todoMonth.value = m; todoSelected.value = s }
  else if (target === 'flow') { flowYear.value = y; flowMonth.value = m; flowSelected.value = s }
  else if (target === 'sal') { salYear.value = y; salMonth.value = m; salSelected.value = s }
}

// ── ③ 收支流水日历 ────────────────────────────────────────────────────────
const flowYear      = ref(td.year())
const flowMonth     = ref(td.month())
const flowSelected  = ref(td.format('YYYY-MM-DD'))

const flowDataset = computed(() => {
  const map = {}
  const base = td.year(flowYear.value).month(flowMonth.value)
  const dim = base.daysInMonth()
  for (let d = 1; d <= dim; d++) {
    let income = 0, expense = 0
    if (d === td.date()) { income = 8000; expense = 3200 }
    if (d % 5 === 0) expense += 158.5
    if (d % 7 === 0) income += 520
    if (d % 3 === 0) expense += 60
    if (income || expense) map[base.date(d).format('YYYY-MM-DD')] = { income, expense }
  }
  return map
})

const flowStat = computed(() => {
  let inc = 0, exp = 0
  Object.values(flowDataset.value).forEach(v => { inc += v.income; exp += v.expense })
  const balance = inc - exp
  return {
    mainLabel: '月结余', mainValue: (balance >= 0 ? '+' : '-') + abbrMoney(Math.abs(balance)), mainClass: balance >= 0 ? 'income' : 'expense',
    sub1Label: '收入',   sub1Value: '+' + abbrMoney(inc), sub1Class: 'income',
    sub2Label: '支出',   sub2Value: '-' + abbrMoney(exp), sub2Class: 'expense',
  }
})

const flowPrev    = () => stepMonth(flowYear, flowMonth, -1)
const flowNext    = () => stepMonth(flowYear, flowMonth, 1)
const flowSelect  = (date) => { flowSelected.value = date }

// ── ④ 工资日历 ────────────────────────────────────────────────────────────
const salYear      = ref(td.year())
const salMonth     = ref(td.month())
const salSelected  = ref(td.format('YYYY-MM-DD'))

const salDataset = computed(() => {
  const map = {}
  const base = td.year(salYear.value).month(salMonth.value)
  const dim = base.daysInMonth()
  for (let d = 1; d <= dim; d++) {
    let formal = 0, parttime = 0, parttimeCount = 0
    if ([5, 10, 15, 20, 25].includes(d)) formal = 12000
    if (d % 4 === 0) { parttime = 300; parttimeCount = 1 }
    if (d % 6 === 0) { parttime = 500; parttimeCount = 2 }
    map[base.date(d).format('YYYY-MM-DD')] = {
      isWorkingDay: true,
      formalIncome: formal,
      parttimeTotal: parttime,
      parttimeCount,
    }
  }
  return map
})

const salStat = computed(() => {
  let formal = 0, part = 0
  Object.values(salDataset.value).forEach(v => { formal += v.formalIncome; part += v.parttimeTotal })
  const total = formal + part
  return {
    mainLabel: '月总收入', mainValue: '¥' + abbrMoney(total), mainClass: 'blue',
    sub1Label: '正式',     sub1Value: '¥' + abbrMoney(formal), sub1Class: 'blue',
    sub2Label: '兼职',     sub2Value: '¥' + abbrMoney(part),   sub2Class: 'orange',
  }
})

const salSelectedLabel = computed(() => dayjs(salSelected.value).format('M月D日'))
const salDetail = computed(() => {
  const v = salDataset.value[salSelected.value] || { formalIncome: 0, parttimeTotal: 0, parttimeCount: 0, isWorkingDay: false }
  return {
    has: v.formalIncome > 0 || v.parttimeTotal > 0,
    formal: abbrMoney(v.formalIncome),
    parttime: abbrMoney(v.parttimeTotal),
    parttimeCount: v.parttimeCount,
    isWorkingDay: v.isWorkingDay,
  }
})

const salPrev    = () => stepMonth(salYear, salMonth, -1)
const salNext    = () => stepMonth(salYear, salMonth, 1)
const salSelect  = (date) => { salSelected.value = date }

// ── 月份选择器弹窗 ─────────────────────────────────────────────────────────────
import AppPopup from '@/components/base/AppPopup.vue'

const pickerOpen   = ref(false)
const pickerTarget = ref('default') // default | todo | flow | sal
const pickerYear   = ref(td.year())
const pickerMonth  = ref(td.month())

// 各日历的 [年, 月, 选中日期] 引用表（此时所有 ref 均已声明，避免 TDZ）
const calRefs = {
  default: { y: currentYear,  m: currentMonth,  s: selectedDate },
  todo:    { y: todoYear,     m: todoMonth,     s: todoSelected },
  flow:    { y: flowYear,     m: flowMonth,     s: flowSelected },
  sal:     { y: salYear,      m: salMonth,      s: salSelected },
}

const openPicker = (target) => {
  pickerTarget.value = target
  const r = calRefs[target]
  pickerYear.value  = r.y.value
  pickerMonth.value = r.m.value
  pickerOpen.value  = true
}

const confirmMonth = (m) => {
  const r = calRefs[pickerTarget.value]
  r.y.value = pickerYear.value
  r.m.value = m
  // 若当前选中日期不在新月份，重置到该月 1 号，避免选中态丢失
  const sd = dayjs(r.s.value)
  if (sd.year() !== pickerYear.value || sd.month() !== m) {
    r.s.value = td.year(pickerYear.value).month(m).date(1).format('YYYY-MM-DD')
  }
  pickerOpen.value = false
}

const pickerGotoToday = () => {
  const r = calRefs[pickerTarget.value]
  r.y.value = td.year()
  r.m.value = td.month()
  r.s.value = td.format('YYYY-MM-DD')
  pickerOpen.value = false
}
</script>

<style scoped>
/* ── 页面基础 ─────────────────────────────────────────────────────────────────── */
.page-cal-demo {
  min-height: 100vh;
  background: var(--theme-bg-primary, #f7f8fa);
  padding: 12px;
  padding-bottom: 80px;
}

/* ── 通用卡片 ─────────────────────────────────────────────────────────────────── */
section {
  background: var(--theme-bg-secondary, #fff);
  border-radius: 18px;
  padding: 16px;
  margin-bottom: 14px;
}
.sec-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--theme-text-primary, #323233);
  margin: 0 0 4px;
}
.sec-desc {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
  margin: 0;
  line-height: 1.6;
}

/* ── 日历卡片 ─────────────────────────────────────────────────────────────────── */
.cal-card {
  padding: 0 0 6px;
  overflow: hidden;
}

/* 月份导航 */
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 8px;
}
.cal-title {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}
.title-text {
  font-size: 22px;
  font-weight: 800;
  color: var(--theme-text-primary, #323233);
  letter-spacing: 0.02em;
}
.title-arrow {
  font-size: 14px;
  color: var(--theme-text-tertiary, #969799);
  margin-top: 2px;
}
.nav-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: var(--theme-bg-tertiary, #f2f3f5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  color: var(--theme-text-secondary, #646566);
  transition: background 0.15s ease, transform 0.1s ease;
}
.nav-btn:active {
  transform: scale(0.88);
  background: var(--theme-border, #ebedf0);
}

/* 星期行 */
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding: 4px 8px 8px;
}
.cal-wd {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-tertiary, #969799);
}
.cal-wd.weekend {
  color: var(--cal-primary, #3a66e0);
  opacity: 0.7;
}

/* 日历主体（高度动画） */
.cal-body {
  overflow: hidden;
  transition: height 0.36s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 0 8px;
}

/* 网格：固定 6 行，每行高度 52px */
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 60px);
  gap: 0;
}

/* ── 单元格核心样式 ───────────────────────────────────────────────────────────── */
.cal-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  transition: transform 0.12s ease;
}
.cal-cell:active:not(.empty) {
  transform: scale(0.85);
}

/* 日期数字 */
.cell-num {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  font-weight: 500;
  color: var(--theme-text-primary, #323233);
  border-radius: 50%;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

/* 空白格 */
.cal-cell.empty {
  pointer-events: none;
  cursor: default;
}
.cal-cell.empty .cell-num {
  color: transparent;
}

/* ── 今日样式 ─────────────────────────────────────────────────────────────────── */
/* 今日：实心圆 + 主题色背景 + 白色文字 */
.cal-cell.today .cell-num {
  background: var(--cal-primary, #3a66e0);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 3px 12px var(--cal-primary-ring, rgba(58,102,224,0.25));
}
/* 今日底部小三角指示（当未选中时） */
.today-pip {
  position: absolute;
  bottom: 2px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--cal-primary, #3a66e0);
}

/* ── 选中样式 ─────────────────────────────────────────────────────────────────── */
/* 选中：透明主题色背景圆 + 主题色边框环 */
.cal-cell.selected .cell-num {
  background: var(--cal-primary-bg, rgba(58,102,224,0.09));
  color: var(--cal-primary, #3a66e0);
  font-weight: 700;
  box-shadow:
    inset 0 0 0 2.5px var(--cal-primary, #3a66e0),
    0 2px 8px var(--cal-primary-ring, rgba(58,102,224,0.2));
}

/* 今日 + 选中：实心 + 外环光晕 */
.cal-cell.today-selected .cell-num {
  background: var(--cal-primary, #3a66e0);
  color: #fff;
  font-weight: 700;
  box-shadow:
    inset 0 0 0 2.5px #fff,
    0 0 0 3px var(--cal-primary, #3a66e0),
    0 4px 16px var(--cal-primary-ring, rgba(58,102,224,0.35));
}

/* ── 事件指示点 ─────────────────────────────────────────────────────────────────── */
.event-pip {
  position: absolute;
  bottom: 3px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
}
.event-pip.dot-green  { background: var(--van-green, #07c160); }
.event-pip.dot-orange { background: var(--van-orange, #ff976a); }
.event-pip.dot-red    { background: var(--van-danger-color, #ee0a24); }

/* 今日选中时隐藏底部 pip（已有外环光晕） */
.cal-cell.today-selected .today-pip { display: none; }
.cal-cell.today-selected .event-pip { bottom: 1px; }

/* ── 展开/收起 ─────────────────────────────────────────────────────────────────── */
.cal-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  cursor: pointer;
  user-select: none;
  color: var(--cal-primary, #3a66e0);
  font-size: 13px;
  font-weight: 500;
  transition: opacity 0.15s;
}
.cal-toggle:active { opacity: 0.6; }
.cal-toggle .van-icon {
  font-size: 16px;
  transition: transform 0.3s ease;
}

/* ── 选中信息卡 ──────────────────────────────────────────────────────────────── */
.sel-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sel-date {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
.sel-day {
  font-size: 44px;
  font-weight: 800;
  color: var(--cal-primary, #3a66e0);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.sel-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 4px;
}
.sel-full {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
}
.sel-weekday {
  font-size: 13px;
  color: var(--theme-text-tertiary, #969799);
}
.sel-tag {
  display: flex;
  gap: 6px;
}

/* ── 调色器 ───────────────────────────────────────────────────────────────────── */
.color-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.color-row input[type="color"] {
  width: 44px;
  height: 32px;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
}
.hex-label {
  font-family: 'SF Mono', 'Menlo', monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
}
.color-tip {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
}

/* ── 适配版区块 ───────────────────────────────────────────────────────────── */
.demo-h2 {
  font-size: 14px;
  font-weight: 700;
  color: var(--theme-text-secondary, #646566);
  margin: 22px 4px 10px;
  padding-left: 10px;
  border-left: 3px solid var(--theme-text-tertiary, #c8c9cc);
}

.demo-block-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--theme-text-primary, #323233);
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.demo-tag {
  font-size: 11px;
  font-weight: 500;
  color: var(--theme-text-tertiary, #969799);
  background: var(--theme-bg-tertiary, #f2f3f5);
  padding: 2px 8px;
  border-radius: 10px;
}
.demo-block-desc {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
  margin: 0 0 12px;
  line-height: 1.6;
}

/* 详情面板 */
.adp-detail {
  margin-top: 12px;
  background: var(--theme-bg-tertiary, #f7f8fa);
  border-radius: 14px;
  padding: 12px;
}
.adp-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  margin-bottom: 8px;
}
.adp-empty {
  font-size: 13px;
  color: var(--theme-text-tertiary, #969799);
  text-align: center;
  padding: 8px 0;
}
.adp-empty.sm {
  padding: 4px 0;
  font-size: 12px;
}

/* 待办事件清单 */
.ev-item {
  background: var(--theme-bg-secondary, #fff);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
}
.ev-item:last-child { margin-bottom: 0; }
.ev-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.ev-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
}
.ev-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 流水明细 */
.adp-detail.flow .flow-summary {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.fs-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}
.fs-col span { font-size: 12px; color: var(--theme-text-tertiary, #969799); }
.fs-col b { font-size: 16px; font-weight: 700; }
.fs-col.income b { color: var(--money-income); }
.fs-col.expense b { color: var(--money-expense); }
.fs-divider { width: 1px; height: 28px; background: var(--theme-border, #ebedf0); margin: 0 16px; }
.flow-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.flow-col-h {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-secondary, #646566);
  margin-bottom: 6px;
}
.flow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--theme-text-primary, #323233);
  padding: 4px 0;
}
.flow-row .ex { color: var(--money-expense); font-weight: 600; }
.flow-row .in { color: var(--money-income); font-weight: 600; }

/* 工资明细 */
.sal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  margin-bottom: 8px;
}
.sal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
  color: var(--theme-text-primary, #323233);
}
.sal-amt { font-weight: 700; font-variant-numeric: tabular-nums; }
.sal-amt.blue { color: var(--van-blue, #1989fa); }
.sal-amt.orange { color: var(--van-orange, #ff976a); }
.sal-working-note {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
  text-align: center;
  padding-top: 4px;
}

/* ── 月份选择器弹窗 ─────────────────────────────────────────────────────────── */
.mp {
  padding: 16px 16px 20px;
}
.mp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.mp-year {
  font-size: 17px;
  font-weight: 700;
  color: var(--theme-text-primary, #323233);
  cursor: pointer;
  user-select: none;
}
.mp-nav {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--theme-bg-tertiary, #f2f3f5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 16px;
  color: var(--theme-text-secondary, #646566);
  transition: transform 0.1s ease, background 0.15s ease;
}
.mp-nav:active {
  transform: scale(0.88);
  background: var(--theme-border, #ebedf0);
}
.mp-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.mp-month {
  height: 44px;
  border: none;
  border-radius: 12px;
  background: var(--theme-bg-tertiary, #f2f3f5);
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, transform 0.1s ease;
}
.mp-month:active { transform: scale(0.94); }
.mp-month.active {
  background: var(--cal-primary, #3a66e0);
  color: #fff;
}
.mp-tip {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
}
</style>
