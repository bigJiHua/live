<template>
  <div
    class="app-cell"
    :class="{ 'app-cell--center': center, 'app-cell--borderless': !border }"
    @click="onClick"
  >
    <div v-if="icon || $slots.icon" class="app-cell__icon">
      <slot name="icon"><van-icon v-if="icon" :name="icon" /></slot>
    </div>
    <div v-if="$slots.default" class="app-cell__body">
      <slot />
    </div>
    <template v-else>
      <div class="app-cell__body">
        <div class="app-cell__title">
          <slot name="title">{{ title }}</slot>
        </div>
        <div v-if="label || $slots.label" class="app-cell__label">
          <slot name="label">{{ label }}</slot>
        </div>
      </div>
      <div class="app-cell__value">
        <slot name="value">{{ value }}</slot>
      </div>
    </template>
    <div v-if="isLink || $slots['right-icon']" class="app-cell__right-icon">
      <slot name="right-icon"><van-icon name="arrow" /></slot>
    </div>
  </div>
</template>

<script setup>
// 自写主题化单元格，替代 van-cell
// 支持 title/value/label/icon (prop 或 slot) + is-link + border 控制；to 走 @click 由调用方处理
const props = defineProps({
  title: { type: [String, Number], default: '' },
  value: { type: [String, Number], default: '' },
  label: { type: [String, Number], default: '' },
  icon: { type: String, default: '' },
  isLink: { type: Boolean, default: false },
  border: { type: Boolean, default: true },
  center: { type: Boolean, default: false },
})

const emit = defineEmits(['click'])
const onClick = (e) => emit('click', e)
</script>

<style scoped>
.app-cell {
  display: flex;
  align-items: flex-start;
  box-sizing: border-box;
  width: 100%;
  padding: 12px 16px;
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
  font-size: 14px;
  cursor: pointer;
}
.app-cell--center {
  align-items: center;
}
.app-cell--borderless {
  border-bottom: none;
}
.app-cell:not(.app-cell--borderless) {
  border-bottom: 1px solid var(--theme-border);
}
.app-cell__icon {
  flex-shrink: 0;
  margin-right: 10px;
  font-size: 18px;
  color: var(--theme-primary);
  display: flex;
  align-items: center;
}
.app-cell__body {
  flex: 1;
  min-width: 0;
}
.app-cell__title {
  color: var(--theme-text-primary);
}
.app-cell__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--theme-text-tertiary);
  line-height: 1.4;
}
.app-cell__value {
  flex-shrink: 0;
  margin-left: 8px;
  max-width: 60%;
  text-align: right;
  color: var(--theme-text-secondary);
  word-break: break-all;
}
.app-cell__right-icon {
  flex-shrink: 0;
  margin-left: 6px;
  display: flex;
  align-items: center;
  color: var(--theme-text-tertiary);
  font-size: 16px;
}
</style>
