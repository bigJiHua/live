<template>
  <div class="page-budget-shopping">
    <van-form ref="formRef">
      <!-- 基本信息 -->
      <van-cell-group inset title="购物计划">
        <van-field
          v-model="formData.title"
          label="计划标题"
          placeholder="如：618购物清单"
          :rules="[{ required: true, message: '请输入计划标题' }]"
          class="field-compact"
        />
        <div class="info-row-grid">
          <van-field
            v-model="formData.plan_date"
            label="购买日期"
            readonly
            is-link
            placeholder="请选择"
            :rules="[{ required: true, message: '请选择预计日期' }]"
            class="field-compact"
            @click="showPlanDatePicker"
          />
          <van-field
            v-model="formData.cycle"
            label="预算周期"
            readonly
            placeholder="请选择"
            @click="showCyclePopup = true"
            class="field-compact"
          >
            <template #right-icon><van-icon name="arrow-down" /></template>
          </van-field>
        </div>
        <van-field
          v-model="formData.budget_amount"
          label="总预算"
          type="number"
          placeholder="0.00"
          :rules="[{ required: true, message: '请输入总预算' }]"
          class="field-compact"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
      </van-cell-group>

      <!-- 购物明细 -->
      <div class="section-header">
        <span class="section-title">购物清单</span>
        <van-button size="small" type="primary" plain @click="addItem">添加商品</van-button>
      </div>

      <div v-for="(item, index) in formData.items" :key="index" class="item-card">
        <div class="item-header">
          <span class="item-index">{{ index + 1 }}</span>
          <van-icon name="cross" class="delete-icon" @click="removeItem(index)" />
        </div>

        <van-field
          v-model="item.name"
          label="商品名称"
          placeholder="如：iPhone 16"
          class="item-name"
        />

        <div class="item-row-grid">
          <van-field
            v-model="item.category"
            label="类别"
            placeholder="电子产品"
            class="field-compact"
          />
          <van-field
            v-model="item.quantity"
            label="数量"
            type="number"
            placeholder="1"
            class="field-compact"
          />
          <van-field
            v-model="item.priority"
            label="优先级"
            readonly
            @click="showPriorityPicker(index)"
            placeholder="必买"
            class="field-compact"
          />
        </div>

        <div class="item-row-grid">
          <van-field
            v-model="item.price"
            label="预计价格"
            type="number"
            placeholder="0.00"
            class="field-compact"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
          <van-field
            v-model="item.actual_price"
            label="实际价格"
            type="number"
            placeholder="0.00"
            class="field-compact"
          >
            <template #button><span class="yuan">元</span></template>
          </van-field>
        </div>

        <div class="item-row-grid">
          <van-field
            v-model="item.purchase_date"
            label="购买日期"
            readonly
            is-link
            placeholder="选择日期"
            class="field-compact"
            @click="showDatePicker(index)"
          />
          <van-field
            v-model="item.shop"
            label="店铺"
            placeholder="京东自营"
            class="field-compact"
          />
        </div>

        <van-field
          v-model="item.notes"
          label="备注"
          placeholder="颜色、规格等"
        />
      </div>

      <van-empty v-if="formData.items.length === 0" description="点击上方添加商品" />

      <!-- 预算汇总 -->
      <van-cell-group inset class="summary-group">
        <van-cell title="预算总额">
          <template #value>
            <span class="budget-amount">¥{{ formatAmount(totalBudget) }}</span>
          </template>
        </van-cell>
        <van-cell title="预计总花费">
          <template #value>
            <span class="estimated-amount">¥{{ formatAmount(estimatedTotal) }}</span>
          </template>
        </van-cell>
        <van-cell title="实际总花费">
          <template #value>
            <span class="actual-amount">¥{{ formatAmount(actualTotal) }}</span>
          </template>
        </van-cell>
        <van-cell title="已购/总数">
          <template #value>
            <span class="progress-text">{{ purchasedCount }} / {{ formData.items.length }}</span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 备注 -->
      <van-cell-group inset title="备注说明">
        <van-field
          v-model="formData.budget_details.notes"
          label="备注"
          type="textarea"
          placeholder="可选，添加备注说明"
          rows="2"
          autosize
        />
      </van-cell-group>

      <div class="form-actions">
        <van-button size="large" round type="primary" :loading="saving" @click="submit">
          {{ isEdit ? '保存修改' : '创建购物计划' }}
        </van-button>
      </div>
    </van-form>

    <!-- 优先级选择 -->
    <van-popup v-model:show="showPriorityPopup" position="bottom" round>
      <van-picker
        title="选择优先级"
        :columns="priorityColumns"
        @confirm="onPriorityConfirm"
        @cancel="showPriorityPopup = false"
      />
    </van-popup>
    <van-popup v-model:show="showCyclePopup" position="bottom" round>
      <van-picker
        title="选择周期"
        :columns="cycleColumns"
        @confirm="onCycleConfirm"
        @cancel="showCyclePopup = false"
      />
    </van-popup>
    <!-- 日期选择 -->
    <van-calendar
      v-model:show="showDatePopup"
      :default-date="defaultDate"
      color="#1989fa"
      @confirm="onDateConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { createBudget, updateBudget, getBudget } from '@/utils/api/budget'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const saving = ref(false)
const showPriorityPopup = ref(false)
const showCyclePopup = ref(false)
const showDatePopup = ref(false)
const tempItemIndex = ref(0)
const isPlanDate = ref(false)
const defaultDate = new Date()

const formatDate = (date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const cycleColumns = [
  { text: '月', value: '月' },
  { text: '季', value: '季' },
  { text: '年', value: '年' },
]

const priorityColumns = [
  { text: '必买', value: '必买' },
  { text: '想要', value: '想要' },
  { text: '可选', value: '可选' },
]

const formData = ref({
  title: '',
  budget_type: '买',
  plan_date: formatDate(new Date()),
  cycle: '',
  budget_amount: '',
  items: [],
  budget_details: {
    notes: '',
  },
})

const filteredItems = computed(() => {
  return formData.value.items.filter(item => 
    item.name && item.name.trim() !== ''
  )
})

const totalBudget = computed(() => {
  return parseFloat(formData.value.budget_amount) || 0
})

const estimatedTotal = computed(() => {
  return filteredItems.value.reduce((sum, item) => {
    const price = parseFloat(item.price) || 0
    const qty = parseFloat(item.quantity) || 1
    return sum + price * qty
  }, 0)
})

const actualTotal = computed(() => {
  return filteredItems.value.reduce((sum, item) => {
    return sum + (parseFloat(item.actual_price) || 0)
  }, 0)
})

const purchasedCount = computed(() => {
  return filteredItems.value.filter(item => item.actual_price).length
})

const formatAmount = (amount) => {
  return (parseFloat(amount) || 0).toFixed(2)
}

const addItem = () => {
  formData.value.items.push({
    name: '',
    category: '',
    price: '',
    quantity: '1',
    priority: '',
    purchase_date: formatDate(new Date()),
    actual_price: '',
    shop: '',
    notes: '',
  })
}

const removeItem = (index) => {
  formData.value.items.splice(index, 1)
}

const showPriorityPicker = (index) => {
  tempItemIndex.value = index
  showPriorityPopup.value = true
}

const onPriorityConfirm = ({ selectedOptions }) => {
  formData.value.items[tempItemIndex.value].priority = selectedOptions[0].value
  showPriorityPopup.value = false
}

const onCycleConfirm = ({ selectedOptions }) => {
  formData.value.cycle = selectedOptions[0].value
  showCyclePopup.value = false
}

const showPlanDatePicker = () => {
  isPlanDate.value = true
  showDatePopup.value = true
}

const showDatePicker = (index) => {
  isPlanDate.value = false
  tempItemIndex.value = index
  showDatePopup.value = true
}

const onDateConfirm = (value) => {
  if (isPlanDate.value) {
    formData.value.plan_date = formatDate(value)
  } else {
    formData.value.items[tempItemIndex.value].purchase_date = formatDate(value)
  }
  showDatePopup.value = false
}

const loadData = async () => {
  if (!route.params.id) return
  try {
    const res = await getBudget(route.params.id)
    const data = res.data
    const details = data.budget_details || {}
    formData.value = {
      title: data.title || '',
      budget_type: data.budget_type || '买',
      plan_date: data.plan_date || '',
      budget_amount: data.budget_amount || '',
      cycle: data.cycle || '',
      items: details.items || [],
      budget_details: details,
    }
  } catch (e) {
    showToast('加载失败')
  }
}

const submit = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    await showConfirmDialog({
      title: '确认提交',
      message: isEdit.value ? '确定要保存修改吗？' : '确定要创建购物计划吗？',
    })
  } catch {
    return
  }

  saving.value = true
  try {
    const data = {
      title: formData.value.title,
      budget_type: "买",
      plan_date: formData.value.plan_date,
      cycle: formData.value.cycle || "月",
      budget_amount: parseFloat(formData.value.budget_amount),
      budget_details: {
        items: filteredItems.value,
        estimated_total: estimatedTotal.value,
        actual_total: actualTotal.value,
        purchased_count: purchasedCount.value,
        notes: formData.value.budget_details.notes,
      },
    }

    if (isEdit.value) {
      await updateBudget(route.params.id, data)
    } else {
      await createBudget(data)
    }

    showSuccessToast('保存成功')
    router.replace('/finance/budget')
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (isEdit.value) {
    loadData()
  }
})
</script>

<style scoped>
.page-budget-shopping {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 16px;
}

.yuan {
  color: #969799;
  font-size: 14px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 6px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.item-card {
  background: #fff;
  margin: 0 12px 8px;
  border-radius: 8px;
  padding: 8px 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.item-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #1989fa;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
}

.delete-icon {
  color: #ee0a24;
  font-size: 16px;
  padding: 2px;
}

.item-name {
  margin-bottom: 0;
}

.item-row-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 12px;
  margin-top: 2px;
}

.item-row-grid:nth-of-type(2),
.item-row-grid:nth-of-type(3) {
  grid-template-columns: repeat(2, 1fr);
}

.info-row-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 12px;
}

.field-compact :deep(.van-field__label) {
  font-size: 13px;
  width: auto;
  margin-right: 8px;
}

.field-compact :deep(.van-field__control) {
  font-size: 13px;
}

:deep(.van-cell-group--inset) {
  margin: 8px 12px;
}

:deep(.van-field) {
  padding: 8px 0;
}

.summary-group {
  margin-top: 8px;
}

.budget-amount {
  font-weight: 600;
  color: #323233;
}

.estimated-amount {
  font-weight: 600;
  color: #1989fa;
}

.actual-amount {
  font-weight: 600;
  color: #ee0a24;
}

.progress-text {
  color: #07c160;
  font-weight: 600;
}

.form-actions {
  padding: 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
