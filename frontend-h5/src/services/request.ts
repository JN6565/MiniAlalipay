import axios from 'axios';
import { Toast } from 'antd-mobile';

// 创建axios实例 - 不设置baseURL，让请求通过前端代理
const request = axios.create({
  timeout: 10000,
});

// 生成UUID
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
    /[xy]/g,
    function (c) {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    },
  );
}

// 请求拦截器
request.interceptors.request.use(
  (config: any) => {
    // 添加请求ID
    config.headers = {
      ...config.headers,
      'X-Request-Id': generateUUID(),
    };

    // 为写操作添加幂等键
    if (config.method === 'post' || config.method === 'put' || config.method === 'patch') {
      config.headers = {
        ...config.headers,
        'Idempotency-Key': generateUUID(),
      };
    }

    // 为非登录/注册请求添加 Authorization 头和用户ID
    const url = config.url || '';
    if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
      const token = localStorage.getItem('accessToken');
      const userId = localStorage.getItem('userId');
      if (token) {
        config.headers = {
          ...config.headers,
          Authorization: `Bearer ${token}`,
        };
      }
      if (userId) {
        config.headers = {
          ...config.headers,
          'X-User-Id': userId,
        };
      }
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 响应拦截器
request.interceptors.response.use(
  (response: any) => {
    // 后端返回 ApiResponse 格式: { code, message, requestId, traceId, data }
    const { code, message, requestId, traceId, data } = response.data;

    // 成功响应：code 为 "OK" 或 HTTP 状态码 200/202
    if (code === 'OK' || code === 200 || code === 202) {
      return data;
    }

    // 业务错误处理
    switch (code) {
      case 'AUTH_REQUIRED':
      case 'LOGIN_INVALID':
      case 401:
        localStorage.removeItem('accessToken');
        window.location.href = '/h5/login';
        break;
      case 'LOGIN_LOCKED':
      case 'RATE_LIMITED':
      case 429:
        Toast.show({ content: '操作过于频繁，请稍后再试', icon: 'fail' });
        break;
      case 'ACCOUNT_NUMBER_EXISTS':
      case 'PHONE_NUMBER_EXISTS':
      case 409:
        Toast.show({ content: code === 'PHONE_NUMBER_EXISTS' ? '该手机号已注册' : '账户号已存在', icon: 'fail' });
        break;
      case 'PASSWORD_POLICY_VIOLATION':
      case 422:
        Toast.show({ content: '密码不符合安全规则', icon: 'fail' });
        break;
      case 'REGISTRATION_PROCESSING':
      case 202:
        Toast.show({ content: '注册开户处理中，请稍后再试', icon: 'fail' });
        break;
      default:
        Toast.show({ content: message || '请求失败', icon: 'fail' });
    }

    return Promise.reject(new Error(message));
  },
  (error: any) => {
    // 网络错误或 HTTP 错误状态码
    if (error.response) {
      const { status, data } = error.response;
      const message = data?.message || '请求失败';

      switch (status) {
        case 401:
          localStorage.removeItem('accessToken');
          window.location.href = '/h5/login';
          break;
        case 429:
          Toast.show({ content: '操作过于频繁，请稍后再试', icon: 'fail' });
          break;
        default:
          Toast.show({ content: message, icon: 'fail' });
      }
    } else {
      Toast.show({ content: '网络异常，请检查网络连接', icon: 'fail' });
    }

    return Promise.reject(error);
  },
);

export default request;
