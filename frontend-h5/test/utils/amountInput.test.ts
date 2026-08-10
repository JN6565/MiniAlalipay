import { normalizeAmountInput } from '../../src/utils/amountInput';

describe('normalizeAmountInput', () => {
  test('清空输入时保持空字符串', () => {
    expect(normalizeAmountInput('')).toBe('');
  });

  test('保留输入中的小数点', () => {
    expect(normalizeAmountInput('12.')).toBe('12.');
  });

  test('限制金额最多输入两位小数', () => {
    expect(normalizeAmountInput('12.34')).toBe('12.34');
    expect(normalizeAmountInput('12.345')).toBe('12.34');
  });

  test('清理非数字字符和多余小数点', () => {
    expect(normalizeAmountInput('￥1a2.3.4')).toBe('12.34');
  });

  test('清理整数部分多余的前导零', () => {
    expect(normalizeAmountInput('00012.30')).toBe('12.30');
    expect(normalizeAmountInput('0.25')).toBe('0.25');
  });
});
