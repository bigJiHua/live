<template>
  <div ref="chartEl" class="ai-chart" role="img" :aria-label="chart.title" />
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';
import { init } from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { use } from 'echarts/core';
import { buildAiChartOptions } from '@/utils/aiChartOptions';
use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer]);
const props = defineProps({ chart: { type: Object, required: true }, dataset: { type: Object, required: true } });
const chartEl = ref(null);
let instance, observer;
async function render() {
  await nextTick();
  if (!chartEl.value) return;
  const option = buildAiChartOptions(props.chart, props.dataset, { textColor: getComputedStyle(chartEl.value).color });
  if (!instance) instance = init(chartEl.value);
  instance.setOption(option, true);
}
watch(() => [props.chart, props.dataset], render);
onMounted(() => { render(); if (typeof ResizeObserver !== 'undefined' && chartEl.value) { observer = new ResizeObserver(() => instance?.resize()); observer.observe(chartEl.value); } });
onBeforeUnmount(() => { observer?.disconnect(); instance?.dispose(); });
</script>
<style scoped>
.ai-chart{height:210px;width:100%;margin:8px 0 10px;padding:6px 8px 2px;box-sizing:border-box;border:1px solid var(--theme-border);border-radius:12px;background:var(--theme-bg-secondary)}
@media(max-width:600px){.ai-chart{height:190px;margin:6px 0 8px;padding:4px 6px 2px}}
</style>
