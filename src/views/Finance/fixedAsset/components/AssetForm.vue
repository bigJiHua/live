<template>
  <van-popup
    :show="modelValue"
    position="bottom"
    round
    :close-on-click-overlay="false"
    :style="{ height: '90%' }"
    @update:show="onPopupShow"
  >
    <div class="asset-form">
      <div class="asset-form__header">
        <span class="asset-form__title">{{ isEditing ? "编辑资产" : "登记固定资产" }}</span>
        <van-icon name="cross" class="asset-form__close" aria-label="关闭" @click="requestClose" />
      </div>

      <div class="asset-form__body">
        <div v-if="prefillNotice" class="asset-form__note">{{ prefillNotice }}</div>

        <div class="asset-form__image">
          <div class="asset-form__image-label">
            资产图片<span class="asset-form__required">*</span>
            <span class="asset-form__hint">用于资产卡片展示</span>
          </div>
          <div class="asset-form__image-box" role="button" aria-label="选择资产图片" @click="openImagePicker">
            <img v-if="form.img_url" :src="getImageUrl(form.img_url)" alt="资产图片" />
            <div v-else class="asset-form__image-empty">
              <van-icon name="photograph" />
              <span>点击选择图片</span>
            </div>
          </div>
          <div v-if="errors.img_url" class="asset-form__error">{{ errors.img_url }}</div>
        </div>

        <van-cell-group inset>
          <app-field
            v-model="form.info"
            label="资产名称"
            placeholder="如：MacBook Pro 14寸"
            :error="errors.info"
            clearable
            maxlength="255"
          />
          <app-field
            v-model="form.tag"
            label="品类"
            placeholder="请选择品类"
            readonly
            is-link
            :error="errors.tag"
            @click="openTagPicker"
          />
          <app-field
            v-model="form.buy_price"
            label="购买价格"
            type="number"
            inputmode="decimal"
            placeholder="必须大于0"
            suffix="元"
            :readonly="isEditing"
            :error="errors.buy_price"
          />
          <app-field
            v-model="form.buy_date"
            label="购买日期"
            placeholder="请选择"
            readonly
            is-link
            :error="errors.buy_date"
            @click="openDatePicker"
          />
          <app-field
            v-model="form.use_years"
            label="预计使用年限"
            type="number"
            inputmode="decimal"
            placeholder="如：3"
            suffix="年"
            :error="errors.use_years"
          />
          <app-field
            v-model="form.residual_rate"
            label="残值率"
            type="number"
            inputmode="decimal"
            placeholder="0~100，默认5"
            suffix="%"
            :error="errors.residual_rate"
          />
          <app-field
            v-model="form.secondhand_price"
            label="二手市场价"
            type="number"
            inputmode="decimal"
            placeholder="选填"
            suffix="元"
            :error="errors.secondhand_price"
          />
          <app-field
            v-model="form.now_val"
            label="当前账面价值"
            type="number"
            inputmode="decimal"
            placeholder="选填，留空按购入价计算"
            suffix="元"
            :error="errors.now_val"
          />
        </van-cell-group>

        <div v-if="isEditing" class="asset-form__tip">购买价格与购买日期登记后不可修改。</div>
      </div>

      <div class="asset-form__footer">
        <app-button type="primary" block round :loading="saving" @click="submit">
          {{ isEditing ? "保存修改" : "登记资产" }}
        </app-button>
      </div>
    </div>

    <!-- 品类选择 -->
    <van-popup v-model:show="showTagPicker" position="bottom" round teleport="body">
      <van-picker
        title="选择品类"
        :columns="tagColumns"
        @confirm="onTagConfirm"
        @cancel="showTagPicker = false"
      />
    </van-popup>

    <!-- 购买日期 -->
    <van-popup v-model:show="showDatePicker" position="bottom" round teleport="body">
      <van-date-picker
        v-model="datePickerValue"
        title="选择购买日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <!-- 图片选择 -->
    <van-popup v-model:show="showImagePicker" position="bottom" round teleport="body" :style="{ height: '80%' }">
      <div class="image-picker">
        <div class="image-picker__header">
          <span class="image-picker__title">选择资产图片</span>
          <van-icon name="cross" aria-label="关闭图片选择" @click="showImagePicker = false" />
        </div>

        <div class="image-picker__upload">
          <van-uploader v-model="uploadFileList" :max-count="1" accept="image/*" :preview-size="72">
            <div class="image-picker__trigger">
              <van-icon name="plus" size="22" />
              <span>选择文件</span>
            </div>
          </van-uploader>
          <app-button
            type="primary"
            size="small"
            :loading="imageUploading"
            :disabled="uploadFileList.length === 0"
            @click="handleImageUpload"
          >
            上传
          </app-button>
        </div>

        <van-divider>或选择已有图片</van-divider>

        <div v-if="imageLoading" class="image-picker__loading">
          <van-loading>加载中...</van-loading>
        </div>
        <div v-else-if="imageList.length === 0" class="image-picker__empty">暂无图片，请先上传</div>
        <div v-else class="image-picker__grid">
          <div
            v-for="item in imageList"
            :key="item.id"
            class="image-picker__item"
            :class="{ 'is-selected': form.img_url === (item.url || item.file_path) }"
            @click="selectImage(item)"
          >
            <img :src="getImageUrl(item.thumbnail || item.file_path || item.url)" alt="" />
            <div v-if="form.img_url === (item.url || item.file_path)" class="image-picker__check">
              <van-icon name="success" />
            </div>
          </div>
        </div>
      </div>
    </van-popup>
  </van-popup>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { showConfirmDialog } from 'vant'
import { showToast, showSuccessToast } from '@/utils/toast'
import { apiErrorToast } from '@/utils/request/helpers'
import {
  createFixedAsset,
  updateFixedAsset,
  getAssetImageList,
  uploadAssetImage,
} from '@/utils/api/fixedAsset'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 编辑目标；null = 新增 */
  asset: { type: Object, default: null },
  /** AI 引导卡带入的登记信息 */
  prefill: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'success'])

const TAG_YEARS = { 电脑: 3, 手机: 2, 平板: 2, 外设: 2, 家电: 5, 家具: 5, 其他: 3 }
const EMPTY_FORM = {
  info: '',
  tag: '',
  img_url: '',
  buy_price: '',
  buy_date: '',
  use_years: '',
  residual_rate: '5',
  secondhand_price: '',
  now_val: '',
}

const form = ref({ ...EMPTY_FORM })
const errors = ref({})
const saving = ref(false)
const showTagPicker = ref(false)
const showDatePicker = ref(false)
const showImagePicker = ref(false)
const datePickerValue = ref([String(dayjs().year()), String(dayjs().month() + 1), String(dayjs().date())])
const minDate = new Date(2000, 0, 1)
const maxDate = new Date()
let initialSnapshot = ''

const isEditing = computed(() => !!props.asset)
const prefillNotice = computed(() => {
  if (isEditing.value || !props.prefill) return ''
  return '已带入聊天中核对的信息，请补齐图片并核对后保存。'
})

const tagColumns = computed(() => {
  const tags = Object.keys(TAG_YEARS)
  if (form.value.tag && !tags.includes(form.value.tag)) tags.push(form.value.tag)
  return tags.map(text => ({ text, value: text }))
})

const numberText = value => (value === null || value === undefined ? '' : String(value))
const today = () => dayjs().format('YYYY-MM-DD')

function resetForm() {
  const base = { ...EMPTY_FORM, buy_date: today() }
  if (props.asset) {
    Object.assign(base, {
      info: props.asset.info || '',
      tag: props.asset.tag || '',
      img_url: props.asset.img_url || '',
      buy_price: numberText(props.asset.buy_price),
      buy_date: props.asset.buy_date || '',
      use_years: numberText(props.asset.use_years),
      residual_rate: numberText(props.asset.residual_rate) || '5',
      secondhand_price: numberText(props.asset.secondhand_price),
      now_val: numberText(props.asset.now_val),
    })
  } else if (props.prefill) {
    const source = props.prefill
    if (source.info) base.info = String(source.info)
    if (source.tag) base.tag = String(source.tag)
    if (source.buyPrice !== undefined && source.buyPrice !== null) base.buy_price = String(source.buyPrice)
    if (source.buyDate) base.buy_date = String(source.buyDate)
    if (source.useYears !== undefined && source.useYears !== null) base.use_years = String(source.useYears)
    if (source.residualRate !== undefined && source.residualRate !== null) base.residual_rate = String(source.residualRate)
  }
  form.value = base
  errors.value = {}
  initialSnapshot = JSON.stringify(base)
  const parsed = dayjs(base.buy_date)
  const date = parsed.isValid() ? parsed : dayjs()
  datePickerValue.value = [String(date.year()), String(date.month() + 1), String(date.date())]
}

watch(
  () => props.modelValue,
  visible => {
    if (visible) resetForm()
  },
  { flush: 'post' }
)

function onPopupShow(visible) {
  if (!visible) emit('update:modelValue', false)
}

function isDirty() {
  return JSON.stringify(form.value) !== initialSnapshot
}

async function requestClose() {
  if (saving.value) return
  if (isDirty()) {
    try {
      await showConfirmDialog({
        title: '放弃填写？',
        message: '当前填写的内容不会保存。',
        confirmButtonText: '放弃',
        cancelButtonText: '继续填写',
        confirmButtonColor: '#ee0a24',
      })
    } catch {
      return
    }
  }
  emit('update:modelValue', false)
}

function openTagPicker() {
  showTagPicker.value = true
}

function onTagConfirm({ selectedOptions }) {
  const tag = selectedOptions?.[0]?.text || ''
  form.value.tag = tag
  showTagPicker.value = false
  errors.value.tag = ''
  if (!form.value.use_years && TAG_YEARS[tag]) form.value.use_years = String(TAG_YEARS[tag])
}

function openDatePicker() {
  const parsed = dayjs(form.value.buy_date)
  const date = parsed.isValid() ? parsed : dayjs()
  datePickerValue.value = [String(date.year()), String(date.month() + 1), String(date.date())]
  showDatePicker.value = true
}

function onDateConfirm({ selectedValues }) {
  form.value.buy_date = selectedValues.join('-')
  showDatePicker.value = false
  errors.value.buy_date = ''
}

const imageList = ref([])
const imageLoading = ref(false)
const imageUploading = ref(false)
const uploadFileList = ref([])

async function openImagePicker() {
  showImagePicker.value = true
  uploadFileList.value = []
  imageLoading.value = true
  try {
    const res = await getAssetImageList(100, 0)
    imageList.value = res.data || []
  } catch (exception) {
    imageList.value = []
    apiErrorToast(exception, '加载图片失败')
  } finally {
    imageLoading.value = false
  }
}

function selectImage(item) {
  form.value.img_url = item.url || item.file_path
  errors.value.img_url = ''
  showImagePicker.value = false
}

async function handleImageUpload() {
  if (!uploadFileList.value.length) return showToast('请选择要上传的图片')
  imageUploading.value = true
  try {
    for (const item of uploadFileList.value) {
      await uploadAssetImage(item.file, '资产图片')
    }
    showSuccessToast('上传成功')
    uploadFileList.value = []
    const res = await getAssetImageList(100, 0)
    imageList.value = res.data || []
  } catch (exception) {
    apiErrorToast(exception, '上传失败')
  } finally {
    imageUploading.value = false
  }
}

const getImageUrl = path => {
  if (!path) return ''
  if (/^https?:/.test(path)) return path
  const base = import.meta.env.VITE_FILE_BASE_URL || ''
  return base + path
}

function moneyValue(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return null
  if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return NaN
  return Number(text)
}

function validate() {
  const next = {}
  const source = form.value
  const info = String(source.info || '').trim()
  if (!info) next.info = '请输入资产名称'
  else if (info.length > 255) next.info = '资产名称不能超过 255 字'
  if (!source.tag) next.tag = '请选择品类'
  if (!source.img_url) next.img_url = '请选择一张资产图片'

  const price = moneyValue(source.buy_price)
  if (price === null) next.buy_price = '请输入购买价格'
  else if (Number.isNaN(price)) next.buy_price = '购买价格最多两位小数'
  else if (price <= 0) next.buy_price = '购买价格必须大于0'

  if (!source.buy_date) next.buy_date = '请选择购买日期'

  const years = Number(source.use_years)
  if (!String(source.use_years ?? '').trim()) next.use_years = '请输入预计使用年限'
  else if (!Number.isFinite(years) || years <= 0) next.use_years = '预计使用年限必须大于0'
  else if (years > 100) next.use_years = '预计使用年限不能超过100年'

  const rate = Number(source.residual_rate)
  if (!String(source.residual_rate ?? '').trim()) next.residual_rate = '请输入残值率'
  else if (!Number.isFinite(rate) || rate < 0 || rate > 100) next.residual_rate = '残值率必须在0~100之间'

  const secondhand = moneyValue(source.secondhand_price)
  if (Number.isNaN(secondhand)) next.secondhand_price = '二手市场价最多两位小数'
  else if (secondhand !== null && secondhand < 0) next.secondhand_price = '二手市场价不能为负数'

  const nowValue = moneyValue(source.now_val)
  if (Number.isNaN(nowValue)) next.now_val = '当前账面价值最多两位小数'
  else if (nowValue !== null && nowValue < 0) next.now_val = '当前账面价值不能为负数'

  errors.value = next
  const first = Object.keys(next)[0]
  if (first) return null
  return {
    info,
    tag: source.tag,
    img_url: source.img_url,
    buy_price: price,
    use_years: years,
    residual_rate: rate,
    secondhand_price: secondhand,
    now_val: nowValue,
    ...(isEditing.value ? {} : { buy_date: source.buy_date }),
  }
}

async function submit() {
  if (saving.value) return
  const payload = validate()
  if (!payload) return
  saving.value = true
  try {
    if (isEditing.value) await updateFixedAsset(props.asset.id, payload)
    else await createFixedAsset(payload)
    showSuccessToast(isEditing.value ? '保存成功' : '登记成功')
    emit('success')
    emit('update:modelValue', false)
  } catch (exception) {
    apiErrorToast(exception, isEditing.value ? '保存失败' : '登记失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.asset-form {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--theme-bg-primary);
}

.asset-form__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--theme-bg-secondary);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.asset-form__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.asset-form__close {
  font-size: 18px;
  color: var(--theme-text-tertiary);
  padding: 4px;
}

.asset-form__body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px 0 16px;
}

.asset-form__note {
  margin: 0 16px 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--theme-bg-secondary);
  color: var(--theme-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.asset-form__image {
  margin: 0 16px 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--theme-bg-secondary);
}

.asset-form__image-label {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin-bottom: 8px;
}

.asset-form__required {
  color: var(--van-danger-color, #ee0a24);
  margin-left: 2px;
}

.asset-form__hint {
  margin-left: 8px;
  font-size: 11px;
  color: var(--theme-text-tertiary);
}

.asset-form__image-box {
  width: 96px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed var(--theme-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.asset-form__image-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.asset-form__image-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--theme-text-tertiary);
  font-size: 11px;
}

.asset-form__image-empty .van-icon {
  font-size: 26px;
}

.asset-form__error {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-danger-color, #ee0a24);
}

.asset-form__tip {
  margin: 12px 16px 0;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.asset-form__footer {
  flex-shrink: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: var(--theme-bg-secondary);
  border-top: 1px solid var(--theme-border);
}

.image-picker {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 16px;
  overflow-y: auto;
  background: var(--theme-bg-secondary);
}

.image-picker__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.image-picker__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
}

.image-picker__upload {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.image-picker__trigger {
  width: 72px;
  height: 72px;
  border: 1px dashed var(--theme-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--theme-text-tertiary);
  font-size: 11px;
}

.image-picker__loading,
.image-picker__empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: var(--theme-text-tertiary);
}

.image-picker__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding-bottom: 12px;
}

.image-picker__item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  cursor: pointer;
}

.image-picker__item.is-selected {
  border-color: var(--theme-primary);
}

.image-picker__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-picker__check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--theme-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
</style>
