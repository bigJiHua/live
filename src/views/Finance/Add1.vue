<template>
  <div class="page-add" @click="handlePageClick">
    <div class="input-header" :class="{ 'is-income': type === 'inc' }" @click.stop="showKeyboard = true">
      <div class="type-switch" @click.stop>
        <span :class="{ active: type === 'exp' }" @click="type = 'exp'">支出</span>
        <span :class="{ active: type === 'inc' }" @click="type = 'inc'">收入</span>
      </div>
      <div class="amount-display num-font">
        <span class="symbol">¥</span>
        <span class="amount-text" :class="{ 'has-cursor': showKeyboard }">{{ amount || '0.00' }}</span>
      </div>
    </div>

    <van-cell-group inset class="form-group">
      <van-field
        v-model="category"
        label="分类"
        is-link
        readonly
        placeholder="选择分类"
        @click.stop="onOpenPicker"
      />
      <van-field
        v-model="date"
        label="日期"
        is-link
        readonly
        placeholder="选择日期"
        @click.stop="onOpenCalendar"
      />
      <van-field 
        v-model="note" 
        label="备注" 
        placeholder="写点什么..." 
        @click.stop="showKeyboard = false" 
      />
    </van-cell-group>

    <van-number-keyboard
      v-model="amount"
      :show="showKeyboard"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      @blur="showKeyboard = false"
      @close="onSave"
      @click.stop
    />

    <van-popup v-model:show="showPicker" position="bottom" round>
      </van-popup>
    <van-calendar v-model:show="showCalendar" @confirm="onDateConfirm" color="var(--app-primary)" />
  </div>
</template>

<script setup>
import { ref } from 'vue'

const type = ref('exp')
const amount = ref('')
const category = ref('餐饮')
const date = ref('2026-03-24')
const note = ref('')

// 控制键盘显隐
const showKeyboard = ref(false)
const showPicker = ref(false)
const showCalendar = ref(false)

const handlePageClick = () => {
  showKeyboard.value = false
}

const onOpenPicker = () => {
  showKeyboard.value = false
  showPicker.value = true
}

const onOpenCalendar = () => {
  showKeyboard.value = false
  showCalendar.value = true
}

const onDateConfirm = (val) => {
  date.value = `${val.getFullYear()}-${val.getMonth() + 1}-${val.getDate()}`
  showCalendar.value = false
}

const onSave = () => {
  showKeyboard.value = false
  console.log('保存数据', { amount: amount.value, type: type.value })
  // 可以在这里执行 router.back()
}
</script>

<style scoped>
.page-add {
  min-height: 100vh;
}

.input-header {
  background: var(--app-primary);
  color: white;
  padding: 30px 20px;
  text-align: center;
  transition: background 0.3s ease;
}

/* 收入状态变色 */
.input-header.is-income {
  background: var(--app-income);
}

.type-switch {
  display: inline-flex;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 20px;
  padding: 4px;
  margin-bottom: 20px;
}

.type-switch span {
  padding: 4px 20px;
  border-radius: 16px;
  font-size: 14px;
  transition: all 0.2s;
}

.type-switch span.active {
  background: white;
  color: var(--app-primary);
  font-weight: bold;
}

.is-income .type-switch span.active {
  color: var(--app-income);
}

.amount-display {
  font-size: 48px;
  display: flex;
  justify-content: center;
  align-items: baseline;
}

.has-cursor::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 40px;
  background: white;
  margin-left: 4px;
  animation: blink 1s infinite;
  vertical-align: middle;
}

@keyframes blink {
  50% { opacity: 0; }
}

.symbol {
  font-size: 24px;
  margin-right: 4px;
}

.form-group {
  margin-top: -20px;
  box-shadow: var(--app-shadow);
}
</style>
