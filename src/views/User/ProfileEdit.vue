<template>
  <div class="page-profile-edit">
    <!-- 头像编辑 -->
    <div class="avatar-section">
      <div class="avatar-wrapper">
        <van-image
          round
          width="100"
          height="100"
          :src="userStore.avatar"
          class="avatar"
        />
        <div class="avatar-edit-btn" @click="handleEditAvatar">
          <van-icon name="photograph" size="20" />
        </div>
      </div>
      <div class="avatar-tip">点击头像更换</div>
    </div>

    <!-- 用户名显示（只读） -->
    <van-cell-group inset class="form-section">
      <van-field
        :model-value="userStore.username"
        label="用户名"
        readonly
        is-link
        @click="handleEditUsername"
      />
    </van-cell-group>

    <!-- 邮箱显示（只读） -->
    <van-cell-group inset class="form-section">
      <van-field
        :model-value="userStore.email"
        label="邮箱地址"
        readonly
        is-link
        @click="handleEditEmail"
      />
    </van-cell-group>

    <!-- 修改密码选项 -->
    <div class="action-section">
      <van-cell-group inset>
        <van-cell title="修改登录密码" is-link @click="handleUpdatePassword">
        </van-cell>
      </van-cell-group>
    </div>

    <!-- 用户名修改确认弹窗 -->
    <van-dialog
      v-model:show="showUsernameDialog"
      title="修改用户名"
      show-cancel-button
      confirm-button-text="确认修改"
      @confirm="handleUsernameSubmit"
    >
      <div class="dialog-content">
        <van-field
          v-model="usernameForm.new"
          label="新用户名"
          placeholder="请输入新用户名"
          :maxlength="20"
          clearable
        />
        <div class="username-rules">
          <strong>用户名要求:</strong>
          <ul>
            <li :class="usernameRules.validLength ? 'valid' : 'invalid'">
              ✔ 长度 3-20 位
            </li>
            <li :class="usernameRules.hasChinese ? 'valid' : 'invalid'">
              ✔ 支持中文
            </li>
            <li :class="usernameRules.hasEnglish ? 'valid' : 'invalid'">
              ✔ 支持英文
            </li>
            <li :class="usernameRules.validChars ? 'valid' : 'invalid'">
              ✔ 不支持特殊符号
            </li>
          </ul>
        </div>
      </div>
    </van-dialog>

    <!-- 邮箱修改确认弹窗 -->
    <van-dialog
      v-model:show="showEmailDialog"
      title="修改邮箱"
      show-cancel-button
      confirm-button-text="确认修改"
      @confirm="handleEmailVerify"
      :before-close="onBeforeEmailClose"
    >
      <div class="dialog-content">
        <van-field
          v-model="emailForm.new"
          label="新邮箱"
          type="email"
          placeholder="请输入新邮箱"
          :readonly="emailForm.isCodeSent"
          :clearable="!emailForm.isCodeSent"
        />
        <van-field
          v-model="emailForm.code"
          label="验证码"
          placeholder="请输入验证码"
          maxlength="6"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              :disabled="emailForm.countdown > 0"
              @click="handleSendEmailCode"
            >
              {{
                emailForm.countdown > 0
                  ? `${emailForm.countdown}s`
                  : "发送验证码"
              }}
            </van-button>
          </template>
        </van-field>
      </div>
    </van-dialog>

    <!-- 密码修改确认弹窗 -->
    <van-dialog
      v-model:show="showPasswordDialog"
      title="修改登录密码"
      show-cancel-button
      confirm-button-text="确认修改"
      :before-close="onBeforePasswordClose"
    >
      <div class="dialog-content">
        <van-form @submit="handlePasswordSubmit" autocomplete="on">
          <van-field
            v-model="passwordForm.old"
            label="原密码"
            type="password"
            name="old_password"
            autocomplete="current-password"
            placeholder="请输入原密码"
          />
          <van-field
            v-model="passwordForm.new"
            label="新密码"
            type="password"
            name="new_password"
            autocomplete="new-password"
            placeholder="请输入新密码"
            maxlength="15"
          />
          <van-field
            v-model="passwordForm.confirm"
            label="确认新密码"
            type="password"
            name="confirm_password"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            maxlength="15"
          />

          <van-field
            v-model="passwordForm.code"
            label="验证码"
            placeholder="请输入验证码"
            maxlength="6"
          >
            <template #button>
              <van-button
                size="small"
                type="primary"
                native-type="button"
                :disabled="passwordForm.countdown > 0"
                @click="handleSendPasswordCode"
              >
                {{
                  passwordForm.countdown > 0
                    ? `${passwordForm.countdown}s`
                    : "发送验证码"
                }}
              </van-button>
            </template>
          </van-field>
        </van-form>
      </div>
    </van-dialog>

    <!-- 头像编辑弹窗 -->
    <van-action-sheet
      v-model:show="showAvatarSheet"
      :actions="avatarActions"
      @select="handleAvatarSelect"
    />

    <!-- 头像 URL 输入弹窗 -->
    <van-dialog
      v-model:show="showAvatarUrlDialog"
      title="输入头像 URL"
      show-cancel-button
      confirm-button-text="确认"
      @confirm="handleAvatarUrlConfirm"
    >
      <div class="dialog-content">
        <van-field
          v-model="avatarUrlInput"
          type="url"
          placeholder="请输入图片链接"
          clearable
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { showToast, showSuccessToast } from "vant";
import { useUserStore } from "@/stores/user";
import { authApi } from "@/utils/api/auth";
import { useRouter } from "vue-router";

const userStore = useUserStore();
const router = useRouter();

// 弹窗控制
const showEmailDialog = ref(false);
const showPasswordDialog = ref(false);
const showAvatarSheet = ref(false);
const showAvatarUrlDialog = ref(false);
const showUsernameDialog = ref(false);
const avatarUrlInput = ref("");

// 临时表单
const emailForm = reactive({ new: "", code: "", countdown: 0 });
const usernameForm = reactive({ new: "" });
const passwordForm = reactive({
  old: "",
  new: "",
  confirm: "",
  code: "",
  countdown: 0,
});

// 头像菜单选项
const avatarActions = [
  { name: "输入图片 URL", value: "url" },
  { name: "取消", color: "#ee0a24", value: "cancel" },
];

// --- 用户名规则校验 (计算属性) ---
const usernameRules = computed(() => {
  const val = usernameForm.new;
  return {
    validLength: val.length >= 3 && val.length <= 20,
    hasChinese: /[\u4e00-\u9fa5]/.test(val),
    hasEnglish: /[a-zA-Z]/.test(val),
    validChars: /^[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(val),
  };
});

// --- 初始化 ---
onMounted(async () => {
  if (userStore.hasUserInfo) return;
  try {
    const res = await authApi.getUserinfo();
    userStore.setUserInfo(res.data);
  } catch (err) {
    console.error("初始化用户信息失败");
  }
});

// --- 1. 用户名修改逻辑 ---
const handleEditUsername = () => {
  usernameForm.new = userStore.username; // 初始值为当前用户名
  showUsernameDialog.value = true;
};

const handleUsernameSubmit = async () => {
  if (!usernameRules.value.validLength || !usernameRules.value.validChars) {
    showToast("请检查用户名规则");
    return false;
  }
  try {
    const res = await authApi.updateProfile({ username: usernameForm.new });
    if (res.data?.user) userStore.setUserInfo(res.data.user);
    showSuccessToast("用户名修改成功");
    return true;
  } catch (err) {
    return false;
  }
};

// --- 2. 邮箱修改逻辑 ---
const handleEditEmail = () => {
  emailForm.new = "";
  emailForm.code = "";
  showEmailDialog.value = true;
};

const handleSendEmailCode = async () => {
  if (!emailForm.new) return showToast("请输入新邮箱");
  try {
    await authApi.sendEmailCode({ email: emailForm.new, type: "email" });
    showToast("验证码已发送");
    emailForm.countdown = 60;
    const timer = setInterval(() => {
      emailForm.countdown--;
      if (emailForm.countdown <= 0) clearInterval(timer);
    }, 1000);
  } catch (err) {}
};

const handleEmailVerify = async () => {
  try {
    const res = await authApi.updateEmail({
      email: emailForm.new,
      code: emailForm.code,
    });
    if (res.data?.user) userStore.setUserInfo(res.data.user);
    showSuccessToast("邮箱修改成功");
    return true;
  } catch (err) {
    throw err;
  }
};

const onBeforeEmailClose = async (action) => {
  if (action === "cancel") return true;
  try {
    return await handleEmailVerify();
  } catch (err) {
    return false;
  }
};

// --- 3. 头像修改逻辑 ---
const handleEditAvatar = () => {
  showAvatarSheet.value = true;
};

const handleAvatarSelect = (action) => {
  if (action.value === "url") {
    showAvatarUrlDialog.value = true;
  }
  showAvatarSheet.value = false;
};

const handleAvatarUrlConfirm = async () => {
  const url = avatarUrlInput.value.trim();
  if (!url) return;
  try {
    const res = await authApi.updateProfile({ avatar: url });    
    if (res.data?.user) userStore.setUserInfo(res.data.user);
    showSuccessToast("头像已更新");
    avatarUrlInput.value = "";
  } catch (err) {}
};

// --- 4. 密码修改逻辑 ---
const handleUpdatePassword = () => {
  passwordForm.old = "";
  passwordForm.new = "";
  passwordForm.confirm = "";
  passwordForm.code = "";
  showPasswordDialog.value = true;
};

const handleSendPasswordCode = async () => {
  try {
    await authApi.sendEmailCode({ email: userStore.email, type: "pwd" });
    showToast("验证码已发送");
    passwordForm.countdown = 60;
    const timer = setInterval(() => {
      passwordForm.countdown--;
      if (passwordForm.countdown <= 0) clearInterval(timer);
    }, 1000);
  } catch (err) {}
};

const handlePasswordSubmit = async () => {
  if (!passwordForm.old || !passwordForm.new || !passwordForm.code) {
    showToast("请填写完整信息");
    throw new Error("incomplete");
  }
  if (passwordForm.new !== passwordForm.confirm) {
    showToast("新输入的两次密码不一致");
    throw new Error("mismatch");
  }

  try {
    await authApi.changePassword({
      newPassword: passwordForm.new,
      code: passwordForm.code,
      oldPassword: passwordForm.old,
    });

    showSuccessToast("修改成功，请重新登录");
    passwordForm.countdown = 0;
    localStorage.removeItem("finance_token");
    userStore.$reset();

    setTimeout(() => {
      window.location.href = "/";
    }, 1200);

    return true;
  } catch (err) {
    throw err;
  }
};

const onBeforePasswordClose = async (action) => {
  if (action === "cancel") return true;
  try {
    await handlePasswordSubmit();
    return true;
  } catch (err) {
    return false;
  }
};
</script>

<style scoped>
.page-profile-edit {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 30px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px 30px;
}

.avatar-wrapper {
  position: relative;
}

.avatar {
  border: 3px solid var(--theme-bg-secondary);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.avatar-edit-btn {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 32px;
  height: 32px;
  background: var(--theme-primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  border: 2px solid white;
  cursor: pointer;
}

.avatar-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.form-section {
  margin-bottom: 12px;
}

.action-section {
  margin-bottom: 20px;
}

.cell-icon {
  font-size: 18px;
  color: var(--theme-primary);
  margin-right: 8px;
}

.dialog-content {
  padding: 20px;
}

.password-rules {
  background: var(--theme-bg-tertiary);
  border-radius: 8px;
  padding: 12px;
  margin-top: 12px;
  font-size: 12px;
}

.password-rules strong {
  display: block;
  margin-bottom: 8px;
  color: var(--theme-text-primary);
  font-weight: 600;
}

.password-rules ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.password-rules li {
  margin: 6px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.password-rules li.valid {
  color: var(--theme-success);
  font-weight: 500;
}

.password-rules li.invalid {
  color: var(--theme-text-tertiary);
}

.username-rules {
  background: var(--theme-bg-tertiary);
  border-radius: 8px;
  padding: 12px;
  margin-top: 12px;
  font-size: 12px;
}

.username-rules strong {
  display: block;
  margin-bottom: 8px;
  color: var(--theme-text-primary);
  font-weight: 600;
}

.username-rules ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.username-rules li {
  margin: 6px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.username-rules li.valid {
  color: var(--theme-success);
  font-weight: 500;
}

.username-rules li.invalid {
  color: var(--theme-text-tertiary);
}
</style>
