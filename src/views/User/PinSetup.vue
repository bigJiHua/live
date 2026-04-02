<template>
  <div class="page-pin-setup">
    <div class="setup-content">
      <!-- 步骤指示器 -->
      <div class="step-indicator" v-if="mode !== 'verify'">
        <div
          class="step-item"
          :class="{ active: currentStep === 1, completed: currentStep > 1 }"
        >
          <div class="step-number">{{ getStepNumber(1) }}</div>
          <div class="step-text">{{ getStepText(1) }}</div>
        </div>
        <div class="step-line" :class="{ active: currentStep > 1 }"></div>
        <div
          class="step-item"
          :class="{ active: currentStep === 2, completed: currentStep > 2 }"
        >
          <div class="step-number">{{ getStepNumber(2) }}</div>
          <div class="step-text">{{ getStepText(2) }}</div>
        </div>
        <div
          v-if="mode === 'modify'"
          class="step-line"
          :class="{ active: currentStep > 2 }"
        ></div>
        <div
          v-if="mode === 'modify'"
          class="step-item"
          :class="{ active: currentStep === 3, completed: currentStep > 3 }"
        >
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
            :length="6"
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
          :length="6"
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
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showToast } from "vant";
import SafeKeyboard from "@/components/KeyBoard/index.vue";
import { securityApi } from "@/utils/api/security";

const router = useRouter();
const route = useRoute();

// 模式：new(新建), modify(修改)
const mode = ref(route.query.mode || "new");
const currentStep = ref(1);
const oldPin = ref("");
const firstPin = ref("");
const currentPin = ref("");
const showKeyboard = ref(true);
const errorMessage = ref("");

const pageTitle = computed(() => {
  if (mode.value === "new") return "设置 PIN 码";
  if (mode.value === "modify") return "修改 PIN 码";
  return "PIN 码";
});

// 检查 PIN 设置状态
const checkPinStatus = async () => {
  try {
    await securityApi.checkPin();
    // 200 = 已设置 PIN
    return true;
  } catch (err) {
    // 400 = 未设置 PIN
    return false;
  }
};

onMounted(async () => {
  // 检查 PIN 设置状态
  const hasPinSet = await checkPinStatus();

  if (mode.value === "new") {
    // 如果已设置 PIN，提示并返回
    if (hasPinSet) {
      showToast("您已设置过 PIN 码，请选择修改");
      setTimeout(() => {
        router.back();
      }, 1500);
      return;
    }
  } else if (mode.value === "modify") {
    // 如果未设置 PIN，跳转到设置页面
    if (!hasPinSet) {
      showToast("您还未设置 PIN 码，正在跳转到设置页面...");
      setTimeout(() => {
        router.replace({
          path: "/user/pin-setup",
          query: { mode: "new" },
        });
      }, 1500);
      return;
    }
  }

  // 自动显示键盘
  setTimeout(() => {
    showKeyboard.value = true;
  }, 300);
});

const getStepNumber = (step) => {
  if (mode.value === "new") {
    return step;
  } else {
    return step;
  }
};

const getStepText = (step) => {
  if (mode.value === "new") {
    return step === 1 ? "输入新 PIN 码" : "确认 PIN 码";
  } else {
    if (step === 1) return "验证旧 PIN";
    if (step === 2) return "输入新 PIN";
    return "确认新 PIN";
  }
};

const getTipText = () => {
  if (mode.value === "new") {
    if (currentStep.value === 1) return "请设置 6 位数字 PIN 码";
    return "请再次输入以确认";
  } else {
    if (currentStep.value === 1) return "请输入当前的 PIN 码";
    if (currentStep.value === 2) return "请设置新的 6 位数字 PIN 码";
    return "请再次输入新 PIN 码以确认";
  }
};

// 校验是否为6位相同数字
const isRepeatedPin = (pin) => {
  if (pin.length !== 6) return false;
  return /^(\d)\1{5}$/.test(pin);
};

const handleKeyInput = (val) => {
  errorMessage.value = "";

  if (val === "del") {
    currentPin.value = currentPin.value.slice(0, -1);
  } else if (currentPin.value.length < 6) {
    currentPin.value += val;
  }

  // 检查是否输入完成
  if (currentPin.value.length === 6) {
    setTimeout(() => {
      handlePinComplete();
    }, 100);
  }
};

const handlePinComplete = () => {
  // 禁止输入6位相同的数字
  if (isRepeatedPin(currentPin.value)) {
    errorMessage.value = "PIN 码不能为6位相同的数字";
    currentPin.value = "";
    return;
  }

  if (mode.value === "new") {
    // 新建模式
    handleNewMode();
  } else {
    // 修改模式
    handleModifyMode();
  }
};

const handleNewMode = async () => {
  if (currentStep.value === 1) {
    // 第一步：第一次输入
    firstPin.value = currentPin.value;
    currentStep.value = 2;
    currentPin.value = "";
    errorMessage.value = "";
  } else {
    // 第二步：确认输入
    if (currentPin.value === firstPin.value) {
      // 调用后端 API 设置 PIN
      try {
        await securityApi.setPin({ pin: firstPin.value });
        // PIN 存储在后端，本地只标记启用状态
        localStorage.setItem("pin_enabled", "true");
        showToast("PIN 码设置成功");
        setTimeout(() => {
          router.back();
        }, 1500);
      } catch (err) {
        errorMessage.value = err.response?.data?.message || "设置失败，请重试";
        currentStep.value = 1;
        firstPin.value = "";
        currentPin.value = "";
      }
    } else {
      // 两次输入不一致
      errorMessage.value = "两次输入的 PIN 码不一致，请重新设置";
      currentStep.value = 1;
      firstPin.value = "";
      currentPin.value = "";
    }
  }
};

const handleModifyMode = async () => {
  if (currentStep.value === 1) {
    // 验证旧 PIN - 调用后端 API
    try {
      await securityApi.verifyPin({ pin: currentPin.value });
      oldPin.value = currentPin.value;
      currentStep.value = 2;
      currentPin.value = "";
      errorMessage.value = "";
    } catch (err) {
      errorMessage.value = err.response?.data?.message || "PIN 码错误，请重新输入";
      currentPin.value = "";
    }
  } else if (currentStep.value === 2) {
    // 输入新 PIN
    firstPin.value = currentPin.value;
    currentStep.value = 3;
    currentPin.value = "";
    errorMessage.value = "";
  } else {
    // 确认新 PIN - 调用后端 API 修改
    if (currentPin.value === firstPin.value) {
      try {
        await securityApi.changePin({
          oldPin: oldPin.value,
          newPin: currentPin.value,
        });
        showToast("PIN 码修改成功");
        setTimeout(() => {
          router.back();
        }, 1500);
      } catch (err) {
        errorMessage.value = err.response?.data?.message || "修改失败，请重试";
        currentStep.value = 2;
        firstPin.value = "";
        currentPin.value = "";
      }
    } else {
      // 两次输入不一致
      errorMessage.value = "两次输入的 PIN 码不一致，请重新设置";
      currentStep.value = 2;
      firstPin.value = "";
      currentPin.value = "";
    }
  }
};
</script>

<style scoped>
.page-pin-setup {
  height: 60vh;
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
  padding: 0 20px;
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
  max-width: 95vw;
  display: flex;
  justify-content: center;
  margin: 40px 0;
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

/* 提示信息 */
.tip-text {
  width: 100%;
  margin: 20px 0;
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
