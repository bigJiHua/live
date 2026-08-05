<template>
  <div
    class="org-icon"
    :class="[`org-${org}`, isFilled ? 'filled' : 'glass']"
    :style="iconStyle"
  >
    <div class="org-inner">
      <!-- 1. 银联 UnionPay -->
      <template v-if="org === 'unionpay'">
        <span class="bar bar-red"></span>
        <span class="bar bar-green"></span>
        <span class="bar bar-blue"></span>
        <span class="up-text">银联</span>
      </template>

      <!-- 2. 万事达 Mastercard -->
      <template v-else-if="org === 'mastercard'">
        <span class="circle circle-red"></span>
        <span class="circle circle-yellow"></span>
        <span class="mc-text">mastercard</span>
      </template>

      <!-- 3. Visa -->
      <template v-else-if="org === 'visa'">
        <span class="visa-text">VISA</span>
      </template>

      <!-- 4. 运通 American Express -->
      <template v-else-if="org === 'amex'">
        <span class="amex-text">AMERICAN<br />EXPRESS</span>
      </template>

      <!-- 5. 大莱 Diners Club -->
      <template v-else-if="org === 'diners'">
        <span class="diners-text">Diners Club<br />INTERNATIONAL</span>
      </template>

      <!-- 6. JCB -->
      <template v-else-if="org === 'jcb'">
        <span class="jcb-block jcb-b-1"></span>
        <span class="jcb-block jcb-b-2"></span>
        <span class="jcb-block jcb-b-3"></span>
        <span class="jcb-text">JCB</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";

/**
 * 卡组织 Icon（填色 / 不填色 两种版本，尺寸动态可调）
 * 用法：
 *   <CardOrgIcon org="unionpay" />                      // 默认 80×50，不填色（透明底白 logo）
 *   <CardOrgIcon org="visa" :filled="true" />           // 填色（官方品牌色 logo）
 *   <CardOrgIcon org="jcb" small />                     // 小号预设 48×30
 *   <CardOrgIcon org="amex" :width="100" :height="60" /> // 自定义尺寸，内部元素等比缩放
 *
 * org 取值：unionpay | mastercard | visa | amex | diners | jcb
 * 说明：容器透明无背景填充；内层以 80×50 为基准按 width/80 等比缩放，文字/图形随尺寸联动。
 */
const props = defineProps({
  org: { type: String, default: "visa" },
  // filled=true → 填色（官方品牌色）；false → 不填色（白色 logo，透明底）
  filled: { type: Boolean, default: false },
  // 兼容旧用法：glass | filled
  variant: { type: String, default: "" },
  // 小号预设（48×30），指定 width/height 时以显式值为准
  small: { type: Boolean, default: false },
  // 自定义尺寸（px），0 表示不指定走默认
  width: { type: Number, default: 0 },
  height: { type: Number, default: 0 },
});

const isFilled = computed(
  () => props.filled || props.variant === "filled"
);

// 实际尺寸：显式 width/height 优先，其次 small 预设，最后默认 80×50
const iconW = computed(() => props.width || (props.small ? 48 : 80));
const iconH = computed(() => props.height || (props.small ? 30 : 50));

// 缩放因子：同时适配宽高，保证内层(80×50基准)在容器内完整显示
const iconStyle = computed(() => ({
  width: `${iconW.value}px`,
  height: `${iconH.value}px`,
  "--org-s": Math.min(iconW.value / 80, iconH.value / 50),
}));
</script>

<style scoped>
/* 容器：透明无背景，仅承载 logo */
.org-icon {
  position: relative;
  width: 80px;
  height: 50px;
  border-radius: 8px;
  overflow: hidden;
  color: #fff;
  font-weight: 800;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

/* 内层基准层：80×50，居中并按 width/80 等比缩放 */
.org-inner {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 80px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%) scale(var(--org-s));
  transform-origin: center;
}

/* 1. 银联：三个倒角梯形（flex 一行居中无间隙拼接）+ 居中「银联」文字 */
.org-unionpay {
  gap: 0;
}
.org-unionpay .bar {
  width: 21px;
  height: 38px;
  background: #fff;
  transform: skewX(-15deg);
  border-radius: 4px;
}
.org-unionpay .bar-red { opacity: 0.5; }
.org-unionpay .bar-blue { opacity: 0.85; }
.org-unionpay .bar-green { opacity: 0.6; }
.org-unionpay .up-text {
  position: absolute;
  font-size: 10px;
  font-weight: 800;
  font-style: italic;
  letter-spacing: 1px;
  z-index: 2;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

/* 2. 万事达：两个白色圆（垂直居中，透明度差异）+ 底部文字 */
.org-mastercard .circle {
  position: absolute;
  width: 32px;
  height: 32px;
  top: 50%;
  transform: translateY(-50%);
  border-radius: 50%;
  background: #fff;
}
.org-mastercard .circle-red { left: 12px; opacity: 0.7; }
.org-mastercard .circle-yellow { right: 12px; opacity: 0.45; }
.org-mastercard .mc-text {
  position: absolute;
  bottom: 2px;
  font-size: 7px;
  font-style: italic;
  letter-spacing: 1px;
  text-transform: lowercase;
  z-index: 3;
}

/* 3. VISA：白字 */
.org-visa .visa-text {
  font-size: 17px;
  font-weight: 900;
  font-style: italic;
  letter-spacing: 1px;
  color: #fff;
  padding-left: 3px;
}

/* 4. 运通：白字 */
.org-amex .amex-text {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 1.5px;
  text-align: center;
  line-height: 1.2;
  color: #fff;
}

/* 5. 大莱：白字 */
.org-diners .diners-text {
  font-size: 7px;
  font-weight: 700;
  text-align: center;
  line-height: 1.3;
  color: #fff;
}

/* 6. JCB：三个竖排方块 + 银色 JCB 文字 */
.org-jcb .jcb-block {
  position: absolute;
  width: 18px;
  height: 74%;
  top: 13%;
  background: #fff;
  border-radius: 10px 0 10px 0;
}
.org-jcb .jcb-b-1 { left: 11px; opacity: 0.55; }
.org-jcb .jcb-b-2 { left: 31px; opacity: 0.85; }
.org-jcb .jcb-b-3 { left: 51px; opacity: 0.65; }
.org-jcb .jcb-text {
  position: relative;
  z-index: 3;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 1px;
  color: #fff;
  font-style: italic;
}

/* ===== 填色版本（官方品牌色）：去除白色背景，仅保留品牌色元素 ===== */
/* 银联：官方三色斜块 + 银色文字，透明底 */
.org-unionpay.filled .bar-red { background: #e21836; opacity: 1; }
.org-unionpay.filled .bar-blue { background: #00447c; opacity: 1; }
.org-unionpay.filled .bar-green { background: #007b84; opacity: 1; }
/* 万事达：官方红黄双圆，透明底 */
.org-mastercard.filled .circle-red { background: #eb001b; opacity: 1; }
.org-mastercard.filled .circle-yellow { background: #f79e1b; opacity: 1; }
.org-mastercard.filled .mc-text { color: #231f20; text-shadow: none; }
/* Visa / 运通 / 大莱：官方品牌色实底 + 白字（保留，品牌色非白色） */
.org-visa.filled { background: #1a1f71; }
.org-visa.filled .visa-text { color: #fff; }
.org-amex.filled { background: #2e77bc; }
.org-amex.filled .amex-text { color: #fff; }
.org-diners.filled { background: #0067b1; }
.org-diners.filled .diners-text { color: #fff; }
/* JCB：官方三色块 + 银色文字，透明底 */
.org-jcb.filled .jcb-b-1 { background: #0066b2; opacity: 1; }
.org-jcb.filled .jcb-b-2 { background: #e60012; opacity: 1; }
.org-jcb.filled .jcb-b-3 { background: #00a94f; opacity: 1; }
</style>
