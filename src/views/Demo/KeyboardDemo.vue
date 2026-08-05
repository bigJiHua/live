<template>
  <div class="kb-demo" :style="activeVars">
    <div class="kb-hero">
      <h2 class="kb-title">键盘组件 Demo</h2>
      <p class="kb-sub">主题适配 · 点击下方输入框唤起键盘（底部弹起）</p>
    </div>

    <!-- 主题切换器 -->
    <div class="theme-bar">
      <div class="theme-swatches">
        <span
          v-for="t in themes"
          :key="t.key"
          class="theme-swatch"
          :class="{ on: t.key === selectedKey }"
          :style="{ background: t.preview }"
          :title="t.name"
          @click="selectedKey = t.key"
        />
      </div>
      <span class="theme-name">{{ currentTheme.name }}</span>
    </div>

    <!-- 英文登录键盘 -->
    <section class="kb-card highlight">
      <div class="kb-card-head">
        <div class="kb-card-info">
          <h3>英文登录键盘</h3>
          <span class="kb-desc">26键字母 · 大小写切换 · 数字符号 · 无空格</span>
        </div>
        <span class="kb-tag accent">Login</span>
      </div>
      <div class="kb-input-wrap">
        <input
          class="kb-input"
          :value="textValue"
          readonly
          placeholder="点击唤起英文登录键盘"
          @click="activeKb = true"
        />
        <button v-if="textValue" class="kb-clear" @click="textValue = ''">✕</button>
      </div>
    </section>

    <p class="kb-foot">切换顶部主题色板，观察键盘随主题实时配色变化</p>

    <!-- 底部弹起键盘 -->
    <transition name="kb-fade">
      <div v-if="activeKb" class="kb-overlay" @click="activeKb = false"></div>
    </transition>
    <transition name="kb-up">
      <div v-if="activeKb" class="kb-sheet">
        <div class="kb-sheet-bar">
          <span class="kb-sheet-handle"></span>
          <span class="kb-sheet-title">{{ kbTitle }}</span>
          <button class="kb-sheet-done" @click="activeKb = false">完成</button>
        </div>
        <FullKeyboard v-if="activeKb" v-model="textValue" :theme-key="selectedKey" />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { THEME_PRESETS as themes, buildVars } from "@/composables/useUiTheme";
import FullKeyboard from "@/components/KeyBoard/FullKeyboard.vue";

const selectedKey = ref(themes[0].key);
const textValue = ref("");

const activeKb = ref(false);
const kbTitle = "英文登录键盘";

const currentTheme = computed(
  () => themes.find((t) => t.key === selectedKey.value) || themes[0]
);
const activeVars = computed(() => buildVars(currentTheme.value));
</script>

<style scoped>
.kb-demo {
  min-height: 100vh;
  background: var(--theme-bg-primary, #f7f8fa);
  padding: 20px 16px 60px;
}

/* ── Hero 区 ── */
.kb-hero {
  margin-bottom: 16px;
}
.kb-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--theme-text-primary, #323233);
  letter-spacing: -0.01em;
}
.kb-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--theme-text-tertiary, #969799);
  line-height: 1.5;
}

/* ── 主题切换器 ── */
.theme-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--theme-bg-secondary, #fff);
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.theme-swatches {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.theme-swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
  transition: transform 0.12s ease, box-shadow 0.15s ease;
}
.theme-swatch:active { transform: scale(0.85); }
.theme-swatch.on {
  outline: 2.5px solid var(--theme-primary, #3a66e0);
  outline-offset: 2px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}
.theme-name {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-secondary, #646566);
}

/* ── 键盘入口卡片 ── */
.kb-card {
  background: var(--theme-bg-secondary, #fff);
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 14px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.15s ease;
}
.kb-card.highlight {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}
.kb-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 8px;
}
.kb-card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kb-card-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--theme-text-primary, #323233);
}
.kb-desc {
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
}
.kb-tag {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-primary, #3a66e0);
  background: var(--theme-primary-light, #eef3ff);
  padding: 3px 10px;
  border-radius: 10px;
  white-space: nowrap;
}
.kb-tag.accent {
  background: var(--theme-primary, #3a66e0);
  color: #fff;
}

/* ── 输入框（点击唤起键盘）── */
.kb-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.kb-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 52px;
  background: var(--theme-bg-primary, #f7f8fa);
  border: 1.5px solid var(--theme-border, #ebedf0);
  border-radius: 12px;
  padding: 12px 40px 12px 14px;
  font-size: 18px;
  font-weight: 500;
  color: var(--theme-text-primary, #323233);
  outline: none;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.kb-input::placeholder {
  color: var(--theme-text-placeholder, #c8c9cc);
  font-weight: 400;
  font-size: 14px;
}
.kb-input:focus,
.kb-input:active {
  border-color: var(--theme-primary, #3a66e0);
  box-shadow: 0 0 0 3px var(--theme-primary-light, #eef3ff);
}
.kb-clear {
  position: absolute;
  right: 8px;
  width: 28px;
  height: 28px;
  border: none;
  background: var(--theme-bg-tertiary, #f2f3f5);
  border-radius: 50%;
  font-size: 13px;
  color: var(--theme-text-tertiary, #969799);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.1s ease;
}
.kb-clear:active {
  background: var(--theme-border, #ebedf0);
}

.kb-foot {
  margin: 4px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--theme-text-tertiary, #969799);
}

/* ── 底部弹起键盘 ── */
.kb-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
}
.kb-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2001;
  background: var(--theme-bg-secondary, #fff);
  border-radius: 20px 20px 0 0;
  padding: 8px 10px calc(20px + env(safe-area-inset-bottom));
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.14);
}
.kb-sheet-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 12px;
  position: relative;
}
.kb-sheet-handle {
  position: absolute;
  top: -2px;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: var(--theme-border, #ebedf0);
}
.kb-sheet-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary, #323233);
}
.kb-sheet-done {
  border: none;
  background: var(--theme-primary, #3a66e0);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border-radius: 14px;
  padding: 5px 14px;
  cursor: pointer;
  transition: opacity 0.1s ease;
}
.kb-sheet-done:active { opacity: 0.7; }

/* ── 底部滑入动画 ── */
.kb-up-enter-active,
.kb-up-leave-active {
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0.4, 1);
}
.kb-up-enter-from,
.kb-up-leave-to {
  transform: translateY(100%);
}
.kb-fade-enter-active,
.kb-fade-leave-active {
  transition: opacity 0.25s ease;
}
.kb-fade-enter-from,
.kb-fade-leave-to {
  opacity: 0;
}
</style>
