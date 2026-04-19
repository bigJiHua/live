<template>
  <van-popup v-model:show="show" position="bottom" round :close-on-click-overlay="false">
    <div class="form-popup">
      <div class="popup-header">
        <span class="popup-title">{{ asset ? '编辑资产' : '登记固定资产' }}</span>
        <van-icon name="cross" @click="close" />
      </div>

      <van-form ref="formRef">
        <!-- 图片上传 -->
        <div class="upload-section">
          <div class="upload-label">资产图片 <span class="required">*</span></div>
          <div class="upload-area" @click="triggerUpload">
            <img v-if="formData.img_url" :src="getImageUrl(formData.img_url)" class="preview-img" />
            <div v-else class="upload-placeholder">
              <van-icon name="photograph" />
              <span>点击选择图片</span>
            </div>
          </div>
        </div>

        <van-cell-group inset>
          <van-field
            v-model="formData.info"
            label="资产名称"
            placeholder="如：MacBook Pro 14寸"
            :rules="[{ required: true, message: '请输入资产名称' }]"
          />
          <van-field
            v-model="formData.tag"
            label="品类标签"
            readonly
            @click="showTagPicker = true"
            placeholder="请选择品类"
            :rules="[{ required: true, message: '请选择品类' }]"
          />
          <van-field
            v-model="formData.buy_price"
            label="购买价格"
            type="number"
            placeholder="必须大于0"
            :rules="[{ required: true, message: '请输入购买价格' }]"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
          <van-field
            v-model="formData.buy_date"
            label="购买日期"
            type="date"
            placeholder="请选择"
            :rules="[{ required: true, message: '请选择购买日期' }]"
          />
          <van-field
            v-model="formData.use_years"
            label="预计使用年限"
            type="number"
            step="0.1"
            :placeholder="'推荐 ' + recommendedYears + ' 年'"
            :rules="[{ required: true, message: '请输入预计使用年限' }]"
          >
            <template #button>
              <van-button size="small" type="primary" plain @click="applyRecommended">推荐</van-button>
            </template>
          </van-field>
          <van-field
            v-model="formData.residual_rate"
            label="残值率"
            type="number"
            placeholder="0~100，默认5%"
            :rules="[{ required: true, message: '请输入残值率' }]"
          >
            <template #button><span class="yuan">%</span></template>
          </van-field>
          <van-field
            v-model="formData.secondhand_price"
            label="二手市场价"
            type="number"
            placeholder="选填"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
        </van-cell-group>

        <div class="form-actions">
          <van-button size="large" round @click="close">取消</van-button>
          <van-button size="large" round type="primary" :loading="saving" @click="submit">保存</van-button>
        </div>
      </van-form>
    </div>

    <!-- 品类选择 -->
    <van-popup v-model:show="showTagPicker" position="bottom" round>
      <van-picker
        title="选择品类"
        :columns="tagColumns"
        @confirm="onTagConfirm"
        @cancel="showTagPicker = false"
      />
    </van-popup>

    <!-- 图片选择弹窗 -->
    <van-popup v-model:show="showImagePicker" position="bottom" round :style="{ height: '85%' }">
      <div class="image-picker-popup">
        <div class="popup-header">
          <span class="popup-title">选择资产图片</span>
          <van-icon name="cross" @click="showImagePicker = false" />
        </div>

        <div class="upload-section">
          <div class="upload-label">上传新图片</div>
          <van-uploader
            v-model="uploadFileList"
            :after-read="handleAfterRead"
            :max-count="1"
            accept="image/*"
            :preview-size="80"
          >
            <div class="upload-trigger-small">
              <van-icon name="plus" size="24" />
              <span>选择文件</span>
            </div>
          </van-uploader>
          <van-button
            type="primary"
            size="small"
            :loading="imageUploading"
            :disabled="uploadFileList.length === 0"
            @click="handleImageUpload"
            style="margin-top: 12px"
          >
            上传
          </van-button>
        </div>

        <van-divider>或选择已有图片</van-divider>

        <div class="image-grid" v-if="!imageLoading">
          <div
            v-for="item in imageList"
            :key="item.id"
            class="image-item"
            :class="{ selected: formData.img_url === (item.url || item.file_path) }"
            @click="selectImage(item)"
          >
            <img :src="getImageUrl(item.thumbnail || item.file_path)" />
            <div class="image-check" v-if="formData.img_url === (item.url || item.file_path)">
              <van-icon name="success" />
            </div>
          </div>
        </div>
        <div class="loading-state" v-else>
          <van-loading>加载中...</van-loading>
        </div>
        <van-empty v-if="!imageLoading && imageList.length === 0" description="暂无图片" />
      </div>
    </van-popup>
  </van-popup>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { showToast, showSuccessToast } from 'vant'
import { createFixedAsset, updateFixedAsset, getAssetImageList, uploadAssetImage } from '@/utils/api/fixedAsset'

const props = defineProps({
  modelValue: Boolean,
  asset: Object, // 编辑时传入资产数据
})

const emit = defineEmits(['update:modelValue', 'success'])

// 品类推荐年限
const tagRecommendedYears = {
  '电脑': 3, '手机': 2, '平板': 2, '外设': 2, '家电': 5, '家具': 5, '其他': 3,
}

const tagColumns = Object.keys(tagRecommendedYears).map(text => ({ text, value: text }))

// 表单数据
const formData = ref({
  info: '',
  tag: '',
  img_url: '',
  buy_price: '',
  buy_date: '',
  use_years: '',
  residual_rate: '5',
  secondhand_price: '',
})

const show = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const saving = ref(false)
const showTagPicker = ref(false)
const showImagePicker = ref(false)
const imageList = ref([])
const imageLoading = ref(false)
const imageUploading = ref(false)
const uploadFileList = ref([])
const formRef = ref(null)

const recommendedYears = computed(() => tagRecommendedYears[formData.value.tag] || 3)

const resetForm = () => {
  formData.value = {
    info: '', tag: '', img_url: '', buy_price: '',
    buy_date: '', use_years: '', residual_rate: '5', secondhand_price: '',
  }
}

// 监听编辑数据变化
watch(() => props.asset, (val) => {
  if (val) {
    formData.value = {
      info: val.info || '',
      tag: val.tag || '',
      img_url: val.img_url || '',
      buy_price: val.buy_price || '',
      buy_date: val.buy_date || '',
      use_years: val.use_years || '',
      residual_rate: val.residual_rate || '5',
      secondhand_price: val.secondhand_price || '',
    }
  } else {
    resetForm()
  }
}, { immediate: true })

const close = () => {
  show.value = false
  resetForm()
}

const applyRecommended = () => {
  formData.value.use_years = recommendedYears.value.toString()
}

const onTagConfirm = ({ selectedOptions }) => {
  formData.value.tag = selectedOptions[0].text
  if (!formData.value.use_years) {
    formData.value.use_years = (tagRecommendedYears[formData.value.tag] || 3).toString()
  }
  showTagPicker.value = false
}

const loadImageList = async () => {
  imageLoading.value = true
  try {
    const res = await getAssetImageList(100, 0)
    imageList.value = res.data || []
  } catch (e) {
    showToast('加载图片失败')
  } finally {
    imageLoading.value = false
  }
}

const triggerUpload = () => {
  loadImageList()
  uploadFileList.value = []
  showImagePicker.value = true
}

const selectImage = (item) => {
  formData.value.img_url = item.url || item.file_path
  showImagePicker.value = false
}

const handleAfterRead = () => {}

const handleImageUpload = async () => {
  if (uploadFileList.value.length === 0) return showToast('请选择要上传的图片')
  imageUploading.value = true
  try {
    showToast('上传中...')
    for (const item of uploadFileList.value) {
      await uploadAssetImage(item.file, 'logo资产')
    }
    showSuccessToast('上传成功')
    await loadImageList()
    uploadFileList.value = []
    showImagePicker.value = false
  } catch (e) {
    showToast('上传失败')
  } finally {
    imageUploading.value = false
  }
}

const getImageUrl = (path) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  const BASE_URL = import.meta.env.VITE_FILE_BASE_URL || ''
  return BASE_URL + path
}

const submit = async () => {
  if (!formData.value.info) return showToast('请输入资产名称')
  if (!formData.value.tag) return showToast('请选择品类')
  if (!formData.value.buy_price || parseFloat(formData.value.buy_price) <= 0) {
    return showToast('购买价格必须大于0')
  }
  if (!formData.value.buy_date) return showToast('请选择购买日期')
  if (!formData.value.use_years || parseFloat(formData.value.use_years) <= 0) {
    return showToast('预计使用年限必须大于0')
  }
  if (!formData.value.residual_rate || parseFloat(formData.value.residual_rate) < 0 || parseFloat(formData.value.residual_rate) > 100) {
    return showToast('残值率必须在0~100之间')
  }

  saving.value = true
  try {
    const data = {
      info: formData.value.info,
      tag: formData.value.tag,
      img_url: formData.value.img_url || '',
      buy_price: parseFloat(formData.value.buy_price),
      buy_date: formData.value.buy_date,
      use_years: parseFloat(formData.value.use_years),
      residual_rate: parseFloat(formData.value.residual_rate),
      secondhand_price: formData.value.secondhand_price ? parseFloat(formData.value.secondhand_price) : null,
    }

    if (props.asset) {
      await updateFixedAsset(props.asset.id, data)
    } else {
      await createFixedAsset(data)
    }

    showSuccessToast('保存成功')
    close()
    emit('success')
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-popup {
  padding: 16px;
  max-height: 85vh;
  overflow-y: auto;
}
.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.popup-title {
  font-size: 17px;
  font-weight: 600;
}
.yuan {
  color: #969799;
  font-size: 14px;
}
.required {
  color: #ee0a24;
}
.upload-section {
  padding: 0 16px;
  margin-bottom: 16px;
}
.upload-label {
  font-size: 14px;
  color: #646566;
  margin-bottom: 8px;
}
.upload-area {
  width: 120px;
  height: 120px;
  border: 1px dashed #dcdee0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}
.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #969799;
  font-size: 12px;
}
.upload-placeholder .van-icon {
  font-size: 28px;
}
.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding: 0 16px 20px;
}
.form-actions .van-button {
  flex: 1;
}
.image-picker-popup {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}
.upload-trigger-small {
  border: 1px dashed #dcdee0;
  border-radius: 8px;
  width: 80px;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #969799;
  font-size: 12px;
  gap: 4px;
}
.image-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 12px 0;
  max-height: 300px;
  overflow-y: auto;
}
.image-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
}
.image-item.selected {
  border-color: #1989fa;
}
.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.image-check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  background: #1989fa;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
}
.loading-state {
  display: flex;
  justify-content: center;
  padding: 40px;
}
</style>
