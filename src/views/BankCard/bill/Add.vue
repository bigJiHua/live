<template>
  <div class="page-bill-add">
    <van-form @submit="onSubmit" ref="formRef">
      <!-- 关联卡片 -->
      <div class="form-section">
        <div class="section-title">
          关联卡片 <span class="required-hint">*必填</span>
        </div>
        <van-cell-group inset>
          <van-field
            v-model="selectedCardName"
            name="cardId"
            label="卡片"
            placeholder="请选择信用卡"
            is-link
            readonly
            @click="showCardPicker = true"
            :rules="[{ required: true, message: '请选择卡片' }]"
          />
        </van-cell-group>
      </div>

      <!-- 额度信息 -->
      <div class="form-section">
        <div class="section-title">
          额度信息 <span class="required-hint">*必填</span>
        </div>
        <van-cell-group inset>
          <van-field
            v-model="formData.creditLimit"
            name="creditLimit"
            label="信用额度"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('creditLimit')"
            :rules="[{ required: true, message: '请输入信用额度' }]"
          >
            <template #button>元</template>
          </van-field>
          <van-field
            v-model="formData.tempLimit"
            name="tempLimit"
            label="临时额度"
            placeholder="如无请留空"
            readonly
            clickable
            @click="openKeyboard('tempLimit')"
          >
            <template #button>元</template>
          </van-field>
        </van-cell-group>
      </div>

      <!-- 积分设置 -->
      <div class="form-section">
        <div class="section-title">积分设置</div>
        <van-cell-group inset>
          <van-field
            v-model="formData.pointsRate"
            name="pointsRate"
            label="积分倍率"
            placeholder="请输入"
            readonly
            clickable
            @click="openKeyboard('pointsRate')"
          >
            <template #suffix>倍</template>
          </van-field>
        </van-cell-group>
        <div class="tip-box">
          <van-icon name="info-o" />
          <span
            >积分 = 消费金额 × 积分倍率<br />积分到期时间由系统自动计算</span
          >
        </div>
      </div>

      <!-- 提醒设置 -->
      <div class="form-section">
        <div class="section-title">提醒设置</div>
        <van-cell-group inset>
          <van-field name="remindSwitch" label="还款提醒">
            <template #input>
              <van-switch v-model="formData.remindSwitch" size="20" />
            </template>
          </van-field>
          <van-field
            v-model="formData.remindDays"
            name="remindDays"
            label="提前提醒"
            placeholder="请输入天数"
            readonly
            clickable
            :disabled="!formData.remindSwitch"
            @click="formData.remindSwitch && openKeyboard('remindDays')"
          >
            <template #suffix>天</template>
          </van-field>
        </van-cell-group>
      </div>

      <!-- 自动计算说明 -->
      <div class="tip-box tip-highlight">
        <van-icon name="info-o" />
        <span>
          以下信息将由系统自动计算生成：<br />
          • 可用额度 = 信用额度 - 已用额度<br />
          • 本期账单 = 账单周期内消费总额<br />
          • 最低还款 = 本期账单 × 10%<br />
          • 积分 = 消费金额 × 积分倍率
        </span>
      </div>

      <div class="submit-btn-wrap">
        <van-button
          type="primary"
          block
          round
          native-type="submit"
          :loading="loading"
          :disabled="loading"
        >
          创建账单
        </van-button>
      </div>
    </van-form>

    <!-- 卡片选择 -->
    <van-popup v-model:show="showCardPicker" position="bottom">
      <van-picker
        :columns="cardColumns"
        @confirm="onCardConfirm"
        @cancel="showCardPicker = false"
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
import { ref, reactive, computed, onMounted } from "vue";
import {
  showToast,
  showLoadingToast,
  closeToast,
  showConfirmDialog,
} from "vant";
import { useRouter, useRoute } from "vue-router";
import { createBill, getCardList } from "@/utils/api/card";

const router = useRouter();
const route = useRoute();

const formRef = ref(null);
const loading = ref(false);
const cardList = ref([]);
const selectedCardName = ref("");

const formData = reactive({
  cardId: "",
  creditLimit: "",
  tempLimit: "",
  pointsRate: "1",
  remindSwitch: true,
  remindDays: 3,
});

// 获取卡片类型文本
const getCardTypeText = (cardType) => {
  const map = {
    credit: "信用卡",
    debit: "借记卡",
    virtual_cash: "现金",
    virtual_balance: "余额",
  };
  return map[cardType] || cardType || "卡片";
};

// 卡片选择列
const cardColumns = computed(() => {
  return cardList.value.map((card) => ({
    text: `${getCardTypeText(card.card_type)} ${card.alias || card.bank_name || ""} **** ${
      card.last4_no || card.last4No || "****"
    }`.replace(/\s+/g, " ").trim(),
    value: card.id,
  }));
});

// 加载卡片列表
const loadCardList = async () => {
  try {
    const res = await getCardList({ cardType: "credit" });
    cardList.value = res.data || res || [];

    // 如果有预选的卡片ID
    const cardId = route.query.cardId;
    if (cardId) {
      formData.cardId = cardId;
      const card = cardList.value.find((c) => c.id === cardId);
      if (card) {
        selectedCardName.value = `${getCardTypeText(card.card_type)} ${
          card.alias || card.bank_name || "卡片"
        } **** ${card.last4_no || card.last4No || "****"}`.replace(/\s+/g, " ").trim();
      }
    }
  } catch (error) {
    // 忽略错误
  }
};

// 卡片选择确认
const onCardConfirm = ({ selectedOptions }) => {
  formData.cardId = selectedOptions[0].value;
  selectedCardName.value = selectedOptions[0].text;
  showCardPicker.value = false;
};

// 弹出状态
const showCardPicker = ref(false);

// 数字键盘控制
const showKeyboard = ref(false);
const currentField = ref("");
const keyboardMaxlength = ref(10);

// 字段配置
const fieldConfig = {
  creditLimit: { maxlength: 10 },
  tempLimit: { maxlength: 10 },
  pointsRate: { maxlength: 3 },
  remindDays: { maxlength: 2 },
};

// 打开数字键盘
const openKeyboard = (field) => {
  currentField.value = field;
  keyboardMaxlength.value = fieldConfig[field]?.maxlength || 10;
  showKeyboard.value = true;
};

// 数字键盘输入
const onKeyboardInput = (value) => {
  const field = currentField.value;
  if (!field) return;
  let newValue = formData[field] + value;
  if (newValue.length > keyboardMaxlength.value) {
    newValue = newValue.slice(0, keyboardMaxlength.value);
  }
  formData[field] = newValue;
};

// 数字键盘删除
const onKeyboardDelete = () => {
  const field = currentField.value;
  if (!field) return;
  formData[field] = formData[field].slice(0, -1);
};

// 数字键盘关闭
const onKeyboardClose = () => {
  showKeyboard.value = false;
};

// 关闭键盘（用于失焦时）
const closeKeyboard = () => {
  showKeyboard.value = false;
};

// 验证信用额度
const validateCreditLimit = () => {
  const limit = Number(formData.creditLimit);
  if (isNaN(limit) || limit < 0) {
    showToast("信用额度必须为有效数字");
    formData.creditLimit = "";
    return false;
  }
  return true;
};

// 返回
const onClickLeft = () => {
  router.back();
};

// 提交
const onSubmit = async () => {
  try {
    // 验证信用额度
    if (!validateCreditLimit()) {
      return;
    }

    // 如果信用额度为0，弹出确认框
    if (Number(formData.creditLimit) === 0) {
      try {
        await showConfirmDialog({
          title: "确认额度",
          message: "信用额度为 0，确定要继续创建吗？",
          confirmButtonColor: "#ee0a24",
        });
      } catch {
        // 用户取消
        return;
      }
    }

    loading.value = true;
    showLoadingToast({ message: "创建中...", forbidClick: true });

    // 根据新API格式构建数据
    const submitData = {
      cardId: formData.cardId,
      creditLimit: Number(formData.creditLimit),
      tempLimit: formData.tempLimit ? Number(formData.tempLimit) : 0,
      pointsRate: formData.pointsRate ? Number(formData.pointsRate) : 1,
      remindSwitch: formData.remindSwitch,
      remindDays: formData.remindDays ? Number(formData.remindDays) : 3,
    };
    await createBill(submitData);

    closeToast();
    showToast({ message: "创建成功", onClose: () => router.back() });
  } catch (error) {
    closeToast();
    showToast(error.message || "创建失败");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadCardList();
});
</script>

<style scoped>
.page-bill-add {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.form-section {
  margin-top: 12px;
}

.section-title {
  font-size: 14px;
  color: #969799;
  padding: 12px 16px 8px;
}

.required-hint {
  font-size: 12px;
  color: #ee0a24;
  margin-left: 8px;
}

.tip-box {
  display: flex;
  align-items: flex-start;
  background: #f7f8fa;
  margin: 12px 16px;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  color: #646566;
  line-height: 1.6;
}

.tip-box .van-icon {
  margin-right: 8px;
  margin-top: 2px;
  color: #1989fa;
}

.tip-box.tip-highlight {
  background: #fff7e6;
  border: 1px solid #ffe1b3;
}

.submit-btn-wrap {
  margin: 24px 16px;
}
</style>
