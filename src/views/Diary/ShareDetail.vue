<template>
  <div class="share-page">
    <!-- Token 模式：加载中 -->
    <van-loading v-if="mode === 'token' && loading" class="loading-center" />

    <!-- 错误状态 -->
    <div v-else-if="errorState" class="error-box">
      <van-empty :image="errorState.image" :description="errorState.message">
        <template v-if="errorState.showRetry">
          <app-button round type="primary" size="small" @click="loadShare">
            重新获取
          </app-button>
        </template>
      </van-empty>
    </div>

    <!-- 密码输入模式 -->
    <div v-else-if="mode === 'password' && !data" class="password-box">
      <div class="pw-card">
        <van-icon name="lock" size="40" :color="'var(--theme-primary)'" />
        <h3>需要访问密码</h3>
        <p class="pw-desc">请输入分享者提供的 6 位数字密码</p>

        <!-- 6位密码输入（点状显示） -->
        <van-password-input
          :value="password"
          :length="6"
          :focused="pwFocused"
          :class="{ 'pw-incomplete': pwIncomplete }"
          @focus="pwFocused = true"
        />

        <p v-if="pwError" class="pw-error">{{ pwError }}</p>
      </div>

      <!-- 数字键盘：删除=退格，完成 → 6位自动验证 / 不足则红色提示 -->
      <van-number-keyboard
        v-model="password"
        :show="pwFocused"
        :maxlength="6"
        theme="custom"
        extra-key="."
        close-button-text="完成"
        @close="onKeyboardClose"
        @blur="pwFocused = false"
      />
    </div>

    <!-- 正常内容 -->
    <div v-else-if="data" class="content-box">
      <div class="header">
        <van-image round width="40" height="40" :src="defaultAvatar" class="avatar" />
        <div class="header-info">
          <div class="author-name">{{ data.author?.name || 'ta' }}</div>
          <div class="time-text">{{ formatTime(data.create_time) }}</div>
        </div>
        <div v-if="data.mood" class="mood-tag">
          <van-icon name="smile-o" /> {{ data.mood }}
        </div>
      </div>

      <div class="body-text" v-html="data.content"></div>

      <div v-if="data.img_url?.length" class="image-grid">
        <div
          v-for="(img, i) in data.img_url"
          :key="i"
          class="grid-item"
          @click="previewImage(i)"
        >
          <van-image fit="cover" :src="img.url" radius="6" />
        </div>
      </div>

      <div v-if="data.location?.name" class="location-row">
        <van-icon name="location-o" />
        <span>{{ data.location.name }}</span>
      </div>

      <div class="footer">
        <span v-if="data.expire_at" class="expire-hint">
          分享链接 {{ expireText }}
        </span>
      </div>
    </div>

    <van-image-preview
      v-model:show="showPreview"
      :images="previewUrls"
      :start-position="previewIdx"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import ENV from '@/utils/env';

const route = useRoute();
const BASE_URL = ENV.FILE_BASE_URL;

// mode: 'token' | 'password'
const mode = ref(
  route.query.token ? 'token' : route.query.id ? 'password' : null
);
const loading = ref(false);
const data = ref(null);
const errorState = ref(null);
// 公开页面不请求任何用户数据，使用内联 SVG 默认头像
const defaultAvatar = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="20" fill="#e8e8e8"/><circle cx="20" cy="14" r="8" fill="#c0c0c0"/><ellipse cx="20" cy="36" rx="15" ry="10" fill="#c0c0c0"/></svg>'
);

// ── 密码模式状态 ──
const password = ref('');
const pwFocused = ref(true);
const pwLoading = ref(false);
const pwError = ref('');
const pwIncomplete = ref(false);

// 键盘「完成」按钮：6位自动验证 → 不足则红框提示
const onKeyboardClose = () => {
  if (password.value.length < 6) {
    pwIncomplete.value = true;
    pwError.value = '请输入完整密码';
    nextTick(() => { pwFocused.value = true; });
  } else {
    pwIncomplete.value = false;
    pwError.value = '';
    handlePassword();
  }
};

// ── 预览 ──
const showPreview = ref(false);
const previewUrls = ref([]);
const previewIdx = ref(0);

const previewImage = (i) => {
  previewUrls.value = data.value.img_url.map((img) => img.url);
  previewIdx.value = i;
  showPreview.value = true;
};

// ── 工具 ──
const formatTime = (ts) => {
  if (!ts) return '';
  const d = new Date(Number(ts));
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const expireText = computed(() => {
  if (!data.value?.expire_at) return '长期有效';
  const diff = new Date(data.value.expire_at).getTime() - Date.now();
  if (diff <= 0) return '已过期';
  const hours = Math.floor(diff / 3600000);
  const mins = Math.floor((diff % 3600000) / 60000);
  if (hours > 24) return `${Math.floor(hours / 24)}天后过期`;
  if (hours > 0) return `${hours}小时后过期`;
  return `${mins}分钟后过期`;
});

const processData = (d) => {
  if (d.img_url?.length) {
    d.img_url = d.img_url.map((img) => {
      const url = img.url || img;
      return { url: url.startsWith('http') ? url : BASE_URL + url };
    });
  }
  data.value = d;
};

// ── Token 模式加载 ──
const loadShare = async () => {
  const token = route.query.token;
  if (!token) {
    errorState.value = { image: 'error', message: '链接无效' };
    return;
  }

  loading.value = true;
  errorState.value = null;

  try {
    const res = await fetch(`/api/v1/share/${encodeURIComponent(token)}`);
    const json = await res.json();

    if (json.status === 200) {
      processData(json.data);
    } else if (json.status === 403 || json.status === 410) {
      errorState.value = { image: 'network', message: json.message, showRetry: false };
    } else if (json.status === 404) {
      errorState.value = { image: 'search', message: '内容不存在或已删除', showRetry: false };
    } else {
      errorState.value = { image: 'error', message: json.message || '加载失败', showRetry: true };
    }
  } catch {
    errorState.value = { image: 'network', message: '网络异常，请重试', showRetry: true };
  } finally {
    loading.value = false;
  }
};

// ── 密码模式验证 ──
const handlePassword = async () => {
  const pw = password.value;
  if (pw.length !== 6) return;

  pwLoading.value = true;
  pwError.value = '';

  try {
    const res = await fetch('/api/v1/share/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: route.query.id, pw }),
    });
    const json = await res.json();

    if (json.status === 200) {
      processData(json.data);
    } else if (json.status === 429) {
      // 防爆锁定
      pwError.value = json.message || '尝试次数过多，请稍后再试';
      pwFocused.value = false;
      setTimeout(() => { pwFocused.value = true; }, (json.retryAfter || 120) * 1000);
    } else if (json.status === 403) {
      pwError.value = json.message || '密码错误';
      password.value = '';
    } else {
      errorState.value = { image: 'error', message: json.message || '验证失败', showRetry: false };
    }
  } catch {
    errorState.value = { image: 'network', message: '网络异常，请重试', showRetry: true };
  } finally {
    pwLoading.value = false;
  }
};

onMounted(() => {
  if (mode.value === 'token') loadShare();
  if (!mode.value) {
    errorState.value = { image: 'error', message: '链接无效，缺少参数' };
  }
});
</script>

<style scoped>
.share-page {
  min-height: 100dvh;
  background: var(--theme-bg-primary);
  padding-bottom: 40px;
}

.loading-center {
  padding: 120px 0;
  display: flex;
  justify-content: center;
}

.error-box {
  padding-top: 120px;
}

/* ── 密码输入卡片 ── */
.password-box {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--theme-bg-primary);
}

.pw-card {
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  padding: 32px 24px 24px;
  width: 100%;
  max-width: 360px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.pw-card h3 {
  margin: 12px 0 6px;
  font-size: 18px;
  color: var(--theme-text-primary);
}

.pw-desc {
  font-size: 13px;
  color: var(--theme-text-tertiary);
  margin-bottom: 24px;
}

.pw-card :deep(.van-password-input) {
  margin: 0 auto;
}

/* 密码未填完 → 红框提示 */
.pw-incomplete :deep(.van-password-input__security) {
  border-color: var(--van-danger-color, #ee0a24) !important;
}

.pw-error {
  color: var(--van-danger-color, #ee0a24);
  font-size: 13px;
  margin-top: 12px;
}

/* ── 内容区 ── */
.content-box {
  background: var(--theme-bg-secondary);
  min-height: 100dvh;
}

.header {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 10px;
}

.header-info {
  flex: 1;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.time-text {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 2px;
}

.mood-tag {
  background: var(--theme-primary-light);
  color: var(--theme-primary);
  padding: 4px 10px;
  border-radius: 100px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.body-text {
  padding: 0 16px 16px;
  font-size: 17px;
  line-height: 1.7;
  color: var(--theme-text-primary);
  word-break: break-word;
}

.body-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 8px 0;
}

.body-text :deep(p) {
  margin: 0 0 8px;
}

.body-text :deep(blockquote) {
  border-left: 3px solid var(--theme-primary);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-tertiary);
}

.image-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 4px;
  padding: 0 16px 16px;
}

.grid-item {
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 8px;
}

.location-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 16px 16px;
  font-size: 13px;
  color: var(--theme-text-tertiary);
}

.footer {
  padding: 16px;
  text-align: center;
}

.expire-hint {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}
</style>
