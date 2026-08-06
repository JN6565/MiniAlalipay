import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { history, useModel } from '@umijs/max';
import PermissionGuard from '../../src/wrappers/PermissionGuard';

/**
 * 通用路由权限守卫单元测试。
 *
 * 守卫是全部业务守卫的公共底座，行为必须稳定：
 * 权限通过时通过 Outlet 渲染子路由页面且不跳转；权限不通过时不渲染并恰好跳转一次 403；
 * 身份仍在异步加载时既不渲染也不跳转。
 */

jest.mock('@umijs/max', () => ({
  history: {
    replace: jest.fn(),
  },
  useModel: jest.fn(),
  Outlet: require('react-router-dom').Outlet,
}));

const replaceMock = history.replace as jest.MockedFunction<typeof history.replace>;
const useModelMock = useModel as jest.MockedFunction<typeof useModel>;

function renderGuarded(allowed: boolean) {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route element={<PermissionGuard allowed={allowed} />}>
          <Route path="protected" element={<div>受保护页面</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('通用路由权限守卫', () => {
  beforeEach(() => {
    replaceMock.mockClear();
    // 默认身份已完成加载。
    useModelMock.mockReturnValue({ loading: false });
  });

  it('权限通过时通过 Outlet 渲染子路由页面且不跳转', () => {
    renderGuarded(true);

    expect(screen.getByText('受保护页面')).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('权限不通过时不渲染子路由页面并跳转 403', () => {
    renderGuarded(false);

    expect(screen.queryByText('受保护页面')).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledTimes(1);
    expect(replaceMock).toHaveBeenCalledWith('/admin/403');
  });

  it('身份仍在加载时不渲染子路由页面也不跳转 403', () => {
    useModelMock.mockReturnValue({ loading: true });
    renderGuarded(false);

    expect(screen.queryByText('受保护页面')).not.toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
