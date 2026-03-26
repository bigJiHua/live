<template>
  <div class="diary-item-card" @click="handleDetail">
    <div v-if="data.image" class="card-cover">
      <van-image :src="data.image" lazy-load fit="cover" />
      <div class="floating-rate">
        <van-icon name="star" color="#ffd21e" /> 
        <span>{{ data.star }}</span>
      </div>
    </div>

    <div class="card-content">
      <div class="main-text">{{ data.content }}</div>

      <div class="interaction-bar">
        <div v-if="data.comments > 0" class="sub-record-tag">
          <van-icon name="chat-o" /> {{ data.comments }} 笔追文
        </div>
        <div v-else class="star-static" v-if="!data.image">
          <van-rate v-model="data.star" readonly size="8px" color="#ffd21e" />
        </div>
      </div>

      <div class="user-meta">
        <div class="author-info">
          <van-image round width="14" height="14" :src="data.author.avatar" />
          <span class="name">{{ data.author.name }}</span>
        </div>
        <span class="time">{{ data.time }}</span>
      </div>

      <div class="location-footer">
        <van-icon name="location-o" />
        <span>{{ data.location }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps(['data'])
const handleDetail = () => { /* 仅跳转，不在此页展示评论 */ }
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