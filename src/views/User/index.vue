<template>
  <div class="page-user">
    <div class="profile-card" @click="handleEditProfile">
      <van-image
        round
        width="64"
        height="64"
        src="https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg"
        class="profile-avatar"
      />
      <div class="profile-info">
        <div class="nickname-row">
          <span class="nickname">Gemini 大师</span>
        </div>
        <div class="user-id">账号 ID: 88886666</div>
      </div>
      <van-icon name="arrow" color="#969799" size="16" />
    </div>

    <div class="menu-sections">
      <div class="section-title">安全与隐私</div>
      <van-cell-group inset class="app-card">
        <van-cell title="PIN 码访问锁定" label="进入系统需二次验证" center>
          <template #right-icon>
            <van-switch
              v-model="security.pinEnabled"
              size="22px"
              @change="onPinSwitch"
            />
          </template>
        </van-cell>
        <van-cell
          title="PIN 码管理"
          is-link
          center
          @click="goToPinManage"
        />
      </van-cell-group>

      <div class="section-title">系统管理</div>
      <van-cell-group inset class="app-card">
        <van-cell
          title="偏好设置"
          icon="setting-o"
          is-link
          @click="$router.push('/settings')"
        />
        <van-cell
          title="UI 主题配色"
          icon="color-o"
          is-link
          @click="$router.push('/user/theme-settings')"
        />
        <van-cell
          title="修改登录密码"
          icon="shield-check-o"
          is-link
          @click="handleUpdatePassword"
        />
      </van-cell-group>
    </div>

    <div class="logout-wrapper">
      <van-button block round type="danger" plain @click="onLogout"
        >退出当前登录</van-button
      >
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'

const router = useRouter()
const security = reactive({ pinEnabled: false, hasPinSet: false })

// 初始化时检查本地存储的 PIN 状态
onMounted(() => {
  security.pinEnabled = localStorage.getItem('pin_enabled') === 'true'
  security.hasPinSet = !!localStorage.getItem('user_pin')
})

// 跳转修改密码页面
const handleUpdatePassword = () => {
  router.push('/update-password')
}

const handleEditProfile = () => {
  showToast('进入个人资料编辑')
}

const onPinSwitch = (checked) => {
  if (checked && !security.hasPinSet) {
    showToast('请先设置 PIN 码')
    security.pinEnabled = false
  } else {
    localStorage.setItem('pin_enabled', checked ? 'true' : 'false')
  }
}

const goToPinManage = () => {
  router.push('/user/pin-manage')
}

const onLogout = () => {
  showConfirmDialog({ title: '提醒', message: '确定退出登录？' }).then(() => {
    localStorage.removeItem('finance_token')
    router.push('/login')
    showToast('已安全退出')
  })
}
</script>

<style scoped>
.page-user {
  background: #f7f8fa;
  max-height: 100vh;
  overflow-y: auto;
  padding-bottom: 30px;
}

.profile-card {
  background: white;
  padding: 50px 20px 30px;
  display: flex;
  align-items: center;
  transition: background 0.2s;
}
.profile-card:active {
  background: #f2f3f5;
}

.profile-avatar {
  margin-right: 16px;
  border: 1px solid #f2f3f5;
}

.profile-info {
  flex: 1;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.nickname {
  font-size: 20px;
  font-weight: bold;
  color: #323233;
}

.user-id {
  font-size: 13px;
  color: #969799;
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
.logout-wrapper {
  margin: 40px 24px;
}
</style>