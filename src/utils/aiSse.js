/**
 * SSE 帧解析纯函数（追加164）。零依赖、零 DOM——node --test 可直接单测；
 * streamRequest.js 消费之，浏览器 E2E 覆盖其网络/DOM 部分。
 */

export function createSseFrameParser(onFrame, { maxFrameChars = 1024 * 1024 } = {}) {
  let line = '', event = 'message', dataLines = [], frameChars = 0, afterCr = false, ended = false;
  const invalid = message => {
    ended = true; line = ''; dataLines = [];
    throw Object.assign(new Error(message), { code: 'AI_RESPONSE_INVALID', kind: 'protocol' });
  };
  const dispatch = () => {
    const name = event, data = dataLines.join('\n');
    const hasData = dataLines.length > 0;
    event = 'message'; dataLines = []; frameChars = 0;
    if (!hasData) return;
    let payload;
    try { payload = JSON.parse(data); } catch (_) { invalid('AI 流式响应包含无法解析的数据帧'); }
    // A terminal receipt owns the outcome even if a broken peer appends bytes.
    if (name === 'done' || name === 'error') ended = true;
    onFrame({ event: name, data: payload });
  };
  const consumeLine = () => {
    const value = line; line = '';
    if (!value) { dispatch(); return; }
    if (value.startsWith('event:')) event = value.slice(6).trim() || 'message';
    else if (value.startsWith('data:')) dataLines.push(value.slice(5).replace(/^ /, ''));
  };
  return {
    push(chunkText) {
      for (const char of chunkText) {
        if (ended) return;
        if (afterCr) { afterCr = false; if (char === '\n') continue; }
        if (++frameChars > maxFrameChars) invalid('AI 流式响应数据帧超过大小限制');
        if (char === '\r' || char === '\n') {
          consumeLine(); afterCr = char === '\r';
        } else line += char;
      }
    },
    end() {
      if (ended) return;
      if (line) consumeLine();
      dispatch(); ended = true; line = '';
    },
  };
}

export default { createSseFrameParser };
