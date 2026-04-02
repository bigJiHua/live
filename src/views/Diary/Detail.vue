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

        <div class="content-text">{{ detail.content }}</div>

        <div
          v-if="detail.img_url?.length"
          class="image-grid"
          :class="'grid-' + Math.min(detail.img_url.length, 3)"
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
          <div class="share-btn" @click="handleShare">
            <van-icon name="share-o" /> 分享给好友
          </div>
        </div>
      </div>

      <!-- 补充内容（正文续写 / 故事续篇） -->
      <div class="append-section">
        <div class="append-header">
          <span class="title">正文补充</span>
          <span class="count">{{ childrenCount }} 条补充</span>
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
              <div class="append-content">{{ child.content }}</div>

              <div v-if="child.img_url?.length" class="append-image-list">
                <van-image
                  v-for="(img, i) in child.img_url"
                  :key="i"
                  fit="cover"
                  radius="6"
                  width="64"
                  height="64"
                  :src="img.url"
                  @click="previewChildImage(child, i)"
                />
              </div>

              <div class="append-bottom">
                <span class="time">{{ formatTime(child.create_time) }}</span>
                <div class="tags">
                  <span v-if="child.mood" class="tag mood">
                    <van-icon name="smile-o" size="10" /> {{ child.mood }}
                  </span>
                  <span v-if="child.location?.name" class="tag location">
                    <van-icon name="location-o" size="10" />
                    {{ child.location.name }}
                  </span>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
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

// 基础 URL
const BASE_URL = "http://192.168.0.103:3001/api/public";

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

// 加载详情
const loadDetail = async () => {
  const id = route.params.id || route.query.id;
  if (!id) {
    showToast("参数错误");
    router.back();
    return;
  }

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

// 分享
const handleShare = () => {
  showToast("分享功能开发中");
};

onMounted(() => {
  loadDetail();
});
</script>

<style scoped>
.page-diary-detail {
  background: #fdfdfd;
  min-height: 100vh;
  padding-bottom: 20px;
}
.loading-center {
  padding: 60px 0;
  display: flex;
  justify-content: center;
}

/* 主内容 */
.main-moment {
  background: #fff;
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
  color: #222;
}
.time-location {
  font-size: 12px;
  color: #999;
  display: flex;
  align-items: center;
}
.main-mood-tag {
  background: #f3ebff;
  color: #7232dd;
  padding: 4px 10px;
  border-radius: 100px;
  font-size: 12px;
}
.content-text {
  padding: 0 16px 16px;
  font-size: 17px;
  line-height: 1.6;
  color: #2c3e50;
}
.image-grid {
  display: grid;
  gap: 4px;
  padding: 0 16px 16px;
}
.grid-1 {
  grid-template-columns: 1fr;
}
.grid-2 {
  grid-template-columns: 1fr 1fr;
}
.grid-3 {
  grid-template-columns: 1fr 1fr 1fr;
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
.share-btn {
  font-size: 13px;
  color: #666;
  background: #f5f5f5;
  padding: 6px 12px;
  border-radius: 4px;
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
  color: #333;
}
.append-header .count {
  font-size: 12px;
  color: #999;
}
.loading-append {
  text-align: center;
  padding: 20px;
}
.empty-append {
  text-align: center;
  padding: 40px 0;
  color: #ccc;
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
  background: #7232dd;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px #f3ebff;
}
.line {
  width: 2px;
  background: #f0f0f0;
  flex: 1;
  margin-top: 4px;
}

/* 补充内容块 */
.append-content-wrap {
  flex: 1;
  background: #f8f9fb;
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 10px;
}
.append-content {
  font-size: 15px;
  line-height: 1.6;
  color: #333;
  margin-bottom: 10px;
}
.append-image-list {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}
.append-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #999;
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
  color: #7232dd;
}
.tag.location {
  color: #07c160;
}
</style>
