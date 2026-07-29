# MiniAIalipay C2C Personal Collection Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的普通用户长期个人收款码和固定金额收款请求完整合入 PRD 与系统分析，并消除系统分析相对 PRD V1.5 的 Mini 花呗基线缺口。

**Architecture:** C2C 主动转账、个人码付款和固定请求付款统一生成 `TRANSFER`，业务中心新增个人收款码、收款请求和付款尝试聚合，账户中心继续通过 Seata TCC 执行虚拟余额转移与复式记账。长期个人码允许多笔独立订单；固定请求允许多个尝试，但使用请求 CAS、`active_order_id` 和订单来源唯一约束保证同时最多一个处理中且最终最多一笔成功。

**Tech Stack:** Markdown、Mermaid、MySQL 8 DDL、REST API、SSE、Seata TCC、Outbox/Inbox、OpenTelemetry。

## Global Constraints

- 项目周期固定为两周，团队固定为 5 人，不增加新的后端部署进程。
- 所有资金均为演示虚拟资金，不接入真实人民币、银行卡或第三方清算。
- C2C 仅允许虚拟余额，Mini 花呗不得用于普通用户转账或收款。
- C2C 手续费固定为 0。
- 不增加好友申请、双向好友关系、群收款或 AA 收款。
- 普通用户个人码与模拟商户收款码使用不同领域聚合和 API，最终分别形成 `TRANSFER` 与 `QR_PAY`/`CREDIT_PAY`。
- 资金成功状态必须由终态发布器验证 TCC、余额和复式账本事实后发布。
- 保留参考文件 `docs/superpowers/specs/MiniAIalipay.md` 和 `docs/superpowers/specs/PRD模板参考.md`，不得修改。
- 工作区不是 Git 仓库；计划中的版本控制提交步骤以变更清单和文件哈希记录替代。

---

### Task 1: Upgrade PRD Baseline And Requirement Catalog

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-minialalipay-prd.md:3`
- Reference: `docs/superpowers/specs/2026-07-29-p2p-personal-collection-design.md`

**Interfaces:**
- Consumes: 已确认的产品范围、三种 C2C 入口和两周约束。
- Produces: PRD V1.6、需求 `FR-PC-001` 至 `FR-PC-004` 和验收编号 `AT-54` 至 `AT-68`。

- [ ] **Step 1: Record the pre-change PRD structure**

Run:

```powershell
rg -n "文档版本|FR-PC-|AT-5[4-9]|AT-6[0-8]|^## " docs/superpowers/specs/2026-07-28-minialalipay-prd.md
```

Expected: document version is `V1.5`; no `FR-PC-*` or `AT-54` through `AT-68` entries exist.

- [ ] **Step 2: Upgrade metadata, goals, scope, roles and information architecture**

Use `apply_patch` to:

- add a V1.6 change record dated 2026-07-29;
- set document version to V1.6;
- extend the executive summary and `G-09` with C2C personal collection;
- state that direct transfer, reusable personal QR and fixed request are supported without a friend graph;
- add `FR-PC-001` through `FR-PC-004` to the requirement catalog;
- distinguish ordinary-user collection from simulated-merchant collection in roles and boundaries;
- add personal collection aggregates to Business Center information architecture.

Expected requirement definitions:

```text
FR-PC-001 长期个人收款码
FR-PC-002 固定金额收款请求
FR-PC-003 H5 确认与余额付款
FR-PC-004 状态同步、回执与监控
```

- [ ] **Step 3: Add product flow and functional requirements**

Use `apply_patch` to add:

- a C2C personal collection core flow after existing QR payment flow;
- explicit no-friend, no-credit, fee-zero and self-payment rules;
- P0 acceptance criteria for code regeneration, 30-minute expiry, amount immutability, payment confirmation, TCC, ledger and concurrency;
- fixed-request safe reopen only after verified full rollback;
- `source_type` values for direct transfer, personal QR orders and collection request orders.

- [ ] **Step 4: Extend state, pages, data entities and APIs**

Use `apply_patch` to add:

- personal code, collection request and collection order state machines;
- C-end “收款” page, personal-code H5, fixed-request create/detail/receipt views;
- `PersonalCollectionCode`, `CollectionRequest` and `CollectionOrder` entities;
- `/api/v1/p2p-collections/...` endpoint catalog;
- object-level authorization and token privacy rules.

- [ ] **Step 5: Extend metrics, monitoring, delivery scope and acceptance**

Use `apply_patch` to:

- add personal QR scan/conversion metrics and fixed-request payment/expiry/conflict metrics;
- add C2C collection event dimensions without double-counting `TRANSFER` amount;
- add `AT-54` through `AT-68` matching all 15 design acceptance cases;
- adjust frontend responsibility, milestones, Gantt, scope protection, demo script, risks and completion definition;
- update requirement total from 45 to 49 and acceptance total from 53 to 68 wherever totals are stated.

- [ ] **Step 6: Validate PRD identifiers and structure**

Run:

```powershell
$prd='docs/superpowers/specs/2026-07-28-minialalipay-prd.md'
rg -n "V1.6|FR-PC-00[1-4]|AT-(5[4-9]|6[0-8])|PersonalCollectionCode|CollectionRequest|CollectionOrder|p2p-collections" $prd
$f=(Select-String -Path $prd -Pattern '^```').Count
[pscustomobject]@{CodeFences=$f;Balanced=($f % 2 -eq 0)}
```

Expected: all four requirements and all 15 acceptance cases are present; code fences are balanced.

- [ ] **Step 7: Record the final PRD hash**

Run:

```powershell
(Get-FileHash docs/superpowers/specs/2026-07-28-minialalipay-prd.md -Algorithm SHA256).Hash
```

Expected: one 64-character uppercase SHA-256 value to use in Task 2.

---

### Task 2: Synchronize System Analysis Baseline And Domain Architecture

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md:3`
- Reference: `docs/superpowers/specs/2026-07-28-minialalipay-prd.md`
- Reference: `docs/superpowers/specs/2026-07-29-p2p-personal-collection-design.md`

**Interfaces:**
- Consumes: PRD V1.6 and its SHA-256 from Task 1.
- Produces: System Analysis V1.3 aligned with Mini 花呗 and C2C personal collection.

- [ ] **Step 1: Upgrade document metadata**

Use `apply_patch` to:

- set system-analysis version to V1.3;
- set requirement baseline to PRD V1.6;
- replace the old PRD hash with Task 1 output;
- add a V1.3 change record describing Mini 花呗 synchronization and C2C personal collection;
- update references that still say PRD V1.4.

- [ ] **Step 2: Extend participants, use cases and service boundaries**

Use `apply_patch` to add ordinary-user collection use cases:

```text
UC-15 管理长期个人收款码
UC-16 创建和取消固定金额收款请求
UC-17 扫码或打开请求完成 C2C 余额付款
UC-18 查看跨端状态与回执
```

Extend context, logical architecture and UML component diagrams with a P2P Collection module inside `business-center`; keep the physical deployment count at five.

- [ ] **Step 3: Synchronize Mini Credit domain gaps**

Use `apply_patch` to add the missing PRD V1.5 system design:

- `CREDIT_PAY` and `CREDIT_REPAY` transaction types;
- credit account, receivable, bill, bill item and repayment aggregates;
- credit payment and repayment sequences and state models;
- fixed 5,000.00 CNY limit, statement day 1, due day 10 and overdue suspension;
- credit accounting templates and TCC participants;
- prohibition of credit transfer/withdrawal and fee/interest behavior.

This step must not change the approved C2C rule that only `BALANCE` is accepted.

- [ ] **Step 4: Add personal collection domain model and sequences**

Use `apply_patch` to add:

- `PersonalCollectionCode`, `CollectionRequest` and `CollectionOrder` to bounded contexts and the UML class diagram;
- one-to-many attempts for a fixed request with `active_order_id` arbitration;
- personal-code payment and fixed-request concurrency sequence diagrams;
- architecture invariants for server-derived payer/payee accounts, request amount immutability and no friend dependency.

- [ ] **Step 5: Add state models and activity flow branches**

Use `apply_patch` to add:

- personal code `ACTIVE`, `DISABLED`, `REVOKED` states;
- fixed request `OPEN`, `PROCESSING`, `SUCCESS`, `CANCELLED`, `EXPIRED`, `MANUAL_REVIEW` transitions;
- collection order states and verified rollback behavior;
- direct transfer, personal QR and fixed request branches in the unified funds activity flow.

Expected invariant: an unknown `PROCESSING` result never reopens a fixed request.

---

### Task 3: Add Physical Database And API Contracts

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md:855`

**Interfaces:**
- Consumes: domain aggregates and states from Task 2.
- Produces: implementable MySQL 8 DDL and REST/SSE contracts.

- [ ] **Step 1: Extend database ownership and logical table catalog**

Add to `business_db`:

```text
personal_collection_code
collection_request
collection_order
```

Add Mini 花呗 tables to their owning schemas, including credit account, receivable, bill, bill item and repayment records. State that services cannot directly access another schema owner's tables.

- [ ] **Step 2: Add MySQL DDL baselines**

Use `apply_patch` to provide complete `CREATE TABLE` definitions for the three C2C collection tables with:

- `BIGINT` amount in fen;
- `BINARY(32)` token digest;
- status checks or documented enum validation;
- `version`, UTC millisecond timestamps and soft/audit fields;
- one active personal-code slot per user;
- request `active_order_id`, `transaction_id` and expiry indexes;
- order source, payer/payee, session, status and transaction indexes.

Update `fund_transaction` source uniqueness to the PRD V1.6 rule and include all four business types.

- [ ] **Step 3: Add C2C endpoint directory and error codes**

Document all endpoints from the approved design, including permissions, `Idempotency-Key`, object authorization, status CAS and SSE reconnection. Add the defined C2C errors with stable HTTP mapping.

- [ ] **Step 4: Add representative request and response contracts**

Provide JSON examples for:

- creating a fixed request;
- exchanging a personal/fixed collection token;
- locking a personal-code amount;
- creating a confirmation;
- submitting payment;
- SSE state event.

Examples must not accept a client-supplied payee account, must use integer fen in service contracts, and must show `fundingSource: BALANCE`.

- [ ] **Step 5: Specify atomic acceptance and TCC rules**

Document the exact local-transaction order for personal and fixed collection acceptance. For a fixed request it must atomically consume confirmation, CAS request and order, insert transaction, link source records and write Outbox. Document safe reopening only after final verified Cancel and describe empty rollback, anti-hanging and idempotent Confirm/Cancel.

- [ ] **Step 6: Validate path and schema coverage**

Run:

```powershell
$sa='docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md'
rg -n "CREATE TABLE (personal_collection_code|collection_request|collection_order)|/api/v1/p2p-collections|P2P_CODE_INVALID|COLLECTION_REQUEST_PAID|active_order_id" $sa
```

Expected: all three DDL blocks, all approved endpoint groups, concurrency key and stable error mappings are present.

---

### Task 4: Complete Security, Observability, Testing And Traceability

**Files:**
- Modify: `docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md:1881`
- Modify: `docs/superpowers/specs/2026-07-28-minialalipay-prd.md:1140` only if cross-document verification finds a missing metric or event definition.

**Interfaces:**
- Consumes: PRD requirements `FR-PC-001` through `FR-PC-004`, acceptance `AT-54` through `AT-68`, API and DDL contracts.
- Produces: end-to-end observability, threat controls, tests and a complete traceability matrix.

- [ ] **Step 1: Add events, metrics and reconciliation rules**

Add the eight `P2P_COLLECTION_*` lifecycle events, personal-code conversion metrics, fixed-request payment/expiry/conflict metrics, SSE delay and TCC recovery metrics. Define dimensions so all collection payments remain `TRANSFER` while `source_type` separates channels and prevents double counting.

- [ ] **Step 2: Extend security and threat analysis**

Cover reusable public-token behavior, fixed-request multi-view behavior, URL cleanup, no-store/no-referrer response headers, token digest storage, rate limits, self-payment, amount/payee tampering, old-code revocation, authorization and audit.

- [ ] **Step 3: Add failure modes and test matrix**

Add all 15 acceptance scenarios from the design, including 100-way fixed-request competition, two simultaneous personal-code payments, Redis outage, TCC rollback/recovery, old-code invalidation and Trace correlation.

- [ ] **Step 4: Complete requirements traceability**

Update the traceability matrix to cover:

- all existing `FR-CR-001` through `FR-CR-006` requirements and `AT-41` through `AT-53`;
- new `FR-PC-001` through `FR-PC-004` and `AT-54` through `AT-68`;
- total 49 functional requirements and 68 acceptance cases.

Each P0 personal-collection requirement must map to at least one component, data/API artifact and acceptance case.

- [ ] **Step 5: Update delivery boundaries and future inputs**

Keep personal collection P0 essentials in the two-week scope, move AI-assisted collection request and cosmetic QR customization to P1, and update implementation risks and future OpenAPI/DDL/prototype inputs.

---

### Task 5: Cross-Document Validation And Review

**Files:**
- Verify: `docs/superpowers/specs/2026-07-28-minialalipay-prd.md`
- Verify: `docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md`
- Verify: `docs/superpowers/specs/2026-07-29-p2p-personal-collection-design.md`

**Interfaces:**
- Consumes: all updated documentation.
- Produces: validated, internally consistent documentation handoff.

- [ ] **Step 1: Verify requirement and acceptance identifiers**

Run a PowerShell script that extracts `FR-*` definitions and `AT-*` identifiers, reports duplicates and confirms the system-analysis traceability matrix contains every PRD requirement.

Expected:

```text
Unique functional requirements: 49
Unique acceptance cases: 68
Missing traceability requirements: 0
Duplicate identifiers: 0
```

- [ ] **Step 2: Verify document structure and placeholders**

Run:

```powershell
$files=@(
  'docs/superpowers/specs/2026-07-28-minialalipay-prd.md',
  'docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md'
)
foreach($file in $files){
  $f=(Select-String -Path $file -Pattern '^```').Count
  [pscustomobject]@{File=$file;CodeFences=$f;Balanced=($f % 2 -eq 0)}
  rg -n "TBD|TODO|待定|暂定|PRD V1\.4|PRD V1\.5" $file
}
```

Expected: all code fences balanced; no placeholders or stale PRD baseline references.

- [ ] **Step 3: Verify PRD hash linkage**

Run:

```powershell
$hash=(Get-FileHash docs/superpowers/specs/2026-07-28-minialalipay-prd.md -Algorithm SHA256).Hash
$hash
Select-String -Path docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md -Pattern $hash
```

Expected: calculated hash appears exactly in the system-analysis document metadata.

- [ ] **Step 4: Review critical invariants manually**

Confirm both documents state all of the following without contradiction:

```text
direct transfer + personal QR + fixed request -> TRANSFER
C2C funding source -> BALANCE only
personal code -> reusable, revocable, one active per user
fixed request -> 30 minutes, amount immutable, no bound payer
fixed request -> many attempts, at most one processing/success
merchant QR -> QR_PAY or CREDIT_PAY, separate from personal collection
Redis -> never the funds source of truth
SUCCESS -> finalizer-verified TCC + balance/credit + balanced ledger
```

- [ ] **Step 5: Request independent document review**

Invoke `requesting-code-review` and ask the reviewer to prioritize contradictions, missing concurrency cases, unimplementable DDL/API contracts, stale requirement references and test gaps. Resolve all material findings and rerun Steps 1 through 4.

- [ ] **Step 6: Record final artifact hashes and change summary**

Run:

```powershell
Get-FileHash \
  docs/superpowers/specs/2026-07-28-minialalipay-prd.md,\
  docs/superpowers/specs/2026-07-28-minialalipay-system-analysis.md,\
  docs/superpowers/specs/2026-07-29-p2p-personal-collection-design.md \
  -Algorithm SHA256 | Select-Object Path,Hash
```

Expected: three final hashes recorded in the handoff because the workspace has no Git history.
