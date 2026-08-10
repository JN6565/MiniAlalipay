/** @jest-environment jsdom */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import ProfilePage from '../../src/pages/h5/Profile';
import * as userService from '../../src/services/user';
import * as identityService from '../../src/services/identity';

jest.mock('../../src/services/user');
jest.mock('../../src/services/identity');
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));

const profile = (accountNumber: string) => ({
  userId: 'user-1',
  accountNumber,
  nickname: '测试用户',
  maskedPhone: '138****0000',
  createdAt: '2026-08-10T00:00:00Z',
});

describe('ProfilePage 账户名展示', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('nickname', '测试用户');
    localStorage.setItem('accountNumber', 'cached-account');
    jest.mocked(identityService.getIdentity).mockRejectedValue(new Error('身份接口不可用'));
  });

  test('进入页面后立即展示登录缓存中的账户名', () => {
    jest.mocked(userService.getMyInfo).mockReturnValue(new Promise<never>(() => {}));

    render(React.createElement(ProfilePage));

    expect(screen.getByText('账户名：cached-account')).toBeTruthy();
  });

  test('本人资料接口成功后刷新为服务端账户名', async () => {
    jest.mocked(userService.getMyInfo).mockResolvedValue(profile('server-account'));

    render(React.createElement(ProfilePage));

    expect(await screen.findByText('账户名：server-account')).toBeTruthy();
  });

  test('本人资料接口失败时保留登录缓存中的账户名', async () => {
    jest.mocked(userService.getMyInfo).mockRejectedValue(new Error('用户接口不可用'));

    render(React.createElement(ProfilePage));

    await waitFor(() => expect(userService.getMyInfo).toHaveBeenCalled());
    expect(screen.getByText('账户名：cached-account')).toBeTruthy();
  });
});
