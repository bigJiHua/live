<template>
  <div class="page-finance-add" @click="showKeyboard = false">
    <!-- 顶部金额卡片 -->
    <div class="amount-card" @click.stop="showKeyboard = true">
      <!-- 币种选择 -->
      <div class="currency-selector" @click.stop="showCurrencyPicker = true">
        <span class="currency-name">{{ selectedCurrency.label }}</span>
        <van-icon name="arrow-down" />
      </div>
      <div class="label">请输入{{ isExpense ? '支出' : '收入' }}金额</div>
      <div class="value">
        <span class="currency-symbol">{{ selectedCurrency.symbol }}</span>
        <span>{{ displayAmount || "0.00" }}</span>
      </div>
      <!-- 汇率换算提示（非人民币时显示） -->
      <div class="exchange-tip" v-if="selectedCurrency.code !== 'CNY' && displayAmount">
        约 ¥ {{ formatMoney(exchangedAmount) }}
      </div>
    </div>

    <!-- 支出/收入切换 + 信息填写区域 -->
    <div class="info-section">
      <!-- 支出/收入切换 -->
      <van-tabs v-model:active="type" animated swipeable @change="onTypeChange">
        <van-tab title="支出" name="expense" />
        <van-tab title="收入" name="income" />
      </van-tabs>

      <!-- 信息填写表单项 -->
      <div class="form-items">
        <!-- 分类选择 -->
        <van-cell title="分类" is-link @click="showCategoryPicker = true">
          <template #value>
            <span :class="{ placeholder: !selectedCategory }">
              {{ selectedCategory?.name || '请选择分类' }}
            </span>
          </template>
        </van-cell>

        <!-- 支付方式 -->
        <van-field
          v-model="selectedPayMethod"
          :label="isExpense ? '支出方式' : '收入方式'"
          placeholder="请输入或选择支付方式"
          @click-right-icon="showPayMethodPicker = true"
        >
          <template #right-icon>
            <van-icon name="arrow-down" @click="showPayMethodPicker = true" />
          </template>
        </van-field>

        <!-- 关联卡片（只有非现金/非余额时才显示） -->
        <van-cell v-if="showCardCell" :title="selectedPayMethod" is-link @click="showCardPicker = true">
          <template #value>
            <span :class="{ placeholder: !selectedCard?.id }">
              {{ getCardDisplayText(selectedCard) }}
            </span>
          </template>
        </van-cell>

        <!-- 日期选择 -->
        <van-cell title="日期" is-link @click="showDatePicker = true">
          <template #value>
            <span>{{ formatDate(selectedDate) }}</span>
          </template>
        </van-cell>

        <!-- 汇率输入（非人民币时显示） -->
        <van-field
          v-if="selectedCurrency.code !== 'CNY'"
          v-model="exchangeRate"
          label="汇率"
          type="number"
          placeholder="如: 684.5125"
          @focus="showKeyboard = false"
        >
          <template #button>
            <span style="color: #969799">/100</span>
          </template>
        </van-field>

        <!-- 备注 -->
        <van-field
          v-model="remark"
          :label="isExpense ? '备注' : '说明'"
          placeholder="选填"
          clearable
        />
      </div>
    </div>

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model="displayAmount"
      :show="showKeyboard"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      maxlength="12"
      @blur="showKeyboard = false"
    />

    <!-- 提交按钮 -->
    <div class="submit-wrap">
      <van-button
        type="primary"
        block
        round
        :disabled="!canSubmit"
        :loading="submitting"
        @click="handleSubmit"
      >
        {{ isExpense ? '记一笔支出' : '记一笔收入' }}
      </van-button>
    </div>

    <!-- 分类选择弹框 -->
    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <!-- 支付方式选择弹框 -->
    <van-popup v-model:show="showPayMethodPicker" position="bottom" round>
      <van-picker
        title="选择支付方式"
        :columns="payMethodColumns"
        @confirm="onPayMethodConfirm"
        @cancel="showPayMethodPicker = false"
      />
    </van-popup>

    <!-- 卡片选择弹框 -->
    <van-popup v-model:show="showCardPicker" position="bottom" round>
      <van-picker
        title="选择关联卡片"
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
      />
    </van-popup>

    <!-- 币种选择弹框 -->
    <van-popup v-model:show="showCurrencyPicker" position="bottom" round>
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
      />
    </van-popup>

    <!-- 日期日历弹框 -->
    <van-calendar
      v-model:show="showDatePicker"
      :default-date="selectedDate"
      :min-date="minDate"
      :max-date="maxDate"
      @confirm="onDateConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { showToast, showSuccessToast, showFailToast } from "vant";
import { useRouter } from "vue-router";
import dayjs from "dayjs";
import { categoryApi } from "@/utils/api/category";
import { getCardList } from "@/utils/api/card";
import { createAccount } from "@/utils/api/account";

// ============================================
// 常量配置
// ============================================

// 支出支付方式选项
const expensePayMethods = [
  '现金', '余额', '微信支付', '支付宝', '借记卡', '信用卡'
]

// 收入支付方式选项（不含信用卡）
const incomePayMethods = [
  '现金', '余额', '微信支付', '支付宝', '借记卡'
]

// ============================================
// 组件逻辑
// ============================================

const router = useRouter()

// 基础状态
const displayAmount = ref("")
const showKeyboard = ref(false)
const submitting = ref(false)

// 类型状态
const type = ref("expense")
const isExpense = computed(() => type.value === "expense")
const direction = computed(() => type.value === "expense" ? 0 : 1)

// 分类状态
const categories = ref([])
const selectedCategory = ref(null)
const showCategoryPicker = ref(false)

// 支付方式状态
const selectedPayMethod = ref('')
const showPayMethodPicker = ref(false)

// 卡片状态
const cardList = ref([])
const selectedCard = ref(null)
const showCardPicker = ref(false)

// 日期状态
const minDate = new Date(2020, 0, 1)
const maxDate = new Date()
const selectedDate = ref(new Date())
const showDatePicker = ref(false)

// 备注
const remark = ref("")

// 币种状态
const currencyOptions = [
  { code: 'CNY', label: '人民币', symbol: '¥', rate: 1 },
  { code: 'USD', label: '美元', symbol: '$', rate: 6.8451 },
  { code: 'EUR', label: '欧元', symbol: '€', rate: 7.5 },
  { code: 'HKD', label: '港币', symbol: 'HK$', rate: 0.88 },
  { code: 'JPY', label: '日元', symbol: '¥', rate: 0.045 },
  { code: 'GBP', label: '英镑', symbol: '£', rate: 8.7 },
  { code: 'KRW', label: '韩元', symbol: '₩', rate: 0.005 },
  { code: 'TWD', label: '台币', symbol: 'NT$', rate: 0.22 }
]
const selectedCurrency = ref(currencyOptions[0])
const showCurrencyPicker = ref(false)
const exchangeRate = ref('')

// 汇率换算
const exchangedAmount = computed(() => {
  if (!displayAmount.value || selectedCurrency.value.code === 'CNY') return 0
  const foreignAmount = Number(displayAmount.value)
  const rate = Number(exchangeRate.value) || selectedCurrency.value.rate
  return (foreignAmount * rate) / 100
})

// 加载分类数据
const loadCategories = async () => {
  try {
    const res = await categoryApi.list(type.value)
    categories.value = res.data || res || []
  } catch (e) {
    showToast("加载分类失败")
  }
}

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList()
    cardList.value = res.data || res || []
  } catch (e) {
    cardList.value = []
  }
}

// 计算属性
const categoryColumns = computed(() =>
  categories.value.map((c) => ({ 
    text: c.name, 
    value: c.id, 
    iconUrl: c.icon_url || '',
    ...c 
  }))
)

// 支付方式选项（根据类型动态过滤）
const payMethodColumns = computed(() => {
  const options = isExpense.value ? expensePayMethods : incomePayMethods
  return options.map((m) => ({ text: m, value: m }))
})

const currencyColumns = currencyOptions.map((c) => ({ text: `${c.label} ${c.symbol}`, value: c.code }))

const cardColumns = computed(() => {
  const method = selectedPayMethod.value

  // 借记卡：只显示真实借记卡
  if (method === '借记卡') {
    return cardList.value
      .filter(c => c.card_type === 'debit')
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c
      }))
  }

  // 信用卡：只显示真实信用卡
  if (method === '信用卡') {
    return cardList.value
      .filter(c => c.card_type === 'credit')
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c
      }))
  }

  // 微信/支付宝：显示借记卡 + 余额
  if (method === '微信支付' || method === '支付宝') {
    return cardList.value
      .filter(c => c.card_type === 'debit' || c.card_type === 'virtual_balance')
      .map((c) => ({
        text: getCardDisplayText(c),
        value: c.id,
        ...c
      }))
  }

  // 其他（现金等）：返回空
  return []
})

// 是否显示关联卡片（借记卡/信用卡/微信支付/支付宝时显示）
const showCardCell = computed(() => {
  const method = selectedPayMethod.value
  return method && !['现金', '余额'].includes(method)
})

// 获取卡片显示文本
const getCardDisplayText = (card) => {
  if (!card) return '请选择'
  const typeText = getCardTypeText(card.card_type)
  // 虚拟卡片（现金、余额）只显示名称
  if (card.card_type === 'virtual_cash' || card.card_type === 'virtual_balance') {
    return typeText
  }
  // 其他卡片显示类型 + 卡号后四位
  const cardNo = card.last4_no || card.last4No || card.card_last4 || '****'
  return `${typeText} ${cardNo}`
}

// 获取卡片类型文本
const getCardTypeText = (cardType) => {
  const map = {
    credit: '信用卡',
    debit: '借记卡',
    virtual_cash: '现金',
    virtual_balance: '余额',
  }
  return map[cardType] || cardType || '卡片'
}

const canSubmit = computed(() => {
  const needCard = showCardCell.value // 非现金/非余额时需要选择卡片
  const hasCard = !!selectedCard.value?.id
  return (
    displayAmount.value && 
    Number(displayAmount.value) > 0 &&
    selectedCategory.value?.id &&
    selectedPayMethod.value &&
    (!needCard || hasCard)
  )
})

// 格式化日期
const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD')
}

// 格式化金额
const formatMoney = (val) => {
  const num = Number(val) || 0
  return num.toFixed(2)
}

// 币种选择
const onCurrencyConfirm = ({ selectedOptions }) => {
  const code = selectedOptions[0].value
  selectedCurrency.value = currencyOptions.find(c => c.code === code) || currencyOptions[0]
  showCurrencyPicker.value = false
}

// 类型切换
const onTypeChange = () => {
  selectedCategory.value = null
  // 如果切换到收入模式且当前选择的是信用卡，清空选择
  if (!isExpense.value && selectedPayMethod.value === '信用卡') {
    selectedPayMethod.value = ''
    selectedCard.value = null
  }
  loadCategories()
}

// 分类选择
const onCategoryConfirm = ({ selectedOptions }) => {
  selectedCategory.value = selectedOptions[0]
  showCategoryPicker.value = false
}

// 支付方式选择
const onPayMethodConfirm = ({ selectedOptions }) => {
  const method = selectedOptions[0].value
  selectedPayMethod.value = method
  // 现金/余额不需要卡片，清空选择；其他方式也清空，等待用户选择
  selectedCard.value = null
  showPayMethodPicker.value = false
}

// 卡片选择
const onCardConfirm = ({ selectedOptions }) => {
  selectedCard.value = selectedOptions[0]
  showCardPicker.value = false
}

// 日期选择
const onDateConfirm = (date) => {
  selectedDate.value = date
  showDatePicker.value = false
}

// 监听金额输入，decimal(12,2)：总共12位，小数2位，整数10位
watch(displayAmount, (val) => {
  val = val.replace(/[^\d.]/g, "")
  const parts = val.split(".")
  if (parts.length > 2) {
    displayAmount.value = parts[0] + "." + parts.slice(1).join("")
    return
  }
  if (parts[1] && parts[1].length > 2) {
    displayAmount.value = parts[0] + "." + parts[1].slice(0, 2)
    return
  }
  if (parts[0].length > 10) {
    displayAmount.value = parts[0].slice(0, 10) + (parts[1] ? "." + parts[1] : "")
    return
  }
  displayAmount.value = val
})

// 监听汇率输入，限制前3后4位精度（999.9999）
watch(exchangeRate, (val) => {
  val = val.replace(/[^\d.]/g, "")
  const parts = val.split(".")
  if (parts.length > 2) {
    exchangeRate.value = parts[0] + "." + parts.slice(1).join("")
    return
  }
  // 整数部分最多3位
  if (parts[0].length > 3) {
    exchangeRate.value = parts[0].slice(0, 3) + (parts[1] ? "." + parts[1] : "")
    return
  }
  // 小数部分最多4位
  if (parts[1] && parts[1].length > 4) {
    exchangeRate.value = parts[0] + "." + parts[1].slice(0, 4)
    return
  }
  exchangeRate.value = val
})

// 提交
const handleSubmit = async () => {
  if (!canSubmit.value) {
    if (showCardCell.value && !selectedCard.value?.id) {
      showFailToast("请选择关联卡片")
    } else {
      showFailToast("请填写完整信息")
    }
    return
  }

  submitting.value = true
  try {
    // 根据支付方式决定 cardId
    let cardId = ''
    if (selectedPayMethod.value === '现金') {
      cardId = 'xxxx'
    } else if (selectedPayMethod.value === '余额') {
      cardId = 'yyyy'
    } else {
      cardId = selectedCard.value?.id || ''
    }

    const data = {
      direction: direction.value,
      categoryId: selectedCategory.value?.id,
      payType: selectedCategory.value?.name,
      payMethod: selectedPayMethod.value,
      amount: Number(displayAmount.value),
      currency: selectedCurrency.value.code,
      transDate: formatDate(selectedDate.value),
      cardId,
      remark: remark.value.trim() || ''
    }

    // 汇率处理：人民币不传，其他币种保留5位小数
    if (selectedCurrency.value.code !== 'CNY') {
      const rate = Number(exchangeRate.value) || selectedCurrency.value.rate
      data.exchangeRate = Math.round(rate * 100000) / 100000
    } else {
      data.exchangeRate = 1
    }

    await createAccount(data)
    showSuccessToast("提交成功")

    // 返回上一页
    router.back()
  } catch (e) {
    showFailToast(e.message || "提交失败")
  } finally {
    submitting.value = false
  }
}

// 初始化
onMounted(() => {
  loadCategories()
  loadCardList()
})
</script>

<style scoped>
.page-finance-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.amount-card {
  margin: 0;
  padding: 40px 20px 20px;
  background: #fff;
  text-align: center;
  border-bottom: 1px solid #eee;
}

.currency-selector {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: #f7f8fa;
  border-radius: 16px;
  font-size: 13px;
  color: #323233;
  margin-bottom: 12px;
  cursor: pointer;
}

.currency-symbol {
  font-size: 32px;
  margin-right: 2px;
}

.exchange-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #969799;
}

.label {
  color: #999;
  font-size: 14px;
}

.value {
  font-size: 48px;
  font-weight: bold;
  color: #333;
  margin-top: 15px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.placeholder {
  color: #999;
}

.info-section {
  background: #fff;
  margin-top: 12px;
}

.form-items {
  padding-bottom: 10px;
}

.submit-wrap {
  padding: 30px 20px;
  background: #f7f8fa;
}
</style>
