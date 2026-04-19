<template>
  <div class="page-budget-list">
    <!-- 顶部统计 -->
    <div class="stats-summary">
      <div class="summary-main">
        <div class="summary-item">
          <span class="label">总预算</span>
          <span class="value primary">¥{{ formatAmount(totalBudget) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">总支出</span>
          <span class="value danger">¥{{ formatAmount(totalExpense) }}</span>
        </div>
        <div class="summary-item">
          <span class="label">剩余</span>
          <span class="value" :class="totalBalance >= 0 ? 'success' : 'danger'">
            ¥{{ formatAmount(totalBalance) }}
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
              <van-tag :type="getTypeColor(item.budget_type)" size="small">
                {{ getTypeName(item.budget_type) }}
              </van-tag>
              <van-tag :type="getCycleColor(item.cycle)" size="small" plain>
                {{ getCycleName(item.cycle) }}
              </van-tag>
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
          <van-tag type="danger" size="small">已超支</van-tag>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && filteredList.length === 0" description="暂无预算" />

    <van-loading v-if="loading" class="loading-center" vertical>加载中...</van-loading>

    <!-- 底部按钮 -->
    <div class="bottom-actions">
      <van-button type="primary" round block @click="goAdd">
        <van-icon name="plus" /> 登记预算
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { showToast } from "vant";
import { getBudgetList, getBudgetStats } from "@/utils/api/budget";

const router = useRouter();

const loading = ref(false);
const budgetList = ref([]);
const stats = ref([]);
const activeTab = ref("all");

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
  if (activeTab.value === "all") return budgetList.value;
  return budgetList.value.filter((item) => item.budget_type === activeTab.value);
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
    showToast("加载失败");
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
  background: #f7f8fa;
  padding-bottom: 100px;
}

/* 顶部统计 */
.stats-summary {
  background: linear-gradient(135deg, #1989fa, #1976d2);
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
  color: #ffd000;
}

.summary-item .value.success {
  color: #90f0c8;
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
  background: #fff;
  border-radius: 20px;
  font-size: 13px;
  color: #646566;
  white-space: nowrap;
  flex-shrink: 0;
}

.cycle-tab.active {
  background: linear-gradient(135deg, #1989fa, #1976d2);
  color: #fff;
}

/* 预算卡片 */
.budget-card {
  position: relative;
  background: #fff;
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

.card-cycle-bar.日 { background: #07c160; }
.card-cycle-bar.周 { background: #1989fa; }
.card-cycle-bar.月 { background: #ff976a; }
.card-cycle-bar.季 { background: #7232dd; }
.card-cycle-bar.年 { background: #ee0a24; }

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
  background: #fff;
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

.stat-icon.行 { background: linear-gradient(135deg, #1989fa, #1976d2); }
.stat-icon.买 { background: linear-gradient(135deg, #ee0a24, #ff6034); }
.stat-icon.吃 { background: linear-gradient(135deg, #ff976a, #ee0a24); }

.stat-info {
  flex: 1;
}

.stat-name {
  font-size: 12px;
  color: #969799;
  margin-bottom: 2px;
}

.stat-amount {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
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
  stroke: #f0f0f0;
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
  color: #323233;
}

/* 预算卡片 */
.budget-list {
  padding: 0 12px;
}

.budget-card {
  background: #fff;
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
  color: #323233;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-route {
  font-size: 12px;
  color: #646566;
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
  color: #969799;
}

.card-right {
  text-align: right;
}

.card-budget {
  font-size: 16px;
  font-weight: 700;
  color: #323233;
  margin-bottom: 2px;
}

.card-remaining {
  font-size: 12px;
}

.card-remaining.positive {
  color: #07c160;
}

.card-remaining.negative {
  color: #ee0a24;
}

.progress-section {
  margin-bottom: 4px;
}

.progress-bar {
  height: 4px;
  background: #ebedf0;
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #1989fa, #07c160);
  border-radius: 2px;
  transition: width 0.3s;
}

.progress-fill.over {
  background: linear-gradient(90deg, #ee0a24, #ff6034);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #969799;
  margin-top: 4px;
}

.progress-percent {
  color: #1989fa;
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
