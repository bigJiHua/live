/**
 * 富文本 HTML 消毒（唯一入口）
 *
 * ## 为什么需要
 * 日记正文来自 wangEditor，入库时**不做过滤**（`views/Diary/Add.vue:542,553` 原样存 HTML），
 * 渲染时用 `v-html` 直接输出。若不消毒，任何能写入日记的人
 * （含拿到分享链接的未登录访客，因为 `/share/diary/detail` 在路由白名单内）
 * 都能注入 `<script>` / `onerror=` / `javascript:` 等脚本 => 存储型 XSS。
 *
 * ## 为什么放在「渲染侧」而不是「入库侧」
 * 1. 入库侧可被绕过：攻击者不一定走前端表单，可能直接调 API 写库。
 * 2. 渲染侧是**执行前最后一道闸门**，无论数据怎么来的都会被拦。
 * 3. 历史脏数据无需清洗脚本，读出来就会被净化（原始数据仍留库，可回退）。
 *
 * ## 使用约定（新增 v-html 时必须走本函数）
 *   import { sanitizeHtml } from '@/utils/sanitize';
 *   <div v-html="sanitizeHtml(row.content)"></div>
 *
 * ⚠ 禁止在模板里写 `v-html="xxx"`（未经消毒）——那等于把用户内容当代码执行。
 */

import DOMPurify from 'dompurify';

// ==================== 白名单配置 ====================

/** 允许的标签：仅覆盖 wangEditor 会产出的排版标签，未列出的标签会被「剥离标签但保留文本」 */
const ALLOWED_TAGS = [
  // 块级 / 换行
  'p', 'br', 'hr', 'div', 'span', 'blockquote', 'pre', 'code',
  // 文本样式
  'strong', 'b', 'em', 'i', 'u', 's', 'strike', 'del', 'sub', 'sup', 'mark', 'small',
  // 列表
  'ul', 'ol', 'li',
  // 标题
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  // 媒体 / 链接
  'img', 'a',
  // 表格
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption',
];

/**
 * 允许的属性。
 * ⚠ 刻意**不包含** `style`：CSS 可做全屏覆盖/点击劫持/内容伪造，比 XSS 更隐蔽。
 * ⚠ 刻意**不包含** `class`：避免借用宿主页面的类名伪装元素（视觉欺骗）。
 * ⚠ 刻意**不包含** `id`：避免锚点/JS 引用冲突与 DOM 覆盖。
 */
const ALLOWED_ATTR = [
  'href', 'title', 'target', 'rel',       // a
  'src', 'alt', 'width', 'height',        // img
  'colspan', 'rowspan',                   // table
];

/** 即使绕过白名单也显式禁掉的高危标签 */
const FORBID_TAGS = [
  'script', 'style', 'iframe', 'frame', 'object', 'embed',
  'form', 'input', 'button', 'textarea', 'select', 'option',
  'link', 'meta', 'base', 'svg', 'math', 'template', 'noscript',
];

const PURIFY_CONFIG = {
  ALLOWED_TAGS,
  ALLOWED_ATTR,
  FORBID_TAGS,
  // 不需要 data-* 属性
  ALLOW_DATA_ATTR: false,
  // 不允许 ARIA 之外的额外内容；保留 aria 以免破坏无障碍
  ALLOW_ARIA_ATTR: true,
  // 明确拒绝注释节点（条件注释在旧 IE 可执行）
  ALLOW_UNKNOWN_PROTOCOLS: false,
};

// ==================== 危险链接兜底 ====================

/**
 * 给带 target 的链接补 rel="noopener noreferrer"。
 * DOMPurify 只保证 href 协议安全，不会自动补 rel，
 * 而 `target="_blank"` + 未加 noopener ⇒ window.opener 可被源页面用于钓鱼跳转。
 */
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.hasAttribute('target')) {
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

// ==================== 对外 API ====================

/**
 * 消毒富文本 HTML，供 v-html 使用
 * @param {unknown} dirty 未经处理的 HTML（通常来自接口）
 * @returns {string} 可安全渲染的 HTML；入参为空或非字符串时返回空串
 */
export function sanitizeHtml(dirty) {
  if (!dirty || typeof dirty !== 'string') return '';
  try {
    return DOMPurify.sanitize(dirty, PURIFY_CONFIG);
  } catch (err) {
    //消毒器自身异常时必须「宁可显示为空」，绝不能把原始内容放行
    console.error('[sanitize] 消毒失败，已降级为空内容:', err);
    return '';
  }
}

/**
 * 消毒纯文本（用于标题、昵称等不应含 HTML 的字段）
 * 会把 `<`、`>`、`&` 等转义，浏览器按字面量显示
 * @param {unknown} dirty
 * @returns {string}
 */
export function sanitizeText(dirty) {
  if (dirty === null || dirty === undefined) return '';
  const str = String(dirty);
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ==================== Mermaid SVG 白名单（追加172） ====================
// AI 正文里的 ```mermaid 结构图由 AiMermaid 渲染成 SVG 后经本入口进 v-html。
// 与主白名单完全独立：主配置禁 svg 是为富文本设的；SVG 画面需要显式放行一批
// 图形标签。铁律不变——mermaid 输出未经 sanitizeMermaidSvg 不得进任何 v-html。
const MERMAID_TAGS = [
  'svg', 'g', 'defs', 'marker', 'clipPath',
  'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon',
  'text', 'tspan', 'textPath', 'title', 'desc',
  // mermaid 主题样式走 <style>：内容由下方 CSS 危险模式清洗兜底
  'style',
  // mermaid 在 securityLevel:strict 下强制 htmlLabels（节点文字在 foreignObject 的 HTML 里，
  // mermaid 自身已 DOMPurify 消毒，这里再过同一入口的白名单+CSS 清洗，双层不放行脚本/链接/图片）
  'foreignObject', 'div', 'span', 'p', 'br', 'b', 'i', 'em', 'strong', 'small', 'sub', 'sup',
  'table', 'thead', 'tbody', 'tr', 'td', 'th', 'ul', 'ol', 'li', 'h3', 'h4', 'code', 'pre', 'hr',
];
const MERMAID_ATTRS = [
  'id', 'class', 'style', 'role', 'tabindex', 'aria-roledescription', 'aria-label',
  'd', 'fill', 'fill-opacity', 'stroke', 'stroke-opacity', 'stroke-width', 'stroke-dasharray',
  'stroke-linecap', 'stroke-linejoin', 'marker-end', 'marker-start', 'marker-mid',
  'transform', 'transform-origin', 'x', 'y', 'x1', 'x2', 'y1', 'y2', 'cx', 'cy', 'r', 'rx', 'ry',
  'width', 'height', 'points', 'viewBox', 'xmlns', 'version', 'preserveAspectRatio', 'overflow',
  'markerWidth', 'markerHeight', 'refX', 'refY', 'orient', 'markerUnits',
  'text-anchor', 'font-family', 'font-size', 'font-weight', 'font-style', 'letter-spacing',
  'dominant-baseline', 'alignment-baseline', 'dy', 'dx', 'rotate', 'textLength', 'lengthAdjust',
  'gradientUnits', 'gradientTransform', 'offset', 'stop-color', 'stop-opacity', 'spreadMethod',
  'patternUnits', 'patternContentUnits', 'patternTransform', 'clip-path', 'mask', 'opacity',
];
// 图内链接、外部资源与动画一律禁；foreignObject 已放行（见上，内容走 HTML 白名单子集）
const MERMAID_FORBID_TAGS = [
  'script', 'iframe', 'frame', 'object', 'embed', 'audio', 'video',
  'a', 'image', 'use', 'animate', 'animateMotion', 'animateTransform', 'set',
  'form', 'input', 'button', 'textarea', 'select', 'link', 'meta', 'base', 'template',
];
const MERMAID_PURIFY_CONFIG = {
  ALLOWED_TAGS: MERMAID_TAGS,
  ALLOWED_ATTR: MERMAID_ATTRS,
  FORBID_TAGS: MERMAID_FORBID_TAGS,
  ALLOW_DATA_ATTR: false,
  ALLOW_ARIA_ATTR: true,
  ALLOW_UNKNOWN_PROTOCOLS: false,
};
// <style> 文本与 style 属性是 AI 可控 CSS（mermaid 的 themeCSS 指令可注入任意选择器）。
// 只放行无外联、无定位逃逸、无表达式执行的安全子集；url 仅保留 #fragment（箭头 marker 引用）。
const CSS_DANGEROUS_PATTERNS = [
  /@import[^;]*;?/gi,
  /@charset[^;]*;?/gi,
  /@namespace[^;]*;?/gi,
  /url\s*\(\s*(['"]?)\s*(?!#)[^)'"]*\1\s*\)/gi,
  /position\s*:\s*(?:fixed|absolute)\b/gi,
  /expression\s*\(/gi,
  /behaviou?r\s*:/gi,
  /javascript\s*:/gi,
  /-moz-binding[^;]*;?/gi,
];
function scrubUnsafeCss(css) {
  if (typeof css !== 'string' || !css) return css;
  let out = css;
  for (const pattern of CSS_DANGEROUS_PATTERNS) out = out.replace(pattern, 'none');
  return out;
}
// DOMPurify 钩子够不到 <style> 的文本子节点与 style 属性值的稳定清洗——在消毒后的
// 字符串上做（文本子节点已被实体化转义，不会误伤正文）。
function scrubSvgCss(svg) {
  return svg
    .replace(/<style>([\s\S]*?)<\/style>/gi, (_, css) => `<style>${scrubUnsafeCss(css)}</style>`)
    .replace(/\sstyle\s*=\s*"([^"]*)"/gi, (match, css) => {
      const scrubbed = scrubUnsafeCss(css);
      return scrubbed === css ? match : ` style="${scrubbed}"`;
    });
}

/**
 * 消毒 mermaid 渲染产物 SVG，供 AiMermaid 的 v-html 使用。
 * 主富文本 sanitizeHtml 的白名单（禁 svg）不受影响；两条管道互不穿透。
 * @param {unknown} dirty mermaid.render 产出的 SVG 字符串（AI 代码间接产物，不可信）
 * @returns {string} 可安全渲染的 SVG；入参为空或非字符串时返回空串
 */
export function sanitizeMermaidSvg(dirty) {
  if (!dirty || typeof dirty !== 'string') return '';
  try {
    return scrubSvgCss(DOMPurify.sanitize(dirty, MERMAID_PURIFY_CONFIG));
  } catch (err) {
    // 与 sanitizeHtml 同一降级原则：宁可显示为空，绝不放行原始内容
    console.error('[sanitize] mermaid SVG 消毒失败，已降级为空内容:', err);
    return '';
  }
}

export default { sanitizeHtml, sanitizeText, sanitizeMermaidSvg };