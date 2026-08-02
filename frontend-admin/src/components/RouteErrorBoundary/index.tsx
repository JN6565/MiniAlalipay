import { Button, Result } from 'antd';
import { Component, type ErrorInfo, type ReactNode } from 'react';

export interface RouteErrorBoundaryProps {
  /** 受保护的路由页面。 */
  children: ReactNode;
}

interface RouteErrorBoundaryState {
  hasError: boolean;
}

/** 捕获路由渲染异常并提供可恢复的中文兜底页。 */
export default class RouteErrorBoundary extends Component<
  RouteErrorBoundaryProps,
  RouteErrorBoundaryState
> {
  state: RouteErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _errorInfo: ErrorInfo): void {
    // 正式前端监控契约落地后，仅上报脱敏后的错误摘要与请求编号。
  }

  render() {
    if (this.state.hasError) {
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
