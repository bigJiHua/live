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

export default { sanitizeHtml, sanitizeText };