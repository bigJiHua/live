<template>
  <div class="dc" :class="{ card }" :style="themeVars">
    <!-- 月份导航（页面自带导航时可隐藏） -->
    <div v-if="showHeader" class="dc-header">
      <button class="dc-nav" @click="$emit('prev')"><van-icon name="arrow-left" /></button>
      <div class="dc-title" @click="$emit('title')">
        <span class="dc-title-text">{{ year }}年{{ month + 1 }}月</span>
        <van-icon name="arrow-down" class="dc-title-arrow" />
      </div>
      <button class="dc-nav" @click="$emit('next')"><van-icon name="arrow" /></button>
    </div>

    <!-- 待办：提醒横幅（可折叠，默认展开） -->
    <div v-if="variant === 'todo' && reminderBanner.length" class="dc-reminder" :class="{ collapsed: !reminderExpanded }">
      <div class="dc-reminder-head" @click="reminderExpanded = !reminderExpanded">
        <template v-if="reminderExpanded">
          <van-icon name="bell" />
          <span class="dc-reminder-title">{{ year }}年{{ month + 1 }}月提醒</span>
          <span class="dc-reminder-count">{{ reminderBanner.length }}个</span>
        </template>
        <template v-else>
          <van-icon name="bell" />
          <span class="dc-reminder-title">本月提醒：</span>
          <van-notice-bar class="dc-reminder-scroll" left-icon="" :scrollable="false" @click.stop>
            <van-swipe vertical class="dc-reminder-swipe" :autoplay="3500" :touchable="false" :show-indicators="false">
              <van-swipe-item v-for="r in reminderBanner" :key="r.date + r.content">
                <span class="dc-reminder-chip" :class="'lv-' + r.level">{{ r.content }} <b>{{ r.date.slice(5) }}</b></span>
              </van-swipe-item>
            </van-swipe>
          </van-notice-bar>
        </template>
        <div class="dc-reminder-actions">
          <slot name="reminder-action" />
          <van-icon :name="reminderExpanded ? 'arrow-up' : 'arrow-down'" class="dc-reminder-toggle" />
        </div>
      </div>
      <div v-if="reminderExpanded" class="dc-reminder-list">
        <span
          v-for="r in reminderBanner"
          :key="r.date + r.content"
          class="dc-reminder-chip"
          :class="'lv-' + r.level"
        >{{ r.content }} <b>{{ r.date.slice(5) }}</b></span>
      </div>
    </div>

    <!-- 流水 / 工资：顶部统计栏 -->
    <div v-if="(variant === 'flow' || variant === 'salary') && showStat" class="dc-stat">
      <div class="dc-stat-item main">
        <span class="dc-stat-label">{{ stat.mainLabel }}</span>
        <span class="dc-stat-value" :class="stat.mainClass">{{ stat.mainValue }}</span>
      </div>
      <div class="dc-stat-divider"></div>
      <div class="dc-stat-item">
        <span class="dc-stat-label">{{ stat.sub1Label }}</span>
        <span class="dc-stat-value" :class="stat.sub1Class">{{ stat.sub1Value }}</span>
      </div>
      <div class="dc-stat-item">
        <span class="dc-stat-label">{{ stat.sub2Label }}</span>
        <span class="dc-stat-value" :class="stat.sub2Class">{{ stat.sub2Value }}</span>
      </div>
    </div>

    <!-- 星期标题 -->
    <div class="dc-weekdays">
      <div
        v-for="(w, i) in weekDays"
        :key="w"
        class="dc-wd"
        :class="{ weekend: i === 0 || i === 6 }"
      >{{ w }}</div>
    </div>

    <!-- 日历网格 -->
    <div
      class="dc-body"
      :class="{ collapsed: collapsible && !expanded }"
      :style="{ height: bodyHeight + 'px' }"
    >
      <div
        class="dc-grid"
        :style="{ transform: `translateY(${gridOffset}px)`, transition: 'transform 0.36s cubic-bezier(0.4,0,0.2,1)' }"
      >
        <div
          v-for="(cell, i) in cells"
          :key="i"
          class="dc-cell"
          :class="cellClass(cell)"
          @click="cell.day && $emit('select', cell.date)"
        >
          <span class="dc-num">{{ cell.day || '' }}</span>

          <!-- TODO 变体：事件点 / 小飞机 -->
          <template v-if="variant === 'todo'">
            <span v-if="cell.airplane" class="dc-air">✈️</span>
            <span v-else-if="cell.eventCount > 0" class="dc-dot" :class="{ overdue: cell.overdue }"></span>
          </template>

          <!-- FLOW 变体：收(+红)/支(-绿) 加减色 -->
          <template v-else-if="variant === 'flow'">
            <span class="dc-amts">
              <span v-if="cell.income > 0" class="dc-amt flow-income">+{{ fmt(cell.income) }}</span>
              <span v-if="cell.expense > 0" class="dc-amt flow-expense">-{{ fmt(cell.expense) }}</span>
            </span>
          </template>

          <!-- SALARY 变体：正式/兼职金额 或 计薪标记（统一放入固定高度区，避免溢出/位移） -->
          <template v-else-if="variant === 'salary'">
            <span class="dc-amts">
              <span v-if="cell.formalIncome > 0" class="dc-amt formal">¥{{ fmt(cell.formalIncome) }}</span>
              <span v-if="cell.parttimeTotal > 0" class="dc-amt parttime">¥{{ fmt(cell.parttimeTotal) }}</span>
              <span v-if="cell.isWorkingDay && !cell.notWorking && !cell.formalIncome && !cell.parttimeTotal" class="dc-working">计薪</span>
            </span>
          </template>

          <!-- 信用卡账单日/还款日标记 -->
          <span v-if="cell.credit && cell.credit.length" class="dc-credit">
            <span
              v-for="(m, idx) in cell.credit.slice(0, 3)"
              :key="idx"
              class="dc-credit-tag"
              :class="'c-' + m.type"
            >{{ m.type === 'bill' ? '账' : '还' }}</span>
            <span v-if="cell.credit.length > 3" class="dc-credit-more">+{{ cell.credit.length - 3 }}</span>
          </span>
        </div>
      </div>
    </div>

    <!-- 折叠/收起 -->
    <div v-if="collapsible" class="dc-toggle" @click="expanded = !expanded">
      <van-icon :name="expanded ? 'arrow-up' : 'arrow-down'" />
      <span>{{ expanded ? '收起' : '展开' }}</span>
    </div>

    <!-- 选中他日时：右下角「今」字圆环，点击回到今天 -->
    <button v-if="showTodayJump" class="dc-today-btn" @click="$emit('go-today')">今</button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import dayjs from 'dayjs'
import { abbrMoney } from '@/utils/abbrMoney'

const props = defineProps({
  year: { type: Number, required: true },
  month: { type: Number, required: true }, // 0-based
  selectedDate: { type: String, default: '' },
  // 跟随主题：传 var(--theme-primary) 即可，黑金主题选中即金色
  primary: { type: String, default: 'var(--theme-primary)' },
  variant: { type: String, default: 'default' }, // todo | flow | salary
  dataset: { type: Object, default: () => ({}) }, // { 'YYYY-MM-DD': {...} }
  reminderBanner: { type: Array, default: () => [] },
  stat: {
    type: Object,
    default: () => ({
      mainLabel: '', mainValue: '', mainClass: '',
      sub1Label: '', sub1Value: '', sub1Class: '',
      sub2Label: '', sub2Value: '', sub2Class: '',
    }),
  },
  collapsible: { type: Boolean, default: false },
  defaultExpanded: { type: Boolean, default: true },
  showHeader: { type: Boolean, default: true },
  card: { type: Boolean, default: true },
  showStat: { type: Boolean, default: true },
})

const weekDays = ['日', '一', '二', '三', '四', '五', '六']

const expanded = ref(props.defaultExpanded)
// 待办提醒横幅折叠状态（默认展开）
const reminderExpanded = ref(true)

// 选中了非今天日期 → 右下角显示「今」，点击由父组件回到今天
const todayStr = dayjs().format('YYYY-MM-DD')
const showTodayJump = computed(() => !!props.selectedDate && props.selectedDate !== todayStr)

// 解析 #rrggbb → "r, g, b"，供 rgba(var(--dc-primary-rgb), a) 半透明辉光使用
function hexToRgb(hex) {
  const h = (hex || '#000000').replace('#', '')
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
  const r = parseInt(full.substring(0, 2), 16) || 0
  const g = parseInt(full.substring(2, 4), 16) || 0
  const b = parseInt(full.substring(4, 6), 16) || 0
  return `${r}, ${g}, ${b}`
}

const themeVars = computed(() => {
  // 传 var(--theme-primary) 时，辉光复用全局 --theme-primary-rgb（与全站半透明约定一致）
  const isVar = /^var\(/.test(props.primary)
  return {
    '--dc-primary': props.primary,
    '--dc-primary-rgb': isVar ? 'var(--theme-primary-rgb)' : hexToRgb(props.primary),
  }
})

// ── 生成 42 格网格，合并 dataset ──────────────────────────────────────────────
const cells = computed(() => {
  const first = dayjs().year(props.year).month(props.month).date(1)
  const daysTotal = first.daysInMonth()
  const startWd = first.day()
  const todayStr = dayjs().format('YYYY-MM-DD')
  const out = []
  for (let i = 0; i < startWd; i++) {
    out.push({ day: 0, date: '', isToday: false, isSelected: false, row: 0 })
  }
  for (let d = 1; d <= daysTotal; d++) {
    const dateStr = first.date(d).format('YYYY-MM-DD')
    const extra = props.dataset[dateStr] || {}
    out.push({
      day: d,
      date: dateStr,
      isToday: dateStr === todayStr,
      isSelected: dateStr === props.selectedDate,
      row: Math.floor((startWd + d - 1) / 7),
      // 默认字段
      eventCount: 0,
      overdue: false,
      reminder: null,
      airplane: false,
      credit: [],
      income: 0,
      expense: 0,
      formalIncome: 0,
      parttimeTotal: 0,
      parttimeCount: 0,
      isWorkingDay: false,
      notWorking: false,
      ...extra,
    })
  }
  while (out.length < 42) {
    out.push({ day: 0, date: '', isToday: false, isSelected: false, row: 0 })
  }
  return out
})

// ── 折叠/展开高度 ───────────────────────────────────────────────────────────
const ROW_H = 80
const selectedRow = computed(() => {
  const c = cells.value.find((x) => x.isSelected && x.day)
  return c ? c.row : 0
})
const totalRows = computed(() => Math.ceil(cells.value.length / 7))
const bodyHeight = computed(() =>
  props.collapsible && !expanded.value ? ROW_H : totalRows.value * ROW_H
)
const gridOffset = computed(() =>
  props.collapsible && !expanded.value ? -(selectedRow.value * ROW_H) : 0
)

// ── 单元格 class ───────────────────────────────────────────────────────────
const cellClass = (cell) => {
  if (!cell.day) return 'dc-cell empty'
  const c = ['dc-cell']
  if (cell.isToday) c.push('today')
  if (cell.isSelected) c.push('selected')
  if (props.variant === 'todo') {
    if (cell.airplane) c.push('has-airplane')
    if (cell.reminder) c.push('reminder-' + cell.reminder)
    else if (cell.eventCount > 0) c.push('has-event')
  } else if (props.variant === 'flow') {
    if (cell.income > 0 && cell.expense > 0) c.push('has-both')
    else if (cell.income > 0) c.push('has-income')
    else if (cell.expense > 0) c.push('has-expense')
  } else if (props.variant === 'salary') {
    if (cell.notWorking) c.push('not-working')
    else if (cell.formalIncome > 0 && cell.parttimeTotal > 0) c.push('has-both')
    else if (cell.formalIncome > 0) c.push('has-fulltime')
    else if (cell.parttimeTotal > 0) c.push('has-parttime')
    else if (cell.isWorkingDay) c.push('working-day')
  }
  return c.join(' ')
}

// ── 金额格式化（含万单位缩写） ───────────────────────────────────────────
const fmt = abbrMoney
</script>

<style scoped>
.dc {
  /* 基础包裹：默认不携带视觉，由 .card 决定外观 */
  position: relative;
}
.dc.card {
  background: var(--theme-bg-secondary, #fff);
  border-radius: 18px;
  overflow: hidden;
}

/* 右下角「今」圆环按钮 */
.dc-today-btn {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid var(--dc-primary);
  background: var(--theme-bg-secondary, #fff);
  color: var(--dc-primary);
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 8;
  box-shadow: 0 4px 12px rgba(var(--dc-primary-rgb), 0.35);
  transition: transform 0.12s ease;
}
.dc-today-btn:active { transform: scale(0.88); }

/* 月份导航 */
.dc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 8px;
}
.dc-title {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}
.dc-title-text {
  font-size: 22px;
  font-weight: 800;
  color: var(--theme-text-primary, #323233);
  letter-spacing: 0.02em;
}
.dc-title-arrow {
  font-size: 14px;
  color: var(--theme-text-tertiary, #969799);
  margin-top: 2px;
}
.dc-nav {
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
.dc-nav:active {
  transform: scale(0.88);
  background: var(--theme-border, #ebedf0);
}

/* 提醒横幅 */
.dc-reminder {
  padding: 10px 16px;
  border-bottom: 1px solid var(--theme-border, #ebedf0);
}
.dc-reminder-head {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  font-size: 14px;
  line-height: 28px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  margin-bottom: 8px;
  cursor: pointer;
  user-select: none;
}
.dc-reminder-head .van-icon { color: var(--dc-primary); font-size: 18px; }
.dc-reminder-title { white-space: nowrap; line-height: 28px; }
.dc-reminder-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--theme-text-tertiary, #969799);
  line-height: 28px;
}
.dc-reminder-scroll { flex: 1; min-width: 0; padding: 0; background: transparent; --van-notice-bar-background: transparent; --van-notice-bar-padding: 0; height: 28px; }
.dc-reminder-swipe { height: 28px; line-height: 28px; }
.dc-reminder-toggle {
  color: var(--theme-text-tertiary, #969799);
  margin-left: 4px;
  font-size: 20px;
  padding: 0 4px;
  line-height: 28px;
  border-radius: 6px;
  box-sizing: content-box;
}
.dc-reminder-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  height: 28px;
}
.dc-reminder-actions :deep(.van-icon) {
  font-size: 20px;
  padding: 0 4px;
  line-height: 28px;
  border-radius: 6px;
  box-sizing: content-box;
  color: var(--theme-text-tertiary, #969799);
}
.dc-reminder.collapsed .dc-reminder-actions { margin-left: 0; }
.dc-reminder-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.dc-reminder-chip {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 14px;
  font-weight: 500;
  flex: 0 0 auto;
}
.dc-reminder-chip b { font-weight: 700; }
.dc-reminder-chip.lv-red { background: var(--van-danger-bg, rgba(238,10,36,0.1)); color: var(--van-danger-color, #ee0a24); }
.dc-reminder-chip.lv-yellow { background: var(--van-orange-bg, rgba(255,151,106,0.12)); color: var(--van-orange, #ff976a); }
.dc-reminder-chip.lv-green { background: var(--van-green-bg, rgba(7,193,96,0.1)); color: var(--van-green, #07c160); }

/* 统计栏 */
.dc-stat {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  gap: 16px;
}
.dc-stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dc-stat-item.main { flex: 1; }
.dc-stat-label {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
}
.dc-stat-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
}
.dc-stat-value.income { color: var(--money-income); }
.dc-stat-value.expense { color: var(--money-expense); }
.dc-stat-value.blue { color: var(--dc-primary); }
.dc-stat-value.orange { color: var(--van-orange, #ff976a); }
.dc-stat-divider {
  width: 1px;
  height: 30px;
  background: var(--theme-border, #ebedf0);
}

/* 星期 */
.dc-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding: 4px 8px 8px;
}
.dc-wd {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-tertiary, #969799);
}
.dc-wd.weekend { color: var(--dc-primary); opacity: 0.7; }

/* 网格 */
.dc-body {
  overflow: hidden;
  transition: height 0.36s cubic-bezier(0.4,0,0.2,1);
  padding: 5px 8px;
}
.dc-body.collapsed {
  padding-top: 0;
  padding-bottom: 0;
}
.dc-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 80px);
  gap: 0;
}
.dc-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  min-width: 0;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
  transition: transform 0.12s ease;
}
.dc-cell:active:not(.empty) { transform: scale(0.85); }
.dc-cell.empty { pointer-events: none; cursor: default; }

.dc-num {
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  font-weight: 500;
  color: var(--theme-text-primary, #323233);
  border-radius: 50%;
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}
.dc-cell.empty .dc-num { color: transparent; }

/* 今日：实心主色圆（跟随主题，黑金即金色） */
.dc-cell.today .dc-num {
  background: var(--dc-primary);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 3px 12px rgba(var(--dc-primary-rgb), 0.25);
}
/* 选中（非今日）：圆环 + 主题色字（参照第一个默认日历，不填充背景） */
.dc-cell.selected:not(.today) .dc-num {
  background: transparent;
  color: var(--dc-primary);
  font-weight: 700;
  box-shadow: inset 0 0 0 2.5px var(--dc-primary), 0 2px 8px rgba(var(--dc-primary-rgb), 0.25);
}
.dc-cell.today.selected .dc-num {
  box-shadow: inset 0 0 0 2.5px #fff, 0 0 0 3px var(--dc-primary), 0 4px 16px rgba(var(--dc-primary-rgb), 0.25);
}

/* ── TODO 标记 ── */
.dc-dot {
  position: absolute;
  bottom: 3px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dc-primary);
}
.dc-dot.overdue { background: var(--van-danger-color, #ee0a24); }
.dc-air { position: absolute; bottom: 2px; font-size: 12px; }

/* 提醒闪烁（todo）：日期数字直接变红/黄/绿 + 背景脉动 + 内描边，保证任意主题（深色/黑金等）下醒目 */
.dc-cell.reminder-red .dc-num { color: #ee0a24; font-weight: 700; animation: dc-ring-red 1s infinite; }
.dc-cell.reminder-yellow .dc-num { color: #ff976a; font-weight: 700; animation: dc-ring-yellow 1.5s infinite; }
.dc-cell.reminder-green .dc-num { color: #07c160; font-weight: 700; animation: dc-ring-green 2s infinite; }
@keyframes dc-ring-red {
  0%, 100% { background: rgba(238, 10, 36, 0.32); box-shadow: inset 0 0 0 2px rgba(238, 10, 36, 0.65); }
  50% { background: rgba(238, 10, 36, 0.08); box-shadow: inset 0 0 0 2px rgba(238, 10, 36, 0.2); }
}
@keyframes dc-ring-yellow {
  0%, 100% { background: rgba(255, 151, 106, 0.32); box-shadow: inset 0 0 0 2px rgba(255, 151, 106, 0.65); }
  50% { background: rgba(255, 151, 106, 0.08); box-shadow: inset 0 0 0 2px rgba(255, 151, 106, 0.2); }
}
@keyframes dc-ring-green {
  0%, 100% { background: rgba(7, 193, 96, 0.3); box-shadow: inset 0 0 0 2px rgba(7, 193, 96, 0.6); }
  50% { background: rgba(7, 193, 96, 0.06); box-shadow: inset 0 0 0 2px rgba(7, 193, 96, 0.16); }
}

/* 出差日：仅弱化 emoji，融入主题，不填充背景 */
.dc-air {
  position: absolute;
  bottom: 3px;
  font-size: 11px;
  opacity: 0.5;
  filter: saturate(0.55);
}

/* 信用卡账单日/还款日标记（账=账单日 还=还款日） */
.dc-credit {
  display: flex;
  gap: 2px;
  margin-top: 3px;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
}
.dc-credit-tag {
  font-size: 9px;
  line-height: 1;
  padding: 1px 3px;
  border-radius: 4px;
  font-weight: 600;
}
.dc-credit-tag.c-bill { background: rgba(var(--dc-primary-rgb), 0.14); color: var(--dc-primary); }
.dc-credit-tag.c-repay { background: rgba(238, 10, 36, 0.12); color: var(--van-danger-color, #ee0a24); }
.dc-credit-more { font-size: 9px; color: var(--theme-text-tertiary, #969799); }

/* ── FLOW 金额（收+红 / 支-绿，日历数字本身保持原色，仅选中日变白） ── */
.dc-amt {
  font-size: 11px;
  font-weight: 600;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
  line-height: 1.15;
}
.dc-amts {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  margin: 2.5px 0;
  min-height: 26px;
  width: 100%;
  min-width: 0;
  line-height: 1.15;
}
.dc-amt.flow-income { color: var(--money-income); }
.dc-amt.flow-expense { color: var(--money-expense); }

/* ── SALARY 金额 / 计薪（日期数字保持默认白色，不染色） ── */
.dc-amt.formal { color: var(--dc-primary); }
.dc-amt.parttime { color: var(--van-orange, #ff976a); }
.dc-working {
  font-size: 10px;
  color: var(--theme-text-tertiary, #969799);
}
.dc-cell.not-working { opacity: 0.4; cursor: not-allowed; }

/* 折叠/收起 */
.dc-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  cursor: pointer;
  user-select: none;
  color: var(--dc-primary);
  font-size: 13px;
  font-weight: 500;
}
.dc-toggle:active { opacity: 0.6; }
.dc-toggle .van-icon { font-size: 16px; }
</style>
