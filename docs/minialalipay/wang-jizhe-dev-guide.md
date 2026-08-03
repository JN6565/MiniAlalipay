# 王基哲个人开发文档：信用业务与质量保障

## 1. 文档目的

本文档依据 MiniAlalipay PRD V1.9、系统分析 V1.3、数据库设计、团队任务分工设计和当前仓库代码现状，为王基哲在 P0 阶段的全部任务提供开发指导。

本文档只梳理已有需求和契约中已定义的能力，不新增或改变产品功能、接口字段、状态流转、数据库表、安全规则或部署依赖。如实现中发现契约缺失或矛盾，应先提出契约变更（由王钧平合入），再开发依赖该契约的代码。

---

## 2. 角色与职责概述

| 维度    | 内容                                                                    |
| ----- | --------------------------------------------------------------------- |
| 角色    | 信用业务与质量负责人                                                            |
| 代码所有权 | `account-center` 的 `credit`、`bill`、`repayment` 子域包；`tests/`；`deploy/` |
| 最终合入  | 信用子域代码由王钧平最终合入；`tests/` 和 `deploy/` 由王基哲自行合入                          |
| 核心交付  | Mini 花呗额度/账单/还款、跨服务测试、故障注入、本地部署配置                                     |
| 交叉评审  | 负责对王钧平的余额/TCC/账本/交易状态机代码进行并发和故障交叉验证                                   |

### 2.1 不可逾越的边界

1. **信用子域不得绕过账户和账本应用入口**直接修改 `account_balance`、`ledger_voucher` 或 `ledger_entry` 表。信用额度变更、应收增减和还款扣款必须通过王钧平提供的账户/TCC/账本应用接口完成资金联动。
2. **账户中心不创建业务交易主单**，也不自行决定业务订单终态。交易主单和终态发布由 `business-center` 负责。
3. **账本修复只能通过新冲正分录完成**，不修改或删除原分录。
4. **`credit_account` 的额度字段只能由信用领域服务修改**；账单汇总不得反向改写已确认的信用消费事实。
5. 信用子域不得导入 `business-center` 的 Mapper、仓储、PO、实体或聚合根；跨上下文只能通过版本化 HTTP/OpenAPI 契约或 Outbox 事件交互。

---

## 3. 任务总览与依赖关系

### 3.1 任务清单

| 编号   | 任务                      | 所属阶段  | 依赖                  | 交付物                   |
| ---- | ----------------------- | ----- | ------------------- | --------------------- |
| T-01 | 信用领域模型与枚举               | 阶段三   | 阶段一契约已冻结            | 领域实体、值对象、状态枚举         |
| T-02 | 信用 Flyway 迁移            | 阶段三   | T-01                | `V*.sql` 迁移文件（王钧平合入）  |
| T-03 | 信用 TCC 分支参与者            | 阶段四   | 王钧平的 TCC 内核         | Try/Confirm/Cancel 实现 |
| T-04 | 信用查询与还款应用服务             | 阶段五   | T-01、T-03、账户/账本应用接口 | 应用服务、DTO              |
| T-05 | 信用 Controller 与 OpenAPI | 阶段五   | T-04、OpenAPI 操作已补齐  | Controller、API DTO    |
| T-06 | 出账与到期检查任务               | 阶段五   | T-01、T-04           | 定时/手动任务、幂等            |
| T-07 | 跨服务 API 契约测试            | 阶段七   | 各服务 Controller 已实现  | 契约校验脚本                |
| T-08 | E2E 端到端测试               | 阶段七   | 全链路可经网关访问           | 至少 24 条场景             |
| T-09 | 故障注入测试                  | 阶段七   | T-08                | 故障注入脚本                |
| T-10 | 性能与安全测试                 | 阶段七   | T-08                | 性能报告、安全验证             |
| T-11 | 本地部署配置完善                | 阶段二   | 无                   | docker-compose、可观测配置  |
| T-12 | 交叉评审王钧平资金内核             | 阶段三~四 | 王钧平代码提交             | 评审意见、测试验证             |

### 3.2 依赖说明

```
阶段一（已完成）─ 契约冻结
    │
    ▼
阶段二 ── T-11 本地部署配置
    │
    ▼
阶段三 ── T-01 信用领域模型 ── T-02 Flyway迁移
    │                         │
    │                         ▼
    │              T-12 交叉评审（余额/账本内核）
    │                         │
    ▼                         ▼
阶段四 ── T-03 信用TCC分支 ── T-12 交叉评审（交易状态机/TCC全局）
    │
    ▼
阶段五 ── T-04 信用应用服务 ── T-05 Controller ── T-06 出账/到期任务
    │
    ▼
阶段七 ── T-07 契约测试 ── T-08 E2E ── T-09 故障注入 ── T-10 性能/安全
```

**超前开发限制：**

- 阶段四统一交易和账户应用接口未完成前，只能完成领域草图、Schema 和 Mock，不得写扣款、入账或账本逻辑。
- OpenAPI 操作未补齐前，不得实现依赖该操作的 Controller。
- 网关路由只能指向已存在且通过测试的接口。

---

## 4. 信用子域开发指南

### 4.1 包结构与分层

当前 `account-center` 已有分层骨架，信用子域代码应按以下结构组织：

```
account-center/src/main/java/com/minialalipay/account/
├── domain/credit/          # 信用领域层
│   ├── CreditAccountStatus.java       # [已存在] 额度账户状态枚举
│   ├── CreditAccount.java             # 额度账户聚合根
│   ├── CreditFreeze.java              # 额度冻结记录（TCC分支）
│   ├── CreditFreezeStatus.java        # 冻结状态枚举
│   ├── CreditReceivable.java          # 信用应收（与已用额度对等）
│   ├── CreditPurchase.java            # 信用消费明细
│   ├── CreditPurchaseBillingStatus.java # 消费出账状态枚举
│   ├── CreditAccountDomainService.java # 额度冻结/占用/释放领域服务
│   └── package-info.java
├── domain/bill/            # 账单领域层
│   ├── CreditBill.java                # 月度账单聚合根
│   ├── CreditBillStatus.java          # 账单状态枚举
│   ├── CreditBillItem.java            # 账单与消费关联
│   ├── CreditBillItemStatus.java      # 明细状态枚举
│   ├── BillDomainService.java         # 出账、金额汇总领域服务
│   └── package-info.java
├── domain/repayment/       # 还款领域层
│   ├── CreditRepayment.java           # 还款记录
│   ├── CreditRepaymentStatus.java     # 还款状态枚举
│   ├── CreditRepaymentAllocation.java # 还款分配
│   ├── CreditRepaymentAllocationDetail.java # 分配明细
│   ├── CreditRepaymentDraft.java      # 还款草稿（确认前快照）
│   ├── CreditRepaymentDraftStatus.java # 草稿状态枚举
│   ├── RepaymentAllocationType.java   # 分配目标类型枚举
│   ├── RepaymentDomainService.java    # 分配计算领域服务
│   └── package-info.java
├── application/credit/     # 信用应用层
│   ├── CreditQueryService.java        # 额度/消费/账单查询
│   ├── CreditRepaymentService.java    # 还款草稿与提交
│   ├── CreditJobService.java          # 出账/到期检查任务
│   ├── CreditTccParticipant.java      # 信用TCC分支参与者
│   ├── command/                       # 命令对象
│   └── dto/                           # API DTO
├── infrastructure/credit/  # 信用基础设施层
│   ├── CreditAccountRepositoryImpl.java
│   ├── CreditFreezeRepositoryImpl.java
│   ├── CreditReceivableRepositoryImpl.java
│   ├── CreditPurchaseRepositoryImpl.java
│   ├── CreditBillRepositoryImpl.java
│   ├── CreditRepaymentRepositoryImpl.java
│   ├── CreditRepaymentDraftRepositoryImpl.java
│   ├── CreditJobRunRepositoryImpl.java
│   ├── po/                            # 持久化对象
│   │   ├── CreditAccountPO.java
│   │   ├── CreditFreezePO.java
│   │   ├── CreditReceivablePO.java
│   │   ├── CreditPurchasePO.java
│   │   ├── CreditBillPO.java
│   │   ├── CreditBillItemPO.java
│   │   ├── CreditRepaymentPO.java
│   │   ├── CreditRepaymentAllocationPO.java
│   │   ├── CreditRepaymentAllocationDetailPO.java
│   │   ├── CreditRepaymentDraftPO.java
│   │   └── CreditJobRunPO.java
│   └── mapper/                        # MyBatis Mapper接口
├── interfaces/credit/      # 信用接口层
│   ├── CreditController.java          # /api/v1/credit/**
│   ├── CreditOpsController.java       # /api/v1/ops/credit/**
│   └── dto/                           # 请求/响应DTO
```

### 4.2 领域模型设计

#### 4.2.1 CreditAccount（额度账户聚合根）

**业务含义：** Mini 花呗固定虚拟授信额度账户，每用户唯一，固定 5000 元（500000 分）。

**关键不变量：**

- `total_limit_fen = 500000`（固定值，数据库 CHECK 约束已保证）
- `available = total - used - frozen`，四项均不得为负
- `used + frozen <= total`
- 状态：`ACTIVE`（正常）、`SUSPENDED`（逾期暂停）、`CLOSED`（已关闭）

**核心方法：**

- `freeze(amountFen)`：Try 阶段冻结额度，校验可用额度充足
- `confirmFreeze(amountFen)`：Confirm 阶段冻结转已用
- `releaseFreeze(amountFen)`：Cancel 阶段释放冻结
- `restoreByRepayment(amountFen)`：还款后恢复可用额度（减少已用）
- `suspend(reason)` / `activate()`：状态流转

> **注意：** 额度不是余额，不计入用户可用余额。`CreditAccount` 只管理额度数字，不触碰 `account_balance` 表。

#### 4.2.2 CreditFreeze（额度冻结记录）

**业务含义：** `CREDIT_PAY` 的 TCC 分支冻结记录，保证 Try/Confirm/Cancel 幂等。

**状态：** `FROZEN` → `CONFIRMED`（Confirm 后）或 `RELEASED`（Cancel 后）

**唯一键：** `(transaction_id, credit_account_id)`，保证同一交易同一账户只有一条冻结记录。

#### 4.2.3 CreditReceivable（信用应收）

**业务含义：** 用户使用 Mini 花呗后平台形成的虚拟应收资产，与已用额度对等。

**关键不变量：**

- `used_fen = receivable_outstanding = unbilled_remaining + sum(bill.outstanding)`
- `overdue_fen <= billed_fen`
- 应收分布在 `ledger_db`（不是 `account_db`）

**字段：** `unbilled_fen`（未出账）、`billed_fen`（已出账）、`overdue_fen`（逾期，是 billed 的子集）

#### 4.2.4 CreditPurchase（信用消费明细）

**业务含义：** 每笔成功 `CREDIT_PAY` 生成一条不可重复的消费明细。

**唯一键：** `credit_transaction_id`（一笔支付不能重复进入账单）

**出账状态：** `UNBILLED` → `BILLED`（月度出账）或 `UNBILLED` → `REPAID`（提前还清）→ `BILLED` → `REPAID`

#### 4.2.5 CreditBill（月度账单聚合根）

**业务含义：** 按自然月管理，每月 1 日生成上月账单，10 日 23:59:59 到期。

**关键不变量：**

- `total = paid + outstanding`
- 唯一键：`(credit_account_id, period)`
- 状态：`OPEN` → `PARTIALLY_PAID` / `PAID` / `OVERDUE`

**核心方法：**

- `applyRepayment(amountFen)`：应用还款，更新 paid/outstanding 和状态

#### 4.2.6 CreditRepaymentDraft（还款草稿）

**业务含义：** 还款确认前的金额与分配快照，绑定一次性确认令牌。

**关键约束：**

- 金额不得超过虚拟可用余额或信用应收
- `allocation_hash` 绑定分配预览，确认令牌必须与同一快照匹配
- 草稿有过期时间

#### 4.2.7 CreditRepaymentAllocation（还款分配）

**分配顺序（固定规则，Try 阶段固化，Confirm 不得重新计算）：**

1. 逾期账单（`OVERDUE_BILL`）→ 按最早到期时间
2. 已出账账单（`BILL`）→ 按最早出账时间
3. 未出账消费（`UNBILLED`）→ 按最早发生时间

### 4.3 状态机设计

#### 4.3.1 额度账户状态机

```
ACTIVE ──存在逾期账单──> SUSPENDED
SUSPENDED ──逾期账单全部还清──> ACTIVE（无人工冻结时）
ACTIVE/SUSPENDED ──关闭──> CLOSED（终态）
```

**规则：**

- `SUSPENDED` 只禁止新的 `CREDIT_PAY`，不得阻止余额支付、`TRANSFER`、查询或 `CREDIT_REPAY`
- 所有逾期账单还清后自动恢复 `ACTIVE`

#### 4.3.2 账单状态机

```
OPEN ──部分还款──> PARTIALLY_PAID
OPEN ──全额还款──> PAID
PARTIALLY_PAID ──全额还款──> PAID
OPEN/PARTIALLY_PAID ──到期未还清──> OVERDUE
OVERDUE ──部分还款──> PARTIALLY_PAID
OVERDUE ──全额还款──> PAID
```

#### 4.3.3 消费明细状态机

```
UNBILLED ──月度出账──> BILLED
UNBILLED ──提前还清──> REPAID
BILLED ──分配后剩余为0──> REPAID
```

### 4.4 TCC 分支参与者设计

信用子域需要实现两个 TCC 分支参与者，由王钧平的 TCC 全局协调器调用。

#### 4.4.1 CREDIT_PAY 信用支付参与者

| 阶段      | 信用子域职责                                                                    | 联动                             |
| ------- | ------------------------------------------------------------------------- | ------------------------------ |
| Try     | 冻结信用额度（`CreditAccount.freeze`），创建 `CreditFreeze(FROZEN)`                  | 账户中心余额/账本分支并行执行收款用户预占          |
| Confirm | 冻结转已用（`CreditAccount.confirmFreeze`），增加信用应收，创建 `CreditPurchase(UNBILLED)` | 账户中心账本分支写"借：信用应收资产；贷：收款用户余额负债" |
| Cancel  | 释放冻结（`CreditAccount.releaseFreeze`），`CreditFreeze(RELEASED)`              | 账户中心释放收款用户预占                   |

**幂等保障：**

- `credit_freeze` 的 `(transaction_id, credit_account_id)` 唯一键
- Try 幂等：已存在 FROZEN 记录时直接返回成功
- Confirm 幂等：已存在 CONFIRMED 记录时直接返回成功
- Cancel 幂等：已存在 RELEASED 记录时直接返回成功
- 空回滚：Try 未执行时 Cancel 应记录悬挂日志并快速返回
- 防悬挂：Try 在 Cancel 之后到达时应拒绝执行

#### 4.4.2 CREDIT_REPAY 信用还款参与者

| 阶段      | 信用子域职责                     | 联动                                |
| ------- | -------------------------- | --------------------------------- |
| Try     | 锁定还款分配计划，预占信用应收减少          | 账户中心冻结用户虚拟余额，账本预占还款凭证             |
| Confirm | 减少应收/已用额度，恢复可用额度，更新账单/明细状态 | 账户中心扣减虚拟余额，账本写"借：用户余额负债；贷：信用应收资产" |
| Cancel  | 取消分配预占                     | 账户中心释放余额冻结                        |

**分配金额守恒：**

- `sum(allocation.amount) = repayment.amount`
- 还款金额 = 信用应收减少 = 已用额度减少 = 可用额度恢复 = 虚拟余额减少

### 4.5 接口设计

根据 P0 接口目录，信用子域需要实现以下 9 个端点：

#### 4.5.1 C 端信用接口

| 方法   | 路径                                | operationId                  | 幂等                   | 说明                      |
| ---- | --------------------------------- | ---------------------------- | -------------------- | ----------------------- |
| GET  | `/api/v1/credit/me`               | `getMyCredit`                | 只读                   | 查询本人额度摘要（总/已用/冻结/可用+应收） |
| GET  | `/api/v1/credit/purchases`        | `listCreditPurchases`        | 只读                   | 查询未出账及历史消费（游标分页、状态筛选）   |
| GET  | `/api/v1/credit/bills`            | `listCreditBills`            | 只读                   | 查询账单列表（账期、状态、游标分页）      |
| GET  | `/api/v1/credit/bills/{id}`       | `getCreditBill`              | 只读                   | 查询账单明细和还款分配             |
| POST | `/api/v1/credit/repayment-drafts` | `createCreditRepaymentDraft` | `Idempotency-Key` 必填 | 创建还款草稿和分配预览             |
| POST | `/api/v1/credit/repayments`       | `submitCreditRepayment`      | `Idempotency-Key` 必填 | 提交还款（密码证明+确认令牌+TCC+账本）  |
| GET  | `/api/v1/credit/repayments/{id}`  | `getCreditRepayment`         | 只读                   | 查询还款状态和额度恢复             |

#### 4.5.2 B 端运维接口

| 方法   | 路径                                  | operationId          | 幂等     | 说明             |
| ---- | ----------------------------------- | -------------------- | ------ | -------------- |
| POST | `/api/v1/ops/credit/statement-runs` | `runCreditStatement` | 业务日期幂等 | 触发出账任务（管理员+审计） |
| POST | `/api/v1/ops/credit/due-check-runs` | `runCreditDueCheck`  | 业务日期幂等 | 触发到期检查（管理员+审计） |

**接口约束：**

- 客户端不能提交 `creditAccountId`、收款账户、账单分配结果或额度变更值
- 还款分配由服务端按固定顺序生成，以 `allocationHash` 绑定确认
- 所有接口经网关访问，禁止直连 8083 端口
- 金额字段统一使用整数 `amountFen`，禁止 `float`/`double`
- 支付密码证明和确认令牌不得写入日志、URL 或浏览器存储

### 4.6 数据库迁移

**迁移文件位置：** `backend/account-center/src/main/resources/db/migration/`

**命名规范：** `VYYYYMMDDHHMM__lower_snake_case_description.sql`

**已有表结构：** `deploy/mysql/init/00-create-schemas.sql` 中已包含全部信用表定义（`credit_account`、`credit_freeze`、`credit_receivable`、`credit_purchase`、`credit_bill`、`credit_bill_item`、`credit_repayment`、`credit_repayment_allocation`、`credit_repayment_allocation_detail`、`credit_repayment_draft`、`credit_job_run`）。

**迁移要点：**

- 迁移文件由王基哲编写，王钧平最终审核和合入
- 创建迁移前在团队内登记完整文件名，避免时间戳和名称冲突
- 已执行迁移不可修改、删除或重命名
- 涉及金额、信用表时必须同步数据库设计文档和系统分析
- 必须为查询和幂等路径建立索引

**建议迁移文件：**

```
V202608050900__create_credit_account_tables.sql     # credit_account, credit_freeze, credit_receivable
V202608050910__create_credit_purchase_tables.sql    # credit_purchase
V202608050920__create_credit_bill_tables.sql        # credit_bill, credit_bill_item
V202608050930__create_credit_repayment_tables.sql   # credit_repayment, credit_repayment_allocation, credit_repayment_allocation_detail, credit_repayment_draft
V202608050940__create_credit_job_tables.sql          # credit_job_run
```

### 4.7 事件契约

信用子域需要发布以下事件（已定义于 `contracts/events/event-types.yaml`）：

| 事件类型                              | 版本  | 触发时机                     |
| --------------------------------- | --- | ------------------------ |
| `credit.account.opened`           | 1.0 | 用户开户时自动创建信用额度账户          |
| `credit.limit.changed`            | 1.0 | 额度状态变更（ACTIVE↔SUSPENDED） |
| `credit.purchase.posted`          | 1.0 | `CREDIT_PAY` Confirm 成功后 |
| `credit.bill.generated`           | 1.0 | 月度出账任务完成后                |
| `credit.bill.overdue`             | 1.0 | 到期检查任务标记逾期后              |
| `credit.repayment.status_changed` | 1.0 | 还款状态变更后                  |

事件必须通过 Outbox 模式同事务发布，保证事实与事件一致。

### 4.8 错误码

已在 `contracts/error-codes/error-codes.yaml` 中定义，信用子域必须在服务内建立对应的错误码枚举：

| 错误码                         | HTTP | 含义               | 重试策略  |
| --------------------------- | ---- | ---------------- | ----- |
| `CREDIT_ACCOUNT_NOT_FOUND`  | 404  | 信用账户不存在          | 否     |
| `CREDIT_NOT_AVAILABLE`      | 422  | Mini 花呗未开通/关闭/暂停 | 否     |
| `CREDIT_LIMIT_INSUFFICIENT` | 422  | 可用额度不足           | 调整金额后 |
| `CREDIT_OVERDUE`            | 422  | 存在逾期，禁止信用支付      | 还款后   |
| `BILL_NOT_FOUND`            | 404  | 信用账单不存在          | 否     |
| `REPAYMENT_AMOUNT_INVALID`  | 422  | 还款额超过余额/应收或小于1分  | 调整金额后 |
| `REPAYMENT_NOT_FOUND`       | 404  | 还款记录不存在          | 否     |

**强制要求：** 错误码枚举的 `code`、`message`、`httpStatus` 必须与 YAML 完全一致，并增加契约测试逐项校验。

---

## 5. 跨服务测试指南

### 5.1 测试范围

`tests/` 目录用于存放跨服务接口测试、端到端测试、性能测试和故障注入测试。资金测试必须同时断言交易状态、双方账户余额和复式账本分录，仅验证 HTTP 返回成功不能证明资金处理正确。

### 5.2 测试分层

| 层级        | 内容                    | 数量要求           | 工具建议                         |
| --------- | --------------------- | -------------- | ---------------------------- |
| API 契约测试  | 校验各服务接口与 OpenAPI 一致   | 全部 P0 接口       | PowerShell 脚本 / REST Assured |
| E2E 端到端测试 | 从 H5/B 端/AI 入口经网关完整链路 | ≥ 24 条（花呗 ≥ 5） | Newman / Karate              |
| 故障注入测试    | 余额/额度不足、密码错误、超时、重复请求等 | ≥ 11 类         | Testcontainers / Chaos       |
| 性能测试      | 关键接口响应时间、并发           | 见 PRD 指标       | JMeter / k6                  |
| 安全测试      | 越权、自付、信用绕过、敏感数据       | 覆盖全部安全规则       | 手动 + 自动化                     |

### 5.3 E2E 场景清单（Mini 花呗部分）

以下场景必须覆盖，对应 PRD 验收用例 AT-41 至 AT-53：

| 场景              | PRD 用例 | 验证要点                                                    |
| --------------- | ------ | ------------------------------------------------------- |
| 开户自动获得 5000 元额度 | AT-41  | `total=available`，`used/frozen=0`，不计入虚拟余额               |
| 扫码花呗支付 88 元     | AT-42  | `CREDIT_PAY SUCCESS`；余额不变、已用+88、收款用户余额+88、应收+88且借贷平衡    |
| 可用额度不足          | AT-43  | 确认前拒绝，不创建交易，不改变余额/额度/账本                                 |
| 并发余额/花呗支付同一订单   | AT-44  | `source_type + source_order_id` 唯一约束保证仅一种来源成功           |
| Try 失败 Cancel   | AT-45  | 释放额度冻结，已用/收款用户余额/应收均不变                                  |
| 重复执行出账任务        | AT-46  | 每个账期只产生一张账单，每笔消费只汇总一次                                   |
| 部分还款 30 元       | AT-47  | `CREDIT_REPAY SUCCESS`；余额-30、应收/已用-30、可用+30、账单部分已还且分录平衡 |
| 还款金额超限          | AT-48  | 确认前拒绝，账单/额度/余额/账本均不变化                                   |
| 重复提交还款 100 次    | AT-49  | 只产生一笔还款交易和一组分配/分录                                       |
| 到期未还            | AT-50  | 账单 `OVERDUE`、额度 `SUSPENDED`；信用支付拒绝但余额支付和还款可用            |
| 还清逾期            | AT-51  | 账单 `PAID`、应收减少；额度恢复 `ACTIVE`                            |
| AI 无确认还款        | AT-52  | 策略网关拒绝；只允许生成草稿和可信确认卡片                                   |
| 额度/应收差异         | AT-53  | 1 分钟内产生 P0 告警，阻断信用指标发布                                  |

### 5.4 故障注入清单

| 故障类型          | 注入方式        | 预期行为                              |
| ------------- | ----------- | --------------------------------- |
| 余额不足          | 构造超额还款      | `INSUFFICIENT_BALANCE`，不创建交易      |
| 额度不足          | 构造超额信用支付    | `CREDIT_LIMIT_INSUFFICIENT`，不创建交易 |
| 支付密码错误        | 传入错误密码证明    | `PAY_PASSWORD_INVALID`，不创建交易      |
| 网络超时          | 模拟 TCC 分支超时 | 全局事务 Cancel，释放所有预占                |
| 重复请求          | 同幂等键提交100次  | 只产生一笔交易                           |
| CAS 竞争        | 并发提交不同资金来源  | 唯一约束保证仅一笔成功                       |
| Redis 不可用     | 停止 Redis 容器 | 回源 MySQL，不放宽校验                    |
| Outbox 不可用    | 模拟事件发布失败    | 交易回滚或转人工                          |
| 数据库不可用        | 停止 MySQL 容器 | 返回 500，不产生部分写入                    |
| 服务重启          | 中途重启服务      | 恢复任务接续，最终收敛                       |
| 部分 Confirm 失败 | 模拟账本分支失败    | 全局事务 Cancel，额度/应收恢复               |

### 5.5 测试组织

```
tests/
├── README.md                              # [已存在]
├── contracts/                             # 契约校验
│   └── validate-stage-one-contracts.ps1   # [已存在]
├── e2e/                                   # 端到端测试
│   ├── credit-pay-flow.http               # 花呗支付完整流程
│   ├── credit-repay-flow.http             # 花呗还款完整流程
│   ├── credit-bill-statement.http         # 出账任务流程
│   └── ...
├── fault-injection/                       # 故障注入
│   ├── concurrent-credit-pay.ps1          # 并发信用支付
│   ├── tcc-cancel-recovery.ps1            # TCC Cancel 恢复
│   └── ...
├── performance/                           # 性能测试
│   └── ...
└── security/                              # 安全测试
    ├── cross-user-bill-access.ps1         # 越权查询账单
    ├── self-credit-pay.ps1                # 自付信用支付
    └── ...
```

### 5.6 测试编写规范

1. **资金测试三断言：** 每个资金场景必须同时断言交易状态、账户余额/额度变化和账本分录平衡。
2. **幂等验证：** 重复提交场景必须断言只产生一笔交易和一组分录。
3. **状态收敛：** 故障场景必须验证最终状态收敛（不残留处理中状态）。
4. **敏感数据检查：** 验证响应、日志和 URL 中不含支付密码、确认令牌或二维码原始令牌。
5. **未知结果不展示成功：** 处理中状态不得展示为成功。

---

## 6. 本地部署指南

### 6.1 当前部署配置

`deploy/docker-compose.yml` 已配置以下组件：

| 组件           | 镜像                           | 端口   | 用途               |
| ------------ | ---------------------------- | ---- | ---------------- |
| MySQL        | `mysql:8.4`                  | 3306 | 全部逻辑库（6个Schema）  |
| Redis        | `redis:7.4-alpine`           | 6379 | 缓存、限流            |
| Seata Server | `seataio/seata-server:2.0.0` | 8091 | TCC/Saga 分布式事务协调 |

### 6.2 数据库初始化

`deploy/mysql/init/00-create-schemas.sql` 已包含全部表结构定义，包括信用相关 11 张表。`01-add-chinese-comments.sql` 提供中文注释。

**启动命令：**

```bash
cd deploy
docker compose up -d
```

### 6.3 需要补充的部署配置

| 项目       | 说明                                            | 优先级 |
| -------- | --------------------------------------------- | --- |
| Seata 配置 | 配置 `file.conf` 和 `registry.conf`，注册各服务 TCC 分支 | P0  |
| 可观测组件    | 可选增加 Prometheus + Grafana + Jaeger 容器         | P1  |
| 环境变量模板   | 创建 `.env.example` 文件                          | P0  |
| 健康检查脚本   | 各服务启动后验证 `/actuator/health`                   | P0  |

### 6.4 Seata 配置要点

Seata Server 当前使用 `STORE_MODE: file`（文件存储），适用于本地开发。各服务需要：

1. 引入 `seata-spring-boot-starter` 依赖（向王钧平提出，独立 PR 合入 `backend/pom.xml`）
2. 配置 `application.yml` 中的 Seata 注册中心和事务组
3. 信用 TCC 分支参与者通过 `@TwoPhaseBusinessAction` 注解注册

> **注意：** `backend/pom.xml` 是共享热点文件，依赖变更必须独立提交，由王钧平合入。

---

## 7. 交叉评审指南

### 7.1 评审对象与重点

作为质量负责人，你需要对王钧平的以下代码进行交叉评审：

| PR 类型                         | 评审重点                    |
| ----------------------------- | ----------------------- |
| 余额、TCC 分支、账本和冲正               | 幂等、并发、空回滚、防悬挂、借贷平衡、资金恢复 |
| 交易状态机、TCC 全局协调、Saga、终态发布、对账核心 | 交易意图、冻结释放、账本事实、异常收敛     |

### 7.2 评审检查清单

#### 余额与 TCC 分支

- [ ] Try 阶段是否使用 CAS（`version` 字段）保证并发安全
- [ ] Confirm/Cancel 是否幂等（重复调用不产生副作用）
- [ ] Cancel 是否处理空回滚（Try 未执行时 Cancel 到达）
- [ ] Try 是否防悬挂（Cancel 之后 Try 到达时拒绝执行）
- [ ] 余额变更是否使用 `long` 分，不使用 `float`/`double`
- [ ] 冻结记录是否有唯一键防止重复冻结
- [ ] 余额不会出现负数（数据库 CHECK 约束 + 领域校验）

#### 账本与冲正

- [ ] 每张凭证 `total_debit = total_credit` 且 `> 0`
- [ ] 冲正只创建反向凭证，不修改或删除原凭证
- [ ] 凭证状态不可逆（`POSTED` 不可回退）
- [ ] 信用交易的会计平衡：`CREDIT_PAY` 收款用户余额增加 = 信用应收增加；`CREDIT_REPAY` 余额减少 = 应收减少

#### 交易状态机与终态发布

- [ ] 终态发布器是否在验证全部 TCC 分支 `CONFIRMED` 和账本平衡后才发布 `SUCCESS`
- [ ] 交易终态不提前于 TCC 分支和账本事实发布
- [ ] `source_type + source_order_id` 唯一约束是否在数据库和领域层双重保障
- [ ] 恢复扫描是否能正确处理"已完成余额扣款但未完成入账"的场景
- [ ] 对账核心是否能检测额度/应收/账单差异

### 7.3 交叉验证测试

除了代码评审，你还需要通过测试验证王钧平的代码：

| 测试场景           | 验证目标                           |
| -------------- | ------------------------------ |
| 余额并发扣减         | CAS 版本控制正确，不超扣                 |
| TCC 重复 Confirm | 幂等，不重复扣款                       |
| TCC 空回滚        | Try 未执行时 Cancel 正常处理           |
| TCC 悬挂         | Cancel 后 Try 被拒绝               |
| 重复提交           | 幂等键保证只一笔交易                     |
| 账本平衡           | 每笔成功交易借贷相等                     |
| 终态发布前置         | TCC 未全部 CONFIRMED 时不发布 SUCCESS |
| 服务重启恢复         | 恢复任务正确接续未完成事务                  |

---

## 8. 开发顺序与里程碑

### 8.1 阶段二（服务骨架）

**任务 T-11：本地部署配置完善**

1. 验证 `docker-compose.yml` 能正常启动 MySQL、Redis、Seata
2. 验证 `00-create-schemas.sql` 能正确创建全部信用表
3. 创建 `.env.example` 环境变量模板
4. 编写各服务健康检查验证脚本

**交付条件：** `docker compose up -d` 后全部容器健康，MySQL 中 6 个 Schema 和全部表已创建。

### 8.2 阶段三（用户、账户和数据基础）

**任务 T-01：信用领域模型与枚举**

1. 在 `domain/credit/` 下创建 `CreditAccount` 聚合根、`CreditFreeze`、`CreditReceivable`、`CreditPurchase` 及对应枚举
2. 在 `domain/bill/` 下创建 `CreditBill`、`CreditBillItem` 及枚举
3. 在 `domain/repayment/` 下创建 `CreditRepayment`、`CreditRepaymentAllocation`、`CreditRepaymentDraft` 及枚举
4. 编写领域模型单元测试（状态流转、不变量校验）

**任务 T-02：信用 Flyway 迁移**

1. 在 `account-center/src/main/resources/db/migration/` 下创建迁移文件
2. 迁移内容从 `00-create-schemas.sql` 中提取信用相关表定义
3. 提交给王钧平审核和合入

**任务 T-12（部分）：交叉评审余额/账本内核**

1. 评审王钧平的 `Account`、`AccountBalance`、`LedgerVoucher` 领域模型
2. 验证余额 CAS 并发安全
3. 验证账本借贷平衡不变量
4. 编写并发测试用例

**交付条件：** 信用领域模型单元测试通过；Flyway 迁移在容器中验证通过；余额/账本不变量测试通过。

### 8.3 阶段四（统一交易内核）

**任务 T-03：信用 TCC 分支参与者**

1. 实现 `CreditTccParticipant`（`CREDIT_PAY` 分支）
2. 实现还款 TCC 分支参与者（`CREDIT_REPAY` 分支）
3. 保证 Try/Confirm/Cancel 幂等、空回滚、防悬挂
4. 编写 TCC 分支模块测试

**任务 T-12（续）：交叉评审交易状态机/TCC 全局**

1. 评审终态发布器逻辑
2. 验证 `source_type + source_order_id` 唯一约束
3. 编写 TCC 重试、Cancel 恢复测试
4. 验证服务重启后恢复扫描

**交付条件：** TCC 分支测试通过；重复请求、余额不足、Confirm 超时、Cancel 重试、服务重启和账本不平测试通过。

### 8.4 阶段五（业务场景子域）

**任务 T-04：信用查询与还款应用服务**

1. 实现 `CreditQueryService`（额度摘要、消费明细、账单列表、账单详情）
2. 实现 `CreditRepaymentService`（创建草稿+分配预览、提交还款、查询还款状态）
3. 实现 `CreditJobService`（出账任务、到期检查任务）
4. 编写应用服务测试

**任务 T-05：信用 Controller 与 OpenAPI**

1. 在 OpenAPI YAML 中补齐 9 个信用操作的完整 Schema
2. 实现 `CreditController` 和 `CreditOpsController`
3. Controller 只接收和返回 API DTO，禁止返回 PO 或聚合根
4. 编写接口契约测试

**任务 T-06：出账与到期检查任务**

1. 实现月度出账逻辑（`UNBILLED → BILLED`，幂等汇总）
2. 实现到期检查逻辑（未还账单 → `OVERDUE`，额度 → `SUSPENDED`）
3. 任务以 `(job_type, business_date)` 幂等
4. 重复运行不重复建账单或重复改变金额

**交付条件：** 每个场景具备模块测试、错误码和迁移；场景不创建第二套扣款逻辑或交易状态机。

### 8.5 阶段七（跨服务联调）

**任务 T-07 至 T-10：跨服务测试**

1. 编写 API 契约测试（全部 P0 接口）
2. 编写 E2E 测试（≥ 24 条，花呗 ≥ 5 条）
3. 编写故障注入测试（≥ 11 类）
4. 编写性能和安全测试
5. 从 H5、B 端和 AI 入口统一经网关验证完整链路

**交付条件：** 全部跨服务测试通过；P0 完整功能允许合并。

---

## 9. 关键不变量速查

### 9.1 信用不变量

| 不变量  | 公式                                                                           | 验证时机           |
| ---- | ---------------------------------------------------------------------------- | -------------- |
| 额度恒等 | `total = available + used + frozen`                                          | 每次额度变更后        |
| 应收恒等 | `used = receivable_outstanding = unbilled_remaining + sum(bill.outstanding)` | 每次消费/还款后       |
| 交易净额 | `累计 CREDIT_PAY - CREDIT_REPAY - 信用冲正 = 信用应收账本净额`                             | 对账任务           |
| 账单金额 | `total = paid + outstanding`                                                 | 每次还款后          |
| 还款守恒 | `还款金额 = 应收减少 = 已用减少 = 可用恢复 = 余额减少`                                           | 每次还款 Confirm 后 |
| 借贷平衡 | `CREDIT_PAY: 借应收 = 贷收款用户余额`；`CREDIT_REPAY: 借用户余额 = 贷应收`                      | 每次过账后          |

### 9.2 安全不变量

| 不变量      | 说明                                                   |
| -------- | ---------------------------------------------------- |
| 额度不计入余额  | `CreditAccount` 的额度字段不影响 `account_balance`           |
| 信用支付不扣余额 | `CREDIT_PAY` 成功后用户虚拟余额不变                             |
| 不可自付     | 扫自己的码返回 `SELF_PAYMENT_FORBIDDEN`                     |
| C2C 不可信用 | C2C 提交 `MINI_CREDIT` 返回 `FUNDING_SOURCE_NOT_ALLOWED` |
| 逾期停信用    | `SUSPENDED` 状态禁止 `CREDIT_PAY`，允许 `CREDIT_REPAY`      |
| 密码不落日志   | 支付密码证明和确认令牌不写入日志、URL 或浏览器存储                          |

---

## 10. Git 协作规范

### 10.1 分支命名

| 任务        | 分支                                 |
| --------- | ---------------------------------- |
| 信用领域模型    | `feature/account-credit-domain`    |
| 信用 TCC 分支 | `feature/account-credit-tcc`       |
| 信用查询与还款   | `feature/account-credit-bill`      |
| 跨服务测试     | `test/transaction-fault-injection` |
| 故障修复      | `fix/account-tcc-idempotency`      |

### 10.2 提交格式

```text
feat(credit): 增加Mini花呗额度冻结命令
test(credit): 覆盖重复还款确认场景
fix(credit): 修复额度释放未更新版本号问题
docs(credit): 补充信用账单出账流程说明
```

### 10.3 PR 评审

| PR 类型         | 实现人 | 复核人     | 重点                  |
| ------------- | --- | ------- | ------------------- |
| Mini 花呗、账单和还款 | 王基哲 | 王钧平     | 额度不变量、应收、还款分配和账本联动  |
| 跨服务测试         | 王基哲 | 对应业务负责人 | 验收口径、异常路径和测试数据      |
| 余额、TCC、账本     | 王钧平 | 王基哲     | 幂等、并发、空回滚、防悬挂、借贷平衡  |
| 交易状态机、TCC 全局  | 王钧平 | 王基哲     | 交易意图、冻结释放、账本事实、异常收敛 |

### 10.4 共享热点文件

以下修改必须建立独立 PR，由指定负责人合入：

| 文件                          | 合入人   | 约束           |
| --------------------------- | ----- | ------------ |
| `backend/pom.xml`           | 王钧平   | 依赖变更独立提交     |
| `backend/platform-common/`  | 王钧平   | 只增加技术通用类型    |
| `contracts/`                | 王钧平   | 先合入契约再开发依赖代码 |
| `deploy/docker-compose.yml` | 王基哲   | 独立 PR        |
| Flyway 迁移文件                 | 王钧平合入 | 王基哲编写，王钧平审核  |

---

## 11. 任务完成定义

一个任务包只有同时满足以下条件才算完成：

1. ✅ 需求编号和验收条件明确（对应 PRD FR-CR-001 至 FR-CR-005）
2. ✅ 生产代码已实现
3. ✅ 模块内测试已通过
4. ✅ OpenAPI、事件和错误码与实现一致
5. ✅ 必要的数据库迁移和数据库设计已同步
6. ✅ 错误处理、请求编号、日志脱敏和审计字段已覆盖
7. ✅ 跨服务任务已加入集成测试或 E2E 测试
8. ✅ 涉及资金时已验证幂等、TCC、复式记账、恢复和对账
9. ✅ PR 已由目录负责人及交叉复核人批准

---

## 12. 参考文档索引

| 文档          | 路径                                                            | 用途               |
| ----------- | ------------------------------------------------------------- | ---------------- |
| PRD         | `docs/minialalipay/minialalipay-prd.md`                       | 产品需求与验收标准        |
| 系统分析        | `docs/minialalipay/minialalipay-system-analysis.md`           | 架构、领域模型、时序图、状态机  |
| 数据库设计       | `docs/minialalipay/minialalipay-database-design.md`           | 表结构、索引、约束        |
| 团队分工        | `docs/minialalipay/minialalipay-team-task-division-design.md` | 代码所有权与协作规则       |
| OpenAPI 契约  | `contracts/openapi/minialalipay-api.yaml`                     | 接口定义             |
| P0 接口目录     | `contracts/openapi/p0-interface-catalog.yaml`                 | P0 接口清单          |
| 错误码         | `contracts/error-codes/error-codes.yaml`                      | 统一错误码            |
| 事件类型        | `contracts/events/event-types.yaml`                           | 事件契约             |
| 事件 Envelope | `contracts/events/event-envelope.schema.json`                 | 事件格式             |
| 项目规范        | `AGENTS.md`                                                   | 代码规范、注释规范、Git 规范 |
