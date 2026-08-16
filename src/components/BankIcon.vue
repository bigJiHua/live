<template>
  <span class="bank-icon-wrap" :style="wrapStyle">
    <img
      v-if="src && !error"
      class="bank-icon-img"
      :src="src"
      :alt="name || '银行'"
      loading="lazy"
      @error="error = true"
    />
    <span v-else class="bank-icon-mock" :style="mockStyle">
      {{ bankInitial }}
    </span>
  </span>
</template>

<script setup>
import { ref, computed } from "vue";

/**
 * 银行图标：优先展示真实 logo；获取失败（src 为空 / 图片加载错误）时，
 * 统一回退为毛玻璃圆角块 + 银行名首字。
 * 首字规则：
 *   - 「中国银行」→ 显示「中」（白名单例外）
 *   - 以「中国」开头的其他银行（如 中国农业银行）→ 去掉「中国」取首字「农」
 *   - 不以「中国」开头（如 广西农村信用社银行）→ 直接取首字「广」
 *   - name 为空 → 显示空（避免出现 "?" 问号）
 * 用法：<BankIcon :src="iconUrl" :name="bankName" :size="16" />
 */
const props = defineProps({
  src: { type: String, default: "" },
  name: { type: String, default: "" },
  size: { type: [Number, String], default: 16 },
  rounded: { type: [Number, String], default: 6 },
});

const error = ref(false);

const wrapStyle = computed(() => ({
  width: props.size + "px",
  height: props.size + "px",
}));

const mockStyle = computed(() => ({
  fontSize: Math.max(Math.round(Number(props.size) * 0.45), 8) + "px",
  borderRadius: props.rounded + "px",
}));

// 银行名取首字（去"中国"前缀规则 + "中国银行"白名单）
const bankInitial = computed(() => {
  const n = (props.name || "").trim();
  if (!n) return ""; // 空名称不显示 "?"
  if (n === "中国银行" || n.startsWith("中国银行")) return "中";
  if (n.startsWith("中国")) return n.slice(2).charAt(0) || "";
  return n.charAt(0);
});
</script>

<style scoped>
.bank-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  vertical-align: middle;
  overflow: hidden;
}
.bank-icon-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.bank-icon-mock {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: var(--theme-text-secondary, #646566);
  background: rgba(127, 127, 127, 0.16);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  box-shadow: inset 0 0 0 1px rgba(127, 127, 127, 0.2);
  box-sizing: border-box;
}
</style>
