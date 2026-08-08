# 动态扫码 Mini 花呗支付接入实施计划

> **For agentic workers:** 按任务逐项执行并在每项完成后运行对应测试。

**目标：** 打通动态扫码订单的 `BALANCE`/`MINI_CREDIT` 明确选源支付闭环，并保持额度、应收、余额、账本和交易状态一致。

**架构：** 扩展现有 `QrPayApplicationService`，让确认令牌绑定并推导资金来源；统一交易按 `QR_PAY` 或 `CREDIT_PAY` 分派。新增账户中心只读信用资格预检适配，信用 TCC 复用现有参与者和信用账本接口。

**技术栈：** Java 21、Spring Boot、MyBatis、Seata TCC、OpenAPI、React + TypeScript + Umi、Axios。

## 全局约束

- Mini 花呗只允许用于另一普通用户创建的动态扫码订单。
- 金额只使用整数 `amountFen`；支付密码、证明令牌和二维码原始令牌不得写日志或浏览器存储。
- 前端只访问网关 `http://localhost:8080`，写操作携带服务端幂等键和 `X-Request-Id`。
- `account-center` 是唯一余额、额度、应收和账本写入方；新增说明和注释使用中文。

### 任务 1：补齐信用资格预检端口与契约

**文件：** `contracts/openapi/minialalipay-api.yaml`；`backend/account-center/src/main/java/com/minialalipay/account/interfaces/credit/InternalCreditAccountDirectoryController.java`；新增对应 DTO、领域方法和测试。

- [ ] 先为 `POST /internal/v1/credit-accounts/{creditAccountId}/eligibility` 编写契约测试：ACTIVE、SUSPENDED、逾期、额度不足分别返回成功或现有信用错误码。
- [ ] 在账户中心实现只读资格校验，使用 `CreditAccount.allowsCreditPay()`、应收逾期值和 `availableFen`，不返回余额/额度明细，只返回 `{eligible, version}`。
- [ ] 在 OpenAPI 同步请求/响应、错误响应和内部调用说明，并为服务错误码枚举增加逐项契约校验。
- [ ] 运行 `mvn -pl account-center -am -Dtest='*Credit*ContractTest' test`。
- [ ] 提交：`feat(credit): 增加动态扫码信用资格预检`。

### 任务 2：扩展业务中心动态扫码确认与交易受理

**文件：** `backend/business-center/src/main/java/com/minialalipay/business/application/port/` 新增信用目录端口与适配器；`QrPayApplicationService.java`；`QrPayController.java`；确认持久化/领域测试。

- [ ] 先增加服务测试，断言 `MINI_CREDIT` 不再被拒绝、确认摘要可区分资金来源、同源订单只生成一笔交易。
- [ ] 增加信用账户引用解析和资格预检调用；余额来源沿用现有账户解析。
- [ ] 移除确认阶段的余额硬编码；支付时分别匹配 `BALANCE`/`MINI_CREDIT` 摘要，推导来源并创建对应 `TransactionType` 与 `FundingSource`。
- [ ] 保持确认接口提交 `version + paymentProof + fundingSource`，支付接口只接受确认令牌；补齐中文 Javadoc 和敏感字段说明。
- [ ] 运行 `mvn -pl business-center -am -Dtest='*QrPay*Test,*QrPay*ContractTest' test`。
- [ ] 提交：`feat(qr-pay): 支持动态扫码选择花呗支付`。

### 任务 3：增加信用支付 TCC 协调分支和终态核验

**文件：** `backend/business-center/src/main/java/com/minialalipay/business/infrastructure/tcc/HttpTccCoordinator.java`、`SeataTccCoordinator.java`、事实查询适配；相关 TCC 测试。

- [ ] 先写失败测试，覆盖 `CREDIT_PAY` Try/Confirm/Cancel、收款方余额预占、信用账本分支和未知结果恢复。
- [ ] 为 `CREDIT_PAY` 派生稳定 XID/分支键，调用 `/internal/v1/tcc/credit-pay/{action}`、`/internal/v1/tcc/balance/{role}/{action}` 和 `/internal/v1/tcc/credit-ledger/{action}`。
- [ ] 仅在 `CREDIT_PAY` 使用信用账户 ID；余额支付保持原路径不变。
- [ ] 扩展账户事实读取和成功/取消一致性判断，信用冻结、应收和账本事实不一致时进入人工处理。
- [ ] 运行 `mvn -pl business-center,account-center -am -Dtest='*Tcc*Test,*TransactionFact*Test' test`。
- [ ] 提交：`feat(credit): 接入动态扫码信用 TCC`。

### 任务 4：修正 H5 支付协议与资金来源交互

**文件：** `frontend-h5/src/services/qrPay.ts`、`paymentPassword.ts`、`frontend-h5/src/pages/h5/QrPay/index.tsx`、样式和测试。

- [ ] 先写服务测试，断言证明用途 `QR_PAY_CONFIRM`、确认请求字段、支付请求幂等键和 `X-Request-Id`。
- [ ] 将页面从直接提交支付密码改为先签发支付证明，再提交确认请求；支付阶段只发送确认令牌。
- [ ] 两个资金来源初始不选中；根据余额/额度查询结果禁用不可用来源；切换来源清理密码和令牌；禁止自动切换。
- [ ] 成功或处理中后刷新原订单、余额、花呗摘要和账单查询缓存，不在客户端保存余额或额度事实。
- [ ] 运行 `npm test -- --runInBand test/services/qrPay.test.ts` 和 `npm run lint`（工作目录 `frontend-h5`）。
- [ ] 提交：`feat(h5): 对齐动态扫码花呗支付流程`。

### 任务 5：同步系统分析、契约校验和端到端测试

**文件：** `docs/minialalipay/minialalipay-system-analysis.md`、`contracts/openapi/minialalipay-api.yaml`、`tests/` 下相关跨服务用例。

- [ ] 先新增跨服务失败用例：动态扫码花呗成功、额度不足、重复提交、TCC 回滚和回执来源。
- [ ] 同步系统分析中的内部资格预检、`CREDIT_PAY` 交易流和错误处理；确认不修改数据库设计。
- [ ] 执行 OpenAPI 结构校验、错误码契约测试及跨服务测试。
- [ ] 运行 `mvn test`、`npm run build`（两个前端工程）以及仓库已有契约校验命令。
- [ ] 提交：`test(credit): 覆盖动态扫码花呗跨服务闭环`。

## 自检结论

计划覆盖设计中的架构、数据流、交互错误处理、幂等、安全、TCC、账本和测试要求；未新增产品范围或数据库表。所有任务均给出具体路径、测试和提交边界。

