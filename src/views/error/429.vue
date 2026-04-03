<template>
  <div class="error-page">
    <div class="warning-box">
      <div class="warning-text">warning！请勿爆破！</div>
    </div>
  </div>
</template>

<script setup></script>

<style scoped>
/* 整个页面背景闪烁 */
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ff0000;
  animation: bgFlash 0.5s infinite; /* 背景闪 */
}

.warning-box {
  text-align: center;
  padding: 40px;
  border: 4px solid #f7ff02;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.3);
  margin: 10px;
}

.warning-text {
  font-size: 48px;
  font-weight: bold;
  color: #ffffff;
  text-shadow: 0 0 20px #ff3b30;
  letter-spacing: 4px;
}

/* 背景闪烁动画 */
@keyframes bgFlash {
  0%,
  100% {
    background: #ff0000;
  }
  50% {
    background: #b10e0e;
  }
}
</style>
