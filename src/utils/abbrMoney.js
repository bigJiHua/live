// 金额缩写：超过 9999.999（即 >= 10000）自动转为「万」单位，保留 2 位小数
// 例如 12345 -> "1.23万"，8000 -> "8000"
export function abbrMoney(v) {
  const n = Number(v) || 0
  const abs = Math.abs(n)
  if (abs >= 10000) {
    return (n / 10000).toFixed(2) + '万'
  }
  return Number.isInteger(n) ? String(n) : (Math.round(n * 100) / 100).toString()
}
