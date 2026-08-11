import axios from 'axios';
import { friendlyNetworkError, isRetryableRequest, slowRequestTracker } from './networkFeedback';

/** 前端统一 API 错误，页面根据业务错误码决定提示内容。 */
export class ApiError extends Error {
  constructor(public code: string, message: string, public requestId?: string, public status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * 清除会话数据：仅移除登录态与账户身份，保留浏览器本地的展示偏好（昵称、头像）。
 * 展示偏好按系统分析第 25 节仅保存在当前浏览器、不上传服务端，退出后重新登录需继续可见。
 */
export const clearSession = () => {
  ['accessToken', 'userId', 'accountNumber', 'userType', 'session-storage',
   'ai_session_id', 'ai_session_expiry']
    .forEach((key) => localStorage.removeItem(key));
};

const request = axios.create({ timeout: 15000 });
const SLOW_REQUEST_THRESHOLD_MS = 1200;

interface SlowRequestState {
  timer: ReturnType<typeof setTimeout>;
  visible: boolean;
}

const startSlowRequestTracking = (config: any) => {
  const state: SlowRequestState = {
    visible: false,
    timer: setTimeout(() => {
      state.visible = true;
      slowRequestTracker.start();
    }, SLOW_REQUEST_THRESHOLD_MS),
  };
  config.slowRequestState = state;
};

const finishSlowRequestTracking = (config?: any) => {
  const state = config?.slowRequestState as SlowRequestState | undefined;
  if (!state) return;
  clearTimeout(state.timer);
  if (state.visible) slowRequestTracker.finish();
};
const hasIdempotencyKey = (config?: any) => {
  const headers = config?.headers;
  return Boolean(headers?.get?.('Idempotency-Key')
    || headers?.['Idempotency-Key']
    || headers?.['idempotency-key']);
};
const canRetry = (config?: any) => isRetryableRequest(config?.method, hasIdempotencyKey(config));
const generateUUID = () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
  const r = (Math.random() * 16) | 0;
  return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
});
const isPublicAuthEndpoint = (url: string) => {
  const path = url.split('?')[0];
  return path === '/api/v1/auth/login' || path === '/api/v1/auth/register';
};
const handleExpiredSession = (code?: string) => {
  if (code !== 'AUTH_REQUIRED' && code !== 'COMMON_UNAUTHORIZED') return;
  clearSession();
  if (window.location.pathname !== '/h5/login') window.location.replace('/h5/login');
};

request.interceptors.request.use((config: any) => {
  startSlowRequestTracking(config);
  config.headers = { ...config.headers, 'X-Request-Id': generateUUID() };
  // Idempotency-Key 由各服务自行管理，不在这里自动生成
  const url = config.url || '';
  if (!isPublicAuthEndpoint(url)) {
    const token = localStorage.getItem('accessToken');
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use((response: any) => {
  finishSlowRequestTracking(response.config);
  // 处理 204 No Content 响应（如 bootstrap 接口）
  if (response.status === 204) {
    return undefined;
  }

  const { code, message, requestId, data } = response.data;
  if (code === 'OK' || code === 200 || code === 202) return data;
  handleExpiredSession(String(code));
  return Promise.reject(new ApiError(String(code), message || '请求失败', requestId, response.status));
}, async (error: any) => {
  const config = error.config;
  finishSlowRequestTracking(config);

  if (!error.response) {
    // 网络错误最多重试 3 次，并使用指数退避避免弱网时瞬间放大请求量。
    if (config && canRetry(config)) {
      config.__retryCount = (config.__retryCount || 0) + 1;
      if (config.__retryCount <= 3) {
        const delay = Math.min(1000 * Math.pow(2, config.__retryCount - 1), 4000);
        await new Promise(resolve => setTimeout(resolve, delay));
        return request(config);
      }
    }
    const code = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
      ? 'NETWORK_TIMEOUT'
      : 'NETWORK_ERROR';
    const message = friendlyNetworkError(error.code) || '当前网络连接异常，请检查网络后重试';
    return Promise.reject(new ApiError(code, message));
  }

  const { status, data } = error.response;
  // 网关错误（502/503/504）最多重试 3 次，并使用指数退避。
  if (status >= 502 && status <= 504 && config && canRetry(config)) {
    config.__retryCount = (config.__retryCount || 0) + 1;
    if (config.__retryCount <= 3) {
      const delay = Math.min(1000 * Math.pow(2, config.__retryCount - 1), 4000);
      await new Promise(resolve => setTimeout(resolve, delay));
      return request(config);
    }
  }
  const code = data?.code || (status === 401 ? 'COMMON_UNAUTHORIZED' : `HTTP_${status}`);
  handleExpiredSession(code);
  const message = friendlyNetworkError(error.code, status) || data?.message || '请求失败';
  return Promise.reject(new ApiError(code, message, data?.requestId, status));
});

export default request;
