<template>
  <div class="page-app-settings">
    <div class="section-title">分类设置</div>
    <van-cell-group inset class="app-card">
      <app-cell
        title="收支分类"
        label="管理支出/收入的分类"
        icon="orders-o"
        is-link
        @click="$router.push('/user/category-manage')"
      />
      <app-cell
        title="银行分类"
        label="管理银行卡所属银行分类"
        icon="card"
        is-link
        @click="$router.push('/user/bank-category-manage')"
      />
    </van-cell-group>

    <div class="section-title">UI 主题</div>
    <div class="theme-section app-card">
      <div class="theme-grid">
        <div
          class="theme-item"
          :class="{ active: choice === 'system' }"
          @click="setChoice('system')"
        >
          <div class="theme-swatch" :style="{ background: systemThemePreview }"></div>
          <div class="theme-name">系统默认</div>
          <van-icon v-if="choice === 'system'" name="success" class="theme-check" :color="themeActiveColor" />
        </div>
      </div>
      <template v-for="group in themeGroups" :key="group.title">
        <div class="theme-group-title">{{ group.title }}</div>
        <div class="theme-grid">
          <div
            v-for="p in group.items"
            :key="p.key"
            class="theme-item"
            :class="{ active: choice === p.key }"
            @click="setChoice(p.key)"
          >
            <div class="theme-swatch" :style="{ background: p.preview }"></div>
            <div class="theme-name">{{ p.name }}</div>
            <van-icon v-if="choice === p.key" name="success" class="theme-check" :color="themeActiveColor" />
          </div>
        </div>
      </template>
    </div>

    <div class="section-title">收支颜色</div>
    <div class="app-card money-color-group">
      <div class="money-color-preview">
        <span
          class="mc-char"
          :class="{ active: moneyMode === 'red-in' }"
          :style="{ color: 'var(--money-income)' }"
        >收</span>
        <span
          class="mc-char"
          :class="{ active: moneyMode === 'red-out' }"
          :style="{ color: 'var(--money-expense)' }"
        >支</span>
      </div>
      <div class="money-color-control">
        <van-button size="small" type="primary" @click="toggleMoneyMode">切换</van-button>
      </div>
    </div>

    <div class="section-title">安装站点应用</div>
    <van-cell-group inset class="app-card">
      <app-cell title="安装状态">
        <template #value>
          <app-tag :type="statusTagType">{{ statusText }}</app-tag>
        </template>
      </app-cell>
      <app-cell v-if="!pwaState.installed" title="安装应用" is-link @click="installPWA">
        <template #icon>
          <van-icon name="down" class="install-icon" />
        </template>
      </app-cell>
      <app-cell v-if="pwaState.installed" title="已添加到桌面" label="可在桌面直接打开使用" />

      <van-collapse v-model="activeCollapse" :border="false">
        <van-collapse-item title="检测详情" name="diagnostics" :border="false">
          <div class="diagnostics-list">
            <div class="diag-item" v-for="item in diagnostics" :key="item.label">
              <span class="diag-label">{{ item.label }}</span>
              <span :class="['diag-value', item.ok ? 'diag-ok' : 'diag-fail']">{{ item.value }}</span>
            </div>
          </div>
          <div class="diag-actions">
            <app-button size="small" plain type="primary" @click="refreshPWAStatus">重新检测</app-button>
            <app-button size="small" plain @click="showPwaLogs">查看日志</app-button>
          </div>
          <div class="pwa-log-box" v-if="pwaLogs.length > 0">
            <div v-for="item in pwaLogs.slice(-5)" :key="item.id" class="pwa-log-line">
              {{ item.text }}
            </div>
          </div>
        </van-collapse-item>
      </van-collapse>
    </van-cell-group>

    <!-- Demo 预览入口（仅 demo 模式展示，供小伙伴点击预览） -->
    <template v-if="isDemo">
      <div class="section-title">Demo 预览</div>
      <van-cell-group inset class="app-card">
        <app-cell
          v-for="d in demoLinks"
          :key="d.path"
          :title="d.title"
          :label="d.label"
          :icon="d.icon"
          is-link
          @click="$router.push(d.path)"
        />
      </van-cell-group>
    </template>

    <!-- 更多设置入口可在此添加 -->
    <!-- <div class="section-title">其他设置</div>
    <van-cell-group inset class="app-card">
      <app-cell title="通知设置" label="即将上线" icon="bell-o" is-link disabled />
    </van-cell-group> -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { showToast, showDialog } from 'vant'
import { useUiTheme } from '@/composables/useUiTheme'
import { useMoneyColor } from '@/composables/useMoneyColor'

// UI 主题自定义（系统默认 / 各方案），选择后即时应用到全局并保存到 localStorage
const { presets, activeKey, choice, setChoice } = useUiTheme()

// Demo 模式开关：仅 demo 模式展示 demo 预览入口，供小伙伴点击预览各组件 Demo
const isDemo = import.meta.env.VITE_APP_DEMO === 'true'
const demoLinks = [
  { path: '/demo/ui', title: 'UI 配色展示', label: '主题色板与组件样式预览', icon: 'palette-o' },
  { path: '/demo/calendar', title: '日历组件 Demo', label: '自研日历网格预览', icon: 'calendar-o' },
  { path: '/demo/keyboard', title: '键盘组件 Demo', label: '安全键盘交互预览', icon: 'keyboard-o' },
  { path: '/demo/bank-org', title: '卡组织 Icon Demo', label: '银行卡组织图标预览', icon: 'card' },
]
// 系统默认时展示"浅色靛蓝 / 深色黑金"的混合色块
const systemThemePreview = 'linear-gradient(135deg, #3a66e0, #C9A86A)'

// 收支金额颜色（红出绿收 / 红收绿支），切换后即时应用并保存到 localStorage
const { mode: moneyMode, setMode: setMoneyMode } = useMoneyColor()
const toggleMoneyMode = () => setMoneyMode(moneyMode.value === 'red-in' ? 'red-out' : 'red-in')
const moneyColorOptions = [
  { value: 'red-out', text: '红出绿收', desc: '支出为红，收入为绿', in: '#07c160', out: '#ee0a24' },
  { value: 'red-in', text: '红收绿支', desc: '收入为红，支出为绿（默认）', in: '#ee0a24', out: '#07c160' },
]
const themeActiveColor = computed(() => {
  const key = choice.value === 'system' ? activeKey.value : choice.value
  const t = presets.find((p) => p.key === key) || presets[2]
  return t.primary
})

// 按「白底配色 / 黑底配色」分组，组内按色系（绿→蓝→红→金→中性）排序
const HUE_ORDER = { green: 0, blue: 1, red: 2, gold: 3, neutral: 4 }
const themeGroups = computed(() => {
  const sortByHue = (a, b) => (HUE_ORDER[a.hue] ?? 9) - (HUE_ORDER[b.hue] ?? 9)
  return [
    { title: '白底配色', items: presets.filter((p) => p.mode === 'light').sort(sortByHue) },
    { title: '黑底配色', items: presets.filter((p) => p.mode === 'dark').sort(sortByHue) },
  ]
})

const activeCollapse = ref([])
const pwaLogs = ref([])
let pwaLogId = 0

const pwaState = reactive({
  canInstall: false,
  installed: false,
  installing: false,
  promptEvent: null,
})

const addPwaLog = (message) => {
  const text = `[${new Date().toLocaleTimeString()}] ${message}`
  pwaLogs.value.push({ id: ++pwaLogId, text })
  if (pwaLogs.value.length > 80) {
    pwaLogs.value = pwaLogs.value.slice(-80)
  }
  console.log(`[PWA] ${message}`)
}

const isStandalonePWA = () => {
  return window.__PWA_INSTALLED__ ||
    window.matchMedia?.('(display-mode: standalone)').matches ||
    window.navigator.standalone === true
}

const isIOSDevice = () => {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

const isInAppBrowser = () => {
  return /MicroMessenger|QQ\/|QQBrowser|DingTalk|Weibo|AlipayClient|BytedanceWebview|Lark/i.test(navigator.userAgent)
}

const statusText = computed(() => {
  if (pwaState.installed) return '已安装'
  if (pwaState.canInstall) return '可安装'
  if (isIOSDevice()) return 'iOS 请用 Safari 添加到主屏幕'
  if (isInAppBrowser()) return '内置浏览器不支持安装'
  return '待检测'
})

const statusTagType = computed(() => {
  if (pwaState.installed) return 'success'
  if (pwaState.canInstall) return 'primary'
  return 'warning'
})

const diagnostics = computed(() => [
  { label: '协议', value: location.protocol, ok: location.protocol === 'https:' || location.hostname === 'localhost' },
  { label: '安全上下文', value: window.isSecureContext ? '是' : '否', ok: !!window.isSecureContext },
  { label: '安装事件', value: pwaState.canInstall ? '已就绪' : '未触发', ok: pwaState.canInstall },
  { label: '独立窗口', value: isStandalonePWA() ? '是' : '否', ok: isStandalonePWA() },
  { label: 'iOS 设备', value: isIOSDevice() ? '是' : '否', ok: !isIOSDevice() },
  { label: '内置浏览器', value: isInAppBrowser() ? '是' : '否', ok: !isInAppBrowser() },
  { label: 'Service Worker', value: 'serviceWorker' in navigator ? '支持' : '不支持', ok: 'serviceWorker' in navigator },
  { label: '已安装状态', value: pwaState.installed ? '已安装' : '未安装', ok: pwaState.installed },
])

const syncPWAStatus = (source = '状态同步') => {
  pwaState.promptEvent = window.__PWA_PROMPT__ || pwaState.promptEvent
  pwaState.canInstall = !!pwaState.promptEvent
  pwaState.installed = isStandalonePWA()
  if (pwaState.installed) {
    pwaState.canInstall = false
    pwaState.promptEvent = null
  }
  addPwaLog(`${source}: installed=${pwaState.installed}, canInstall=${pwaState.canInstall}`)
}

const getPWAInstallDiagnostics = async () => {
  const checks = [
    `协议: ${location.protocol}`,
    `安全上下文: ${window.isSecureContext ? '是' : '否'}`,
    `安装事件: ${pwaState.canInstall ? '已就绪' : '未触发'}`,
    `独立窗口: ${isStandalonePWA() ? '是' : '否'}`,
    `iOS: ${isIOSDevice() ? '是' : '否'}`,
    `内置浏览器: ${isInAppBrowser() ? '是' : '否'}`,
    `Service Worker API: ${'serviceWorker' in navigator ? '支持' : '不支持'}`,
  ]

  if ('serviceWorker' in navigator) {
    const registration = await navigator.serviceWorker.getRegistration('/')
    checks.push(`Service Worker: ${registration ? '已注册' : '未注册'}`)
  }

  const manifestLink = document.querySelector("link[rel='manifest']")?.href
  checks.push(`Manifest: ${manifestLink ? '已发现' : '未发现'}`)

  if (manifestLink) {
    try {
      const res = await fetch(manifestLink, { cache: 'no-store' })
      checks.push(`Manifest 请求: ${res.ok ? '成功' : `失败 ${res.status}`}`)
    } catch (err) {
      checks.push(`Manifest 请求: 失败 ${err.message}`)
    }
  }

  checks.push(`UA: ${navigator.userAgent}`)
  return checks.join('\n')
}

const refreshPWAStatus = async () => {
  addPwaLog('用户点击重新检测')
  syncPWAStatus('重新检测')
  const diagnosticsText = await getPWAInstallDiagnostics()
  addPwaLog('诊断信息已生成')
  showDialog({
    title: 'PWA 检测结果',
    message: diagnosticsText,
    messageAlign: 'left',
  })
}

const showPwaLogs = () => {
  showDialog({
    title: 'PWA 安装日志',
    message: pwaLogs.value.map(item => item.text).join('\n') || '暂无日志',
    messageAlign: 'left',
  })
}

const installPWA = async () => {
  addPwaLog('用户点击安装应用')
  syncPWAStatus('安装前检测')

  if (pwaState.installed) {
    addPwaLog('已安装，跳过安装流程')
    showToast('应用已安装')
    return
  }

  if (isInAppBrowser()) {
    addPwaLog('检测到内置浏览器，终止安装流程')
    showDialog({
      title: '请在浏览器中打开',
      message: '微信、QQ 等内置浏览器通常不能安装 PWA。请用 Chrome、Edge 或 Safari 打开后再安装。',
    })
    return
  }

  if (!pwaState.promptEvent) {
    addPwaLog('没有 beforeinstallprompt 事件，无法调起浏览器安装弹窗')
    const diagnosticsText = await getPWAInstallDiagnostics()
    showDialog({
      title: isIOSDevice() ? '添加到主屏幕' : '当前浏览器未提供安装入口',
      message: isIOSDevice()
        ? `iPhone/iPad 不支持网页内安装弹窗。请点击 Safari 底部分享按钮，然后选择"添加到主屏幕"。\n\n${diagnosticsText}`
        : diagnosticsText,
      messageAlign: 'left',
    })
    return
  }

  pwaState.installing = true
  addPwaLog('准备调用 beforeinstallprompt.prompt()')

  try {
    await pwaState.promptEvent.prompt()
    addPwaLog('浏览器安装弹窗已调起，等待用户选择')

    const result = await pwaState.promptEvent.userChoice
    addPwaLog(`用户选择结果: ${result?.outcome || '未知'}`)

    window.__PWA_PROMPT__ = null
    pwaState.promptEvent = null
    pwaState.canInstall = false

    if (result?.outcome === 'accepted') {
      addPwaLog('用户接受安装，等待 appinstalled 事件')
      showDialog({
        title: '安装流程',
        message: pwaLogs.value.map(item => item.text).join('\n'),
        messageAlign: 'left',
      })
    } else {
      addPwaLog('用户取消/关闭了安装弹窗')
      showDialog({
        title: '安装已取消',
        message: pwaLogs.value.map(item => item.text).join('\n'),
        messageAlign: 'left',
      })
    }
  } catch (err) {
    addPwaLog(`安装流程异常: ${err.message}`)
    showDialog({
      title: '安装失败',
      message: pwaLogs.value.map(item => item.text).join('\n'),
      messageAlign: 'left',
    })
  } finally {
    pwaState.installing = false
    syncPWAStatus('安装后检测')
  }
}

const handleBeforeInstallPrompt = (event) => {
  event.preventDefault()
  window.__PWA_PROMPT__ = event
  pwaState.promptEvent = event
  pwaState.canInstall = true
  addPwaLog('收到 beforeinstallprompt 事件，安装按钮可用')
}

const handleAppInstalled = () => {
  window.__PWA_INSTALLED__ = true
  window.__PWA_PROMPT__ = null
  pwaState.installed = true
  pwaState.canInstall = false
  pwaState.promptEvent = null
  addPwaLog('收到 appinstalled 事件，应用已安装')
  showToast('应用已安装')
}

const handleVisibilityChange = () => {
  if (!document.hidden) {
    syncPWAStatus('页面重新可见检测')
  }
}

onMounted(() => {
  syncPWAStatus('初始化检测')
  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
  window.addEventListener('appinstalled', handleAppInstalled)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
  window.removeEventListener('appinstalled', handleAppInstalled)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.page-app-settings {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-top: 12px;
}

.section-title {
  padding: 20px 20px 10px;
  font-size: 13px;
  color: var(--theme-text-tertiary);
  font-weight: 500;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

.theme-section {
  margin: 0 16px;
  padding: 16px 16px 10px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
}

.theme-group-title {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin: 14px 0 8px;
}

.theme-group-title:first-of-type {
  margin-top: 0;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.theme-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 4px 8px;
  border-radius: 10px;
  background: var(--theme-bg-tertiary);
  border: 1px solid var(--theme-border);
  cursor: pointer;
  min-width: 0;
  transition: border-color 0.2s, background 0.2s;
}

.theme-item.active {
  border-color: var(--theme-primary);
  background: var(--theme-primary-light);
}

.theme-swatch {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  flex-shrink: 0;
}

.theme-name {
  font-size: 11px;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  line-height: 1.2;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.theme-item.active .theme-name {
  color: var(--theme-text-primary);
  font-weight: 500;
}

.theme-check {
  position: absolute;
  top: 3px;
  right: 3px;
  font-size: 14px;
}

.install-icon {
  margin-right: 6px;
  color: var(--theme-primary);
}

.diagnostics-list {
  padding: 4px 0;
}

.diag-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
}

.diag-label {
  color: var(--theme-text-secondary);
}

.diag-value {
  font-weight: 500;
}

.diag-ok {
  color: var(--van-green, #07c160);
}

.diag-fail {
  color: var(--van-danger-color, #ee0a24);
}

.diag-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.pwa-log-box {
  margin-top: 8px;
  padding: 8px;
  background: var(--theme-bg-primary);
  border-radius: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.pwa-log-line {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  line-height: 1.6;
  font-family: monospace;
}

/* 收支颜色分组：预览（左）与下拉框（右）左右并排 */
.money-color-group {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 16px;
  padding: 12px 16px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
}
/* 收支颜色预览：选中的字（收/支）放大，不加粗，两字靠拢 */
.money-color-preview {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.money-color-preview .mc-char {
  font-size: 16px;
  font-weight: 400;
  line-height: 1;
  transition: font-size 0.18s ease;
}
.money-color-preview .mc-char.active {
  font-size: 30px;
  font-weight: 400;
}
.money-color-control {
  flex: none;
  display: flex;
  justify-content: flex-end;
}
</style>