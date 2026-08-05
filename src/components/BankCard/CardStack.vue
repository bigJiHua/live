<template>
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
        }"
        :style="getCardStyle(item, index)"
        @click.stop="handleCardClick(item)"
      >
        <!-- 隐藏的图片用于检测卡面图片加载失败 -->
        <img
          v-if="item.cardImgUrl"
          :src="item.cardImgUrl"
          style="display: none;"
          @error="emit('card-img-error', item.id)"
        />
        <div class="bg-pattern" v-if="!item.cardImgUrl || cardImgErrors[item.id]"></div>

        <!-- 无卡面图片或图片加载失败时：显示左上角银行信息 -->
        <div class="card-header" v-if="!item.cardImgUrl || cardImgErrors[item.id]">
          <div class="bank-info">
            <div class="bank-icon" v-if="item.bankIconUrl && !bankIconErrors[item.id]">
              <img :src="item.bankIconUrl" :alt="item.bankName" @error="emit('bank-icon-error', item.id)" />
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
          <app-tag v-if="item.isDefault || item.is_default" class="custom-tag"
            >默认</app-tag>
        </div>

        <!-- 有卡面图片且加载成功时：右上角显示尾号后四位（弹出后隐藏） -->
        <div class="card-header-img" v-if="item.cardImgUrl && !cardImgErrors[item.id] && selectedId !== item.id">
          <app-tag v-if="item.isDefault || item.is_default" class="custom-tag"
            >默认</app-tag>
          <span class="card-img-last4">{{ item.last4No }}</span>
        </div>

        <div class="card-number" v-if="!item.cardImgUrl || cardImgErrors[item.id]">
          {{ formatCardNo(item) }}
        </div>

        <div class="card-footer" v-if="!item.cardImgUrl || cardImgErrors[item.id]">
          <div class="holder-section">
            <span class="label">{{ cardTypeLabel }}</span>
            <span class="value">{{
              item.alias || item.cardLevel || "银行卡"
            }}</span>
          </div>

          <!-- 卡组织图标 - 左下角默认：已有 logo 图且能打开则展示图，否则用彩色组件兜底 -->
          <div
            class="card-org"
            v-if="item.cardOrgIconUrl && !orgIconErrors[item.id] && selectedId === null"
          >
            <img :src="item.cardOrgIconUrl" alt="卡组织" @error="onOrgImgError(item.id)" />
          </div>
          <CardOrgIcon
            v-else-if="selectedId === null && toOrgKey(item)"
            class="card-org"
            :org="toOrgKey(item)"
            :width="orgSize(item).width"
            :height="orgSize(item).height"
            :filled="true"
          />
        </div>

        <!-- 有卡面图片且加载成功时：弹出后左下角显示尾号 -->
        <div class="card-footer-img" v-if="item.cardImgUrl && !cardImgErrors[item.id] && selectedId === item.id">
          <span class="card-img-last4-bottom">{{ item.last4No }}</span>
        </div>

        <transition name="fade">
          <div class="card-actions-quick" v-if="selectedId === item.id">
            <button class="action-pill-btn" @click.stop="emit('edit', item)">
              <van-icon name="setting-o" />
              <span>管理</span>
            </button>
            <!-- 卡片弹出后右下角卡组织：仅在无卡面图片时展示（有卡面则不显示），logo 图加载失败回退组件 -->
            <div
              class="card-org-floating"
              v-if="(!item.cardImgUrl || cardImgErrors[item.id]) && item.cardOrgIconUrl && !orgIconErrors[item.id] && selectedId === item.id"
            >
              <img :src="item.cardOrgIconUrl" alt="卡组织" @error="onOrgImgError(item.id)" />
            </div>
            <CardOrgIcon
              v-else-if="(!item.cardImgUrl || cardImgErrors[item.id]) && selectedId === item.id && toOrgKey(item)"
              class="card-org-floating"
              :org="toOrgKey(item)"
              :width="orgSize(item).width"
              :height="orgSize(item).height"
              :filled="true"
            />
          </div>
        </transition>
      </div>
    </div>

    <!-- 底部毛玻璃关闭按钮：仅选中时显示 -->
    <transition name="fade">
      <button
        v-if="selectedId !== null"
        class="close-btn"
        @click.stop="emit('update:selectedId', null)"
        aria-label="关闭"
      >
        <van-icon name="cross" />
      </button>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onUnmounted } from "vue";
import CardOrgIcon from "@/components/BankCard/org/CardOrgIcon.vue";
import { ORG_NAME_MAP } from "@/components/BankCard/org/orgMap.js";

// 把卡片的卡组织字段（中文名）映射为组件 org key
const toOrgKey = (item) => {
  const name = item.cardOrg || item.card_org;
  return (name && ORG_NAME_MAP[name]) || "";
};

// 卡组织角标尺寸：银联/万事达/visa/大莱/JCB 统一 80×40；运通 50×50
const orgSize = (item) => {
  const k = toOrgKey(item);
  if (k === "amex") return { width: 50, height: 50 };
  return { width: 80, height: 40 };
};

// 卡组织 logo 图加载失败记录（src 存在但打不开 → 回退到组件）
const orgIconErrors = reactive({});
const onOrgImgError = (id) => {
  orgIconErrors[id] = true;
};

const props = defineProps({
  cardList: { type: Array, required: true },
  selectedId: { type: [String, Number, null], default: null },
  defaultColor: { type: String, default: "var(--theme-primary, #4A90E2)" },
  cardTypeLabel: { type: String, default: "DEBIT CARD" },
  cardImgErrors: { type: Object, default: () => ({}) },
  bankIconErrors: { type: Object, default: () => ({}) },
});
const emit = defineEmits([
  "update:selectedId",
  "edit",
  "card-img-error",
  "bank-icon-error",
]);

// 锁/解锁 body 滚动：选中卡片时禁用页面滚动
const lockScroll = () => {
  document.body.style.overflow = "hidden";
};
const unlockScroll = () => {
  document.body.style.overflow = "";
};
watch(
  () => props.selectedId,
  (val) => {
    if (val !== null) lockScroll();
    else unlockScroll();
  }
);
onUnmounted(unlockScroll);

const handleCardClick = (item) => {
  if (props.selectedId === item.id) {
    emit("update:selectedId", null);
  } else {
    emit("update:selectedId", item.id);
  }
};

const getCardStyle = (item, index) => {
  const style = {
    "--card-color": item.color || props.defaultColor,
    "--stack-offset": `${index * 70}px`,
    "z-index": props.selectedId === item.id ? 99999 : index,
    "background-color": item.color || props.defaultColor,
  };

  if (item.cardImgUrl && !props.cardImgErrors[item.id]) {
    style.backgroundImage = `url(${item.cardImgUrl}), linear-gradient(135deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.45) 150%)`;
  }

  return style;
};

const formatCardNo = (item) => {
  const bin = item.card_bin || item.cardBin || "";
  const length = parseInt(item.card_length || item.cardLength || "16");
  const last4 = item.last4_no || item.last4No || "****";
  const middleLength = length - bin.length - 4;
  const middleStars = middleLength > 0 ? "*".repeat(middleLength) : "";
  const fullNo = bin + middleStars + last4;
  return fullNo.match(/.{1,4}/g)?.join(" ") || fullNo;
};
</script>

<style scoped>
.card-stack-container {
  position: relative;
  z-index: 9999;
}

/* 单卡专属遮罩：覆盖在该压暗卡片之上，捕获点击关闭弹窗 */
.stack-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 95000;
}
.stack-backdrop.is-visible {
  opacity: 1;
  pointer-events: auto;
}

.card-stack {
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
  background-color: var(--card-color);
  background-image: linear-gradient(135deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.45) 150%);
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  top: var(--stack-offset);
  transition: transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), top 0.5s ease,
    filter 0.4s ease, box-shadow 0.4s ease;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.bank-card-item.is-selected {
  /* 固定到遮罩顶部（top:24px），position:fixed 完全脱离 card-stack 容器 */
  position: fixed;
  top: 24px;
  left: 20px;
  right: 20px;
  z-index: 99999;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.35);
  filter: brightness(1.1);
  /* translate3d 强制 GPU 合成层 */
  transform: translate3d(0, 0, 0) scale(1.05);
  will-change: top, transform, box-shadow, filter, opacity;
  /* 固定曲线无弹性 + 较短时长 + 全部属性统一过渡保证丝滑 */
  transition: top 0.38s cubic-bezier(0.4, 0, 0.2, 1),
    transform 0.38s cubic-bezier(0.4, 0, 0.2, 1),
    box-shadow 0.3s ease, filter 0.3s ease, opacity 0.3s ease;
}

.bank-card-item.is-dimmed {
  filter: brightness(0.5) blur(1px);
  transform: translateY(10px) scale(0.95);
  opacity: 0.6;
  /* 关键：禁用压暗卡片的点击，让点击穿透到遮罩，避免误开新卡 */
  pointer-events: none;
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

/* 卡组织图标（右下角角标，底部留 20px，右侧留 12px） */
.card-org {
  position: absolute;
  right: 12px;
  bottom: 20px;
  max-width: 100px;
  max-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* 操作列内的浮动卡组织：底部抬升避免贴地 */
.card-org-floating {
  margin-bottom: 16px;
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
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
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

.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -30%);
}

/* 底部毛玻璃关闭按钮 */
.close-btn {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
  color: #fff;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 100000;
  transition: transform 0.2s ease, background 0.2s ease;
}
.close-btn:active {
  transform: translateX(-50%) scale(0.92);
  background: rgba(255, 255, 255, 0.3);
}
</style>
