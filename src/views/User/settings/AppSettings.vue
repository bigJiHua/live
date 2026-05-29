<template>
  <div class="page-app-settings">
    <div class="section-title">分类设置</div>
    <van-cell-group inset class="app-card">
      <van-cell
        title="收支分类"
        label="管理支出/收入的分类"
        icon="orders-o"
        is-link
        @click="$router.push('/user/category-manage')"
      />
      <van-cell
        title="银行分类"
        label="管理银行卡所属银行分类"
        icon="card"
        is-link
        @click="$router.push('/user/bank-category-manage')"
      />
    </van-cell-group>

    <div class="section-title">安装站点应用</div>
    <van-cell-group inset class="app-card">
      <van-cell title="安装状态">
        <template #value>
          <van-tag :type="statusTagType">{{ statusText }}</van-tag>
        </template>
      </van-cell>
      <van-cell v-if="!pwaState.installed" title="安装应用" is-link @click="installPWA">
        <template #icon>
          <van-icon name="down" class="install-icon" />
        </template>
      </van-cell>
      <van-cell v-if="pwaState.installed" title="已添加到桌面" label="可在桌面直接打开使用" />

      <van-collapse v-model="activeCollapse" :border="false">
        <van-collapse-item title="检测详情" name="diagnostics" :border="false">
          <div class="diagnostics-list">
            <div class="diag-item" v-for="item in diagnostics" :key="item.label">
              <span class="diag-label">{{ item.label }}</span>
              <span :class="['diag-value', item.ok ? 'diag-ok' : 'diag-fail']">{{ item.value }}</span>
            </div>
          </div>
          <div class="diag-actions">
            <van-button size="small" plain type="primary" @click="refreshPWAStatus">重新检测</van-button>
            <van-button size="small" plain @click="showPwaLogs">查看日志</van-button>
          </div>
          <div class="pwa-log-box" v-if="pwaLogs.length > 0">
            <div v-for="item in pwaLogs.slice(-5)" :key="item.id" class="pwa-log-line">
              {{ item.text }}
            </div>
          </div>
        </van-collapse-item>
      </van-collapse>
    </van-cell-group>

    <!-- 更多设置入口可在此添加 -->
    <!-- <div class="section-title">其他设置</div>
    <van-cell-group inset class="app-card">
      <van-cell title="通知设置" label="即将上线" icon="bell-o" is-link disabled />
    </van-cell-group> -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { showToast, showDialog } from 'vant'

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
  background: #f7f8fa;
  padding-top: 12px;
}

.section-title {
  padding: 20px 20px 10px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

.install-icon {
  margin-right: 6px;
  color: #1989fa;
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
  color: #646566;
}

.diag-value {
  font-weight: 500;
}

.diag-ok {
  color: #07c160;
}

.diag-fail {
  color: #ee0a24;
}

.diag-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.pwa-log-box {
  margin-top: 8px;
  padding: 8px;
  background: #f7f8fa;
  border-radius: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.pwa-log-line {
  font-size: 11px;
  color: #969799;
  line-height: 1.6;
  font-family: monospace;
}
</style>