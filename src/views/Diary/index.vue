<template>
  <div class="page-diary">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <!-- 加载中 -->
      <van-loading v-if="loading && !refreshing" class="loading-center" />

      <!-- 空状态 -->
      <van-empty
        v-else-if="!loading && diaryList.length === 0"
        description="还没有动态，快来发布第一条吧"
        image="search"
      />

      <!-- 瀑布流列表 -->
      <div v-else class="waterfall-container">
        <div class="waterfall-column">
          <DiaryCard v-for="item in leftList" :key="item.id" :data="item" />
        </div>
        <div class="waterfall-column">
          <DiaryCard v-for="item in rightList" :key="item.id" :data="item" />
        </div>
      </div>

      <!-- 加载更多 -->
      <div class="load-more">
        <van-loading v-if="loadingMore">加载中...</van-loading>
        <span v-else-if="noMore">没有更多了</span>
        <span v-else @click="loadMore">加载更多</span>
      </div>

      <van-back-top bottom="100px" />
      <div style="height: 100px"></div>
    </van-pull-refresh>

    <div class="add-diary-btn" @click="$router.push('/diary/add')">
      <van-icon name="plus" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import DiaryCard from '@/components/Diary/DiaryCard.vue'
import { momentApi } from '@/utils/api/moment'

const refreshing = ref(false)
const loading = ref(false)
const diaryList = ref([])

// 分页
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const totalPages = ref(0);
const loadingMore = ref(false);
const noMore = computed(() => diaryList.value.length >= total.value);

// 基础 URL
const BASE_URL = "http://192.168.0.103:3001/api/public";
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  return BASE_URL + path;
};

// 处理图片 URL
const processImageUrl = (item) => {
  if (item.img_url && Array.isArray(item.img_url)) {
    item.img_url = item.img_url.map((img) => ({
      url: getFullUrl(img.url || img),
      thumbnail: getFullUrl(img.thumbnail || img.url || img),
    }));
  }
  return item;
};

// 加载动态列表
const loadList = async (append = false) => {
  if (append) {
    loadingMore.value = true;
  } else {
    loading.value = true;
    currentPage.value = 1;
  }

  try {
    const res = await momentApi.list({
      page: currentPage.value,
      pageSize: pageSize.value,
    });

    const list = res.data?.list || res.data || [];

    // 更新分页信息
    if (res.data?.total !== undefined) {
      total.value = res.data.total;
      totalPages.value = res.data.totalPages || Math.ceil(res.data.total / pageSize.value);
    }

    if (append) {
      diaryList.value = [...diaryList.value, ...list.map(processImageUrl)];
    } else {
      diaryList.value = list.map(processImageUrl);
    }
  } catch (err) {
    console.error("加载动态列表失败:", err);
    showToast("加载失败");
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

// 加载更多
const loadMore = () => {
  if (noMore.value || loadingMore.value) return;
  currentPage.value++;
  loadList(true);
};

// 瀑布流分流
const leftList = computed(() => diaryList.value.filter((_, i) => i % 2 === 0));
const rightList = computed(() => diaryList.value.filter((_, i) => i % 2 === 1));

// 下拉刷新
const onRefresh = async () => {
  try {
    await loadList(false);
  } finally {
    refreshing.value = false;
  }
};

onMounted(() => {
  loadList(false);
});
</script>

<style scoped>
.page-diary {
  padding: 8px;
  background-color: #f4f4f5;
  min-height: 100vh;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.waterfall-container {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.waterfall-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 加载更多 */
.load-more {
  text-align: center;
  padding: 16px;
  color: #969799;
  font-size: 13px;
}

/* 悬浮按钮保持原样 */
.add-diary-btn {
  position: fixed;
  bottom: 80px;
  right: 20px; /* 瀑布流布局适合放在右侧 */
  width: 50px;
  height: 50px;
  background: var(--app-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  z-index: 100;
}
</style>