<template>
  <div class="page-finance-add" @click="showKeyboard = false">
    <!-- 顶部金额卡片 -->
    <div class="amount-card" @click.stop="showKeyboard = true">
      <div class="label">请输入{{ isExpense ? '支出' : '收入' }}金额</div>
      <div class="value">
        ¥ <span>{{ displayAmount || "0.00" }}</span>
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

        <!-- 日期选择 -->
        <van-cell title="日期" is-link @click="showDatePicker = true">
          <template #value>
            <span>{{ formatDate(selectedDate) }}</span>
          </template>
        </van-cell>

        <!-- 日期日历弹框 -->
        <van-calendar
          v-model:show="showDatePicker"
          :default-date="selectedDate"
          :min-date="minDate"
          :max-date="maxDate"
          @confirm="onDateConfirm"
        />

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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { showToast, showSuccessToast, showFailToast } from "vant";
import { categoryApi } from "@/utils/api/category";

// ============================================
// 接口
// ============================================

/**
 * 获取分类列表
 * @param {string} type - 分类类型：expense/income/asset/fixed
 */
const apiGetCategories = async (type) => {
  const res = await categoryApi.list(type);
  return res.data || [];
};

/**
 * 提交账单
 * @param {object} data - 账单数据
 * @returns {Promise}
 */
const apiSubmitBill = async (data) => {
  // TODO: 替换为实际 API
  console.log("提交账单数据:", data);
  return { success: true };
};

// ============================================
// 组件逻辑
// ============================================

// 基础状态
const displayAmount = ref("");
const showKeyboard = ref(false);
const submitting = ref(false);

// 类型状态
const type = ref("expense");
const isExpense = computed(() => type.value === "expense");

// 分类状态
const categories = ref([]);
const selectedCategory = ref(null);
const showCategoryPicker = ref(false);

// 日期状态
const minDate = new Date(2000, 0, 1);
const maxDate = new Date(2100, 11, 31);
const selectedDate = ref(new Date());
const showDatePicker = ref(false);

// 备注
const remark = ref("");

// 监听金额输入，decimal(12,2)：总共12位，小数2位，整数10位
watch(displayAmount, (val) => {
  // 限制只能输入数字和小数点
  val = val.replace(/[^\d.]/g, "");
  // 确保只有一个小数点
  const parts = val.split(".");
  if (parts.length > 2) {
    displayAmount.value = parts[0] + "." + parts.slice(1).join("");
    return;
  }
  // 限制小数位最多2位
  if (parts[1] && parts[1].length > 2) {
    displayAmount.value = parts[0] + "." + parts[1].slice(0, 2);
    return;
  }
  // 限制整数位最多10位 (12 - 2 = 10)
  if (parts[0].length > 10) {
    displayAmount.value = parts[0].slice(0, 10) + (parts[1] ? "." + parts[1] : "");
    return;
  }
  displayAmount.value = val;
});

// 计算属性
const categoryColumns = computed(() =>
  categories.value.map((c) => ({ text: c.name, value: c.id, ...c }))
);

const canSubmit = computed(() => {
  return displayAmount.value && Number(displayAmount.value) > 0;
});

// 加载分类数据
const loadCategories = async () => {
  try {
    categories.value = await apiGetCategories(type.value);
  } catch (e) {
    showToast("加载分类失败");
  }
};

// 格式化日期
const formatDate = (date) => {
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

// 类型切换
const onTypeChange = () => {
  selectedCategory.value = null;
  loadCategories();
};

// 分类选择
const onCategoryConfirm = ({ selectedOptions }) => {
  selectedCategory.value = selectedOptions[0];
  showCategoryPicker.value = false;
};

// 日期选择
const onDateConfirm = (date) => {
  selectedDate.value = date;
  showDatePicker.value = false;
};

// 提交
const handleSubmit = async () => {
  if (!canSubmit.value) {
    showFailToast("请输入金额");
    return;
  }

  submitting.value = true;
  try {
    const data = {
      type: type.value,
      amount: Number(displayAmount.value),
      categoryId: selectedCategory.value?.id,
      date: formatDate(selectedDate.value),
      remark: remark.value.trim(),
    };

    await apiSubmitBill(data);
    showSuccessToast("提交成功");

    // 重置表单
    displayAmount.value = "";
    selectedCategory.value = null;
    selectedDate.value = new Date();
    remark.value = "";
  } catch (e) {
    showFailToast("提交失败");
  } finally {
    submitting.value = false;
  }
};

// 初始化
onMounted(() => {
  loadCategories();
});
</script>

<style scoped>
.page-finance-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 100px;
}

.amount-card {
  margin: 0;
  padding: 40px 20px;
  background: #fff;
  text-align: center;
  border-bottom: 1px solid #eee;
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
