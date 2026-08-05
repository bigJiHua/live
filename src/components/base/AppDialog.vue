<template>
  <app-popup
    v-model:show="visible"
    position="center"
    :round="true"
    :close-on-click-overlay="closeOnClickOverlay"
    @click-overlay="onOverlay"
  >
    <div class="app-dialog">
      <div v-if="title || $slots.title" class="app-dialog__title">
        <slot name="title">{{ title }}</slot>
      </div>
      <div class="app-dialog__content">
        <slot>{{ message }}</slot>
      </div>
      <div v-if="showCancelButton || showConfirmButton" class="app-dialog__footer">
        <button
          v-if="showCancelButton"
          class="app-dialog__btn app-dialog__btn--cancel"
          @click="onCancel"
        >
          {{ cancelButtonText }}
        </button>
        <button
          v-if="showConfirmButton"
          class="app-dialog__btn app-dialog__btn--confirm"
          :class="{ 'app-dialog__btn--danger': theme === 'danger' }"
          @click="onConfirm"
        >
          {{ confirmButtonText }}
        </button>
      </div>
    </div>
  </app-popup>
</template>

<script setup>
import { computed } from 'vue'

// 自写主题化对话框，替代 van-dialog（内部复用 AppPopup）
const props = defineProps({
  show: { type: Boolean, default: false },
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  showCancelButton: { type: Boolean, default: false },
  showConfirmButton: { type: Boolean, default: true },
  confirmButtonText: { type: String, default: '确认' },
  cancelButtonText: { type: String, default: '取消' },
  theme: { type: String, default: '' }, // danger
  closeOnClickOverlay: { type: Boolean, default: true },
  // 关闭前钩子（Vant Dialog 语义）：(action) => boolean | Promise<boolean>，返回 false/reject 阻止关闭
  beforeClose: { type: Function, default: null },
})
const emit = defineEmits(['update:show', 'update:modelValue', 'confirm', 'cancel', 'close'])

const visible = computed({
  get: () => props.show || props.modelValue,
  set: (v) => {
    emit('update:show', v)
    emit('update:modelValue', v)
  },
})

// 执行 before-close 钩子：允许则返回 true，阻止则返回 false
const runBeforeClose = async (action) => {
  if (typeof props.beforeClose !== 'function') return true
  try {
    const result = await props.beforeClose(action)
    return result !== false
  } catch (err) {
    return false
  }
}

const onConfirm = async () => {
  if (!(await runBeforeClose('confirm'))) return
  emit('confirm')
  visible.value = false
}
const onCancel = async () => {
  if (!(await runBeforeClose('cancel'))) return
  emit('cancel')
  visible.value = false
}
const onOverlay = async () => {
  if (!props.closeOnClickOverlay) return
  // 点击遮罩关闭同样走 before-close（action=overlay，Vant 语义一致）
  if (!(await runBeforeClose('overlay'))) return
  visible.value = false
}
</script>

<style scoped>
.app-dialog {
  min-width: 280px;
  max-width: 85vw;
  padding: 20px 16px 0;
  text-align: left;
}
.app-dialog__title {
  font-size: 16px;
  font-weight: 600;
  text-align: left;
  color: var(--theme-text-primary);
  margin-bottom: 10px;
}
.app-dialog__content {
  font-size: 14px;
  text-align: left;
  color: var(--theme-text-secondary);
  line-height: 1.5;
  padding-bottom: 20px;
}
.app-dialog__footer {
  display: flex;
  border-top: 1px solid var(--theme-border);
  margin: 0 -16px;
}
.app-dialog__btn {
  flex: 1;
  height: 48px;
  border: none;
  background: transparent;
  font-size: 15px;
  cursor: pointer;
}
.app-dialog__btn--cancel {
  color: var(--theme-text-secondary);
  border-right: 1px solid var(--theme-border);
}
.app-dialog__btn--confirm {
  color: var(--theme-primary);
  font-weight: 500;
}
.app-dialog__btn--confirm.app-dialog__btn--danger {
  color: var(--theme-danger);
}
</style>
