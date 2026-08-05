<template>
  <div class="login-container" @click="activeField && $event.target === $event.currentTarget && (activeField = null)">
    <div class="login-header">
      <div class="logo">
        <img src="/logo.png" alt="Gold 财管" class="icon" />
      </div>
      <h1 class="title">Gold 财管</h1>
      <p class="subtitle">开启您的数字资产管理</p>
    </div>

    <app-form @submit="onSubmit" class="login-form">
      <van-cell-group inset>
        <div class="field-wrap" :class="{ active: activeField === 'username' }">
          <app-field v-model="username" name="username" label="账号" placeholder="请输入账号或邮箱" left-icon="user-o"
            readonly @click="activeField = 'username'" />
          <span class="field-cursor" :style="{ left: curUser + 'px', top: curUserTop + 'px', transform: 'translateY(-50%)' }" />
          <span class="field-measure" ref="mUser">{{ username || ' ' }}</span>
        </div>
        <div class="field-wrap" :class="{ active: activeField === 'password' }">
          <app-field v-model="password" type="password" name="password" label="密码"
            placeholder="请输入密码" left-icon="lock" autocomplete="current-password" maxlength="30"
            :password-visible="passwordReveal" readonly :rules="passwordRules" @click="activeField = 'password'">
            <template #right-icon>
              <van-icon :name="passwordReveal ? 'eye-o' : 'closed-eye'" class="pwd-eye" @click.stop="toggleReveal" />
            </template>
          </app-field>
          <span class="field-cursor" :style="{ left: curPwd + 'px', top: curPwdTop + 'px', transform: 'translateY(-50%)' }" />
          <span class="field-measure" ref="mPwd">{{ passwordReveal ? (password || ' ') : ('●'.repeat(password.length) || ' ') }}</span>
        </div>
      </van-cell-group>
      <!-- 
      <div class="password-requirements">
        <strong>密码要求:</strong>
        <ul>
          <li :class="passwordRulesComputed.hasUpperCase ? 'valid' : 'invalid'">
            ✔ 包含大写字母
          </li>
          <li :class="passwordRulesComputed.hasLowerCase ? 'valid' : 'invalid'">
            ✔ 包含小写字母
          </li>
          <li :class="passwordRulesComputed.hasNumber ? 'valid' : 'invalid'">
            ✔ 包含数字
          </li>
          <li :class="passwordRulesComputed.hasSpecial ? 'valid' : 'invalid'">
            ✔ 包含特殊字符
          </li>
          <li :class="passwordRulesComputed.validLength ? 'valid' : 'invalid'">
            ✔ 长度 6-30 位
          </li>
        </ul>
      </div> -->

      <div class="submit-bar">
        <van-row gutter="12">
          <!-- <van-col span="12">
            <app-button round block plain type="primary" @click="goToRegister">
              注册账号
            </app-button>
          </van-col> -->
          <van-col span="24">
            <app-button round block type="primary" native-type="submit" :loading="loading" loading-text="正在安全登录..."
              :disabled="!passwordRulesComputed.validLength">
              立即登录
            </app-button>
          </van-col>
        </van-row>
      </div>
    </app-form>

    <!-- 安全键盘弹层 -->
    <transition name="kb-up">
      <div v-if="activeField" class="kb-sheet">
        <FullKeyboard
          :model-value="activeValue"
          :public-key="publicKey"
          @update:model-value="onKeyInput"
          @login="activeField = null"
        />
      </div>
    </transition>

    <!-- TODO 注释 -->
    <template v-if="showDemoInfo">
      <div style="text-align: center; padding: 20px; font-size: 12px; color: var(--theme-text-tertiary);">
        本站点仅作演示效果，推荐使用手机Chrome浏览器打开预览。锁定PIN码为 123456
      </div>
    </template>
    <div v-else class="login-footer">
      <div class="footer-brand">
        <img src="/logo.png" :alt="brandName" class="footer-logo" />
        <span class="footer-name">{{ brandName }}</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" style="color: var(--theme-text-tertiary);">{{ icpNumber }}</a>
      </div>
      <div class="footer-info">
        <a href="http://www.beian.gov.cn/" target="_blank" rel="noopener noreferrer">
          <img src="/icons/ga-beian.png" class="beian-icon" alt="公安备案" />
          {{ psbNumber }}
        </a>
      </div>
      <div class="footer-meta">
        <span>Power By {{ poweredBy }}</span>
        <span class="footer-sep">|</span>
        <span>© {{ copyrightStart }}-{{ currentYear }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from "vue";
import { useRouter } from "vue-router";
import { authApi } from "@/utils/api/auth";
import FullKeyboard from "@/components/KeyBoard/FullKeyboard.vue";

const router = useRouter();
// 仅在演示模式预填演示账号；生产环境不预填任何凭据，避免硬编码密码泄露
const username = ref(import.meta.env.VITE_APP_DEMO === 'true' ? import.meta.env.VITE_LOGIN_USERNAME || "" : "");
const password = ref(import.meta.env.VITE_APP_DEMO === 'true' ? import.meta.env.VITE_LOGIN_PASSWORD || "" : "");

const loading = ref(false);
const activeField = ref(null);
const publicKey = ref("");
// 密码明文显示开关（眼睛按钮）——仅本地展示，不影响 readonly + 安全键盘输入逻辑
const passwordReveal = ref(false);
const toggleReveal = () => {
  passwordReveal.value = !passwordReveal.value;
  nextTick(() => syncCursorCaret(mPwd.value, (v) => (curPwd.value = v), (v) => (curPwdTop.value = v)));
};

// 光标跟随位置
// 注意：安全界面——字段为 readonly，输入经由 FullKeyboard 安全键盘，密码以 ● 圆点显示。
// 光标/测量元素均为只读模拟层，必须与实际 .app-field__input 的文字精确对齐，
// 故改用 JS 动态读取输入框真实位置，避免硬编码 top 导致的光标与文字错位。
const mUser = ref(null);
const mPwd = ref(null);
const curUser = ref(8);
const curPwd = ref(8);
const curUserTop = ref(42);
const curPwdTop = ref(42);

function syncCursorCaret(refEl, setLeft, setTop) {
  if (!refEl) return;
  const wrap = refEl.closest(".field-wrap");
  const input = wrap && wrap.querySelector(".app-field__input");
  if (!input) return;
  // 让隐藏测量元素与输入框文字完全重叠，确保测宽精确
  refEl.style.left = input.offsetLeft + "px";
  refEl.style.top = input.offsetTop + "px";
  setLeft(input.offsetLeft + refEl.offsetWidth);
  // 以隐藏测量元素（与输入框文字同字体、已与 input 同位置）的垂直中心对齐光标，
  // 避免 text / password 输入框 clientHeight 差异导致光标错位
  setTop(refEl.offsetTop + refEl.offsetHeight / 2);
}

watch(username, () => nextTick(() => {
  syncCursorCaret(mUser.value, (v) => (curUser.value = v), (v) => (curUserTop.value = v));
}));
watch(password, () => nextTick(() => {
  syncCursorCaret(mPwd.value, (v) => (curPwd.value = v), (v) => (curPwdTop.value = v));
}));
onMounted(() => nextTick(() => {
  syncCursorCaret(mUser.value, (v) => (curUser.value = v), (v) => (curUserTop.value = v));
  syncCursorCaret(mPwd.value, (v) => (curPwd.value = v), (v) => (curPwdTop.value = v));
}));
const currentYear = new Date().getFullYear();
const showDemoInfo = import.meta.env.VITE_APP_DEMO === 'true'
const brandName = import.meta.env.VITE_BRAND_NAME
const icpNumber = import.meta.env.VITE_ICP_NUMBER
const psbNumber = import.meta.env.VITE_PSB_NUMBER
const poweredBy = import.meta.env.VITE_POWERED_BY
const copyrightStart = import.meta.env.VITE_COPYRIGHT_START

// 键盘 v-model 绑定当前激活字段值
const activeValue = computed(() => {
  if (activeField.value === "username") return username.value;
  if (activeField.value === "password") return password.value;
  return "";
});
const onKeyInput = (val) => {
  if (activeField.value === "username") username.value = val;
  else if (activeField.value === "password") password.value = val;
};

// 密码校验规则（实时）
const passwordRulesComputed = computed(() => {
  const pwd = password.value;
  return {
    hasUpperCase: /[A-Z]/.test(pwd),
    hasLowerCase: /[a-z]/.test(pwd),
    hasNumber: /\d/.test(pwd),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
    validLength: pwd.length >= 6 && pwd.length <= 30,
  };
});

// Vant 表单校验规则
const passwordRules = computed(() => {
  const rules = [];
  const pwd = password.value;

  if (pwd.length > 0) {
    rules.push({
      required: true,
      message: "请输入密码",
    });
  }

  if (pwd.length > 0 && (pwd.length < 6 || pwd.length > 30)) {
    rules.push({
      validator: () => pwd.length >= 6 && pwd.length <= 30,
      message: "密码长度必须在 6-30 位之间",
    });
  }

  if (pwd.length > 0 && !/[A-Z]/.test(pwd)) {
    rules.push({
      validator: () => /[A-Z]/.test(pwd),
      message: "密码必须包含大写字母",
    });
  }

  if (pwd.length > 0 && !/[a-z]/.test(pwd)) {
    rules.push({
      validator: () => /[a-z]/.test(pwd),
      message: "密码必须包含小写字母",
    });
  }

  if (pwd.length > 0 && !/\d/.test(pwd)) {
    rules.push({
      validator: () => /\d/.test(pwd),
      message: "密码必须包含数字",
    });
  }

  if (pwd.length > 0 && !/[!@#$%^&*(),.?":{}|<>]/.test(pwd)) {
    rules.push({
      validator: () => /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
      message: "密码必须包含特殊字符",
    });
  }

  return rules;
});

const onSubmit = async (values) => {
  loading.value = true;

  try {
    // 调用登录 API
    const res = await authApi.login({
      nameOrEmail: values.username,
      password: values.password,
    });
    // 存储返回的 Token
    const token = res.token;
    if (token) {
      localStorage.setItem("finance_token", token);
    }
    setTimeout(() => {
      loading.value = false;
      router.push("/");
    }, 1500);
  } catch (error) {
    loading.value = false;
  }
};

const goToRegister = () => {
  router.push("/register");
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background-color: var(--theme-bg-secondary);
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
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;

  >img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }
}

.title {
  font-size: 24px;
  color: var(--theme-text-primary);
  margin-bottom: 8px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: var(--theme-text-tertiary);
}

.login-form {
  margin-bottom: 20px;
}

/* 调整 Vant 单元格组间距 */
:deep(.van-cell-group--inset) {
  margin: 0;
  background: transparent;
}

.submit-bar {
  margin-top: 30px;
}

.password-requirements {
  background: var(--theme-bg-primary);
  border-radius: 8px;
  padding: 12px 16px;
  margin-top: 16px;
  font-size: 12px;
}

.password-requirements strong {
  display: block;
  margin-bottom: 8px;
  color: var(--theme-text-primary);
  font-weight: 600;
}

.password-requirements ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.password-requirements li {
  margin: 6px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.password-requirements li.valid {
  color: var(--van-green, #07c160);
  font-weight: 500;
}

.password-requirements li.invalid {
  color: var(--theme-text-tertiary);
}

.login-tip {
  text-align: center;
  padding: 20px 10px;
  margin-top: auto;
}

.login-tip p {
  margin: 0;
  font-size: 12px;
  color: var(--theme-text-secondary);
  line-height: 1.8;
}

.login-footer {
  text-align: center;
  padding: 24px 16px 32px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  line-height: 1.8;
  margin-top: auto;
}

.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 6px;
}

.footer-logo {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.footer-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-text-secondary);
}

.footer-info a,
.footer-links a {
  color: var(--theme-text-tertiary);
  text-decoration: none;
}

.footer-info a:hover,
.footer-links a:hover {
  color: var(--theme-text-secondary);
}

.beian-icon {
  width: 14px;
  height: 14px;
  vertical-align: middle;
  margin-right: 2px;
}

.footer-sep {
  margin: 0 6px;
  color: var(--theme-text-tertiary);
}

.footer-meta {
  color: var(--theme-text-tertiary);
}

.footer-links {
  margin-top: 2px;
}

/* 安全键盘弹层 */
.field-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 10px;
  transition: border-color .2s ease, box-shadow .2s ease;
}
.field-wrap:not(:last-child) { margin-bottom: 12px; }
.field-wrap :deep(.app-field) {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  transition: border-color .2s ease, box-shadow .2s ease;
}
.field-wrap:hover :deep(.app-field) {
  border-color: var(--theme-text-tertiary);
}
.field-wrap.active :deep(.app-field) {
  border-color: var(--theme-primary);
  box-shadow: inset 0 0 0 2px var(--theme-primary);
}
.field-wrap.active :deep(.app-field__label) { color: var(--theme-primary); }
.field-wrap.active :deep(.app-field__control) { border-color: transparent; }
.pwd-eye {
  font-size: 18px;
  color: var(--theme-primary);
  padding: 2px;
  border-radius: 50%;
  transition: background-color .2s ease, color .2s ease;
}
.pwd-eye:hover { background: rgba(var(--theme-primary-rgb, 58, 102, 224), 0.12); }
.field-measure {
  position: absolute; visibility: hidden; white-space: pre;
  font-size: 14px; line-height: 1.5; font-family: inherit; top: 42px;
}
.field-cursor {
  display: none;
  position: absolute; left: 8px; top: 42px;
  width: 2px; height: 18px; background: var(--theme-primary, #07c160);
  border-radius: 1px;
  animation: cursor-blink 1s step-end infinite;
}
.field-wrap.active .field-cursor { display: block; }
@keyframes cursor-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.kb-sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 2001;
  background: var(--theme-bg-secondary, #fff);
  border-radius: 18px 18px 0 0;
  padding: 12px 10px calc(16px + env(safe-area-inset-bottom));
  box-shadow: 0 -6px 24px rgba(0,0,0,.12);
}

.kb-up-enter-active, .kb-up-leave-active { transition: transform .28s cubic-bezier(.32,.72,.4,1); }
.kb-up-enter-from, .kb-up-leave-to { transform: translateY(100%); }
</style>
