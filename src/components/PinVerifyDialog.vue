<template>
  <van-overlay :show="visible" z-index="9999" @click="onOverlayClick">
    <div class="pin-verify-wrapper" @click.stop>
      <div class="pin-verify-card">
        <div class="pin-title">安全验证</div>
        <div class="pin-desc">请输入 PIN 码以继续操作</div>

        <div class="pin-input-area">
          <van-password-input
            :value="pinValue"
            :length="6"
            :focused="showKeyboard"
            :gutter="10"
            @focus="showKeyboard = true"
          />
        </div>

        <div v-if="errorMessage" class="pin-error">{{ errorMessage }}</div>

        <div class="pin-actions">
          <van-button size="small" plain @click="onCancel">取消</van-button>
          <!-- <van-button size="small" type="primary" :disabled="pinValue.length < 6" @click="onConfirm">
            确认
          </van-button> -->
        </div>
      </div>
    </div>
  </van-overlay>

  <!-- 安全键盘 -->
  <div
    v-if="showKeyboard"
    class="pin-keyboard-overlay"
    @click="showKeyboard = false"
  >
    <SafeKeyboard @input="handleKeyInput" @confirm="showKeyboard = false" />
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { submitPin, cancelPinVerify } from "@/utils/request/pin";
import SafeKeyboard from "@/components/KeyBoard/index.vue";

const props = defineProps({
  onSuccess: {
    type: Function,
    default: null
  }
});

const visible = ref(false);
const pinValue = ref("");
const errorMessage = ref("");
const showKeyboard = ref(false);

function show() {
  visible.value = true;
  pinValue.value = "";
  errorMessage.value = "";
  setTimeout(() => {
    showKeyboard.value = true;
  }, 300);
}

function hide() {
  visible.value = false;
  showKeyboard.value = false;
  pinValue.value = "";
  errorMessage.value = "";
}

function setError(msg) {
  errorMessage.value = msg;
  pinValue.value = "";
}

function onCancel() {
  hide();
  cancelPinVerify();
}

function onOverlayClick() {
  // 不允许点击遮罩关闭
}

function onConfirm() {
  if (pinValue.value.length !== 6) return;
  errorMessage.value = "";
  
  // 调用 submitPin 处理验证逻辑，包括页面刷新等后续操作
  submitPin(pinValue.value)
}

function handleKeyInput(val) {
  errorMessage.value = "";
  if (val === "del") {
    pinValue.value = pinValue.value.slice(0, -1);
  } else if (pinValue.value.length < 6) {
    pinValue.value += val;
  }

  // 输入满6位自动提交
  if (pinValue.value.length === 6) {
    setTimeout(() => {
      onConfirm();
    }, 150);
  }
}

// 暴露方法给 pin.js 管理
defineExpose({ show, hide, setError });
</script>

<style scoped>
.pin-verify-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.pin-verify-card {
  width: 320px;
  background: #fff;
  border-radius: 16px;
  padding: 28px 24px 20px;
  text-align: center;
}

.pin-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 8px;
}

.pin-desc {
  font-size: 14px;
  color: #969799;
  margin-bottom: 24px;
}

.pin-input-area {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

/* 1. 选中每一个 PIN 码的格子 */
:deep(.van-password-input__item) {
  /* 基础边框 */
  border: 1px solid #acabab !important; /* Vant 的标准灰色边框色 */
  background-color: #ffffff; /* 强制白色背景，防止看不见 */
  border-radius: 6px; /* 让格子稍微圆润一点 */
  transition: all 0.2s; /* 增加过渡动画，更好看 */
  flex: 1; /* 均匀分配空间 */
  width: 35px;
  height: 50px; /* 显式给个高度，确保是正方形或长方形 */
}

/* 2. 选中“聚焦”状态下的格子（当前正在输入的那个格子） */
:deep(.van-password-input__item--focus) {
  /* 聚焦时改变边框颜色，提示用户 */
  border-color: var(--app-primary, #07c160) !important; /* 使用你的主题色 */
  /* 增加一个淡淡的呼吸灯阴影效果 (可选) */
  box-shadow: 0 0 8px rgba(7, 193, 96, 0.2);
}

/* 3. 选中格子内部的那个“闪烁光标” */
:deep(.van-password-input__cursor) {
  /* 确保光标颜色也是主题色 (可选) */
  background-color: var(--app-primary, #07c160) !important;
}

.pin-error {
  color: #ee0a24;
  font-size: 13px;
  margin-bottom: 16px;
  min-height: 18px;
}

.pin-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.pin-keyboard-overlay {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 10000;
  background: rgba(0, 0, 0, 0.5);
}
</style>
