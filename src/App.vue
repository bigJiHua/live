<template>
  <router-view />
  <PinVerifyDialog ref="pinDialogRef" />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import PinVerifyDialog from '@/components/PinVerifyDialog.vue'
import { registerPinDialog, unregisterPinDialog } from '@/utils/request/pin'

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
