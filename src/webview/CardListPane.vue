<template>
  <div
    class="page-card-list"
    :class="{ 'sort-page': sortMode, scrolled: scrolled }"
    @scroll.passive="onScroll"
    @touchstart.passive="onPullStart"
    @touchmove="onPullMove"
    @touchend="onPullEnd"
    @touchcancel="onPullEnd"
  >
    <!-- 下拉刷新指示器：fixed 定位，不占列表布局；只在非排序模式出现 -->
    <div
      v-if="!sortMode"
      class="pull-refresh"
      :style="{ opacity: pullOpacity, top: (8 + pullDistance) + 'px' }"
    >
      <van-loading v-if="refreshing" size="14px" />
      <van-icon v-else name="arrow-down" />
      <span>{{ refreshing ? '刷新中…' : (pullDistance >= PULL_THRESHOLD ? '松开刷新' : '下拉刷新') }}</span>
    </div>

    <!-- 排序模式：简洁 list + 拖拽（vuedraggable，与 web debit.vue:5-27 同款） -->
    <div v-if="sortMode" class="sort-scroll">
      <draggable
        v-model="cards"
        item-key="id"
        class="sort-list"
        handle=".sort-handle"
        :animation="180"
        ghost-class="sort-ghost"
        drag-class="sort-drag"
        :scroll-sensitivity="80"
        :scroll-speed="12"
        @end="commitSort"
      >
        <template #item="{ element }">
          <div class="sort-item">
            <span class="sort-name">
              <span class="sort-bank">{{ element.bankName }}</span>
              <span class="sort-last4">{{ element.last4No }}</span>
            </span>
            <van-icon name="bars" class="sort-handle" />
          </div>
        </template>
      </draggable>
    </div>

    <CardStack
      v-else
      v-model:selected-id="selectedId"
      :card-list="displayCards"
      default-color="#4A90E2"
      :card-type-label="isDebit ? 'DEBIT CARD' : 'CREDIT CARD'"
      :card-img-errors="cardImgError"
      :bank-icon-errors="bankIconError"
      @edit="goToEdit"
      @card-img-error="onCardImgError"
      @bank-icon-error="onBankIconError"
    />

    <van-empty v-if="!loading && cards.length === 0" :description="emptyText" />

    <div class="add-btn-wrap" :class="{ hidden: scrolled }" v-if="selectedId === null && !sortMode">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加卡片</span>
      </button>
    </div>

    <!--
      ⚠ 不能用 van-overlay：它默认带 rgba(0,0,0,.7) 深色底，
      首次加载 / 切 tab 时整页会「啪」地暗一下，没有任何过渡（2026-10-18 负责人反馈）。
      改成透明层：只显示转圈，不压暗、不吃点击。
      下拉刷新（force）走顶部刷新指示器，不经过这里（原生侧 force 不打开 loading）。
    -->
    <div v-if="loading" class="pane-loading">
      <van-loading size="28px" vertical>加载中...</van-loading>
    </div>

    <!-- 选中卡片弹起时：底部毛玻璃「查看消费明细」 -->
    <transition name="fade">
      <div class="flow-bar" v-if="selectedId !== null && selectedCard">
        <button class="flow-btn" type="button" @click="openCardFlow">
          <van-icon name="orders-o" />
          <span>查看消费明细</span>
        </button>
        <div class="flow-empty-tip" v-if="cardFlowIdle">近6个月无动账</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
/**
 * 离线版卡片列表（借记卡 / 信用卡共用，靠 `cardType` prop 区分）。
 *
 * ## 与 web `views/BankCard/debit.vue` 的关系
 * 模板与样式**沿用**线上版（保持视觉一致），只有数据层被换掉：
 *  - 线上：`getCardList()` / `categoryApi.list('bank')` / `getCardsFlowStats()` 三个 axios
 *  - 离线：**全部由原生注入**。父组件 `CardOffline.vue` 收到 `applyData` 后作为 props 传下来
 *
 * 之所以复制一份而不改 `debit.vue`/`credit.vue`：线上 web 版必须零影响。
 * 另外两子页差异只有「借记卡才有近6个月无动账」，合并成一个组件比维护两份更清晰。
 *
 * ⚠ 原生已把字段映射成本组件要用的 camelCase 结构（bankName/bankIconUrl/last4No…），
 *   与 `debit.vue:244-268` 线上版的映射结果一致，故此处不再做字段转换。
 */
import { ref, computed, watch, inject } from 'vue'
import draggable from 'vuedraggable'
import CardStack from '@/components/BankCard/CardStack.vue'
import { native } from './bridge'

const props = defineProps({
  /** 'debit' | 'credit' */
  cardType: { type: String, required: true },
  /** 原生注入的卡片列表（已映射好 camelCase） */
  cardsData: { type: Array, default: () => [] },
  /** 原生算好的「近6个月无动账」（仅借记卡用） */
  flowIdle: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  scrolled: { type: Boolean, default: false },
})

const previewMode = inject('cardPreviewMode', ref(false))
const sortMode = inject('cardSortMode', ref(false))

const isDebit = computed(() => props.cardType === 'debit')
const emptyText = computed(() =>
  isDebit.value
    ? '暂无借记卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！'
    : '暂无信用卡'
)

// ===== 下拉刷新 =====
// 2026-10-17 负责人要求：卡片列表要有手动重新取数入口，
// 避免「从编辑/删除页返回后列表空了」时只能杀进程。
// 手势只在本组件自己的滚动容器上生效，不影响 CardStack 的点击与拖拽排序。
const PULL_THRESHOLD = 56
const pullDistance = ref(0)
const refreshing = ref(false)
let pullStartY = 0
let pulling = false
let refreshTimer = null

const pullOpacity = computed(() =>
  refreshing.value ? 1 : Math.min(1, pullDistance.value / 36)
)

// 本地副本：排序模式下 vuedraggable 直接改这个数组的顺序（线上版同款做法）
const cards = ref([])
watch(
  () => props.cardsData,
  (v) => {
    cards.value = (v || []).slice()
    // 原生 applyData 一定会换一个新数组 ⇒ 收到即代表本轮刷新结束
    if (refreshing.value) {
      refreshing.value = false
      clearTimeout(refreshTimer)
    }
  },
  { immediate: true }
)

const selectedId = ref(null)
const bankIconError = ref({})
const cardImgError = ref({})
const cardFlowIdle = computed(() => props.flowIdle)

// ===== 预览脱敏（与 web debit.vue:113-148 同算法）=====
const random4 = () => String(Math.floor(1000 + Math.random() * 9000))
const randomDigits = (n) => {
  let s = ''
  for (let i = 0; i < n; i++) s += Math.floor(Math.random() * 10)
  return s
}
const maskedCardList = ref([])
const displayCards = computed(() => (previewMode.value ? maskedCardList.value : cards.value))

watch([previewMode, cards], ([mode, list]) => {
  if (!mode) { maskedCardList.value = []; return }
  maskedCardList.value = (list || []).map((item) => {
    const bin = item.cardBin || ''
    const maskedBin = bin.slice(0, 3) + randomDigits(bin.length > 3 ? bin.length - 3 : 3)
    return { ...item, last4No: random4(), cardBin: maskedBin }
  })
}, { immediate: true, deep: true })

const selectedCard = computed(() => cards.value.find((c) => c.id === selectedId.value) || null)

// ===== 滚动方向 → 收起/展开顶栏（阈值死区照抄线上版，避免临界抖动）=====
let lastScrollTop = 0
const SCROLL_DIR_THRESHOLD = 4
const onScroll = (e) => {
  const top = e.target.scrollTop || 0
  const delta = top - lastScrollTop
  if (delta > SCROLL_DIR_THRESHOLD) emit('scroll-dir', 'down')
  else if (delta < -SCROLL_DIR_THRESHOLD) emit('scroll-dir', 'up')
  lastScrollTop = top
}

// ===== 下拉刷新手势 =====
// 仅在「非排序模式 + 滚动到顶部 + 非刷新中」启动；0.55 阻尼，最大 80px。
const onPullStart = (e) => {
  if (sortMode.value || refreshing.value) return
  const el = e.currentTarget
  if (!el || el.scrollTop > 0) return
  pullStartY = e.touches[0].clientY
  pulling = true
}

const onPullMove = (e) => {
  if (!pulling) return
  const dy = e.touches[0].clientY - pullStartY
  if (dy <= 0) { pullDistance.value = 0; return }
  pullDistance.value = Math.min(80, dy * 0.55)
  // 阻止 WebView 的原生 overscroll/回弹，让指示器跟手
  if (pullDistance.value > 4 && e.cancelable) e.preventDefault()
}

const onPullEnd = () => {
  if (!pulling) return
  pulling = false
  if (pullDistance.value >= PULL_THRESHOLD) {
    pullDistance.value = 0
    refreshing.value = true
    native.refresh(props.cardType)
    clearTimeout(refreshTimer)
    // 兜底：原生 8s 内没回数据也要把「刷新中」收掉，避免永久卡住
    refreshTimer = setTimeout(() => { refreshing.value = false }, 8000)
  } else {
    pullDistance.value = 0
  }
}

// ===== 交互 → 原生 =====
const emit = defineEmits(['scroll-dir'])

const goToEdit = (item) => {
  selectedId.value = null
  native.navigate(`/card/edit?id=${item.id}&from=${props.cardType}`)
}
const goToAdd = () => native.navigate('/card/add')
const openCardFlow = () => {
  const id = selectedCard.value?.id
  selectedId.value = null
  if (id) native.navigate(`/finance/report/card-flow?cardId=${id}`)
}
const commitSort = (e) => {
  if (e && typeof e.oldIndex === 'number' && e.oldIndex === e.newIndex) return
  native.saveSort(cards.value.map((c, i) => ({ id: c.id, sort: i + 1 })))
}
const onBankIconError = (cardId) => { bankIconError.value[cardId] = true }
const onCardImgError = (cardId) => { cardImgError.value[cardId] = true }

/** 切 tab 时由父组件调：清空选中态（刷新时机由原生控制，web 不再自己发请求）。 */
const resetSelection = () => { selectedId.value = null }
defineExpose({ resetSelection })
</script>

<style scoped>
/* 与 web views/BankCard/debit.vue:324-513 一致；仅 .sort-page 的 top 由 150px 改为 0
   （离线版 header 在本组件之外，排序页从容器顶部铺满，不必再给壳层 header 预留 150px）。 */
.page-card-list {
  flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch;
  background: var(--theme-bg-secondary);
  padding: 24px 20px 120px;
  position: relative; overflow-x: hidden;
  transition: min-height 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    padding-bottom 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}
.sort-page {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  margin: 0; padding: 0; overflow: hidden;
  display: flex; flex-direction: column;
}
.sort-scroll {
  flex: 1; min-height: 0; overflow-y: auto; -webkit-overflow-scrolling: touch; padding: 0;
  /* 顶栏（z-index:10）会悬浮在排序页之上，前两项会被遮住 ⇒ 预留顶栏高度
     （88px 内容 + 10px 外边距 ≈ 98px，多留 10px 呼吸位）。web 原版是 sort-page top:150px
     达到同样效果，离线版 header 矮一些，按实测取 108px。 */
  padding-top: 108px;
}
.sort-list { list-style: none; margin: 0; padding: 0; }
.sort-item {
  height: 40px; margin-bottom: 2px; padding: 0 15px 0 20px;
  display: flex; align-items: center; justify-content: space-between;
  /* 同 CardOffline 的分段开关：深色下 bgPrimary=近黑，改 bgThird 微隆起 */
  background: var(--theme-bg-tertiary, #fff);
  border-radius: 12px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05); user-select: none;
}
.sort-name { flex: 1; display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.sort-bank {
  font-size: 15px; font-weight: 600; color: var(--theme-text-primary, #323233);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.sort-last4 {
  font-size: 14px; color: var(--theme-text-secondary, #969799);
  font-variant-numeric: tabular-nums; letter-spacing: 1px; flex-shrink: 0;
}
.sort-handle {
  font-size: 20px; color: var(--theme-text-secondary, #c8c9cc);
  flex-shrink: 0; cursor: grab; padding: 4px; touch-action: none;
}
.sort-handle:active { cursor: grabbing; color: var(--theme-primary); }
.sort-ghost { opacity: 0.4; background: var(--theme-primary, #1989fa); }
.sort-ghost * { visibility: hidden; }
.sort-drag { box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2); opacity: 0.95; }

.add-btn-wrap {
  position: fixed; bottom: 30px; left: 20px; right: 20px;
  z-index: 12000; transition: transform 0.3s ease, opacity 0.3s ease;
}
.add-btn-wrap.hidden { transform: translateY(160%); opacity: 0; pointer-events: none; }
.page-card-list.scrolled { padding-bottom: 24px; }
.glass-add-btn {
  width: 100%; height: 56px; background: var(--theme-primary); color: #fff;
  border: none; border-radius: 28px; font-weight: 600; font-size: 16px;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  /* 光晕跟随主色（原来是写死的蓝 rgba(25,137,250,.3)，金/绿主题下违和）。
     ⚠ color-mix 需 Chrome 111+：旧 WebView 会丢弃整条声明 ⇒ 光晕全丢（变透明），
     所以前面压一条 rgba 兜底，新引擎用后者覆盖。 */
  box-shadow: 0 8px 24px rgba(25, 137, 250, 0.3);
  box-shadow: 0 8px 24px color-mix(in srgb, var(--theme-primary) 42%, transparent);
}
.flex-center { display: flex; height: 100%; align-items: center; justify-content: center; }

/* 透明加载层：不遮暗页面，也不拦截点击（只做「正在取数」的视觉提示） */
.pane-loading {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  pointer-events: none;
}

.flow-bar {
  position: fixed; top: 280px; left: 20px; right: 20px; z-index: 96000;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.flow-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  width: 100%; height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.3); border-radius: 24px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  color: #fff; font-size: 15px; font-weight: 600;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3); cursor: pointer; white-space: nowrap;
}
.flow-btn:active { opacity: 0.85; }
.flow-empty-tip { font-size: 12px; color: #ff7a7a; text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5); }

/* 下拉刷新提示：fixed 定位，不参与列表布局；top 由内联样式跟随下拉距离 */
.pull-refresh {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  z-index: 11000;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 14px;
  background: var(--theme-bg-tertiary, #fff);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.14);
  font-size: 12px;
  color: var(--theme-text-secondary, #969799);
  white-space: nowrap;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.fade-enter-active, .fade-leave-active { transition: all 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(30%); }
</style>

<!--
  ⚠ 非 scoped：专门覆盖子组件 CardStack 的样式。
  CardStack.vue 与线上 web 共用，**不能改它本身**（线上零影响是硬约束）；
  本组件只被离线入口 `src/webview/main.js` 引用，因此这里的覆盖不会进入 web 包。

  2026-10-17 负责人报「点击卡片弹起的一瞬间，遮罩后的画面会刷新闪烁一下」。
  根因是 Android WebView 的合成层重建：
   1) `.stack-backdrop` 的 backdrop-filter 首次出现（opacity 0→1）时要为整片
      下层内容创建离屏纹理做模糊 → 整屏重绘一次；
   2) `.is-dimmed` 的 blur(1px) 让每张未选中卡各自再建一个滤镜层，
      与遮罩同一帧出现，成倍放大重绘；
   3) CardStack 选中时会切 document.body.style.overflow，而本页滚动在
      `.page-card-list` 内部、body 本就不滚 → 这次切换只会触发一次整页重排。
  下面三条分别掐掉这三个来源，视觉上只少了「遮罩内那层模糊」。
-->
<style>
.card-stack-container .stack-backdrop {
  -webkit-backdrop-filter: none !important;
  backdrop-filter: none !important;
  background: rgba(0, 0, 0, 0.66) !important;
}
/* 未选中卡已被遮罩盖住，亮度差肉眼几乎不可见；换掉 filter 可消除逐卡离屏层 */
.card-stack-container .bank-card-item.is-dimmed {
  filter: none !important;
}
/* 锁死 body 滚动：抵消 CardStack 的 lockScroll/unlockScroll 切换 */
html,
body {
  overflow: hidden !important;
}
</style>