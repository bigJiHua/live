<template>
  <div
    class="ui-showcase"
    :class="[{ 'glass-on': glassOn }, themeClass]"
    :style="activeVars"
  >
    <div class="page">
      <!-- 1. 导航栏 -->
      <section>
        <h3 class="sec-title">导航栏 NavBar（返回文字/图标/标题配色）</h3>
        <van-nav-bar
          title="标题文字"
          :fixed="false"
          :left-arrow="true"
          @click-left="noop"
        >
          <template #right>
            <van-icon name="search" size="20" />
            <van-icon name="ellipsis" size="20" style="margin-left: 12px" />
          </template>
        </van-nav-bar>
      </section>

      <!-- 2. 底部 Tabbar -->
      <section>
        <h3 class="sec-title">底部 Tabbar（激活色 = 主色）</h3>
        <div class="tabbar-demo">
          <van-tabbar :fixed="false" v-model="tabbarActive">
            <van-tabbar-item icon="wap-home-o">首页</van-tabbar-item>
            <van-tabbar-item icon="bill-o">账本</van-tabbar-item>
            <van-tabbar-item icon="notes-o">动态</van-tabbar-item>
            <van-tabbar-item icon="user-o">我的</van-tabbar-item>
          </van-tabbar>
        </div>
      </section>

      <!-- 3. 按钮 -->
      <section>
        <h3 class="sec-title">按钮 Buttons（实心）</h3>
        <div class="row">
          <app-button type="primary">主要 Primary</app-button>
          <app-button type="success">成功</app-button>
          <app-button type="warning">警告</app-button>
          <app-button type="danger">危险</app-button>
          <app-button type="default">默认</app-button>
        </div>
        <h3 class="sec-title">按钮 Buttons（Plain 描边）</h3>
        <div class="row">
          <app-button type="primary" plain>主要</app-button>
          <app-button type="success" plain>成功</app-button>
          <app-button type="warning" plain>警告</app-button>
          <app-button type="danger" plain>危险</app-button>
          <app-button type="default" plain>默认</app-button>
        </div>
        <h3 class="sec-title">按钮 尺寸 / 块级 / 状态</h3>
        <div class="row">
          <app-button size="large" type="primary" block>块级大按钮</app-button>
        </div>
        <div class="row">
          <app-button size="small" type="primary">小按钮</app-button>
          <app-button size="mini" type="primary">迷你</app-button>
          <app-button type="primary" round>圆角</app-button>
          <app-button type="primary" :loading="true">加载中</app-button>
          <app-button type="primary" disabled>禁用</app-button>
        </div>
      </section>

      <!-- 4. 数字与金额 -->
      <section>
        <h3 class="sec-title">数字与金额（金额字体 / 收入 / 支出 / 结余）</h3>
        <div class="amount-big num-font">¥12,345.67</div>
        <div class="row stats">
          <div class="stat">
            <div class="num-font text-income">+¥1,234.00</div>
            <div class="lbl">本月收入</div>
          </div>
          <div class="stat">
            <div class="num-font text-expense">-¥567.00</div>
            <div class="lbl">本月支出</div>
          </div>
          <div class="stat">
            <div class="num-font text-primary">¥766.67</div>
            <div class="lbl">结余</div>
          </div>
        </div>
      </section>

      <!-- 5. 标签 -->
      <section>
        <h3 class="sec-title">标签 Tag</h3>
        <div class="row">
          <app-tag type="primary">主要</app-tag>
          <app-tag type="success">成功</app-tag>
          <app-tag type="warning">警告</app-tag>
          <app-tag type="danger">危险</app-tag>
          <app-tag type="default">默认</app-tag>
        </div>
        <div class="row">
          <app-tag plain type="primary">主要</app-tag>
          <app-tag plain type="success">成功</app-tag>
          <app-tag plain type="warning">警告</app-tag>
          <app-tag plain type="danger">危险</app-tag>
        </div>
        <div class="row">
          <app-tag type="success">已还款</app-tag>
          <app-tag type="warning">进行中</app-tag>
          <app-tag type="danger">已删除</app-tag>
          <app-tag type="primary" mark>标记</app-tag>
        </div>
      </section>

      <!-- 6. 图标墙 -->
      <section>
        <h3 class="sec-title">图标 Icon（语义配色）</h3>
        <div class="icon-wall">
          <div class="ic" style="color: var(--theme-primary)">
            <van-icon name="balance-o" />
            <span>账户</span>
          </div>
          <div class="ic" style="color: var(--theme-success)">
            <van-icon name="gold-coin-o" />
            <span>收入</span>
          </div>
          <div class="ic" style="color: var(--theme-warning)">
            <van-icon name="cash-o" />
            <span>支出</span>
          </div>
          <div class="ic" style="color: var(--theme-danger)">
            <van-icon name="warning-o" />
            <span>警示</span>
          </div>
          <div class="ic" style="color: var(--theme-info)">
            <van-icon name="info-o" />
            <span>信息</span>
          </div>
          <div class="ic">
            <van-icon name="setting-o" />
            <span>设置</span>
          </div>
        </div>
      </section>

      <!-- 7. 表单 & 开关 -->
      <section>
        <h3 class="sec-title">表单 Field & Switch（标签/输入/占位配色）</h3>
        <van-cell-group>
          <app-field v-model="form.name" label="姓名" placeholder="请输入姓名" />
          <app-field v-model="form.amount" label="金额" placeholder="0.00" />
          <app-cell title="开启状态（开 = 主色）">
            <template #value>
              <van-switch v-model="sw" />
            </template>
          </app-cell>
        </van-cell-group>
      </section>

      <!-- 8. 进度 -->
      <section>
        <h3 class="sec-title">进度 Progress（主色）</h3>
        <div class="row progress-row">
          <van-circle :rate="70" text="70%" />
          <div style="flex: 1">
            <van-progress :percentage="60" />
            <div style="height: 10px" />
            <van-progress :percentage="85" />
          </div>
        </div>
      </section>

      <!-- 9. 渐变头图卡 -->
      <section>
        <h3 class="sec-title">渐变头图卡（主色同色调浅变）</h3>
        <div class="hero-card">
          <div class="hero-title">总资产</div>
          <div class="hero-num num-font">¥1,280,000</div>
          <div class="hero-sub">本月 +¥12,345 · 较上月 ↑ 3.2%</div>
        </div>
      </section>

      <!-- 10. 卡片 -->
      <section>
        <h3 class="sec-title">卡片 Card</h3>
        <div class="app-card" style="margin-bottom: 10px">
          <div class="card-title">标准卡片</div>
          <div class="card-body">这是一段正文内容，用于观察文字主色与卡片背景的对比。</div>
        </div>
        <div class="app-card themed">
          <div class="card-title text-theme">主色强调卡片</div>
          <div class="card-body">带主色文字强调的卡片示例。</div>
        </div>
      </section>

      <!-- 11. 分类配色参考 -->
      <section>
        <h3 class="sec-title">分类配色参考（散落硬编码色，待收编为 token）</h3>
        <div class="swatch-grid">
          <div
            class="swatch"
            v-for="c in swatches"
            :key="c.hex"
            :style="{ background: c.hex }"
          >
            <span>{{ c.name }}</span>
            <em>{{ c.hex }}</em>
          </div>
        </div>
      </section>

      <!-- 12. 文字色阶 & 边框 -->
      <section>
        <h3 class="sec-title">文字色阶 & 边框</h3>
        <p class="text-primary">主文字 #323233</p>
        <p class="text-secondary">次文字 #646566</p>
        <p class="text-tertiary">三级文字 #969799</p>
        <p style="color: var(--theme-text-placeholder)">占位文字 #c8c9cc</p>
        <div class="border-demo">边框色</div>
      </section>
    </div>

    <!-- 悬浮配色选择器 / 调色器（v-model 实时双向同步） -->
    <div class="floating-picker" :class="{ expanded: pickerOpen }">
      <div class="picker-toggle" @click="pickerOpen = !pickerOpen">
        <van-icon name="palette" />
      </div>
      <div class="picker-panel" v-show="pickerOpen">
        <div class="picker-h">配色方案</div>
        <div class="swatches">
          <button
            v-for="t in themes"
            :key="t.key"
            class="sw"
            :class="{ on: t.key === selectedKey }"
            :style="{ background: t.preview }"
            :title="t.name"
            @click="selectedKey = t.key"
          />
        </div>
        <van-divider>实时调主色</van-divider>
        <div class="color-ctrl">
          <span>主色</span>
          <input type="color" v-model="customPrimary" />
          <span class="hex">{{ customPrimary.toUpperCase() }}</span>
        </div>
        <van-divider>材质质感</van-divider>
        <app-cell center title="玻璃拟态">
          <template #right-icon>
            <van-switch v-model="glassOn" size="20" />
          </template>
        </app-cell>
        <div class="picker-tip">新增配色只需在 useUiTheme 的 THEME_PRESETS push 一套</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { THEME_PRESETS as themes, buildVars } from "@/composables/useUiTheme";

const tabbarActive = ref(0);
const sw = ref(true);
const form = ref({ name: "", amount: "" });
const noop = () => {};

// 主题列表统一维护在 @/composables/useUiTheme（THEME_PRESETS），已通过 import 别名 themes 引入
// 变量生成统一使用 @/composables/useUiTheme 的 buildVars
// （已含状态文字衍生色与 TabBar 玻璃变量，与正式主题一致）

const selectedKey = ref(themes[0].key);
const customPrimary = ref(themes[0].primary);
const glassOn = ref(false);
const pickerOpen = ref(false);

const currentTheme = computed(() => themes.find((t) => t.key === selectedKey.value) || themes[0]);
const themeClass = computed(() => `theme-${currentTheme.value.key}`);

// 切换方案时，调色器同步回该方案主色
watch(selectedKey, () => {
  customPrimary.value = currentTheme.value.primary;
});

// 组装最终变量：基础 + 方案 + 实时主色覆盖 + 玻璃拟态浅色覆盖
const activeVars = computed(() => {
  const vars = buildVars(currentTheme.value);
  // 实时调主色（v-model 双向绑定）
  Object.assign(vars, {
    "--theme-primary": customPrimary.value,
    "--van-primary-color": customPrimary.value,
    "--van-nav-bar-text-color": customPrimary.value,
    "--van-tabbar-item-active-color": customPrimary.value,
    "--van-switch-on-background": customPrimary.value,
    "--van-button-primary-background": customPrimary.value,
    "--van-button-primary-border-color": customPrimary.value,
  });
  // 玻璃拟态：统一浅色文字浮于彩色背景
  if (glassOn.value) {
    Object.assign(vars, {
      "--theme-text-primary": "#ffffff",
      "--theme-text-secondary": "rgba(255,255,255,.72)",
      "--theme-text-tertiary": "rgba(255,255,255,.5)",
      "--theme-text-placeholder": "rgba(255,255,255,.4)",
      "--van-nav-bar-title-text-color": "#fff",
      "--van-cell-text-color": "#fff",
      "--van-cell-label-color": "rgba(255,255,255,.6)",
      "--van-field-input-text-color": "#fff",
      "--van-field-label-color": "rgba(255,255,255,.7)",
      "--van-tag-default-color": "rgba(255,255,255,.85)",
      "--van-button-default-color": "#fff",
      "--van-button-default-background": "rgba(255,255,255,.12)",
      "--van-button-default-border-color": "rgba(255,255,255,.25)",
    });
  }
  return vars;
});

// 分类配色参考（来源：审计发现的散落硬编码色，现统一指向 --van-* 主题别名）
const swatches = [
  { name: "收入绿", hex: "var(--van-green, #07c160)" },
  { name: "支出橙", hex: "var(--van-orange, #ff976a)" },
  { name: "危险红", hex: "var(--van-danger-color, #ee0a24)" },
  { name: "主色蓝", hex: "var(--van-blue, #1989fa)" },
  { name: "靛蓝", hex: "#3a66e0" },
  { name: "紫", hex: "var(--van-purple, #7232dd)" },
  { name: "青", hex: "#00bcd4" },
  { name: "teal", hex: "#009688" },
  { name: "粉", hex: "#e91e63" },
  { name: "黄", hex: "#ffb300" },
  { name: "深蓝", hex: "#0a4ba8" },
  { name: "琥珀", hex: "#e6a23c" },
  { name: "境内绿条", hex: "var(--van-green, #07c160)" },
  { name: "境外蓝条", hex: "var(--van-blue, #1989fa)" },
  { name: "信用卡红条", hex: "var(--van-danger-color, #ee0a24)" },
];
</script>

<style scoped>
.ui-showcase {
  min-height: 100vh;
  background: var(--theme-bg-primary);
  padding-bottom: 60px;
  transition: background 0.3s ease;
}
.page {
  padding: 12px;
}
section {
  background: var(--theme-bg-secondary);
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;
  transition: background 0.3s ease;
}
.sec-title {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin: 4px 2px 12px;
  font-weight: 600;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  align-items: center;
}
/* 让展示用 tabbar 不 fixed，仅作预览 */
.tabbar-demo :deep(.van-tabbar) {
  position: static;
}
.amount-big {
  font-size: 32px;
  color: var(--theme-text-primary);
  margin: 4px 0 12px;
}
.stats {
  justify-content: space-between;
}
.stat {
  flex: 1;
  min-width: 90px;
  text-align: center;
}
.stat .lbl {
  font-size: 12px;
  color: var(--theme-text-tertiary);
  margin-top: 6px;
}
.icon-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}
.ic {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--theme-text-secondary);
}
.ic .van-icon {
  font-size: 24px;
}
.progress-row {
  align-items: center;
}
.hero-card {
  background: linear-gradient(135deg, var(--theme-primary), var(--theme-primary-grad));
  color: #fff;
  border-radius: 16px;
  padding: 24px;
}
.hero-title {
  font-size: 13px;
  opacity: 0.85;
}
.hero-num {
  font-size: 30px;
  margin: 8px 0;
}
.hero-sub {
  font-size: 12px;
  opacity: 0.8;
}
.app-card {
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  padding: 14px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}
.card-body {
  font-size: 13px;
  color: var(--theme-text-secondary);
}
.swatch-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.swatch {
  height: 64px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 8px;
  font-size: 11px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}
.swatch em {
  opacity: 0.85;
  font-style: normal;
}
.border-demo {
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 12px;
  color: var(--theme-text-tertiary);
  font-size: 13px;
}
.num-font {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.text-income { color: var(--theme-success); }
.text-expense { color: var(--theme-warning); }
.text-primary { color: var(--theme-primary); }
.text-theme { color: var(--theme-primary); }
.text-secondary { color: var(--theme-text-secondary); }
.text-tertiary { color: var(--theme-text-tertiary); }

/* 黑白方案：按钮更方正（银行简约感） */
.theme-mono :deep(.van-button) {
  border-radius: 4px;
}

/* 黑金方案：主按钮金色微光，强化奢华质感 */
.theme-blackgold :deep(.van-button--primary) {
  box-shadow: 0 2px 10px rgba(201, 168, 106, 0.32);
}
/* 钛金属：银色主按钮微光 */
.theme-titanium :deep(.van-button--primary) {
  box-shadow: 0 2px 10px rgba(216, 216, 220, 0.25);
}

/* ===== 悬浮配色选择器 ===== */
.floating-picker {
  position: fixed;
  right: 14px;
  bottom: 14px;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.picker-toggle {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--theme-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  cursor: pointer;
  transition: transform 0.2s ease;
}
.floating-picker.expanded .picker-toggle {
  transform: rotate(30deg) scale(0.92);
}
.picker-panel {
  width: 240px;
  background: var(--theme-bg-secondary);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  padding: 14px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.28);
}
.picker-h {
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 10px;
}
.swatches {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}
.sw {
  aspect-ratio: 1;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: transform 0.15s ease;
}
.sw:hover {
  transform: scale(1.08);
}
.sw.on {
  border-color: var(--theme-text-primary);
}
.color-ctrl {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--theme-text-secondary);
}
.color-ctrl input[type="color"] {
  width: 40px;
  height: 28px;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
}
.color-ctrl .hex {
  font-family: monospace;
  color: var(--theme-text-primary);
}
.picker-tip {
  font-size: 11px;
  color: var(--theme-text-tertiary);
  margin-top: 8px;
  text-align: center;
}

/* ===== 玻璃拟态：彩色 aurora 背景 + 毛玻璃卡片 ===== */
.glass-on {
  background:
    radial-gradient(700px 500px at 15% 8%, rgba(255, 255, 255, 0.12), transparent 60%),
    radial-gradient(620px 460px at 85% 18%, var(--theme-primary), transparent 55%),
    linear-gradient(135deg, var(--theme-bg-primary), var(--theme-bg-tertiary) 55%, var(--theme-bg-secondary));
  background-attachment: fixed;
}
.glass-on section,
.glass-on .app-card {
  background: rgba(255, 255, 255, 0.1) !important;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.16) !important;
}
.glass-on :deep(.van-cell),
.glass-on :deep(.van-card),
.glass-on :deep(.van-tag--default) {
  background: rgba(255, 255, 255, 0.1) !important;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.16) !important;
  color: #fff;
}
.glass-on .picker-panel,
.glass-on .picker-toggle {
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}
</style>
