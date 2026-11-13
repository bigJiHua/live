/**
 * 剪贴板（追加164）：从 Diary/Detail.vue copyToClipboard 抽取的通用实现。
 * Clipboard API 优先，非安全上下文/失败时回退 textarea + execCommand。
 */

import { showSuccessToast, showFailToast } from '@/utils/toast';
import { copyWithTextarea } from './clipboardFallback';

export async function copyText(text, successMessage = '已复制') {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else if (!copyWithTextarea(text)) {
      throw new Error('Copy command failed');
    }
    if (successMessage) showSuccessToast(successMessage);
    return true;
  } catch (_) {
    if (successMessage) showFailToast('复制失败，请手动选择文本');
    return false;
  }
}

export default { copyText };
