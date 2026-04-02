<template>
  <div class="page-resource-manage">
    <div class="manage-content">
      <div class="section-title">资源类型</div>
      <van-cell-group inset class="app-card">
        <van-cell
          title="头像资源"
          label="我的头像图片"
          icon="user-o"
          is-link
          center
          @click="goToResource('avatar')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
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
          title="评论附件"
          label="评论中的图片附件"
          icon="comment-o"
          is-link
          center
          @click="goToResource('comment')"
        >
          <template #right-icon>
            <van-icon name="arrow" color="#969799" />
          </template>
        </van-cell>
        <van-cell
          title="商品/资产图片"
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
          title="Banner 图片"
          label="横幅图片资源"
          icon="cluster-o"
          is-link
          center
          @click="goToResource('banner')"
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
import { BusType } from "@/utils/api/upload";

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
