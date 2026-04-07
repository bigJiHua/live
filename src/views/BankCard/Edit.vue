<template>
  <div class="page-edit-card">

    <!-- 卡片预览 -->
    <div class="card-preview" v-if="!loading && cardData.id">
      <div
        class="preview-card"
        :style="{ background: `linear-gradient(135deg, ${formData.color} 0%, #1a1a1a 150%)` }"
      >
        <div class="preview-header">
          <span class="preview-bank">{{ bankNameDisplay || getBankName(formData.bankId) }}</span>
          <van-tag v-if="formData.isDefault" type="warning" size="small">默认</van-tag>
        </div>
        <div class="preview-number">**** **** **** {{ formData.last4No }}</div>
        <div class="preview-footer">
          <div>
            <div class="preview-label">持卡人</div>
            <div class="preview-value">{{ formData.alias || '未设置别名' }}</div>
          </div>
          <div class="preview-type">{{ isCreditCard ? '信用卡' : '借记卡' }}</div>
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
            maxlength="4"
            type="number"
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
            is-link
            readonly
            @click="showCardOrgPicker = true"
          />
          <van-field
            v-model="formData.cardLength"
            name="cardLength"
            label="卡号长度"
            type="number"
          />
          <van-field
            v-model="formData.cardBin"
            name="cardBin"
            label="卡BIN"
            placeholder="卡号前6位"
            maxlength="6"
            type="number"
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
            placeholder="如：5"
            type="number"
          />
          <van-field
            v-model="formData.repayDay"
            name="repayDay"
            label="还款日"
            placeholder="如：25"
            type="number"
          />
          <van-field
            v-model="formData.annualFee"
            name="annualFee"
            label="年费"
            placeholder="如：0"
            type="number"
          />
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
  { value: '#1989fa', label: '蓝色' },
  { value: '#07c160', label: '绿色' },
  { value: '#ee0a24', label: '红色' },
  { value: '#ff976a', label: '橙色' },
  { value: '#7232dd', label: '紫色' },
  { value: '#1a1a1a', label: '黑色' },
  { value: '#c01d24', label: '深红' },
  { value: '#1b4f9a', label: '深蓝' }
]

// 银行列表（从 API 加载）
const bankColumns = ref([])

// 加载银行分类列表
const loadBankList = async () => {
  try {
    const res = await categoryApi.list("bank")
    const banks = res.data || res || []
    bankColumns.value = banks.map(bank => ({
      text: bank.name,
      value: bank.id,
      iconUrl: bank.icon_url || bank.iconUrl || ''
    }))
  } catch (e) {
    bankColumns.value = []
  }
}

// 根据 ID 获取银行名称
const getBankName = (bankId) => {
  const bank = bankColumns.value.find(b => b.value === bankId)
  return bank ? bank.text : '未知银行'
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

// 选择器确认
const onBankConfirm = ({ selectedOptions }) => {
  const selected = selectedOptions[0]
  formData.bankId = selected.value
  bankNameDisplay.value = selected.text
  showBankPicker.value = false
}

const onMainSubConfirm = ({ selectedOptions }) => {
  formData.mainSub = selectedOptions[0].value
  showMainSubPicker.value = false
}

const onCardOrgConfirm = ({ selectedOptions }) => {
  formData.cardOrg = selectedOptions[0].value
  showCardOrgPicker.value = false
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
    bankNameDisplay.value = getBankName(formData.bankId)

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
    showToast({ message: '保存成功', onClose: () => router.back() })
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
    showToast({ message: '删除成功', onClose: () => router.back() })
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
  border-radius: 16px;
  padding: 20px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-bank {
  font-size: 18px;
  font-weight: 600;
}

.preview-number {
  font-size: 20px;
  letter-spacing: 4px;
  margin: 20px 0;
  font-family: monospace;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.preview-label {
  font-size: 10px;
  opacity: 0.7;
  text-transform: uppercase;
}

.preview-value {
  font-size: 14px;
  margin-top: 4px;
}

.preview-type {
  font-size: 12px;
  opacity: 0.8;
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
