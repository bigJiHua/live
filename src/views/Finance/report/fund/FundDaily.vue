<template>
  <div class="page-fund-daily">
    <div class="section-title">选择基金</div>
    <div class="fund-chips">
      <span
        v-for="f in fundList"
        :key="f.id"
        :class="['chip', { active: selectedFund === f.id }]"
        @click="selectFund(f.id)"
      >
        {{ f.fund_name }}
      </span>
    </div>

    <template v-if="selectedFund">
      <div class="current-info" v-if="currentFund">
        <div class="ci-row">
          <span>当前本金</span>
          <span class="num">¥{{ formatAmount(currentFund.invest) }}</span>
        </div>
        <div class="ci-row">
          <span>累计收益</span>
          <span class="num" :class="Number(currentFund.net_value || 0) >= 0 ? 'success' : 'danger'">
            {{ Number(currentFund.net_value || 0) >= 0 ? '+' : '' }}¥{{ formatAmount(Math.abs(Number(currentFund.net_value || 0))) }}
          </span>
        </div>
        <div class="ci-row">
          <span>当前市值</span>
          <span class="num primary">¥{{ formatAmount(currentFund.market_val) }}</span>
        </div>
      </div>

      <div class="last-record" v-if="lastRecord">
        <div class="lr-title">最近登记</div>
        <div class="lr-row">
          <span>{{ lastRecord.record_date }}</span>
          <span>
            <span :class="Number(lastRecord.net_value || 0) >= 0 ? 'success' : 'danger'">收益 {{ signedAmount(lastRecord.net_value) }}</span>
            <template v-if="Number(lastRecord.market_val || 0) !== 0">
              / 增持 ¥{{ formatAmount(lastRecord.market_val) }}
            </template>
          </span>
        </div>
      </div>

      <div class="form-card">
        <div class="form-title">每日收益登记</div>
        <input type="text" style="display:none" autocomplete="off" />
        <van-field
          v-model="form.date"
          label="日期"
          is-link
          readonly
          placeholder="请选择日期"
          @click="showDatePicker = true"
        />
        <van-field
          v-model="form.netValue"
          label="今日收益"
          placeholder="如 0.19 / -0.30"
          type="number"
          clearable
          :input-attr="{ autocomplete: 'off', autofill: 'off' }"
        />
        <van-field
          v-model="form.marketVal"
          label="增持本金"
          placeholder="如 5000，不增持留空"
          type="number"
          clearable
          :input-attr="{ autocomplete: 'off', autofill: 'off' }"
        />
        <div class="form-tip">
          今日收益只填当天赚亏金额；增持本金只填新投入的本金。保存后系统会自动累计当前本金、市值和收益率。
        </div>
      </div>

      <div class="form-actions">
        <van-button round block type="primary" :loading="saving" @click="handleSave">保存</van-button>
      </div>
    </template>

    <van-empty v-else description="请先选择基金" />

    <van-popup v-model:show="showDatePicker" position="bottom" round teleport="body">
      <van-date-picker
        title="选择日期"
        :model-value="datePickerValue"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getFundList, getFundHistory, addFundHistory } from '@/utils/api/fund'
import { showToast } from 'vant'

const fundList = ref([])
const selectedFund = ref('')
const lastRecord = ref(null)
const showDatePicker = ref(false)
const saving = ref(false)

const form = reactive({ date: '', netValue: '', marketVal: '' })
const currentFund = computed(() => fundList.value.find(f => f.id === selectedFund.value))

const formatAmount = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  if (Math.abs(n) >= 10000) return `${(n / 10000).toFixed(2)}万`
  return n.toFixed(2)
}

const signedAmount = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return '+¥0.00'
  return `${n >= 0 ? '+' : '-'}¥${formatAmount(Math.abs(n))}`
}

const setToday = () => {
  const now = new Date()
  form.date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const datePickerValue = computed(() => {
  if (form.date) {
    const parts = form.date.split('-')
    if (parts.length === 3) return parts
  }
  const now = new Date()
  return [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ]
})

const loadFundList = async () => {
  const res = await getFundList()
  fundList.value = res.data?.list || []
  if (!fundList.value.some(f => f.id === selectedFund.value)) {
    selectedFund.value = fundList.value[0]?.id || ''
  }
}

const loadLastRecord = async () => {
  if (!selectedFund.value) return
  lastRecord.value = null
  try {
    const res = await getFundHistory(selectedFund.value, 365)
    const rows = res.data?.list || []
    lastRecord.value = rows.length > 0 ? rows[rows.length - 1] : null
  } catch (e) {
    lastRecord.value = null
  }
  setToday()
}

const selectFund = async (id) => {
  if (!id || id === selectedFund.value) return
  selectedFund.value = id
  await loadLastRecord()
}

const onDateConfirm = ({ selectedValues }) => {
  form.date = selectedValues.join('-')
  showDatePicker.value = false
}

const isBlank = (value) => value === undefined || value === null || String(value).trim() === ''
const isValidNumber = (value) => isBlank(value) || Number.isFinite(Number(value))

const handleSave = async () => {
  if (!form.date) {
    showToast('请选择日期')
    return
  }
  if (isBlank(form.netValue) && isBlank(form.marketVal)) {
    showToast('今日收益和增持本金至少填写一项')
    return
  }
  if (!isValidNumber(form.netValue) || !isValidNumber(form.marketVal)) {
    showToast('请输入有效金额')
    return
  }

  saving.value = true
  try {
    await addFundHistory(selectedFund.value, {
      netValue: isBlank(form.netValue) ? '0' : form.netValue,
      marketVal: isBlank(form.marketVal) ? '0' : form.marketVal,
      recordDate: form.date,
    })
    showToast('保存成功')
    form.netValue = ''
    form.marketVal = ''
    await loadFundList()
    await loadLastRecord()
  } catch (e) {
    showToast(e?.message || e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    await loadFundList()
    setToday()
    if (selectedFund.value) await loadLastRecord()
  } catch (e) {
    showToast('加载失败')
  }
})
</script>

<style scoped>
.page-fund-daily { min-height: 100vh; background: #f7f8fa; padding: 12px 16px 30px; }
.section-title { font-size: 15px; font-weight: 600; color: #323233; margin-bottom: 10px; padding-left: 2px; }
.fund-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.chip { font-size: 12px; padding: 6px 12px; border-radius: 16px; background: #f5f6fa; color: #646566; cursor: pointer; white-space: nowrap; }
.chip.active { background: #1989fa; color: #fff; }
.current-info, .last-record, .form-card { background: #fff; border-radius: 10px; padding: 14px; margin-bottom: 12px; }
.ci-row, .lr-row { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; color: #323233; padding: 4px 0; }
.ci-row .num { font-weight: 600; font-family: 'DIN Alternate', sans-serif; }
.num.primary { color: #1989fa; }
.success { color: #ee0a24; }
.danger { color: #07c160; }
.lr-title, .form-title { font-size: 14px; font-weight: 600; color: #323233; margin-bottom: 8px; }
.lr-row { font-size: 12px; color: #646566; }
.form-tip { font-size: 11px; color: #fa8c16; margin-top: 8px; background: #fff7e6; padding: 8px 10px; border-radius: 6px; line-height: 1.5; }
.form-actions { margin-top: 16px; }
</style>
