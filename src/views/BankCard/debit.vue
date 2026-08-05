<template>
  <div class="page-card-list">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="title">借记卡</h1>
        <span class="subtitle">共 {{ cardList.length }} 张卡片</span>
      </div>
      <div class="preview-toggle">
        <span class="preview-label">预览</span>
        <van-switch
          v-model="previewMode"
          size="20px"
          inactive-color="#dcdee0"
          active-color="#1989fa"
        />
      </div>
    </div>

    <CardStack
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

    <div class="add-btn-wrap" v-if="selectedId === null">
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
import { ref, computed, watch, onMounted } from "vue";
import { showToast } from "vant";
import { useRouter } from "vue-router";
import { getCardList } from "@/utils/api/card";
import { getCardsFlowStats } from "@/utils/api/account";
import { categoryApi } from "@/utils/api/category";
import CardStack from "@/components/BankCard/CardStack.vue";

const router = useRouter();

const cardList = ref([]);
const bankList = ref([]);
const loading = ref(false);
const selectedId = ref(null);
const bankIconError = ref({});
const cardImgError = ref({});

// 预览模式：开启后所有卡号/尾号随机化，用于录屏分享避免泄露
const previewMode = ref(false);

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
const loadCards = async () => {
  loading.value = true;
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
  } catch (error) {
    showToast(error.message || "加载失败");
    cardList.value = [];
  } finally {
    loading.value = false;
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

onMounted(async () => {
  await loadBankList();
  await loadCards();
});
</script>

<style scoped>
.page-card-list {
  min-height: calc(100vh - 100px);
  background: var(--theme-bg-secondary);
  padding: 24px 20px 120px;
  position: relative;
  overflow-x: hidden;
}

.page-header {
  margin-bottom: 24px;
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-header-left {
  display: flex;
  flex-direction: column;
}
.preview-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--theme-bg-primary, #fff);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.preview-label {
  font-size: 13px;
  color: var(--theme-text-secondary);
  font-weight: 500;
}
.page-header .title {
  font-size: 28px;
  font-weight: 800;
  color: var(--theme-text-primary);
  margin: 0;
}
.page-header .subtitle {
  font-size: 14px;
  color: var(--theme-text-tertiary);
}

/* 底部按钮 */
.add-btn-wrap {
  position: fixed;
  bottom: 30px;
  left: 20px;
  right: 20px;
  z-index: 12000;
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
