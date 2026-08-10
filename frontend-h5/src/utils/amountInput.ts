/**
 * 规范化金额输入，只保留数字和一个小数点，并截取两位小数。
 *
 * 保留末尾小数点，避免受控输入在用户输入 `12.` 时立即回退为 `12`。
 */
export const normalizeAmountInput = (value: string): string => {
  const cleaned = value.replace(/[^\d.]/g, '');
  if (!cleaned) return '';

  const [integerPart, ...decimalParts] = cleaned.split('.');
  const normalizedInteger = integerPart.replace(/^0+(?=\d)/, '') || '0';

  if (decimalParts.length === 0) return normalizedInteger;

  const decimalPart = decimalParts.join('').slice(0, 2);
  return `${normalizedInteger}.${decimalPart}`;
};
