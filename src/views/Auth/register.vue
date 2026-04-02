<template>
  <div class="page">
    <div class="container">
      <div class="header">
        <h1>👨‍💼 管理员注册</h1>
        <p>首次设置 - 仅限一次</p>
      </div>

      <div class="content">
        <div class="info-box">
          <strong>📌 重要提示</strong>
          此页面仅在系统首次初始化时可用。注册完成后将无法再次访问。
        </div>

        <!-- 已存在管理员 -->
        <div v-if="hasAdmin" class="error show">
          ❌ 系统已存在管理员账号，请直接使用已有账号登录
        </div>

        <!-- 表单 -->
        <form v-else @submit.prevent="handleSubmit">
          <div v-if="error" class="error show">{{ error }}</div>
          <div v-if="success" class="success show">{{ success }}</div>

          <div class="form-group">
            <label for="username">用户名</label>
            <input
              v-model="form.username"
              id="username"
              type="text"
              placeholder="请输入管理员用户名"
              autocomplete="username"
              minlength="3"
              maxlength="20"
            />
          </div>

          <div class="form-group">
            <label for="email">邮箱地址</label>
            <input
              v-model="form.email"
              id="email"
              type="email"
              placeholder="请输入管理员邮箱"
              autocomplete="email"
            />
          </div>

          <div class="form-group">
            <label for="password">密码</label>
            <input
              v-model="form.password"
              id="password"
              type="password"
              placeholder="请输入管理员密码"
              autocomplete="new-password"
              maxlength="15"
              :class="{ 'error-input': !passwordRules.validLength && form.password.length > 0 }"
            />
          </div>

          <div class="form-group">
            <label for="confirmPassword">确认密码</label>
            <input
              v-model="form.confirmPassword"
              id="confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
              maxlength="15"
              :class="{ 'error-input': confirmPasswordError }"
            />
          </div>

          <div class="requirements">
            <strong>🔒 密码要求:</strong>
            <ul>
              <li :class="passwordRules.hasUpperCase ? 'valid' : form.password && !passwordRules.hasUpperCase ? 'invalid' : ''">
                ✔ 包含大写字母
              </li>
              <li :class="passwordRules.hasLowerCase ? 'valid' : form.password && !passwordRules.hasLowerCase ? 'invalid' : ''">
                ✔ 包含小写字母
              </li>
              <li :class="passwordRules.hasNumber ? 'valid' : form.password && !passwordRules.hasNumber ? 'invalid' : ''">
                ✔ 包含数字
              </li>
              <li :class="passwordRules.hasSpecial ? 'valid' : form.password && !passwordRules.hasSpecial ? 'invalid' : ''">
                ✔ 包含特殊字符
              </li>
              <li :class="passwordRules.validLength ? 'valid' : form.password && !passwordRules.validLength ? 'invalid' : ''">
                ✔ 长度 6-15 位
              </li>
            </ul>
          </div>

          <button type="submit" class="btn" :disabled="loading || !passwordRules.validLength">
            <span v-if="loading" class="spinner"></span>
            <span>{{ loading ? "正在注册..." : "🚀 注册管理员账号" }}</span>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { authApi } from "@/utils/api/auth";

const form = reactive({
  username: "admin",
  email: "admin@a.com",
  password: "Admin8888ok.",
  confirmPassword: "Admin8888ok.",
});

const loading = ref(false);
const error = ref("");
const success = ref("");
const hasAdmin = ref(false);

// 密码强度校验规则
const passwordRules = computed(() => {
  const pwd = form.password;
  return {
    hasUpperCase: /[A-Z]/.test(pwd),
    hasLowerCase: /[a-z]/.test(pwd),
    hasNumber: /\d/.test(pwd),
    hasSpecial: /[!@#$%^&*(),.?":{}|<>]/.test(pwd),
    validLength: pwd.length >= 6 && pwd.length <= 15,
  };
});

// 确认密码是否匹配
const confirmPasswordError = computed(() => {
  return form.confirmPassword && form.password !== form.confirmPassword;
});

// 检查管理员是否存在
const checkAdminExists = async () => {
  try {
    const res = await authApi.checkAdmin();
    hasAdmin.value = res.data.hasAdmin;
  } catch (err) {
    console.error("检查管理员失败:", err);
    hasAdmin.value = false;
  }
};

// 提交注册
const handleSubmit = async () => {
  error.value = "";
  success.value = "";

  if (form.username.length < 3) {
    error.value = "用户名至少 3 位";
    return;
  }

  if (!passwordRules.value.validLength) {
    error.value = "密码长度必须在 6-15 位之间";
    return;
  }

  if (!passwordRules.value.hasUpperCase) {
    error.value = "密码必须包含至少 1 个大写字母";
    return;
  }

  if (!passwordRules.value.hasLowerCase) {
    error.value = "密码必须包含至少 1 个小写字母";
    return;
  }

  if (!passwordRules.value.hasNumber) {
    error.value = "密码必须包含至少 1 个数字";
    return;
  }

  if (!passwordRules.value.hasSpecial) {
    error.value = "密码必须包含至少 1 个特殊字符";
    return;
  }

  if (form.password !== form.confirmPassword) {
    error.value = "两次密码不一致";
    return;
  }

  loading.value = true;

  try {
    const res = await authApi.adminRegister({
      username: form.username,
      email: form.email,
      password: form.password,
    });

    success.value = "注册成功，3秒后跳转...";
    setTimeout(() => {
      window.location.href = "/login";
    }, 3000);
  } catch (err) {
    error.value = err.response?.data?.message || "注册失败";
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  checkAdminExists();
});
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  max-width: 450px;
  width: 100%;
  overflow: hidden;
}

.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 30px;
  text-align: center;
}

.header h1 {
  font-size: 28px;
  margin-bottom: 10px;
}

.header p {
  opacity: 0.9;
  font-size: 14px;
}

.content {
  padding: 40px;
}

.form-group {
  margin-bottom: 25px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #2d3748;
  font-size: 14px;
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group input::placeholder {
  color: #a0aec0;
}

.form-group input.error-input {
  border-color: #fc8181;
  background-color: #fff5f5;
}

.btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.btn:active {
  transform: translateY(0);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.info-box {
  background: #ebf8ff;
  border-left: 4px solid #4299e1;
  padding: 15px;
  margin-bottom: 20px;
  border-radius: 4px;
  font-size: 13px;
  color: #2b6cb0;
}

.info-box strong {
  display: block;
  margin-bottom: 5px;
}

.error {
  background: #fed7d7;
  color: #c53030;
  padding: 12px 15px;
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 14px;
  display: none;
}

.success {
  background: #c6f6d5;
  color: #2f855a;
  padding: 12px 15px;
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 14px;
  display: none;
}

.error.show,
.success.show {
  display: block;
}

.spinner {
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top: 3px solid white;
  border-radius: 50%;
  width: 16px;
  height: 16px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.requirements {
  background: #fff5f5;
  border-left: 4px solid #fc8181;
  padding: 12px 15px;
  margin-bottom: 20px;
  border-radius: 4px;
  font-size: 12px;
  color: #c53030;
}

.requirements ul {
  margin-left: 20px;
  margin-top: 5px;
  list-style: none;
  padding: 0;
}

.requirements li {
  margin: 8px 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.requirements li.valid {
  color: #07c160;
  font-weight: 500;
}

.requirements li.invalid {
  color: #969799;
}

.requirements li.invalid::before {
  content: '✘';
}
</style>
