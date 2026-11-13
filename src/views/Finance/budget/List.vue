<template>
  <div class="page-budget-list">
    <!-- 顶部统计 -->
    <div class="stats-summary">
      <div class="summary-main">
        <div class="summary-item">
          <span class="label">总预算</span>
          <span class="value primary">¥{{ compactAmount(totalBudget) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">总支出</span>
          <span class="value danger">¥{{ compactAmount(totalExpense) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">剩余</span>
          <span class="value" :class="totalBalance >= 0 ? 'success' : 'danger'">
            ¥{{ compactAmount(totalBalance) }}
          </span>
        </div>
      </div>
      <div class="summary-progress">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: totalProgress + '%' }"></div>
        </div>
        <span class="progress-text">整体进度 {{ totalProgress }}%</span>
      </div>
    </div>

    <!-- Tab切换 -->
    <van-tabs v-model:active="activeTab" sticky>
      <van-tab title="全部" name="all"></van-tab>
      <van-tab title="出行" name="行"></van-tab>
      <van-tab title="购物" name="买"></van-tab>
      <van-tab title="餐饮" name="吃"></van-tab>
    </van-tabs>

    <!-- 本地搜索：纯前端过滤当前列表（非联网），关键词匹配预算标题（含年月/类型） -->
    <van-search
      v-model="searchKeyword"
      class="budget-search"
      shape="round"
      placeholder="搜索预算（如 餐饮 / 2026-11）"
    />

    <!-- 预算列表 -->
    <div class="budget-list" v-if="filteredList.length > 0">
      <div
        v-for="item in filteredList"
        :key="item.id"
        class="budget-card"
        @click="goDetail(item)"
      >
        <div class="card-cycle-bar" :class="item.cycle"></div>
        <div class="card-main">
          <div class="card-left">
            <div class="card-title">{{ item.title }}</div>
            <div class="card-route" v-if="item.route">
              <van-icon name="location-o" /> {{ item.route }}
            </div>
            <div class="card-meta">
              <span class="meta-date">{{ item.plan_date }}</span>
              <app-tag :type="getTypeColor(item.budget_type)" size="small">
                {{ getTypeName(item.budget_type) }}
              </app-tag>
              <app-tag :type="getCycleColor(item.cycle)" size="small" plain>
                {{ getCycleName(item.cycle) }}
              </app-tag>
            </div>
          </div>
          <div class="card-right">
            <div class="card-budget">¥{{ formatAmount(item.budget_amount) }}</div>
            <div class="card-remaining" :class="getRemainingClass(item)">
              {{ getRemainingText(item) }}
            </div>
          </div>
        </div>

        <!-- 进度条 -->
        <div class="progress-section">
          <div class="progress-bar">
            <div
              class="progress-fill"
              :class="{ over: isOverBudget(item) }"
              :style="{ width: getProgress(item) + '%' }"
            ></div>
          </div>
          <div class="progress-info">
            <span class="progress-used">
              已 ¥{{ formatAmount(getExpense(item)) }}
            </span>
            <span class="progress-percent">
              {{ getProgress(item) }}%
            </span>
          </div>
        </div>

        <!-- 标签 -->
        <div class="card-tags" v-if="isOverBudget(item)">
          <app-tag type="danger" size="small">已超支</app-tag>
        </div>
      </div>
    </div>

    <van-empty
      v-if="!loading && filteredList.length === 0"
      :description="searchKeyword.trim() ? '未找到匹配的预算' : '暂无预算'"
    />

    <van-loading v-if="loading" class="loading-center" vertical>加载中...</van-loading>

    <!-- 底部按钮 -->
    <div class="bottom-actions">
      <app-button type="primary" round block @click="goAdd">
        <van-icon name="plus" /> 登记预算
      </app-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast, showFailToast } from '@/utils/toast';
import { getBudgetList, getBudgetStats } from "@/utils/api/budget";

const router = useRouter();

const loading = ref(false);
const budgetList = ref([]);
const stats = ref([]);
const activeTab = ref("all");
const searchKeyword = ref("");

// 根据类型计算支出
const calcExpense = (item) => {
  const details = item.budget_details || {};
  if (item.budget_type === "行") {
    const days = details.days || [];
    return days.reduce((sum, day) => {
      const items = day.items || [];
      return sum + items.reduce((s, i) => {
        if (i.currency === "CNY") return s + (parseFloat(i.amount) || 0);
        return s + (parseFloat(i.cny_amount) || 0);
      }, 0);
    }, 0);
  }
  if (item.budget_type === "买") {
    return parseFloat(details.actual_total) || 0;
  }
  if (item.budget_type === "吃") {
    return parseFloat(details.actual_total) || 0;
  }
  return parseFloat(item.used_amount) || 0;
};

const filteredList = computed(() => {
  let list =
    activeTab.value === "all"
      ? budgetList.value
      : budgetList.value.filter((item) => item.budget_type === activeTab.value);
  // 本地搜索：聚合多字段（标题/日期/类型中文名/周期中文名/金额/类型原值）
  // 不区分大小写包含匹配——标签显示「出行」而标题写「交通」这类不一致也能命中
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (keyword) {
    list = list.filter((item) => {
      const haystack = [
        item.title,
        item.plan_date,
        getTypeName(item.budget_type),
        getCycleName(item.cycle),
        item.budget_type,
        item.cycle,
        formatAmount(item.budget_amount),
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(keyword);
    });
  }
  return list;
});


const totalBudget = computed(() => {
  return budgetList.value.reduce(
    (sum, item) => sum + parseFloat(item.budget_amount || 0),
    0
  );
});

const totalExpense = computed(() => {
  return budgetList.value.reduce((sum, item) => sum + calcExpense(item), 0);
});

const totalBalance = computed(() => totalBudget.value - totalExpense.value);

const totalProgress = computed(() => {
  if (totalBudget.value === 0) return 0;
  return Math.min(100, Math.round((totalExpense.value / totalBudget.value) * 100));
});

const formatAmount = (amount) => (parseFloat(amount) || 0).toFixed(2);

// 金额计量单位折合：超过 9999.9999 折中文单位，长金额不再撑爆统计卡
// - < 1万：原样两位小数
// - 1万 ~ 1亿：折「万」（170200 → 17.02万、1702000 → 170.2万）——十万/百万/千万
//   量级统一在「万」档内表达（中文习惯说 170.2万 而非 1.702十万）
// - ≥ 1亿：折「亿」（170200000 → 1.702亿）；负值保留符号，3 位小数去尾零
const compactAmount = (amount) => {
  const n = parseFloat(amount) || 0;
  const abs = Math.abs(n);
  const sign = n < 0 ? "-" : "";
  if (abs < 10000) return `${sign}${formatAmount(n)}`;
  if (abs >= 1e8) return `${sign}${String(parseFloat((abs / 1e8).toFixed(3)))}亿`;
  return `${sign}${String(parseFloat((abs / 1e4).toFixed(3)))}万`;
};

const getTypeName = (type) => {
  const names = { 行: "出行", 买: "购物", 吃: "餐饮" };
  return names[type] || type;
};

const getTypeColor = (type) => {
  const colors = { 行: "primary", 买: "danger", 吃: "warning" };
  return colors[type] || "default";
};

const getCycleName = (cycle) => {
  const names = { 日: "日计划", 周: "周计划", 月: "月度", 季: "季度", 年: "年度" };
  return names[cycle] || cycle || "";
};

const getCycleColor = (cycle) => {
  const colors = { 日: "success", 周: "primary", 月: "warning", 季: "primary", 年: "purple" };
  return colors[cycle] || "default";
};

const getExpense = (item) => calcExpense(item);

const getProgress = (item) => {
  const budget = parseFloat(item.budget_amount) || 0;
  const used = calcExpense(item);
  if (budget === 0) return 0;
  return Math.min(100, Math.round((used / budget) * 100));
};

const isOverBudget = (item) => {
  const budget = parseFloat(item.budget_amount) || 0;
  return calcExpense(item) > budget;
};

const getRemainingClass = (item) => {
  const remaining = parseFloat(item.budget_amount || 0) - calcExpense(item);
  return remaining >= 0 ? "positive" : "negative";
};

const getRemainingText = (item) => {
  const remaining = parseFloat(item.budget_amount || 0) - calcExpense(item);
  if (remaining >= 0) return `剩余 ¥${formatAmount(remaining)}`;
  return `超支 ¥${formatAmount(Math.abs(remaining))}`;
};

const loadData = async () => {
  loading.value = true;
  try {
    const [listRes, statsRes] = await Promise.all([
      getBudgetList(),
      getBudgetStats(),
    ]);
    budgetList.value = listRes.data || [];
    stats.value = statsRes.data || [];
  } catch (e) {
    showFailToast("加载失败");
  } finally {
    loading.value = false;
  }
};

const goDetail = (item) => {
  router.push(`/finance/budget/detail/${item.id}`);
};

const goAdd = () => {
  router.push("/finance/budget/type-select");
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.page-budget-list {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 100px;
}

/* 顶部统计 */
.stats-summary {
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad));
  margin: 12px;
  border-radius: 16px;
  padding: 16px;
  color: #fff;
}

.summary-main {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.summary-item {
  text-align: center;
  flex: 1;
}

.summary-item .label {
  display: block;
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.summary-item .value {
  font-size: 18px;
  font-weight: 700;
}

.summary-item .value.danger {
  color: rgba(255, 255, 255, 0.95);
}

.summary-item .value.success {
  /* 统计卡是主题色渐变背景：剩余为正时用白色（绿底配浅绿字颜色不正、对比度差） */
  color: #fff;
}

.summary-progress {
  margin-top: 4px;
}

.progress-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}

.progress-fill {
  height: 100%;
  background: #90f0c8;
  border-radius: 3px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 11px;
  opacity: 0.85;
}

/* 周期筛选 */
.cycle-filter {
  display: flex;
  gap: 8px;
  padding: 12px 12px 8px;
  overflow-x: auto;
}

.cycle-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: var(--theme-bg-secondary);
  border-radius: 20px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  flex-shrink: 0;
}

.cycle-tab.active {
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad));
  color: #fff;
}

/* 预算卡片 */
.budget-card {
  position: relative;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 14px;
  margin: 0 12px 10px;
  cursor: pointer;
  overflow: hidden;
}

.card-cycle-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

.card-cycle-bar.日 { background: var(--van-green, #07c160); }
.card-cycle-bar.周 { background: var(--theme-primary); }
.card-cycle-bar.月 { background: var(--van-orange, #ff976a); }
.card-cycle-bar.季 { background: var(--van-purple, #7232dd); }
.card-cycle-bar.年 { background: var(--van-danger-color, #ee0a24); }

/* 统计卡片 */
.stats-cards {
  display: flex;
  gap: 8px;
  padding: 0 12px;
  overflow-x: auto;
  margin-bottom: 12px;
}

.stat-card {
  flex: 0 0 auto;
  min-width: 110px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #fff;
}

.stat-icon.行 { background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad)); }
.stat-icon.买 { background: linear-gradient(135deg, var(--van-danger-color, #ee0a24), var(--van-danger-grad, #ff6034)); }
.stat-icon.吃 { background: linear-gradient(135deg, var(--van-orange, #ff976a), var(--van-danger-color, #ee0a24)); }

.stat-info {
  flex: 1;
}

.stat-name {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-bottom: 2px;
}

.stat-amount {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.stat-progress {
  width: 44px;
  height: 44px;
}

.progress-ring {
  position: relative;
  width: 44px;
  height: 44px;
}

.progress-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: var(--theme-bg-tertiary);
  stroke-width: 3;
}

.ring-fill {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  transition: stroke-dasharray 0.3s;
}

.ring-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 9px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

/* 预算卡片 */
.budget-list {
  padding: 0 12px 60px;
  /* 固定高度、盒子内滚动：统计卡+Tabs+搜索框固定在上方，长列表不再撑开整页；
     扣减含顶部三块(~228px)与底部 fixed Tabbar(~50px)，padding-bottom 兜底最后一张卡不被遮挡 */
  max-height: calc(100dvh - 300px);
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

/* 本地搜索框：背景跟随主题（Vant 默认白底在暗色模式下突兀） */
.budget-search {
  background: var(--theme-bg-primary);
}

.budget-card {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 10px;
  cursor: pointer;
}

.card-main {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-left {
  flex: 1;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-route {
  font-size: 12px;
  color: var(--theme-text-secondary);
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 6px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.meta-date {
  font-size: 11px;
  color: var(--theme-text-tertiary);
}

.card-right {
  text-align: right;
}

.card-budget {
  font-size: 16px;
  font-weight: 700;
  color: var(--theme-text-primary);
  margin-bottom: 2px;
}

.card-remaining {
  font-size: 12px;
}

.card-remaining.positive {
  color: var(--van-green);
}

.card-remaining.negative {
  color: var(--van-danger-color);
}

.progress-section {
  margin-bottom: 4px;
}

.progress-bar {
  height: 4px;
  background: var(--theme-bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--theme-primary), var(--van-green));
  border-radius: 2px;
  transition: width 0.3s;
}

.progress-fill.over {
  background: linear-gradient(90deg, var(--van-danger-color, #ee0a24), #ff6034);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 4px;
}

.progress-percent {
  color: var(--theme-primary);
  font-weight: 600;
}

.card-tags {
  margin-top: 8px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.bottom-actions {
  position: fixed;
  bottom: 20px;
  left: 16px;
  right: 16px;
}
</style>
