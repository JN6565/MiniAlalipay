import { getInitialState } from '../src/app';
import { getCurrentIdentity } from '@/services/auth';
import { DEV_STUB_TOKEN, readStoredToken, setActiveToken } from '@/utils/adminToken';

/**
 * B 端运行时初始身份（app.tsx getInitialState）单元测试。
 *
 * 事实基准：身份只能由网关 /api/v1/auth/me 认证后下发，客户端不得在未认证时伪造身份——
 * 回归保护「开发环境未启用网关 dev Stub 时，直接访问链接必须落在登录页」。
 */

jest.mock('@/services/auth', () => ({
  getCurrentIdentity: jest.fn(),
}));

jest.mock('@/utils/adminToken', () => ({
  readStoredToken: jest.fn(),
  setActiveToken: jest.fn(),
  getActiveToken: jest.fn(),
  clearToken: jest.fn(),
  rememberToken: jest.fn(),
  DEV_STUB_TOKEN: 'dev-admin-token',
}));

const getCurrentIdentityMock = getCurrentIdentity as unknown as jest.Mock;
const readStoredTokenMock = readStoredToken as unknown as jest.Mock;
const setActiveTokenMock = setActiveToken as unknown as jest.Mock;

/** 修改 NODE_ENV 以便覆盖 getInitialState 的开发分支，测试后恢复。 */
const devEnv = () => (process.env as { NODE_ENV?: string }).NODE_ENV = 'development';
const prodEnv = () => (process.env as { NODE_ENV?: string }).NODE_ENV = 'test';

describe('B 端初始身份加载', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    prodEnv();
    readStoredTokenMock.mockReturnValue(null);
  });

  afterEach(() => {
    prodEnv();
  });

  it('持久化真实令牌有效时返回服务端下发的身份与角色', async () => {
    readStoredTokenMock.mockReturnValue('real-token');
    getCurrentIdentityMock.mockResolvedValue({
      data: { userId: 'u1', displayName: '管理员', roles: ['ADMIN', 'USER'] },
    });

    const state = await getInitialState();

    expect(setActiveTokenMock).toHaveBeenCalledWith('real-token');
    expect(state.currentAdmin?.userId).toBe('u1');
    expect(state.currentAdmin?.displayName).toBe('管理员');
    expect(state.currentAdmin?.roles).toEqual(['ADMIN', 'USER']);
  });

  it('开发环境未启用网关 dev Stub 时不授予任何本地身份（须落入登录页）', async () => {
    devEnv();
    getCurrentIdentityMock.mockRejectedValue(new Error('401 未认证'));

    const state = await getInitialState();

    expect(setActiveTokenMock).toHaveBeenCalledWith(DEV_STUB_TOKEN);
    expect(state.currentAdmin).toBeUndefined();
  });

  it('开发环境启用网关 dev Stub 时由 /auth/me 下发受控身份', async () => {
    devEnv();
    getCurrentIdentityMock.mockResolvedValue({
      data: { userId: 'dev', displayName: '开发运营', roles: ['ADMIN'] },
    });

    const state = await getInitialState();

    expect(setActiveTokenMock).toHaveBeenCalledWith(DEV_STUB_TOKEN);
    expect(state.currentAdmin?.userId).toBe('dev');
    expect(state.currentAdmin?.roles).toEqual(['ADMIN']);
  });

  it('生产构建未登录时返回空身份', async () => {
    prodEnv();

    const state = await getInitialState();

    expect(setActiveTokenMock).not.toHaveBeenCalled();
    expect(state.currentAdmin).toBeUndefined();
  });
});
