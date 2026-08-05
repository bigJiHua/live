<template>
  <div class="page-edit-card">

    <!-- 卡片预览 -->
    <div class="card-preview" v-if="!loading && cardData.id">
      <div
        class="preview-card"
        :style="{ background: `linear-gradient(135deg, ${formData.color} 0%, var(--theme-text-primary) 150%)` }"
      >
        <div class="preview-bg-pattern"></div>
        
        <div class="preview-header">
          <div class="preview-bank-info">
            <div class="preview-bank-icon">
              <BankIcon :src="bankIconUrl" :name="bankNameDisplay" :size="40" rounded="12" />
            </div>
            <div class="preview-bank-name">
              {{ bankNameDisplay || '未知银行' }}
              <span class="preview-bank-last4">（{{ formData.last4No }}）</span>
            </div>
          </div>
          <app-tag v-if="formData.isDefault" class="preview-tag">默认</app-tag>
        </div>
        
        <div class="preview-number">{{ previewCardNo }}</div>
        
        <div class="preview-footer">
          <div class="preview-holder">
            <div class="preview-label">{{ isCreditCard ? 'CREDIT CARD' : 'DEBIT CARD' }}</div>
            <div class="preview-value">{{ formData.alias || formData.cardLevel || (isCreditCard ? '信用卡' : '银行卡') }}</div>
          </div>
          
          <!-- 卡组织图标：有 logo 图且能加载则显示图，否则显示银色 org 组件 -->
          <div class="preview-card-org" v-if="cardOrgIconUrl && !cardOrgImgError">
            <img :src="cardOrgIconUrl" alt="卡组织" @error="cardOrgImgError = true" />
          </div>
          <CardOrgIcon
            v-else-if="cardOrgKey"
            class="preview-card-org-comp"
            :org="cardOrgKey"
            :width="orgSize(formData.cardOrg).width"
            :height="orgSize(formData.cardOrg).height"
            :filled="true"
          />
        </div>
      </div>
    </div>

    <van-skeleton title :row="15" v-if="loading" />

    <app-form @submit="onSubmit" ref="formRef" v-if="!loading && cardData.id">
      <!-- 基本信息 -->
      <div class="form-section">
        <div class="section-title">基本信息</div>
        <van-cell-group inset>
          <app-field
            v-model="bankNameDisplay"
            name="bankId"
            label="银行"
            is-link
            readonly
            @click="showBankPicker = true"
          />
          <app-field
            v-model="formData.cardTypeName"
            name="cardType"
            label="卡类型"
            readonly
          />
          <app-field
            v-model="formData.alias"
            name="alias"
            label="卡片别名"
            placeholder="如：工资卡、留学卡"
          />
          <app-field
            v-model="formData.cardLevel"
            name="cardLevel"
            label="卡等级"
            placeholder="如：金卡、白金卡"
          />
          <app-field
            v-model="formData.mainSub"
            name="mainSub"
            label="主副卡"
            is-link
            readonly
            @click="showMainSubPicker = true"
          />
        </van-cell-group>
      </div>

      <!-- 卡片信息 -->
      <div class="form-section">
        <div class="section-title">卡片信息</div>
        <van-cell-group inset>
          <app-field
            v-model="formData.cardOrg"
            name="cardOrg"
            label="卡组织"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showCardOrgPicker = true"
          />
          <app-field
            v-model="formData.cardLength"
            name="cardLength"
            label="卡号长度"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardLength')"
          />
          <app-field
            v-model="formData.currency"
            name="currency"
            label="币种"
            placeholder="请选择"
            is-link
            readonly
            clickable
            @click="showCurrencyPicker = true"
          />
          <app-field
            v-model="formData.cardBin"
            name="cardBin"
            label="卡BIN"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('cardBin')"
          />
          <app-field
            v-model="formData.last4No"
            name="last4No"
            label="卡号后4位"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('last4No')"
          />
          <app-field
            v-model="formData.openDate"
            name="openDate"
            label="开卡日期"
            is-link
            readonly
            @click="showOpenDatePicker = true"
          />
          <app-field
            v-model="formData.expireDate"
            name="expireDate"
            label="过期日期"
            is-link
            readonly
            @click="showExpireDatePicker = true"
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
          <app-field
            v-model="formData.billDay"
            name="billDay"
            label="账单日"
            placeholder="请输入"
            readonly
            :clickable="isEditable"
            @click="isEditable && openKeyboard('billDay')"
            suffix="日"
          />
          <app-field
            v-model="formData.repayDay"
            name="repayDay"
            label="还款日"
            placeholder="请输入"
            readonly
            :clickable="isEditable"
            @click="isEditable && openKeyboard('repayDay')"
            suffix="日"
          />
          <div class="check-editable">
            <app-button
              size="small"
              :loading="checkLoading"
              :disabled="isEditable !== null"
              @click="checkEditable"
            >
              {{ isEditable !== null ? (isEditable ? '已解锁' : '已锁定') : '检查是否可修改' }}
            </app-button>
            <span class="check-hint" v-if="isEditable === false">有流水记录，不可修改</span>
            <span class="check-hint text-success" v-else-if="isEditable === true">可自由修改</span>
          </div>
          <app-field
            v-model="formData.annualFee"
            name="annualFee"
            label="年费"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('annualFee')"
            suffix="元"
          />
          <app-field
            v-model="formData.feeFreeRule"
            name="feeFreeRule"
            label="免年费规则"
            placeholder="如：刷6次免年费"
          />
        </van-cell-group>
      </div>

      <!-- 基本设置 -->
      <div class="form-section">
        <div class="section-title">基本设置</div>
        <van-cell-group inset>
          <app-field
            v-model="formData.status"
            name="status"
            label="卡片状态"
            is-link
            readonly
            @click="showStatusPicker = true"
          />
          <app-field name="isDefault" label="设为默认卡">
            <van-switch v-model="formData.isDefault" size="20" />
          </app-field>
          <app-field name="isHide" label="隐藏卡片">
            <van-switch v-model="formData.isHide" size="20" />
          </app-field>
        </van-cell-group>
      </div>

      <!-- 外观 -->
      <div class="form-section">
        <div class="section-title">外观</div>
        <van-cell-group inset>
          <app-field label="卡片颜色">
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
          </app-field>
          <div class="custom-color">
            <span class="custom-label">自定义</span>
            <input
              type="color"
              v-model="formData.color"
              class="color-input"
            />
          </div>
          <app-cell
            title="卡面选择"
            is-link
            :value="formData.cardImg ? '已选择' : '未选择'"
            @click="showImagePicker = true"
          >
            <template #right-icon v-if="formData.cardImg">
              <van-icon name="clear" :color="themeDanger" @click.stop="clearCardImg" />
            </template>
          </app-cell>
        </van-cell-group>
      </div>

      <!-- 图片选择弹窗 -->
      <app-popup
        v-model:show="showImagePicker"
        position="bottom"
        round
        :style="{ height: '70%' }"
      >
        <div class="image-picker">
          <div class="picker-header">
            <span class="picker-title">选择卡面</span>
            <div class="picker-actions">
              <app-button size="small" plain type="danger" @click="clearCardImg">
                清空
              </app-button>
              <app-button size="small" type="primary" @click="confirmCardImg">
                确认
              </app-button>
            </div>
          </div>
          <van-loading v-if="imageLoading" class="loading" />
          <div v-else class="image-list">
            <div class="image-grid">
              <div
                v-for="item in imageList"
                :key="item.id"
                class="image-item"
                :class="{ selected: selectedImageId === item.id }"
                @click="selectImage(item)"
              >
                <van-image fit="cover" :src="getFullUrl(item.file_path)" />
                <van-icon v-if="selectedImageId === item.id" name="success" class="select-icon" />
              </div>
            </div>
          </div>
          <van-empty v-if="!imageLoading && imageList.length === 0" description="暂无图片" image="search" />
        </div>
      </app-popup>

      <!-- 备注 -->
      <div class="form-section">
        <div class="section-title">备注</div>
        <van-cell-group inset>
          <app-field
            v-model="formData.tag"
            name="tag"
            label="标签"
          />
          <app-field
            v-model="formData.remark"
            name="remark"
            label="备注"
            type="textarea"
            rows="2"
          />
        </van-cell-group>
      </div>

      <div class="submit-btn-wrap">
        <app-button
          type="primary"
          block
          round
          native-type="submit"
          :loading="loading"
        >
          保存修改
        </app-button>
        <app-button
          plain
          type="danger"
          block
          round
          class="delete-btn"
          @click="onDelete"
        >
          删除卡片
        </app-button>
      </div>
    </app-form>

    <!-- 银行选择 -->
    <app-popup v-model:show="showBankPicker" position="bottom">
      <van-picker
        :columns="bankColumns"
        @confirm="onBankConfirm"
        @cancel="showBankPicker = false"
      />
    </app-popup>

    <!-- 主副卡选择 -->
    <app-popup v-model:show="showMainSubPicker" position="bottom">
      <van-picker
        :columns="mainSubColumns"
        @confirm="onMainSubConfirm"
        @cancel="showMainSubPicker = false"
      />
    </app-popup>

    <!-- 卡组织选择（左侧带 org 图标） -->
    <app-popup v-model:show="showCardOrgPicker" position="bottom" round>
      <div class="org-picker">
        <div class="org-picker-title">选择卡组织</div>
        <div
          class="org-picker-item"
          v-for="col in cardOrgColumns"
          :key="col.value"
          @click="onCardOrgConfirm(col)"
        >
          <CardOrgIcon :org="ORG_NAME_MAP[col.value]" small :filled="true" />
          <span class="org-picker-label">{{ col.text }}</span>
          <van-icon
            v-if="formData.cardOrg === col.value"
            name="success"
            class="org-picker-check"
          />
        </div>
      </div>
    </app-popup>

    <!-- 币种选择 -->
    <app-popup v-model:show="showCurrencyPicker" position="bottom">
      <van-picker
        title="选择币种"
        :columns="currencyColumns"
        @confirm="onCurrencyConfirm"
        @cancel="showCurrencyPicker = false"
      />
    </app-popup>

    <!-- 卡片状态选择 -->
    <app-popup v-model:show="showStatusPicker" position="bottom">
      <van-picker
        :columns="statusColumns"
        @confirm="onStatusConfirm"
        @cancel="showStatusPicker = false"
      />
    </app-popup>

    <!-- 开卡日期 -->
    <app-popup v-model:show="showOpenDatePicker" position="bottom">
      <van-date-picker
        v-model="openDate"
        type="date"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onOpenDateConfirm"
        @cancel="showOpenDatePicker = false"
      />
    </app-popup>

    <!-- 过期日期 -->
    <app-popup v-model:show="showExpireDatePicker" position="bottom">
      <van-date-picker
        v-model="expireDate"
        type="year-month"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onExpireDateConfirm"
        @cancel="showExpireDatePicker = false"
      />
    </app-popup>

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
import { getAccountListByCard } from '@/utils/api/account'
import { uploadApi } from '@/utils/api/upload'
import { useUiTheme } from '@/composables/useUiTheme'
import BankIcon from '@/components/BankIcon.vue'
import CardOrgIcon from '@/components/BankCard/org/CardOrgIcon.vue'
import { ORG_NAME_MAP } from '@/components/BankCard/org/orgMap.js'

const router = useRouter()
const route = useRoute()
const { danger: themeDanger } = useUiTheme()
const loading = ref(false)
const cardData = ref({})
const bankNameDisplay = ref('')
const bankIconUrl = ref('')
const cardOrgIconUrl = ref('')
const cardOrgImgError = ref(false)
const bankList = ref([])

// 卡组织中文名 → 组件 org key（无匹配返回空）
const cardOrgKey = computed(() => (formData.cardOrg && ORG_NAME_MAP[formData.cardOrg]) || '')

// 卡组织角标尺寸：银联/万事达/visa/大莱/JCB 统一 80×40；运通 50×50
const orgSize = (name) => {
  const k = (name && ORG_NAME_MAP[name]) || ''
  if (k === 'amex') return { width: 50, height: 50 }
  return { width: 80, height: 40 }
}

// 账单日/还款日是否可编辑（默认不可编辑）
const isEditable = ref(null)
const checkLoading = ref(false)

// 获取来源页面，决定成功后返回哪里
const fromPage = route.query.from || 'debit'
const backPath = computed(() => fromPage === 'credit' ? '/card/credit' : '/card/debit')

import ENV from '@/utils/env'

const BASE_URL = ENV.FILE_BASE_URL

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
  color: '#0052cc',
  cardImg: '',
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
  { text: "银联", value: "银联" },
  { text: "万事达", value: "万事达" },
  { text: "Visa", value: "Visa" },
  { text: "运通", value: "运通" },
  { text: "大莱", value: "大莱" },
  { text: "JCB", value: "JCB" },
]

// 卡组织对应的卡号长度
const cardOrgLength = {
  '银联': 19,
  '万事达': 16,
  'Visa': 16,
  '运通': 15,
  '大莱': 16,
  'JCB': 16,
}

// 币种
const currencyColumns = [
  { text: "CNY 人民币", value: "CNY" },
  { text: "USD 美元", value: "USD" },
  { text: "HKD 港币", value: "HKD" },
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
const showCurrencyPicker = ref(false)
const showStatusPicker = ref(false)
const showOpenDatePicker = ref(false)
const showExpireDatePicker = ref(false)

// 卡面图片选择
const showImagePicker = ref(false)
const imageList = ref([])
const imageLoading = ref(false)
const selectedImageId = ref('')
const selectedImageItem = ref(null)

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

// 检查账单日/还款日是否可以修改
const checkEditable = async () => {
  if (!cardData.value.id) return

  checkLoading.value = true
  try {
    const res = await getAccountListByCard({
      cardId: cardData.value.id,
      page: 1,
      limit: 1
    })
    const data = res.data || res
    const total = data.pagination?.total || 0

    if (total > 0) {
      isEditable.value = false
      showToast('该卡片已有流水记录，账单日和还款日不可修改')
    } else {
      isEditable.value = true
      showToast('该卡片暂无流水，可以修改账单日和还款日')
    }
  } catch (e) {
    console.error('检查失败', e)
    showToast('检查失败')
  } finally {
    checkLoading.value = false
  }
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

const onCardOrgConfirm = (col) => {
  formData.cardOrg = col.value
  showCardOrgPicker.value = false
  formData.cardLength = cardOrgLength[formData.cardOrg] || 16
  cardOrgImgError.value = false
  cardOrgIconUrl.value = getCardOrgIcon(col.value)
}

const onCurrencyConfirm = ({ selectedOptions }) => {
  formData.currency = selectedOptions[0].value
  showCurrencyPicker.value = false
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

// 加载图片列表
const loadImageList = async () => {
  imageLoading.value = true
  try {
    const res = await uploadApi.list({
      busType: 'other',
      limit: 100,
      offset: 0
    })
    const list = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    // 过滤：只保留 .png .jpg .jpeg .webp 结尾的图片
    imageList.value = list.filter(item => {
      const ext = (item.file_ext || '').toLowerCase()
      return ['.png', '.jpg', '.jpeg', '.webp'].includes(ext)
    })
  } catch (err) {
    console.error('加载图片列表失败:', err)
  } finally {
    imageLoading.value = false
  }
}

// 监听弹窗打开，加载图片
import { watch } from 'vue'
watch(showImagePicker, (val) => {
  if (val) {
    // 如果已有选择，记录已选
    if (formData.cardImg) {
      const selected = imageList.value.find(item => {
        const path = item.file_path || ''
        return path === formData.cardImg || getFullUrl(path) === formData.cardImg
      })
      if (selected) {
        selectedImageId.value = selected.id
        selectedImageItem.value = selected
      }
    }
    loadImageList()
  }
})

// 获取完整图片地址
const getFullUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return BASE_URL + path
}

// 选择图片（支持再次点击取消选择）
const selectImage = (item) => {
  if (selectedImageId.value === item.id) {
    // 再次点击已选中的图片，取消选择
    selectedImageId.value = ''
    selectedImageItem.value = null
  } else {
    selectedImageId.value = item.id
    selectedImageItem.value = item
  }
}

// 清空卡面选择
const clearCardImg = () => {
  selectedImageId.value = ''
  selectedImageItem.value = null
  formData.cardImg = ''
  showImagePicker.value = false
  showToast('已清空卡面')
}

// 确认选择
const confirmCardImg = () => {
  if (selectedImageItem.value) {
    // 只传 URL，不传图片格式
    const path = selectedImageItem.value.file_path || ''
    formData.cardImg = path
  }
  showImagePicker.value = false
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
    formData.color = data.color || '#0052cc'
    formData.cardImg = data.card_img || data.cardImg || ''
    formData.annualFee = data.annual_fee ?? data.annualFee ?? ''
    formData.feeFreeRule = data.fee_free_rule || data.feeFreeRule || ''
    formData.sourceFrom = data.source_from || data.sourceFrom || '手动'

    // 设置银行名称显示
    const bankInfo = getBankInfo(formData.bankId)
    bankNameDisplay.value = bankInfo.name
    bankIconUrl.value = bankInfo.iconUrl
    
    // 设置卡组织图标
    cardOrgImgError.value = false
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
    // 二次确认
    await showConfirmDialog({
      title: '确认保存',
      message: '确定要保存对这张卡片的修改吗？'
    })

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
      cardImg: formData.cardImg || '',
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
      confirmButtonColor: 'var(--van-danger-color, #ee0a24)'
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
  background: var(--theme-bg-primary);
  padding-bottom: 40px;
}

.page-header {
  background: var(--theme-bg-secondary);
}

/* 卡片预览 */
.card-preview {
  padding: 16px;
}

.preview-card {
  position: relative;
  border-radius: 20px;
  padding: 20px;
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
  z-index: 0;
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

/* 预览卡银行图标兜底块：灰色文字改为白色 */
.preview-bank-icon :deep(.bank-icon-mock) {
  color: #fff !important;
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
  color: #fff !important;
}

.preview-number {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  margin: 30px 0;
  position: relative;
  z-index: 0;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  position: relative;
  z-index: 0;
}

.preview-holder .preview-label {
  font-size: 14px;
  opacity: 1;
  color: #fff;
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
  right: 12px;
  bottom: 0;
  max-width: 100px;
  max-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-card-org img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.preview-card-org-comp {
  position: absolute;
  right: -15px;
  bottom: -10px;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: var(--theme-text-tertiary);
  padding: 16px 16px 8px;
  display: flex;
  align-items: center;
}

.credit-section .section-title {
  color: var(--van-danger-color, #ee0a24);
}

.check-editable {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  gap: 12px;
}

.check-hint {
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.check-hint.text-success {
  color: var(--van-green, #07c160);
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
  color: var(--theme-text-tertiary);
}

.color-input {
  width: 40px;
  height: 28px;
  border: 1px solid var(--theme-border);
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

/* 图片选择弹窗 */
.image-picker {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border: 1px solid var(--theme-border);
  margin-bottom: 12px;
  flex-shrink: 0;
}

.picker-actions {
  display: flex;
  gap: 8px;
}

.picker-title {
  font-size: 16px;
  font-weight: 600;
}

.image-list {
  flex:1;
  overflow-y: auto;
  padding-right: 4px;
  min-height: 0;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-auto-rows: 100px;
  gap: 8px;
  padding-bottom: 16px;
}

/* 自定义滚动条 */
.image-list::-webkit-scrollbar {
  width: 4px;
}

.image-list::-webkit-scrollbar-track {
  background: var(--theme-bg-tertiary);
  border-radius: 2px;
}

.image-list::-webkit-scrollbar-thumb {
  background: var(--theme-text-placeholder);
  border-radius: 2px;
}

.image-list::-webkit-scrollbar-thumb:hover {
  background: var(--theme-text-tertiary);
}

.image-item {
  position: relative;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
}

.image-item.selected {
  border-color: var(--van-blue, #1989fa);
}

.image-item :deep(.van-image) {
  width: 100%;
  height: 100%;
}

.select-icon {
  position: absolute;
  right: 4px;
  top: 4px;
  background: var(--theme-primary);
  color: #fff;
  border-radius: 50%;
  padding: 2px;
  font-size: 12px;
}

.loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

/* 卡组织选择器：左侧 org 图标 + 文字 */
.org-picker {
  background: var(--theme-bg-primary);
  padding: 8px 0 16px;
  max-height: 70vh;
  overflow-y: auto;
}
.org-picker-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text-primary);
  padding: 12px 0 8px;
}
.org-picker-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 20px;
  cursor: pointer;
}
.org-picker-item:active {
  background: var(--theme-bg-secondary);
}
.org-picker-label {
  flex: 1;
  font-size: 15px;
  color: var(--theme-text-primary);
}
.org-picker-check {
  color: var(--theme-primary);
  font-size: 18px;
}
</style>
