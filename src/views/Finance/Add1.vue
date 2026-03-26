<template>
  <div class="page" @click="showKeyboard = false">
    <div class="amount-card" @click.stop="showKeyboard = true">
      <div class="label">请输入支付金额</div>
      <div class="value">
        ¥ <span>{{ displayAmount || '0.00' }}</span>
        <i v-if="showKeyboard" class="cursor"></i>
      </div>
    </div>

    <transition name="van-slide-up">
      <div v-if="showKeyboard" class="keyboard-fixed" @click.stop>
        <MobileSafeKeyboard
          :public-key="SERVER_PUBLIC_KEY"
          @input="onInput"
          @secure-payload="onSecureInput"
          @confirm="showKeyboard = false"
        />
      </div>
    </transition>

    <div class="submit-wrap">
      <van-button
        type="primary"
        block
        round
        :disabled="!displayAmount"
        @click="handleSubmit"
      >
        立即支付
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MobileSafeKeyboard from '@/components/KeyBoard/index.vue'

// 后端提供的 RSA 公钥
// 这是一个标准的 1024位 RSA 公钥模拟串
const SERVER_PUBLIC_KEY = `MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDeX8rsV3N4U7pC0kU6Iqm3q23v
h/ZInM5C29YF+WvS5B28K9C0R1S6iM3L9VpX9X7J7y6zW5v6I9g7l6v5v6I9g7l6
v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6
I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7
l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5
v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9g7l6v5v6I9QIDAQAB`
// 注意：实际使用时，jsencrypt 会自动处理头尾，如果不成功，
// 请尝试在首尾加上 -----BEGIN PUBLIC KEY----- 和 -----END PUBLIC KEY-----
const displayAmount = ref('')
const secureTokens = ref([]) // 存储加密后的数字队列
const showKeyboard = ref(false)

/**
 * 处理按键逻辑
 * @param {Number|String} val 键盘传回的值
 */
const onInput = (val) => {
  // 处理空按键（普通模式下的占位符）
  if (val === '') return

  // 处理删除逻辑
  if (val === 'del') {
    if (displayAmount.value.length > 0) {
      displayAmount.value = displayAmount.value.slice(0, -1)
      // 同步移除密文队列的最后一个
      secureTokens.value.pop()
    }
    return
  }

  // 处理小数点逻辑
  if (val === '.') {
    if (displayAmount.value.includes('.') || displayAmount.value === '') return
    displayAmount.value += '.'
    return
  }

  // 处理数字逻辑与精度控制
  if (typeof val === 'number') {
    const str = displayAmount.value
    // 限制两位小数
    if (str.includes('.') && str.split('.')[1].length >= 2) return
    // 限制最大金额长度（例如 9 位）
    if (str.replace('.', '').length >= 9) return

    displayAmount.value += val.toString()
  }
}

/**
 * 接收加密后的密文
 * 每点击一次有效数字，键盘会加密后通过此事件抛出
 */
const onSecureInput = (token) => {
  secureTokens.value.push(token)
  // console.log('当前密文队列长度:', secureTokens.value.length)
}

/**
 * 最终提交数据
 */
import { showSuccessToast, showFailToast } from 'vant'
const handleSubmit = () => {
  if (
    !displayAmount.value ||
    displayAmount.value === '0.00' ||
    Number(displayAmount.value) <= 0
  ) {
    showFailToast('请输入金额')
    return
  }

  // 业务逻辑：将 secureTokens 发给后端解密
  console.log('--- 提交数据 ---')
  console.log('金额回显:', displayAmount.value)
  console.log('加密队列:', secureTokens.value)

  showKeyboard.value = false
  showSuccessToast('加密指令已发出，请查看控制台输出！')
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.amount-card {
  margin: 0;
  padding: 40px 20px;
  background: #fff;
  text-align: center;
  border-bottom: 1px solid #eee;
}

.label {
  color: #999;
  font-size: 14px;
}

.value {
  font-size: 48px;
  font-weight: bold;
  color: #333;
  margin-top: 15px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.cursor {
  display: inline-block;
  width: 2px;
  height: 40px;
  background: #0080ff;
  margin-left: 4px;
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.keyboard-fixed {
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  z-index: 1000;
}

.submit-wrap {
  padding: 30px 20px;
}

/* Vant 动画适配 */
.van-slide-up-enter-active,
.van-slide-up-leave-active {
  transition: transform 0.3s ease-out;
}
.van-slide-up-enter-from,
.van-slide-up-leave-to {
  transform: translateY(100%);
}
</style>
