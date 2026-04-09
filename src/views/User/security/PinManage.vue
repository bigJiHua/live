<template>
  <div class="page-pin-manage">
    <div class="manage-content">
      <div class="status-card app-card">
        <div class="status-icon">
          <van-icon
            name="shield-o"
            size="48"
            :color="hasPinSet ? '#07c160' : '#FC9000'"
          />
        </div>
        <div class="status-info">
          <div class="status-title">
            {{ hasPinSet ? "PIN 码已设置" : "未设置 PIN 码" }}
          </div>
          <div class="status-desc">
            {{
              hasPinSet
                ? "您的账户已启用 PIN 码保护"
                : "设置 PIN 码可以更好地保护您的账户安全"
            }}
          </div>
        </div>
        <div class="status-badge" :class="{ enabled: pinEnabled }">
          {{ pinEnabled ? "已启用" : "已禁用" }}
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
            @click="showDisableDialog = true"
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

    <!-- 关闭 PIN 验证弹窗 -->
    <van-dialog
      v-model:show="showDisableDialog"
      title="关闭 PIN 码"
      show-cancel-button
      confirm-button-text="确认关闭"
      :before-close="onDisableDialogClose"
    >
      <div class="disable-dialog-content">
        <div class="dialog-tip">请输入当前 PIN 码以确认关闭</div>
        <van-password-input
          :value="disablePin"
          :length="6"
          :focused="showDisableKeyboard"
          :gutter="10"
          @focus="showDisableKeyboard = true"
        />
        <div v-if="disableError" class="error-text">{{ disableError }}</div>
      </div>
    </van-dialog>

    <!-- 安全键盘 -->
    <div
      v-if="showDisableKeyboard"
      class="keyboard-overlay"
      @click="showDisableKeyboard = false"
    >
      <SafeKeyboard
        @input="handleDisableKeyInput"
        @confirm="showDisableKeyboard = false"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { securityApi } from "@/utils/api/security";
import SafeKeyboard from "@/components/Keyboard/index.vue";

const router = useRouter();

// PIN 状态由后端管理
const hasPinSet = ref(false);
const pinEnabled = ref(false);

// 关闭 PIN 相关状态
const showDisableDialog = ref(false);
const showDisableKeyboard = ref(false);
const disablePin = ref("");
const disableError = ref("");

// 检查 PIN 设置状态
const checkPinStatus = async () => {
  try {
    await securityApi.checkPin();
    // 200 = 已设置 PIN
    hasPinSet.value = true;
    pinEnabled.value = true;
  } catch (err) {
    // 400 = 未设置 PIN
    hasPinSet.value = false;
    pinEnabled.value = false;
  }
  // 同步本地状态
  localStorage.setItem("pin_enabled", hasPinSet.value ? "true" : "false");
};

onMounted(() => {
  checkPinStatus();
});

// 弹窗打开时自动显示键盘
watch(showDisableDialog, (val) => {
  if (val) {
    setTimeout(() => {
      showDisableKeyboard.value = true;
    }, 300);
  } else {
    showDisableKeyboard.value = false;
  }
});

const goToSetup = (mode) => {
  if (mode === "new") {
    // 首次设置
    router.push({
      path: "/user/pin-setup",
      query: { mode: "new" },
    });
  } else {
    // 修改 PIN
    router.push({
      path: "/user/pin-setup",
      query: { mode: "modify" },
    });
  }
};

// 处理关闭 PIN 键盘输入
const handleDisableKeyInput = (val) => {
  disableError.value = "";
  if (val === "del") {
    disablePin.value = disablePin.value.slice(0, -1);
  } else if (disablePin.value.length < 6) {
    disablePin.value += val;
  }
};

// 关闭 PIN 弹窗确认
const onDisableDialogClose = async (action) => {
  // 关闭键盘
  showDisableKeyboard.value = false;

  if (action === "cancel") {
    disablePin.value = "";
    disableError.value = "";
    return true;
  }

  if (disablePin.value.length !== 6) {
    disableError.value = "请输入完整的 PIN 码";
    return false;
  }

  try {
    // 调用修改 PIN 接口，newPin 传 000000 表示关闭
    await securityApi.changePin({
      oldPin: disablePin.value,
      newPin: "000000",
    });
    // 关闭成功，更新本地状态
    localStorage.setItem("pin_enabled", "false");
    checkPinStatus();
    showToast("PIN 码已关闭");
    disablePin.value = "";
    disableError.value = "";
    return true;
  } catch (err) {
    disableError.value = err.response?.data?.message || "PIN 码错误";
    disablePin.value = "";
    return false;
  }
};
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

/* 关闭 PIN 弹窗 */
.disable-dialog-content {
  padding: 20px 16px;
  text-align: center;
}

.dialog-tip {
  font-size: 14px;
  color: #969799;
  margin-bottom: 16px;
}

.error-text {
  color: #ee0a24;
  font-size: 12px;
  margin-top: 8px;
}

.keyboard-overlay {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 9998;
  background: rgba(0, 0, 0, 0.5);
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
</style>
