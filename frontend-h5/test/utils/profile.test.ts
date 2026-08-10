import { formatAccountName } from '../../src/utils/profile';

describe('formatAccountName', () => {
  test('生成当前登录用户的账户名展示文本', () => {
    expect(formatAccountName('6222000000000001')).toBe('账户名：6222000000000001');
  });

  test('账户名尚未返回时展示加载状态', () => {
    expect(formatAccountName('')).toBe('账户名：加载中...');
  });
});
