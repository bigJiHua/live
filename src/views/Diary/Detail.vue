<template>
  <div class="page-diary-detail">
    <van-loading v-if="loading" class="loading-center" />

    <div v-else-if="detail" class="detail-content">
      <!-- 主内容 -->
      <div class="main-moment">
        <div class="user-header">
          <van-image
            round
            width="48"
            height="48"
            :src="avatarUrl"
            class="avatar-shadow"
          />
          <div class="user-info">
            <span class="username">{{ authorName }}</span>
            <div class="time-location">
              <span class="time">{{ formatTime(detail.create_time) }}</span>
              <span v-if="detail.location?.name" class="loc-text">
                · {{ detail.location.name }}</span
              >
            </div>
          </div>
          <div v-if="detail.mood" class="main-mood-tag">
            <van-icon name="smile-o" /> {{ detail.mood }}
          </div>
        </div>

        <div class="content-text" v-html="detail.content"></div>

        <div
          v-if="detail.img_url?.length"
          class="image-grid"
        >
          <div
            v-for="(img, index) in detail.img_url"
            :key="index"
            class="grid-item"
          >
            <van-image
              fit="cover"
              :src="img.url"
              @click="previewImage(index)"
            />
          </div>
        </div>

        <div class="main-footer">
          <div class="action-btns">
            <div class="share-btn" @click="handleShare">
              <van-icon :name="isShared ? 'share-o' : 'share-o'" />
              {{ isShared ? '关闭分享' : '分享给好友' }}
            </div>
            <div class="delete-btn" @click="openDeleteDialog('main', null, '确定要删除这条动态吗？')">
              <van-icon name="delete-o" /> 删除
            </div>
          </div>
        </div>
      </div>

      <!-- 补充内容（正文续写 / 故事续篇） -->
      <div class="append-section">
        <div class="append-header">
          <span class="title">追文</span>
          <span class="count">{{ childrenCount }} 条今日追文</span>
        </div>

        <van-loading v-if="childrenLoading" class="loading-append" />

        <div v-else-if="children.length === 0" class="empty-append">
          <van-icon name="edit" size="36" color="#e0e0e0" />
          <p>暂无补充内容</p>
        </div>

        <div v-else class="append-list">
          <div
            v-for="(child, idx) in children"
            :key="child.id"
            class="append-item"
          >
            <div class="append-line">
              <div class="dot"></div>
              <div v-if="idx !== children.length - 1" class="line"></div>
            </div>

            <div class="append-content-wrap">
              <div class="append-content" v-html="child.content"></div>

              <div v-if="child.img_url?.length" class="append-image-list">
                <div
                  v-for="(img, i) in child.img_url"
                  :key="i"
                  class="append-grid-item"
                >
                  <van-image
                    fit="cover"
                    radius="6"
                    :src="img.url"
                    @click="previewChildImage(child, i)"
                  />
                </div>
              </div>

              <div class="append-bottom">
                <div class="append-bottom-left">
                  <span class="time">{{ formatTime(child.create_time) }}</span>
                  <span v-if="child.location?.name" class="loc-text">· {{ child.location.name }}</span>
                </div>
                <div class="append-actions">
                  <div class="delete-btn" @click="openDeleteDialog('child', child.id, '确定要删除这条追文吗？')">
                    <van-icon name="delete-o" size="12" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <van-empty v-else description="内容不存在" />
    <van-image-preview
      v-model:show="showPreview"
      :images="previewImages"
      :start-position="previewIndex"
    />

    <div class="add-diary-btn" @click="$router.push('/diary/add')">
      <van-icon name="plus" />
    </div>

    <!-- 删除确认弹窗 -->
    <app-dialog
      v-model:show="showDeleteDialog"
      title="确认删除"
      :message="deleteMessage"
      show-cancel-button
      :confirm-button-disabled="confirmCountdown > 0"
      :confirm-button-text="confirmCountdown > 0 ? `${confirmCountdown}秒后确认` : '确认'"
      @confirm="executeDelete"
    />

    <!-- 分享弹窗 -->
    <app-popup
      v-model:show="showSharePopup"
      position="bottom"
      round
      :style="{ padding: '20px 16px' }"
    >
      <div class="share-popup">
        <h3 class="share-title">{{ isShared ? '管理分享' : '开启分享' }}</h3>

        <!-- 开关 -->
        <div class="share-row">
          <span>分享状态</span>
          <van-switch
            v-model="shareOn"
            active-color="var(--van-green, #07c160)"
            size="22px"
          />
        </div>

        <!-- 时效选择（仅开启时显示） -->
        <div v-if="shareOn && !isShared" class="share-row">
          <span>有效时间</span>
          <van-action-sheet
            v-model:show="showDurationPicker"
            :actions="durationOptions"
            @select="onDurationSelect"
          />
          <span class="duration-value" @click="showDurationPicker = true">
            {{ durationText }}
            <van-icon name="arrow" size="12" />
          </span>
        </div>
        <p v-if="shareOn && !isShared" class="share-hint">不选择默认 1 小时后过期</p>

        <!-- 当已分享时显示当前密码 + 复制按钮 -->
        <template v-if="isShared && shareOn">
          <div class="share-row">
            <span>当前密码</span>
            <span class="pw-display">{{ detail?.visible_type?.pw || '***' }}</span>
          </div>
          <div class="result-btns" style="margin-top:12px">
            <app-button
              round
              block
              plain
              type="primary"
              size="small"
              @click="copyExistingLink('password')"
            >
              密码访问（复制链接）
            </app-button>
            <app-button
              round
              block
              type="primary"
              size="small"
              @click="copyExistingLink('token')"
            >
              公共访问（复制链接）
            </app-button>
          </div>
        </template>

        <!-- 按钮 -->
        <div class="share-btns">
          <app-button round block plain type="default" @click="showSharePopup = false">
            取消
          </app-button>
          <app-button
            v-if="shareOn && !isShared"
            round
            block
            type="primary"
            :loading="shareLoading"
            @click="doOpenShare"
          >
            确认并开启分享
          </app-button>
          <app-button
            v-if="isShared && !shareOn"
            round
            block
            type="danger"
            :loading="shareLoading"
            @click="doCloseShare"
          >
            确认关闭分享
          </app-button>
        </div>
      </div>
    </app-popup>

    <!-- 分享结果弹窗 -->
    <app-popup
      v-model:show="showShareResult"
      position="bottom"
      round
      :style="{ padding: '24px 16px' }"
    >
      <div class="share-result">
        <van-icon name="success" size="40" color="var(--van-green, #07c160)" style="display:block;margin:0 auto 8px" />
        <h3 class="share-title">分享已开启</h3>
        <p v-if="shareResult.password" class="result-pw">
          密码：<strong>{{ shareResult.password }}</strong>
        </p>
        <div class="result-btns">
          <app-button
            round
            block
            plain
            type="primary"
            @click="copyShareLink('password')"
          >
            密码访问（复制链接）
          </app-button>
          <app-button
            round
            block
            type="primary"
            @click="copyShareLink('token')"
          >
            公共访问（复制链接）
          </app-button>
        </div>
        <app-button
          size="small"
          plain
          type="default"
          style="margin-top:12px"
          @click="showShareResult = false"
        >
          关闭
        </app-button>
      </div>
    </app-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { showToast } from "vant";
import { momentApi } from "@/utils/api/moment";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const loading = ref(false);
const detail = ref(null);
const childrenLoading = ref(false);

// 删除确认弹窗
const showDeleteDialog = ref(false);
const deleteMessage = ref('');
const deleteTarget = ref(null); // { type: 'main' | 'child', id?: string }

// 确认弹窗倒计时
const confirmCountdown = ref(0);
let confirmTimer = null;

const resetConfirmCountdown = () => {
  confirmCountdown.value = 0;
  if (confirmTimer) {
    clearInterval(confirmTimer);
    confirmTimer = null;
  }
};

const startConfirmCountdown = () => {
  confirmCountdown.value = 10;
  confirmTimer = setInterval(() => {
    confirmCountdown.value--;
    if (confirmCountdown.value <= 0) {
      clearInterval(confirmTimer);
      confirmTimer = null;
    }
  }, 1000);
};

// 打开删除弹窗
const openDeleteDialog = (type, id = null, message = '') => {
  deleteTarget.value = { type, id };
  deleteMessage.value = message;
  showDeleteDialog.value = true;
  startConfirmCountdown();
};

// 执行删除
const executeDelete = async () => {
  const target = deleteTarget.value;
  if (!target) return;
  
  try {
    if (target.type === 'main') {
      await momentApi.delete(detail.value.id);
      showToast("删除成功");
      router.back();
    } else if (target.type === 'child') {
      await momentApi.delete(target.id);
      showToast("删除成功");
      const childIds = detail.value.children || [];
      if (childIds.length > 0) {
        await loadChildren(childIds);
      }
    }
  } catch (err) {
    console.error("删除失败:", err);
    showToast(err.message || "删除失败");
  }
};

// 基础 URL
import ENV from '@/utils/env'
const BASE_URL = ENV.FILE_BASE_URL;

const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  return BASE_URL + path;
};

// 预览相关
const showPreview = ref(false);
const previewImages = ref([]);
const previewIndex = ref(0);

// 计算属性 - 从 store 获取用户信息
const authorName = computed(() => {
  return (
    detail.value?.author?.name ||
    detail.value?.user_name ||
    userStore.username ||
    "用户"
  );
});

const avatarUrl = computed(() => {
  const storeAvatar = userStore.actualAvatar;
  return getFullUrl(
    detail.value?.author?.avatar || detail.value?.avatar || storeAvatar
  );
});

// 追文列表（从 children_data 获取）
const children = computed(() => {
  return detail.value?.children_data || [];
});

const childrenCount = computed(() => {
  return children.value.length;
});

// 处理图片 URL
const processImageUrl = (data) => {
  if (data.img_url && Array.isArray(data.img_url)) {
    data.img_url = data.img_url.map((img) => ({
      url: getFullUrl(img.url || img),
      thumbnail: getFullUrl(img.thumbnail || img.url || img),
    }));
  }
  if (data.children) {
    data.children.forEach((child) => {
      if (child.img_url && Array.isArray(child.img_url)) {
        child.img_url = child.img_url.map((img) => ({
          url: getFullUrl(img.url || img),
        }));
      }
    });
  }
  return data;
};

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return "";
  const date = new Date(parseInt(timestamp));
  const now = new Date();
  const diff = now - date;

  // 小于1分钟
  if (diff < 60000) return "刚刚";
  // 小于1小时
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  // 小于24小时
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;

  // 超过24小时显示日期
  return `${date.getMonth() + 1}-${date.getDate()} ${date
    .getHours()
    .toString()
    .padStart(2, "0")}:${date.getMinutes().toString().padStart(2, "0")}`;
};

// 5+ Runtime(hash 路由) 下 router.back() 返回时 URL query 可能丢失，
// 用 sessionStorage 暂存「上一次成功加载的日记 id」作兜底，避免返回后 id 丢失导致「日记不存在」
const DIARY_ID_KEY = 'diary_detail_last_id'
// 加载详情
const loadDetail = async () => {
  const id = route.params.id || route.query.id || sessionStorage.getItem(DIARY_ID_KEY);
  if (!id) {
    showToast("参数错误");
    router.back();
    return;
  }
  sessionStorage.setItem(DIARY_ID_KEY, id);

  loading.value = true;
  try {
    const res = await momentApi.getOne(id);
    if (res.data) {
      const processedData = processImageUrl(res.data);
      detail.value = processedData;

      // 如果有追文ID列表，批量获取追文数据
      if (processedData.children && processedData.children.length > 0) {
        await loadChildren(processedData.children);
      }
    }
  } catch (err) {
    console.error("加载详情失败:", err);
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 加载追文数据
const loadChildren = async (childIds) => {
  childrenLoading.value = true;
  try {
    const res = await momentApi.batchDetail(childIds);
    if (res.data && Array.isArray(res.data)) {
      // 处理追文图片 URL
      const processedChildren = res.data.map((child) => processImageUrl(child));
      // 按时间戳排序（升序，最早的在前）
      processedChildren.sort((a, b) => {
        const timeA = parseInt(a.create_time) || 0;
        const timeB = parseInt(b.create_time) || 0;
        return timeA - timeB;
      });
      // 更新 detail 的 children_data
      detail.value.children_data = processedChildren;
    }
  } catch (err) {
    console.error("加载追文失败:", err);
  } finally {
    childrenLoading.value = false;
  }
};

// 预览图片
const previewImage = (index) => {
  previewImages.value = detail.value.img_url.map((img) => img.url);
  previewIndex.value = index;
  showPreview.value = true;
};

// 预览追文图片
const previewChildImage = (child, index) => {
  previewImages.value = child.img_url.map((img) => img.url);
  previewIndex.value = index;
  showPreview.value = true;
};

// ── 分享功能 ──
const showSharePopup = ref(false);
const showShareResult = ref(false);
const shareOn = ref(false);
const shareLoading = ref(false);
const shareDuration = ref(1);
const showDurationPicker = ref(false);
const shareResult = ref({ token: '', password: '', tokenUrl: '', pwUrl: '' });

const isShared = computed(() => detail.value?.visible_type?.vt === 1);

const durationOptions = [
  { name: '1 小时', value: 1 },
  { name: '6 小时', value: 6 },
  { name: '12 小时', value: 12 },
  { name: '24 小时', value: 24 },
  { name: '48 小时', value: 48 },
  { name: '72 小时', value: 72 },
];

const durationText = computed(() => {
  const opt = durationOptions.find((o) => o.value === shareDuration.value);
  return opt ? opt.name : '1 小时';
});

const onDurationSelect = (item) => {
  shareDuration.value = item.value;
  showDurationPicker.value = false;
};

const handleShare = () => {
  shareOn.value = isShared.value;
  shareDuration.value = 1;
  showSharePopup.value = true;
};

const doOpenShare = async () => {
  shareLoading.value = true;
  try {
    const res = await momentApi.update(detail.value.id, {
      shareAction: 'open',
      shareDuration: shareDuration.value,
    });
    // 响应拦截器已解包 response.data → res 就是 body
    if (res.share) {
      shareResult.value = res.share;
      detail.value.visible_type = { vt: 1, vs: detail.value.visible_type?.vs || 1, pw: res.share.password };
      showSharePopup.value = false;
      showShareResult.value = true;
    }
  } catch {
    showToast('开启分享失败');
  } finally {
    shareLoading.value = false;
  }
};

const doCloseShare = async () => {
  shareLoading.value = true;
  try {
    await momentApi.update(detail.value.id, {
      shareAction: 'close',
    });
    detail.value.visible_type = { vt: 0, vs: detail.value.visible_type?.vs || 0, pw: 0 };
    showSharePopup.value = false;
    showToast('分享已关闭');
  } catch (err) {
    showToast('关闭分享失败');
  } finally {
    shareLoading.value = false;
  }
};

// 已分享状态下重新复制
const copyExistingLink = async (type) => {
  if (type === 'token') {
    // 每次请求后端生成新 token（不改变密码），前端自行拼接域名
    try {
      showToast('正在生成链接...');
      const res = await momentApi.update(detail.value.id, {
        shareAction: 'token',
        shareDuration: 1,
      });
      if (res.share?.token) {
        const url = `${ENV.SITE_URL}/share/diary/detail?token=${encodeURIComponent(res.share.token)}`;
        copyToClipboard(url, '公共链接已复制');
      } else {
        showToast(res.message || '生成失败');
      }
    } catch {
      showToast('生成链接失败');
    }
  } else {
    const url = `${ENV.SITE_URL}/share/diary/detail?id=${detail.value.id}`;
    const pw = detail.value?.visible_type?.pw || '***';
    copyToClipboard(`链接：${url}\n密码：${pw}`, '密码链接+密码已复制');
  }
};

const copyToClipboard = async (text, msg) => {
  try {
    await navigator.clipboard.writeText(text);
    showToast(msg);
  } catch {
    const el = document.createElement('textarea');
    el.value = text;
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
    showToast('已复制');
  }
};

const copyShareLink = (type) => {
  const text = type === 'token'
    ? `${ENV.SITE_URL}/share/diary/detail?token=${encodeURIComponent(shareResult.value.token)}`
    : `链接：${ENV.SITE_URL}/share/diary/detail?id=${detail.value.id}\n密码：${shareResult.value.password}`;
  copyToClipboard(text, type === 'token' ? '公共链接已复制' : '密码链接+密码已复制');
};

onMounted(() => {
  loadDetail();
});
</script>

<style scoped>
.page-diary-detail {
  background: var(--theme-bg-primary);
  min-height: 100dvh;
  padding-bottom: 20px;
}

.loading-center {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

/* 主内容 */
.main-moment {
  background: var(--theme-bg-secondary);
  padding-bottom: 10px;
}
.user-header {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
}
.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.username {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
}
.time-location {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  display: flex;
  align-items: center;
}
.main-mood-tag {
  background: var(--theme-primary-light);
  color: var(--theme-primary);
  padding: 4px 10px;
  border-radius: 100px;
  font-size: 12px;
}
.content-text {
  padding: 0 16px 16px;
  font-size: 17px;
  line-height: 1.6;
  color: var(--theme-text-primary);
  word-break: break-word;
  overflow-wrap: break-word;
}

.content-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 8px 0;
}

.content-text :deep(p) {
  margin: 0 0 8px;
}

.content-text :deep(blockquote) {
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
.main-footer {
  padding: 0 16px 16px;
  display: flex;
  justify-content: flex-end;
}
.action-btns {
  display: flex;
  gap: 8px;
}
.share-btn {
  font-size: 13px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-tertiary);
  padding: 6px 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.delete-btn {
  font-size: 13px;
  color: var(--van-danger-color, #ee0a24);
  background: var(--van-danger-bg, #fff0f0);
  padding: 6px 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 正文补充（完全匹配你要的风格） */
.append-section {
  padding: 20px 16px;
}
.append-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.append-header .title {
  font-size: 17px;
  font-weight: 600;
  color: var(--theme-text-primary);
}
.append-header .count {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}
.loading-append {
  text-align: center;
  padding: 20px;
}
.empty-append {
  text-align: center;
  padding: 40px 0;
  color: var(--theme-text-secondary);
}

.append-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.append-item {
  display: flex;
  gap: 14px;
}

/* 时间轴线条 */
.append-line {
  width: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8px;
}
.dot {
  width: 8px;
  height: 8px;
  background: var(--theme-primary);
  border-radius: 50%;
  border: 2px solid var(--theme-bg-secondary);
  box-shadow: 0 0 0 2px var(--theme-bg-tertiary);
}
.line {
  width: 2px;
  background: var(--theme-bg-tertiary);
  flex: 1;
  margin-top: 4px;
}

/* 补充内容块 */
.append-content-wrap {
  flex: 1;
  background: var(--theme-bg-tertiary);
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 10px;
}
.append-content {
  font-size: 15px;
  line-height: 1.6;
  color: var(--theme-text-primary);
  margin-bottom: 10px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.append-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 6px 0;
}

.append-content :deep(p) {
  margin: 0 0 6px;
}

.append-content :deep(blockquote) {
  border-left: 3px solid var(--theme-primary);
  margin: 6px 0;
  padding: 3px 10px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-tertiary);
}
.append-image-list {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 4px;
  margin-bottom: 10px;
}
.append-grid-item {
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 6px;
}
.append-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--theme-text-tertiary);
}
.append-bottom-left {
  display: flex;
  align-items: center;
  gap: 4px;
}
.loc-text {
  color: var(--theme-text-tertiary);
}
.append-actions {
  display: flex;
  gap: 8px;
}
.append-actions .delete-btn {
  color: var(--van-danger-color, #ee0a24);
  padding: 2px 6px;
  background: var(--van-danger-bg, #fff0f0);
  border-radius: 4px;
}
.tags {
  display: flex;
  gap: 6px;
}
.tag {
  display: flex;
  align-items: center;
  gap: 2px;
}
.tag.mood {
  color: var(--theme-primary);
}
.tag.location {
  color: var(--van-green, #07c160);
}

/* ── 分享弹窗 ── */
.share-popup {
  text-align: center;
}

.share-title {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--theme-text-primary);
}

.share-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border: 1px solid var(--theme-border);
  font-size: 15px;
  color: var(--theme-text-primary);
}

.duration-value {
  color: var(--theme-primary);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pw-display {
  color: var(--van-green, #07c160);
  font-weight: 600;
  font-size: 18px;
  letter-spacing: 4px;
}

.share-hint {
  font-size: 12px;
  color: var(--van-orange, #ff976a);
  text-align: left;
  padding: 8px 0 0;
  margin: 0;
}

.share-btns {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.share-btns .van-button {
  flex: 1;
}

/* 分享结果弹窗 */
.share-result {
  text-align: center;
}

.result-pw {
  font-size: 15px;
  color: var(--theme-text-secondary);
  margin: 12px 0 20px;
}

.result-pw strong {
  color: var(--van-green, #07c160);
  font-size: 24px;
  letter-spacing: 6px;
}

.result-btns {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 悬浮按钮 */
.add-diary-btn {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 50px;
  height: 50px;
  background: var(--app-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
}
</style>
