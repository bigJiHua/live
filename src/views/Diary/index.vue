<template>
  <div class="page-diary">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="waterfall-container">
        <div class="waterfall-column">
          <DiaryCard v-for="item in leftList" :key="item.id" :data="item" />
        </div>
        <div class="waterfall-column">
          <DiaryCard v-for="item in rightList" :key="item.id" :data="item" />
        </div>
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
import { ref, onMounted } from 'vue'
import DiaryCard from '../../components/Diary/DiaryCard.vue' // 抽离组件保持代码整洁

const refreshing = ref(false)
const leftList = ref([])
const rightList = ref([])

// 模拟数据 (包含大纲要求的：评分、追文、图文、定位)
const mockData = [
  {
    id: 1,
    author: { name: 'Gemini', avatar: 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg' },
    content: '今天把系统架构调整成了瀑布流模式。这种错落有致的感觉真不错！✨',
    image: 'https://fastly.jsdelivr.net/npm/@vant/assets/apple-1.jpeg',
    time: '14:20',
    date: '03-24',
    location: '新宿区',
    star: 5,
    comments: 2, // 追文数量
    lastComment: '架构调整后顺滑多了！' // 最新追文预览
  },
  {
    id: 2,
    author: { name: 'Gemini', avatar: 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg' },
    content: '纯文字测试。记录一下今天的心情，由于理财收益达标，压力值减小。五星好评的一天！',
    image: '',
    time: '10:00',
    date: '03-24',
    location: '涩谷',
    star: 4,
    comments: 0
  },
  {
    id: 3,
    author: { name: 'Gemini', avatar: 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg' },
    content: '晚餐打卡。这家的猪脚饭虽然贵，但是工作成本核算下来还能接受。',
    image: 'https://fastly.jsdelivr.net/npm/@vant/assets/apple-2.jpeg',
    time: '20:15',
    date: '03-23',
    location: '银座',
    star: 3,
    comments: 1,
    lastComment: '下次去试试隔壁那家。'
  }
]

// 简单的瀑布流分流算法
const initWaterfall = (data) => {
  data.forEach((item, index) => {
    if (index % 2 === 0) leftList.value.push(item)
    else rightList.value.push(item)
  })
}

const onRefresh = () => {
  setTimeout(() => {
    refreshing.value = false
  }, 1000)
}

onMounted(() => {
  initWaterfall(mockData)
})
</script>

<style scoped>
.page-diary {
  padding: 8px;
  background-color: #f4f4f4;
  min-height: 100vh;
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