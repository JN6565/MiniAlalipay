import { Button, Result } from 'antd';
import { Component, type ErrorInfo, type ReactNode } from 'react';

export interface RouteErrorBoundaryProps {
  /** 受保护的路由页面。 */
  children: ReactNode;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
}

/**
 * 路由渲染错误边界。
 *
 * 路由页面在渲染期抛出的异常（如接口数据结构变更导致的空值访问）不应让整个后台白屏，
 * 因此用 React 错误边界捕获，并提供可恢复的中文兜底页。
 * 依赖父组件（AdminLayout）为每条路由传入 key，路径切换时自动重置错误状态、恢复正常渲染。
 */
export default class RouteErrorBoundary extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false };

  /** 渲染阶段捕获到子组件异常时置为出错状态，触发兜底 UI。 */
  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _errorInfo: ErrorInfo): void {
    // 正式前端监控契约落地后，仅上报脱敏后的错误摘要与请求编号，不落盘原始堆栈中的敏感数据。
  }

  render() {
    if (this.state.hasError) {
      // 兜底页提供整页刷新入口；刷新会重新进入路由并重建组件树，从而尝试恢复页面。
      return (
        <Result
          status="500"
          title="页面加载失败"
          subTitle="页面渲染发生异常，请刷新后重试。"
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              刷新重试
            </Button>
          }
        />
      );
    }

    return this.props.children;
  }
}
