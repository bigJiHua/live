<template>
  <div class="page-card-list">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="title">信用卡</h1>
        <span class="subtitle">共 {{ cardList.length }} 张卡片</span>
      </div>
      <div class="preview-toggle">
        <span class="preview-label">预览</span>
        <van-switch
          v-model="previewMode"
          size="20px"
          inactive-color="#dcdee0"
          active-color="#ee0a24"
        />
      </div>
    </div>

    <CardStack
      v-model:selected-id="selectedId"
      :card-list="displayCardList"
      default-color="var(--theme-danger-color, #ee0a24)"
      card-type-label="CREDIT CARD"
      :card-img-errors="cardImgError"
      :bank-icon-errors="bankIconError"
      @edit="goToEdit"
      @card-img-error="onCardImgError"
      @bank-icon-error="onBankIconError"
    />

    <van-empty
      v-if="!loading && cardList.length === 0"
      description="暂无信用卡，如需添加银行卡请先到应用设置-银行分类新增银行信息！"
    />

    <div class="add-btn-wrap" v-if="selectedId === null">
      <button class="glass-add-btn" @click="goToAdd">
        <van-icon name="plus" />
        <span>添加信用卡</span>
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
import { ref, computed, watch, onMounted } from "vue";
import { showToast } from "vant";
import { useRouter } from "vue-router";
import { getCardList } from "@/utils/api/card";
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

// 预览脱敏处理：BIN 保留前 3 位 + 后 N 位随机数字，尾号 4 位随机
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
    const res = await getCardList({ cardType: "credit" });
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
  router.push(`/card/edit?id=${item.id}&from=credit`);
  selectedId.value = null;
};

// 跳转到添加页面
const goToAdd = () => {
  router.push("/credit-full");
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
  background: var(--van-danger-color, #ee0a24);
  color: #fff;
  border: none;
  border-radius: 28px;
  font-weight: 600;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 8px 24px rgba(238, 10, 36, 0.3);
}

.flex-center {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
}
</style>
