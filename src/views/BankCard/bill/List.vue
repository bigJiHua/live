<template>
  <div class="page-bill-list">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" :style="{ minHeight: '100vh' }">

    <!-- 过滤条件：左月份 右卡片 -->
    <div class="filter-section">
      <van-cell-group inset>
        <div class="bill-filter-row">
          <app-field
            v-model="monthText"
            label="待还月份"
            placeholder="请选择月份"
            is-link
            readonly
            borderless
            class="bill-filter-item"
            @click="showMonthPicker = true"
          />
          <div class="bill-filter-sep"></div>
          <app-field
            v-model="selectedCardName"
            label="选择卡片"
            placeholder="全部卡片"
            is-link
            readonly
            borderless
            class="bill-filter-item"
            @click="showCardPicker = true"
          />
        </div>
      </van-cell-group>
    </div>

    <!-- 月份选择器 -->
    <app-popup v-model:show="showMonthPicker" position="bottom" round>
      <van-picker
        v-model="selectedValues"
        title="选择月份"
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="showMonthPicker = false"
      />
    </app-popup>

    <!-- 账单列表 -->
    <div class="bill-list" v-if="billList.length > 0">
      <van-collapse v-model="activeGroupNames">
        <van-collapse-item
          v-for="group in billGroups"
          :key="group.key"
          :name="group.key"
          :title="`${group.name} · ${group.items.length} 张账单`"
        >
      <!-- 信报合一池：一次性结清共享额度入口 -->
      <div class="merge-repay-bar" v-if="group.poolMerged && group.poolTotalDebt > 0">
        <div class="merge-repay-info">
          <span class="merge-repay-tag">信报合一</span>
          <span class="merge-repay-text">共享额度共待还 <b>¥{{ formatMoney(group.poolTotalDebt) }}</b></span>
        </div>
        <app-button size="small" type="primary" round @click.stop="openMergeRepay(group)">合并还款</app-button>
      </div>
      <div
        v-for="item in group.items"
        :key="item.id"
        class="bill-card"
      >
        <div class="bill-header">
          <div class="bill-info">
            <div class="bill-name-row">
              <BankIcon
                :src="getFullUrl(getCardBankIcon(item))"
                :name="getCardBankName(item)"
                :size="22"
                class="bank-icon"
              />
              <span class="bill-card-name">{{ getCardDisplayName(item) }}</span>
              <span class="bill-card-last4" v-if="item.card_last4">**** {{ item.card_last4 }}</span>
              <app-button size="mini" plain type="primary" class="detail-btn" @click.stop="goToLedger(item)">明细</app-button>
            </div>
            <div class="bill-fee-info">
              <span>年费 ¥{{ formatMoney(item.annual_fee) }}</span>
              <span class="fee-divider">|</span>
              <span>{{ getFeeFreeRuleText(item.fee_free_rule) }}</span>
            </div>
          </div>
          <div class="bill-status-col">
            <app-tag :type="getStatusType(item)" :class="{ 'tag-normal': getStatusType(item) === '' }">
              {{ getStatusText(item) }}
            </app-tag>
            <div class="status-extra" v-if="getStatusExtra(item)">
              {{ getStatusExtra(item) }}
            </div>
          </div>
        </div>

        <div class="bill-body">
          <div class="bill-limit-small">
            <div class="limit-row">
              <span>额度</span>
              <span>¥{{ formatMoney(item.credit_limit) }}</span>
            </div>
            <div class="limit-row">
              <span>可用</span>
              <span>¥{{ formatMoney(item.avail_limit) }}</span>
            </div>
            <div
              class="limit-row pending-fx-row"
              v-if="pendingForeignCount(item.card_id) > 0"
              @click.stop="goToForeignRegister"
            >
              <span class="pending-fx-tag">待对账</span>
              <span class="pending-fx-count">{{ pendingForeignCount(item.card_id) }} 笔</span>
              <van-icon name="arrow" class="pending-fx-arrow" />
            </div>
          </div>
          <div class="bill-amount-right" @click.stop="goToDetail(item)">
            <div class="amount-col">
              <div class="repay-label">{{ getBillMonthText(item) }}</div>
              <div class="repay-value" :class="{ overdue: Number(item.used_limit) > 0 }">
                ¥{{ formatMoney(item.used_limit) }}
              </div>
            </div>
            <div class="amount-col">
              <div class="repay-label">待还</div>
              <div class="repay-value danger">
                ¥{{ formatMoney(item.need_repay) }}
              </div>
            </div>
          </div>
        </div>

        <div class="bill-footer">
          <div class="bill-day-info">
            <div class="day-row">
              <span class="day-label">账单日</span>
              <span class="day-month">{{ item.bill_day }}号</span>
            </div>
            <div class="day-row">
              <span class="day-label">还款日</span>
              <span class="day-month">{{ item.repay_day }}号</span>
            </div>
          </div>
          <div class="bill-actions">
            <app-button
              size="small"
              plain
              type="primary"
              round
              @click.stop="refreshBill(item, $event)"
            >
              刷新账单
            </app-button>
            <app-button
              size="small"
              type="primary"
              round
              @click.stop="goToRepay(item)"
            >
              立即还款
            </app-button>
          </div>
        </div>
      </div>
        </van-collapse-item>
      </van-collapse>
    </div>

    <van-empty v-if="!loading && billList.length === 0" description="暂无账单记录" />

    <!-- 添加账单按钮 -->
    <div class="add-btn-wrap" v-if="canAddBill">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加账单</span>
      </button>
    </div>

    <van-overlay :show="loading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>

    <!-- 卡片选择器 -->
    <app-popup v-model:show="showCardPicker" position="bottom">
      <van-picker
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </app-popup>

    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated } from "vue";
import { showToast } from "vant";
import { useRoute, useRouter } from "vue-router";
import dayjs from "dayjs";
import { getBillList, getCardList, getCreditPools, getForeignPending, rebuildBill } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";
import ENV from "@/utils/env";
import BankIcon from "@/components/BankIcon.vue";

// keep-alive 精准缓存：name 须与 MainLayout.cachedViewNames / 路由 name 一致
defineOptions({ name: "BillList" });

const BASE_URL = ENV.FILE_BASE_URL;

const route = useRoute();
const router = useRouter();

const billList = ref([]);
const cardList = ref([]);
const bankList = ref([]);
const pools = ref([]);
const foreignPending = ref([]);
const loading = ref(false);
const refreshing = ref(false);
const selectedCardId = ref(null);
const selectedCardName = ref("");
const showCardPicker = ref(false);

// 折叠面板：默认展开所有银行分组
const activeGroupNames = ref([]);

// 月份选择（Vant 4 写法）
// 从 URL query 恢复年份/月份（?year=2026&month=8），keep-alive 返回时月份不丢
const now = dayjs();
const yearFromUrl = parseInt(route.query.year, 10);
const monthFromUrl = parseInt(route.query.month, 10);
const currentYear = ref(Number.isInteger(yearFromUrl) && yearFromUrl >= 2000 ? yearFromUrl : now.year());
const currentMonth = ref(Number.isInteger(monthFromUrl) && monthFromUrl >= 1 && monthFromUrl <= 12 ? monthFromUrl : now.month() + 1);
const showMonthPicker = ref(false);
// Vant 4 必须绑定 v-model 数组来控制选中项
const selectedValues = ref([`${currentYear.value}年`, `${currentMonth.value}月`]);
const monthText = computed(() => `${currentYear.value}年${currentMonth.value}月`);

// 把当前月份同步到 URL query（避免刷新/缓存返回后忘了看哪个月）
const syncMonthToUrl = () => {
  const q = { ...route.query, year: String(currentYear.value), month: String(currentMonth.value) };
  router.replace({ path: route.path, query: q });
};

// 月份选择器列
const pickerColumns = computed(() => {
  const currentYearVal = dayjs().year();
  const years = [];
  for (let i = currentYearVal - 10; i <= currentYearVal + 10; i++) {
    years.push({ text: `${i}年`, value: `${i}年` });
  }
  const months = Array.from({ length: 12 }, (_, i) => ({
    text: `${i + 1}月`,
    value: `${i + 1}月`,
  }));
  return [years, months];
});

// 月份确认
const onPickerConfirm = ({ selectedOptions }) => {
  const yearText = selectedOptions[0].text;
  const monthText = selectedOptions[1].text;
  currentYear.value = parseInt(yearText);
  currentMonth.value = parseInt(monthText);
  selectedValues.value = [yearText, monthText];
  showMonthPicker.value = false;
  syncMonthToUrl();
  loadBillList();
};

// 卡片选择列
const cardColumns = computed(() => {
  const cols = [{ text: "全部卡片", value: null }];
  cardList.value.forEach(card => {
    cols.push({
      text: `${card.alias || card.bank_name || '卡片'} **** ${card.last4_no || card.last4No || '****'}`,
      value: card.id
    });
  });
  return cols;
});

// 是否可以添加账单（需要先有信用卡）
const canAddBill = computed(() => {
  return cardList.value.some(card => (card.card_type || card.cardType) === 'credit');
});

// 加载账单列表
const loadBillList = async () => {
  loading.value = true;
  try {
    const params = {};
    if (selectedCardId.value) {
      params.cardId = selectedCardId.value;
    }
    // 添加账单月份筛选
    const billMonth = `${currentYear.value}-${String(currentMonth.value).padStart(2, '0')}`;
    params.billMonth = billMonth;
    const res = await getBillList(params);
    let list = res.data || res || [];
    // 排序：有欠款（need_repay > 0）按账单日升序排前面，无欠款的放底部
    list.sort((a, b) => {
      const aHasDebt = Number(a.need_repay) > 0
      const bHasDebt = Number(b.need_repay) > 0
      // 有欠款的优先
      if (aHasDebt !== bHasDebt) return aHasDebt ? -1 : 1
      // 都有欠款或都无欠款时，按账单日排序
      const aDate = dayjs(`${a.bill_month}-${String(a.bill_day).padStart(2, '0')}`)
      const bDate = dayjs(`${b.bill_month}-${String(b.bill_day).padStart(2, '0')}`)
      return aDate.valueOf() - bDate.valueOf()
    })
    billList.value = list
  } catch (error) {
    showToast(error.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

// 下拉刷新：重新拉取账单/卡片/池/外币待对账（保持当前月份）
const onRefresh = async () => {
  try {
    await Promise.allSettled([
      loadBillList(),
      loadCardList(),
      loadPools(),
      loadForeignPending(),
    ]);
  } finally {
    refreshing.value = false;
  }
};

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList({ cardType: 'credit' });
    cardList.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    bankList.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 加载共享池列表（用于识别信报合一池、计算共享待还）
const loadPools = async () => {
  try {
    const res = await getCreditPools();
    pools.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 加载待对账外币列表（用于账单卡显示黄色"待对账 N 笔"提示）
const loadForeignPending = async () => {
  try {
    const res = await getForeignPending();
    foreignPending.value = res.data || res || [];
  } catch (error) {
    // 忽略错误
  }
};

// 某张卡当前待对账的外币笔数（按 card_id 统计 pending 登记）
const pendingForeignCount = (cardId) => {
  if (!cardId) return 0;
  return foreignPending.value.filter((r) => r.card_id === cardId).length;
};

// 卡片选择确认
const onCardConfirm = ({ selectedOptions }) => {
  selectedCardId.value = selectedOptions[0].value;
  selectedCardName.value = selectedOptions[0].text;
  showCardPicker.value = false;
  loadBillList();
};

// 计算账单状态（核心逻辑）
// 基于 bill_day（账单日）和 repay_day（次月还款日）做本地计算
const getBillStatus = (item) => {
  if (!item) return { type: '', text: '正常', extra: '' }

  const now = dayjs()
  const billMonth = item.bill_month
  const billDayNum = Number(item.bill_day)
  const repayDayNum = Number(item.repay_day)

  // 账单日：bill_month 月的 bill_day 号
  const billDate = dayjs(`${billMonth}-${String(billDayNum).padStart(2, '0')}`)
  if (!billDate.isValid()) return { type: '', text: '正常', extra: '' }

  // 还款日：账单日所在月的**次月**的 repay_day 号
  const repayDate = billDate.add(1, 'month').date(repayDayNum)

  const needRepay = Number(item.need_repay) || 0
  const daysToBill = billDate.diff(now, 'day')
  const daysToRepay = repayDate.diff(now, 'day')
  const daysAfterBill = now.diff(billDate, 'day')

  // ① 已逾期：超过还款日且仍有欠款
  if (needRepay > 0 && now.isAfter(repayDate, 'day')) {
    const overdueDays = now.diff(repayDate, 'day')
    return { type: 'danger', text: `已逾期${overdueDays}天`, extra: '' }
  }

  // ② 已过账单日（已出账）
  if (!now.isBefore(billDate, 'day')) {
    // 无欠款或已还清
    if (needRepay === 0 || item.repay_status === '已还清') {
      const extra = daysAfterBill > 0 ? `已出账${daysAfterBill}天` : ''
      return { type: 'success', text: '已还清', extra }
    }
    // 有欠款且在还款日前 → 待还款
    const extra = daysToRepay >= 0 ? `${daysToRepay}天后还款日` : ''
    return { type: 'warning', text: '待还款', extra }
  }

  // ③ 未到账单日（未出账）
  if (needRepay > 0) {
    const extra = `出账${daysToBill}天·还款${daysToRepay}天`
    return { type: 'default', text: '未出账', extra }
  }

  // ④ 默认：正常
  return { type: '', text: '正常', extra: '' }
}

// 获取状态类型（供模板使用）
const getStatusType = (item) => getBillStatus(item).type

// 获取状态文本（供模板使用）
const getStatusText = (item) => getBillStatus(item).text

// 获取附加信息（如"XX天后出账"等，供模板使用）
const getStatusExtra = (item) => getBillStatus(item).extra

// 格式化金额
import { formatMoney } from "@/utils/money";

// 格式化日期 - 只显示天数
const formatDay = (date) => {
  if (!date) return "--";
  // 格式如 2026-03-06，提取最后5位中的天数部分
  const match = date.match(/-(\d{2})$/);
  return match ? match[1] : "--";
};

// 根据 bank_id 获取银行信息
const getBankInfo = (bankId) => {
  return bankList.value.find((b) => b.id === bankId) || null;
};

// 获取完整 URL
const getFullUrl = (path) => {
  if (!path) return "";
  if (path.startsWith("http")) return path;
  const pureBase = BASE_URL.replace(/\/+$/, "");
  const purePath = path.startsWith("/") ? path : `/${path}`;
  return pureBase + purePath;
};

// 获取卡片关联的银行图标
const getCardBankIcon = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  if (!bankId) return "";
  const bank = getBankInfo(bankId);
  return bank?.icon_url || bank?.iconUrl || bank?.icon || bank?.image || "";
};

// 获取卡片关联的银行名称（图标失败时首字兜底）
const getCardBankName = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  if (!card) return "";
  const bankId = card.bank_id || card.bankId;
  if (!bankId) return "";
  const bank = getBankInfo(bankId);
  return bank?.name || bank?.bank_name || card.bank_name || "";
};

// 获取账单对应卡片的 bank_id（用于分组，无则归入 "其他"）
const getBillBankId = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  return (card && (card.bank_id || card.bankId)) || "other";
};

// 账单按银行分组：同一银行的账单收纳为一个折叠组。
// 若该银行存在开启信报合一的共享池，则提供一次性结清入口（poolMerged + 池内总待还）
const billGroups = computed(() => {
  const map = new Map();
  billList.value.forEach((item) => {
    const key = getBillBankId(item);
    if (!map.has(key)) map.set(key, { key, name: getCardBankName(item) || "其他", items: [] });
    map.get(key).items.push(item);
  });

  const groups = Array.from(map.values());
  // 对每个银行组，尝试关联共享池（按卡 bank_id 匹配池 bank_id）
  return groups.map((g) => {
    if (g.key === "other") return { ...g, pool: null, poolMerged: false, poolTotalDebt: 0 };
    const pool = pools.value.find((p) => p.bank_id === g.key && Number(p.credit_report_merged) === 1);
    if (!pool) return { ...g, pool: null, poolMerged: false, poolTotalDebt: 0 };
    // 池内总待还 = 该组中属于该池的卡的 need_repay 之和（每个账单一条）
    const cardsInPool = new Set(
      cardList.value
        .filter((c) => c.bank_id === g.key && c.share_pool_id === pool.id)
        .map((c) => c.id)
    );
    const poolTotalDebt = g.items
      .filter((item) => cardsInPool.has(item.card_id))
      .reduce((s, item) => s + (Number(item.need_repay) || 0), 0);
    return { ...g, pool, poolMerged: true, poolTotalDebt };
  });
});

// 折叠面板：默认展开所有银行分组
watch(billGroups, (groups) => {
  activeGroupNames.value = groups.map((g) => g.key)
})

// 获取卡片显示名称
const getCardDisplayName = (item) => {
  const card = cardList.value.find(c => c.id === item.card_id);
  const bankId = card?.bank_id || card?.bankId;
  const bank = getBankInfo(bankId);
  const bankName = bank?.name || "";
  if (item.card_alias) return item.card_alias;
  if (bankName) return bankName;
  return '信用卡';
};

// 获取免年费规则文本
const getFeeFreeRuleText = (rule) => {
  if (!rule) return '无免年费规则';
  // 如果是纯数字，显示 "X笔消费免"
  if (/^\d+$/.test(String(rule))) {
    return `${rule}笔消费免`;
  }
  // 否则直接显示原有值
  return rule;
};

// 获取账单月份文本（根据 bill_start_date 判断）
const getBillMonthText = (item) => {
  if (!item.bill_start_date) return '账单';
  const d = dayjs(item.bill_start_date);
  if (!d.isValid()) return '账单';
  return `${d.month() + 1}月账单`;
};

// 跳转到详情
const goToDetail = (item) => {
  router.push(`/card/bill/detail?id=${item.id}`);
};

// 跳转到流水明细
const goToLedger = (item) => {
  router.push(`/card/bill/ledger?id=${item.id}`);
};

// 跳转到还款
const goToRepay = (item) => {
  router.push(`/card/repay/add?billId=${item.id}`);
};

// 跳转到外币对账页（顶层路由，非 /card 子路由）
const goToForeignRegister = () => {
  router.push({ name: "CreditForeignRegister" });
};

// ===== 信报合一合并还款：跳转还款页（复用 card/repay/add，带 mergePoolId）=====
const openMergeRepay = (group) => {
  // 任取一张该池内的账单作为入口账单（还款页需要 billId 展示关联信息）
  const firstBill = group.items.find((item) => item.need_repay > 0);
  if (!firstBill) {
    showToast('共享池内暂无待还账单');
    return;
  }
  router.push(`/card/repay/add?billId=${firstBill.id}&mergePoolId=${group.pool?.id || ''}`);
};

// 跳转到添加
const goToAdd = () => {
  router.push("/card/bill/add");
};

// 刷新账单
const refreshBill = async (item, event) => {
  event.stopPropagation();
  try {
    const res = await rebuildBill(item.card_id);
    const msg = res.message || "账单已刷新";
    showToast(msg);
    loadBillList();
  } catch (error) {
    showToast(error.message || "刷新失败");
  }
};

// 返回
const onClickLeft = () => {
  router.back();
};

// 根据 URL 月份决定加载目标月，并加载对应数据。
// 规则：URL 带 year/month → 用 URL 月份；URL 无月份 → 强制重置为本月并同步 URL
// （避免 keep-alive 缓存了旧月份却看不到本月数据）。
const applyMonthAndLoad = (forceReload = false) => {
  const yFromUrl = parseInt(route.query.year, 10);
  const mFromUrl = parseInt(route.query.month, 10);
  const hasMonthInUrl = Number.isInteger(yFromUrl) && yFromUrl >= 2000 && Number.isInteger(mFromUrl) && mFromUrl >= 1 && mFromUrl <= 12;

  const nowY = dayjs().year();
  const nowM = dayjs().month() + 1;
  const targetY = hasMonthInUrl ? yFromUrl : nowY;
  const targetM = hasMonthInUrl ? mFromUrl : nowM;

  // 目标月与当前缓存不一致，或 URL 无月份（需重置为本月）→ 更新选中并重新加载
  const changed = currentYear.value !== targetY || currentMonth.value !== targetM;
  if (changed) {
    currentYear.value = targetY;
    currentMonth.value = targetM;
    selectedValues.value = [`${targetY}年`, `${targetM}月`];
  }
  if (!hasMonthInUrl) syncMonthToUrl();

  // URL 无月份：一律强制重新加载本月（不接着 keep-alive 的旧数据）
  if (changed || forceReload || !hasMonthInUrl) {
    loadBillList();
  }
};

onMounted(() => {
  loadCardList();
  loadBankList();
  loadPools();
  loadForeignPending();
  applyMonthAndLoad(true); // 首次：按 URL 月份加载（无月份则本月）
});

// keep-alive：从缓存返回时刷新。URL 无月份时强制回本月加载，
// URL 有月份时若与缓存一致则仅刷新（消费/还款变化），不一致则切换到该月。
// 首次挂载时 onActivated 也会触发，与 onMounted 重复，用首次标志跳过。
let firstActivated = true;
onActivated(() => {
  if (typeof window === "undefined") return;
  if (firstActivated) {
    firstActivated = false;
    return;
  }
  applyMonthAndLoad(false);
  loadForeignPending();
  loadPools();
});
</script>

<style scoped>
.page-bill-list {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 100px;
}

.page-header {
  background: var(--theme-bg-secondary);
}

.filter-section {
  padding: 12px 0;
}

.bill-list {
  padding: 0 16px;
}

/* 折叠面板：无外边框，银行标题不居中（覆盖全局样式） */
:deep(.van-collapse) {
  background: transparent;
}
:deep(.van-collapse-item) {
  background: transparent;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.06));
  padding-bottom: 4px;
}
/* 最后一个银行分组不加底部边框，避免页底多余分隔线 */
:deep(.van-collapse-item:last-child) {
  border-bottom: none;
  margin-bottom: 0;
}
:deep(.van-collapse-item__title) {
  justify-content: flex-start;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
}
:deep(.van-collapse-item__title::after) {
  right: 0 !important;
}
:deep(.van-collapse-item__content) {
  background: transparent;
  padding: 0;
}
:deep(.van-collapse-item__content > .van-cell-group) {
  margin-top: 4px;
}

.bill-card {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.bill-card:last-child {
  margin-bottom: 0;
}

.bill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.bill-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.bill-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bank-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
}

.bill-card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.bill-card-last4 {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.detail-btn {
  margin-left: 6px;
  flex-shrink: 0;
}

.bill-fee-info {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.fee-divider {
  color: var(--theme-text-tertiary);
}

.bill-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 8px;
}

.bill-repay {
  flex: 1;
  text-align: center;
}

.repay-label {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-bottom: 4px;
}

.repay-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.repay-value.overdue {
  color: var(--van-danger-color, #ee0a24);
}

.repay-value.danger {
  color: var(--van-danger-color, #ee0a24);
}

.bill-limit-small {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding-right: 12px;
}

.limit-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
}

.limit-row span:last-child {
  color: var(--theme-text-secondary);
}

/* 外币待对账黄色提示（可点击跳转对账页） */
.pending-fx-row {
  align-items: center;
  cursor: pointer;
}
.pending-fx-tag {
  color: #ed6a0c !important;
  font-weight: 600;
}
.pending-fx-count {
  color: #ed6a0c !important;
  font-weight: 600;
}
.pending-fx-arrow {
  color: #ed6a0c;
  font-size: 12px;
}

/* 账单金额区（N月账单/待还）作为详情点击区域 */
.bill-amount-right {
  cursor: pointer;
}
.bill-amount-right:active {
  opacity: 0.6;
}

/* 账单日/还款日清晰度 */
.bill-day-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.day-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
}
.day-label {
  flex: 0 0 auto;
  color: var(--theme-text-secondary);
  font-weight: 500;
}
.day-month {
  font-weight: 600;
  color: var(--theme-text-primary);
}

.bill-amount-right {
  flex: 1;
  display: flex;
  justify-content: space-around;
  align-items: center;
}

.amount-col {
  text-align: center;
}

.tag-normal {
  background: var(--theme-primary) !important;
  color: #fff !important;
}

.bill-status-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.status-extra {
  font-size: 10px;
  color: var(--theme-text-tertiary);
  white-space: nowrap;
}

.bill-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
}

.bill-actions {
  display: flex;
  gap: 8px;
}

/* 信报合一合并还款入口栏 */
.merge-repay-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 10px 14px;
  margin: 0 0 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.merge-repay-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--theme-text-secondary);
  min-width: 0;
}
.merge-repay-tag {
  flex-shrink: 0;
  background: var(--theme-primary);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}
.merge-repay-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.merge-repay-text b {
  color: var(--theme-danger, #ee0a24);
  font-size: 15px;
}

.bill-limit {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.bill-filter-row {
  display: flex;
  align-items: stretch;
}
.bill-filter-item {
  flex: 1;
  min-width: 0;
}
.bill-filter-sep {
  width: 1px;
  background: var(--theme-border);
  margin: 12px 0;
}

.add-btn-wrap {
  position: fixed;
  bottom: 30px;
  left: 20px;
  right: 20px;
  z-index: 100;
}

.glass-add-btn {
  width: 100%;
  height: 56px;
  background: var(--theme-primary);
  color: #fff;
  border: none;
  border-radius: 28px;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 24px rgba(25, 137, 250, 0.3);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
