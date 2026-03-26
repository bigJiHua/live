<template>
  <div class="page-pin-manage">
    <div class="manage-content">
      <div class="status-card app-card">
        <div class="status-icon">
          <van-icon :name="hasPinSet ? 'shield-o' : 'shield-fail-o'" size="48" :color="hasPinSet ? '#07c160' : '#ccc'" />
        </div>
        <div class="status-info">
          <div class="status-title">{{ hasPinSet ? 'PIN 码已设置' : '未设置 PIN 码' }}</div>
          <div class="status-desc">
            {{ hasPinSet ? '您的账户已启用 PIN 码保护' : '设置 PIN 码可以更好地保护您的账户安全' }}
          </div>
        </div>
        <div class="status-badge" :class="{ enabled: pinEnabled }">
          {{ pinEnabled ? '已启用' : '已禁用' }}
        </div>
      </div>

      <div class="action-section">
        <div class="section-title">操作选项</div>
        <van-cell-group inset class="app-card">
          <van-cell
            v-if="!hasPinSet"
            title="设置 PIN 码"
            icon="lock"
            is-link
            center
            @click="goToSetup('new')"
          >
            <template #right-icon>
              <van-icon name="arrow" color="#969799" />
            </template>
          </van-cell>
          <van-cell
            v-if="hasPinSet"
            title="修改 PIN 码"
            icon="edit"
            is-link
            center
            @click="goToSetup('modify')"
          >
            <template #right-icon>
              <van-icon name="arrow" color="#969799" />
            </template>
          </van-cell>
          <van-cell
            v-if="hasPinSet"
            title="关闭 PIN 码"
            icon="cross"
            is-link
            center
            @click="handleDisablePin"
          >
            <template #right-icon>
              <van-icon name="arrow" color="#969799" />
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <div class="security-tips">
        <van-notice-bar
          left-icon="info-o"
          text="PIN 码用于保护您的敏感操作，请妥善保管，不要告知他人。"
          background="#fff7e6"
          color="#ff9900"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'

const router = useRouter()

const hasPinSet = computed(() => !!localStorage.getItem('user_pin'))
const pinEnabled = computed(() => localStorage.getItem('pin_enabled') === 'true')

const goToSetup = (mode) => {
  if (mode === 'new') {
    // 首次设置，不需要验证旧密码
    router.push({
      path: '/user/pin-setup',
      query: { mode: 'new' }
    })
  } else {
    // 修改，需要先验证旧密码
    router.push({
      path: '/user/pin-setup',
      query: { mode: 'modify' }
    })
  }
}

const handleDisablePin = () => {
  showConfirmDialog({
    title: '关闭 PIN 码',
    message: '关闭 PIN 码后，您的账户安全保护将降低，确定要关闭吗？',
  }).then(() => {
    localStorage.removeItem('user_pin')
    localStorage.setItem('pin_enabled', 'false')
    showToast('PIN 码已关闭')
    router.back()
  }).catch(() => {
    // 用户取消
  })
}
</script>

<style scoped>
.page-pin-manage {
  min-height: 100vh;
  background: #f7f8fa;
  padding-top: 0;
}

.manage-content {
  padding: 16px;
  padding-top: 80px; /* 避开 MainLayout 导航栏 */
}

/* 状态卡片 */
.status-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
}

.status-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #f7f8fa;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.status-info {
  text-align: center;
  margin-bottom: 16px;
}

.status-title {
  font-size: 18px;
  font-weight: bold;
  color: #323233;
  margin-bottom: 8px;
}

.status-desc {
  font-size: 14px;
  color: #969799;
  line-height: 1.5;
}

.status-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  background: #f2f3f5;
  color: #969799;
  font-weight: 500;
}

.status-badge.enabled {
  background: #e8f9f0;
  color: #07c160;
}

/* 操作区域 */
.action-section {
  margin-bottom: 24px;
}

.section-title {
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
  margin-bottom: 8px;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}

/* 安全提示 */
.security-tips {
  margin-top: 24px;
}
</style>
