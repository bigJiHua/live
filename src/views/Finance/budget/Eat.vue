<template>
  <div class="page-budget-eat">
    <van-form ref="formRef">
      <!-- 基本信息 -->
      <van-cell-group inset title="本次餐饮">
        <van-field
          v-model="formData.title"
          label="标题"
          placeholder="如：周末家庭聚餐"
          :rules="[{ required: true, message: '请输入标题' }]"
          class="field-compact"
        />
        <div class="info-row-grid">
          <van-field
            v-model="formData.plan_date"
            label="用餐日期"
            type="date"
            placeholder="请选择"
            :rules="[{ required: true, message: '请选择日期' }]"
            class="field-compact"
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
          label="本次预算"
          type="number"
          placeholder="0.00"
          :rules="[{ required: true, message: '请输入预算' }]"
          class="field-compact"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
      </van-cell-group>

      <!-- 菜单明细 -->
      <div class="section-header">
        <span class="section-title">菜单</span>
        <van-button size="small" type="primary" plain @click="addDish">添加菜品</van-button>
      </div>

      <div class="menu-section">
        <div v-for="(dish, index) in formData.dishes" :key="index" class="dish-card">
          <div class="dish-header">
            <span class="dish-index">{{ index + 1 }}</span>
            <van-icon name="cross" class="delete-icon" @click="removeDish(index)" />
          </div>
          <van-field
            v-model="dish.name"
            label="菜品"
            placeholder="如：红烧肉"
          />
          <div class="dish-row-grid">
            <van-field
              v-model="dish.price"
              label="价格"
              type="number"
              placeholder="0.00"
              class="field-compact"
            >
              <template #button><span class="yuan">元</span></template>
            </van-field>
            <van-field
              v-model="dish.quantity"
              label="份数"
              type="number"
              placeholder="1"
              class="field-compact"
            />
          </div>
          <van-field
            v-model="dish.notes"
            label="备注"
            placeholder="口味、做法等"
          />
          <div class="dish-subtotal" v-if="dish.price">
            小计：¥{{ formatAmount(dishSubtotal(dish)) }}
          </div>
        </div>
      </div>

      <van-empty v-if="formData.dishes.length === 0" description="点击上方添加菜品" />

      <!-- 金额汇总 -->
      <van-cell-group inset class="summary-group">
        <van-cell title="预算">
          <template #value>
            <span class="budget-amount">¥{{ formatAmount(totalBudget) }}</span>
          </template>
        </van-cell>
        <van-cell title="实际">
          <template #value>
            <span class="actual-amount">¥{{ formatAmount(actualTotal) }}</span>
          </template>
        </van-cell>
        <van-cell title="余额">
          <template #value>
            <span :class="{ 'diff-positive': diff > 0, 'diff-negative': diff < 0 }">
              {{ diff > 0 ? '+' : '' }}{{ formatAmount(diff) }}
            </span>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 备注 -->
      <van-cell-group inset title="备注">
        <van-field
          v-model="formData.notes"
          label="备注"
          type="textarea"
          placeholder="餐厅名称、用餐人数等"
          rows="2"
          autosize
        />
      </van-cell-group>

      <div class="form-actions">
        <van-button size="large" round type="primary" :loading="saving" @click="submit">
          {{ isEdit ? '保存' : '创建' }}
        </van-button>
      </div>
    </van-form>

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
import { showToast, showSuccessToast, showConfirmDialog } from 'vant'
import { createBudget, updateBudget, getBudget } from '@/utils/api/budget'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)

const formRef = ref(null)
const saving = ref(false)
const showCyclePopup = ref(false)

const cycleColumns = [
  { text: '月', value: '月' },
  { text: '季', value: '季' },
  { text: '年', value: '年' },
]

const formData = ref({
  title: '',
  budget_type: '吃',
  plan_date: '',
  cycle: '',
  budget_amount: '',
  dishes: [],
  notes: '',
})

const filteredDishes = computed(() => {
  return formData.value.dishes.filter(dish => 
    dish.name && dish.name.trim() !== ''
  )
})

const dishSubtotal = (dish) => {
  const price = parseFloat(dish.price) || 0
  const qty = parseFloat(dish.quantity) || 1
  return price * qty
}

const actualTotal = computed(() => {
  return filteredDishes.value.reduce((sum, dish) => sum + dishSubtotal(dish), 0)
})

const totalBudget = computed(() => {
  return parseFloat(formData.value.budget_amount) || 0
})

const diff = computed(() => {
  return totalBudget.value - actualTotal.value
})

const formatAmount = (amount) => {
  return (parseFloat(amount) || 0).toFixed(2)
}

const addDish = () => {
  formData.value.dishes.push({
    name: '',
    price: '',
    quantity: '1',
    notes: '',
  })
}

const removeDish = (index) => {
  formData.value.dishes.splice(index, 1)
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
    const details = data.budget_details || {}
    formData.value = {
      title: data.title || '',
      budget_type: data.budget_type || '吃',
      plan_date: data.plan_date || '',
      budget_amount: data.budget_amount || '',
      cycle: data.cycle || '',
      dishes: details.dishes || [],
      notes: details.notes || '',
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
      message: isEdit.value ? '确定要保存修改吗？' : '确定要创建餐饮预算吗？',
    })
  } catch {
    return
  }

  saving.value = true
  try {
    const data = {
      title: formData.value.title,
      budget_type: "吃",
      plan_date: formData.value.plan_date,
      cycle: formData.value.cycle || "月",
      budget_amount: parseFloat(formData.value.budget_amount),
      budget_details: {
        dishes: filteredDishes.value,
        actual_total: actualTotal.value,
        notes: formData.value.notes,
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
.page-budget-eat {
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

.menu-section {
  padding: 0 12px;
}

.dish-card {
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.dish-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.dish-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: #ee0a24;
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

.dish-row-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 12px;
  margin-top: 2px;
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

.dish-subtotal {
  text-align: right;
  font-size: 12px;
  color: #969799;
  padding-top: 4px;
}

.summary-group {
  margin-top: 8px;
}

.budget-amount {
  font-weight: 600;
  color: #323233;
}

.actual-amount {
  font-weight: 600;
  color: #ee0a24;
}

.diff-positive {
  color: #07c160;
  font-weight: 600;
}

.diff-negative {
  color: #ee0a24;
  font-weight: 600;
}

.form-actions {
  padding: 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
