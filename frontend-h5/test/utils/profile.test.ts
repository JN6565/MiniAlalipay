import {
  AVATAR_FILE_MAX_SIZE,
  formatAccountName,
  getProfilePreference,
  readAvatarFile,
  saveProfilePreference,
  seedNicknamePreference,
} from '../../src/utils/profile';

describe('formatAccountName', () => {
  test('生成当前登录用户的账户名展示文本', () => {
    expect(formatAccountName('6222000000000001')).toBe('账户名：6222000000000001');
  });

  test('账户名尚未返回时展示加载状态', () => {
    expect(formatAccountName('')).toBe('账户名：加载中...');
  });
});

describe('个人资料头像偏好', () => {
  beforeEach(() => {
    const values = new Map<string, string>();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
        removeItem: (key: string) => values.delete(key),
      },
    });
  });

  it('保存和读取本地图片头像', () => {
    saveProfilePreference({ nickname: '小林', avatarCode: 'CUSTOM', avatarDataUrl: 'data:image/png;base64,abc' });
    expect(getProfilePreference()).toEqual({
      nickname: '小林',
      avatarCode: 'CUSTOM',
      avatarDataUrl: 'data:image/png;base64,abc',
    });
  });

  it('切换为预设头像时清除已保存的本地图片', () => {
    saveProfilePreference({ nickname: '小林', avatarCode: 'CUSTOM', avatarDataUrl: 'data:image/png;base64,abc' });
    saveProfilePreference({ nickname: '小林', avatarCode: 'BLUE' });

    expect(getProfilePreference()).toEqual({
      nickname: '小林',
      avatarCode: 'BLUE',
      avatarDataUrl: undefined,
    });
  });

  it('拒绝不支持的格式和过大文件', async () => {
    const unsupportedFile = { type: 'image/gif', size: 1 } as File;
    const oversizedFile = { type: 'image/png', size: AVATAR_FILE_MAX_SIZE + 1 } as File;
    await expect(readAvatarFile(unsupportedFile)).rejects.toThrow('仅支持');
    await expect(
      readAvatarFile(oversizedFile),
    ).rejects.toThrow('不能超过');
  });
});

describe('昵称展示偏好的服务端初始化', () => {
  beforeEach(() => {
    const values = new Map<string, string>();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
        removeItem: (key: string) => values.delete(key),
      },
    });
  });

  it('浏览器尚无昵称时用服务端昵称初始化', () => {
    seedNicknamePreference('张三');
    expect(getProfilePreference().nickname).toBe('张三');
  });

  it('已有本地昵称时保留本地编辑，不被服务端昵称覆盖', () => {
    seedNicknamePreference('张三');
    seedNicknamePreference('李四');
    expect(getProfilePreference().nickname).toBe('张三');
  });

  it('服务端昵称缺失时使用回退值', () => {
    seedNicknamePreference(undefined, '账户123');
    expect(getProfilePreference().nickname).toBe('账户123');
  });
});
