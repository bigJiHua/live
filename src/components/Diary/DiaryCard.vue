<template>
  <div class="diary-item-card" @click="handleDetail">
    <!-- 封面图 - 支持多图和单图 -->
    <div v-if="coverImage" class="card-cover">
      <van-image :src="coverImage" lazy-load fit="cover" />
      <div v-if="imageCount > 1" class="image-count">
        <van-icon name="photo-o" /> {{ imageCount }}
      </div>
    </div>

    <div class="card-content">
      <div class="main-text">{{ data.content }}...</div>

      <div class="interaction-bar">
        <!-- 心情标签 -->
        <div v-if="data.mood" class="mood-tag">
          {{ data.mood }}
        </div>
        <!-- 追文数量 -->
        <div v-if="childrenCount > 0" class="sub-record-tag">
          <van-icon name="chat-o" /> {{ childrenCount }} 笔追文
        </div>
        <!-- 无图时的评分 -->
        <div v-else-if="!coverImage" class="star-static">
          <van-rate :model-value="starValue" readonly size="8px" color="#ffd21e" />
        </div>
      </div>

      <div class="user-meta">
        <div class="author-info">
          <van-image round width="14" height="14" :src="avatarUrl" />
          <span class="name">{{ authorName }}</span>
        </div>
        <div class="time-wrap">
          <span class="time">{{ displayTime }}</span>
          <span v-if="displayDate" class="date">{{ displayDate }}</span>
        </div>
      </div>

      <div class="location-footer" v-if="locationName">
        <van-icon name="location-o" />
        <span>{{ locationName }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";

const props = defineProps(["data"]);
const router = useRouter();
const userStore = useUserStore();

// 封面图 - 优先取 img_url 数组第一张
const coverImage = computed(() => {
  if (props.data.img_url && props.data.img_url.length > 0) {
    return props.data.img_url[0].url;
  }
  return props.data.image || "";
});

// 图片数量
const imageCount = computed(() => {
  if (props.data.img_url) return props.data.img_url.length;
  return props.data.image ? 1 : 0;
});

// 追文数量
const childrenCount = computed(() => {
  return props.data.children?.length || props.data.comments || 0;
});

// 评分
const starValue = computed(() => {
  return props.data.star || 5;
});

// 作者信息 - 优先从 store 获取当前用户信息
const authorName = computed(() => {
  // 如果有后端返回的用户信息用后端的，否则用 store 里的
  return props.data.author?.name || props.data.user_name || userStore.username || "用户";
});

const avatarUrl = computed(() => {
  // 如果有后端返回的头像用后端的，否则用 store 里的
  const storeAvatar = userStore.actualAvatar;
  return props.data.author?.avatar || props.data.avatar || storeAvatar;
});

// 时间显示
const displayTime = computed(() => {
  if (props.data.time) return props.data.time;
  if (props.data.created_at) {
    const date = new Date(props.data.created_at);
    return `${date.getHours().toString().padStart(2, "0")}:${date.getMinutes().toString().padStart(2, "0")}`;
  }
  if (props.data.create_time) {
    const date = new Date(parseInt(props.data.create_time));
    return `${date.getHours().toString().padStart(2, "0")}:${date.getMinutes().toString().padStart(2, "0")}`;
  }
  return "";
});

// 日期显示
const displayDate = computed(() => {
  if (props.data.date) return props.data.date;
  if (props.data.create_time) {
    const date = new Date(parseInt(props.data.create_time));
    const now = new Date();
    const todayStr = `${now.getMonth() + 1}-${now.getDate()}`;
    const dateStr = `${date.getMonth() + 1}-${date.getDate()}`;
    if (todayStr === dateStr) return "今天";
    return dateStr;
  }
  return "";
});

// 位置名称
const locationName = computed(() => {
  if (props.data.location?.name) return props.data.location.name;
  return props.data.location || "";
});

const handleDetail = () => {
  router.push({ name: "DiaryDetail", query: { id: props.data.id } });
};
</script>

<style scoped>
.diary-item-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
  margin-bottom: 4px;
  animation: slideUp 0.4s ease-out;
  border: 0.5px solid #f0f0f0;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(15px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 封面图样式 */
.card-cover {
  position: relative;
  width: 100%;
  max-height: 200px;
  overflow: hidden;
}

.floating-rate {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 10px;
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 多图数量标记 */
.image-count {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.5);
  color: white;
  padding: 2px 6px;
  border-radius: 8px;
  font-size: 10px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.card-content {
  padding: 10px;
}

/* 正文显示两行 */
.main-text {
  font-size: 13px;
  color: #2c3e50;
  line-height: 1.45;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-weight: 500;
}

/* 追文标记 */
.interaction-bar {
  margin-bottom: 10px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sub-record-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: var(--app-primary);
  background: #f0f9eb;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 心情标签 */
.mood-tag {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  color: #7232dd;
  background: #f2e8ff;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 用户与时间 */
.user-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #f7f8fa;
  margin-bottom: 6px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 5px;
}

.author-info .name {
  font-size: 11px;
  color: #666;
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta .time {
  font-size: 10px;
  color: #999;
}

.time-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.time-wrap .date {
  font-size: 9px;
  color: #07c160;
  background: #f0f9eb;
  padding: 1px 4px;
  border-radius: 3px;
}

/* 定位脚部 */
.location-footer {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  color: #bdc3c7;
}

.diary-item-card:active {
  transform: scale(0.97);
  background-color: #fafafa;
}
</style>