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
        />
        <van-field
          v-model="formData.plan_date"
          label="预计购买日期"
          type="date"
          placeholder="请选择"
          :rules="[{ required: true, message: '请选择预计日期' }]"
        />
        <van-field
          v-model="formData.budget_amount"
          label="总预算"
          type="number"
          placeholder="0.00"
          :rules="[{ required: true, message: '请输入总预算' }]"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
        <van-field
          v-model="formData.cycle"
          label="预算周期"
          readonly
          placeholder="请选择"
          @click="showCyclePopup = true"
        >
          <template #right-icon><van-icon name="arrow-down" /></template>
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
        <van-field
          v-model="item.category"
          label="商品类别"
          placeholder="如：电子产品"
        />
        <van-field
          v-model="item.price"
          label="预计价格"
          type="number"
          placeholder="0.00"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
        <van-field
          v-model="item.quantity"
          label="数量"
          type="number"
          placeholder="1"
        />
        <van-field
          v-model="item.priority"
          label="优先级"
          readonly
          @click="showPriorityPicker(index)"
          placeholder="必买/想要/可选"
        />
        <van-field
          v-model="item.purchase_date"
          label="购买日期"
          type="date"
          placeholder="实际购买日期"
        />
        <van-field
          v-model="item.actual_price"
          label="实际价格"
          type="number"
          placeholder="实际花了多少"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
        <van-field
          v-model="item.shop"
          label="购买店铺"
          placeholder="如：京东自营"
        />
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
        <van-button v-if="isEdit" size="large" round type="default" @click="goExecute">
          登记实际购买
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { createBudget, updateBudget, getBudget } from '@/utils/api/budget'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const saving = ref(false)
const showPriorityPopup = ref(false)
const showCyclePopup = ref(false)
const tempItemIndex = ref(0)

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
  plan_date: '',
  cycle: '',
  budget_amount: '',
  items: [],
  budget_details: {
    notes: '',
  },
})

const totalBudget = computed(() => {
  return parseFloat(formData.value.budget_amount) || 0
})

const estimatedTotal = computed(() => {
  return formData.value.items.reduce((sum, item) => {
    const price = parseFloat(item.price) || 0
    const qty = parseFloat(item.quantity) || 1
    return sum + price * qty
  }, 0)
})

const actualTotal = computed(() => {
  return formData.value.items.reduce((sum, item) => {
    return sum + (parseFloat(item.actual_price) || 0)
  }, 0)
})

const purchasedCount = computed(() => {
  return formData.value.items.filter(item => item.actual_price).length
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
    purchase_date: '',
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

const loadData = async () => {
  if (!route.params.id) return
  try {
    const res = await getBudget(route.params.id)
    const data = res.data
    formData.value = {
      title: data.title || '',
      budget_type: data.budget_type || '买',
      plan_date: data.plan_date || '',
      budget_amount: data.budget_amount || '',
      items: data.items || [],
      budget_details: data.budget_details || {},
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

  saving.value = true
  try {
    const data = {
      title: formData.value.title,
      budget_type: "买",
      plan_date: formData.value.plan_date,
      cycle: formData.value.cycle || "月",
      budget_amount: parseFloat(formData.value.budget_amount),
      budget_details: {
        items: formData.value.items,
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

const goExecute = () => {
  router.push(`/finance/budget/execute/${route.params.id}`)
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
  padding-bottom: 24px;
}

.yuan {
  color: #969799;
  font-size: 14px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.item-card {
  background: #fff;
  margin: 0 16px 12px;
  border-radius: 12px;
  padding: 12px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: #1989fa;
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
}

.delete-icon {
  color: #ee0a24;
  font-size: 18px;
  padding: 4px;
}

.item-name {
  margin-bottom: 8px;
}

.summary-group {
  margin-top: 12px;
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
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
