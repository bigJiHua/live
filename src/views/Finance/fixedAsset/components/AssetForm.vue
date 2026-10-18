<template>
  <van-popup
    :show="modelValue"
    position="bottom"
    round
    :style="{ minHeight: '30vh' }"
    @update:show="emit('update:modelValue', $event)"
  >
    <div class="asset-form-placeholder">
      <div class="placeholder-header">
        <span>{{ isEditing ? "编辑资产" : "新增资产" }}</span>
        <van-icon name="cross" @click="close" />
      </div>
      <van-empty image="error" description="资产表单功能建设中，敬请期待" />
    </div>
  </van-popup>
</template>

<script setup>
/**
 * 固定资产新增/编辑表单（占位组件，2026-10-09 补建）
 *
 * 背景：BUILD-1 —— 本文件在仓库中缺失（components/ 目录为空），导致
 * `npm run build` 因 UNRESOLVED_IMPORT 失败。父列表 `../List.vue:124-128`
 * 的使用契约：`v-model`（弹窗显隐）+ `:asset`（编辑目标，null=新增）+
 * `@success`（保存成功后父级刷新列表）。这里完整满足契约但不含业务逻辑。
 *
 * 后续实现提示：字段参考 List.vue 卡片展示（info/tag/buy_price/status/img_url）；
 * 提交接口走 `@/utils/api/fixedAsset`，成功后 emit('success')。
 */
import { computed } from "vue";

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 编辑目标；null = 新增 */
  asset: { type: Object, default: null },
});

const emit = defineEmits(["update:modelValue", "success"]);

const isEditing = computed(() => !!props.asset);

function close() {
  emit("update:modelValue", false);
}
</script>

<style scoped>
.asset-form-placeholder {
  padding: 16px;
}

.placeholder-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  padding-bottom: 8px;
}
</style>
