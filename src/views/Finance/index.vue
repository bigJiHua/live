<template>
  <div class="page-finance">
    <div class="app-card finance-header">
      <div class="month-selector" @click="showMonthPicker = true">
        <span class="num-font">2026</span>年<span class="num-font">3</span>月 
        <van-icon name="arrow-down" />
      </div>
      
      <van-row class="total-info">
        <van-col span="12" class="info-item">
          <div class="label">本月总支出</div>
          <div class="amount text-expense num-font">3,240.60</div>
        </van-col>
        <van-col span="12" class="info-item">
          <div class="label">本月总收入</div>
          <div class="amount text-income num-font">8,500.00</div>
        </van-col>
      </van-row>

      <div class="header-footer">
        <div class="net-assets">
          <span class="label">预估净资产：</span>
          <span class="val num-font">￥125,210.00</span>
        </div>
        <van-button 
          plain
          size="small"
          type="primary" 
          class="quick-add-btn" 
          icon="plus" 
          @click="$router.push('/finance/add')"
        >
          记一笔
        </van-button>
      </div>
    </div>

    <div class="app-card menu-grid-wrapper">
      <div class="grid-section-title">财务把控中心</div>
      <van-grid :column-num="3" :border="false" clickable>
        <van-grid-item @click="goFunction('bill-list')">
          <template #icon><van-icon name="todo-list" class="grid-icon blue" /></template>
          <template #text><span class="grid-text">账单明细</span></template>
        </van-grid-item>

        <van-grid-item @click="goFunction('report')">
          <template #icon><van-icon name="chart-trending-o" class="grid-icon green" /></template>
          <template #text><span class="grid-text">阶段财报</span></template>
        </van-grid-item>

        <van-grid-item @click="goFunction('credit')">
          <template #icon><van-icon name="credit-pay" class="grid-icon orange" /></template>
          <template #text><span class="grid-text">信用卡专项</span></template>
        </van-grid-item>

        <van-grid-item @click="goFunction('assets')">
          <template #icon><van-icon name="gem" class="grid-icon purple" /></template>
          <template #text><span class="grid-text">资产结构</span></template>
        </van-grid-item>

        <van-grid-item @click="goFunction('budget')">
          <template #icon><van-icon name="balance-list" class="grid-icon cyan" /></template>
          <template #text><span class="grid-text">预算控制</span></template>
        </van-grid-item>

        <van-grid-item @click="goFunction('super-calc')" class="special-item">
          <template #icon><van-icon name="points" class="grid-icon gold" /></template>
          <template #text><span class="grid-text font-bold">超级计算</span></template>
        </van-grid-item>
      </van-grid>
    </div>

    <van-cell-group inset class="asset-cell-group">
      <van-cell title="固定资产标注" is-link value="残值计算" @click="goFunction('fixed-assets')">
        <template #icon>
          <van-icon name="shop-o" class="cell-icon" />
        </template>
      </van-cell>
      <van-cell title="工作成本核算" is-link value="薪资/日薪" @click="goFunction('work-cost')">
        <template #icon>
          <van-icon name="medal-o" class="cell-icon" />
        </template>
      </van-cell>
    </van-cell-group>

    <div class="footer-tips">
      数据已进行全站加密处理 <van-icon name="shield-check" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const showMonthPicker = ref(false)

const goFunction = (type) => {
  console.log('正在进入专项模块:', type)
  // router.push(`/finance/${type}`)
}
</script>

<style scoped>
.page-finance {
  padding: 8px 16px;
}

/* 1. 头部看板优化 */
.finance-header {
  background: white;
  padding: 20px;
  border-radius: 20px;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}
.month-selector {
  font-size: 14px;
  color: #646566;
  margin-bottom: 15px;
}
.total-info .info-item {
  text-align: left;
}
.info-item .label {
  font-size: 12px;
  color: #969799;
  margin-bottom: 4px;
}
.info-item .amount {
  font-size: 22px;
  font-weight: bold;
}

.header-footer {
  margin-top: 18px;
  padding-top: 15px;
  border-top: 1px dashed #f2f3f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.net-assets .label { font-size: 12px; color: #969799; }
.net-assets .val { font-size: 14px; color: #323233; font-weight: 500; }
.quick-add-btn { padding: 0 15px; height: 32px; border-radius: 8px; }

/* 2. 功能矩阵优化 */
.menu-grid-wrapper {
  background: white;
  padding: 16px 8px;
  border-radius: 20px;
  margin-bottom: 16px;
}
.grid-section-title {
  font-size: 14px;
  font-weight: bold;
  padding-left: 10px;
  margin-bottom: 15px;
  color: #323233;
}
.grid-icon { font-size: 26px; margin-bottom: 8px; }
.grid-text { font-size: 13px; color: #646566; }
.font-bold { font-weight: bold; color: #323233; }

/* 图标颜色方案 */
.blue { color: #1989fa; }
.green { color: #07c160; }
.orange { color: #ff976a; }
.purple { color: #7232dd; }
.cyan { color: #00bcd4; }
.gold { color: #ffb300; }

/* 特殊项目样式 */
.special-item {
  background: #fff9e6; /* 淡淡的金色背景 */
  border-radius: 12px;
}

/* 3. 单元格组 */
.asset-cell-group {
  margin: 0 -16px 20px !important;
}
.cell-icon { font-size: 20px; margin-right: 8px; color: #646566; }

/* 4. 底部提示 */
.footer-tips {
  text-align: center;
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 30px;
  padding-bottom: 20px;
}

.text-income { color: #07c160; }
.text-expense { color: #ee0a24; }
</style>