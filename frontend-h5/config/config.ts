import { defineConfig } from '@umijs/max';
import routes from './routes';

export default defineConfig({
  // 基础配置
  title: 'MiniAlalipay',
  favicons: ['/favicon.ico'],

  // 路由配置
  routes,

  // 代理配置（开发环境）
  proxy: {
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
