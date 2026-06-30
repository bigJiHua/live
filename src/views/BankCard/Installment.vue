<template>
  <div class="page-installment">
    <div class="header-card">
      <div class="header-title">创建分期</div>
      <div class="header-sub">信用卡分期 · 首期下个账单日+1纳入事件</div>
    </div>

    <!-- 新建分期 -->
    <div class="section">
      <div class="section-title">新建分期</div>

      <van-field
        v-model="cardLabel"
        label="目标信用卡"
        readonly is-link
        placeholder="选择信用卡"
        @click="showCardPicker = true"
      />
      <div class="input-cell" @click="openKeyboard('amount')">
        <span class="input-label">分期总额</span>
        <span class="input-value">{{ formAmount || '0.00' }}</span>
        <van-icon name="arrow" class="input-arrow" />
      </div>
      <div class="input-cell" @click="openKeyboard('fee')">
        <span class="input-label">手续费/利息</span>
        <span class="input-value">{{ formFee || '0.0000' }}</span>
        <van-icon name="arrow" class="input-arrow" />
      </div>
      <van-field label="分期期数">
        <template #input>
          <div class="period-btns">
            <span v-for="p in periodOptions" :key="p"
              class="period-btn" :class="{ active: formPeriods === p }"
              @click="formPeriods = p"
            >{{ p }}期</span>
          </div>
        </template>
      </van-field>

      <!-- 预览 -->
      <div class="preview-card" v-if="preview.periods > 0">
        <div class="preview-row">
          <span>每期金额</span>
          <span class="preview-amount">￥{{ preview.perPeriod }}</span>
        </div>
        <div class="preview-row">
          <span>总还款额</span>
          <span>￥{{ preview.total }}</span>
        </div>
        <div class="preview-row">
          <span>账单日</span>
          <span>{{ selectedCard ? selectedCard.bill_day + '号' : '-' }}</span>
        </div>
        <div class="preview-row">
          <span>首期还款日</span>
          <span class="preview-date">{{ preview.firstDate }}</span>
        </div>
        <div class="preview-list">
          <div v-for="(d, idx) in preview.dates" :key="idx" class="preview-item">
            <span class="pi-idx">第{{ idx + 1 }}期</span>
            <span class="pi-date">{{ d }}</span>
            <span class="pi-amount">￥{{ preview.perPeriod }}</span>
          </div>
        </div>
      </div>

      <div class="form-actions">
        <van-button round block type="primary" :loading="submitting" @click="handleSubmit">确认创建分期</van-button>
      </div>
    </div>

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model="keyboardValue"
      :show="showKeyboard"
      theme="custom"
      :extra-key="keyboardField === 'fee' ? '.' : '.'"
      close-button-text="完成"
      :maxlength="keyboardField === 'fee' ? 13 : 12"
      @blur="closeKeyboard"
    />

    <!-- 信用卡选择 -->
    <van-popup v-model:show="showCardPicker" position="bottom" round>
      <van-picker
        title="选择信用卡"
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import dayjs from 'dayjs'
import { getCardList } from '@/utils/api/card'
import { createRecurring } from '@/utils/api/recurring'

const cards = ref([])
const submitting = ref(false)
const showCardPicker = ref(false)
const selectedCard = ref(null)

const cardLabel = computed(() => {
  if (!selectedCard.value) return ''
  const c = selectedCard.value
  return `${c.alias || c.bank_name || ''} (尾号${c.last4_no || ''}) · 账单日${c.bill_day || '?'}号`
})

const cardColumns = computed(() =>
  cards.value.map(c => ({
    text: `${c.alias || c.bank_name} 尾号${c.last4_no} · 账单${c.bill_day || '?'}号`,
    value: c.id,
  }))
)

const periodOptions = [3, 6, 12, 18, 24, 36]

const formAmount = ref('')
const formFee = ref('')
const formPeriods = ref(3)

// 数字键盘
const showKeyboard = ref(false)
const keyboardField = ref('amount')
const keyboardValue = ref('')

const openKeyboard = (field) => {
  keyboardField.value = field
  keyboardValue.value = field === 'amount' ? formAmount.value : formFee.value
  showKeyboard.value = true
}

// 键盘输入实时同步到表单 ref
watch(keyboardValue, (val) => {
  const clean = String(val).replace(/[^\d.]/g, '')
  if (keyboardField.value === 'amount') {
    formAmount.value = clean
  } else {
    formFee.value = clean
  }
})

const closeKeyboard = () => {
  let v = keyboardValue.value.replace(/[^\d.]/g, '')
  const parts = v.split('.')
  if (keyboardField.value === 'fee') {
    if (parts[0].length > 8) parts[0] = parts[0].slice(0, 8)
    if (parts[1]?.length > 4) parts[1] = parts[1].slice(0, 4)
    if (Number(v) > 99999999.9999) { parts[0] = '99999999'; parts[1] = '9999' }
    v = parts.join('.')
    formFee.value = v
  } else {
    if (parts[0].length > 8) parts[0] = parts[0].slice(0, 8)
    if (parts[1]?.length > 2) parts[1] = parts[1].slice(0, 2)
    if (Number(v) > 99999999.99) { parts[0] = '99999999'; parts[1] = '99' }
    v = parts.join('.')
    formAmount.value = v
  }
  showKeyboard.value = false
}

const preview = computed(() => {
  const periods = Number(formPeriods.value || 0)
  const amount = Number(formAmount.value || 0)
  const fee = Number(formFee.value || 0)
  if (!periods || !amount) return { periods: 0, perPeriod: '0.00', total: '0.00', firstDate: '-', dates: [] }

  const total = amount + fee
  const perPeriod = (total / periods).toFixed(2)
  const billingDay = selectedCard.value?.bill_day || 1

  // 首期 = 下个自然月账单日+1
  const now = dayjs()
  const firstMonth = now.add(1, 'month')
  const safeDay = Math.min(billingDay + 1, firstMonth.daysInMonth())
  const firstDate = firstMonth.date(safeDay)

  const dates = []
  for (let i = 0; i < periods; i++) {
    const d = firstDate.add(i, 'month')
    const maxDay = d.daysInMonth()
    const day = Math.min(safeDay, maxDay)
    dates.push(d.date(day).format('YYYY-MM-DD'))
  }

  return {
    periods, perPeriod, total: total.toFixed(2),
    firstDate: firstDate.format('YYYY-MM-DD'), dates,
  }
})



const loadData = async () => {
  try {
    const cardRes = await getCardList({ cardType: 'credit' })
    cards.value = (cardRes.data || []).filter(c => c.bill_day)
  } catch (e) { showToast(e.message || '加载失败') }
}

const onCardConfirm = ({ selectedOptions }) => {
  const cardId = selectedOptions?.[0]?.value
  selectedCard.value = cards.value.find(c => c.id === cardId) || null
  showCardPicker.value = false
}

const handleSubmit = async () => {
  if (!selectedCard.value) return showToast('请选择信用卡')
  if (!formAmount.value || Number(formAmount.value) <= 0) return showToast('请输入分期总额')
  if (!formPeriods.value || Number(formPeriods.value) < 2) return showToast('期数至少2期')

  const card = selectedCard.value
  const perPeriodAmount = Number(preview.value.perPeriod)
  const billingDay = card.bill_day || 1

  const accountInfo = JSON.stringify({
    type: 'installment',
    card_id: card.id,
    card_name: `${card.alias || card.bank_name || ''}(尾号${card.last4_no || ''})`,
    billing_day: billingDay,
    original_amount: Number(formAmount.value),
    fee: Number(formFee.value || 0),
    total_periods: Number(formPeriods.value),
  })

  const monthRecords = {}
  preview.value.dates.forEach(d => {
    const month = d.substring(0, 7)
    monthRecords[month] = { status: 'pending', amount: perPeriodAmount, remark: '', remind_time: null, done_time: null }
  })

  const billingDayPlus1 = Math.min(billingDay + 1, 28)

  submitting.value = true
  try {
    await createRecurring({
      name: `${card.alias || card.bank_name}分期`,
      amount: perPeriodAmount,
      category_id: 'installment',
      account_id: accountInfo,
      cycle: 'month',
      day_of_cycle: billingDayPlus1,
      month_records: monthRecords,
      repeat_count: Number(formPeriods.value),
      remark: `总额${formAmount.value} 手续费${formFee.value || 0} ${formPeriods.value}期`,
      is_active: 1,
    })
    showSuccessToast('分期创建成功')
    formAmount.value = ''; formFee.value = ''; formPeriods.value = 3
    selectedCard.value = null
    loadData()
  } catch (e) {
    showToast(e.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.page-installment { min-height: 100vh; padding: 12px 16px 100px; background: #f7f8fa; }
.header-card { background: linear-gradient(135deg, #ee0a24 0%, #d91a4a 100%); border-radius: 12px; padding: 20px; margin-bottom: 16px; color: #fff; }
.header-title { font-size: 20px; font-weight: 700; }
.header-sub { font-size: 13px; opacity: 0.85; margin-top: 6px; }

.section { margin-bottom: 16px; }
.section-title { font-size: 13px; font-weight: 600; color: #969799; margin-bottom: 8px; padding-left: 4px; }

.input-cell {
  display: flex; align-items: center;
  padding: 10px 16px; background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.input-label { flex-shrink: 0; width: 80px; font-size: 14px; color: #323233; }
.input-value { flex: 1; text-align: right; font-size: 16px; font-weight: 600; color: #ee0a24; }
.input-arrow { margin-left: 8px; color: #969799; font-size: 14px; }

.period-btns { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; padding: 4px 0; }
.period-btn { 
  padding: 4px 14px; border-radius: 14px; font-size: 13px; 
  background: #f2f3f5; color: #646566; transition: all .2s;
}
.period-btn.active { background: #ee0a24; color: #fff; font-weight: 600; }

.preview-card { background: #fff; border-radius: 8px; padding: 14px; margin-top: 12px; }
.preview-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: #323233; }
.preview-amount { color: #ee0a24; font-weight: 700; }
.preview-date { color: #07c160; font-weight: 600; }
.preview-list { margin-top: 10px; border-top: 1px solid #f2f3f5; padding-top: 8px; }
.preview-item { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; color: #646566; }
.pi-idx { color: #323233; font-weight: 500; }
.pi-date { color: #07c160; }
.pi-amount { color: #ee0a24; }

.form-actions { margin-top: 16px; }
</style>
