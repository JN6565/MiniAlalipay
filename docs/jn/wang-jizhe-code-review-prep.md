# 代码评审准备材料：王基哲负责范围（信用/花呗、银行卡、账单 + 前后端）

> 本文档基于仓库当前代码实际走读整理，用于小组代码评审讲解。个别产品规则细节（如银行卡字段设计）评审前建议再核对 `docs/minialalipay/minialalipay-bank-card-design.md` 与 `docs/minialalipay/minialalipay-team-task-division-design.md`。

---

## 0. 一分钟总览（先建立全局图）

```
H5/Admin 前端 ──只能调网关:8080──> gateway ──> business-center(交易编排) / account-center(资金事实)
                                              └─ business-center 通过内部接口 /internal/v1/** 调 account-center
                                              └─ Seata TC 负责全局事务的 Confirm/Cancel 回调
```

**一句话讲清分工**：`business-center` 负责"交易主单"（创建交易、状态机、终态发布）；`account-center` 是唯一的资金事实来源（余额、冻结、账本、信用额度、TCC 分支屏障）。前端永远只和网关打交道。

按 `docs/jn/wang-jizhe-dev-guide.md` 的定义，王基哲的代码所有权是：`account-center` 的 `credit`、`bill`、`repayment` 子域包 + `tests/` + `deploy/`；银行卡属于本次额外负责的联调范围。

---

## 1. DDD 分层：代码到底写在哪

每个服务都遵循 `interfaces -> application -> domain <- infrastructure`（依赖方向：interfaces/application 依赖 domain，infrastructure 实现 domain 定义的仓储接口，domain 不依赖任何框架）。

### 1.1 interfaces 层（Controller + 请求/响应 DTO）

| 位置 | 关键文件 | 职责 |
|---|---|---|
| account-center `interfaces/bankcard/` | `BankCardController.java`（`/api/v1/bank-cards`）、`BindBankCardRequest`、`FullCardNumberRequest`、`RegisteredCardController`（`/api/v1/bank-card-registrations`） | 绑卡/列表/详情/设默认/解绑/查完整卡号 |
| account-center `interfaces/credit/` | `CreditController.java`（`/api/v1/credit`）、`CreditOpsController`（`/api/v1/ops/credit`）、`dto/` 下的请求体 | 花呗额度/账单/还款 C 端接口 + B 端出账运维接口 |
| account-center `interfaces/tcc/` | `BalanceTccController`、`SeataTransferTccController`、`SeataBankCardRechargeTccController`、`SeataBankCardWithdrawTccController`、`CreditTccController`、`CreditPayLedgerTccController`、`LedgerTccController` 等 | **内部 TCC 端点**（`/internal/v1/**`），不经过网关，供 Seata TC / business-center 回调 |
| account-center `interfaces/reconciliation/` | `TransactionFactController`（`/internal/v1/transaction-facts/{id}`）、`ReconciliationDiffController` | 交易事实核验（终态判定依据）、对账差异 |
| account-center `interfaces/filter/` | `SeataXidPropagationFilter`、`AccountCenterRequestIdFilter` | Seata XID 跨 HTTP 透传、请求 ID |
| business-center `interfaces/bankcard/` | `BankCardBalanceController.java`（`/api/v1/bank-cards/{cardId}/recharge|withdraw|balance|transactions`） | **银行卡充值/提现的资金入口**（注意：卡在 account-center，钱的流程在 business-center） |
| business-center `interfaces/transfer|qrpay|recharge|refund/` | `TransferController`、`QrPayController`、`RechargeController`、`RefundController` | 转账/扫码/充值/退款入口 |

### 1.2 application 层（用例编排、事务边界）

| 位置 | 关键文件 |
|---|---|
| account-center `application/bankcard/` | `BankCardApplicationService`（绑卡用例）、`RegisteredCardApplicationService`、`dto/BankCardDTO` |
| account-center `application/credit/` | `CreditQueryService`（额度/账单查询）、`CreditRepaymentService`（还款草稿+提交）、`CreditJobService`（出账/到期任务）、`CreditTccParticipant`（CREDIT_PAY 参与者）、`CreditRepayTccParticipant`（CREDIT_REPAY）、`CreditRefundTccParticipant`（REFUND） |
| account-center `application/tcc/` | `BalanceTccApplicationService`、`BankCardBalanceTccApplicationService`、`LedgerTccApplicationService`、`CreditPayLedgerTccApplicationService`、`SeataTransferTccParticipant`、`SeataBankCardBalanceTccParticipant`、`SeataLedgerTccParticipant` |
| account-center `application/reconciliation/` | `TransactionFactApplicationService`（事实核验规则集） |
| business-center `application/bankcard/` | `BankCardRechargeApplicationService`、`BankCardWithdrawApplicationService` |
| business-center `application/port/` | **端口接口**：`TccCoordinatorPort`（TCC 协调抽象）、`BankCardPort`（跨服务查卡）、`BusinessStore`（持久化抽象）、`CreditAccountDirectoryPort` 等。application 只依赖端口，不依赖具体 HTTP/DB 实现 |

事务边界：写操作的应用服务方法上标注 `@Transactional`（如 TCC 参与者每个 Try/Confirm/Cancel 都是 account_db 本地事务）。

### 1.3 domain 层（领域对象、状态、规则）

| 位置 | 内容 |
|---|---|
| account-center `domain/account/` | `Account`、`AccountBalance`（余额聚合，CAS 版本控制）、`FreezeRecord`/`FreezeStatus`/`FreezePurpose`（余额冻结）、`AccountErrorCode` |
| account-center `domain/bankcard/` | `BankCard`（卡聚合，含虚拟余额 `balanceFen`、`recharge()`/`withdraw()` 方法）、`BankCardNumber`（Luhn 校验）、`IdCardValidator`、`SensitiveMask`（脱敏）、`RegisteredCard`、`BankCardErrorCode` |
| account-center `domain/tcc/` | `TccBranch`（分支屏障聚合，含 `initialize`/`emptyRollback`/`markTried`/`confirm`/`cancel` 状态推进）、`TccBranchType`（11 种分支类型）、`TccBranchStatus`、`RollbackType` |
| account-center `domain/credit/` | `CreditAccount`（额度账户聚合根，5000 元固定额度）、`CreditFreeze`、`CreditReceivable`（应收）、`CreditPurchase`（消费明细） |
| account-center `domain/bill/` | `CreditBill`（月度账单聚合根）、`CreditBillItem` |
| account-center `domain/repayment/` | `CreditRepayment`、`CreditRepaymentDraft`（还款草稿+分配）、`CreditRepaymentAllocation` |
| account-center `domain/ledger/` | `LedgerVoucher`、`LedgerEntry`（复式记账）、`LedgerAccount` |
| business-center `domain/transaction/` | `FundTransaction`（交易主单聚合）、`TransactionStatus`、`TransactionType`、`FundingSource`（BALANCE/BANK_CARD/MINI_CREDIT） |

### 1.4 infrastructure 层（持久化、外部调用、Seata 技术实现）

| 位置 | 内容 |
|---|---|
| account-center `infrastructure/credit/{po,mapper}/` | 信用表 MyBatis Mapper 与 PO |
| account-center `infrastructure/{account,bankcard,ledger,reconciliation,tcc}/` | 各子域仓储实现、TCC 分支持久化 |
| business-center `infrastructure/tcc/` | **Seata 技术核心**：`SeataTccCoordinator`、`SeataGlobalTransactionExecutor`、`HttpTccCoordinator`（旧 HTTP 协调器，信用支付等仍用它） |
| business-center `infrastructure/http/` | `BankCardPort`、`AccountDirectoryPort` 等端口的 RestClient 实现（经 `@LoadBalanced` + Nacos 服务名调用 account-center） |
| business-center `infrastructure/persistence/` | `BusinessStore` 的 JDBC/MyBatis 实现 |

---

## 2. TCC 与 Seata 的实现方式（评审核心）

### 2.1 通俗思想（讲解口径）

> "TCC 就是跨服务的'两阶段提交的业务版'。一笔转账涉及付款方账户、收款方账户、账本三个地方，不能一半成功一半失败。所以第一步 **Try** 大家都在自己库里'占住资源'（冻结余额、记录屏障），但都不真正动账；如果所有人都 Try 成功，协调者下令 **Confirm**，大家才真正扣钱入账；任何一个人 Try 失败，协调者下令 **Cancel**，大家释放预占。为了保证网络抖动下不出错，每个分支都有'幂等屏障表'（`tcc_branch`），解决三个经典问题：重复调用不重复扣钱（幂等）、Cancel 先于 Try 到达时空回滚、晚到的 Try 被拒绝（防悬挂）。"

### 2.2 角色划分

| 角色 | 类 | 说明 |
|---|---|---|
| 事务发起者（TM） | `SeataGlobalTransactionExecutor`（business-center） | 方法上的 `@GlobalTransactional` 开启 Seata 全局事务，`RootContext.getXID()` 拿 XID |
| 协调决策者 | `SeataTccCoordinator`（business-center） | 实现 `TccCoordinatorPort`，`@Primary` + 配置项 `minialalipay.tcc.coordinator=seata`（默认启用）；按交易类型路由 |
| TCC 参与者（RM） | account-center 各 TCC 应用服务 | 执行真正的 Try/Confirm/Cancel |

### 2.3 三个阶段各自做什么（以余额转账为例）

**Try**（`BalanceTccApplicationService.tryPayer`）：
- 在 account_db 本地事务内：先 `initialize()` 创建 `tcc_branch` 屏障（唯一键防重，`DataIntegrityViolationException` 时回查已有分支）；
- 调 `balanceService.freeze()` **冻结付款方余额**（`FreezePurpose.TRANSFER_OUT`）；
- 屏障状态 INIT → TRIED，CAS 版本号保存。
- 收款方 `tryPayee` 只持久化预占屏障，**不提前加可用余额**。

**Confirm**（`confirmPayer`/`confirmPayee`）：
- 已 CONFIRMED 直接返回（**幂等**）；
- 付款方：把冻结转为扣减（`balanceService.confirm`）；收款方：`balance.credit()` 后用 `updateBalanceForActiveAccount`（CAS）入账，**只增加一次**；
- 屏障推进到 CONFIRMED。

**Cancel**（`cancelPayer`/`cancelPayee`）：
- 分支不存在 → `emptyRollback()` 写一条 EMPTY 屏障记录（**空回滚**）；
- 之后晚到的 Try 看到 CANCELLED 屏障会抛 `IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try")`（**防悬挂**）；
- 付款方释放冻结；重复 Cancel 直接返回（幂等）。

`sameRequest()` 还校验 transactionId/金额与屏障一致，不一致抛 `IDEMPOTENCY_CONFLICT`。

### 2.4 银行卡充值/提现的三阶段

在 `BankCardBalanceTccApplicationService`，屏障机制与上面完全同构，区别在资金动作：

- **充值**（`BANK_CARD_RECHARGE`）：Try 建屏障 + 校验卡归属 + 校验**卡虚拟余额充足**；Confirm 调 `card.withdraw()` **扣减银行卡虚拟余额**（CAS）；
- **提现**（`BANK_CARD_WITHDRAW`）：Try 建屏障 + 校验归属；Confirm 调 `card.recharge()` **增加卡虚拟余额**。
- 两者 Cancel 均为空回滚/释放屏障，无复式账本。

### 2.5 business-center 如何编排（关键调用链）

```
BankCardBalanceController.recharge
  → BankCardRechargeApplicationService.recharge（创建 FundTransaction 主单并提交）
  → TccCoordinatorPort.startOrResume
  → SeataTccCoordinator.startOrResume（按 businessType 路由）
      ├─ BANK_CARD_RECHARGE → executeBankCardRecharge
      ├─ BANK_CARD_WITHDRAW → executeBankCardWithdraw
      ├─ TRANSFER/QR_PAY + FundingSource.BANK_CARD → executeBankCardTransfer
      └─ 其他（含信用支付、普通充值、退款）→ httpFallback（旧 HTTP 协调器）
  → SeataGlobalTransactionExecutor.executeBankCardRecharge（@GlobalTransactional 开 Seata 全局事务）
      → POST http://account-center/internal/v1/seata-tcc/bank-card-recharge/try（带 XID 头）
  → account-center SeataBankCardRechargeTccController.tryBankCardRecharge
      → SeataBankCardBalanceTccParticipant.tryRecharge（卡分支）
      → SeataTransferTccParticipant.tryPayee（收款入账分支）
  → Try 全部成功 → Seata TC 回调各参与者 Confirm；任一失败 → TC 回调 Cancel
  → 回到 SeataTccCoordinator：TC 成功后还要 readFacts() 读账户事实核验（successConsistent）才发布 SUCCESS；
     异常时读事实 cancelConsistent 才发布 CANCELLED；结果未知保持 ROLLING_BACK 恢复态，绝不把超时当失败
```

**评审亮点**（`SeataTccCoordinator` 类注释）：TC 返回成功 ≠ 可以发布终态，必须再用 `GET /internal/v1/transaction-facts/{id}` 读账户中心事实。这是"事实核验先于终态发布"的资金安全设计。

**易错点提醒**（代码注释明确写了）：银行卡出资转账**复用充值组合端点**（卡扣减→收款入账），**不得改用提现分支**，否则资金方向相反、收款方永远收不到钱——这一点在契约测试 `SeataGlobalTransactionExecutorTest` 里固化了。

---

## 3. 银行卡功能

### 功能模块 → 关键目录 → 关键文件 → 核心流程 → 评审关注点

**A. 绑卡管理（account-center 独立闭环，不涉及资金）**

- 目录：`account-center/interfaces/bankcard/` → `application/bankcard/` → `domain/bankcard/` → `infrastructure/bankcard/`
- 入口：`BankCardController`：`GET /api/v1/bank-cards`（列表）、`POST`（绑卡）、`GET /{cardId}`（详情）、`PUT /{cardId}/default`（设默认）、`DELETE /{cardId}`（解绑软删 UNBOUND）、`POST /{cardId}/full-card-number`（一次性支付证明换明文卡号）
- H5 前端：页面 `frontend-h5/src/pages/h5/{BankCards,BankCardBind,BankCardAdd,BankCardDetail}/index.tsx`；API 封装 `frontend-h5/src/services/bankCard.ts`
- 流程：H5 绑卡 → 网关 → `bindCard`（Luhn 卡号校验、BIN 识别、四要素校验、首卡自动设默认、重复绑卡 409）
- 评审关注点：用户 ID 只信任网关 `X-User-Id` 头；访问他人卡统一返回"银行卡不存在"（不暴露存在性）；卡号明文只在 full-card-number 一次性响应中出现，不落日志；响应 DTO 用 `SensitiveMask` 掩码。

**B. 充值/提现/卡出资转账（资金流程，business-center 编排）**

- 目录：`business-center/interfaces/bankcard/` → `application/bankcard/` → `infrastructure/tcc/` → account-center `interfaces/tcc/` + `application/tcc/`
- 入口：`BankCardBalanceController`：`POST /{cardId}/recharge`、`POST /{cardId}/withdraw`、`GET /{cardId}/balance`、`GET /{cardId}/transactions`（卡交易明细）
- H5 页面：`BankCardRecharge/`、`BankCardWithdraw/`、`BankCardBills/`；服务层 `bankCard.ts` 的 `rechargeBankCard`/`withdrawBankCard`
- 分支类型：`BANK_CARD_RECHARGE`、`BANK_CARD_WITHDRAW`（见 `TccBranchType`）
- 编排细节：银行卡操作的用户账户存于交易的 `payeeAccountId` 字段（`acceptBankCardOperation` 设计，见 `SeataTccCoordinator` 相关注释）；幂等键、支付密码只透传 user-center 验密不落日志。

**阅读顺序**：`BankCardBalanceController` → `BankCardRechargeApplicationService` → `SeataTccCoordinator.startOrResume` → `SeataGlobalTransactionExecutor.executeBankCardRecharge` → `SeataBankCardRechargeTccController` → `BankCardBalanceTccApplicationService.tryRecharge/confirmRecharge` → `TransactionFactApplicationService.inspectBankCardRecharge`。

---

## 4. 账单功能

本项目"账单"实际分三类，评审时注意区分：

**A. 花呗账单（核心负责范围）**
- 领域：`account-center/domain/bill/` 的 `CreditBill`（月度账单聚合根，状态 OPEN→PARTIALLY_PAID/PAID/OVERDUE）、`CreditBillItem`；数据库表 `credit_bill`、`credit_bill_item`
- 出账任务：`application/credit/CreditJobService`（每月 1 日出账，`(job_type, business_date)` 幂等，重复运行不重复建账单）；B 端触发入口 `CreditOpsController`（`POST /api/v1/ops/credit/statement-runs`、`/due-check-runs`）
- 查询接口：`CreditController` 的 `GET /api/v1/credit/bills`（列表）、`GET /api/v1/credit/bills/{id}`（详情含明细），实现在 `CreditQueryService`
- H5 页面：`frontend-h5/src/pages/h5/{CreditBills,CreditBillDetail,CreditRepay}/index.tsx`，API 封装 `frontend-h5/src/services/credit.ts`

**B. 交易流水/账本明细账单（C 端"账单"主页）**
- 接口：`account-center/interfaces/account/AccountController` 的 `GET /api/v1/accounts/me/entries`（分页账本分录）与 `GET /api/v1/accounts/me/analytics`（收支分析）
- H5 页面：`pages/h5/Transactions/index.tsx` + `AnalyticsPanel.tsx`，服务层 `services/account.ts`
- 数据来源：`application/analytics/AccountAnalyticsApplicationService`、`domain/ledger` 的账本分录

**C. 银行卡交易明细**：即上文 `GET /api/v1/bank-cards/{cardId}/transactions`（H5 `BankCardBills` 页面），由 business-center `BusinessStore.findBankCardTransactions` 查询交易主单。

**B 端 Admin**：`frontend-admin/src/pages/Transactions/index.tsx` 展示运维交易视图，走 `services/ops.ts` → business-center `OpsController`（`/api/v1/ops/**`）。Admin 中无个人花呗账单页。

**调用关系**：花呗账单数据来自信用消费事实（`credit_purchase`，由 CREDIT_PAY Confirm 写入）→ 出账任务汇总成 `credit_bill` → `CreditQueryService` 组装 DTO → Controller → H5。账单汇总**不得反向改写**已确认的信用消费事实（wang-jizhe-dev-guide 2.1 边界第 4 条）。

---

## 5. 花呗/信用支付

**代码位置一览**：

| 功能 | 关键类 |
|---|---|
| 额度冻结/确认/释放 | `application/credit/CreditTccParticipant`（`tryFreeze`/`confirmFreeze`/`cancelFreeze`），分支类型 `CREDIT_PAY` |
| 信用账本分支 | `application/tcc/CreditPayLedgerTccApplicationService`，分支类型 `CREDIT_PAY_LEDGER` |
| 还款 | `CreditRepayTccParticipant`（`CREDIT_REPAY`）、`CreditRepaymentService`（草稿→确认令牌→提交） |
| 信用退款冲正 | `CreditRefundTccParticipant`（`REFUND`/`REFUND_LEDGER`） |
| 支付发起 | business-center 扫码支付 `QrPayController`/`QrPayApplicationService`，`FundingSource.MINI_CREDIT` |
| 前端 | H5 `pages/h5/Credit/index.tsx`（额度页）、`services/credit.ts` |

**TransactionFactApplicationService 如何识别花呗支付**（`inspect()` 方法）：先看交易是否存在 `CREDIT_PAY` 账户分支——存在即花呗支付，走 `inspectCreditPay` 信用规则集（核验 `credit_freeze` 状态 + 2 个账户分支 + `CREDIT_PAY_LEDGER` 账本分支，**不查付款余额冻结**）；否则再看 `BANK_CARD_WITHDRAW`/`BANK_CARD_RECHARGE` 分支；都没有才按余额转账规则集。两种规则集互斥，混用会把一致的交易误判为不一致。

**花呗 vs 余额转账的区别（评审必备对照表）**：

| 维度 | 余额转账 | 花呗支付 |
|---|---|---|
| TCC 分支 | PAYER_BALANCE + PAYEE_BALANCE + LEDGER | CREDIT_PAY + PAYEE_BALANCE + CREDIT_PAY_LEDGER |
| 冻结对象 | 付款方余额冻结（freeze_record，TRANSFER_OUT） | 信用额度冻结（credit_freeze），**不动付款余额** |
| Confirm 资金动作 | 扣付款余额 + 加收款余额 | 额度冻结转已用 + 加应收 + 生成 CreditPurchase，收款方余额照常入账 |
| 账本分录 | 借：付款余额负债；贷：收款余额负债 | 借：信用应收资产；贷：收款用户余额负债 |
| 协调器 | Seata TCC | **当前仍走 HttpTccCoordinator**（SeataTccCoordinator 注释明确：信用支付等尚未迁移到 Seata） |

---

## 6. 跨服务测试与联调

**单元测试位置**：`backend/<服务>/src/test/java/...`，镜像主代码分层。负责范围应重点关注的测试：

- 信用 TCC：`CreditTccParticipantTest`、`CreditRepayTccParticipantTest`、`CreditRefundTccParticipantTest`（幂等、空回滚、防悬挂）
- 账单/还款：`CreditBillTest`、`CreditBillItemTest`、`CreditRepaymentServiceTest`、`CreditRepaymentDraftTest`
- 银行卡：`BankCardTest`、`BankCardBalanceTest`、`BankCardApplicationServiceTest`、`BankCardNumberTest`、`IdCardValidatorTest`、`SensitiveMaskTest`
- 事实核验：`TransactionFactApplicationServiceTest`；迁移契约：`CreditMigrationContractTest`
- OpenAPI 契约：`CreditOpenApiContractTest`、`CreditControllerTest`
- Seata 路由契约：`SeataGlobalTransactionExecutorTest`（business-center）、`SeataTccContractTest`、account 侧 `SeataTransferTccContractTest`

**测试技术要点（可现场讲）**：`SeataGlobalTransactionExecutorTest` 用 `RootContext.bind("mock-xid")` 模拟 Seata 上下文，用 `MockRestServiceServer.bindTo(RestClient.builder())` 拦截出站 HTTP，断言：①打到正确的内部端点（`/internal/v1/seata-tcc/bank-card-recharge/try`）②透传了 `RootContext.KEY_XID` 头 ③JSON 字段（userId/accountId/cardId/reservationId/amountFen）正确。路由接错会导致资金方向错误，所以用契约测试固化。AI 生成测试时应围绕：**参与者幂等三态、屏障空回滚/防悬挂、事实核验规则集路由、协调器类型分发、错误码 YAML 一致性**。

**合并后联调验证步骤**：
1. `cd deploy; docker compose up -d` 启动 MySQL(3306)/Redis(6379)/Seata Server(8091)；再启动 Nacos（注册中心）与四个后端服务（gateway:8080、user-center、business-center、account-center、ai-service）。
2. 前端只经 `http://localhost:8080` 网关调用，先 `GET /actuator/health` 确认 UP。
3. 银行卡充值联调：`POST /api/v1/bank-cards/{cardId}/recharge`（带 `Idempotency-Key` 和支付密码）→ 检查 `business_db` 交易主单 status=SUCCESS → 检查 account_db：`tcc_branch` 两条分支均 CONFIRMED、收款账户 `account_balance` 增加、`bank_card` 卡余额减少。
4. 花呗支付联调：扫码支付选花呗 → 检查 `credit_freeze`（CONFIRMED）、`credit_account` used+88 元、`credit_purchase` 新增一条、收款余额+88 元、账本借贷平衡。
5. 故障路径：重复提交同幂等键（只一笔）、额度不足（422 且无残留冻结）、模拟 Cancel（`tcc_branch` 出现 CANCELLED/EMPTY 屏障且余额恢复）。
6. 资金测试三断言原则：**交易状态 + 余额/额度变化 + 账本借贷平衡**，三者都验才算通过。

---

## 7. 五类流程防混淆速查 + 阅读顺序

| 流程 | 前端入口 | 后端入口 | 分支类型 | 有账本？ | 协调器 |
|---|---|---|---|---|---|
| 余额转账 | Transfer/TransferConfirm | TransferController | PAYER/PAYEE_BALANCE+LEDGER | 有 | Seata |
| 银行卡充值 | BankCardRecharge | BankCardBalanceController | BANK_CARD_RECHARGE+PAYEE_BALANCE | 无 | Seata |
| 银行卡提现 | BankCardWithdraw | BankCardBalanceController | PAYER_BALANCE+BANK_CARD_WITHDRAW | 无 | Seata |
| 花呗支付 | QrPay（选花呗） | QrPayController | CREDIT_PAY+PAYEE_BALANCE+CREDIT_PAY_LEDGER | 有（信用账本） | HTTP 协调器 |
| 账单查询 | CreditBills/Transactions | CreditController/AccountController | —（只读） | — | — |

**推荐整体阅读顺序**（评审讲解时按此走读代码）：
1. `TccBranchType` + `TccBranch`（先懂屏障是什么）
2. `BalanceTccApplicationService`（最标准的 Try/Confirm/Cancel 模板）
3. `BankCardBalanceTccApplicationService`（对照理解银行卡差异）
4. `SeataTccCoordinator` → `SeataGlobalTransactionExecutor` → `SeataBankCardRechargeTccController`（理解全局编排）
5. `TransactionFactApplicationService`（理解终态如何判定）
6. `CreditTccParticipant` + `CreditQueryService` + `CreditController`（花呗主线）
7. `SeataGlobalTransactionExecutorTest`（理解测试怎么验证路由）

---

## 8. 面向组员的通俗讲解口径（2 分钟版）

> "我们这套系统的资金安全靠两个机制配合：**TCC 分支屏障**和**事实核验**。所有资金操作分三步：Try 先在各库里'占座'（冻结余额/额度、写屏障记录），Confirm 才真正动钱，Cancel 负责释放。屏障表解决网络重试的三个坑：重复调用、空回滚、晚到 Try。business-center 是导演，管交易主单和状态机；account-center 是金库，所有余额、账本、额度只有它能改。Seata 负责全局事务的 Confirm/Cancel 调度，但注意**发布终态前还要回读账户事实**——TC 说成功不算数，账平了才算数。我负责的信用子域就是在这套骨架上加了花呗的额度分支、信用账本分支和账单/还款用例，规则集在 TransactionFactApplicationService 里和余额转账互斥识别。银行卡充值提现则是把卡的虚拟余额当成另一个资金账户，复用同一套屏障机制。"

**评审前建议再打开确认的文件**（未逐行走读，避免凭印象讲）：
- `backend/business-center/src/main/java/com/minialalipay/business/application/bankcard/BankCardRechargeApplicationService.java`、`BankCardWithdrawApplicationService.java`（应用层细节）
- `backend/account-center/src/main/java/com/minialalipay/account/application/credit/CreditRepaymentService.java`（还款提交细节）
- `frontend-h5/src/pages/h5/CreditBills/index.tsx`（页面具体交互）
- `docs/minialalipay/minialalipay-bank-card-design.md`（银行卡产品规则）

---

## 9. 前端讲解补充：围绕我的分工讲页面怎么承接信用、银行卡和质量门禁

这一章给明天讲前端用，但口径要先摆正：**我不是 H5 或 B 端工程的总负责人；按团队分工，H5 主体由闫泽华负责，B 端主体由王春桂负责。我负责的是 `account-center` 的信用/账单/还款子域，以及 `tests/`、`deploy/` 的质量门禁；银行卡是本次额外负责的联调范围。**  
所以我讲前端时，不讲所有页面的全量实现，而是讲“我的后端能力和质量验证，分别落到了哪些前端页面、卡片和接口调用上”。

### 9.1 我的分工和前端页面的对应关系

| 工程 | 位置 | 面向谁 | UI 技术 | 主要职责 |
|---|---|---|---|---|
| C 端 H5 | `frontend-h5/` | 普通用户 | React + TypeScript + Umi + `antd-mobile` + 自定义 Less | 与我强相关的是 Mini 花呗、花呗账单、还款、银行卡、卡账单、钱包入口、首页花呗摘要 |
| B 端 Admin | `frontend-admin/` | 运营/管理员/观察者 | React + TypeScript + Umi + Ant Design + TanStack Query + Zustand | 与我强相关的是信用出账/到期运维入口、交易查询、数据质量、看板中的质量门禁和故障验证结果 |

可以这样讲分工：

- **我负责的业务事实**：Mini 花呗额度、已用/可用、信用应收、消费明细、月度账单、还款分配、逾期暂停和清偿恢复。
- **我负责的联调范围**：银行卡绑定、银行卡充值/提现、卡交易明细，重点验证资金方向、卡号脱敏、余额掩码和幂等。
- **我负责的质量门禁**：跨服务测试、故障注入、本地部署，验证前端入口经网关打通后，后端交易状态、余额/额度变化、账本分录三者一致。
- **我不把前端页面说成自己的代码所有权**：H5 页面和 Admin 页面分别归 C 端/B 端负责人，但我需要能讲清它们如何调用我的接口、展示我的业务事实，以及我用什么测试证明它们没有展示错误状态。

工程入口配置仍然要知道：

- H5 配置：`frontend-h5/config/config.ts`，这里配置移动端 viewport、路由、开发代理 `/api -> http://localhost:8080`，还对 AI SSE 流接口单独配置代理。
- H5 路由：`frontend-h5/config/routes.ts`，其中 `/h5/credit`、`/h5/credit/bills`、`/h5/credit/bills/:id`、`/h5/credit/repay`、`/h5/bank-cards/**` 是我讲解时最需要打开的路由。
- Admin 配置：`frontend-admin/config/config.ts`，使用 Hash 路由、Ant Design 插件、请求插件、权限插件，代理 `/api` 和 `/actuator` 到网关。
- Admin 路由：`frontend-admin/config/routes.ts`，其中 `/admin/transactions`、`/admin/dashboard`、`/admin/data-quality`、`/admin/reports`、`/admin/demo-tasks` 与我的质量保障讲解更相关。

### 9.2 我相关的 H5 页面放在哪里

H5 的目录结构可以简单讲，但重点落到这些页面：

| 页面 | 代码位置 | 和我分工的关系 |
|---|---|---|
| 首页花呗摘要 | `frontend-h5/src/pages/h5/Home/index.tsx` | 首页只取花呗摘要，展示本期应还和可用额度 |
| Mini 花呗首页 | `frontend-h5/src/pages/h5/Credit/index.tsx` | 展示额度、应还、还款日、最近账单 |
| 花呗账单列表 | `frontend-h5/src/pages/h5/CreditBills/index.tsx` | 展示月度账单、未出账/已出账状态 |
| 花呗账单详情 | `frontend-h5/src/pages/h5/CreditBillDetail/index.tsx` | 展示账单明细、消费项和应还金额 |
| 花呗还款 | `frontend-h5/src/pages/h5/CreditRepay/index.tsx` | 创建还款草稿、展示分配、提交还款 |
| 银行卡列表 | `frontend-h5/src/pages/h5/BankCards/index.tsx` | 展示已绑定卡和卡面掩码 |
| 注册银行卡 | `frontend-h5/src/pages/h5/BankCardAdd/index.tsx` | 银行卡注册，生成演示卡 |
| 绑定银行卡 | `frontend-h5/src/pages/h5/BankCardBind/index.tsx` | 三要素绑定已注册银行卡 |
| 卡片详情 | `frontend-h5/src/pages/h5/BankCardDetail/index.tsx` | 查看默认卡、余额掩码、查完整卡号入口 |
| 卡账单 | `frontend-h5/src/pages/h5/BankCardBills/index.tsx` | 展示银行卡充值/提现/出资交易明细 |
| 充值/提现 | `frontend-h5/src/pages/h5/BankCardRecharge/index.tsx`、`BankCardWithdraw/index.tsx` | 触发银行卡资金流程，后端用 Seata TCC 编排 |

讲解时的主线是：**页面文件只是用户入口；真正的信用事实来自 `frontend-h5/src/services/credit.ts` 调网关后的后端接口，银行卡事实来自 `frontend-h5/src/services/bankCard.ts`。页面不能自己发明额度、账单、交易终态。**

### 9.3 首页里和我相关的卡片：花呗摘要卡

首页代码在 `frontend-h5/src/pages/h5/Home/index.tsx`，样式在同目录 `index.less`。

我讲首页时只讲和我相关的两块：

- 快捷入口里有“花呗”和“银行卡”，分别跳 `/h5/credit` 和 `/h5/bank-cards`。
- `credit-card` 是首页花呗摘要卡，展示“本期应还”和“可用额度”，点击进入 `/h5/credit`。

首页数据只拉花呗摘要：

```ts
const creditResult = await creditService.getCreditSummary();
setCredit(creditResult as unknown as creditService.CreditSummary);
```

这里可以强调：**首页只是信用信息的轻量入口，不展示完整账单，也不做额度计算。`billedFen`、`availableFen` 都是后端信用子域返回的事实，前端只用 `formatAmount()` 做展示转换。**

### 9.4 Mini 花呗页面怎么做：承接我的信用接口

花呗首页在 `frontend-h5/src/pages/h5/Credit/index.tsx`，样式在 `Credit/index.less`。

页面结构：

- `credit-hero`：沉浸式头部，展示“Mini 花呗”、本月应还、还款日、总额度、可用额度。
- `credit-entry-card`：白色入口卡，里面两行入口：花呗账单、立即还款。
- `credit-repay-cta`：大按钮，根据是否有应还/未出账决定可点状态。
- `bills-card`：最近账单列表，最多展示 3 条，点击进入账单详情。

数据加载方式：

```ts
const [creditData, billsData] = await Promise.all([
  creditService.getCreditSummary(),
  creditService.getBills(),
]);
```

这对应我的后端接口：

- `GET /api/v1/credit/me`：额度摘要，来自 `CreditController` + `CreditQueryService`。
- `GET /api/v1/credit/bills`：账单列表，来自 `CreditQueryService`。

讲解口径：**这页展示的是我信用子域的读模型。前端只负责把 `totalLimitFen`、`availableFen`、`billedFen`、`unbilledFen` 展示出来；额度守恒、逾期暂停、已用恢复都由后端领域模型和测试保证。**

### 9.5 花呗账单和还款页怎么讲

这一块最贴近我的核心分工，建议重点讲。

| 页面 | 位置 | 调用能力 | 我负责解释的后端事实 |
|---|---|---|---|
| 账单列表 | `frontend-h5/src/pages/h5/CreditBills/index.tsx` | `creditService.getBills()` | 每月 1 日出账；账单状态 OPEN/PARTIALLY_PAID/PAID/OVERDUE |
| 账单详情 | `frontend-h5/src/pages/h5/CreditBillDetail/index.tsx` | `creditService.getBillDetail()` | 账单金额、消费明细、已还/待还金额 |
| 还款页 | `frontend-h5/src/pages/h5/CreditRepay/index.tsx` | `createCreditRepaymentDraft()`、`submitCreditRepayment()` | 还款草稿、分配顺序、确认令牌、幂等提交 |

还款页讲解重点：

- 前端输入金额后，不自己决定还哪笔账单，而是调用后端创建还款草稿。
- 后端按固定顺序分配：逾期账单 → 已出账账单 → 未出账消费。
- 草稿返回分配预览和确认上下文；提交还款时必须走支付密码证明和幂等键。
- 还款成功后，后端同时保证：用户余额减少、信用应收减少、已用额度减少、可用额度恢复、账本借贷平衡。

一句话口径：**还款页只是展示和提交入口，真正的还款分配和资金守恒在我的 `CreditRepaymentService`、`CreditRepayTccParticipant` 和账本联动测试里。**

### 9.6 银行卡卡片怎么做：我的额外联调范围

银行卡不是我原始代码所有权，但属于这次我需要讲清的额外联调范围。前端最重要的复用组件是：

`frontend-h5/src/components/h5/common/BankCardFace.tsx`

这个组件的输入参数包括：

- `bankCode`：银行代码，如 ICBC、CMB、BOC。
- `bankName`：银行名称。
- `cardType`：`DEBIT` 或 `CREDIT`。
- `cardLast4`：卡号后四位。
- `isDefault`：是否默认卡。
- `balanceFen`：卡内虚拟余额，单位是分。
- `balanceRevealed`：余额是否明文展示。

卡片的视觉不是图片，而是 CSS 做的：

- `BANK_FACE_STYLES` 根据银行代码选择不同 `gradient` 渐变和 `pattern` 纹样。
- 外层 `<div className="bank-card-face">` 把 `pattern + gradient` 拼成背景。
- 卡号只显示 `**** **** **** 后四位`。
- 余额默认显示 `****`，只有调用方传 `balanceRevealed=true` 时才显示金额。

可以这样讲：**银行卡卡片是一个真正的复用组件。页面只负责拿数据和决定点哪里跳转；卡面的颜色、纹样、掩码、默认角标都集中在 `BankCardFace` 里。这样列表页、详情页、充值提现入口都能保持同一套卡面。**

相关页面位置：

- 卡列表：`frontend-h5/src/pages/h5/BankCards/index.tsx`
- 注册银行卡：`frontend-h5/src/pages/h5/BankCardAdd/index.tsx`
- 绑定银行卡：`frontend-h5/src/pages/h5/BankCardBind/index.tsx`
- 卡片详情：`frontend-h5/src/pages/h5/BankCardDetail/index.tsx`
- 卡账单：`frontend-h5/src/pages/h5/BankCardBills/index.tsx`
- 充值：`frontend-h5/src/pages/h5/BankCardRecharge/index.tsx`
- 提现：`frontend-h5/src/pages/h5/BankCardWithdraw/index.tsx`
- API 封装：`frontend-h5/src/services/bankCard.ts`

我的讲解重点不是“卡片样式好看”，而是四个安全点：

- 卡号只显示后四位，完整卡号必须走一次性证明接口。
- 余额默认掩码，明文只在当前页面状态里展示，不写浏览器存储。
- 充值/提现页面提交的是整数分金额和幂等键，后端通过 TCC 判断最终资金状态。
- 卡交易明细来自后端交易主单查询，不由前端根据本地操作记录拼出来。

### 9.7 AI Talk 里和我相关的是信用查询/还款确认，不是普通转账全流程

AI Talk 页面在 `frontend-h5/src/pages/h5/AITalk/index.tsx`，拆了很多子组件：

| 子组件 | 位置 | 职责 |
|---|---|---|
| 消息列表 | `AITalk/components/MessageList.tsx` | 渲染用户消息、AI 消息、流式消息 |
| 输入栏 | `AITalk/components/InputBar.tsx` | 输入自然语言并发送 |
| 澄清气泡 | `AITalk/components/ClarificationBubble.tsx` | 多收款人/信息不完整时让用户选择 |
| 确认卡片 | `AITalk/components/ConfirmationCard.tsx` | 展示转账/还款确认信息，输入支付密码，提交确认 |
| 工具结果卡片 | `AITalk/components/ToolResultCard.tsx` | 展示 AI 工具调用结果 |

和我分工相关的是两点：

- AI 可以查询 Mini 花呗额度、账单、还款状态，但查询结果必须来自后端 `credit` 接口。
- AI 可以引导创建还款草稿或展示还款确认卡片，但不能绕过确认令牌、支付密码和后端还款分配。

讲解口径：**我的信用子域给 AI 提供的是可查询、可确认的结构化能力，不给 AI 直接写额度、写应收或提交资金动作的权限。**

### 9.8 H5 接口调用：重点讲 credit.ts 和 bankCard.ts

H5 统一请求基础层在 `frontend-h5/src/services/request.ts`。

它做的事情：

- 每个请求自动加 `X-Request-Id`，方便和后端日志串起来。
- 登录后自动加 `Authorization: Bearer <token>`。
- 统一把后端错误包装成 `ApiError`。
- 遇到 401 或会话失效错误码，会清理本地会话并回到登录页。
- 对带 `Idempotency-Key` 的可重试请求，在网络错误或 502/503/504 时最多重试 3 次。
- `clearSession()` 只清登录态，不清头像/昵称展示偏好，符合系统分析第 25 节。

业务服务文件按领域拆分：

| 文件 | 主要负责 |
|---|---|
| `services/credit.ts` | **我的核心分工入口**：花呗额度、消费、账单、还款草稿、提交还款 |
| `services/bankCard.ts` | **我的联调范围入口**：银行卡列表、注册、绑定、详情、充值、提现、卡流水 |
| `services/account.ts` | 账单主页/余额明细会引用账户事实，用于和信用还款后的余额变化对照 |
| `services/ai.ts` | AI 查询信用信息或发起还款确认时会间接关联我的信用接口 |

可以这样讲：**我检查前端时，主要看 `credit.ts` 和 `bankCard.ts` 是否只走网关、是否传整数分、写操作是否有幂等键、是否没有把支付密码/确认令牌/完整卡号放进 URL 或本地存储。**

### 9.9 B 端 Admin：我重点讲质量门禁和信用运维入口

B 端代码在 `frontend-admin/src/`。

基本结构：

```
frontend-admin/src/
  layouts/AdminLayout/          左侧菜单、顶部栏、内容区
  pages/<页面名>/index.tsx      运营页面
  services/ops.ts               运营接口封装
  services/request.ts           网关请求基础层
  wrappers/*.tsx                登录、角色、权限守卫
  stores/ui.ts                  Zustand，只保存菜单折叠这类 UI 状态
```

`AdminLayout/index.tsx` 做后台壳子：

- 左侧 `Sider` 菜单，分为“总览 / 数据 / 系统”。
- 顶部 `Header` 显示当前页面标题、页面说明、登录用户、退出按钮。
- 内容区用 `<Outlet />` 渲染当前页面。
- 菜单折叠状态存在 `stores/ui.ts`，这是客户端 UI 状态，不是业务事实。

菜单权限在 `AdminLayout/menu.tsx`：先定义全部菜单项，再根据 `access.ts` 的权限模型过滤。这里要讲清楚：**隐藏菜单只是前端体验，不能替代服务端鉴权；路由 wrapper 和后端权限才是最终门禁。**

与我分工强相关的 B 端页面：

| 页面 | 位置 | 和我分工的关系 |
|---|---|---|
| 可信运行看板 | `frontend-admin/src/pages/Dashboard/index.tsx` | 展示质量结果、服务健康、最近交易，是质量门禁的展示入口 |
| 交易查询与回执 | `frontend-admin/src/pages/Transactions/index.tsx` | 用于验证信用支付、还款、银行卡充值提现的交易事实和 TCC/Outbox 状态 |
| 数据质量 | `frontend-admin/src/pages/DataQuality/index.tsx` | 对应我负责的数据质量门禁、对账差异和发布阻断 |
| T+1 报表 | `frontend-admin/src/pages/Reports/index.tsx` | 验证日终指标和质量门禁结果 |
| 演示任务触发 | `frontend-admin/src/pages/DemoTasks/index.tsx` | 可辅助触发出账、到期检查、故障演示任务 |

### 9.10 B 端交易页怎么支撑我的测试讲解

交易查询页：`frontend-admin/src/pages/Transactions/index.tsx`

页面结构：

- 顶部筛选栏：交易状态、业务类型、发起人关键词、搜索、刷新。
- 中间表格：交易编号、金额、状态、业务类型、发起人、来源订单、风险、创建时间。
- 右侧抽屉：点击交易行后打开“交易唯一事实详情”。
- 抽屉分组：交易结果、处理进度、风险/人工处理、技术追溯。

我讲这页时，重点把它和测试三断言连起来：

- 信用支付：交易状态是 `SUCCESS`，资金来源是花呗，后端验证额度已用增加、应收增加、收款余额增加、账本平衡。
- 信用还款：交易状态是 `SUCCESS`，后端验证余额减少、应收减少、可用额度恢复、账本平衡。
- 银行卡充值/提现：交易状态和 TCC 状态一致，银行卡虚拟余额方向正确。
- 故障注入：如果 TCC 未收敛，页面应展示处理中/回滚中/人工处理，不能展示成功。

### 9.11 讲前端时的推荐路线：按我的任务包走

建议不要从“前端技术栈”发散，而按你的任务包讲：

1. **Mini 花呗额度**：打开 `/h5/credit`，讲 `Credit/index.tsx` 如何展示 `GET /api/v1/credit/me` 的额度摘要。
2. **花呗账单**：打开 `/h5/credit/bills` 和详情页，讲账单来自 `credit_bill`/`credit_bill_item`，前端只展示后端状态。
3. **花呗还款**：打开 `/h5/credit/repay`，讲创建草稿、后端分配、支付密码、幂等提交和还款守恒。
4. **银行卡联调**：打开 `/h5/bank-cards`，讲 `BankCardFace` 卡面、卡号脱敏、充值/提现入口和 TCC 资金方向。
5. **质量门禁**：打开 `/admin/transactions` 和 `/admin/dashboard`，讲如何用交易事实、TCC 状态、数据质量结果证明“页面显示没有把未知当成功”。
6. **收束边界**：前端不是资金事实来源；我负责通过后端不变量和跨服务测试保证页面展示可信。

---

## 10. 前端评审问答示例：围绕我的分工回答

### 问题 1：你负责后端信用业务，为什么还要讲 H5 的花呗页面？

因为前端页面是我负责能力的用户入口和验收证据。我的代码所有权在 `account-center` 的 `credit`、`bill`、`repayment`，但这些能力最终要在 H5 展示为 `/h5/credit`、`/h5/credit/bills`、`/h5/credit/repay`。我讲前端不是说页面归我维护，而是说明我的接口如何被页面调用、页面展示哪些后端事实、以及我怎么用测试保证展示可信。

### 问题 2：首页花呗卡片是怎么做的，和你的接口有什么关系？

首页在 `frontend-h5/src/pages/h5/Home/index.tsx`。里面的 `credit-card` 展示本期应还和可用额度，数据来自 `creditService.getCreditSummary()`，也就是后端 `GET /api/v1/credit/me`。页面只是把 `billedFen`、`availableFen` 用 `formatAmount()` 转成元展示，不自己计算额度。

### 问题 3：Mini 花呗首页的卡片怎么做？

页面在 `frontend-h5/src/pages/h5/Credit/index.tsx`。上半部分是 `credit-hero`，展示本月应还、还款日、总额度、可用额度；下面 `credit-entry-card` 提供账单和还款入口；`bills-card` 展示最近账单。它同时调用 `getCreditSummary()` 和 `getBills()`，对应我的 `CreditQueryService`。

### 问题 4：还款页为什么不能由前端自己决定还哪张账单？

因为还款分配是信用业务规则，必须由后端固定。前端 `/h5/credit/repay` 只提交还款金额并请求创建草稿，后端按“逾期账单 → 已出账账单 → 未出账消费”的顺序生成分配预览。提交还款时，后端还要校验支付密码证明、确认令牌和幂等键，并通过 TCC 保证余额、应收、额度、账本同时一致。

### 问题 5：银行卡卡片为什么每家银行颜色不一样？这块你怎么讲到自己的分工？

卡面组件在 `frontend-h5/src/components/h5/common/BankCardFace.tsx`，通过 `BANK_FACE_STYLES` 配置不同银行的 CSS 渐变和纹样，不使用真实银行卡图片或商标。我讲它主要是因为银行卡是我的额外联调范围：卡号只显示后四位，余额默认掩码，充值/提现和卡流水都必须走网关接口并接受后端 TCC 事实校验。

### 问题 6：B 端交易详情和你的质量负责人职责有什么关系？

B 端交易查询页在 `frontend-admin/src/pages/Transactions/index.tsx`。它展示交易唯一事实、资金处理状态、终态发布状态、风险和 Trace。我的跨服务测试和故障注入要验证：信用支付、信用还款、银行卡充值提现在这里展示的状态，和后端余额/额度/账本事实一致；处理中或回滚中的交易不能被前端显示成成功。

### 问题 7：你怎么确认前端没有绕过网关调用你的服务？

开发代理和请求层都约束了这个原则。H5 的 `frontend-h5/config/config.ts` 把 `/api` 代理到 `http://localhost:8080`；Admin 的 `frontend-admin/src/services/request.ts` 会检查 URL 必须以 `/api/` 或 `/actuator/` 开头，否则直接抛错。

所以页面代码只写 `/api/v1/...` 这种网关路径，不写 `8081`、`8082`、`8083`、`8084`，也不访问 MySQL 或 Redis。

### 问题 8：金额为什么总是 `amountFen`，页面上才显示“元”？

这是资金系统的统一约束：接口和计算都用整数分，避免 JavaScript 小数精度问题。前端只在展示边界用 `formatAmount()` 或 `formatAmountFen()` 把分转成元字符串；输入金额时也要转回整数分再提交。

例如还款页用户输入“12.34”，前端提交给后端的应该是 `1234` 分。我的后端信用模型、账单和还款分配也全部使用 `long` 分，避免前后端金额口径不一致。

### 问题 9：如果接口失败，页面会不会把失败当成 0 或成功？

不会。H5 请求层会把错误包装成 `ApiError`，页面一般用 Toast 或失败状态提示。B 端看板更明确：`Dashboard/index.tsx` 如果汇总请求失败，会展示“看板汇总数据加载失败”，不会把交易额、成功率、服务健康伪造成 0 或正常。

这点适合和后端资金一致性一起讲：**前端展示也遵循事实优先，未知就是未知，不用假数据掩盖。**

### 问题 10：你明天前端部分最应该强调什么？

强调三句话：

1. **页面所有权不是我的，但信用/账单/还款事实是我的后端分工。**
2. **前端卡片只展示事实，不计算资金终态，不保存敏感材料。**
3. **我负责用跨服务测试和故障注入证明：H5、B 端、AI 这些入口最终看到的交易状态、余额/额度变化和账本事实是一致的。**
