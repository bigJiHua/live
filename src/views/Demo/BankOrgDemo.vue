<template>
  <div class="bank-org-demo">
    <div class="demo-header">
      <h2>卡组织毛玻璃纯色 Icon</h2>
      <p class="hint">6 个常见卡组织 · backdrop-filter blur · 半透明叠加</p>
    </div>

    <div class="org-grid">
      <div class="org-card" v-for="o in orgs" :key="'g-' + o.key">
        <div class="org-label">{{ o.label }}</div>
        <CardOrgIcon :org="o.key" :filled="false" />
      </div>
    </div>

    <div class="demo-section">
      <h3>在卡片上的实际效果（毛玻璃叠加）</h3>
      <div class="card-stage">
        <div class="mock-card">
          <div class="mock-card-bg"></div>
          <div class="mock-card-content">
            <div class="mock-bank">中国工商银行</div>
            <div class="mock-no">**** **** **** 1234</div>
          </div>
          <div class="mock-card-orgs">
            <CardOrgIcon org="unionpay" small />
            <CardOrgIcon org="visa" small />
            <CardOrgIcon org="jcb" small />
          </div>
        </div>
      </div>
    </div>

    <div class="demo-section filled-section">
      <h3>填充颜色版本（官方品牌色）</h3>
      <div class="org-grid">
        <div class="org-card" v-for="o in orgs" :key="'f-' + o.key">
          <div class="org-label">{{ o.label }}</div>
          <CardOrgIcon :org="o.key" :filled="true" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import CardOrgIcon from "@/components/BankCard/org/CardOrgIcon.vue";

const orgs = [
  { key: "unionpay", label: "银联 UnionPay" },
  { key: "mastercard", label: "万事达 Mastercard" },
  { key: "visa", label: "Visa" },
  { key: "amex", label: "运通 AmEx" },
  { key: "diners", label: "大莱 Diners" },
  { key: "jcb", label: "JCB" },
];
</script>

<style scoped>
.bank-org-demo {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  padding: 32px 20px 80px;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.demo-header {
  text-align: center;
  margin-bottom: 32px;
}
.demo-header h2 {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 8px;
}
.demo-header .hint {
  font-size: 13px;
  opacity: 0.7;
  margin: 0;
}

.org-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  max-width: 600px;
  margin: 0 auto 40px;
}

.org-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.org-label {
  font-size: 13px;
  opacity: 0.8;
  font-weight: 500;
}

.demo-section {
  max-width: 600px;
  margin: 0 auto;
}
.demo-section h3 {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  margin: 0 0 16px;
  opacity: 0.9;
}
.filled-section {
  margin-top: 40px;
}
.filled-section .org-grid {
  margin-bottom: 0;
}

.card-stage {
  display: flex;
  justify-content: center;
}
.mock-card {
  position: relative;
  width: 320px;
  height: 200px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
}
.mock-card-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #4a90e2 0%, #357abd 50%, #1e5a99 100%);
}
.mock-card-content {
  position: absolute;
  top: 24px;
  left: 24px;
  right: 24px;
  color: #fff;
  z-index: 2;
}
.mock-bank {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 60px;
}
.mock-no {
  font-size: 18px;
  letter-spacing: 3px;
  font-weight: 600;
}
.mock-card-orgs {
  position: absolute;
  right: 20px;
  bottom: 20px;
  display: flex;
  gap: 6px;
  z-index: 3;
}
</style>
