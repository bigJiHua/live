// 货币换算工具
// 汇率约定（与前端 exchangedAmount、清算中心一致）：
//   rate = "100 外币 = rate 人民币"，即 1 外币 = rate / 100 人民币
// 入账时按交易汇率冻结折算，CNY 原值返回。

function toCNY(amount, currency, exchangeRate) {
  const amt = parseFloat(amount) || 0;
  if (!currency || currency === "CNY") {
    // 人民币按“分”取整（货币最小单位），属正常口径而非随意四舍五入
    return Math.round(amt * 100) / 100;
  }
  const rate = parseFloat(exchangeRate) || 1;
  // 外币折算：保留真实精度，不做四舍五入（如 1 GBP @ 911.3200 = 9.1132 元）。
  // 截断到 4 位小数，规避浮点误差：9.1132 会被浮点表示成 9.1131999…，
  // 若直接 Math.trunc 会误截成 9.1131，故先 toFixed(6) 清理噪声再截断。
  const cny = (amt * rate) / 100;
  const cleaned = Number(cny.toFixed(6));
  return Math.trunc(cleaned * 10000) / 10000;
}

// 金额格式化（用于展示/拼接文案）：截断到 4 位小数、最少 2 位、千分位、不四舍五入。
// 例：1234.5 → "1,234.50"；9.1132 → "9.1132"。
function formatCNY(amount) {
  const n = parseFloat(amount);
  if (isNaN(n)) return "0.00";
  const truncated = Math.trunc(Number(n.toFixed(6)) * 10000) / 10000;
  let s = Math.abs(truncated).toString();
  if (!s.includes(".")) s += ".00";
  else {
    const parts = s.split(".");
    while (parts[1].length < 2) parts[1] += "0";
    s = parts.join(".");
  }
  s = s.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return (truncated < 0 ? "-" : "") + s;
}

module.exports = { toCNY, formatCNY };
