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
      <app-field
        :model-value="userStore.username"
        label="用户名"
        readonly
        is-link
        @click="handleEditUsername"
      />
    </van-cell-group>

    <!-- 邮箱显示（只读） -->
    <van-cell-group inset class="form-section">
      <app-field
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
        <app-cell title="修改登录密码" is-link @click="handleUpdatePassword">
        </app-cell>
      </van-cell-group>
    </div>

    <!-- 用户名修改确认弹窗 -->
    <app-dialog
      v-model:show="showUsernameDialog"
      title="修改用户名"
      show-cancel-button
      confirm-button-text="确认修改"
      @confirm="handleUsernameSubmit"
    >
      <div class="dialog-content">
        <div class="field-wrap" :class="{ active: activeField === 'username' }">
          <app-field
            v-model="usernameForm.new"
            label="新用户名"
            placeholder="请输入新用户名"
            :maxlength="20"
            readonly
            @click="activateField('username')"
          />
          <span class="field-cursor" :style="cursorStyle('username')" />
          <span class="field-measure" ref="mUsername">{{ usernameForm.new || ' ' }}</span>
        </div>
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
    </app-dialog>

    <!-- 邮箱修改确认弹窗 -->
    <app-dialog
      v-model:show="showEmailDialog"
      title="修改邮箱"
      show-cancel-button
      confirm-button-text="确认修改"
      @confirm="handleEmailVerify"
      :before-close="onBeforeEmailClose"
    >
      <div class="dialog-content">
        <div class="field-wrap" :class="{ active: activeField === 'email' }">
          <app-field
            v-model="emailForm.new"
            label="新邮箱"
            type="email"
            placeholder="请输入新邮箱"
            readonly
            @click="activateField('email')"
          />
          <span class="field-cursor" :style="cursorStyle('email')" />
          <span class="field-measure" ref="mEmail">{{ emailForm.new || ' ' }}</span>
        </div>
        <div class="field-wrap" :class="{ active: activeField === 'emailCode' }">
          <app-field
            v-model="emailForm.code"
            label="验证码"
            placeholder="请输入验证码"
            maxlength="6"
            readonly
            @click="activateField('emailCode')"
          >
          <template #right-icon>
            <app-button
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
            </app-button>
          </template>
          </app-field>
          <span class="field-cursor" :style="cursorStyle('emailCode')" />
          <span class="field-measure" ref="mEmailCode">{{ emailForm.code || ' ' }}</span>
        </div>
      </div>
    </app-dialog>

    <!-- 密码修改确认弹窗 -->
    <app-dialog
      v-model:show="showPasswordDialog"
      title="修改登录密码"
      show-cancel-button
      confirm-button-text="确认修改"
      :before-close="onBeforePasswordClose"
      :close-on-click-overlay="!activeField"
      :style="passwordDialogPanelStyle"
    >
      <div class="dialog-content password-dialog-content" :style="passwordDialogContentStyle" ref="passwordDialogBody">
          <app-form @submit="handlePasswordSubmit" autocomplete="on">
          <div class="field-wrap" :class="{ active: activeField === 'pwdOld' }">
          <app-field
            v-model="passwordForm.old"
            label="原密码"
            type="password"
            name="old_password"
            autocomplete="current-password"
            placeholder="请输入原密码"
            readonly
            @click="activateField('pwdOld')"
          />
          <span class="field-cursor" :style="cursorStyle('pwdOld')" />
          <span class="field-measure" ref="mPwdOld">{{ '●'.repeat(passwordForm.old.length) || ' ' }}</span>
          </div>
          <div class="field-wrap" :class="{ active: activeField === 'pwdNew' }">
          <app-field
            v-model="passwordForm.new"
            label="新密码"
            type="password"
            name="new_password"
            autocomplete="new-password"
            placeholder="请输入新密码"
            maxlength="15"
            readonly
            @click="activateField('pwdNew')"
          />
          <span class="field-cursor" :style="cursorStyle('pwdNew')" />
          <span class="field-measure" ref="mPwdNew">{{ '●'.repeat(passwordForm.new.length) || ' ' }}</span>
          </div>
          <div class="field-wrap" :class="{ active: activeField === 'pwdConfirm' }">
          <app-field
            v-model="passwordForm.confirm"
            label="确认新密码"
            type="password"
            name="confirm_password"
            autocomplete="new-password"
            placeholder="请再次输入新密码"
            maxlength="15"
            readonly
            @click="activateField('pwdConfirm')"
          />
          <span class="field-cursor" :style="cursorStyle('pwdConfirm')" />
          <span class="field-measure" ref="mPwdConfirm">{{ '●'.repeat(passwordForm.confirm.length) || ' ' }}</span>
          </div>

          <div class="field-wrap" :class="{ active: activeField === 'pwdCode' }">
          <app-field
            v-model="passwordForm.code"
            label="验证码"
            placeholder="请输入验证码"
            maxlength="6"
            readonly
            @click="activateField('pwdCode')"
          >
            <template #right-icon>
              <app-button
                size="small"
                type="primary"
                native-type="button"
                :disabled="!canSendPasswordCode"
                @click="handleSendPasswordCode"
              >
                {{
                  passwordForm.countdown > 0
                    ? `${passwordForm.countdown}s`
                    : "发送验证码"
                }}
              </app-button>
            </template>
          </app-field>
          <span class="field-cursor" :style="cursorStyle('pwdCode')" />
          <span class="field-measure" ref="mPwdCode">{{ passwordForm.code || ' ' }}</span>
          </div>
        </app-form>
      </div>
    </app-dialog>

    <!-- 头像编辑弹窗 -->
    <van-action-sheet
      v-model:show="showAvatarSheet"
      :actions="avatarActions"
      @select="handleAvatarSelect"
    />

    <!-- 头像 URL 输入弹窗 -->
    <app-dialog
      v-model:show="showAvatarUrlDialog"
      title="输入头像 URL"
      show-cancel-button
      confirm-button-text="确认"
      @confirm="handleAvatarUrlConfirm"
    >
      <div class="dialog-content">
        <div class="field-wrap" :class="{ active: activeField === 'avatarUrl' }">
          <app-field
            v-model="avatarUrlInput"
            type="url"
            placeholder="请输入图片链接"
            readonly
            @click="activateField('avatarUrl')"
          />
          <span class="field-cursor" :style="cursorStyle('avatarUrl')" />
          <span class="field-measure" ref="mAvatarUrl">{{ avatarUrlInput || ' ' }}</span>
        </div>
      </div>
    </app-dialog>

    <!-- 内置 26 安全键盘弹层（覆盖所有弹窗内输入框） -->
    <!-- 收起方式：document 点击监听（点键盘外且非输入框处即收起），避免全屏遮罩挡住弹窗面板 -->
    <transition name="kb-up">
      <div v-if="activeField" class="kb-sheet" @click.stop ref="kbSheetRef">
        <SimpleKeyboard
          :model-value="activeValue"
          @update:model-value="onKeyInput"
          @login="closeKeyboard"
        />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from "vue";
import { showToast, showSuccessToast } from "vant";
import { useUserStore } from "@/stores/user";
import { authApi } from "@/utils/api/auth";
import { useRouter } from "vue-router";
import SimpleKeyboard from "@/components/KeyBoard/SimpleKeyboard.vue";

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

// 密码表单是否填完且两次新密码一致
const canSendPasswordCode = computed(() => {
  return (
    passwordForm.old.trim() &&
    passwordForm.new.trim() &&
    passwordForm.confirm.trim() &&
    passwordForm.new === passwordForm.confirm &&
    passwordForm.countdown <= 0
  );
});

// 头像菜单选项
const avatarActions = [
  { name: "输入图片 URL", value: "url" },
  { name: "取消", color: "var(--van-danger-color, #ee0a24)", value: "cancel" },
];

// --- 内置 26 安全键盘（仿 Login：所有弹窗内输入框 readonly，点击唤起安全键盘） ---
const activeField = ref(null); // 'username' | 'email' | 'emailCode' | 'pwdOld' | 'pwdNew' | 'pwdConfirm' | 'pwdCode' | 'avatarUrl'
const activeValue = computed(() => {
  switch (activeField.value) {
    case "username": return usernameForm.new;
    case "email": return emailForm.new;
    case "emailCode": return emailForm.code;
    case "pwdOld": return passwordForm.old;
    case "pwdNew": return passwordForm.new;
    case "pwdConfirm": return passwordForm.confirm;
    case "pwdCode": return passwordForm.code;
    case "avatarUrl": return avatarUrlInput.value;
    default: return "";
  }
});
const setActiveValue = (val) => {
  switch (activeField.value) {
    case "username": usernameForm.new = val; break;
    case "email": emailForm.new = val; break;
    case "emailCode": emailForm.code = val; break;
    case "pwdOld": passwordForm.old = val; break;
    case "pwdNew": passwordForm.new = val; break;
    case "pwdConfirm": passwordForm.confirm = val; break;
    case "pwdCode": passwordForm.code = val; break;
    case "avatarUrl": avatarUrlInput.value = val; break;
  }
};
const activateField = (field) => {
  activeField.value = field;
  nextTick(() => {
    syncCursorCaret(field);
    scrollFieldIntoView(field);
  });
};

const scrollFieldIntoView = (field) => {
  const refEl = measureRefs[field]?.value;
  if (!refEl) return;
  refEl.scrollIntoView({ behavior: "smooth", block: "center" });
  // 二次校正：键盘弹出后，确保激活字段位于键盘上方可视区
  nextTick(() => adjustActiveFieldAboveKeyboard(field));
};

// --- 键盘高度测量 + 弹窗避让 ---
// 密码表单字段过多，键盘弹出会遮挡下方字段。解决方案：
// 1) 动态测量键盘高度；
// 2) 弹出时给密码弹窗整体上移（避免被键盘盖住）；
// 3) 给弹窗内容加 padding-bottom，让用户可滚动查看被键盘挡住的字段；
// 4) 激活字段时再做一次校正滚动。
const kbSheetRef = ref(null);
const passwordDialogBody = ref(null);
const keyboardHeight = ref(0);
let kbResizeObserver = null;

const measureKeyboardHeight = () => {
  const el = kbSheetRef.value;
  if (!el) {
    keyboardHeight.value = 0;
    return;
  }
  keyboardHeight.value = el.getBoundingClientRect().height || 0;
};

const passwordDialogPanelStyle = computed(() => {
  // 仅在密码弹窗打开且键盘弹出时，整体上移避开键盘
  if (!showPasswordDialog.value || !activeField.value || !keyboardHeight.value) {
    return {};
  }
  // 弹窗原本居中（translate(-50%, -50%)）。键盘覆盖底部 viewport，
  // 只需将弹窗中心上移 ~kbH/2 + 少量留白，底部即可避开键盘，
  // 避免上移过多导致弹窗贴近屏幕顶部。
  const lift = Math.round(keyboardHeight.value * 0.5) + 8;
  return {
    transform: `translate(-50%, calc(-50% - ${lift}px))`,
    maxHeight: `calc(100vh - ${keyboardHeight.value + 32}px)`,
  };
});

const passwordDialogContentStyle = computed(() => {
  if (!showPasswordDialog.value || !activeField.value || !keyboardHeight.value) {
    return {};
  }
  return {
    paddingBottom: `${keyboardHeight.value + 12}px`,
  };
});

const adjustActiveFieldAboveKeyboard = (field) => {
  const refEl = measureRefs[field]?.value;
  const body = passwordDialogBody.value;
  const kbH = keyboardHeight.value;
  if (!refEl || !body || !kbH) return;
  // 找到最近的可滚动祖先（弹窗本体 .app-popup）
  const scrollable = body.closest(".app-popup") || body.parentElement;
  if (!scrollable) return;
  const fieldRect = refEl.getBoundingClientRect();
  const scrollRect = scrollable.getBoundingClientRect();
  // 字段底部低于键盘顶部，则向上滚动该距离
  const keyboardTop = window.innerHeight - kbH;
  const overlap = fieldRect.bottom - keyboardTop + 12;
  if (overlap > 0) {
    scrollable.scrollBy({ top: overlap, behavior: "smooth" });
  }
  // 若字段顶部在弹窗顶部之上，也做反向修正
  if (fieldRect.top < scrollRect.top + 8) {
    scrollable.scrollBy({
      top: fieldRect.top - (scrollRect.top + 8),
      behavior: "smooth",
    });
  }
};

// 光标跟随（照搬 Login 安全界面规则）
// 注意：安全界面——字段为 readonly，输入经由安全键盘，密码以 ● 圆点显示。
// 光标/测量元素均为只读模拟层，必须与实际 .app-field__input 文字精确对齐，
// 故动态读取输入框真实位置，避免硬编码 top 导致光标与文字错位。
const mUsername = ref(null);
const mEmail = ref(null);
const mEmailCode = ref(null);
const mPwdOld = ref(null);
const mPwdNew = ref(null);
const mPwdConfirm = ref(null);
const mPwdCode = ref(null);
const mAvatarUrl = ref(null);
const measureRefs = {
  username: mUsername,
  email: mEmail,
  emailCode: mEmailCode,
  pwdOld: mPwdOld,
  pwdNew: mPwdNew,
  pwdConfirm: mPwdConfirm,
  pwdCode: mPwdCode,
  avatarUrl: mAvatarUrl,
};
const cursorPos = reactive({
  username: { left: 8, top: 42 },
  email: { left: 8, top: 42 },
  emailCode: { left: 8, top: 42 },
  pwdOld: { left: 8, top: 42 },
  pwdNew: { left: 8, top: 42 },
  pwdConfirm: { left: 8, top: 42 },
  pwdCode: { left: 8, top: 42 },
  avatarUrl: { left: 8, top: 42 },
});

const cursorStyle = (field) => ({
  left: cursorPos[field].left + "px",
  top: cursorPos[field].top + "px",
  transform: "translateY(-50%)",
});

function syncCursorCaret(field) {
  const refEl = measureRefs[field]?.value;
  if (!refEl) return;
  const wrap = refEl.closest(".field-wrap");
  const input = wrap && wrap.querySelector(".app-field__input");
  if (!input) return;
  // 让隐藏测量元素与输入框文字完全重叠，确保测宽精确
  refEl.style.left = input.offsetLeft + "px";
  refEl.style.top = input.offsetTop + "px";
  cursorPos[field].left = input.offsetLeft + refEl.offsetWidth;
  // 以隐藏测量元素（与输入框文字同字体、已与 input 同位置）的垂直中心对齐光标
  cursorPos[field].top = refEl.offsetTop + refEl.offsetHeight / 2;
}

// 输入值变化时同步光标
watch(
  () => usernameForm.new,
  () => nextTick(() => syncCursorCaret("username"))
);
watch(
  () => emailForm.new,
  () => nextTick(() => syncCursorCaret("email"))
);
watch(
  () => emailForm.code,
  () => nextTick(() => syncCursorCaret("emailCode"))
);
watch(
  () => passwordForm.old,
  () => nextTick(() => syncCursorCaret("pwdOld"))
);
watch(
  () => passwordForm.new,
  () => nextTick(() => syncCursorCaret("pwdNew"))
);
watch(
  () => passwordForm.confirm,
  () => nextTick(() => syncCursorCaret("pwdConfirm"))
);
watch(
  () => passwordForm.code,
  () => nextTick(() => syncCursorCaret("pwdCode"))
);
watch(
  () => avatarUrlInput.value,
  () => nextTick(() => syncCursorCaret("avatarUrl"))
);

const onKeyInput = (val) => setActiveValue(val);
const closeKeyboard = () => {
  activeField.value = null;
};

// 键盘弹出/关闭时测量实际高度（安全键盘有安全区，高度会变化）
watch(
  activeField,
  (val) => {
    if (val) {
      nextTick(() => {
        measureKeyboardHeight();
        if (kbSheetRef.value && "ResizeObserver" in window) {
          if (kbResizeObserver) kbResizeObserver.disconnect();
          kbResizeObserver = new ResizeObserver(() => measureKeyboardHeight());
          kbResizeObserver.observe(kbSheetRef.value);
        }
      });
    } else {
      keyboardHeight.value = 0;
      if (kbResizeObserver) {
        kbResizeObserver.disconnect();
        kbResizeObserver = null;
      }
    }
  },
  { immediate: false }
);

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

// 点击键盘以外的任意区域（且不是输入框）时自动收起键盘
const handleDocClick = (e) => {
  if (!activeField.value) return;
  const el = e.target;
  // 点在键盘面板内 / 点在输入框（field-wrap）内 → 不收起
  if (el.closest(".kb-sheet") || el.closest(".field-wrap")) return;
  closeKeyboard();
};
onMounted(() => document.addEventListener("click", handleDocClick));
onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocClick);
  if (kbResizeObserver) {
    kbResizeObserver.disconnect();
    kbResizeObserver = null;
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
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.new)) {
    return showToast("邮箱格式不正确");
  }
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
  if (!passwordForm.old.trim() || !passwordForm.new.trim() || !passwordForm.confirm.trim()) {
    return showToast("请先填写完整信息");
  }
  if (passwordForm.new !== passwordForm.confirm) {
    return showToast("两次输入的新密码不一致");
  }
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

/* 密码弹窗内容：键盘弹出时可滚动，避免被键盘遮挡 */
.password-dialog-content {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 安全界面输入框：readonly + 模拟光标（照搬 Login） */
.field-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.field-wrap:not(:last-child) {
  margin-bottom: 12px;
}
.field-wrap :deep(.app-field) {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.field-wrap:hover :deep(.app-field) {
  border-color: var(--theme-text-tertiary);
}
.field-wrap.active :deep(.app-field) {
  border-color: var(--theme-primary);
  box-shadow: inset 0 0 0 2px var(--theme-primary);
}
.field-wrap.active :deep(.app-field__label) {
  color: var(--theme-primary);
}
.field-wrap.active :deep(.app-field__control) {
  border-color: transparent;
}
.field-measure {
  position: absolute;
  visibility: hidden;
  white-space: pre;
  font-size: 14px;
  line-height: 1.5;
  font-family: inherit;
  top: 42px;
}
.field-cursor {
  display: none;
  position: absolute;
  left: 8px;
  top: 42px;
  width: 2px;
  height: 18px;
  background: var(--theme-primary, #07c160);
  border-radius: 1px;
  animation: cursor-blink 1s step-end infinite;
}
.field-wrap.active .field-cursor {
  display: block;
}
@keyframes cursor-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
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

/* 内置 26 安全键盘弹层（覆盖于弹窗之上） */
.kb-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2002;
  background: var(--theme-bg-secondary, #fff);
  border-radius: 18px 18px 0 0;
  padding: 10px;
  box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.12);
}
.kb-up-enter-active,
.kb-up-leave-active {
  transition: transform 0.28s cubic-bezier(0.32, 0.72, 0.4, 1);
}
.kb-up-enter-from,
.kb-up-leave-to {
  transform: translateY(100%);
}
</style>
