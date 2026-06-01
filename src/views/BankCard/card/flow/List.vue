<template>
  <div class="page-card-flow">
    <van-nav-bar
      title="卡片流水"
      left-arrow
      @click-left="onClickLeft"
    />

    <!-- 日期筛选 -->
    <van-cell-group inset class="filter-group">
      <van-cell title="选择月份" is-link @click="showMonthPicker = true">
        <template #value>
          <span>{{ currentMonth }}</span>
        </template>
      </van-cell>
    </van-cell-group>

    <!-- 统计概览 -->
    <van-cell-group inset class="stats-group">
      <van-cell>
        <div class="stats-row">
          <div class="stat-item">
            <div class="stat-label">支出</div>
            <div class="stat-value text-expense">{{ formatMoney(stats.expense) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">收入</div>
            <div class="stat-value text-income">{{ formatMoney(stats.income) }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">笔数</div>
            <div class="stat-value">{{ list.length }}</div>
          </div>
        </div>
      </van-cell>
    </van-cell-group>

    <!-- 流水列表 -->
    <van-cell-group inset>
      <template v-if="loading">
        <van-skeleton :row="3" v-for="i in 5" :key="i" class="skeleton-item" />
      </template>
      <template v-else-if="list.length === 0">
        <van-empty description="暂无流水记录" />
      </template>
      <template v-else>
        <van-cell
          v-for="item in list"
          :key="item.id"
          :title="item.category_name || item.pay_type || '未知'"
          :label="item.trans_date"
          clickable
          @click="goDetail(item)"
        >
          <template #value>
            <span
              class="num-font"
              :class="item.direction === 1 ? 'text-income' : 'text-expense'"
            >
              {{ item.direction === 1 ? '+' : '-' }}{{ formatMoney(item.amount) }}
            </span>
          </template>
          <template #right-icon>
            <van-icon name="arrow" class="arrow-icon" />
          </template>
        </van-cell>
      </template>
    </van-cell-group>

    <!-- 月份选择器 -->
    <van-popup v-model:show="showMonthPicker" position="bottom">
      <van-picker
        :columns="monthColumns"
        @confirm="onMonthConfirm"
        @cancel="showMonthPicker = false"
      />
    </van-popup>

    <!-- 加载更多 -->
    <div class="load-more" v-if="list.length > 0 && hasMore">
      <van-button size="small" :loading="loadingMore" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <div style="height: 20px"></div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, onDeactivated, nextTick } from 'vue'
defineOptions({ name: 'CardFlowList' })
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getAccountListByCard } from '@/utils/api/account'
import { useFlowSyncStore } from '@/stores/flowSync'

const router = useRouter()
const route = useRoute()
const flowSync = useFlowSyncStore()

// 从路由或 URL 获取 cardId
const cardId = computed(() => route.query.cardId || '')
const cardAlias = computed(() => route.query.alias || '卡片流水')

// 月份选择（URL 参数记忆，无参数默认当月）
const currentDate = new Date()
const defaultMonth = `${currentDate.getFullYear()}-${String(currentDate.getMonth() + 1).padStart(2, '0')}`
const currentMonth = ref(route.query.month || defaultMonth)
const showMonthPicker = ref(false)

// 生成月份列
const monthColumns = computed(() => {
  const columns = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    columns.push({
      text: `${d.getFullYear()}年${d.getMonth() + 1}月`,
      value: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    })
  }
  return columns
})

// 数据
const loading = ref(false)
const loadingMore = ref(false)
const list = ref([])
const page = ref(1)
const limit = ref(20)
const hasMore = ref(false)
const savedScrollY = ref(0)

// 统计数据
const stats = reactive({
  expense: 0,
  income: 0
})

// 格式化金额
const formatMoney = (val) => {
  return Number(val || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// 获取月份范围
const getMonthRange = (monthStr) => {
  const [year, month] = monthStr.split('-').map(Number)
  const startDate = `${year}-${String(month).padStart(2, '0')}-01`
  const lastDay = new Date(year, month, 0).getDate()
  const endDate = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  return { startDate, endDate }
}

// 加载流水列表
const loadList = async (reset = false) => {
  if (!cardId.value) {
    showToast('缺少卡片ID')
    return
  }

  if (reset) {
    page.value = 1
    list.value = []
    stats.expense = 0
    stats.income = 0
    loading.value = true
  } else {
    loadingMore.value = true
  }

  try {
    const { startDate, endDate } = getMonthRange(currentMonth.value)

    const res = await getAccountListByCard({
      cardId: cardId.value,
      startDate,
      endDate,
      page: page.value,
      limit: limit.value
    })

    const data = res.data || res
    const items = data.list || []

    if (reset) {
      list.value = items
    } else {
      list.value = [...list.value, ...items]
    }

    // 统计
    items.forEach(item => {
      if (item.direction === 0) {
        stats.expense += Number(item.amount || 0)
      } else {
        stats.income += Number(item.amount || 0)
      }
    })

    // 分页
    const pagination = data.pagination || {}
    hasMore.value = list.value.length < (pagination.total || 0)

  } catch (e) {
    console.error('加载失败', e)
    showToast('加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// 加载更多
const loadMore = () => {
  page.value++
  loadList(false)
}

// 月份选择确认
const onMonthConfirm = ({ selectedOptions }) => {
  currentMonth.value = selectedOptions[0].value
  showMonthPicker.value = false
  router.replace({ query: { ...route.query, month: currentMonth.value } })
  loadList(true)
}

// 返回
const onClickLeft = () => {
  router.back()
}

// 查看详情
const goDetail = (item) => {
  router.push(`/finance/flow/${item.id}`)
}

onMounted(() => {
  if (cardId.value) {
    loadList(true)
  } else {
    showToast('缺少卡片ID')
  }
})

// keep-alive 激活时：URL 有参数就恢复，无参数就重置为当前月
onActivated(() => {
  if (route.query.month) {
    if (route.query.month !== currentMonth.value) {
      currentMonth.value = route.query.month
      loadList(true)
    }
  } else {
    const d = new Date()
    const def = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    if (def !== currentMonth.value) {
      currentMonth.value = def
      loadList(true)
    }
  }

  // 同步 Detail 变更（原地 patch）
  const changes = flowSync.consumeChanges();
  const ids = Object.keys(changes);
  if (ids.length > 0) {
    list.value.forEach(item => {
      const patch = changes[item.id];
      if (patch) Object.assign(item, patch);
    });
  }

  // 恢复滚动位置
  nextTick(() => {
    if (savedScrollY.value > 0) window.scrollTo({ top: savedScrollY.value, behavior: 'instant' })
  })
})

onDeactivated(() => {
  savedScrollY.value = window.scrollY || document.documentElement.scrollTop
})
</script>

<style scoped>
.page-card-flow {
  min-height: 100vh;
  background: #f7f8fa;
}

.filter-group {
  margin-top: 12px;
}

.stats-group {
  margin-top: 12px;
}

.stats-row {
  display: flex;
  justify-content: space-around;
  width: 100%;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
}

.text-income { color: #07c160; }
.text-expense { color: #ee0a24; }

.skeleton-item {
  padding: 16px;
}

.arrow-icon {
  color: #969799;
  margin-left: 8px;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 16px;
}
</style>
