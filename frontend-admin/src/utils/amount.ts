/**
 * 将整数分格式化为两位小数的元展示文本。
 *
 * 此函数只用于展示边界，并通过整数拆分避免 JavaScript 小数参与资金计算。
 */
export function formatAmountFen(amountFen: number): string {
  if (!Number.isSafeInteger(amountFen)) {
    throw new TypeError('金额必须是安全范围内的整数分');
  }

  const sign = amountFen < 0 ? '-' : '';
  const absoluteFen = Math.abs(amountFen);
  const yuan = Math.floor(absoluteFen / 100);
  const fen = String(absoluteFen % 100).padStart(2, '0');

  return `${sign}${yuan}.${fen}`;
}
