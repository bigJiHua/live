<template>
  <button
    class="app-btn"
    :class="btnClass"
    :type="nativeType"
    :disabled="isDisabled"
    @click="handleClick"
  >
    <span v-if="loading" class="app-btn__spinner" aria-hidden="true"></span>
    <span v-else-if="icon || $slots.icon" class="app-btn__icon">
      <slot name="icon"><van-icon v-if="icon" :name="icon" /></slot>
    </span>
    <span class="app-btn__content"><slot>{{ loading ? loadingText : '' }}</slot></span>
  </button>
</template>

<script setup>
import { computed } from 'vue'

// 自写主题化按钮：完全由 --theme-* token 驱动，不依赖 Vant 的 van-button
// prop 命名对齐 van-button，便于平滑迁移
const props = defineProps({
  // primary（主色渐变实心）| default（白卡描边）| success | warning | danger | text（纯文字）
  type: { type: String, default: 'default' },
  plain: { type: Boolean, default: false },
  // large | normal | small | mini
  size: { type: String, default: 'normal' },
  block: { type: Boolean, default: false },
  round: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '' },
  // submit | button | reset（form 提交兼容 van-button native-type）
  nativeType: { type: String, default: 'button' },
  icon: { type: String, default: '' },
})

const emit = defineEmits(['click'])

const isDisabled = computed(() => props.disabled || props.loading)
const btnClass = computed(() => [
  `app-btn--${props.type}`,
  `app-btn--${props.size}`,
  {
    'app-btn--block': props.block,
    'app-btn--round': props.round,
    'app-btn--plain': props.plain,
    'app-btn--loading': props.loading,
    'app-btn--disabled': isDisabled.value,
  },
])

const handleClick = (e) => {
  if (isDisabled.value) return
  emit('click', e)
}
</script>

<style scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-sizing: border-box;
  margin: 0;
  font-family: inherit;
  font-weight: 500;
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  border: 1px solid transparent;
  border-radius: 8px;
  outline: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.12s ease, opacity 0.12s ease,
    background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.app-btn:active:not(.app-btn--disabled) {
  transform: scale(0.97);
}
.app-btn--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ===== 尺寸 ===== */
.app-btn--large {
  height: 50px;
  padding: 0 18px;
  font-size: 16px;
}
.app-btn--normal {
  height: 44px;
  padding: 0 16px;
  font-size: 15px;
}
.app-btn--small {
  height: 34px;
  padding: 0 12px;
  font-size: 13px;
}
.app-btn--mini {
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
}
.app-btn--block {
  display: flex;
  width: 100%;
}
.app-btn--round {
  border-radius: 999px;
}

/* ===== 主色（渐变实心 + 半透明阴影） ===== */
.app-btn--primary:not(.app-btn--plain) {
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad));
  color: var(--van-button-primary-color, #fff);
  box-shadow: 0 4px 12px rgba(var(--theme-primary-rgb), 0.25);
}
.app-btn--primary.app-btn--plain {
  background: transparent;
  color: var(--theme-primary);
  border-color: var(--theme-primary);
}

/* ===== 默认（白卡 + 描边） ===== */
.app-btn--default:not(.app-btn--plain) {
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
  border-color: var(--theme-border);
}
.app-btn--default.app-btn--plain {
  background: transparent;
  color: var(--theme-text-secondary);
  border-color: var(--theme-border);
}

/* ===== 纯文字按钮（无背景无边框） ===== */
.app-btn--text {
  background: transparent;
  border-color: transparent;
  color: var(--theme-primary);
  padding-left: 8px;
  padding-right: 8px;
}

/* ===== 状态色（复用主题 token，保证深浅色可读） ===== */
.app-btn--success:not(.app-btn--plain) {
  background: var(--theme-success);
  color: #fff;
}
.app-btn--success.app-btn--plain {
  background: transparent;
  color: var(--theme-success);
  border-color: var(--theme-success);
}
.app-btn--warning:not(.app-btn--plain) {
  background: var(--theme-warning);
  color: #fff;
}
.app-btn--warning.app-btn--plain {
  background: transparent;
  color: var(--theme-warning);
  border-color: var(--theme-warning);
}
.app-btn--danger:not(.app-btn--plain) {
  background: var(--theme-danger);
  color: #fff;
}
.app-btn--danger.app-btn--plain {
  background: transparent;
  color: var(--theme-danger);
  border-color: var(--theme-danger);
}

/* ===== 图标与加载 ===== */
.app-btn__icon {
  display: inline-flex;
  align-items: center;
  font-size: 1.1em;
}
.app-btn__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: app-btn-spin 0.7s linear infinite;
}
@keyframes app-btn-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
