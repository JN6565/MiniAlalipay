# MiniAlalipay API 网关

## 目录职责

作为 Web、H5（C 端）、运营后台（B 端）和 AI/MCP 外部调用的统一入口，本地基础地址为 `http://localhost:8080`。

## 核心能力

| 能力 | 说明 |
|---|---|
| 路由转发 | 根据路径前缀将请求路由到 user-center、business-center、account-center 或 ai-service |
| 身份认证 | Bearer Token 校验，白名单路径跳过，认证结果写入 `X-User-Id`/`X-User-Roles` 头 |
| CSRF 防护 | 对 Cookie 会话写请求校验 `X-CSRF-Token` 头 |
| 限流 | 按用户、IP 和业务动作维度实施速率限制 |
| 链路追踪 | 生成或透传 `X-Request-Id` 和 `X-Trace-Id` |
| 安全响应头 | 全局添加 nosniff/DENY/XSS-Protection/Referrer-Policy，二维码路径额外 no-store/no-referrer |
| CORS | 白名单化跨域来源，支持凭据传递 |
| 统一异常处理 | 下游错误码映射为统一 JSON 响应，中文错误消息 |
| 审计日志 | 认证拒绝、CSRF 拒绝、越权、限流触发等安全事件的结构化审计输出 |

## 技术栈

- Java 21，Spring Cloud Gateway，Reactor 响应式编程
- Nacos 服务发现（`lb://` 动态路由）
- Redis 限流（Lettuce 响应式客户端）
- 无业务数据库，不持有业务状态

## 过滤器执行顺序

| 顺序 | 过滤器 | 职责 |
|---|---|---|
| 0 | RequestIdGlobalFilter | 生成/透传请求编号和链路编号 |
| 10 | SecurityHeadersGlobalFilter | 添加安全响应头 |
| 20 | AuthenticationGlobalFilter | 身份认证与角色门禁 |
| 25 | CsrfGlobalFilter | CSRF Token 校验 |
| 30 | RequestRateLimiter | 速率限制 |
| 100 | AccessLogGlobalFilter | 脱敏访问日志 |

## 目录结构

```text
com.minialalipay.gateway/
├── GatewayApplication.java          # 启动入口
├── audit/                           # 审计日志
├── auth/                            # 认证端口与 Stub 适配器
├── config/                          # CORS、限流等配置
├── controller/                      # 健康检查
├── error/                           # 全局异常处理
└── filter/                          # 全局过滤器
```

## 边界约束

- 只依赖 `platform-common` 的技术通用类型
- 禁止引入 Web MVC、Servlet Filter 和阻塞式客户端
- 禁止访问任何业务数据库
- 禁止根据 HTTP 状态码推断资金交易结果
- 路由只为已合入 OpenAPI 契约且 Controller 已实现的操作开放
