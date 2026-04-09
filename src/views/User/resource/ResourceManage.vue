<template>
  <div class="page-resource-manage">
    <div class="manage-content">
      <div class="section-title">资源类型</div>
      <van-cell-group inset class="app-card">
        <van-cell
          title="动态图片"
          label="发布的动态图片"
          icon="photo-o"
          is-link
          center
          @click="goToResource('post')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
        <van-cell
          title="资产图片"
          label="资产相关的图片"
          icon="coupon-o"
          is-link
          center
          @click="goToResource('product')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
        <van-cell
          title="银行 Icon"
          label="银行图标资源"
          icon="shop-o"
          is-link
          center
          @click="goToResource('bank')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
        <van-cell
          title="其他资源"
          label="其他类型的文件"
          icon="folder-o"
          is-link
          center
          @click="goToResource('other')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
      </van-cell-group>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

const goToResource = (busType) => {
  router.push({
    path: "/user/resource-list",
    query: { type: busType },
  });
};
</script>

<style scoped>
.page-resource-manage {
  min-height: 100vh;
  background: #f7f8fa;
}

.manage-content {
  padding: 16px;
  padding-top: 20px;
}

.section-title {
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
  margin-bottom: 8px;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}
</style>
