<template>
  <span
    class="app-tag"
    :class="[
      `app-tag--${type}`,
      `app-tag--${size}`,
      {
        'app-tag--plain': plain,
        'app-tag--round': round,
        'app-tag--mark': mark,
        'app-tag--closeable': closeable,
      },
    ]"
    :style="customStyle"
  >
    <slot />
    <span v-if="closeable" class="app-tag__close" @click.stop="$emit('close')">×</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

// 自写主题化标签，替代 van-tag
// type 映射到主题状态色；color 为数据驱动自定义色（如分类色），plain 时作描边
const props = defineProps({
  type: { type: String, default: 'default' }, // primary | success | warning | danger | default
  plain: { type: Boolean, default: false },
  round: { type: Boolean, default: false },
  mark: { type: Boolean, default: false },
  closeable: { type: Boolean, default: false },
  size: { type: String, default: 'medium' }, // large | medium | small
  color: { type: String, default: '' }, // 自定义背景色（数据驱动）
  textColor: { type: String, default: '' },
})

const emit = defineEmits(['close'])

const customStyle = computed(() => {
  if (!props.color) return {}
  return props.plain
    ? { color: props.color, borderColor: props.color, background: 'transparent' }
    : { color: props.textColor || '#fff', background: props.color, borderColor: props.color }
})
</script>

<style scoped>
.app-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  box-sizing: border-box;
  padding: 2px 6px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--theme-text-secondary);
  background: var(--theme-bg-tertiary);
}

.app-tag--large {
  font-size: 14px;
  padding: 4px 10px;
}
.app-tag--small {
  font-size: 10px;
  padding: 1px 4px;
}

/* 状态色（复用主题 token） */
.app-tag--primary {
  color: #fff;
  background: var(--theme-primary);
}
.app-tag--success {
  color: #fff;
  background: var(--theme-success);
}
.app-tag--warning {
  color: #fff;
  background: var(--theme-warning);
}
.app-tag--danger {
  color: #fff;
  background: var(--theme-danger);
}

/* plain 描边变体 */
.app-tag--plain.app-tag--primary {
  color: var(--theme-primary);
  background: transparent;
  border-color: var(--theme-primary);
}
.app-tag--plain.app-tag--success {
  color: var(--theme-success);
  background: transparent;
  border-color: var(--theme-success);
}
.app-tag--plain.app-tag--warning {
  color: var(--theme-warning);
  background: transparent;
  border-color: var(--theme-warning);
}
.app-tag--plain.app-tag--danger {
  color: var(--theme-danger);
  background: transparent;
  border-color: var(--theme-danger);
}

.app-tag--round {
  border-radius: 999px;
}
.app-tag--mark {
  border-radius: 0 999px 999px 0;
}
.app-tag__close {
  margin-left: 2px;
  cursor: pointer;
  opacity: 0.7;
  font-size: 1.1em;
  line-height: 1;
}
.app-tag__close:hover {
  opacity: 1;
}
</style>
