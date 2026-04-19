<template>
  <div class="page-budget-execute">

    <!-- 预算信息卡片 -->
    <div class="budget-info-card" v-if="budgetInfo">
      <div class="budget-title">{{ budgetInfo.title }}</div>
      <div class="budget-stats">
        <div class="stat-item">
          <span class="label">预算</span>
          <span class="value">¥{{ formatAmount(budgetInfo.budget_amount) }}</span>
        </div>
        <div class="stat-item">
          <span class="label">已用</span>
          <span class="value used">¥{{ formatAmount(budgetInfo.used_amount) }}</span>
        </div>
        <div class="stat-item">
          <span class="label">剩余</span>
          <span class="value remaining" :class="{ negative: remaining < 0 }">
            ¥{{ formatAmount(remaining) }}
          </span>
        </div>
      </div>
    </div>

    <van-form ref="formRef">
      <!-- 支出登记 -->
      <van-cell-group inset title="支出信息">
        <van-field
          v-model="formData.amount"
          label="支出金额"
          type="number"
          placeholder="0.00"
          :rules="[{ required: true, message: '请输入支出金额' }]"
        >
          <template #button><span class="yuan">元</span></template>
        </van-field>
        <van-field
          v-model="formData.budget_details.merchant"
          label="商家/地点"
          placeholder="如：麦当劳"
        />
        <van-field
          v-model="formData.budget_details.category"
          label="消费类别"
          placeholder="如：午餐、早餐"
        />
        <van-field
          v-model="formData.budget_details.payment"
          label="支付方式"
          placeholder="如：微信、支付宝"
        />
        <van-field
          v-model="formData.budget_details.notes"
          label="备注"
          type="textarea"
          placeholder="可选，添加备注"
          rows="2"
          autosize
        />
      </van-cell-group>

      <div class="form-actions">
        <van-button size="large" round type="primary" :loading="saving" @click="submit">
          确认登记
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getBudget, executeBudget } from '@/utils/api/budget'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const saving = ref(false)
const budgetInfo = ref(null)

const formData = ref({
  amount: '',
  budget_details: {
    merchant: '',
    category: '',
    payment: '',
    notes: '',
  },
})

const remaining = computed(() => {
  if (!budgetInfo.value) return 0
  return parseFloat(budgetInfo.value.budget_amount || 0) - parseFloat(budgetInfo.value.used_amount || 0)
})

const formatAmount = (amount) => {
  return (parseFloat(amount) || 0).toFixed(2)
}

const loadBudget = async () => {
  try {
    const res = await getBudget(route.params.id)
    budgetInfo.value = res.data
  } catch (e) {
    showToast('加载预算信息失败')
    router.back()
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
      amount: parseFloat(formData.value.amount),
      budget_details: {
        merchant: formData.value.budget_details.merchant,
        category: formData.value.budget_details.category,
        payment: formData.value.budget_details.payment,
        notes: formData.value.budget_details.notes,
      },
    }

    await executeBudget(route.params.id, data)
    showSuccessToast('登记成功')
    router.replace(`/finance/budget/detail/${route.params.id}`)
  } catch (e) {
    showToast(e.message || '登记失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadBudget()
})
</script>

<style scoped>
.page-budget-execute {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 24px;
}

.budget-info-card {
  background: linear-gradient(135deg, #1989fa, #1976d2);
  margin: 12px 16px;
  border-radius: 12px;
  padding: 16px;
  color: #fff;
}

.budget-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.budget-stats {
  display: flex;
  justify-content: space-between;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-item .label {
  font-size: 12px;
  opacity: 0.85;
}

.stat-item .value {
  font-size: 16px;
  font-weight: 700;
}

.stat-item .value.used {
  color: #ffd700;
}

.stat-item .value.remaining {
  color: #07c160;
}

.stat-item .value.remaining.negative {
  color: #ee0a24;
}

.yuan {
  color: #969799;
  font-size: 14px;
}

.form-actions {
  padding: 24px 16px;
}
</style>
