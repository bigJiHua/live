<template>
  <div class="page-assets-list">
    <!-- 总资产走势快速预览（略缩版） -->
    <div class="mini-trend" v-if="miniTrend.length > 0">
      <div class="mini-trend-head">
        <span class="mini-trend-title">总资产走势</span>
        <span class="mini-trend-latest">最新 ¥{{ formatAmount(miniTrend[miniTrend.length - 1].value) }}</span>
        <button class="mini-trend-btn" @click="goToTrend">
          <van-icon name="chart-trending-o" />
          <span>查看趋势</span>
        </button>
      </div>
      <div ref="miniChartRef" class="mini-chart"></div>
    </div>

    <!-- 登记列表 -->
    <div class="record-list" v-if="list.length > 0">
      <div v-for="(item, index) in list" :key="item.id" class="record-card">
        <div class="record-header">
          <div class="record-date">
            <p>
              <van-icon name="clock-o" />
              合计日期：<span>{{ formatDate(item.create_time) }}</span>
            </p>
            <p>
              <van-icon name="clock-o" />
              最后更新日期：<span>{{ formatDate(item.update_time) }}</span>
            </p>
          </div>
          <app-tag :type="index === 0 ? 'success' : 'default'">
            {{ index === 0 ? "最新" : "历史" }}
          </app-tag>
        </div>

        <div class="record-body">
          <div class="balance-display">
            <span class="label">总资产</span>
            <span class="value">¥{{ formatAmount(item.total_balance) }}</span>
          </div>
          <div class="balance-detail">
            <div class="detail-row">
              <span class="detail-label">资产合计</span>
              <span class="detail-value"
                >¥{{ formatAmount(item.total_asset) }}</span
              >
            </div>
            <div class="detail-row">
              <span class="detail-label">信用卡欠款</span>
              <span class="detail-value danger"
                >-¥{{ formatAmount(item.credit_debt) }}</span
              >
            </div>
          </div>
        </div>

        <!-- 展开资产明细 -->
        <van-collapse v-model="activeNames[index]">
          <van-collapse-item :name="index" title="查看明细" icon="orders-o">
            <div class="detail-content">
              <!-- 境内资产 -->
              <template v-if="item.asset_details?.balance && item.asset_details.balance.length > 0">
                <div class="detail-section">
                  <div class="region-header region-domestic">
                    <span class="region-title">境内资产</span>
                    <span class="region-total">总计 ¥{{ formatAmount(balanceTotal(item)) }}</span>
                  </div>
                  <div class="section-items">
                    <div
                      v-for="(val, idx) in item.asset_details.balance"
                      :key="val.id || idx"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getBalanceName(val.type || (typeof idx === 'string' ? idx : '')) }}</span>
                      <span>¥{{ formatAmount(val.amount) }}</span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 境外资产 -->
              <template v-if="item.asset_details?.offshore && item.asset_details.offshore.length > 0">
                <div class="detail-section">
                  <div class="region-header region-offshore">
                    <span class="region-title">境外资产</span>
                    <span class="region-total">折合 ¥{{ formatAmount(offshoreTotal(item)) }}</span>
                  </div>
                  <div class="section-items">
                    <div
                      v-for="(val, idx) in item.asset_details.offshore"
                      :key="val.id || idx"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getOffshoreName(val.type || (typeof idx === 'string' ? idx : '')) }}</span>
                      <span class="offshore-amount"
                        >{{ val.currency || "" }}
                        {{ formatAmount(val.amount) }}
                        <span
                          v-if="hasValidRate(item.asset_details.exchangeRates, val.currency)"
                          class="convert"
                          >≈ ¥{{
                            formatAmount(
                              convertToCNY(
                                item.asset_details.exchangeRates,
                                val.amount,
                                val.currency
                              )
                            )
                          }}</span
                        ></span
                      >
                    </div>
                  </div>
                </div>
              </template>

              <!-- 信用卡欠款 -->
              <template v-if="item.asset_details?.debt && item.asset_details.debt.length > 0">
                <div class="detail-section">
                  <div class="region-header region-debt">
                    <span class="region-title">信用卡欠款</span>
                    <span class="region-total danger">欠款 ¥{{ formatAmount(debtTotal(item)) }}</span>
                  </div>
                  <div class="section-items">
                    <div
                      v-for="(val, idx) in item.asset_details.debt"
                      :key="val.id || idx"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getDebtName(val.type || (typeof idx === 'string' ? idx : '')) }}</span>
                      <span class="danger"
                        >¥{{ formatAmount(val.amount) }}</span
                      >
                    </div>
                  </div>
                </div>
              </template>

              <!-- 汇率明细 -->
              <template
                v-if="
                  item.asset_details?.exchangeRates &&
                  Object.keys(item.asset_details.exchangeRates).length > 0
                "
              >
                <div class="detail-section">
                  <div class="section-label">登记汇率（100外币 = ? 人民币）</div>
                  <div class="section-items">
                    <div
                      v-for="(rate, currency) in item.asset_details.exchangeRates"
                      :key="currency"
                      class="detail-item"
                    >
                      <span>{{ currency }}</span>
                      <span>100 {{ currency }} ≈ ¥{{ formatAmount(rate) }}</span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 备注 -->
              <div class="remark-section" v-if="item.remark">
                <span class="remark-label">备注：</span>
                <span class="remark-value">{{ item.remark }}</span>
              </div>
            </div>
          </van-collapse-item>
        </van-collapse>

        <!-- 操作按钮 -->
        <div class="record-actions">
          <!-- 最新记录可编辑、可复制、可删除 -->
          <template v-if="index === 0">
            <app-button type="primary" round @click="goToRegister(item)">
              编辑
            </app-button>
            <app-button plain round type="default" @click="copyToNew(item)">
              追加
            </app-button>
            <app-button plain round type="danger" @click="handleDelete(item)">
              删除
            </app-button>
          </template>
          <!-- 历史记录只能复制和查看 -->
          <template v-else>
            <app-button size="small" plain round @click="copyToNew(item)">
              复制继续
            </app-button>
          </template>
        </div>
      </div>
    </div>

    <van-empty
      v-if="!loading && list.length === 0"
      description="暂无登记记录"
    />

    <!-- 底部操作按钮 -->
    <div class="add-btn-wrap">
      <button class="add-btn" @click="goToRegister()">
        <van-icon name="plus" />
        <span>新增登记</span>
      </button>
    </div>

    <van-overlay :show="loading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue";
import { showToast, showConfirmDialog } from "vant";
import { useRouter } from "vue-router";
import { getRegisterList, deleteAssetRegister } from "@/utils/api/asset";
import * as echarts from "echarts/core";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

const router = useRouter();

const loading = ref(false);
const list = ref([]);
const activeNames = ref([]);

// 余额类型名称映射
const getBalanceName = (key) => {
  const names = {
    wechat: "微信余额",
    alipay: "支付宝余额",
    bank: "银行活期",
    wealth: "理财",
    fund: "基金",
    stock: "股票/股市",
    profit: "收益",
    redpacket: "虚拟红包",
    cash: "现金",
    other: "其他",
  };
  return names[key] || key;
};

// 境外资产类型名称映射
const getOffshoreName = (key) => {
  const names = {
    ICBCA: "工商银行(港)",
    BOCA: "中国银行(港)",
    HSBC: "汇丰银行(港)",
    CMBCA: "招商银行(港)",
    Wise: "Wise",
    ifast: "iFast",
    IBKR: "IBKR",
    Schwab: "盈透证券",
    OtherUSD: "其他美元",
    OtherHKD: "其他港币",
    OtherGBP: "其他英镑",
    OtherEUR: "其他欧元",
    Other: "其他",
  };
  return names[key] || key;
};

// 信用卡欠款类型名称映射
const getDebtName = (key) => {
  const names = {
    ICBC: "工商银行信用卡",
    ABC: "农业银行信用卡",
    CCB: "建设银行信用卡",
    BOC: "中国银行信用卡",
    CMBC: "招商银行信用卡",
    COMM: "交通银行信用卡",
    SPDB: "浦发银行信用卡",
    CIB: "兴业银行信用卡",
    Huabei: "花呗",
    Jiebei: "借呗",
    JD: "京东白条",
    Meituan: "美团月付",
    Other: "其他",
  };
  return names[key] || key;
};

// 格式化金额（大数值缩略为 万/亿/万亿，避免溢出）
const formatAmount = (amount) => {
  const num = Number(amount) || 0;
  const abs = Math.abs(num);
  if (abs >= 1e12) {
    return (num / 1e12).toFixed(2) + "万亿";
  }
  if (abs >= 1e8) {
    return (num / 1e8).toFixed(2) + "亿";
  }
  if (abs >= 1e4) {
    return (num / 1e4).toFixed(2) + "万";
  }
  return num.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

// 是否有有效汇率（rate = 100外币 = ? 人民币）
const hasValidRate = (rates, currency) => {
  if (!rates || !currency) return false;
  const rate = rates[currency];
  return rate !== null && rate !== undefined && rate !== "";
};

// 外币折合人民币
const convertToCNY = (rates, amount, currency) => {
  if (!hasValidRate(rates, currency)) return Number(amount) || 0;
  const rate = Number(rates[currency]);
  return Math.round(((Number(amount) * rate) / 100) * 100) / 100;
};

// 境外资产折合人民币总计
const offshoreTotal = (item) => {
  const rates = item.asset_details?.exchangeRates || {};
  const arr = item.asset_details?.offshore || [];
  return arr.reduce(
    (sum, a) => sum + convertToCNY(rates, a.amount, a.currency),
    0
  );
};

// 境内资产（余额）总计
const balanceTotal = (item) => {
  const arr = item.asset_details?.balance || [];
  return arr.reduce((sum, a) => sum + (Number(a.amount) || 0), 0);
};

// 信用卡欠款总计
const debtTotal = (item) => {
  const arr = item.asset_details?.debt || [];
  return arr.reduce((sum, a) => sum + (Number(a.amount) || 0), 0);
};
import dayjs from "dayjs";
// 格式化日期
const formatDate = (date) => {
  if (!date) return "-";
  const d = Number(date);
  return dayjs(d).format("YYYY-MM-DD HH:mm:ss");
};

// ============ 顶部总资产走势快速预览（略缩版） ============
const miniChartRef = ref(null);
let miniChart = null;

// 统一日期转时间戳数值：兼容 YYYY-MM-DD 与时间戳（秒/毫秒），与 Trend 口径一致
const toTimeNum = (raw) => {
  if (raw === null || raw === undefined || raw === "") return 0;
  const s = String(raw);
  if (/^\d+$/.test(s)) {
    let ts = Number(s);
    if (ts < 1e12) ts *= 1000; // 秒 → 毫秒
    return ts;
  }
  const t = new Date(s).getTime();
  return isNaN(t) ? 0 : t;
};

// 登记日期转 YYYY-MM-DD（兼容时间戳/日期串），与 Trend 口径一致
const toDateStr = (raw) => {
  if (raw === null || raw === undefined || raw === "") return "";
  const s = String(raw);
  if (/^\d+$/.test(s)) {
    let ts = Number(s);
    if (ts < 1e12) ts *= 1000;
    const d = new Date(ts);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  }
  return s.length >= 10 ? s.slice(0, 10) : s;
};

// 按时间升序、同日取最新一条，得到总资产走势（略缩预览用）
const miniTrend = computed(() => {
  const sorted = [...list.value].sort(
    (a, b) => toTimeNum(a.register_date) - toTimeNum(b.register_date)
  );
  const byDay = new Map();
  sorted.forEach((r) => byDay.set(toDateStr(r.register_date), r));
  return [...byDay.values()]
    .sort((a, b) => toTimeNum(a.register_date) - toTimeNum(b.register_date))
    .map((r) => ({ date: toDateStr(r.register_date), value: Number(r.total_balance) || 0 }));
});

const miniDates = computed(() => miniTrend.value.map((d) => d.date));

const formatLabelMini = (value) => {
  const num = Number(value) || 0;
  const abs = Math.abs(num);
  if (abs >= 1e8) return (num / 1e8).toFixed(1) + "亿";
  if (abs >= 1e4) return (num / 1e4).toFixed(1) + "万";
  return String(Math.round(num));
};

// 略缩版总资产折线：无 dataZoom，紧凑高度，仅供快速预览
const renderMini = () => {
  if (!miniChartRef.value) return;
  if (!miniChart) miniChart = echarts.init(miniChartRef.value);
  miniChart.setOption(
    {
      color: ["#3b82f6"],
      grid: { top: 8, right: 10, bottom: 18, left: 4, containLabel: true },
      tooltip: {
        trigger: "axis",
        triggerOn: "mousemove|click",
        axisPointer: {
          type: "line",
          snap: true,
          lineStyle: { color: "#3b82f6", type: "dashed", width: 1.5 },
        },
        confine: true,
        backgroundColor: "rgba(255,255,255,0.96)",
        borderColor: "rgba(0,0,0,0.1)",
        padding: [4, 8],
        textStyle: { color: "#323233", fontSize: 11 },
        formatter: (params) => {
          const p = Array.isArray(params) ? params[0] : params;
          if (!p) return "";
          return `${p.axisValue}<br/>总资产 ¥${Number(p.value).toLocaleString(
            "zh-CN",
            { minimumFractionDigits: 2, maximumFractionDigits: 2 }
          )}`;
        },
      },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: miniDates.value,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: "#dcdee0" } },
        axisLabel: {
          color: "#969799",
          fontSize: 9,
          hideOverlap: true,
          formatter: (v) => v,
        },
      },
      yAxis: {
        type: "value",
        minInterval: 1,
        splitNumber: 2,
        splitLine: { lineStyle: { type: "solid", color: "#eceef1", width: 1 } },
        axisLine: { show: false },
        axisLabel: { color: "#969799", fontSize: 9, formatter: (v) => formatLabelMini(v) },
      },
      series: [
        {
          type: "line",
          smooth: true,
          symbol: "circle",
          symbolSize: 4,
          connectNulls: true,
          data: miniTrend.value.map((d) => d.value),
          lineStyle: { width: 2 },
          emphasis: { scale: false },
          select: { disabled: true },
          areaStyle: { opacity: 0.1, color: "#3b82f6" },
        },
      ],
    },
    true
  );
};

const resizeMini = () => {
  if (miniChart) miniChart.resize();
};

// 加载列表
const loadList = async () => {
  loading.value = true;
  try {
    const res = await getRegisterList();
    const data = res.data || res || [];
    // 按日期倒序，最新的在最前面
    list.value = data.sort((a, b) => {
      return new Date(b.register_date) - new Date(a.register_date);
    });
    // 初始化展开状态
    activeNames.value = list.value.map(() => []);
    await nextTick();
    renderMini();
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 跳转到登记页面
const goToRegister = (item) => {
  if (item) {
    router.push({
      path: "/finance/assets/edit",
      query: { id: item.id },
    });
  } else {
    router.push("/finance/assets/register");
  }
};

// 跳转到趋势页面
const goToTrend = () => {
  router.push("/finance/assets/trend");
};

// 复制到新登记
const copyToNew = (item) => {
  sessionStorage.setItem('editAssetData', JSON.stringify({
    asset_details: item.asset_details,
    exchange_rates: item.asset_details?.exchangeRates || {},
    copy: "1"
  }));
  router.push("/finance/assets/register");
};

// 删除记录
const handleDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: "确认删除",
      message: "确定要删除这条登记记录吗？",
      confirmButtonColor: "var(--theme-danger-color)",
    });

    await deleteAssetRegister(item.id);
    showToast("删除成功");
    loadList();
  } catch (e) {
    if (e !== "cancel") {
      showToast(e.message || "删除失败");
    }
  }
};

onMounted(() => {
  loadList();
  window.addEventListener("resize", resizeMini);
});

onUnmounted(() => {
  window.removeEventListener("resize", resizeMini);
  if (miniChart) {
    miniChart.dispose();
    miniChart = null;
  }
});
</script>

<style scoped>
.page-assets-list {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 100px;
}

.add-btn-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0px;
  padding: 12px 16px;
  background: var(--theme-bg-primary);
  display: flex;
  gap: 10px;
}

.add-btn {
  flex: 1;
  height: 50px;
  background: var(--theme-primary);
  color: #fff;
  border: none;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px var(--theme-shadow-color, rgba(7, 193, 96, 0.3));
}

.record-list {
  padding: 0 16px;
}

.record-card {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  margin-bottom: 12px;
  overflow: hidden;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid var(--theme-border);
}

.record-date {
  display: flex;
  align-items: flex-start;
  font-size: 0.8rem;
  color: var(--theme-text-secondary);
  flex-direction: column;
  flex-wrap: nowrap;
  justify-content: space-between;
  p {
    margin: 0;
  }
}

.record-date p .van-icon {
  color: var(--theme-text-tertiary);
}
.record-date p span {
  color: var(--theme-text-primary);
  font-size: 0.6rem;
}

.record-body {
  padding: 16px;
}

.balance-display {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.balance-display .label {
  font-size: 14px;
  color: var(--theme-text-secondary);
}

.balance-display .value {
  font-size: 24px;
  font-weight: bold;
  color: var(--theme-text-primary);
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.balance-detail {
  display: flex;
  gap: 24px;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.detail-value {
  font-size: 14px;
  color: var(--theme-text-primary);
  font-weight: 500;
}

.detail-value.danger {
  color: var(--theme-danger-color);
}

/* 展开明细区跟随主题 */
:deep(.van-collapse-item__wrapper) {
  background: var(--theme-bg-secondary);
}
:deep(.van-collapse-item__content) {
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
  padding: 0 16px;
}

.detail-content {
  padding: 8px 0;
}

.detail-section {
  margin-bottom: 12px;
}

.section-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-bottom: 8px;
}

.region-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: var(--theme-bg-primary);
  border-left: 4px solid var(--theme-text-tertiary);
}

.region-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--theme-text-primary);
}

.region-total {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.region-domestic {
  border-left-color: var(--van-green);
}

.region-offshore {
  border-left-color: var(--theme-primary);
}

.region-debt {
  border-left-color: var(--theme-danger);
}

.section-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--theme-text-secondary);
}

.detail-item .danger {
  color: var(--theme-danger-color);
}

.remark-section {
  padding-top: 8px;
  border: 1px solid var(--theme-border);
  font-size: 12px;
}

.remark-label {
  color: var(--theme-text-tertiary);
}

.remark-value {
  color: var(--theme-text-secondary);
}

.offshore-amount {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.offshore-amount .convert {
  color: var(--van-green);
  font-size: 12px;
}

.section-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border: 1px solid var(--theme-border);
  font-size: 13px;
  color: var(--theme-text-primary);
  font-weight: 600;
}

.record-actions {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--theme-border);
  justify-content: center;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}

/* 顶部总资产走势快速预览（略缩版） */
.mini-trend {
  margin: 12px 16px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  overflow: hidden;
}

.mini-trend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
}

.mini-trend-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.mini-trend-latest {
  flex: 1;
  margin-left: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-primary);
  font-family: "DIN Alternate", -apple-system, sans-serif;
}

.mini-trend-btn {
  padding: 4px 10px;
  background: var(--theme-bg-tertiary);
  color: var(--theme-primary);
  border: 1px solid var(--theme-primary);
  border-radius: 14px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}

.mini-trend-btn:active {
  opacity: 0.8;
}

.mini-chart {
  width: 100%;
  height: 110px;
}
</style>
