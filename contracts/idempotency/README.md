# 幂等键契约

## 请求规则

- `Idempotency-Key` 只能由 `A-Z`、`a-z`、`0-9`、`.`、`_`、`:`、`-` 组成，长度为 16 至 64；禁止放入用户信息、密码或业务明文。
- 作用域固定为 `(principalKey, apiScope, idempotencyKey)`，其中 `apiScope` 使用 OpenAPI `operationId`，同一主体同一操作至少保留 24 小时。
- 请求摘要使用规范化方法、路径和业务请求体计算；不包含 `Authorization`、Cookie、`X-Request-Id`、`traceId` 等传输字段。
- 同键同摘要：`COMPLETED` 返回首次响应，`PROCESSING` 返回原资源和处理中状态，允许恢复任务接管超时记录。
- 同键不同摘要：返回 HTTP 409 和 `IDEMPOTENCY_CONFLICT`，不得覆盖首次请求或创建新业务资源。
- 业务资源、响应摘要和幂等记录 `COMPLETED` 必须在资源所有者的同一本地事务提交；资金操作还必须叠加来源唯一键、状态 CAS 和 TCC 屏障。

## 主体规则

- 登录接口使用服务端解析的 `userId`；不得信任请求体内的用户或账户 ID。
- 匿名注册使用 `HMAC-SHA-256(idempotencySecret, normalize(loginName))`，不得把登录名明文写入幂等表。
- 匿名令牌交换使用 bootstrap 会话 ID，并按同一浏览器会话恢复；不同会话不得获取已绑定订单详情。

## 日志与响应

日志仅允许记录幂等键摘要、`apiScope`、状态、资源 ID、`requestId` 和 `traceId`。不得记录请求中的密码、支付证明、确认令牌或二维码原始令牌。
