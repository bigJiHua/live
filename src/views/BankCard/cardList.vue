<template>
  <div class="page-card-list">
    <div
      class="wheel-container"
      @touchstart="handleTouchStart"
      @touchmove.prevent="handleTouchMove"
      @touchend="handleTouchEnd"
    >
      <div class="wheel-scene">
        <div
          class="wheel-rack"
          :style="{
            transform: `rotateX(${currentAngle}deg)`,
            transition: isMoving
              ? 'none'
              : 'transform 0.6s cubic-bezier(0.23, 1, 0.32, 1)',
          }"
        >
          <div
            v-for="(item, idx) in cardList"
            :key="item.id"
            class="wheel-card"
            :style="getCardStyle(idx, item)"
          >
            <div class="card-face">
              <div class="card-glare"></div>
              <div class="card-main">
                <div class="card-row">
                  <div class="bank-logo">{{ item.bank_name[0] }}</div>
                  <div class="bank-name">{{ item.bank_name }}</div>
                  <div class="chip-icon-small"></div>
                </div>
                <div class="card-number">
                  {{ formatNumber(item.card_number) }}
                </div>
                <div class="card-footer">
                  <div class="user-info">
                    <div class="label">PLATINUM CARD</div>
                    <div class="name">{{ item.holder_name }}</div>
                  </div>
                  <van-icon
                    name="contact"
                    size="20"
                    color="rgba(255,255,255,0.3)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="wheel-shadow top"></div>
      <div class="wheel-shadow bottom"></div>
    </div>
  </div>
  <div class="add-btn-wrap">
    <button class="glass-add-btn" @click="openAddDialog">
      <van-icon name="plus" />
      <span>添加借记卡</span>
    </button>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";

const cardList = ref([
  {
    id: 1,
    bank_name: "招商银行",
    card_number: "6214831234567890",
    holder_name: "ALEX WANG",
    color: "#e33e33",
  },
  {
    id: 2,
    bank_name: "建设银行",
    card_number: "6217009876543210",
    holder_name: "ALEX WANG",
    color: "#1b4f9a",
  },
  {
    id: 3,
    bank_name: "工商银行",
    card_number: "6222020001112223",
    holder_name: "ALEX WANG",
    color: "#c01d24",
  },
  {
    id: 4,
    bank_name: "农业银行",
    card_number: "6228480012345678",
    holder_name: "ALEX WANG",
    color: "#2d8c4e",
  },
  {
    id: 5,
    bank_name: "中国银行",
    card_number: "6217850001234567",
    holder_name: "ALEX WANG",
    color: "#e67e22",
  },
  {
    id: 6,
    bank_name: "浦发银行",
    card_number: "6225880011223344",
    holder_name: "ALEX WANG",
    color: "#2c3e50",
  },
]);

// 关键状态：不再记录 activeIndex，只记录总偏转角度
const currentAngle = ref(0);
const radius = 180;
const cardAngle = computed(() => 360 / cardList.value.length);

let startY = 0;
let isMoving = ref(false);

// 计算当前哪一张是“逻辑上”的 active
const activeIndex = computed(() => {
  const count = cardList.value.length;
  // 根据当前总角度，计算出对应的索引
  const rawIndex = Math.round(currentAngle.value / cardAngle.value) % count;
  return (rawIndex + count) % count;
});

const getCardStyle = (idx, item) => {
  const baseAngle = idx * cardAngle.value;

  // 核心：计算每张卡片相对于当前旋转终点（currentAngle）的偏移量
  // 这样无论转了多少圈，卡片都能正确计算出自己是否在正面
  let diff = (baseAngle - currentAngle.value) % 360;
  if (diff > 180) diff -= 360;
  if (diff < -180) diff += 360;

  const absDiff = Math.abs(diff);
  const normalizedDiff = diff / cardAngle.value; // 这里的数值就是 -1, 0, 1 这种相对距离

  return {
    "--card-theme": item.color,
    transform: `
      rotateX(${-baseAngle}deg) 
      translateZ(${radius}px)
      translateY(${normalizedDiff * 18}px)
      scale(${1 - Math.abs(normalizedDiff) * 0.06})
    `,
    "z-index": Math.round(100 - absDiff),
    opacity: absDiff > 90 ? 0 : 1 - absDiff / 100, // 超过 90 度（背面）彻底隐藏
    filter: `brightness(${1 - absDiff / 150}) blur(${absDiff / 40}px)`,
    transition: isMoving.value
      ? "none"
      : "all 0.5s cubic-bezier(0.2, 0.8, 0.4, 1)",
  };
};

// 手势滑动逻辑
const handleTouchStart = (e) => {
  startY = e.touches[0].pageY;
  isMoving.value = true;
};

const handleTouchMove = (e) => {
  if (!isMoving.value) return;
  const delta = (e.touches[0].pageY - startY) * 0.3; // 灵敏度
  currentAngle.value -= delta; // 反向：向下滚动卡片向上旋转
  startY = e.touches[0].pageY;
};

const handleTouchEnd = () => {
  isMoving.value = false;
  // 自动吸附到最近的角度，维持无限滚动的精髓
  const targetStep = Math.round(currentAngle.value / cardAngle.value);
  currentAngle.value = targetStep * cardAngle.value;
};

const formatNumber = (n) => n.replace(/(\d{4})(?=\d)/g, "$1 ");
</script>

<style scoped>
.page-card-list {
  height: 50vh;
  /* background: radial-gradient(circle at center, #111 0%, #000 100%); */
  color: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  margin: 20px 0;
}

.wheel-container {
  flex: 1;
  position: relative;
  perspective: 1200px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.wheel-scene {
  width: 100%;
  height: 400px;
  display: flex;
  justify-content: center;
  align-items: center;
  transform-style: preserve-3d;
}

.wheel-rack {
  position: relative;
  width: 300px;
  height: 180px;
  transform-style: preserve-3d;
}

.wheel-card {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  backface-visibility: visible;
}

.card-face {
  width: 100%;
  height: 100%;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--card-theme) 0%, #000 150%);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.5);
  position: relative;
  overflow: hidden;
}

.card-glare {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.1) 0%,
    transparent 50%
  );
}

.card-main {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-sizing: border-box;
}
.card-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bank-logo {
  width: 28px;
  height: 28px;
  background: #fff;
  color: #000;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  font-size: 12px;
}
.bank-name {
  font-weight: 600;
  flex: 1;
  font-size: 14px;
  opacity: 0.9;
}
.chip-icon-small {
  width: 30px;
  height: 22px;
  background: rgba(255, 215, 0, 0.5);
  border-radius: 4px;
}
.card-number {
  font-size: 18px;
  font-family: monospace;
  letter-spacing: 1px;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.label {
  font-size: 8px;
  opacity: 0.3;
  margin-bottom: 2px;
}
.name {
  font-size: 12px;
  font-weight: 500;
}

.wheel-shadow {
  position: absolute;
  left: 0;
  right: 0;
  height: 150px;
  z-index: 10;
  pointer-events: none;
}
/* .wheel-shadow.top {
  top: 0;
  background: linear-gradient(180deg, #000 0%, transparent 100%);
}
.wheel-shadow.bottom {
  bottom: 0;
  background: linear-gradient(0deg, #000 0%, transparent 100%);
} */

.footer-hint {
  padding: 40px;
  text-align: center;
}
.loop-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid #222;
  font-size: 10px;
  color: #444;
  font-weight: bold;
  margin-bottom: 10px;
}
.footer-hint p {
  font-size: 12px;
  color: #333;
}

/* ============================================
   底部毛玻璃操作栏
   ============================================ */
.add-btn-wrap {
  position: fixed;
  bottom: 30px;
  left: 20px;
  right: 20px;
  z-index: 10;
}
.glass-add-btn {
  width: 100%;
  height: 54px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 27px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1989fa;
  box-shadow: 0 10px 30px rgba(25, 137, 250, 0.15);
  transition: all 0.3s;
}
.glass-add-btn:active {
  transform: scale(0.97);
  background: rgba(255, 255, 255, 0.95);
}
</style>
