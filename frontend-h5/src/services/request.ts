import axios from 'axios';

/** 前端统一 API 错误，页面根据业务错误码决定提示内容。 */
export class ApiError extends Error {
  constructor(public code: string, message: string, public requestId?: string, public status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

export const clearSession = () => {
  ['accessToken', 'userId', 'accountNumber', 'nickname', 'userType', 'session-storage']
    .forEach((key) => localStorage.removeItem(key));
};

const request = axios.create({ timeout: 10000 });
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
  config.headers = { ...config.headers, 'X-Request-Id': generateUUID() };
  if (['post', 'put', 'patch'].includes(config.method || '')) {
    config.headers['Idempotency-Key'] = generateUUID();
  }
  const url = config.url || '';
  if (!isPublicAuthEndpoint(url)) {
    const token = localStorage.getItem('accessToken');
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use((response: any) => {
  const { code, message, requestId, data } = response.data;
  if (code === 'OK' || code === 200 || code === 202) return data;
  handleExpiredSession(String(code));
  return Promise.reject(new ApiError(String(code), message || '请求失败', requestId, response.status));
}, (error: any) => {
  if (!error.response) return Promise.reject(new ApiError('NETWORK_ERROR', '网络异常，请检查网络连接'));
  const { status, data } = error.response;
  const code = data?.code || (status === 401 ? 'COMMON_UNAUTHORIZED' : `HTTP_${status}`);
  handleExpiredSession(code);
  return Promise.reject(new ApiError(code, data?.message || '请求失败', data?.requestId, status));
});

export default request;
