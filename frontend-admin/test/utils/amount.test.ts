import { formatAmountFen } from '../../src/utils/amount';

/**
 * 金额格式化单元测试。
 *
 * 资金展示涉及整数分到元的转换，任何实现变更都必须保证：
 * 正数、负数、跨元进位、大额数值格式正确，且小数或超出安全整数的输入被拒绝。
 */
describe('formatAmountFen', () => {
  it.each([
    [0, '0.00'],
    [1, '0.01'],
    [105, '1.05'],
    [123456789, '1234567.89'],
    [-205, '-2.05'],
  ])('将整数分 %s 格式化为 %s', (amountFen, expected) => {
    expect(formatAmountFen(amountFen)).toBe(expected);
  });

  it('拒绝小数金额', () => {
    expect(() => formatAmountFen(1.5)).toThrow('金额必须是安全范围内的整数分');
  });

  it('拒绝超过 JavaScript 安全整数范围的金额', () => {
    expect(() => formatAmountFen(Number.MAX_SAFE_INTEGER + 1)).toThrow(
      '金额必须是安全范围内的整数分',
    );
  });
});
