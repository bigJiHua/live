<template>
  <div class="page-assets-list">
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
              最后更新日期：<span>{{ formatDate(item.create_time) }}</span>
            </p>
          </div>
          <van-tag :type="index === 0 ? 'success' : 'default'">
            {{ index === 0 ? "最新" : "历史" }}
          </van-tag>
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
              <!-- 余额明细 -->
              <template v-if="item.asset_details?.balance">
                <div class="detail-section">
                  <div class="section-label">余额</div>
                  <div class="section-items">
                    <div
                      v-for="(val, key) in item.asset_details.balance"
                      :key="key"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getBalanceName(key) }}</span>
                      <span>¥{{ formatAmount(val.amount) }}</span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 境外资产明细 -->
              <template v-if="item.asset_details?.offshore">
                <div class="detail-section">
                  <div class="section-label">境外资产</div>
                  <div class="section-items">
                    <div
                      v-for="(val, key) in item.asset_details.offshore"
                      :key="key"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getOffshoreName(key) }}</span>
                      <span
                        >{{ val.currency || "" }}
                        {{ formatAmount(val.amount) }}</span
                      >
                    </div>
                  </div>
                </div>
              </template>

              <!-- 信用卡欠款明细 -->
              <template v-if="item.asset_details?.debt">
                <div class="detail-section">
                  <div class="section-label">信用卡欠款</div>
                  <div class="section-items">
                    <div
                      v-for="(val, key) in item.asset_details.debt"
                      :key="key"
                      class="detail-item"
                    >
                      <span>{{ val.customName || getDebtName(key) }}</span>
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
                  item.exchange_rates &&
                  Object.keys(item.exchange_rates).length > 0
                "
              >
                <div class="detail-section">
                  <div class="section-label">登记汇率</div>
                  <div class="section-items">
                    <div
                      v-for="(rate, currency) in item.exchange_rates"
                      :key="currency"
                      class="detail-item"
                    >
                      <span>{{ currency }}</span>
                      <span>{{ rate }}</span>
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
            <van-button type="primary" round @click="goToRegister(item)">
              编辑
            </van-button>
            <van-button plain round type="default" @click="copyToNew(item)">
              追加
            </van-button>
            <van-button plain round type="danger" @click="handleDelete(item)">
              删除
            </van-button>
          </template>
          <!-- 历史记录只能复制和查看 -->
          <template v-else>
            <van-button size="small" plain round @click="copyToNew(item)">
              复制继续
            </van-button>
          </template>
        </div>
      </div>
    </div>

    <van-empty
      v-if="!loading && list.length === 0"
      description="暂无登记记录"
    />

    <!-- 底部新增按钮 -->
    <div class="add-btn-wrap">
      <button class="add-btn" @click="goToRegister">
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
import { ref, onMounted } from "vue";
import { showToast, showConfirmDialog } from "vant";
import { useRouter } from "vue-router";
import { getRegisterList, deleteAssetRegister } from "@/utils/api/asset";

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

// 格式化金额
const formatAmount = (amount) => {
  const num = Number(amount) || 0;
  return num.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};
import dayjs from "dayjs";
// 格式化日期
const formatDate = (date) => {
  if (!date) return "-";
  const d = Number(date);
  return dayjs(d).format("YYYY-MM-DD HH:mm:ss");
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
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

// 跳转到登记页面
const goToRegister = (item) => {
  if (item) {
    // 传递数据用于编辑
    router.push({
      path: "/finance/assets/register",
      query: {
        id: item.id,
        data: JSON.stringify(item.asset_details),
        rates: JSON.stringify(item.exchange_rates || {}),
      },
    });
  } else {
    router.push("/finance/assets/register");
  }
};

// 复制到新登记
const copyToNew = (item) => {
  router.push({
    path: "/finance/assets/register",
    query: {
      data: JSON.stringify(item.asset_details),
      rates: JSON.stringify(item.exchange_rates || {}),
      copy: "1",
    },
  });
};

// 删除记录
const handleDelete = async (item) => {
  try {
    await showConfirmDialog({
      title: "确认删除",
      message: "确定要删除这条登记记录吗？",
      confirmButtonColor: "#ee0a24",
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
});
</script>

<style scoped>
.page-assets-list {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.add-btn-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 60px;
  padding: 12px 16px;
  background: #f7f8fa;
}

.add-btn {
  width: 100%;
  height: 50px;
  background: #07c160;
  color: #fff;
  border: none;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(7, 193, 96, 0.3);
}

.record-list {
  padding: 0 16px;
}

.record-card {
  background: #fff;
  border-radius: 12px;
  margin-bottom: 12px;
  overflow: hidden;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.record-date {
  display: flex;
  align-items: flex-start;
  font-size: 0.8rem;
  color: #666;
  flex-direction: column;
  flex-wrap: nowrap;
  justify-content: space-between;
  p {
    margin: 0;
  }
}

.record-date p .van-icon {
  color: #999;
}
.record-date p span {
  color: #333;
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
  color: #666;
}

.balance-display .value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
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
  color: #999;
}

.detail-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.detail-value.danger {
  color: #ee0a24;
}

.detail-content {
  padding: 8px 0;
}

.detail-section {
  margin-bottom: 12px;
}

.section-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
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
  color: #666;
}

.detail-item .danger {
  color: #ee0a24;
}

.remark-section {
  padding-top: 8px;
  border-top: 1px dashed #f0f0f0;
  font-size: 12px;
}

.remark-label {
  color: #999;
}

.remark-value {
  color: #666;
}

.record-actions {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  justify-content: center;
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
