<template>
  <div class="page-recycle-bin">
    <RecycleBin ref="recycleBinRef" />
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import RecycleBin from "./components/RecycleBin.vue";

const recycleBinRef = ref(null);

onMounted(() => {
  recycleBinRef.value?.loadList();
});
</script>

<style scoped>
.page-recycle-bin {
  min-height: 100vh;
  background: #f7f8fa;
}
</style>
