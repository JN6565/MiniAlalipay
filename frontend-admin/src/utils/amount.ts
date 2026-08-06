/**
 * 金额展示工具。
 *
 * 整个系统约定金额一律使用整数分（amountFen）传输与计算，禁止使用浮点数表示金额。
 * 前端在展示边界才允许将整数分转换为元文本，且必须通过整数运算拆分，
 * 避免 `0.1 + 0.2` 这类 JavaScript 小数误差污染资金展示。
 */

/**
 * 将整数分格式化为两位小数的元展示文本。
 *
 * 只接受安全范围内的整数，任何小数或溢出值都直接抛错，从源头拦截错误金额进入展示层。
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
