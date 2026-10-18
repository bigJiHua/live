<template>
  <van-overlay :show="visible" z-index="9999" @click="onOverlayClick">
    <div class="pin-verify-wrapper" @click.stop>
      <div class="pin-verify-card">
        <div class="pin-title">安全验证</div>
        <div class="pin-desc">请输入 PIN 码以继续操作</div>

        <!-- 点击输入区重新唤出键盘：Vant 的 password-input 只在 touchstart 派发 focus，
             鼠标点击（桌面/调试）收不到，且键盘遮罩可能盖住输入区，故额外绑 click 兜底 -->
        <div class="pin-input-area" @click="showKeyboard = true">
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
          <app-button size="normal" plain class="pin-cancel-btn" @click="onCancel">
            取消
          </app-button>
          <!-- <app-button size="small" type="primary" :disabled="pinValue.length < 6" @click="onConfirm">
            确认
          </app-button> -->
        </div>
      </div>
    </div>

    <!-- 安全键盘：放在 van-overlay 内部、与卡片同一层叠上下文，z-index 低于卡片，
         这样键盘遮罩不会盖住输入区/取消按钮（此前它是 overlay 的兄弟节点，
         z-index 10000 > overlay 9999，短屏上会把卡片下缘整个吃掉，点不到输入框和取消键） -->
    <div
      v-if="showKeyboard"
      class="pin-keyboard-overlay"
      @click="showKeyboard = false"
    >
      <SafeKeyboard
        :public-key="publicKey"
        :secure-only="true"
        @secure-payload="handleSecurePayload"
        @input="handleKeyInput"
        @confirm="showKeyboard = false"
      />
    </div>
  </van-overlay>
</template>

<script setup>
import { ref, watch } from "vue";
import { submitPin, cancelPinVerify } from "@/utils/request/pin";
import { getRsaPublicKey } from "@/utils/request/handshake";
import { getClientContext } from "@/utils/request/client";
import SafeKeyboard from "@/components/KeyBoard/index.vue";

const props = defineProps({
  onSuccess: {
    type: Function,
    default: null
  }
});

const visible = ref(false);
const pinValue = ref("");
// secureOnly 输入：PIN 以 RSA 密文字符数组存在，明文不落 ref/事件
const pinEncrypted = ref([]);
const publicKey = ref("");
const errorMessage = ref("");
const showKeyboard = ref(false);

async function ensurePublicKey() {
  if (publicKey.value) return;
  try {
    const deviceData = await getClientContext();
    publicKey.value = (await getRsaPublicKey(deviceData)) || "";
  } catch (e) {
    console.warn("[PinVerifyDialog] 获取 RSA 公钥失败，PIN 键盘将降级为普通模式", e);
  }
}

function show() {
  visible.value = true;
  pinValue.value = "";
  pinEncrypted.value = [];
  errorMessage.value = "";
  setTimeout(() => {
    showKeyboard.value = true;
  }, 300);
}

function hide() {
  visible.value = false;
  showKeyboard.value = false;
  pinValue.value = "";
  pinEncrypted.value = [];
  errorMessage.value = "";
}

function setError(msg) {
  errorMessage.value = msg;
  pinValue.value = "";
  pinEncrypted.value = [];
}

function onCancel() {
  hide();
  cancelPinVerify();
}

function onOverlayClick() {
  // 不允许点击遮罩关闭
}

function onConfirm() {
  const len = pinEncrypted.value.length || pinValue.value.length;
  if (len !== 6) return;
  errorMessage.value = "";

  // secureOnly 输入提交 RSA 密文数组，后端私钥解密；无密文时回退明文（普通模式/降级）
  submitPin(
    pinEncrypted.value.length
      ? { pin: pinEncrypted.value }
      : { pin: pinValue.value }
  );
}

// secureOnly：只累加密文数组，不依赖明文
function handleSecurePayload(payload) {
  if (!payload) return;
  if (payload.type === "char") {
    if (payload.encrypted) {
      pinEncrypted.value.push(payload.encrypted);
      pinValue.value += "*"; // 占位，供长度判断
    }
  } else if (payload.type === "del") {
    pinEncrypted.value.pop();
    pinValue.value = pinValue.value.slice(0, -1);
  }
  // 输入满6位自动提交
  if (pinEncrypted.value.length === 6) {
    setTimeout(() => {
      onConfirm();
    }, 150);
  }
}

// 降级/普通模式明文输入
function handleKeyInput(val) {
  errorMessage.value = "";
  if (val === "del") {
    pinValue.value = pinValue.value.slice(0, -1);
    pinEncrypted.value.pop();
  } else if (pinValue.value.length < 6) {
    pinValue.value += val;
  }

  // 输入满6位自动提交
  if (pinValue.value.length === 6 && !pinEncrypted.value.length) {
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
  /* 高于 .pin-keyboard-overlay(10000)：键盘弹出时卡片仍在最上层，输入区/取消按钮始终可点 */
  position: relative;
  z-index: 10001;
  /* 它是满屏容器，若可点击会整片挡住下方键盘，故整层禁用、只在卡片上恢复 */
  pointer-events: none;
}

.pin-verify-card {
  pointer-events: auto;
  width: 320px;
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  padding: 28px 24px 20px;
  text-align: center;
}

.pin-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 8px;
}

.pin-desc {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  margin-bottom: 24px;
}

.pin-input-area {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

/* 1. 选中每一个 PIN 码的格子 */
:deep(.van-password-input__item) {
  /* 基础边框：跟随主题（原为写死 #acabab，深色模式下边框不跟随） */
  border: 1px solid var(--theme-border) !important;
  /* 背景跟随主题卡片色（原声明 `var(--theme-bg-secondary)fff` 是无效值，整条被浏览器丢弃，
     深色模式下会退回 Vant 默认的 --van-background-2 白底） */
  background-color: var(--theme-bg-secondary) !important;
  border-radius: 6px; /* 让格子稍微圆润一点 */
  transition: all 0.2s; /* 增加过渡动画，更好看 */
  flex: 1; /* 均匀分配空间 */
  width: 35px;
  height: 50px; /* 显式给个高度，确保是正方形或长方形 */
}

/* 2. 选中“聚焦”状态下的格子（当前正在输入的那个格子） */
:deep(.van-password-input__item--focus) {
  /* 聚焦时改变边框颜色，提示用户 */
  border-color: var(--app-primary) !important; /* 使用你的主题色 */
  /* 增加一个淡淡的呼吸灯阴影效果 (可选) */
  box-shadow: 0 0 8px var(--theme-shadow-color, rgba(7, 193, 96, 0.2));
}

/* 3. 选中格子内部的那个“闪烁光标” */
:deep(.van-password-input__cursor) {
  /* 确保光标颜色也是主题色 (可选) */
  background-color: var(--app-primary) !important;
}

.pin-error {
  color: var(--van-danger-color, #ee0a24);
  font-size: 13px;
  margin-bottom: 16px;
  min-height: 18px;
}

.pin-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 取消按钮：原来用 small（高 34 / 字号 13）偏小不好点，加宽到易点的尺寸 */
.pin-cancel-btn {
  min-width: 132px;
  height: 44px;
  font-size: 15px;
}

.pin-keyboard-overlay {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  /* 低于 .pin-verify-wrapper(10001)：与卡片同层叠上下文内比较，卡片永远在键盘之上 */
  z-index: 10000;
  background: rgba(0, 0, 0, 0.5);
}
</style>
