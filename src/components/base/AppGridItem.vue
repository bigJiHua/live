<template>
  <div
    class="app-grid-item"
    :class="{ 'is-clickable': clickable }"
    @click="onClick"
  >
    <div v-if="$slots.icon" class="app-grid-item__icon">
      <slot name="icon" />
    </div>
    <div class="app-grid-item__text">
      <slot name="text" />
      <slot />
    </div>
  </div>
</template>

<script setup>
import { inject, computed } from 'vue'

const props = defineProps({
  // 允许单独覆盖 grid 级 clickable
  clickable: { type: Boolean, default: undefined },
})
const emit = defineEmits(['click'])

const gridClickable = inject('app-grid-clickable', null)
const clickable = computed(() =>
  props.clickable !== undefined
    ? props.clickable
    : gridClickable
      ? gridClickable.value
      : false
)

const onClick = (e) => emit('click', e)
</script>

<style scoped>
.app-grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 4px;
  box-sizing: border-box;
  min-width: 0;
}

.is-clickable {
  cursor: pointer;
  border-radius: 10px;
  transition: background-color 0.15s ease, opacity 0.15s ease;
}

.is-clickable:active {
  background-color: var(--theme-bg-tertiary);
  opacity: 0.7;
}

.app-grid-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  line-height: 1;
}

.app-grid-item__text {
  font-size: 13px;
  color: var(--theme-text-secondary);
  text-align: center;
  min-width: 0;
}
</style>
