# 账户、账本与交易内核联调指南

## 1. 文档用途

本文说明其他成员如何调用王钧平负责的账户、账本和普通转账内核。接口字段和响应结构以
`contracts/openapi/minialalipay-api.yaml` 为事实来源；本文只说明调用关系，不定义第二套契约。

## 2. 调用边界

| 调用方 | 目标服务 | 允许调用的能力 |
|---|---|---|
| H5、B 端、AI 工具 | `gateway:8080` | 只调用 `/api/v1/**` 外部接口，不得直连业务服务 |
| `user-center` | `account-center:8083` | 注册开户和开户恢复 |
| `business-center` | `account-center:8083` | 账户解析、余额 TCC、账本 TCC、终态事实和对账差异 |
| 阶段五场景子域 | 同进程 `business-center` 应用层 | 复用统一交易、确认和 TCC 应用入口，不通过 HTTP 绕回自身服务 |

内部路径 `/internal/v1/**` 不配置网关路由。生产部署必须使用双向 TLS（双方同时校验证书）并按证书主体限制调用方；
当前仓库未提交证书、私钥和生产服务 ACL，本地联调使用受控开发网络中的 `http://localhost:8083`，不得把该端口暴露到公网。

## 3. 用户中心自动开户

用户中心创建 `PROVISIONING` 用户后调用：

```http
PUT http://localhost:8083/internal/v1/accounts/registrations/{registrationId}
Content-Type: application/json
X-Request-Id: registration-request-001
X-Trace-Id: 0123456789abcdef0123456789abcdef

{
  "userId": "01K1ABCDEFGHJKMNPQRSTVWXYZ"
}
```

接口以 `registrationId` 幂等创建或返回以下事实：

- 一个初始余额为 0 的 `PERSONAL/CNY` 账户和余额行。
- 一个用户余额账本科目。
- 一个固定额度 500000 分的信用账户。
- 一条初始金额为 0 的信用应收记录。

重复调用必须返回同一账户。用户中心只有收到成功响应并核验资源后，才能将用户从
`PROVISIONING` CAS 更新为 `ACTIVE`；失败时保持不可登录并由恢复任务使用同一 `registrationId` 重试。

## 4. 业务中心账户解析

创建转账草稿或其他资金来源对象时，业务中心通过以下接口解析账户：

```http
GET http://localhost:8083/internal/v1/accounts/by-user/{userId}
```

响应只包含 `accountId`、`userId` 和账户状态，不返回余额。余额不足只能在账户中心资金入口中判断，
业务中心不得读取数据库或缓存余额后自行决定扣款。

## 5. 普通转账 TCC

余额分支：

```http
POST http://localhost:8083/internal/v1/tcc/balance/{role}/{action}
```

`role` 只允许 `payer/payee`，`action` 只允许 `try/confirm/cancel`。相同
`xid + role + accountId` 的重试必须携带相同 `transactionId` 和 `amountFen`，否则返回幂等冲突。

账本分支：

```http
POST http://localhost:8083/internal/v1/tcc/ledger/{action}
```

Try 创建借贷平衡的 `PREPARED` 凭证，Confirm 验平后过账并写账本 Outbox，Cancel 只取消尚未过账的凭证。
调用方必须在所有重试中复用相同的凭证 ID、分录 ID 和事件 ID。

## 6. 终态核验与对账

TCC Confirm 或 Cancel 调用完成后，业务中心必须查询：

```http
GET http://localhost:8083/internal/v1/transaction-facts/{transactionId}
```

只有 `successConsistent=true` 才能发布 `SUCCESS`；只有 `cancelConsistent=true` 才能发布 `CANCELLED`。
事实不一致时调用：

```http
POST http://localhost:8083/internal/v1/reconciliation-diffs
```

差异接口只追加脱敏证据，不直接修改余额或历史账本。无法自动收敛的交易进入 `MANUAL_REVIEW`。

## 7. 外部账户与普通转账接口

前端和 AI 工具统一通过 `http://localhost:8080` 网关调用：

- `GET /api/v1/accounts/me`：本人账户和实时余额。
- `GET /api/v1/accounts/me/entries`：本人不可变账本明细。
- `/api/v1/transfer-drafts/**`：普通转账草稿创建、编辑、校验和查询。
- `POST /api/v1/confirmations`：签发一次性确认令牌。
- `/api/v1/transfers/**`：提交转账、查询权威状态和确定终态回执。

## 8. 后续场景接入限制

充值、扫码、C2C、信用支付、信用还款和退款不能直接调用普通转账 Controller，也不能直接调用
账户中心 TCC Controller 拼装资金流程。它们需要由各自场景负责人在 `business-center` 内调用统一交易应用入口，
并由交易内核按业务类型选择正确的账户、信用、发行权益、退款和账本参与者。

当前普通转账 TCC 的账本类型固定为 `TRANSFER`。在各业务类型的参与者和终态事实契约冻结前，禁止提供一个
看似通用但会写错凭证类型的“通用扣款接口”。新增场景资金能力时必须同步更新系统分析、OpenAPI、错误码和测试。
