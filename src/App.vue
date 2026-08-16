<template>
  <router-view />
  <PinVerifyDialog ref="pinDialogRef" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import PinVerifyDialog from '@/components/PinVerifyDialog.vue'
import { registerPinDialog, unregisterPinDialog } from '@/utils/request/pin'
import { useUiTheme } from '@/composables/useUiTheme'
import { useMoneyColor } from '@/composables/useMoneyColor'

// 初始化全局 UI 主题：读取 localStorage + 监听系统深色模式，注入 CSS 变量到 :root
useUiTheme()

// 初始化全局收支金额颜色：读取 localStorage，注入 --money-income/--money-expense 变量到 :root
useMoneyColor()

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
