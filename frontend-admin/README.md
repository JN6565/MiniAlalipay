# B 端前端工程说明

本目录用于运营管理后台，使用 React、TypeScript 和 Umi 独立构建、部署。B 端只能通过网关访问后端接口，不得直接修改资金事实。

## 工程边界

- `config/` 负责 Umi 构建、开发代理和路由配置，不承载业务逻辑。
- `src/layouts/` 负责运营后台整体布局和菜单，不把菜单隐藏作为服务端权限校验的替代方案。
- `src/pages/` 负责路由级页面。每个 B 端业务路由拥有独立页面模块；OpenAPI 尚未定义时保留页面结构并禁用请求动作，不得猜测接口或复制后端 DTO。
- `src/services/` 负责网关请求、错误归一化和 OpenAPI 生成客户端，只能访问 `http://localhost:8080`。
- `src/wrappers/` 负责路由级权限守卫，在 `access` 过滤路由（404）之上补充明确的 403 反馈。
- `src/components/`、`src/utils/` 和 `src/stores/` 只放 B 端展示及客户端 UI 状态，不缓存余额、账本事实或交易终态。

## 视觉主题

B 端采用「晴空」浅蓝色主题，定位为清爽、安静、适合高频运营操作的后台观感：

- `src/theme.ts` 是 Ant Design 主题令牌的唯一来源，定义主色、状态色、背景、边框和组件级令牌。
- `src/global.less` 维护同源的 CSS 变量，供自定义布局和页面样式使用。
- 主色为低饱和度天空蓝 `#2f7ff2`，背景使用带蓝色倾向的浅中性色，并补充青绿、琥珀和珊瑚作为状态色，避免界面过于单一。
- 布局使用浅色侧栏、半透明顶栏和浅蓝内容底色；窄屏时根据断点自动收起侧栏，保证移动端不横向溢出。

## 首次使用

TypeScript 类型检查依赖 Umi 生成的临时文件，首次进入本目录或依赖发生变化后需要先完成以下步骤：

```bash
# 1. 安装依赖
npm install

# 2. 生成 .umi 临时类型（同时为 type-check 的前置步骤）
npm run setup
```

如果 IDE 的 TypeScript 语言服务报错找不到 `./src/.umi/tsconfig.json`，执行 `npm run setup` 即可恢复。该目录已在 `.gitignore` 中排除，不会被提交。

## 本地命令

### 启动

```bash
# 安装项目依赖，首次进入本目录或依赖发生变化后执行
npm install

# 启动本地开发服务器，默认访问 http://localhost:8000/#/admin/dashboard
npm run dev
```

### 提交前检查

```bash
# 生成 Umi 临时类型并执行 TypeScript 静态类型检查
npm run type-check

# 检查 TypeScript、React 和样式代码规范
npm run lint

# 执行生产环境构建，验证产物可正常生成
npm run build
```

本地开发地址默认为 [http://localhost:8000/#/admin/dashboard](http://localhost:8000/#/admin/dashboard)。使用 Hash 路由可以避免静态部署或本地刷新管理页面时返回 404。业务接口必须在 OpenAPI、后端 Controller 和测试同时落地后才能接入。
