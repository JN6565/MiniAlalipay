import { clearSession } from '../../src/services/request';

describe('clearSession 清除会话数据', () => {
  const values = new Map<string, string>();

  beforeEach(() => {
    values.clear();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
        removeItem: (key: string) => values.delete(key),
      },
    });
  });

  it('只清除登录会话与账户身份，保留浏览器本地的昵称与头像展示偏好', () => {
    ['accessToken', 'userId', 'accountNumber', 'nickname', 'avatarCode', 'avatarDataUrl',
     'userType', 'session-storage', 'ai_session_id', 'ai_session_expiry']
      .forEach((key) => values.set(key, `value-${key}`));

    clearSession();

    ['accessToken', 'userId', 'accountNumber', 'userType', 'session-storage',
     'ai_session_id', 'ai_session_expiry']
      .forEach((key) => expect(localStorage.getItem(key)).toBeNull());

    // 展示偏好仅保存在当前浏览器，退出后重新登录仍应继续显示已设置的头像与昵称
    expect(localStorage.getItem('nickname')).toBe('value-nickname');
    expect(localStorage.getItem('avatarCode')).toBe('value-avatarCode');
    expect(localStorage.getItem('avatarDataUrl')).toBe('value-avatarDataUrl');
  });
});
