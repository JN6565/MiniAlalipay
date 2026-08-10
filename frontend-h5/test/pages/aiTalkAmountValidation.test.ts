import {
  MAX_TRANSFER_AMOUNT_FEN,
  getTransferAmountError,
  parseYuanToFen,
} from '../../src/pages/h5/AITalk/utils/transferAmountValidation';

describe('AI 转账确认卡片金额校验', () => {
  test('使用整数分解析元金额，避免小数金额参与业务计算', () => {
    expect(parseYuanToFen('50000')).toBe(5_000_000);
    expect(parseYuanToFen('12.3')).toBe(1_230);
    expect(parseYuanToFen('0.01')).toBe(1);
    expect(parseYuanToFen('12.345')).toBeNull();
  });

  test('超过五万元时阻止确认', () => {
    expect(getTransferAmountError(MAX_TRANSFER_AMOUNT_FEN + 1, 10_000_000))
      .toBe('单笔转账金额不能超过 50,000.00 元');
  });

  test('超过可用余额时阻止确认', () => {
    expect(getTransferAmountError(10_001, 10_000))
      .toBe('转账金额不能超过当前可用余额');
  });

  test('金额等于余额且不超过五万元时允许确认', () => {
    expect(getTransferAmountError(10_000, 10_000)).toBeNull();
  });
});
