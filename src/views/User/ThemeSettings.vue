<template>
  <div class="page-theme-settings">
    <!-- 预设主题 -->
    <div class="section-title">预设主题</div>
    <div class="theme-grid">
      <div
        v-for="(theme, key) in THEME_PRESETS"
        :key="key"
        class="theme-item"
        :class="{ active: currentThemeId === key }"
        @click="applyPreset(key)"
      >
        <div class="theme-preview">
          <div class="preview-color primary" :style="{ background: theme.colors.primary }"></div>
          <div class="preview-color secondary" :style="{ background: theme.colors.secondary }"></div>
          <div class="preview-color bg" :style="{ background: theme.colors.bgPrimary }"></div>
        </div>
        <div class="theme-name">{{ theme.name }}</div>
        <van-icon v-if="currentThemeId === key" name="success" class="theme-check" />
      </div>
    </div>

    <!-- 自定义主题 -->
    <div class="section-title">自定义配色</div>
    <div class="custom-theme-section">
      <van-cell-group inset>
        <van-cell title="主题色">
          <template #right-icon>
            <div class="color-picker-wrapper">
              <input
                type="color"
                v-model="customColors.primary"
                @change="applyCustom"
                class="color-input"
              />
            </div>
          </template>
        </van-cell>
        <van-cell title="辅助色">
          <template #right-icon>
            <div class="color-picker-wrapper">
              <input
                type="color"
                v-model="customColors.secondary"
                @change="applyCustom"
                class="color-input"
              />
            </div>
          </template>
        </van-cell>
        <van-cell title="成功色">
          <template #right-icon>
            <div class="color-picker-wrapper">
              <input
                type="color"
                v-model="customColors.success"
                @change="applyCustom"
                class="color-input"
              />
            </div>
          </template>
        </van-cell>
        <van-cell title="警告色">
          <template #right-icon>
            <div class="color-picker-wrapper">
              <input
                type="color"
                v-model="customColors.warning"
                @change="applyCustom"
                class="color-input"
              />
            </div>
          </template>
        </van-cell>
        <van-cell title="危险色">
          <template #right-icon>
            <div class="color-picker-wrapper">
              <input
                type="color"
                v-model="customColors.danger"
                @change="applyCustom"
                class="color-input"
              />
            </div>
          </template>
        </van-cell>
      </van-cell-group>
    </div>

    <!-- 预览区域 -->
    <div class="section-title">效果预览</div>
    <div class="preview-section">
      <div class="preview-card">
        <div class="preview-header">
          <div class="preview-title">示例卡片</div>
          <div class="preview-amount num-font text-theme">￥1,234.56</div>
        </div>
        <div class="preview-body">
          <div class="preview-row">
            <span class="preview-label">收入</span>
            <span class="preview-value text-income num-font">+8,500.00</span>
          </div>
          <div class="preview-row">
            <span class="preview-label">支出</span>
            <span class="preview-value text-expense num-font">-3,240.60</span>
          </div>
        </div>
        <div class="preview-footer">
          <van-button type="primary" size="small">按钮示例</van-button>
          <van-switch v-model="switchValue" size="20px" />
        </div>
      </div>
    </div>

    <!-- 重置按钮 -->
    <div class="reset-wrapper">
      <van-button block plain type="danger" @click="resetToDefault">
        恢复默认主题
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import {
  THEME_PRESETS,
  getCurrentTheme,
  applyThemePreset,
  applyCustomTheme,
  saveCustomTheme,
  loadTheme,
} from '@/utils/theme'

const currentThemeId = ref('default')
const switchValue = ref(true)

// 自定义颜色配置
const customColors = ref({
  primary: '#07c160',
  primaryLight: '#e6f9ed',
  secondary: '#1989fa',
  secondaryLight: '#ecf5fe',
  success: '#07c160',
  warning: '#ff976a',
  danger: '#ee0a24',
  info: '#969799',
  bgPrimary: '#f7f8fa',
  bgSecondary: '#ffffff',
  bgTertiary: '#f2f3f5',
  textPrimary: '#323233',
  textSecondary: '#646566',
  textTertiary: '#969799',
  textPlaceholder: '#c8c9cc',
  border: '#ebedf0',
  borderLight: '#f7f8fa',
})

// 初始化
onMounted(() => {
  const theme = getCurrentTheme()
  currentThemeId.value = theme.id

  // 如果是自定义主题，加载自定义颜色
  if (theme.id === 'custom') {
    Object.assign(customColors.value, theme.colors)
  }
})

// 应用预设主题
const applyPreset = (themeId) => {
  applyThemePreset(themeId)
  currentThemeId.value = themeId
  showToast('主题已应用')
}

// 应用自定义颜色
const applyCustom = () => {
  // 生成浅色变体
  customColors.value.primaryLight = hexToRgba(customColors.value.primary, 0.1)
  customColors.value.secondaryLight = hexToRgba(customColors.value.secondary, 0.1)

  applyCustomTheme(customColors.value)
  currentThemeId.value = 'custom'
}

// 重置为默认主题
const resetToDefault = () => {
  applyPreset('default')
  showToast('已恢复默认主题')
}

// 辅助函数：十六进制转 rgba
function hexToRgba(hex, alpha) {
  let r = 0,
    g = 0,
    b = 0
  if (hex.length === 4) {
    r = parseInt(hex[1] + hex[1], 16)
    g = parseInt(hex[2] + hex[2], 16)
    b = parseInt(hex[3] + hex[3], 16)
  } else if (hex.length === 7) {
    r = parseInt(hex.slice(1, 3), 16)
    g = parseInt(hex.slice(3, 5), 16)
    b = parseInt(hex.slice(5, 7), 16)
  }
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<style scoped>
.page-theme-settings {
  padding: 16px;
  min-height: 100vh;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin: 20px 0 12px;
}

/* 预设主题网格 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.theme-item {
  position: relative;
  padding: 12px;
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.3s;
}

.theme-item.active {
  border-color: var(--theme-primary);
  box-shadow: 0 0 0 2px var(--theme-primary-light);
}

.theme-item:active {
  transform: scale(0.95);
}

.theme-preview {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  height: 40px;
}

.preview-color {
  flex: 1;
  border-radius: 6px;
}

.theme-name {
  text-align: center;
  font-size: 13px;
  color: var(--theme-text-primary);
}

.theme-check {
  position: absolute;
  top: 8px;
  right: 8px;
  color: var(--theme-primary);
  font-size: 16px;
}

/* 自定义主题部分 */
.custom-theme-section {
  margin-bottom: 16px;
}

.color-picker-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--theme-border);
}

.color-input {
  width: 100%;
  height: 100%;
  border: none;
  padding: 0;
  cursor: pointer;
}

.color-input::-webkit-color-swatch-wrapper {
  padding: 0;
}

.color-input::-webkit-color-swatch {
  border: none;
}

/* 预览区域 */
.preview-section {
  margin-bottom: 16px;
}

.preview-card {
  background: var(--theme-bg-secondary);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.preview-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--theme-border);
}

.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 8px;
}

.preview-amount {
  font-size: 28px;
  font-weight: bold;
}

.preview-body {
  margin-bottom: 16px;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.preview-label {
  font-size: 14px;
  color: var(--theme-text-secondary);
}

.preview-value {
  font-size: 16px;
  font-weight: bold;
}

.preview-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--theme-border);
}

.reset-wrapper {
  margin-top: 30px;
  padding: 0 24px;
}
</style>
