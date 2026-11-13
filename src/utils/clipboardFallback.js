// execCommand 复制失败会返回 false（而不是抛错），调用方必须检查该结果。
export function copyWithTextarea(text, doc = document) {
  const area = doc.createElement('textarea');
  area.value = text;
  area.style.position = 'fixed';
  area.style.opacity = '0';
  doc.body.appendChild(area);
  try {
    area.select();
    return doc.execCommand('copy') === true;
  } finally {
    doc.body.removeChild(area);
  }
}
