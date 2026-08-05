<template>
  <Teleport :to="teleport">
    <div class="app-popup-layer">
      <Transition name="app-popup-fade">
        <div v-if="visible && overlay" class="app-popup__overlay" @click="onOverlay"></div>
      </Transition>
      <Transition :name="transitionName">
        <div
          v-if="visible"
          class="app-popup"
          :class="[`app-popup--${position}`, { 'app-popup--round': round }]"
          :style="panelStyle"
        >
          <div v-if="closeable" class="app-popup__close" @click="close">×</div>
          <slot />
        </div>
      </Transition>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, useAttrs } from 'vue'

// 自写主题化弹层，替代 van-popup
// 支持 v-model:show（兼容 :show / v-model）、position(bottom/top/left/right/center)、round、overlay、
// close-on-click-overlay、teleport；父级 :style 透传到面板
const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: Boolean, default: false },
  position: { type: String, default: 'bottom' }, // bottom | top | left | right | center
  round: { type: Boolean, default: false },
  overlay: { type: Boolean, default: true },
  closeable: { type: Boolean, default: false },
  closeOnClickOverlay: { type: Boolean, default: true },
  teleport: { type: String, default: 'body' },
})
const emit = defineEmits(['update:show', 'update:modelValue', 'open', 'close', 'click-overlay'])

const attrs = useAttrs()
const visible = computed({
  get: () => props.show || props.modelValue,
  set: (v) => {
    emit('update:show', v)
    emit('update:modelValue', v)
    if (!v) emit('close')
  },
})

const panelStyle = computed(() => {
  const base = {}
  if (props.round) {
    base.borderRadius = props.position === 'center' ? '12px' : '16px 16px 0 0'
  }
  return [attrs.style || '', base]
})

const transitionName = computed(() => {
  if (props.position === 'bottom') return 'app-slide-up'
  if (props.position === 'top') return 'app-slide-down'
  if (props.position === 'left' || props.position === 'right') return 'app-slide-left'
  return 'app-popup-zoom'
})

const close = () => {
  visible.value = false
}
const onOverlay = () => {
  emit('click-overlay')
  if (props.closeOnClickOverlay) close()
}
</script>

<style scoped>
.app-popup-layer {
  position: fixed;
  inset: 0;
  z-index: 2000;
  /* 未显示时让点击穿透，避免常驻全屏层吞掉底部 Tab 导航的点击 */
  pointer-events: none;
}
.app-popup__overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  pointer-events: auto;
}
.app-popup {
  position: absolute;
  pointer-events: auto;
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
  box-sizing: border-box;
  max-height: 90%;
  overflow: auto;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
}
.app-popup--bottom {
  left: 0;
  right: 0;
  bottom: 0;
}
.app-popup--top {
  left: 0;
  right: 0;
  top: 0;
}
.app-popup--left {
  top: 0;
  bottom: 0;
  left: 0;
}
.app-popup--right {
  top: 0;
  bottom: 0;
  right: 0;
}
.app-popup--center {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  min-width: 280px;
}
.app-popup__close {
  position: absolute;
  top: 10px;
  right: 12px;
  font-size: 20px;
  color: var(--theme-text-tertiary);
  cursor: pointer;
  line-height: 1;
  z-index: 1;
}

/* 进出场动画 */
.app-popup-fade-enter-active,
.app-popup-fade-leave-active,
.app-slide-up-enter-active,
.app-slide-up-leave-active,
.app-slide-down-enter-active,
.app-slide-down-leave-active,
.app-slide-left-enter-active,
.app-slide-left-leave-active,
.app-popup-zoom-enter-active,
.app-popup-zoom-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.app-popup-fade-enter-from,
.app-popup-fade-leave-to {
  opacity: 0;
}
.app-slide-up-enter-from,
.app-slide-up-leave-to {
  transform: translateY(100%);
}
.app-slide-down-enter-from,
.app-slide-down-leave-to {
  transform: translateY(-100%);
}
.app-slide-left-enter-from,
.app-slide-left-leave-to {
  transform: translateX(-100%);
}
.app-popup-zoom-enter-from,
.app-popup-zoom-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.9);
}
</style>
