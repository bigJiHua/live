<template>
  <div class="page-bank-card-manage">
    <!-- 统一顶栏：标题 + 分段切换 + 预览/排序开关（照搬 web views/BankCard/index.vue） -->
    <header class="page-header card-manage-header" :class="{ scrolled: scrolled }">
      <div class="header-row">
        <h1 class="title">{{ activeTab === 'credit' ? '信用卡' : '借记卡' }}</h1>
        <div class="segment-switch" role="tablist" v-if="!sortMode">
          <button class="segment-item" :class="{ active: activeTab === 'debit' }" @click="onTabChange('debit')">借记卡</button>
          <button class="segment-item" :class="{ active: activeTab === 'credit' }" @click="onTabChange('credit')">信用卡</button>
          <span class="segment-thumb" :class="activeTab" />
        </div>
      </div>

      <div class="header-row">
        <span class="subtitle">{{ sortMode ? '长按拖动卡片排序' : '管理你的银行卡' }}</span>
        <div class="header-actions">
          <label class="preview-toggle" :class="{ on: sortMode }" @click="toggleSort">
            <span class="preview-label">排序</span><span class="dot" />
          </label>
          <label class="preview-toggle" v-if="!sortMode" :class="{ on: previewMode }" @click="togglePreview">
            <span class="preview-label">预览</span><span class="dot" />
          </label>
        </div>
      </div>
    </header>

    <!-- 两个列表都在 DOM 里：用 v-show 切换而非 v-if，保持各自选中态/滚动位置，
         这正是 web 用 keep-alive 达到的效果（web router/map.js:450-466 两个路由都 meta.keepAlive）。 -->
    <!-- ⚠ :scrolled 必须传：web 原版子页是 inject('cardListScrolled') 直接拿壳层 ref，
         离线版改成了 prop 但曾经漏传 ⇒ 滚动时顶栏收了、添加按钮却不隐藏（2026-10-12 修）。 -->
    <CardListPane
      v-show="activeTab === 'debit'"
      card-type="debit"
      :cards-data="data.debit.cards"
      :flow-idle="data.debit.flowIdle"
      :loading="loading.debit"
      :scrolled="scrolled"
      @scroll-dir="onScrollDir"
    />
    <CardListPane
      v-show="activeTab === 'credit'"
      card-type="credit"
      :cards-data="data.credit.cards"
      :flow-idle="false"
      :loading="loading.credit"
      :scrolled="scrolled"
      @scroll-dir="onScrollDir"
    />
  </div>
</template>

<script setup>
/**
 * 离线 WebView 的根组件 —— 承载 web `views/BankCard/index.vue`（壳层）+ 两个卡片列表。
 *
 * ## 为什么把壳层 UI 搬进来
 * web 的壳层 `index.vue` 靠 `provide` 把 `cardPreviewMode` / `cardSortMode` / `cardListScrolled`
 * 传给子页，并把「借记卡/信用卡切换」实现成 **router.push**（`index.vue:78-80`）。
 * 离线版没有 router（`file://` 下 `createWebHistory` 必白屏），所以：
 *  - 壳层 UI 整体搬进本组件；
 *  - tab 切换改为本地 `activeTab` + `v-show`（**不用 v-if**，否则切换会丢选中态与滚动位置）；
 *  - 子页通过 `inject` 拿到 `cardPreviewMode` / `cardSortMode`，与 web 的 provide/inject 契约一致。
 *
 * ## 数据流
 * 原生 → `applyData(json)`（见 main.js 注册的 `window.__cardWebview`）→ 写入 `data`。
 * 本组件**不发任何网络请求**；需要新数据时调 `native.requestData(tab)`，由原生决定是否真发
 * （原生侧复用了 `SilentRefreshThrottle` 的 10s 时间窗，对齐 web `debit.vue:290-303`）。
 */
import { ref, reactive, provide, onMounted } from 'vue'
import CardListPane from './CardListPane.vue'
import { native } from './bridge'

const activeTab = ref('debit')
const previewMode = ref(false)
const sortMode = ref(false)
const scrolled = ref(false)
const loading = reactive({ debit: true, credit: true })

// 与 web index.vue:65-75 的 provide 同名同义，子页 inject 契约不变
provide('cardPreviewMode', previewMode)
provide('cardSortMode', sortMode)

/** 原生注入的数据。`flowIdle` 由原生算好（借记卡才需要，线上版是 `getCardsFlowStats`）。 */
const data = reactive({
  debit: { cards: [], flowIdle: false },
  credit: { cards: [], flowIdle: false },
})

const onTabChange = (name) => {
  if (name === activeTab.value) return
  activeTab.value = name
  scrolled.value = false
  // 切 tab 后如果该 tab 还没数据，向原生要一次（原生会按 10s 时间窗决定是否真发请求）
  if (data[name].cards.length === 0) native.requestData(name)
}

const togglePreview = () => { previewMode.value = !previewMode.value }
const toggleSort = () => {
  sortMode.value = !sortMode.value
  if (sortMode.value && data[activeTab.value].cards.length === 0) native.requestData(activeTab.value)
}
const onScrollDir = (dir) => { scrolled.value = dir === 'down' }

// ===== 暴露给原生的 API（由 main.js 挂到 window.__cardWebview）=====
function applyData(payload) {
  const t = payload && payload.tab === 'credit' ? 'credit' : 'debit'
  data[t].cards = Array.isArray(payload.cards) ? payload.cards : []
  data[t].flowIdle = !!payload.flowIdle
  loading[t] = false
}

function setTab(tab) {
  const t = tab === 'credit' ? 'credit' : 'debit'
  activeTab.value = t
  scrolled.value = false
  // ⚠ 这里**不能**顺手关 loading：原生在 ready 后先 setTab、再 fetch，
  //   在此关掉遮罩会把「取数还没回来」伪装成「加载完成但没数据」——
  //   2026-10-17 报的「编辑/删除卡片返回后列表变空」正是这个假象：
  //   首屏 fetch 被旧节流跳过 → 没数据 + loading 已被这里关掉 ⇒ 空态直接可见。
  //   loading 统一由 applyData（成功推数据）或原生 setLoading(false) 关闭。
}

function setLoading(tab, v) {
  loading[tab === 'credit' ? 'credit' : 'debit'] = !!v
}

/**
 * 主题注入：原生把 AppTokens 映射成与 web `composables/useUiTheme.js` 的 buildVars
 * **同名**的 CSS 变量推过来，挂到 `documentElement` 上。
 *
 * 为什么非得运行时给：`style.css` 的 `:root` 只写死浅色默认值，深色/自定义主题在 web 上
 * 全靠 useUiTheme 的 setProperty；离线入口不引那个 composable ⇒ 不注入就永远停在浅色，
 * 而系统栏/原生顶栏跟的是原生主题，两边就对不上。
 * 只给 `--theme-*` 就够：bundle 里 `--van-*` 已整体映射到 `--theme-*`，Vant 组件跟着走。
 */
function setTheme(payload) {
  const vars = typeof payload === 'string' ? JSON.parse(payload) : payload
  const root = document.documentElement
  Object.keys(vars || {}).forEach((k) => root.style.setProperty(k, vars[k]))
}

// ===== 注册给原生调用的命令入口 =====
// 由组件自己注册（而不是 main.js 里 `app.mount()` 后反查实例）：
// script setup 的实例引用在生产构建下可能被摇掉，且 mount 后再取实例存在时序坑。
onMounted(() => {
  window.__cardWebview = {
    applyData(json) {
      try {
        applyData(typeof json === 'string' ? JSON.parse(json) : json)
      } catch (e) {
        // 只报真错误。⚠ 这里不能用 reportError 打诊断信息 —— 它会置 state.errorMsg，
        // 而 CardWebScreen 见 errorMsg 非空就把整页换成「重新加载」遮罩。
        native.reportError('applyData parse failed: ' + e.message)
      }
    },
    setTab,
    setLoading,
    setTheme,
  }
})
</script>

<style scoped>
/* 与 web views/BankCard/index.vue:88-232 一致；两处有意差异：
   1) height: calc(100vh - 50px) → position:fixed;inset:0：离线 WebView 里没有 web MainLayout
      的固定头部，本组件自己就是整个页面；且不能用任何高度链（百分比/100vh 在 WebView
      首次 0 高布局下都可能解析成 0，详见下方注释）；
   2) 补 overflow: hidden：竖向整体由两个 pane 自己滚动，避免整页 + 内层双滚动。 */
.page-bank-card-manage {
  /* ⚠⚠ 2026-10-11 定案（真机 + 模拟器探针 chain= 实测）：
     Android WebView 里 loadUrl 早于 WebView 首次 layout 时，Chromium 按 0 高视口做
     首次布局，html/body/#app 的 height:100% **整条解析成 0px 且之后不重解析**
     —— 探针原话：`#app render=0 css=0px < BODY render=0 css=0px < HTML render=0 css=0px`。
     后果：高度链上的顶栏/两个列表全部 0 高被裁，屏幕上只剩 position:fixed 的
     「+添加卡片」（fixed 的包含块是视口，不吃百分比链）—— 就是「整屏只剩一个按钮」。
     桌面 Chromium 无此问题（加载时视口已定型），所以浏览器里怎么测都是好的。
     ✅ 修复：根容器改用 `position:fixed; inset:0` 直接锚定视口 —— fixed 包含块是
     视口（探针 vw=411x820 为真实视口），与百分比链彻底解耦。
     （loadUrl 推迟到 doOnLayout 之后是并列的第二道修复，见 CardWebScreen.kt。） */
  position: fixed;
  inset: 0;
  background: var(--theme-bg-secondary);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.card-manage-header {
  margin: 10px 20px 0;
  position: relative; z-index: 10;
  display: flex; flex-direction: column; gap: 12px;
  background: var(--theme-bg-secondary);
  overflow: hidden; max-height: 200px;
  transition: opacity 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    max-height 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    margin 0.32s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: max-height, opacity;
}
.card-manage-header.scrolled {
  opacity: 0; max-height: 0; margin-top: 0;
  padding-top: 0; padding-bottom: 0; pointer-events: none;
}
.header-row { display: flex; align-items: center; justify-content: space-between; }
.title { margin: 0; }

.segment-switch {
  position: relative; display: inline-flex; padding: 4px;
  /* ⚠ 2026-10-11 深色适配：原 background:var(--theme-bg-primary) 在深色下=bgPage
     (#0f0f12 近黑)，贴在 bgCard 的顶栏上就是一坨黑。改 bgThird：深色 #26262b 微隆起，
     浅色 #f2f3f5 与原 #f7f8fa 几乎无差。 */
  background: var(--theme-bg-tertiary, #fff);
  border-radius: 22px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.segment-item {
  position: relative; z-index: 2; border: none; background: transparent;
  padding: 7px 18px; font-size: 14px; font-weight: 600;
  color: var(--theme-text-secondary); cursor: pointer; border-radius: 18px;
  transition: color 0.28s ease;
}
.segment-item.active { color: #fff; }
.segment-thumb {
  position: absolute; z-index: 1; top: 4px; bottom: 4px;
  width: calc(50% - 4px); border-radius: 18px;
  background: var(--theme-primary);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}
.segment-thumb.debit { transform: translateX(0); }
.segment-thumb.credit { transform: translateX(100%); }

.header-actions { display: flex; align-items: center; justify-content: space-between; width: 164px; }
.preview-toggle {
  flex: 1; min-width: 0; justify-content: center;
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; background: var(--theme-bg-tertiary, #fff);
  border-radius: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer; user-select: none;
}
.preview-label {
  font-size: 13px; color: var(--theme-text-secondary); font-weight: 500;
  transition: color 0.2s ease;
}
.preview-toggle.on .preview-label { color: var(--theme-primary); font-weight: 600; }
.dot {
  flex: none; width: 18px; height: 18px; border-radius: 50%;
  border: 2px solid var(--theme-border, #dcdee0);
  background: transparent; transition: all 0.2s ease;
}
.preview-toggle.on .dot {
  border-color: var(--theme-primary);
  background: var(--theme-primary);
  /* 同上：color-mix 兜底，旧 WebView 丢掉第二条也不至于没有光环 */
  box-shadow: 0 0 0 3px rgba(58, 102, 224, 0.2);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--theme-primary) 20%, transparent);
}
</style>