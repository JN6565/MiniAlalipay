# 错误码契约

`error-codes.yaml` 是跨端错误码注册表。OpenAPI、后端枚举、前端错误归一化和验收测试只能使用已登记错误码；新增或改变 HTTP 映射时必须在同一次变更中更新该文件及对应 OpenAPI 操作。

错误响应统一包含 `code`、中文 `message`、`requestId`、`traceId` 和安全的 `data`。不得返回堆栈、SQL、内部地址、密码、支付证明、确认令牌或二维码原始令牌。领域错误码留在所属服务实现，`platform-common` 只维护 `COMMON_*` 技术通用码。

## Java 实现约束

- 每个服务必须在自己的代码模块中建立实现 `ErrorCode` 的错误码枚举，例如 `UserErrorCode`、`BusinessErrorCode`、`AccountErrorCode` 和 `AiErrorCode`；业务代码通过枚举抛出 `BusinessException`，不得使用字符串临时拼装错误码。
- 服务枚举只能实现本服务拥有的领域错误码。用户、账户、交易、信用和 AI 错误码不得加入 `platform-common`；`CommonErrorCode` 只允许实现 `COMMON_*` 技术通用码。
- 枚举的 `code`、中文 `message` 和 `httpStatus` 必须与 `error-codes.yaml` 完全一致。每个服务必须提供契约测试，逐项验证枚举在 YAML 中已登记且三个字段一致，并验证 YAML 中归属本服务的错误码没有遗漏实现。
- 新增、删除或修改错误码时，必须在同一次提交中更新 `error-codes.yaml`、所属服务枚举、OpenAPI 对应错误响应和契约测试。任何一项缺失或不一致都视为契约漂移，禁止合并。

Java 业务代码的标准调用方式如下：

```java
throw new BusinessException(
        BusinessErrorCode.IDEMPOTENCY_CONFLICT,
        Map.of("originalTransactionId", transactionId)
);
```

示例中的 `data` 只能包含 OpenAPI 已声明的安全冲突详情，不得放入聚合根、持久化对象或敏感数据。
