<template>
  <div class="page-card-list" :class="{ 'sort-page': sortMode, scrolled: cardListScrolled }" @scroll.passive="onCardListScroll">
    <!-- 排序模式：简洁 list + 拖拽（vuedraggable） -->
    <div v-if="sortMode" class="sort-scroll">
      <draggable
        v-model="cardList"
        item-key="id"
        class="sort-list"
        handle=".sort-handle"
        :animation="180"
        ghost-class="sort-ghost"
        drag-class="sort-drag"
        :scroll-sensitivity="80"
        :scroll-speed="12"
        @end="commitSort"
      >
      <template #item="{ element }">
        <div class="sort-item">
          <span class="sort-name">
            <span class="sort-bank">{{ element.bankName }}</span>
            <span class="sort-last4">{{ element.last4No }}</span>
          </span>
          <van-icon name="bars" class="sort-handle" />
        </div>
      </template>
      </draggable>
    </div>

    <CardStack
      v-else
      v-model:selected-id="selectedId"
      :card-list="displayCardList"
      default-color="#4A90E2"
      card-type-label="DEBIT CARD"
      :card-img-errors="cardImgError"
      :bank-icon-errors="bankIconError"
      @edit="goToEdit"
      @card-img-error="onCardImgError"
      @bank-icon-error="onBankIconError"
    />

    <van-empty
      v-if="!loading && cardList.length === 0"
      description="暂无借记卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！"
    />

    <div class="add-btn-wrap" :class="{ hidden: cardListScrolled }" v-if="selectedId === null && !sortMode">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加卡片</span>
      </button>
    </div>

    <van-overlay :show="loading" z-index="2000">
      <div class="flex-center">
        <van-loading size="36px" vertical color="#fff">加载中...</van-loading>
      </div>
    </van-overlay>

    <!-- 选中卡片弹起时：底部毛玻璃「查看消费明细」 -->
    <transition name="fade">
      <div class="flow-bar" v-if="selectedId !== null && selectedCard">
        <button class="flow-btn" type="button" @click="openCardFlow">
          <van-icon name="orders-o" />
          <span>查看消费明细</span>
        </button>
        <div class="flow-empty-tip" v-if="cardFlowIdle">近6个月无动账</div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated, inject } from "vue";
defineOptions({ name: "BankCardDebit" });
import { showToast } from "vant";
import { useRouter } from "vue-router";
import { getCardList, updateCardSortBatch } from "@/utils/api/card";
import { getCardsFlowStats } from "@/utils/api/account";
import { categoryApi } from "@/utils/api/category";
import CardStack from "@/components/BankCard/CardStack.vue";
import draggable from "vuedraggable";

const router = useRouter();

// 预览模式由壳层 index.vue 提供（跨子页共享）
const previewMode = inject("cardPreviewMode", ref(false));
// 排序模式由壳层提供；开启时列表切换为可拖拽简洁 list
const sortMode = inject("cardSortMode", ref(false));
// 列表滚动收起状态（壳层单一源）：上滑收起按钮、撑满卡片区
const cardListScrolled = inject("cardListScrolled", ref(false));

// 滚动容器是 .page-card-list 本身（非 window）：上滑方向收起、下滑方向展开。
// 方向判定带小幅死区（THRESHOLD），避免临界位置来回微抖导致状态反复切换「卡卡卡」。
// 仅非排序模式（sort-page 为 overflow:hidden 不触发）。
let lastScrollTop = 0;
const SCROLL_DIR_THRESHOLD = 4;
const onCardListScroll = (e) => {
  const top = e.target.scrollTop || 0;
  const delta = top - lastScrollTop;
  if (delta > SCROLL_DIR_THRESHOLD) cardListScrolled.value = true;
  else if (delta < -SCROLL_DIR_THRESHOLD) cardListScrolled.value = false;
  lastScrollTop = top;
};

const cardList = ref([]);
const bankList = ref([]);
const loading = ref(true);
const selectedId = ref(null);
const bankIconError = ref({});
const cardImgError = ref({});

// 生成 4 位随机数字
const random4 = () =>
  String(Math.floor(1000 + Math.random() * 9000));
// 生成 N 位随机数字串
const randomDigits = (n) => {
  let s = "";
  for (let i = 0; i < n; i++) s += Math.floor(Math.random() * 10);
  return s;
};

// 预览脱敏后缓存（保证列表/卡片显示一致，不随 render 变化）
const maskedCardList = ref([]);

// 预览脱敏处理：BIN 保留前 3 位 + 后 3 位随机数字，尾号 4 位随机
const buildMaskedList = (list) =>
  list.map((item) => {
    const bin = item.card_bin || item.cardBin || "";
    const maskedBin = bin.slice(0, 3) + randomDigits(bin.length > 3 ? bin.length - 3 : 3);
    const fakeLast4 = random4();
    return {
      ...item,
      last4No: fakeLast4,
      last4_no: fakeLast4,
      card_bin: maskedBin,
      cardBin: maskedBin,
    };
  });

// 传给 CardStack 的列表：预览模式用稳定缓存，否则用原始数据
const displayCardList = computed(() =>
  previewMode.value ? maskedCardList.value : cardList.value
);

watch([previewMode, cardList], ([mode, list]) => {
  maskedCardList.value = mode ? buildMaskedList(list) : [];
});

// 当前选中卡片（用于底部「查看消费明细」）
const selectedCard = computed(
  () => displayCardList.value.find((c) => c.id === selectedId.value) || null
);
// 该卡近6个月是否无动账（0 笔支出且无 0 笔收入）
const cardFlowIdle = ref(false);

// 选中卡片弹起时：联动动账查询，匹配单张卡 id 判断有无消费
watch(selectedId, async (val) => {
  if (!val) {
    cardFlowIdle.value = false;
    return;
  }
  try {
    const res = await getCardsFlowStats({ months: 6 });
    const list = res.data?.list || [];
    const item = list.find((x) => x.cardId === val);
    cardFlowIdle.value =
      !!item && item.expenseCount === 0 && item.incomeCount === 0;
  } catch (e) {
    console.error("获取动账状态失败", e);
    cardFlowIdle.value = false;
  }
});

// 查看消费明细：跳转 card-flow 并关闭弹窗
const openCardFlow = () => {
  const id = selectedCard.value?.id;
  selectedId.value = null;
  if (id) router.push(`/finance/report/card-flow?cardId=${id}`);
};

// BASE_URL
import ENV from '@/utils/env'
const BASE_URL = ENV.FILE_BASE_URL;

// 获取银行信息
const getBankInfo = (bankId) => {
  const bank = bankList.value.find((b) => b.id === bankId);
  if (bank) {
    return {
      name: bank.name,
      iconUrl: bank.icon_url ? BASE_URL + bank.icon_url : "",
    };
  }
  return { name: "", iconUrl: "" };
};

// 银行图标加载失败处理
const onBankIconError = (cardId) => {
  bankIconError.value[cardId] = true;
};

// 卡面图片加载失败处理
const onCardImgError = (cardId) => {
  cardImgError.value[cardId] = true;
};

// 获取卡组织信息（根据 card_org 匹配 name）
// 英文忽略大小写，中文全等匹配
const getCardOrgInfo = (cardOrg) => {
  if (!cardOrg) return "";
  // 判断是否包含中文
  const isChinese = /[\u4e00-\u9fa5]/.test(cardOrg);
  const org = bankList.value.find((b) => {
    if (isChinese) {
      return b.name === cardOrg;
    } else {
      return b.name.toLowerCase() === cardOrg.toLowerCase();
    }
  });
  if (org && org.icon_url) {
    return BASE_URL + org.icon_url;
  }
  return "";
};

// 加载银行分类
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank");
    bankList.value = res.data || res || [];
  } catch (e) {
    bankList.value = [];
  }
};

// 加载卡片列表
const loadCards = async (silent = false) => {
  if (!silent) loading.value = true;
  try {
    const res = await getCardList({ cardType: "debit" });
    const data = res.data || res || [];
    cardList.value = data.map((item) => {
      const bankInfo = getBankInfo(item.bank_id || item.bankId);
      const cardOrg = item.card_org || "";
      return {
        id: item.id,
        bankId: item.bank_id || item.bankId,
        bankName:
          bankInfo.name || item.bank_name || item.bankName || "未知银行",
        bankIconUrl: bankInfo.iconUrl,
        cardOrg: cardOrg,
        cardOrgIconUrl: getCardOrgInfo(cardOrg),
        cardType: item.card_type || item.cardType,
        cardBin: item.card_bin,
        cardLength: item.card_length,
        last4No: item.last4_no || item.last4No,
        alias: item.alias,
        cardLevel: item.card_level || item.cardLevel,
        cardImg: item.card_img || item.cardImg,
        cardImgUrl: (item.card_img || item.cardImg) ? BASE_URL + (item.card_img || item.cardImg) : "",
        color: item.color,
        isDefault: item.is_default ?? item.isDefault,
        isHide: item.is_hide ?? item.isHide,
        status: item.status,
      };
    });
    // 仅在成功加载后记录时间戳，供激活节流判断
    lastLoadTime = Date.now();
  } catch (error) {
    showToast(error.message || "加载失败");
    cardList.value = [];
  } finally {
    if (!silent) loading.value = false;
  }
};

// 跳转到编辑页面
const goToEdit = (item) => {
  router.push(`/card/edit?id=${item.id}&from=debit`);
  selectedId.value = null;
};

// 跳转到添加页面
const goToAdd = () => {
  router.push("/card/add");
};

// keep-alive：首次由 onMounted 加载；后续激活时静默刷新（保留列表/选中/预览/滚动状态）
// 短时切换节流：距上次成功加载不足 REFRESH_INTERVAL 不重复请求，避免来回切狂刷
let activatedOnce = false;
let lastLoadTime = 0;
const REFRESH_INTERVAL = 10000;
onActivated(() => {
  if (!activatedOnce) {
    activatedOnce = true;
    return;
  }
  if (Date.now() - lastLoadTime < REFRESH_INTERVAL) return;
  loadBankList();
  loadCards(true);
});

// ===== 排序模式：拖拽 =====
// vuedraggable 通过 v-model 已直接更新 cardList 顺序；@end 时按 1-N 重赋 sort
// 整列提交（一次请求）。本地数据不重新 fetch，保留用户调整后的顺序。
const commitSort = (e) => {
  // 仅当拖拽真实改变了位置才提交，避免松手即请求
  if (e && typeof e.oldIndex === "number" && e.oldIndex === e.newIndex) return;
  const items = cardList.value.map((c, i) => ({ id: c.id, sort: i + 1 }));
  updateCardSortBatch(items).catch((err) => {
    console.error("批量更新排序失败", err);
  });
};

onMounted(async () => {
  await loadBankList();
  await loadCards();
});
</script>

<style scoped>
.page-card-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--theme-bg-secondary);
  padding: 24px 20px 120px;
  position: relative;
  overflow-x: hidden;
  transition: min-height 0.32s cubic-bezier(0.4, 0, 0.2, 1),
    padding-bottom 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 排序模式：列表从 header 下方（150px）钉到底，内部滚动，不盖 header */
.sort-page {
  position: fixed;
  top: 150px;
  left: 0;
  right: 0;
  bottom: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 排序简洁 list 模式 */
.sort-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0;
}
.sort-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.sort-item {
  height: 40px;
  margin-bottom: 2px;
  padding: 0 15px 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--theme-bg-primary, #fff);
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  user-select: none;
}
.sort-name {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.sort-bank {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sort-last4 {
  font-size: 14px;
  color: var(--theme-text-secondary, #969799);
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
  flex-shrink: 0;
}
.sort-handle {
  font-size: 20px;
  color: var(--theme-text-secondary, #c8c9cc);
  flex-shrink: 0;
  cursor: grab;
  padding: 4px;
  touch-action: none;
}
.sort-handle:active {
  cursor: grabbing;
  color: var(--theme-primary);
}
/* 拖拽占位（被拖项原位置） */
.sort-ghost {
  opacity: 0.4;
  background: var(--theme-primary, #1989fa);
}
.sort-ghost * {
  visibility: hidden;
}
/* 拖拽中项 */
.sort-drag {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  opacity: 0.95;
}

/* 底部按钮 */
.add-btn-wrap {
  position: fixed;
  bottom: 30px;
  left: 20px;
  right: 20px;
  z-index: 12000;
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.add-btn-wrap.hidden {
  transform: translateY(160%);
  opacity: 0;
  pointer-events: none;
}
.page-card-list.scrolled {
  padding-bottom: 24px;
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

/* 选中卡片底部毛玻璃「查看消费明细」（位于卡片下方，与卡片同宽） */
.flow-bar {
  position: fixed;
  top: 280px; /* 卡片 fixed top:24px + 卡高≈231px + 间距，置于卡片正下方 */
  left: 20px;
  right: 20px;
  z-index: 96000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.flow-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
  cursor: pointer;
  white-space: nowrap;
}
.flow-btn:active {
  opacity: 0.85;
}
.flow-empty-tip {
  font-size: 12px;
  color: #ff7a7a;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}

/* 与 CardStack 一致的淡入淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(30%);
}
</style>
