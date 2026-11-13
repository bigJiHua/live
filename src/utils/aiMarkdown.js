/**
 * AI 消息 Markdown 渲染（追加164）
 *
 * 铁律（utils/sanitize.js 文档）：任何 v-html 必须先过 sanitizeHtml。
 * AI 输出是不可信数据——marked 转 HTML 后必须经 DOMPurify 白名单清洗再进 v-html，
 * 禁止任何绕过本管道直接 v-html AI 文本的写法。
 * sanitize.js 白名单已覆盖 Markdown 全部产出标签（p/ul/ol/li/strong/em/code/pre/
 * blockquote/h1-h6/table/a…），无需扩表；未列出的标签会被剥离但保留文本。
 */

import { marked } from 'marked';
import { sanitizeHtml } from '@/utils/sanitize';

// GFM（表格/删除线/任务列表）+ 不产出带 id 的标题锚点（白名单不含 id 属性）
marked.setOptions({ gfm: true, breaks: true });

// Use the renderer's own grammar so fenced and indented code never enters
// message-level flow folding, including unclosed or longer fences.
export function getAiMarkdownCodeLines(text) {
  const codeLines = new Set();
  if (typeof text !== 'string' || !text) return codeLines;
  let line = 0;
  for (const token of marked.lexer(text)) {
    const raw = token.raw || '';
    const newlines = (raw.match(/\n/g) || []).length;
    if (token.type === 'code') {
      const end = line + newlines + (raw.endsWith('\n') ? 0 : 1);
      for (let index = line; index < end; index++) codeLines.add(index);
    }
    line += newlines;
  }
  return codeLines;
}

// 追加172：提取 ```mermaid 围栏块供 AssistantMessage 分段渲染（白名单判定/渲染在
// utils/aiMermaid.js 与 AiMermaid.vue）。与 getAiMarkdownCodeLines 同款 lexer 计行法，
// 覆盖区间 [start, end)（含围栏行与收尾围栏；未闭合围栏到文末——流式期间组件按
// active=false 只显示代码，done 后文本必然闭合）。
export function getAiMarkdownMermaidBlocks(text) {
  const blocks = [];
  if (typeof text !== 'string' || !text) return blocks;
  let line = 0;
  for (const token of marked.lexer(text)) {
    const raw = token.raw || '';
    const newlines = (raw.match(/\n/g) || []).length;
    if (token.type === 'code' && /^\s*mermaid\s*$/i.test(token.lang || '')) {
      const end = line + newlines + (raw.endsWith('\n') ? 0 : 1);
      blocks.push({ start: line, end, code: String(token.text || '') });
    }
    line += newlines;
  }
  return blocks;
}

export function renderAiMarkdown(text) {
  if (typeof text !== 'string' || !text.trim()) return '';
  return sanitizeHtml(marked.parse(text));
}

export default { renderAiMarkdown };
