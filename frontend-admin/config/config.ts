import { defineConfig } from '@umijs/max';
import routes from './routes';

export default defineConfig({
  title: 'MiniAlalipay 运营中心',
  routes,
  history: { type: 'hash' },
  hash: true,
  outputPath: 'dist',
  fastRefresh: true,
  mfsu: false,
  antd: {},
  initialState: {},
  access: {},
  request: {},
  model: {},
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    '/actuator': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
