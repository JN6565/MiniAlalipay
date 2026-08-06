import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { history, useAccess } from '@umijs/max';
import DemoTasksGuard from '../../src/wrappers/DemoTasksGuard';
import UserManagementGuard from '../../src/wrappers/UserManagementGuard';

/**
 * 业务权限守卫单元测试。
 *
 * 覆盖 DemoTasksGuard 与 UserManagementGuard 两个基于 PermissionGuard 封装的守卫：
 * 具备权限时通过 Outlet 渲染子路由页面且不跳转，无权限时不渲染并跳转 403。
 * 通过 jest.mock 隔离 Umi 的 history/useAccess/useModel，用 MemoryRouter 提供路由上下文。
 */

jest.mock('@umijs/max', () => ({
  history: {
    replace: jest.fn(),
  },
  useAccess: jest.fn(),
  useModel: jest.fn(() => ({ loading: false })),
  Outlet: require('react-router-dom').Outlet,
}));

const useAccessMock = useAccess as unknown as jest.Mock;
const replaceMock = history.replace as jest.MockedFunction<typeof history.replace>;

function renderGuarded(guard: React.ReactNode, pageText: string) {
  render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route element={guard}>
          <Route path="protected" element={<div>{pageText}</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('演示任务权限守卫', () => {
  beforeEach(() => {
    useAccessMock.mockReset();
    replaceMock.mockClear();
  });

  it('具备演示任务权限时渲染子路由页面且不跳转', () => {
    useAccessMock.mockReturnValue({ canRunDemoTasks: true });

    renderGuarded(<DemoTasksGuard />, '演示任务页');

    expect(screen.getByText('演示任务页')).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('不具备演示任务权限时不渲染页面并跳转 403', () => {
    useAccessMock.mockReturnValue({ canRunDemoTasks: false });

    renderGuarded(<DemoTasksGuard />, '演示任务页');

    expect(screen.queryByText('演示任务页')).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith('/admin/403');
  });
});

describe('用户管理权限守卫', () => {
  beforeEach(() => {
    useAccessMock.mockReset();
    replaceMock.mockClear();
  });

  it('具备用户管理权限时渲染子路由页面且不跳转', () => {
    useAccessMock.mockReturnValue({ canManageUsers: true });

    renderGuarded(<UserManagementGuard />, '用户管理页');

    expect(screen.getByText('用户管理页')).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it('不具备用户管理权限时不渲染页面并跳转 403', () => {
    useAccessMock.mockReturnValue({ canManageUsers: false });

    renderGuarded(<UserManagementGuard />, '用户管理页');

    expect(screen.queryByText('用户管理页')).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenCalledWith('/admin/403');
  });
});
