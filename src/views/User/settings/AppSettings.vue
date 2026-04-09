<template>
  <div class="page-app-settings">
    <div class="section-title">分类设置</div>
    <van-cell-group inset class="app-card">
      <van-cell
        title="收支分类"
        label="管理支出/收入的分类"
        icon="orders-o"
        is-link
        @click="$router.push('/user/category-manage')"
      />
      <van-cell
        title="银行分类"
        label="管理银行卡所属银行分类"
        icon="card"
        is-link
        @click="$router.push('/user/bank-category-manage')"
      />
    </van-cell-group>

    <!-- 更多设置入口可在此添加 -->
    <div class="section-title">其他设置</div>
    <van-cell-group inset class="app-card">
      <van-cell title="主题设置" label="即将上线" icon="paint-o" is-link disabled />
      <van-cell title="通知设置" label="即将上线" icon="bell-o" is-link disabled />
    </van-cell-group>
  </div>
</template>

<script setup>
</script>

<style scoped>
.page-app-settings {
  min-height: 100vh;
  background: #f7f8fa;
  padding-top: 12px;
}

.section-title {
  padding: 20px 20px 10px;
  font-size: 13px;
  color: #969799;
  font-weight: 500;
}

.app-card {
  border-radius: 12px;
  overflow: hidden;
}
</style>
