<template>
  <!-- 白名单外/未激活/渲染失败一律回退原始代码块（与无 mermaid 时的展示一致，不吞内容） -->
  <pre v-if="shown === 'code'" class="ai-mermaid-code"><code>{{ code }}</code></pre>
  <!-- svg 内容已由 renderMermaidSvg → sanitizeMermaidSvg（utils/sanitize.js 唯一入口）消毒 -->
  <figure v-else class="ai-mermaid">
    <figcaption v-if="dataChart">AI 正文图表</figcaption>
    <div role="img" :aria-label="dataChart ? 'AI 正文图表' : 'Mermaid 结构图'" v-html="svg"></div>
  </figure>
</template>
<script setup>
// AI 正文 mermaid 图表渲染，与业务 AiChart 卡片独立并存。
// messageSegments 每次组件重渲染都会重新求值（activityClock 每秒 tick），渲染结果落在
// ref + utils/aiMermaid 模块缓存里，重渲染不会重复画图。
import { computed, ref, watch } from 'vue';
import { parseMermaidDiagramType, renderMermaidSvg } from '@/utils/aiMermaid';

const props = defineProps({
  code: { type: String, required: true },
  // 流式期间（轮次未 done）只显示代码；done 后（含历史恢复）才渲染
  active: { type: Boolean, default: true },
});
const shown = ref('code');
const svg = ref('');
const dataChart = computed(() => ['xychart', 'pie'].includes(parseMermaidDiagramType(props.code)));
let renderRevision = 0;

watch(() => [props.code, props.active], async ([code, active]) => {
  const revision = ++renderRevision;
  shown.value = 'code';
  svg.value = '';
  if (!active || !parseMermaidDiagramType(code)) return;
  const rendered = await renderMermaidSvg(code);
  // 重生成或回到 pending 后，旧的异步结果不能盖过当前正文。
  if (rendered && revision === renderRevision && props.code === code && props.active) {
    svg.value = rendered;
    shown.value = 'diagram';
  }
}, { immediate: true });
</script>
<style scoped>
.ai-mermaid-code{margin:6px 0;padding:10px 12px;border-radius:8px;background:var(--theme-bg-secondary);overflow-x:auto;font-size:12px;line-height:1.6}
.ai-mermaid-code code{padding:0;background:none;font-size:inherit}
.ai-mermaid{margin:6px 0 8px;padding:6px 8px;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary);overflow-x:auto}
.ai-mermaid figcaption{margin:0 0 6px;font-size:11px;color:var(--theme-text-secondary)}
.ai-mermaid > div{background:#fff;border-radius:6px}
.ai-mermaid :deep(svg){max-width:100%;height:auto;display:block;margin:0 auto}
</style>
