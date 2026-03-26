<template>
  <div class="login-container">
    <div class="login-header">
      <div class="logo">💰</div>
      <h1 class="title">Gold 财管</h1>
      <p class="subtitle">开启您的数字资产管理</p>
    </div>

    <van-form @submit="onSubmit" class="login-form">
      <van-cell-group inset>
        <van-field
          v-model="username"
          name="username"
          label="账号"
          placeholder="请输入用户名"
          left-icon="user-o"
          autocomplete="username"
          :rules="[{ required: true, message: '请填写用户名' }]"
        />
        <van-field
          v-model="password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          left-icon="lock"
          autocomplete="current-password"
          :rules="[{ required: true, message: '请填写密码' }]"
        />
      </van-cell-group>

      <div class="submit-bar">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="loading"
          loading-text="正在安全登录..."
        >
          立即登录
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

const router = useRouter()
const username = ref('admin')
const password = ref('123456')
const loading = ref(false)

const onSubmit = async (values) => {
  loading.value = true

  try {
    // --- 模拟后端 API 调用 ---
    // const res = await api.login(values);
    // const { token } = res.data;

    const mockToken = 'eyPIn_Token_Example_123456' // 假设这是后端返回的 JWT

    // 1. 存储 Token 到本地
    localStorage.setItem('finance_token', mockToken)

    showToast({
      type: 'success',
      message: '登录成功',
    })
    // 模拟成功逻辑
    setTimeout(() => {
      loading.value = false
      router.push('/')
    }, 1500)
  } catch (error) {
    showToast('登录失败，请检查账号密码')
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background-color: #fff;
  display: flex;
  flex-direction: column;
  padding: 0 20px;
}

.login-header {
  margin-top: 80px;
  margin-bottom: 40px;
  text-align: center;
}

.logo {
  font-size: 60px;
  margin-bottom: 20px;
}

.title {
  font-size: 24px;
  color: #323233;
  margin-bottom: 8px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: #969799;
}

.login-form {
  margin-bottom: 20px;
}

/* 调整 Vant 单元格组间距 */
:deep(.van-cell-group--inset) {
  margin: 0;
}

.submit-bar {
  margin-top: 30px;
}
</style>
