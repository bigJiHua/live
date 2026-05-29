<template>
  <div class="login-container">
    <div class="login-header">
      <div class="logo">
        <img src="/logo.png" alt="Gold 财管" class="icon" />
      </div>
      <h1 class="title">Gold 财管</h1>
      <p class="subtitle">开启您的数字资产管理</p>
    </div>

    <van-form @submit="onSubmit" class="login-form">
      <van-cell-group inset>
        <van-field v-model="username" name="username" label="账号" placeholder="请输入用户名" left-icon="user-o"
          autocomplete="username" :rules="[{ required: true, message: '请填写用户名' }]" />
        <van-field v-model="password" :type="passwordVisible ? 'text' : 'password'" name="password" label="密码"
          placeholder="请输入密码" left-icon="lock" right-icon="eye-o" autocomplete="current-password" maxlength="15"
          :rules="passwordRules" @click-right-icon="togglePasswordVisibility" />
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
            ✔ 长度 6-15 位
          </li>
        </ul>
      </div> -->

      <div class="submit-bar">
        <van-row gutter="12">
          <!-- <van-col span="12">
            <van-button round block plain type="primary" @click="goToRegister">
              注册账号
            </van-button>
          </van-col> -->
          <van-col span="24">
            <van-button round block type="primary" native-type="submit" :loading="loading" loading-text="正在安全登录..."
              :disabled="!passwordRulesComputed.validLength">
              立即登录
            </van-button>
          </van-col>
        </van-row>
      </div>
    </van-form>
    <!-- TODO 注释 -->
    <template v-if="showDemoInfo">
      <div style="text-align: center; padding: 20px; font-size: 12px; color: #999;">
        本站点仅作演示效果，推荐使用手机Chrome浏览器打开预览。锁定PIN码为 123456
      </div>
    </template>
    <div v-else class="login-footer">
      <div class="footer-brand">
        <img src="/logo.png" :alt="brandName" class="footer-logo" />
        <span class="footer-name">{{ brandName }}</span>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" style="color: #999;">{{ icpNumber }}</a>
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
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { authApi } from "@/utils/api/auth";

const router = useRouter();
const username = ref(import.meta.env.VITE_APP_DEMO === 'true' ? import.meta.env.VITE_LOGIN_USERNAME || "" : "");
const password = ref(import.meta.env.VITE_APP_DEMO === 'true' ? import.meta.env.VITE_LOGIN_PASSWORD || "" : "");
const loading = ref(false);
const passwordVisible = ref(false);
const currentYear = new Date().getFullYear();
const showDemoInfo = import.meta.env.VITE_APP_DEMO === 'true'
const brandName = import.meta.env.VITE_BRAND_NAME
const icpNumber = import.meta.env.VITE_ICP_NUMBER
const psbNumber = import.meta.env.VITE_PSB_NUMBER
const poweredBy = import.meta.env.VITE_POWERED_BY
const copyrightStart = import.meta.env.VITE_COPYRIGHT_START

const togglePasswordVisibility = () => {
  passwordVisible.value = !passwordVisible.value;
};

// 密码校验规则（实时）
const passwordRulesComputed = computed(() => {
  const pwd = password.value;
  return {
    hasUpperCase: /[A-Z]/.test(pwd),
    hasLowerCase: /[a-z]/.test(pwd),
    hasNumber: /\d/.test(pwd),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
    validLength: pwd.length >= 6 && pwd.length <= 15,
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

  if (pwd.length > 0 && pwd.length < 6) {
    rules.push({
      validator: () => pwd.length >= 6 && pwd.length <= 15,
      message: "密码长度必须在 6-15 位之间",
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
  background-color: #fff;
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
  color: #323233;
  margin-bottom: 8px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: #969799;
}

.login-form {
  margin-bottom: 20px;
}

/* 调整 Vant 单元格组间距 */
:deep(.van-cell-group--inset) {
  margin: 0;
}

.submit-bar {
  margin-top: 30px;
}

.password-requirements {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px 16px;
  margin-top: 16px;
  font-size: 12px;
}

.password-requirements strong {
  display: block;
  margin-bottom: 8px;
  color: #323233;
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
  color: #07c160;
  font-weight: 500;
}

.password-requirements li.invalid {
  color: #969799;
}

.login-tip {
  text-align: center;
  padding: 20px 10px;
  margin-top: auto;
}

.login-tip p {
  margin: 0;
  font-size: 12px;
  color: #646566;
  line-height: 1.8;
}

.login-footer {
  text-align: center;
  padding: 24px 16px 32px;
  font-size: 11px;
  color: #999;
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
  color: #6666669d;
}

.footer-info a,
.footer-links a {
  color: #999;
  text-decoration: none;
}

.footer-info a:hover,
.footer-links a:hover {
  color: #666;
}

.beian-icon {
  width: 14px;
  height: 14px;
  vertical-align: middle;
  margin-right: 2px;
}

.footer-sep {
  margin: 0 6px;
  color: #ddd;
}

.footer-meta {
  color: #bbb;
}

.footer-links {
  margin-top: 2px;
}
</style>
