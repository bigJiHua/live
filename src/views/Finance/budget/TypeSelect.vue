<template>
  <div class="page-budget-type-select">
    <div class="type-grid">
      <div class="type-card" @click="selectType('买')">
        <div class="type-icon shopping">
          <van-icon name="shopping-cart-o" />
        </div>
        <div class="type-title">购物预算</div>
        <div class="type-desc">电子产品、日用品等购物计划</div>
      </div>

      <div class="type-card" @click="selectType('行')">
        <div class="type-icon travel">
          <van-icon name="location-o" />
        </div>
        <div class="type-title">出行预算</div>
        <div class="type-desc">旅游、出差等交通住宿费用</div>
      </div>
    </div>

    <div class="type-card eat-card" @click="selectType('吃')">
      <div class="type-icon eat">
        <van-icon name="fire-o" />
      </div>
      <div class="type-content">
        <div class="type-title">餐饮预算</div>
        <div class="type-desc">日常餐饮开销控制</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

const selectType = (type) => {
  if (type === "买") {
    router.push("/finance/budget/shopping");
  } else if (type === "行") {
    router.push("/finance/budget/travel");
  } else if (type === "吃") {
    router.push("/finance/budget/eat");
  }
};
</script>

<style scoped>
.page-budget-type-select {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 16px;
}

.type-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.type-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px 16px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.type-card:active {
  transform: scale(0.98);
}

.type-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  font-size: 28px;
}

.type-icon.shopping {
  background: linear-gradient(135deg, #ff6034, #ee0a24);
  color: #fff;
}

.type-icon.travel {
  background: linear-gradient(135deg, #1989fa, #1976d2);
  color: #fff;
}

.type-icon.eat {
  background: linear-gradient(135deg, #ff976a, #ee0a24);
  color: #fff;
}

.type-title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 4px;
}

.type-desc {
  font-size: 12px;
  color: #969799;
  line-height: 1.4;
}

.eat-card {
  display: flex;
  align-items: center;
  text-align: left;
  padding: 20px;
}

.eat-card .type-icon {
  margin: 0 16px 0 0;
  width: 48px;
  height: 48px;
  font-size: 24px;
}

.eat-card .type-content {
  flex: 1;
}
</style>
