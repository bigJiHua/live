<template>
  <router-view />
  <PinVerifyDialog ref="pinDialogRef" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import PinVerifyDialog from '@/components/PinVerifyDialog.vue'
import { registerPinDialog, unregisterPinDialog } from '@/utils/request/pin'
import { useUiTheme } from '@/composables/useUiTheme'

// 初始化全局 UI 主题：读取 localStorage + 监听系统深色模式，注入 CSS 变量到 :root
useUiTheme()

const pinDialogRef = ref(null)

onMounted(() => {
  if (pinDialogRef.value) {
    registerPinDialog(pinDialogRef.value)
  }
})

onUnmounted(() => {
  unregisterPinDialog()
})
</script>
