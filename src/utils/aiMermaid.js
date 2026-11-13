/**
 * AI 消息 Mermaid 图表渲染。正文图表与业务 chart / AiChart 独立并存：
 * xychart（柱状/折线）、pie 和七种结构图从 Markdown 代码块直接渲染。
 * 正文图表使用回复中的数值，不冒充服务端校验过的业务 chart。
 *
 * 安全：mermaid initialize securityLevel:'strict'（htmlLabels 关闭、click/链接剥离）+
 * 渲染产物必须过 sanitizeMermaidSvg（svg 白名单 + CSS 危险模式清洗）才能进 v-html。
 * 渲染是 lazy 的：首次遇到白名单内 mermaid 块才 dynamic import，不进首屏包。
 */

import { sanitizeMermaidSvg } from '@/utils/sanitize';

// 与提示词（v3Prompt.js / openConversation.js）约定的白名单保持一致；改动需两边同步。
export const MERMAID_DIAGRAM_TYPES = ['flowchart', 'graph', 'sequenceDiagram', 'stateDiagram', 'classDiagram', 'erDiagram', 'mindmap', 'xychart', 'pie'];

// stateDiagram-v2 / flowchart-v2 等版本后缀剥掉后比对基础图型
const VERSION_SUFFIX = /-v\d+$/i;

/**
 * 解析 mermaid 代码的图型；支持的图型返回基础名，其余返回 null。
 * 跳过空行与 %% 注释/指令行，取第一个可判定行。
 * @param {unknown} code 围栏内代码
 * @returns {string|null}
 */
export function parseMermaidDiagramType(code) {
  if (typeof code !== 'string' || !code.trim()) return null;
  for (const line of prepareMermaidCode(code).split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('%%')) continue;
    const word = trimmed.split(/\s+/)[0].replace(VERSION_SUFFIX, '').replace(/^xychart-beta$/, 'xychart');
    return MERMAID_DIAGRAM_TYPES.includes(word) ? word : null;
  }
  return null;
}

// AI 围栏只提供图形内容，不允许 init/themeCSS 等指令改变渲染器配置。
// Mermaid 10.9.3 的坐标图入口名称为 xychart-beta，兼容模型省略 beta 的写法。
export function prepareMermaidCode(code) {
  if (typeof code !== 'string') return '';
  const content = code.replace(/%%\{[\s\S]*?\}%%/g, '');
  return content.replace(/^(\s*)xychart(?=\s|$)/m, '$1xychart-beta');
}

let mermaidPromise = null;
function loadMermaid() {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then(mod => {
      const mermaid = mod.default;
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        suppressErrorRendering: true,
        theme: 'neutral',
        // 默认 neutral 的浅灰折线在白底上难辨；正文图表使用固定可读配色。
        themeVariables: {
          xyChart: { plotColorPalette: '#3a66e0,#f59e0b,#16a34a,#9333ea' },
          pie1: '#93b4f5', pie2: '#fdcf83', pie3: '#94d6ac',
        },
        // 700px 默认画布缩进手机消息后文字过小，使用紧凑尺寸并保留坐标标签。
        xyChart: { width: 460, height: 300, titleFontSize: 22,
          xAxis: { labelFontSize: 18, titleFontSize: 18 },
          yAxis: { labelFontSize: 18, titleFontSize: 18 } },
        // 版本决策（追加172 实测）：mermaid v11 全系（11.4/11.12/11.17）在本地环境
        // flowchart 标签静默渲染为空（tspan/span 无文字，securityLevel/htmlLabels/字体均无关，
        // 官方 esm.min 同样复现）；v10.9.3 正常且白名单七种图型齐全，故 package.json 钉死 10.9.3。
        // htmlLabels:false：v10 的 foreignObject 标签在浏览器端会被 DOMPurify 按 SVG 命名空间
        // 守卫剥掉 HTML 子元素（jsdom 测试无法复现），文字标签走 <text><tspan> 天然存活。
        flowchart: { htmlLabels: false },
        sequence: { useMaxWidth: true },
      });
      return mermaid;
    }).catch(err => {
      // 加载失败可重试（弱网/分包异常）：不留缓存失败的 promise
      mermaidPromise = null;
      throw err;
    });
  }
  return mermaidPromise;
}

// 模块级缓存：messageSegments 每次组件重渲染都会重新求值（activityClock 每秒 tick），
// 组件实例内已有缓存，这里再兜住「卸载重挂/历史重进」场景避免重复 render。
const svgCache = new Map();
const CACHE_LIMIT = 30;
let renderSeq = 0;

/**
 * 渲染白名单内 mermaid 代码为已消毒 SVG 字符串。
 * 任何失败（语法错误/加载失败/消毒降级）都抛错或返回空串？——统一约定：返回空串，
 * 调用方（AiMermaid）以空串视为渲染失败并回退代码块。
 * @param {string} code
 * @returns {Promise<string>} 消毒后的 SVG；失败返回空串
 */
export async function renderMermaidSvg(code) {
  const content = prepareMermaidCode(code);
  if (!parseMermaidDiagramType(content)) return '';
  if (svgCache.has(code)) return svgCache.get(code);
  let svg = '';
  try {
    const mermaid = await loadMermaid();
    const renderId = `ai-mermaid-${Date.now()}-${(renderSeq += 1)}`;
    const output = await mermaid.render(renderId, content);
    svg = sanitizeMermaidSvg(output.svg || '');
  } catch (err) {
    // 语法错误/环境不支持（如 jsdom 无 getBBox）都走这里；渲染失败不吞内容——
    // AiMermaid 会回退显示原始代码块。
    svg = '';
  }
  if (svg) {
    if (svgCache.size >= CACHE_LIMIT) svgCache.delete(svgCache.keys().next().value);
    svgCache.set(code, svg);
  }
  return svg;
}

export default { parseMermaidDiagramType, prepareMermaidCode, renderMermaidSvg, MERMAID_DIAGRAM_TYPES };
