<template>
  <div class="page-pin-setup">
    <div class="setup-content">
      <!-- 步骤指示器 -->
      <div class="step-indicator" v-if="mode !== 'verify'">
        <div class="step-item" :class="{ active: currentStep === 1, completed: currentStep > 1 }">
          <div class="step-number">{{ getStepNumber(1) }}</div>
          <div class="step-text">{{ getStepText(1) }}</div>
        </div>
        <div class="step-line" :class="{ active: currentStep > 1 }"></div>
        <div class="step-item" :class="{ active: currentStep === 2, completed: currentStep > 2 }">
          <div class="step-number">{{ getStepNumber(2) }}</div>
          <div class="step-text">{{ getStepText(2) }}</div>
        </div>
        <div v-if="mode === 'modify'" class="step-line" :class="{ active: currentStep > 2 }"></div>
        <div v-if="mode === 'modify'" class="step-item" :class="{ active: currentStep === 3, completed: currentStep > 3 }">
          <div class="step-number">{{ getStepNumber(3) }}</div>
          <div class="step-text">{{ getStepText(3) }}</div>
        </div>
      </div>

      <!-- 验证旧 PIN -->
      <div v-if="mode === 'modify' && currentStep === 1" class="verify-section">
        <div class="section-title">验证身份</div>
        <div class="tip-text">请输入当前的 PIN 码以确认身份</div>
        <div class="pin-display">
          <van-password-input
            :value="currentPin"
            :length="4"
            :focused="showKeyboard"
            :gutter="15"
            @focus="showKeyboard = true"
          />
        </div>
      </div>

      <!-- 设置新 PIN -->
      <div v-else class="pin-display">
        <van-password-input
          :value="currentPin"
          :length="4"
          :focused="showKeyboard"
          :gutter="15"
          @focus="showKeyboard = true"
        />
      </div>

      <!-- 提示信息 -->
      <div class="tip-text">
        <van-notice-bar
          v-if="errorMessage"
          type="warning"
          :text="errorMessage"
          left-icon="warning-o"
        />
        <div v-else class="normal-tip">
          {{ getTipText() }}
        </div>
      </div>
    </div>

    <!-- 安全键盘 -->
    <div class="keyboard-overlay" @click="showKeyboard = false">
      <SafeKeyboard
        v-if="showKeyboard"
        @input="handleKeyInput"
        @confirm="showKeyboard = false"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import SafeKeyboard from '@/components/Keyboard/index.vue'

const router = useRouter()
const route = useRoute()

// 模式：new(新建), modify(修改)
const mode = ref(route.query.mode || 'new')
const currentStep = ref(1)
const oldPin = ref('')
const firstPin = ref('')
const currentPin = ref('')
const showKeyboard = ref(true)
const errorMessage = ref('')

const pageTitle = computed(() => {
  if (mode.value === 'new') return '设置 PIN 码'
  if (mode.value === 'modify') return '修改 PIN 码'
  return 'PIN 码'
})

onMounted(() => {
  // 自动显示键盘
  setTimeout(() => {
    showKeyboard.value = true
  }, 300)
})

const getStepNumber = (step) => {
  if (mode.value === 'new') {
    return step
  } else {
    return step
  }
}

const getStepText = (step) => {
  if (mode.value === 'new') {
    return step === 1 ? '输入新 PIN 码' : '确认 PIN 码'
  } else {
    if (step === 1) return '验证旧 PIN'
    if (step === 2) return '输入新 PIN'
    return '确认新 PIN'
  }
}

const getTipText = () => {
  if (mode.value === 'new') {
    if (currentStep.value === 1) return '请设置 4 位数字 PIN 码'
    return '请再次输入以确认'
  } else {
    if (currentStep.value === 1) return '请输入当前的 PIN 码'
    if (currentStep.value === 2) return '请设置新的 4 位数字 PIN 码'
    return '请再次输入新 PIN 码以确认'
  }
}

const handleKeyInput = (val) => {
  errorMessage.value = ''

  if (val === 'del') {
    currentPin.value = currentPin.value.slice(0, -1)
  } else if (currentPin.value.length < 4) {
    currentPin.value += val
  }

  // 检查是否输入完成
  if (currentPin.value.length === 4) {
    setTimeout(() => {
      handlePinComplete()
    }, 100)
  }
}

const handlePinComplete = () => {
  if (mode.value === 'new') {
    // 新建模式
    handleNewMode()
  } else {
    // 修改模式
    handleModifyMode()
  }
}

const handleNewMode = () => {
  if (currentStep.value === 1) {
    // 第一步：第一次输入
    firstPin.value = currentPin.value
    currentStep.value = 2
    currentPin.value = ''
    errorMessage.value = ''
  } else {
    // 第二步：确认输入
    if (currentPin.value === firstPin.value) {
      // 保存成功
      localStorage.setItem('user_pin', firstPin.value)
      localStorage.setItem('pin_enabled', 'true')
      showToast('PIN 码设置成功')
      setTimeout(() => {
        router.back()
      }, 1500)
    } else {
      // 两次输入不一致
      errorMessage.value = '两次输入的 PIN 码不一致，请重新设置'
      currentStep.value = 1
      firstPin.value = ''
      currentPin.value = ''
    }
  }
}

const handleModifyMode = () => {
  if (currentStep.value === 1) {
    // 验证旧 PIN
    const savedPin = localStorage.getItem('user_pin')
    if (currentPin.value === savedPin) {
      oldPin.value = currentPin.value
      currentStep.value = 2
      currentPin.value = ''
      errorMessage.value = ''
    } else {
      errorMessage.value = 'PIN 码错误，请重新输入'
      currentPin.value = ''
    }
  } else if (currentStep.value === 2) {
    // 输入新 PIN
    firstPin.value = currentPin.value
    currentStep.value = 3
    currentPin.value = ''
    errorMessage.value = ''
  } else {
    // 确认新 PIN
    if (currentPin.value === firstPin.value) {
      // 保存成功
      localStorage.setItem('user_pin', currentPin.value)
      showToast('PIN 码修改成功')
      setTimeout(() => {
        router.back()
      }, 1500)
    } else {
      // 两次输入不一致
      errorMessage.value = '两次输入的 PIN 码不一致，请重新设置'
      currentStep.value = 2
      firstPin.value = ''
      currentPin.value = ''
    }
  }
}
</script>

<style scoped>
.page-pin-setup {
  min-height: 100vh;
  background: #f7f8fa;
  display: flex;
  flex-direction: column;
}

.setup-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  padding-top: 100px; /* 避开 MainLayout 导航栏 */
}

/* 步骤指示器 */
.step-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 60px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-number {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e0e0e0;
  color: #999;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
  transition: all 0.3s;
}

.step-item.active .step-number {
  background: var(--app-primary, #07c160);
  color: white;
  box-shadow: 0 4px 12px rgba(7, 193, 96, 0.3);
}

.step-item.completed .step-number {
  background: #07c160;
  color: white;
}

.step-text {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}

.step-item.active .step-text {
  color: var(--app-primary, #07c160);
  font-weight: 500;
}

.step-line {
  width: 40px;
  height: 2px;
  background: #e0e0e0;
  transition: all 0.3s;
}

.step-line.active {
  background: var(--app-primary, #07c160);
}

/* 验证区域 */
.verify-section {
  width: 100%;
  text-align: center;
}

.verify-section .section-title {
  font-size: 18px;
  font-weight: bold;
  color: #323233;
  margin-bottom: 12px;
}

/* PIN 显示区域 */
.pin-display {
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 0 20px;
  margin-bottom: 40px;
}

/* 提示信息 */
.tip-text {
  width: 100%;
  padding: 0 20px;
}

.normal-tip {
  text-align: center;
  font-size: 14px;
  color: #969799;
  line-height: 1.6;
}

/* 键盘遮罩 */
.keyboard-overlay {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 9998;
  background: rgba(0, 0, 0, 0.5);
}
</style>