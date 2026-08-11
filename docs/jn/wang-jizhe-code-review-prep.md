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
