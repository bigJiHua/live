<template>
  <form class="app-form" :class="{ 'app-form--invalid': invalid }" @submit.prevent="onSubmit">
    <slot />
  </form>
</template>

<script setup>
import { provide, ref } from 'vue'

// 自写主题化表单，替代 van-form
// 通过 provide('app-form') 收集 AppField，submit 时统一校验 rules，聚合 values（按 field.name）
const props = defineProps({
  showError: { type: Boolean, default: true },
  validateFirst: { type: Boolean, default: false },
})
const emit = defineEmits(['submit', 'failed'])

const invalid = ref(false)
const fields = []
const registerField = (f) => {
  if (!fields.find((x) => x.name === f.name)) fields.push(f)
}
const unregisterField = (name) => {
  const i = fields.findIndex((x) => x.name === name)
  if (i >= 0) fields.splice(i, 1)
}
provide('app-form', { registerField, unregisterField })

const validate = async () => {
  const errors = []
  for (const f of fields) {
    const r = await f.validate()
    if (r && r.message) {
      errors.push(r)
      if (props.validateFirst) break
    }
  }
  invalid.value = errors.length > 0
  return errors
}

const onSubmit = async () => {
  const errors = await validate()
  if (errors.length) {
    emit('failed', errors)
    return
  }
  const values = {}
  fields.forEach((f) => {
    if (f.name != null) values[f.name] = f.getValue()
  })
  emit('submit', values)
}

defineExpose({ validate, resetValidation: () => fields.forEach((f) => f.resetValidation()) })
</script>

<style scoped>
.app-form {
  display: block;
}
</style>
