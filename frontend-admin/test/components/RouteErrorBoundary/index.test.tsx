import { render, screen } from '@testing-library/react';
import RouteErrorBoundary from '../../../src/components/RouteErrorBoundary/index';

/** 用于触发错误边界的测试页面：渲染期恒抛异常。 */
function BrokenPage(): never {
  throw new Error('测试页面渲染异常');
}

/**
 * 路由错误边界单元测试。
 *
 * 验证两件事：子组件渲染异常时展示中文兜底页；
 * 以及 key 绑定路径的恢复语义——同路径保持出错态，切换路径后恢复正常渲染。
 * 因为 jest 会把 React 捕获的异常打到 console.error，测试中需屏蔽以免污染输出。
 */
describe('路由错误边界', () => {
  let consoleErrorSpy: jest.SpyInstance;

  beforeEach(() => {
    consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('页面异常后显示中文兜底页', () => {
    render(
      <RouteErrorBoundary>
        <BrokenPage />
      </RouteErrorBoundary>,
    );

    expect(screen.getByText('页面加载失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '刷新重试' })).toBeInTheDocument();
  });

  it('同一路径保持兜底状态，切换路径后恢复页面渲染', () => {
    const { rerender } = render(
      <RouteErrorBoundary key="/admin/dashboard">
        <BrokenPage />
      </RouteErrorBoundary>,
    );

    // 同一 key 重渲染：错误边界实例未重建，即使子页面已“正常”，仍应保持兜底状态。
    rerender(
      <RouteErrorBoundary key="/admin/dashboard">
        <div>已恢复页面</div>
      </RouteErrorBoundary>,
    );

    expect(screen.getByText('页面加载失败')).toBeInTheDocument();
    expect(screen.queryByText('已恢复页面')).not.toBeInTheDocument();

    // 切换 key（对应路由跳转）：错误边界随 key 重建，恢复渲染新页面。
    rerender(
      <RouteErrorBoundary key="/admin/alerts">
        <div>已恢复页面</div>
      </RouteErrorBoundary>,
    );

    expect(screen.getByText('已恢复页面')).toBeInTheDocument();
    expect(screen.queryByText('页面加载失败')).not.toBeInTheDocument();
  });
});
