<template>
  <div class="app-grid" :style="gridStyle">
    <slot />
  </div>
</template>

<script setup>
import { computed, provide } from 'vue'

const props = defineProps({
  // 列数
  columnNum: { type: [Number, String], default: 4 },
  // 是否显示分割边框（默认不显示，保持干净）
  border: { type: Boolean, default: false },
  // 子项是否可点击（提供 active 反馈）
  clickable: { type: Boolean, default: false },
  // 间隔
  gutter: { type: [Number, String], default: 0 },
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${Number(props.columnNum) || 4}, minmax(0, 1fr))`,
  gap: typeof props.gutter === 'number' ? `${props.gutter}px` : props.gutter,
}))

// 供 AppGridItem 读取 grid 级 clickable
provide('app-grid-clickable', computed(() => props.clickable))
</script>

<style scoped>
.app-grid {
  display: grid;
  width: 100%;
  box-sizing: border-box;
}
</style>
