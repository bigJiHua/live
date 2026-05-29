<template>
  <div class="page-fund-register">
    <div class="top-bar">
      <div class="tb-title">基金列表</div>
      <van-button size="small" type="primary" round @click="openEditor(null)">
        <template #icon><van-icon name="plus" /></template>
        新增基金
      </van-button>
    </div>

    <div class="list" v-if="!loading">
      <div v-for="f in fundList" :key="f.id" class="fund-item">
        <div class="fi-top">
          <span class="fi-name">{{ f.fund_name }}</span>
          <div class="fi-actions">
            <van-icon name="edit" class="fi-btn" @click="openEditor(f)" />
            <van-icon
              name="delete"
              class="fi-btn danger"
              @click="handleDelete(f)"
            />
          </div>
        </div>
        <div class="fi-body">
          <span>份额 {{ f.share }}</span>
          <span class="fi-profit" :class="Number(f.net_value || 0) >= 0 ? 'success' : 'danger'">收益 {{ Number(f.net_value).toFixed(2) }}</span>
          <span>市值 {{ formatAmount(f.market_val) }}</span>
        </div>
        <div class="fi-meta">
          <span v-if="f.fund_company">{{ f.fund_company }}</span>
          <span>购入 {{ f.buy_date }}</span>
        </div>
      </div>
      <van-empty v-if="fundList.length === 0" description="暂无基金数据" />
    </div>

    <van-popup
      v-model:show="showEditor"
      position="bottom"
      round
      :style="{ height: '80%' }"
    >
      <div class="editor">
        <div class="editor-title">
          {{ editingItem ? "编辑基金" : "新增基金" }}
        </div>
        <van-form @submit="handleSave" autocomplete="off">
          <input type="text" style="display: none" autocomplete="off" />
          <van-field
            v-model="form.fundName"
            label="基金名称"
            placeholder="如：易方达蓝筹精选"
            :rules="[{ required: true, message: '请输入基金名称' }]"
            :input-attr="{ autocomplete: 'off' }"
          />
          <van-field
            v-model="form.buyDate"
            is-link
            readonly
            label="购入日期"
            placeholder="请选择日期"
            :rules="[{ required: true, message: '请选择购入日期' }]"
            @click="showDatePicker = true"
            :input-attr="{ autocomplete: 'off' }"
          />
          <van-field
            v-model="form.share"
            label="持有份额"
            placeholder="0.00"
            type="number"
            :rules="[
              { required: true, message: '请输入持有份额' },
              { validator: (v) => Number(v) > 0, message: '份额必须大于0' },
            ]"
            :input-attr="{ autocomplete: 'off' }"
          />
          <van-field
            v-model="form.invest"
            label="初始本金"
            placeholder="0.00"
            type="number"
            :rules="[
              { required: true, message: '请输入初始本金' },
              { validator: (v) => Number(v) > 0, message: '本金必须大于0' },
            ]"
            :input-attr="{ autocomplete: 'off' }"
          />
          <van-cell
            class="optional-toggle"
            title="补充信息"
            :value="showOptionalInfo ? '收起' : '选填'"
            is-link
            :arrow-direction="showOptionalInfo ? 'up' : 'down'"
            @click="showOptionalInfo = !showOptionalInfo"
          />
          <template v-if="showOptionalInfo">
            <van-field
              v-model="form.fundCompany"
              label="基金公司"
              placeholder="选填，如：易方达基金"
              :input-attr="{ autocomplete: 'off' }"
            />
            <van-field
              v-model="form.sellOrg"
              label="销售机构"
              placeholder="选填，如：支付宝"
              :input-attr="{ autocomplete: 'off' }"
            />
            <van-field
              v-model="form.fundAccount"
              label="基金账号"
              placeholder="选填，尾号6位"
              maxlength="6"
              :input-attr="{ autocomplete: 'off' }"
            />
          </template>
          <div class="editor-actions">
            <van-button round block type="primary" native-type="submit">{{
              editingItem ? "保存修改" : "确认添加"
            }}</van-button>
            <van-button
              round
              block
              plain
              style="margin-top: 8px"
              @click="showEditor = false"
              >取消</van-button
            >
          </div>
        </van-form>
        <van-popup
          v-model:show="showDatePicker"
          position="bottom"
          round
          teleport="body"
        >
          <van-date-picker
            title="选择购入日期"
            :model-value="datePickerValue"
            @confirm="onDateConfirm"
            @cancel="showDatePicker = false"
          />
        </van-popup>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import {
  getFundList,
  createFund,
  updateFund,
  deleteFund,
} from "@/utils/api/fund";
import { showToast, showConfirmDialog } from "vant";

const formatAmount = (v) => {
  if (!v && v !== 0) return "0.00";
  const n = Number(v);
  return n >= 10000 ? (n / 10000).toFixed(2) + "万" : n.toFixed(2);
};

const loading = ref(true);
const fundList = ref([]);
const showEditor = ref(false);
const showDatePicker = ref(false);
const showOptionalInfo = ref(false);
const editingItem = ref(null);

const datePickerValue = computed(() => {
  if (form.buyDate) {
    const parts = form.buyDate.split("-");
    if (parts.length === 3) return parts;
  }
  const now = new Date();
  return [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ];
});

const form = reactive({
  fundName: "",
  fundCompany: "",
  sellOrg: "",
  fundAccount: "",
  share: "",
  netValue: "0",
  invest: "",
  marketVal: "",
  buyDate: "",
  rate: "",
});

const resetForm = () => {
  Object.assign(form, {
    fundName: "",
    fundCompany: "",
    sellOrg: "",
    fundAccount: "",
    share: "",
    netValue: "0",
    invest: "",
    marketVal: "",
    buyDate: "",
    rate: "",
  });
};

const onDateConfirm = ({ selectedValues }) => {
  form.buyDate = selectedValues.join("-");
  showDatePicker.value = false;
};

const openEditor = (item) => {
  editingItem.value = item;
  showOptionalInfo.value = !!item;
  resetForm();
  if (item) {
    Object.assign(form, {
      fundName: item.fund_name,
      fundCompany: item.fund_company,
      sellOrg: item.sell_org,
      fundAccount: item.fund_account,
      share: item.share,
      netValue: item.base_net_value ?? item.net_value,
      invest: item.base_invest ?? item.invest,
      marketVal: item.base_market_val ?? item.base_invest ?? item.invest,
      buyDate: item.buy_date,
      rate: item.rate,
    });
  }
  showEditor.value = true;
};

const handleSave = async () => {
  try {
    const invest = parseFloat(form.invest) || 0
    const payload = {
      ...form,
      fundName: form.fundName.trim(),
      fundCompany: form.fundCompany.trim(),
      sellOrg: form.sellOrg.trim(),
      fundAccount: form.fundAccount.trim(),
      netValue: "0",
      marketVal: invest.toFixed(2),
      rate: "0.00%",
    }

    if (editingItem.value) {
      await updateFund(editingItem.value.id, payload);
      showToast("修改成功");
    } else {
      await createFund(payload);
      showToast("添加成功");
    }
    showEditor.value = false;
    await loadList();
  } catch (e) {
    showToast(e?.message || (editingItem.value ? "修改失败" : "添加失败"));
  }
};

const handleDelete = (item) => {
  showConfirmDialog({
    title: "确认删除",
    message: `确定删除「${item.fund_name}」？`,
  })
    .then(async () => {
      try {
        await deleteFund(item.id);
        showToast("删除成功");
        await loadList();
      } catch (e) {
        showToast("删除失败");
      }
    })
    .catch(() => {});
};

const loadList = async () => {
  try {
    const res = await getFundList();
    fundList.value = res.data?.list || [];
  } catch (e) {
    showToast("加载失败");
  } finally {
    loading.value = false;
  }
};

onMounted(loadList);
</script>

<style scoped>
.page-fund-register {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 12px 16px 30px;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.tb-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fund-item {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
}

.fi-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.fi-name {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}

.fi-actions {
  display: flex;
  gap: 14px;
}

.fi-btn {
  font-size: 18px;
  color: #1989fa;
  cursor: pointer;
}

.fi-btn.danger {
  color: #ee0a24;
}
.fi-profit.success {
  color: #ee0a24;
}
.fi-profit.danger {
  color: #07c160;
}

.fi-body {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #323233;
  margin-bottom: 4px;
}

.fi-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #969799;
}

.editor {
  padding: 20px 16px 30px;
}

.editor-title {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 16px;
}

.optional-toggle {
  margin-top: 6px;
}

.editor-actions {
  margin-top: 20px;
}
</style>
