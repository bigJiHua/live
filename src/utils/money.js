// 最多截取 4 位小数，至少显示 2 位；外币折算的真实四位精度不能被舍入。
function moneyParts(value) {
  let text;
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return null;
    // 金额最多 10 位整数 + 4 位小数；15 位有效数字只清理 Number 的 IEEE 尾差。
    text = value.toPrecision(15);
  } else if (typeof value === 'string') {
    text = value.trim();
  } else return null;

  const numeric = Number(text);
  if (!Number.isFinite(numeric)) return null;
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(text);
  if (!match) return null;
  const whole = match[2] || '0';
  let digits = whole + (match[3] ?? match[4] ?? '');
  const leadingZeros = digits.match(/^0*/)[0].length;
  digits = digits.slice(leadingZeros);
  if (!digits) return { integer: '0', fraction: '00', negative: false };
  const position = whole.length + Number(match[5] || 0) - leadingZeros;
  let integer = '0';
  let fraction = '';
  // Extremely small exponents cannot contribute any of the four retained decimals.
  if (position > -4) {
    if (position <= 0) fraction = ('0'.repeat(-position) + digits.slice(0, 4)).slice(0, 4);
    else if (position >= digits.length) integer = digits + '0'.repeat(position - digits.length);
    else { integer = digits.slice(0, position); fraction = digits.slice(position, position + 4); }
  }
  fraction = fraction.padEnd(2, '0');
  while (fraction.length > 2 && fraction.endsWith('0')) fraction = fraction.slice(0, -1);
  return { integer, fraction, negative: numeric < 0 && (integer !== '0' || /[1-9]/.test(fraction)) };
}

function renderMoney(value, grouped) {
  const parts = moneyParts(value);
  if (!parts) return '0.00';
  const integer = grouped ? parts.integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : parts.integer;
  return (parts.negative ? '-' : '') + integer + '.' + parts.fraction;
}

export function formatMoney(value) { return renderMoney(value, true); }

// 表单填值不带千分位，不能把展示字符串再转换为入账金额。
export function formatMoneyInput(value) { return renderMoney(value, false); }
