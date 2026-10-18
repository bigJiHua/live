<template>
  <div class="recycle-bin-panel">
    <van-empty image="search" description="回收站功能建设中，敬请期待" />
  </div>
</template>

<script setup>
/**
 * 固定资产回收站（占位组件，2026-10-09 补建）
 *
 * 背景：BUILD-1 —— 本文件在仓库中缺失（components/ 目录为空），导致
 * `npm run build` 因 UNRESOLVED_IMPORT 失败。父壳 `../RecycleBin.vue`
 * 通过 ref 调用 `loadList()`，故这里必须暴露同名方法（空实现）。
 *
 * 后续实现提示：数据接口可参考同目录 List.vue 的 `@/utils/api/fixedAsset`；
 * 建议后端先补「已归档资产列表 + 恢复/彻底删除」两个接口再接 UI。
 */

// 父壳 onMounted 会调用；占位阶段无列表可加载，保持空实现以满足接口契约
function loadList() {}

defineExpose({ loadList });
</script>

<style scoped>
.recycle-bin-panel {
  padding: 40vh 16px 0;
}
</style>
