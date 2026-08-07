import { defineConfig } from '@umijs/max';
import routes from './routes';

export default defineConfig({
  // 基础配置
  title: 'MiniAlalipay',
  favicons: ['/favicon.ico'],

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
});
