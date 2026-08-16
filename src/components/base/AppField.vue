<template>
  <div
    class="app-field"
    :class="{
      'app-field--disabled': disabled,
      'app-field--error': errorMsg,
      'app-field--borderless': !border,
      'app-field--link': isLink && !disabled,
    }"
    @click="onClick"
  >
    <div v-if="label" class="app-field__label" :class="{ 'is-required': required }">
      <slot name="label">{{ label }}</slot>
    </div>

    <div class="app-field__control">
      <slot v-if="$slots.default" />
      <template v-else>
        <textarea
          v-if="type === 'textarea'"
          class="app-field__input app-field__textarea"
          :value="modelValue"
          :placeholder="placeholder"
          :disabled="disabled"
          :readonly="readonly"
          :rows="rows"
          :autocomplete="autocomplete || undefined"
          @input="onInput"
          @blur="onBlur"
        ></textarea>
        <input
          v-else
          class="app-field__input"
          :type="inputType"
          :value="modelValue"
          :placeholder="placeholder"
          :disabled="disabled"
          :readonly="readonly"
          :maxlength="maxlength"
          :autocomplete="autocomplete || undefined"
          @input="onInput"
          @blur="onBlur"
        />
      </template>

      <span v-if="suffix" class="app-field__suffix">{{ suffix }}</span>

      <span
        v-if="clearable && modelValue && !disabled && !readonly"
        class="app-field__clear"
        @click="clear"
        >×</span
      >

      <span v-if="$slots['right-icon']" class="app-field__right-icon">
        <slot name="right-icon" />
      </span>
      <span v-else-if="rightIcon" class="app-field__right-icon">
        <van-icon :name="rightIcon" />
      </span>
      <span v-else-if="isLink" class="app-field__right-icon">
        <van-icon name="arrow" />
      </span>
    </div>

    <div v-if="errorMsg" class="app-field__error">{{ errorMsg }}</div>
    <div v-else-if="showWordLimit && maxlength" class="app-field__count">
      {{ String(modelValue || '').length }}/{{ maxlength }}
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onBeforeUnmount, ref, watch } from 'vue'

// 自写主题化输入，替代 van-field
// 支持 v-model、label、type(text/number/tel/password/textarea)、placeholder、disabled、readonly、
// clearable、required、rules[{required,pattern,validator,message}]、error(外部错误)、name、suffix、maxlength、border、rightIcon
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
  clearable: { type: Boolean, default: false },
  required: { type: Boolean, default: false },
  rules: { type: Array, default: () => [] },
  error: { type: String, default: '' },
  name: { type: [String, Number], default: '' },
  suffix: { type: String, default: '' },
  rows: { type: [String, Number], default: 3 },
  maxlength: { type: [String, Number], default: null },
  border: { type: Boolean, default: true },
  showWordLimit: { type: Boolean, default: false },
  rightIcon: { type: String, default: '' },
  passwordVisible: { type: Boolean, default: false },
  isLink: { type: Boolean, default: false },
  autocomplete: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'click'])

const errorMsg = ref(props.error || '')
const inputType = computed(() => {
  if (props.type === 'textarea') return 'text'
  // passwordVisible 为 true 时切换为 text 明文展示（眼睛按钮）
  if (props.type === 'password') return props.passwordVisible ? 'text' : 'password'
  return props.type
})

const form = inject('app-form', null)

const onInput = (e) => {
  emit('update:modelValue', e.target.value)
  emit('change', e.target.value)
}
const onBlur = (e) => emit('blur', e)
const onClick = (e) => {
  if (props.disabled) return
  emit('click', e)
}
const clear = () => emit('update:modelValue', '')

const getValue = () => props.modelValue

const validate = () => {
  const val = String(props.modelValue ?? '')
  for (const rule of props.rules) {
    if (rule.required && !val.trim()) {
      errorMsg.value = rule.message || '此项为必填'
      return Promise.resolve({ name: props.name, message: errorMsg.value })
    }
    if (rule.pattern && !rule.pattern.test(val)) {
      errorMsg.value = rule.message || '格式不正确'
      return Promise.resolve({ name: props.name, message: errorMsg.value })
    }
    if (rule.validator) {
      const r = rule.validator(val)
      if (r !== true) {
        errorMsg.value = typeof r === 'string' ? r : rule.message || '格式不正确'
        return Promise.resolve({ name: props.name, message: errorMsg.value })
      }
    }
  }
  errorMsg.value = ''
  return Promise.resolve()
}
const resetValidation = () => {
  errorMsg.value = ''
}

watch(
  () => props.error,
  (v) => {
    errorMsg.value = v || ''
  }
)

onMounted(() => {
  if (form) form.registerField({ name: props.name, validate, resetValidation, getValue })
})
onBeforeUnmount(() => {
  if (form) form.unregisterField(props.name)
})
</script>

<style scoped>
.app-field {
  display: block;
  padding: 10px 16px;
  background: var(--theme-bg-secondary);
  color: var(--theme-text-primary);
  font-size: 14px;
}
.app-field--borderless {
  border-bottom: none;
}
.app-field:not(.app-field--borderless) {
  border-bottom: 1px solid var(--theme-border);
}
.app-field--link {
  cursor: pointer;
}
.app-field__label {
  margin-bottom: 6px;
  color: var(--theme-text-secondary);
  font-size: 13px;
}
.app-field__label.is-required::before {
  content: '*';
  color: var(--theme-danger);
  margin-right: 2px;
}
.app-field__control {
  display: flex;
  align-items: center;
  gap: 6px;
}
.app-field__input {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--theme-text-primary);
  font-size: 14px;
  line-height: 1.5;
  padding: 2px 0;
}
.app-field__input:not(.app-field__textarea) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-field__input::placeholder {
  color: var(--theme-text-tertiary);
}
.app-field__textarea {
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
}
.app-field__suffix {
  flex-shrink: 0;
  color: var(--theme-text-tertiary);
}
.app-field__clear {
  flex-shrink: 0;
  cursor: pointer;
  color: var(--theme-text-tertiary);
  font-size: 18px;
  line-height: 1;
}
.app-field__right-icon {
  flex-shrink: 0;
  color: var(--theme-text-tertiary);
}
.app-field__error {
  margin-top: 4px;
  color: var(--theme-danger);
  font-size: 12px;
}
.app-field__count {
  margin-top: 4px;
  text-align: right;
  color: var(--theme-text-tertiary);
  font-size: 12px;
}
.app-field--disabled .app-field__input {
  color: var(--theme-text-tertiary);
  cursor: not-allowed;
}
.app-field--error .app-field__input {
  color: var(--theme-danger);
}
</style>
