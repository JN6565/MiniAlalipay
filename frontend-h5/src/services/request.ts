import axios from 'axios';
import { Toast } from 'antd-mobile';

// 创建axios实例
const request = axios.create({
  baseURL: 'http://localhost:8080',
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
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers = {
        ...config.headers,
        Authorization: `Bearer ${token}`,
      };
    }

    // 添加请求ID
    config.headers = {
      ...config.headers,
      'X-Request-Id': generateUUID(),
    };

    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// 响应拦截器
request.interceptors.response.use(
  (response: any) => {
    const { code, message, data } = response.data;

    if (code === 0 || code === 200) {
      return data;
    }

    // 业务错误处理
    switch (code) {
      case 401:
        localStorage.removeItem('accessToken');
        window.location.href = '/h5/login';
        break;
      case 429:
        Toast.show({ content: '操作过于频繁，请稍后再试', icon: 'fail' });
        break;
      default:
        Toast.show({ content: message || '请求失败', icon: 'fail' });
    }

    return Promise.reject(new Error(message));
  },
  (error: any) => {
    Toast.show({ content: '网络异常，请检查网络连接', icon: 'fail' });
    return Promise.reject(error);
  },
);

export default request;
