<template>
  <div class="page-card-list" @click="handleBackdropClick">
    <div class="page-header">
      <h1 class="title">借记卡</h1>
      <span class="subtitle">共 {{ cardList.length }} 张卡片</span>
    </div>

    <div class="card-stack-container" v-if="cardList.length > 0">
      <div
        class="stack-backdrop"
        :class="{ 'is-visible': selectedId !== null }"
      ></div>

      <div
        class="card-stack"
        :style="{ height: `${220 + (cardList.length - 1) * 45}px` }"
      >
        <div
          v-for="(item, index) in cardList"
          :key="item.id"
          class="bank-card-item"
          :class="{
            'is-selected': selectedId === item.id,
            'is-dimmed': selectedId !== null && selectedId !== item.id,
            'has-card-img': item.cardImgUrl,
          }"
          :style="{
            '--card-color': item.color || '#4A90E2',
            '--stack-offset': `${index * 70}px`,
            'z-index': selectedId === item.id ? 999 : index,
            ...(item.cardImgUrl ? { backgroundImage: `url(${item.cardImgUrl})` } : {}),
          }"
          @click.stop="handleCardClick(item, index)"
        >
          <div class="bg-pattern" v-if="!item.cardImgUrl"></div>

          <!-- 有卡面图片时：隐藏左上角银行信息，右上角显示尾号 -->
          <div class="card-header" v-if="!item.cardImgUrl">
            <div class="bank-info">
              <div class="bank-icon" v-if="item.bankIconUrl">
                <img :src="item.bankIconUrl" :alt="item.bankName" />
              </div>
              <div class="bank-icon-mock" v-else>
                {{ item.bankName?.charAt(0) || "?" }}
              </div>
              <div class="bank-name">
                {{ item.bankName || "未知银行" }}
                <span class="bank-last4" v-if="selectedId === null"
                  >（{{ item.last4No }}）</span
                >
              </div>
            </div>
            <van-tag v-if="item.isDefault || item.is_default" class="custom-tag"
              >默认</van-tag
            >
          </div>

          <!-- 有卡面图片时：右上角显示尾号后四位（弹出后隐藏） -->
          <div class="card-header-img" v-if="item.cardImgUrl && selectedId !== item.id">
            <van-tag v-if="item.isDefault || item.is_default" class="custom-tag"
              >默认</van-tag
            >
            <span class="card-img-last4">{{ item.last4No }}</span>
          </div>

          <div class="card-number" v-if="!item.cardImgUrl">
            {{ formatCardNo(item) }}
          </div>

          <div class="card-footer" v-if="!item.cardImgUrl">
            <div class="holder-section">
              <span class="label">{{
                item.cardType === "credit" ? "CREDIT CARD" : "DEBIT CARD"
              }}</span>
              <span class="value">{{
                item.alias || item.cardLevel || "银行卡"
              }}</span>
            </div>

            <!-- 卡组织图标 - 左下角默认 -->
            <div
              class="card-org"
              v-if="item.cardOrgIconUrl && selectedId === null"
            >
              <img :src="item.cardOrgIconUrl" alt="卡组织" />
            </div>
          </div>

          <!-- 有卡面图片时：弹出后左下角显示尾号 -->
          <div class="card-footer-img" v-if="item.cardImgUrl && selectedId === item.id">
            <span class="card-img-last4-bottom">{{ item.last4No }}</span>
          </div>

          <transition name="fade">
            <div class="card-actions-quick" v-if="selectedId === item.id">
              <button class="action-pill-btn" @click.stop="goToEdit(item)">
                <van-icon name="setting-o" />
                <span>管理</span>
              </button>
              <!-- 卡组织图标 - 右上角选中时 -->
              <div class="card-org card-org-top" v-if="item.cardOrgIconUrl && !item.cardImgUrl">
                <img :src="item.cardOrgIconUrl" alt="卡组织" />
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <van-empty
      v-if="!loading && cardList.length === 0"
      description="暂无借记卡"
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
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { showToast } from "vant";
import { useRouter } from "vue-router";
import { getCardList } from "@/utils/api/card";
import { categoryApi } from "@/utils/api/category";

const router = useRouter();

const cardList = ref([]);
const bankList = ref([]);
const loading = ref(false);
const selectedId = ref(null);

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

// 格式化卡号显示：4位一组，如 6228 48** **** ***6 6666
const formatCardNo = (item) => {
  const bin = item.card_bin || item.cardBin || "";
  const length = parseInt(item.card_length || item.cardLength || "16");
  const last4 = item.last4_no || item.last4No || "****";
  const middleLength = length - bin.length - 4;
  const middleStars = middleLength > 0 ? "*".repeat(middleLength) : "";
  const fullNo = bin + middleStars + last4;

  // 每4位一组，用空格分隔
  return fullNo.match(/.{1,4}/g)?.join(" ") || fullNo;
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

const handleCardClick = (item) => {
  if (selectedId.value === item.id) {
    selectedId.value = null;
  } else {
    selectedId.value = item.id;
  }
};

const handleBackdropClick = () => {
  selectedId.value = null;
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
  background: #ffffff;
  padding: 24px 20px 120px;
  position: relative;
  overflow-x: hidden;
}

.page-header {
  margin-bottom: 24px;
  position: relative;
  z-index: 10;
}
.page-header .title {
  font-size: 28px;
  font-weight: 800;
  color: #1a1a1a;
  margin: 0;
}
.page-header .subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

/* 堆叠区域 */
.card-stack-container {
  position: relative;
  z-index: 60;
}

.stack-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 50;
}
.stack-backdrop.is-visible {
  opacity: 1;
  pointer-events: auto;
}

.card-stack {
  position: relative;
  width: 100%;
  margin-top: 10px;
}

.bank-card-item {
  position: absolute;
  left: 0;
  right: 0;
  border-radius: 20px;
  height: 220px;
  padding: 20px 5px 20px 20px;
  box-sizing: border-box;
  color: #fff;
  background: linear-gradient(135deg, var(--card-color) 0%, #1a1a1a 150%);
  top: var(--stack-offset);
  transition: transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), top 0.5s ease,
    filter 0.4s ease, box-shadow 0.4s ease;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.bank-card-item.is-selected {
  transform: translateY(-100px) scale(1.05);
  box-shadow: 0 40px 80px rgba(0, 0, 0, 0.4);
  filter: brightness(1.1);
}

.bank-card-item.is-dimmed {
  filter: brightness(0.5) blur(1px);
  transform: translateY(10px) scale(0.95);
  opacity: 0.6;
}

/* 有卡面图片时：用图片做背景，去掉渐变 */
.bank-card-item.has-card-img {
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 有卡面图片时的右上角尾号 */
.card-header-img {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  position: relative;
  z-index: 2;
  gap: 8px;
}
.card-img-last4 {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  background: rgba(0, 0, 0, 0.4);
  padding: 4px 12px;
  border-radius: 8px;
}

/* 有卡面图片时：弹出后左下角尾号 */
.card-footer-img {
  position: absolute;
  left: 20px;
  bottom: 20px;
  z-index: 5;
}
.card-img-last4-bottom {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 3px;
  background: rgba(0, 0, 0, 0.4);
  padding: 6px 14px;
  border-radius: 8px;
}

.bg-pattern {
  position: absolute;
  top: -20%;
  right: -10%;
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  position: relative;
  z-index: 2;
}
.bank-icon-mock {
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
}
.bank-icon {
  width: 40px;
  height: 40px;
  /* background: rgba(255, 255, 255, 0.2); */
  /* border-radius: 12px; */
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.bank-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.bank-name {
  font-size: 18px;
  font-weight: 600;
  margin-left: 12px;
}
.bank-last4 {
  font-size: 1rem;
  font-weight: normal;
}
.bank-info {
  display: flex;
  align-items: center;
}
.custom-tag {
  background: rgba(255, 255, 255, 0.2) !important;
  border: none !important;
}

.card-number {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  margin: 30px 0;
  position: relative;
  z-index: 2;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  position: relative;
  z-index: 2;
}
.holder-section .label {
  font-size: 14px;
  opacity: 0.5;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.holder-section .value {
  font-size: 14px;
  font-weight: 600;
  margin-top: 4px;
  display: block;
}

/* 卡组织图标 */
.card-org {
  position: absolute;
  right: 0;
  bottom: -20px;
  width: 100px;
  height: 80px;
  /* border-radius: 8px; */
  /* background: rgba(255, 255, 255, 0.9); */
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.card-org img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.card-org-top {
  position: static;
  > img {
    width: 80px;
    height: 80px;
  }
}

/* 操作按钮 */
.card-actions-quick {
  position: absolute;
  right: 0;
  top: 0;
  height: 100%;
  width: 100px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  align-content: space-around;
  padding: 20px 10px 0 10px;
  flex-wrap: nowrap;
  box-sizing: border-box;
}
.action-pill-btn {
  width: 100%;
  background: #ffffff71;
  color: #333;
  border: none;
  padding: 5px 12px;
  border-radius: 30px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);
  font-size: 14px;
}

/* 底部按钮 */
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
  background: #1989fa;
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

.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -30%);
}
</style>
