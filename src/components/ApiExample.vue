<template>
  <div class="api-example">
    <van-cell-group inset title="API 调用示例">
      <van-cell
        title="用户登录"
        is-link
        @click="handleLogin"
      />
      <van-cell
        title="设置 PIN 码"
        is-link
        @click="handleSetPin"
      />
      <van-cell
        title="验证 PIN 码"
        is-link
        @click="handleVerifyPin"
      />
      <van-cell
        title="创建账务流水"
        is-link
        @click="handleCreateTransaction"
      />
      <van-cell
        title="获取账务流水列表"
        is-link
        @click="handleGetTransactions"
      />
      <van-cell
        title="更新用户资料"
        is-link
        @click="handleUpdateProfile"
      />
    </van-cell-group>

    <div v-if="responseData" class="response-display">
      <van-cell-group inset title="响应数据">
        <van-cell title="状态">
          <template #value>
            <van-tag :type="responseSuccess ? 'success' : 'danger'">
              {{ responseSuccess ? '成功' : '失败' }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="数据">
          <template #value>
            <pre class="json-display">{{ JSON.stringify(responseData, null, 2) }}</pre>
          </template>
        </van-cell>
      </van-cell-group>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { authApi, securityApi, accountApi, userApi } from '@/utils/index'

const responseData = ref(null)
const responseSuccess = ref(false)

// 示例 1：用户登录
const handleLogin = async () => {
  try {
    const res = await authApi.login({
      email: 'user@example.com',
      password: '123456'
    })
    console.log('登录响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('登录失败:', error)
    responseSuccess.value = false
  }
}

// 示例 2：设置 PIN 码
const handleSetPin = async () => {
  try {
    const res = await securityApi.setPin({
      pin: '123456'
    })
    console.log('设置 PIN 响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('设置 PIN 失败:', error)
    responseSuccess.value = false
  }
}

// 示例 3：验证 PIN 码
const handleVerifyPin = async () => {
  try {
    const res = await securityApi.verifyPin({
      pin: '123456'
    })
    console.log('验证 PIN 响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('验证 PIN 失败:', error)
    responseSuccess.value = false
  }
}

// 示例 4：创建账务流水（需要 PIN 验证）
const handleCreateTransaction = async () => {
  try {
    const res = await accountApi.createTransaction({
      amount: 100.00,
      type: 'expense',
      categoryId: 1,
      description: '午餐',
      date: '2024-03-26'
    })
    console.log('创建账务响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('创建账务失败:', error)
    responseSuccess.value = false
  }
}

// 示例 5：获取账务流水列表（需要 PIN 验证）
const handleGetTransactions = async () => {
  try {
    const res = await accountApi.getTransactions({
      page: 1,
      pageSize: 10
    })
    console.log('获取账务列表响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('获取账务列表失败:', error)
    responseSuccess.value = false
  }
}

// 示例 6：更新用户资料
const handleUpdateProfile = async () => {
  try {
    const res = await userApi.updateProfile({
      username: 'newname'
    })
    console.log('更新用户资料响应:', res)
    responseData.value = res
    responseSuccess.value = true
  } catch (error) {
    console.error('更新用户资料失败:', error)
    responseSuccess.value = false
  }
}
</script>

<style scoped>
.api-example {
  padding: 16px;
}

.response-display {
  margin-top: 20px;
}

.json-display {
  margin: 0;
  max-height: 300px;
  overflow: auto;
  font-size: 12px;
  color: #323233;
  background: #f7f8fa;
  padding: 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>