import { defineConfig } from '@umijs/max';
import routes from './routes';
import { existsSync } from 'fs';
import { resolve } from 'path';

// 检查证书文件是否存在
const certDir = resolve(__dirname, '..');
const keyPath = resolve(certDir, 'localhost+1-key.pem');
const certPath = resolve(certDir, 'localhost+1.pem');
const hasHttps = existsSync(keyPath) && existsSync(certPath);

export default defineConfig({
  // 基础配置
  title: 'MiniAlalipay',
  favicons: ['/favicon.ico'],

  // 移动端 viewport 配置
  metas: [
    { name: 'viewport', content: 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover' },
  ],

  // 路由配置
  routes,

  // 代理配置（开发环境）
  // 注意：更具体的路径必须在前，否则会被 /api 通配路径先匹配
  proxy: {
    // AI 流式端点：SSE 流不能走有缓冲的通用代理
    '/api/v1/agent/messages/stream': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      // 提高代理稳定性，减少 ECONNRESET 错误
      timeout: 15000,
      proxyTimeout: 15000,
    },
  },

  // 构建配置
  hash: true,
  outputPath: 'dist',

  // 主题配置
  theme: {
    'brand-color': '#1677ff',
  },

  // 快速刷新
  fastRefresh: true,

  // 禁用MFSU以解决兼容性问题
  mfsu: false,

  // HTTPS配置 - 使用文件路径
  https: hasHttps ? { key: keyPath, cert: certPath } : undefined,
});
