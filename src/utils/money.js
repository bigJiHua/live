// 金额格式化：保留真实精度，不做四舍五入
//
// 外币折算成人民币可能含最多 4 位小数（如 1 HKD @ 86.58 = 0.8658 元），
// 因此不能对金额做四舍五入。规则：
//   - 最多 4 位小数（截断，不向上/向下舍入）
//   - 至少 2 位小数（人民币保持到“分”，避免 100 显示成 100）
//   - 支持千分位分隔
export function formatMoney(val) {
  const num = Number(val);
  if (!isFinite(num)) return "0.00";

  const neg = num < 0;
  const abs = Math.abs(num);

  // 截断到 4 位小数（避免浮点噪声，且不四舍五入）。
  // 先 toFixed(6) 清理浮点噪声，否则 9.1132 可能被表示成 9.1131999… 而误截成 9.1131。
  const truncated = Math.trunc(Number(abs.toFixed(6)) * 10000) / 10000;

  let frac = truncated.toFixed(4).split(".")[1]; // 固定 4 位
  let len = 4;
  while (len > 2 && frac[len - 1] === "0") len--; // 多余尾零截到至少 2 位
  frac = frac.slice(0, len);

  const intp = Math.trunc(truncated)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ",");

  return (neg ? "-" : "") + intp + (frac ? "." + frac : "");
}
