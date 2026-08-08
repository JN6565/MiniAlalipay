/** 弱网提示文案，避免把网络等待误报成业务服务故障。 */
export const SLOW_NETWORK_MESSAGE = '当前网络环境较慢，正在加载数据，请耐心等待';

type Listener = (visible: boolean) => void;

/** 跟踪正在等待的慢请求，供全局提示条使用。 */
export class SlowRequestTracker {
  private pending = 0;
  private readonly listeners = new Set<Listener>();

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    listener(this.pending > 0);
    return () => {
      this.listeners.delete(listener);
    };
  }

  start() {
    const wasVisible = this.pending > 0;
    this.pending += 1;
    if (!wasVisible) this.emit(true);
  }

  finish() {
    const wasVisible = this.pending > 0;
    this.pending = Math.max(0, this.pending - 1);
    if (wasVisible && this.pending === 0) this.emit(false);
  }

  private emit(visible: boolean) {
    this.listeners.forEach((listener) => listener(visible));
  }
}

/** 将网络层错误转换为用户可理解的提示；普通业务错误返回 undefined。 */
export const friendlyNetworkError = (errorCode?: string, status?: number) => {
  if (errorCode === 'ECONNABORTED' || errorCode === 'ETIMEDOUT' || errorCode === 'NETWORK_TIMEOUT' || errorCode === 'NETWORK_ERROR') {
    return '当前网络环境较差，数据暂未返回，请稍后重试';
  }
  if (status === 502 || status === 503 || status === 504) {
    return '当前网络环境较差或响应较慢，请稍后重试';
  }
  return undefined;
};

/** 判断请求是否允许自动重试，避免无幂等保障的写操作被重复执行。 */
export const isRetryableRequest = (method?: string, hasIdempotencyKey = false) => {
  const normalizedMethod = (method || 'GET').toUpperCase();
  return ['GET', 'HEAD', 'OPTIONS'].includes(normalizedMethod) || hasIdempotencyKey;
};

export const slowRequestTracker = new SlowRequestTracker();
