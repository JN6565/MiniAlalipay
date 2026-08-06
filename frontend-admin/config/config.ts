import { defineConfig } from '@umijs/max';
import routes from './routes';

/**
 * Umi 构建与开发配置。
 *
 * 关键点：
 * - 使用 Hash 路由（history.type = 'hash'），静态部署或本地刷新管理页面不会返回 404；
 * - 开发代理将 /api 与 /actuator 指向本地网关 8080，B 端一切请求只经网关转发，
 *   禁止直连 8081~8084 服务端口或 MySQL、Redis；
 * - 启用 Umi 内置插件：antd 主题、initialState（权限初始状态）、access（界面权限）、request、model。
 */
export default defineConfig({
  title: 'MiniAlalipay 运营中心',
  routes,
  history: { type: 'hash' },
  hash: true,
  outputPath: 'dist',
  fastRefresh: true,
  mfsu: false,
  // 路由级拆包由 Umi 4 默认按路由异步 chunk 启用，每个页面独立加载，无需额外配置键；
  // 多个异步 chunk 共享 esbuild helper，开启 IIFE 最小化避免 helper 命名冲突。
  esbuildMinifyIIFE: true,
  antd: {},
  initialState: {},
  access: {},
  request: {},
  model: {},
  // 开发代理：仅放行网关公开前缀，同源访问经此处转发到本地网关 8080。
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
