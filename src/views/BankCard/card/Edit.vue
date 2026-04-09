<template>
  <div class="page-edit-card">

    <!-- 卡片预览 -->
    <div class="card-preview" v-if="!loading && cardData.id">
      <div
        class="preview-card"
        :style="{ background: `linear-gradient(135deg, ${formData.color} 0%, #1a1a1a 150%)` }"
      >
        <div class="preview-bg-pattern"></div>
        
        <div class="preview-header">
          <div class="preview-bank-info">
            <div class="preview-bank-icon" v-if="bankIconUrl">
              <img :src="bankIconUrl" :alt="bankNameDisplay" />
            </div>
            <div class="preview-bank-icon-mock" v-else>
              {{ (bankNameDisplay || '?').charAt(0) }}
            </div>
            <div class="preview-bank-name">
              {{ bankNameDisplay || '未知银行' }}
              <span class="preview-bank-last4">（{{ formData.last4No }}）</span>
            </div>
          </div>
          <van-tag v-if="formData.isDefault" class="preview-tag">默认</van-tag>
        </div>
        
        <div class="preview-number">{{ previewCardNo }}</div>
        
        <div class="preview-footer">
          <div class="preview-holder">
            <div class="preview-label">{{ isCreditCard ? 'CREDIT CARD' : 'DEBIT CARD' }}</div>
            <div class="preview-value">{{ formData.alias || formData.cardLevel || (isCreditCard ? '信用卡' : '银行卡') }}</div>
          </div>
          
          <!-- 卡组织图标 -->
          <div class="preview-card-org" v-if="cardOrgIconUrl">
            <img :src="cardOrgIconUrl" alt="卡组织" />
          </div>
        </div>
      </div>
    </div>

    <van-skeleton title :row="15" v-if="loading" />

    <van-form @submit="onSubmit" ref="formRef" v-if="!loading && cardData.id">
      <!-- 基本信息 -->
      <div class="form-section">
        <div class="section-title">基本信息</div>
        <van-cell-group inset>
          <van-field
            v-model="bankNameDisplay"
            name="bankId"
            label="银行"
            is-link
            readonly
            @click="showBankPicker = true"
          />
          <van-field
            v-model="formData.cardTypeName"
            name="cardType"
            label="卡类型"
            readonly
          />
          <van-field
            v-model="formData.last4No"
            name="last4No"
            label="卡号后4位"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('last4No')"
            :rules="[{ required: true, message: '请输入卡号后4位' }]"
          />
          <van-field
            v-model="formData.alias"
            name="alias"
            label="卡片别名"
            placeholder="如：工资卡、留学卡"
          />
        </van-cell-group>
      </div>

      <!-- 卡片信息 -->
      <div class="form-section">
        <div class="section-title">卡片信息</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.cardLevel"
            name="cardLevel"
            label="卡等级"
            placeholder="如：金卡、白金卡"
          />
          <van-field
            v-model="formData.mainSub"
            name="mainSub"
            label="主副卡"
            is-link
            readonly
            @click="showMainSubPicker = true"
          />
          <van-field
            v-model="formData.cardOrg"
            name="cardOrg"
            label="卡组织"
            placeholder="请输入或选择卡组织"
            @click-right-icon="showCardOrgPicker = true"
          >
            <template #right-icon>
              <van-icon name="arrow-down" @click="showCardOrgPicker = true" />
            </template>
          </van-field>
          <van-field
            v-model="formData.cardLength"
            name="cardLength"
            label="卡号长度"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardLength')"
          />
          <van-field
            v-model="formData.cardBin"
            name="cardBin"
            label="卡BIN"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardBin')"
          />
        </van-cell-group>
      </div>

      <!-- 信用卡专属信息 -->
      <div class="form-section credit-section" v-if="isCreditCard">
        <div class="section-title">
          <van-icon name="star" style="margin-right: 4px" />
          信用卡专属
        </div>
        <van-cell-group inset>
          <van-field
            v-model="formData.billDay"
            name="billDay"
            label="账单日"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('billDay')"
          >
            <template #suffix>日</template>
          </van-field>
          <van-field
            v-model="formData.repayDay"
            name="repayDay"
            label="还款日"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('repayDay')"
          >
            <template #suffix>日</template>
          </van-field>
          <van-field
            v-model="formData.annualFee"
            name="annualFee"
            label="年费"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('annualFee')"
          >
            <template #button>元</template>
          </van-field>
          <van-field
            v-model="formData.feeFreeRule"
            name="feeFreeRule"
            label="免年费规则"
            placeholder="如：刷6次免年费"
          />
        </van-cell-group>
      </div>

      <!-- 日期设置 -->
      <div class="form-section">
        <div class="section-title">日期设置</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.openDate"
            name="openDate"
            label="开卡日期"
            is-link
            readonly
            @click="showOpenDatePicker = true"
          />
          <van-field
            v-model="formData.expireDate"
            name="expireDate"
            label="过期日期"
            is-link
            readonly
            @click="showExpireDatePicker = true"
          />
        </van-cell-group>
      </div>

      <!-- 基本设置 -->
      <div class="form-section">
        <div class="section-title">基本设置</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.currency"
            name="currency"
            label="币种"
          />
          <van-field
            v-model="formData.status"
            name="status"
            label="卡片状态"
            is-link
            readonly
            @click="showStatusPicker = true"
          />
          <van-field name="isDefault" label="设为默认卡">
            <template #input>
              <van-switch v-model="formData.isDefault" size="20" />
            </template>
          </van-field>
          <van-field name="isHide" label="隐藏卡片">
            <template #input>
              <van-switch v-model="formData.isHide" size="20" />
            </template>
          </van-field>
        </van-cell-group>
      </div>

      <!-- 外观 -->
      <div class="form-section">
        <div class="section-title">外观</div>
        <van-cell-group inset>
          <van-field label="卡片颜色">
            <template #input>
              <div class="color-picker">
                <div
                  v-for="color in colorOptions"
                  :key="color.value"
                  class="color-option"
                  :class="{ active: formData.color === color.value }"
                  :style="{ background: color.value }"
                  @click="formData.color = color.value"
                >
                  <van-icon v-if="formData.color === color.value" name="success" color="#fff" />
                </div>
              </div>
            </template>
          </van-field>
          <div class="custom-color">
            <span class="custom-label">自定义</span>
            <input
              type="color"
              v-model="formData.color"
              class="color-input"
            />
          </div>
        </van-cell-group>
      </div>

      <!-- 备注 -->
      <div class="form-section">
        <div class="section-title">备注</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.tag"
            name="tag"
            label="标签"
          />
          <van-field
            v-model="formData.remark"
            name="remark"
            label="备注"
            type="textarea"
            rows="2"
          />
        </van-cell-group>
      </div>

      <div class="submit-btn-wrap">
        <van-button
          type="primary"
          block
          round
          native-type="submit"
          :loading="loading"
        >
          保存修改
        </van-button>
        <van-button
          plain
          type="danger"
          block
          round
          class="delete-btn"
          @click="onDelete"
        >
          删除卡片
        </van-button>
      </div>
    </van-form>

    <!-- 银行选择 -->
    <van-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
      />
    </van-popup>

    <!-- 主副卡选择 -->
    <van-popup v-model:show="showMainSubPicker" position="bottom">
      <van-picker
        :columns="mainSubColumns"
        @confirm="onMainSubConfirm"
        @cancel="showMainSubPicker = false"
      />
    </van-popup>

    <!-- 卡组织选择 -->
    <van-popup v-model:show="showCardOrgPicker" position="bottom">
      <van-picker
        :columns="cardOrgColumns"
        @confirm="onCardOrgConfirm"
        @cancel="showCardOrgPicker = false"
      />
    </van-popup>

    <!-- 卡片状态选择 -->
    <van-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </van-popup>

    <!-- 开卡日期 -->
    <van-popup v-model:show="showOpenDatePicker" position="bottom">
      <van-date-picker
        v-model="openDate"
        type="date"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onOpenDateConfirm"
        @cancel="showOpenDatePicker = false"
      />
    </van-popup>

    <!-- 过期日期 -->
    <van-popup v-model:show="showExpireDatePicker" position="bottom">
      <van-date-picker
        v-model="expireDate"
        type="year-month"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onExpireDateConfirm"
        @cancel="showExpireDatePicker = false"
      />
    </van-popup>

    <!-- 数字键盘 -->
    <van-number-keyboard
      v-model:show="showKeyboard"
      :maxlength="keyboardMaxlength"
      theme="custom"
      close-on-click-outside
      @input="onKeyboardInput"
      @delete="onKeyboardDelete"
      @close="onKeyboardClose"
      @blur="showKeyboard = false"
      close-button-text="完成"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from 'vant'
import { getCardDetail, updateCard, deleteCard } from '@/utils/api/card'
import { categoryApi } from '@/utils/api/category'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const cardData = ref({})
const bankNameDisplay = ref('')
const bankIconUrl = ref('')
const cardOrgIconUrl = ref('')
const bankList = ref([])

// 获取来源页面，决定成功后返回哪里
const fromPage = route.query.from || 'debit'
const backPath = computed(() => fromPage === 'credit' ? '/card/credit' : '/card/debit')

// BASE_URL
const BASE_URL = "http://192.168.0.103:3001/api/public"

// 格式化卡号显示
const previewCardNo = computed(() => {
  const bin = formData.cardBin || ''
  const length = parseInt(formData.cardLength || '16')
  const last4 = formData.last4No || '****'
  const middleLength = length - bin.length - 4
  const middleStars = middleLength > 0 ? '*'.repeat(middleLength) : ''
  const fullNo = bin + middleStars + last4
  return fullNo.match(/.{1,4}/g)?.join(' ') || fullNo
})

// 获取卡组织图标（英文忽略大小写匹配）
const getCardOrgIcon = (cardOrg) => {
  if (!cardOrg) return ''
  const isChinese = /[\u4e00-\u9fa5]/.test(cardOrg)
  const org = bankList.value.find(b => {
    if (isChinese) {
      return b.name === cardOrg
    } else {
      return b.name.toLowerCase() === cardOrg.toLowerCase()
    }
  })
  if (org && org.icon_url) {
    return BASE_URL + org.icon_url
  }
  return ''
}

// 计算属性
const isCreditCard = computed(() => formData.cardType === 'credit')

// 表单数据
const formData = reactive({
  cardType: '',
  cardTypeName: '',
  bankId: '',
  last4No: '',
  alias: '',
  cardLevel: '',
  mainSub: '主卡',
  cardOrg: '',
  cardLength: '',
  cardBin: '',
  openDate: '',
  expireDate: '',
  billDay: '',
  repayDay: '',
  currency: 'CNY',
  status: '正常',
  isDefault: false,
  isHide: false,
  sort: 99,
  tag: '',
  remark: '',
  color: '#1989fa',
  annualFee: '',
  feeFreeRule: '',
  sourceFrom: '手动'
})

// 颜色选项
const colorOptions = [
  { value: '#0052cc', label: '蓝色' },
  { value: '#07c160', label: '绿色' },
  { value: '#ee0a24', label: '红色' },
  { value: '#ff976a', label: '橙色' },
  { value: '#7232dd', label: '紫色' },
  { value: '#1a1a1a', label: '黑色' },
  { value: '#c01d24', label: '深红' },
  { value: '#1b4f9a', label: '深蓝' },
  { value: '#f0c987', label: '金色' },
  { value: '#ffffff', label: '白色' },
  { value: '#9c27b0', label: '紫色' },
  { value: '#00bcd4', label: '青色' },
]

// 银行列表（从 API 加载）
const bankColumns = ref([])

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank")
    const banks = res.data || res || []
    bankList.value = banks
    bankColumns.value = banks.map(bank => ({
      text: bank.name,
      value: bank.id,
      iconUrl: bank.icon_url || bank.iconUrl || ''
    }))
  } catch (e) {
    bankColumns.value = []
  }
}

// 根据 ID 获取银行信息
const getBankInfo = (bankId) => {
  const bank = bankList.value.find(b => b.id === bankId)
  if (bank) {
    return {
      name: bank.name,
      iconUrl: bank.icon_url ? BASE_URL + bank.icon_url : ''
    }
  }
  return { name: '未知银行', iconUrl: '' }
}

// 根据 ID 获取银行名称
const getBankName = (bankId) => {
  const info = getBankInfo(bankId)
  return info.name
}

// 主副卡
const mainSubColumns = [
  { text: '主卡', value: '主卡' },
  { text: '副卡', value: '副卡' }
]

// 卡组织
const cardOrgColumns = [
  { text: '银联', value: 'UnionPay' },
  { text: 'Visa', value: 'Visa' },
  { text: 'Mastercard', value: 'Mastercard' },
  { text: 'American Express', value: 'Amex' },
  { text: 'JCB', value: 'JCB' }
]

// 卡片状态
const statusColumns = [
  { text: '正常', value: '正常' },
  { text: '挂失', value: '挂失' },
  { text: '注销', value: '注销' }
]

// 日期选择
const minDate = new Date(2000, 0, 1)
const maxDate = new Date(2050, 11, 31)
const openDate = reactive(['2024', '01', '01'])
const expireDate = reactive(['2030', '12'])

// 弹出状态
const showBankPicker = ref(false)
const showMainSubPicker = ref(false)
const showCardOrgPicker = ref(false)
const showStatusPicker = ref(false)
const showOpenDatePicker = ref(false)
const showExpireDatePicker = ref(false)

// 数字键盘控制
const showKeyboard = ref(false)
const currentField = ref("")
const keyboardMaxlength = ref(999)

// 验证函数
const validateLast4No = () => {
  if (formData.last4No && formData.last4No.length !== 4) {
    showToast("卡号后4位必须为4位数字")
    formData.last4No = ""
  }
}

const validateCardBin = () => {
  if (formData.cardBin && formData.cardBin.length < 6) {
    showToast("卡BIN必须至少6位数字")
    formData.cardBin = ""
  }
}

const validateBillDay = () => {
  const day = Number(formData.billDay)
  if (formData.billDay && (day < 1 || day > 31)) {
    showToast("账单日范围为1-31")
    formData.billDay = ""
  }
}

const validateRepayDay = () => {
  const day = Number(formData.repayDay)
  if (formData.repayDay && (day < 1 || day > 31)) {
    showToast("还款日范围为1-31")
    formData.repayDay = ""
  }
}

// 字段配置
const fieldConfig = {
  last4No: { maxlength: 4, validate: validateLast4No },
  cardBin: { maxlength: 6, validate: validateCardBin },
  cardLength: { maxlength: 2, validate: null },
  billDay: { maxlength: 2, validate: validateBillDay },
  repayDay: { maxlength: 2, validate: validateRepayDay },
  annualFee: { maxlength: 10, validate: null },
}

// 打开数字键盘
const openKeyboard = (field) => {
  currentField.value = field
  keyboardMaxlength.value = fieldConfig[field]?.maxlength || 10
  showKeyboard.value = true
}

// 数字键盘输入
const onKeyboardInput = (value) => {
  const field = currentField.value
  if (!field) return
  let newValue = formData[field] + value
  if (newValue.length > keyboardMaxlength.value) {
    newValue = newValue.slice(0, keyboardMaxlength.value)
  }
  formData[field] = newValue
}

// 数字键盘删除
const onKeyboardDelete = () => {
  const field = currentField.value
  if (!field) return
  formData[field] = formData[field].slice(0, -1)
}

// 数字键盘关闭
const onKeyboardClose = () => {
  showKeyboard.value = false
  const field = currentField.value
  if (field && fieldConfig[field]?.validate) {
    fieldConfig[field].validate()
  }
}

// 关闭键盘（用于失焦时）
const closeKeyboard = () => {
  showKeyboard.value = false
}

// 选择器确认
const onBankConfirm = ({ selectedOptions }) => {
  const selected = selectedOptions[0]
  formData.bankId = selected.value
  bankNameDisplay.value = selected.text
  // 更新银行图标
  const bank = bankList.value.find(b => b.id === selected.value)
  bankIconUrl.value = bank?.icon_url ? BASE_URL + bank.icon_url : ''
  showBankPicker.value = false
}

const onCardOrgConfirm = ({ selectedOptions }) => {
  formData.cardOrg = selectedOptions[0].value
  // 更新卡组织图标
  cardOrgIconUrl.value = getCardOrgIcon(selectedOptions[0].value)
  showCardOrgPicker.value = false
}

const onMainSubConfirm = ({ selectedOptions }) => {
  formData.mainSub = selectedOptions[0].value
  showMainSubPicker.value = false
}

const onStatusConfirm = ({ selectedOptions }) => {
  formData.status = selectedOptions[0].value
  showStatusPicker.value = false
}

const onOpenDateConfirm = ({ selectedValues }) => {
  formData.openDate = selectedValues.join('-')
  showOpenDatePicker.value = false
}

const onExpireDateConfirm = ({ selectedValues }) => {
  formData.expireDate = selectedValues.join('-')
  showExpireDatePicker.value = false
}

// 返回
const onClickLeft = () => {
  router.back()
}

// 加载卡片详情
const loadCardDetail = async () => {
  const id = route.query.id
  if (!id) {
    showToast('缺少卡片ID')
    router.back()
    return
  }

  // 先加载银行列表
  await loadBankList()

  loading.value = true
  try {
    const res = await getCardDetail(id)
    cardData.value = res.data || res

    // 填充表单数据（处理后端字段命名）
    const data = cardData.value
    formData.cardType = data.card_type || data.cardType || 'debit'
    formData.cardTypeName = formData.cardType === 'credit' ? '信用卡' : '借记卡'
    formData.bankId = data.bank_id || data.bankId || ''
    formData.last4No = data.last4_no || data.last4No || ''
    formData.alias = data.alias || ''
    formData.cardLevel = data.card_level || data.cardLevel || ''
    formData.mainSub = data.main_sub || data.mainSub || '主卡'
    formData.cardOrg = data.card_org || data.cardOrg || ''
    formData.cardLength = data.card_length || data.cardLength || ''
    formData.cardBin = data.card_bin || data.cardBin || ''
    formData.openDate = data.open_date || data.openDate || ''
    formData.expireDate = data.expire_date || data.expireDate || ''
    formData.billDay = data.bill_day ?? data.billDay ?? ''
    formData.repayDay = data.repay_day ?? data.repayDay ?? ''
    formData.currency = data.currency || 'CNY'
    formData.status = data.status || '正常'
    formData.isDefault = Boolean(data.is_default ?? data.isDefault)
    formData.isHide = Boolean(data.is_hide ?? data.isHide)
    formData.sort = data.sort ?? 99
    formData.tag = data.tag || ''
    formData.remark = data.remark || ''
    formData.color = data.color || '#1989fa'
    formData.annualFee = data.annual_fee ?? data.annualFee ?? ''
    formData.feeFreeRule = data.fee_free_rule || data.feeFreeRule || ''
    formData.sourceFrom = data.source_from || data.sourceFrom || '手动'

    // 设置银行名称显示
    const bankInfo = getBankInfo(formData.bankId)
    bankNameDisplay.value = bankInfo.name
    bankIconUrl.value = bankInfo.iconUrl
    
    // 设置卡组织图标
    cardOrgIconUrl.value = getCardOrgIcon(formData.cardOrg)

    // 处理日期选择器
    if (formData.openDate) {
      const parts = formData.openDate.split('-')
      if (parts.length >= 3) {
        openDate[0] = parts[0]
        openDate[1] = parts[1]
        openDate[2] = parts[2]
      }
    }
    if (formData.expireDate) {
      const parts = formData.expireDate.split('-')
      if (parts.length >= 2) {
        expireDate[0] = parts[0]
        expireDate[1] = parts[1]
      }
    }
  } catch (error) {
    showToast(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 提交更新
const onSubmit = async () => {
  try {
    loading.value = true
    showLoadingToast({ message: '保存中...', forbidClick: true })

    const submitData = {
      bankId: formData.bankId,
      cardType: formData.cardType,
      last4No: formData.last4No,
      alias: formData.alias,
      cardLevel: formData.cardLevel,
      mainSub: formData.mainSub,
      cardOrg: formData.cardOrg,
      cardLength: String(formData.cardLength),
      cardBin: formData.cardBin || '',
      openDate: formData.openDate,
      expireDate: formData.expireDate,
      currency: formData.currency || 'CNY',
      status: formData.status || '正常',
      color: formData.color,
      isDefault: formData.isDefault,
      isHide: formData.isHide,
      sort: formData.sort,
      tag: formData.tag || '',
      remark: formData.remark || '',
      sourceFrom: formData.sourceFrom || '手动'
    }

    // 信用卡专属字段
    if (isCreditCard.value) {
      submitData.billDay = formData.billDay ? Number(formData.billDay) : 0
      submitData.repayDay = formData.repayDay ? Number(formData.repayDay) : 0
      submitData.annualFee = formData.annualFee ? Number(formData.annualFee) : 0
      submitData.feeFreeRule = formData.feeFreeRule || ''
    } else {
      submitData.billDay = 0
      submitData.repayDay = 0
      submitData.annualFee = formData.annualFee ? Number(formData.annualFee) : 0
      submitData.feeFreeRule = formData.feeFreeRule || ''
    }

    await updateCard(cardData.value.id, submitData)

    closeToast()
    showToast({ message: '保存成功', onClose: () => router.push(backPath.value) })
  } catch (error) {
    closeToast()
    showToast(error.message || '保存失败')
  } finally {
    loading.value = false
  }
}

// 删除卡片
const onDelete = async () => {
  try {
    await showConfirmDialog({
      title: '删除确认',
      message: '确定要删除这张卡片吗？删除后无法恢复。',
      confirmButtonColor: '#ee0a24'
    })

    showLoadingToast({ message: '删除中...', forbidClick: true })
    await deleteCard(cardData.value.id)

    closeToast()
    showToast({ message: '删除成功', onClose: () => router.push(backPath.value) })
  } catch (error) {
    if (error !== 'cancel') {
      showToast(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadCardDetail()
})
</script>

<style scoped>
.page-edit-card {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.page-header {
  background: #fff;
}

/* 卡片预览 */
.card-preview {
  padding: 16px;
}

.preview-card {
  position: relative;
  border-radius: 20px;
  padding: 20px 5px 20px 20px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.preview-bg-pattern {
  position: absolute;
  top: -20%;
  right: -10%;
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 2;
}

.preview-bank-info {
  display: flex;
  align-items: center;
}

.preview-bank-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-bank-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.preview-bank-icon-mock {
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
}

.preview-bank-name {
  font-size: 18px;
  font-weight: 600;
  margin-left: 12px;
}

.preview-bank-last4 {
  font-size: 1rem;
  font-weight: normal;
}

.preview-tag {
  background: rgba(255, 255, 255, 0.2) !important;
  border: none !important;
}

.preview-number {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  margin: 30px 0;
  position: relative;
  z-index: 2;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  position: relative;
  z-index: 2;
}

.preview-holder .preview-label {
  font-size: 14px;
  opacity: 0.5;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.preview-holder .preview-value {
  font-size: 14px;
  font-weight: 600;
  margin-top: 4px;
  display: block;
}

.preview-card-org {
  position: absolute;
  right: 0;
  bottom: -20px;
  width: 100px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-card-org img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 16px 16px 8px;
  display: flex;
  align-items: center;
}

.credit-section .section-title {
  color: #ee0a24;
}

.color-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.color-option {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.color-option:active {
  transform: scale(0.9);
}

.color-option.active {
  box-shadow: 0 0 0 2px #fff, 0 0 0 4px currentColor;
}

.custom-color {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  gap: 8px;
}

.custom-label {
  font-size: 14px;
  color: #969799;
}

.color-input {
  width: 40px;
  height: 28px;
  border: 1px solid #ebedf0;
  border-radius: 4px;
  padding: 0;
  cursor: pointer;
}

.submit-btn-wrap {
  margin: 32px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.delete-btn {
  margin-top: 0;
}
</style>
