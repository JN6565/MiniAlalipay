import { formatAmountFen } from './amount';

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
