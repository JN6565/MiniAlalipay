# MiniAIalipay 后端系统分析文档

## 0. 文档信息

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.1 |
| 修订日期 | 2026-07-30 |
| 需求基线 | [MiniAlalipay PRD](./minialalipay-prd.md) |
| 系统架构基线 | [MiniAlalipay 系统分析](./minialalipay-system-analysis.md) |
| 数据库基线 | [MiniAlalipay 数据库设计](./minialalipay-database-design.md) |
| 接口契约 | [`contracts/openapi/minialalipay-api.yaml`](../../contracts/openapi/minialalipay-api.yaml) |
| 目标读者 | 后端、AI、测试、前端联调、运维和技术评审人员 |

### 0.1 变更记录

| 版本 | 日期 | 内容 |
|---|---|---|
| V1.0 | 2026-07-30 | 建立后端系统分析基线 |
| V1.1 | 2026-07-30 | 明确派生文档定位，删除系统级重复事实，改为总体章节引用与后端实现映射 |

## 1. 文档定位与权威边界

本文是总体系统分析的**后端实现设计**，只回答系统事实如何落到 Java 21、Maven、Spring、MySQL、Redis、Seata、消息、配置和测试代码中。

文档权威层级遵循[总体系统分析第 1.5 节“文档权威与派生关系”](./minialalipay-system-analysis.md#15-文档权威与派生关系)：

1. PRD 定义需求与验收事实。
2. 总体系统分析定义系统边界、业务流程、状态、不变量、安全和质量属性。
3. OpenAPI 定义 HTTP/SSE 接口方法、路径、字段和响应。
4. 数据库设计定义物理表、字段、索引、约束和 Schema 所有权。
5. 本文只定义模块、包、组件、端口、事务、配置和测试落点。

本文不得重新定义业务状态、API 字段、表字段、错误语义或系统安全规则。发现冲突时必须先修正上层基线或专项契约，再同步本文和代码；不得以当前实现反向覆盖已批准的系统事实。

### 1.1 引用规则

- 系统事实使用相对链接引用稳定章节标题，不复制完整流程图、状态表、端点目录或表目录。
- 本文只补充实现组件、代码目录、调用端口、事务位置和验证方式。
- 引用不使用物理行号，也不保存容易因普通编辑失效的文档 SHA-256。
- Git 提交历史负责记录基线版本，文档头只保留稳定文件路径和文档版本。

## 2. 当前代码实现基线

### 2.1 已有代码

- Java 21 Maven 父工程与 `platform-common`、`gateway`、`user-center`、`business-center`、`account-center`、`ai-service` 六个模块。
- Spring Cloud Gateway 启动类、基础路由、`X-Request-Id`、安全响应头和统一网关异常响应。
- 通用 `ApiResponse`、`ErrorCode`、`BusinessException` 和 `RequestIdGenerator`。
- 用户、交易、账户、账本、TCC、信用和 AI 工具风险的基础枚举及少量领域单元测试。
- MySQL、Redis、Seata 的本地 Docker Compose 基线。
- 当前 POM 尚未集成 MyBatis、Flyway、Seata、Spring AI、OpenTelemetry 和 Testcontainers；后文章节描述的是目标落点，不能据此标记为已实现。

### 2.2 尚未完成的实现

- 业务 Controller、Application Service、完整聚合、仓储端口、Mapper 和 Flyway 业务迁移。
- 登录鉴权、支付密码、确认令牌、模拟充值和四类资金交易主流程。
- Seata TCC 分支、Outbox/Inbox、复式账本、恢复、对账和终态发布。
- AI Agent、MCP 工具、SSE、监控事件和离线分析。
- 当前 OpenAPI 仅包含 `/actuator/health` 健康检查，尚无业务操作；新增业务能力时，网关路由、Controller、OpenAPI 操作和契约测试必须作为同一任务交付。
- `ApiResponse` 当前只有 `requestId`，当前 OpenAPI `ErrorResponse` 也未定义 `traceId`；实现业务 API 前必须同时修改通用响应类、OpenAPI、生成类型和契约测试。
- `UserStatus` 当前为 `ACTIVE/LOCKED/DISABLED`；目标为 `PROVISIONING/ACTIVE/DISABLED`。注册实现前必须增加 `PROVISIONING`，从 `UserStatus` 移除 `LOCKED`，并由 `Credential` 字段表达临时登录锁定，同时统一持久化约束和恢复测试。

“设计存在”不等于“代码已实现”。完成状态必须以源代码、Flyway、OpenAPI 操作和测试结果共同判定。

## 3. Maven 模块、包结构与依赖门禁

系统部署单元和职责以[总体系统分析第 6.2 节](./minialalipay-system-analysis.md#62-部署单元建议)和[第 6.3 节](./minialalipay-system-analysis.md#63-三大中心职责)为准。本节只规定代码组织。

```text
backend/
├── pom.xml
├── platform-common/
├── gateway/
├── user-center/
├── business-center/
├── account-center/
└── ai-service/
```

每个业务服务使用以下结构：

```text
com.minialalipay.<context>/
├── interfaces/
│   ├── web/                 # 外部 REST/SSE Controller 和 API DTO
│   ├── internal/            # 服务间接口与 TCC 参与者入口
│   ├── messaging/           # Inbox 消费者
│   └── scheduler/           # 恢复、账单、对账和过期任务
├── application/
│   ├── command/             # 命令及处理器
│   ├── query/               # 查询服务与只读投影 DTO
│   ├── service/             # 用例编排和事务入口
│   └── port/                # 外部服务、事件和基础设施端口
├── domain/
│   ├── model/               # 聚合根、实体和值对象
│   ├── service/             # 领域服务
│   ├── repository/          # 仓储端口
│   ├── event/               # 领域事件
│   └── policy/              # 风控和状态策略
└── infrastructure/
    ├── persistence/         # PO、Mapper、仓储实现
    ├── client/              # HTTP/OpenAPI 客户端适配器
    ├── messaging/           # Outbox、Inbox、Redis Streams
    ├── cache/               # Redis 会话、限流和只读缓存
    └── config/              # Spring、Seata、OTel 和安全配置
```

依赖方向固定为 `interfaces -> application -> domain <- infrastructure`：

- `domain` 不依赖 Spring MVC、MyBatis、Feign、Redis、Controller 或其他限界上下文领域包。
- `platform-common` 只保存 API 包装、错误接口、Trace 等技术通用类型。
- 一个服务不得导入另一个服务的 Mapper、PO、仓储实现、实体或聚合根。
- 跨服务调用只使用版本化 OpenAPI 客户端或事件 Schema。
- ArchUnit 必须验证分层方向、跨上下文持久化隔离和账户/账本专属写权限。

## 4. 领域模型的 Java 实现映射

领域事实以[总体系统分析第 8 章](./minialalipay-system-analysis.md#8-领域模型分析)为准。目标代码映射如下：

| 总体上下文 | 模块与领域包 | 聚合实现位置 | 仓储端口位置 | 重点单元测试 |
|---|---|---|---|---|
| Identity、Contact | `user-center/domain/user`、`identity`、`contact` | 对应包内聚合根和值对象 | `domain/**/repository` | 唯一性、锁定、密码状态、联系人归属 |
| Transfer Draft、Transaction | `business-center/domain/transfer`、`transaction` | 草稿、确认和交易聚合 | `domain/**/repository` | 版本、幂等来源、合法状态迁移 |
| QR Payment | `business-center/domain/qrpay` | 扫码订单和 H5 会话引用 | `domain/qrpay/repository` | 订单 CAS、来源互斥和过期 |
| P2P Collection | `business-center/domain/collection` | 个人码、固定请求和单次订单 | `domain/collection/repository` | 换码、请求抢占和多尝试仲裁 |
| Risk、Manual Case | `business-center/domain/risk`、`manualcase` | 风控决策和人工工单 | 对应领域仓储端口 | 决策版本、审批权限和重新确认 |
| Account、TCC | `account-center/domain/account`、`tcc` | 账户与分支预留聚合 | 对应领域仓储端口 | 非负、版本 CAS、空回滚和防悬挂 |
| Ledger | `account-center/domain/ledger` | 凭证和不可变分录 | `domain/ledger/repository` | 借贷平衡、幂等过账和冲正 |
| Mini Credit | `account-center/domain/credit`、`bill`、`repayment` | 额度、应收、账单和还款 | 对应领域仓储端口 | 额度恒等式、分配和逾期恢复 |
| Agent、Memory、Tool、Policy | `ai-service/domain/agent`、`memory`、`tool`、`policy` | 会话、工具调用和值对象 | 对应领域仓储端口 | Schema、风险等级、会话归属和脱敏 |

金额、时间、ID 和版本规则直接引用[总体系统分析第 11.3 节](./minialalipay-system-analysis.md#113-金额与时间规则)和[第 11.6 节](./minialalipay-system-analysis.md#116-物理模型统一规范)。Java 边界统一使用 `long amountFen`、`Instant` 或 `OffsetDateTime`，可变聚合通过 `version` 执行乐观锁。

## 5. Application Service 与事务入口

完整业务流程以[总体系统分析第 9 章](./minialalipay-system-analysis.md#9-关键业务流程分析)为准。后端按用例建立以下编排入口：

| 用例组 | Application Service 落点 | 主要端口 | 本地事务边界 |
|---|---|---|---|
| 注册、开户恢复与会话 | `user-center/application/service` | 账户开户/查询、信用开户/查询、会话、人工工单和审计端口 | 用户与凭证先按本地事务持久化；开户结果核验后 CAS 激活并写 Outbox |
| 支付密码 | `user-center/application/service` | 密码哈希、锁定、确认凭证撤销端口 | 错误计数、锁定和凭证变更原子提交 |
| 草稿与确认 | `business-center/application/service` | 用户解析、账户查询、风控和确认令牌端口 | 草稿版本、风险快照和确认上下文分别按聚合提交 |
| 资金受理 | `business-center/application/service` | TCC 发起、账户中心、Outbox 端口 | 令牌消费、来源 CAS、主单和受理 Outbox 同一本地事务 |
| 扫码与个人收款 | `business-center/application/service` | 令牌、会话、账户预检和 TCC 端口 | 订单/请求抢占、主单关联和 Outbox 同一本地事务 |
| 终态发布与恢复 | `business-center/application/service`、`interfaces/scheduler` | 全局事务、分支快照、账本核验和工单端口 | 核验后 CAS 终态并写终态 Outbox |
| 账户与账本分支 | `account-center/application/service` | 账户、TCC 屏障、账本和本地 Outbox 端口 | 屏障、资源条件更新、分支状态和 Outbox 同一本地事务 |
| 信用支付与还款 | `account-center/application/service` | 额度、应收、账单、账户和账本端口 | 每个 TCC 资源分支独立本地事务 |
| Agent 消息 | `ai-service/application/service` | LLM、Tool Router、三中心客户端和审计端口 | 会话消息、工具摘要和上下文版本按会话串行提交 |

Application Service 只负责用例编排、权限主体、幂等键、事务边界和端口调用；业务不变量由聚合或领域服务执行。查询服务可以读取只读投影，但不得把查询 DTO 回写为领域对象。

注册恢复实现引用[总体系统分析第 9.1 节](./minialalipay-system-analysis.md#91-注册与自动开户)和[第 10.9 节](./minialalipay-system-analysis.md#109-用户注册开户状态)，物理字段引用[数据库设计第 5.1 节](./minialalipay-database-design.md#51-app_user)和[第 6.1 节](./minialalipay-database-design.md#61-account)。`user-center` 生成并持久化 `registrationId`；`account-center` 开户适配器以该键插入账户，遇到唯一键冲突时读取既有账户并核验用户、账户类型和币种绑定，不能把冲突直接当作成功。用户中心核验余额账户和适用的信用账户后，以 `status=PROVISIONING` 和 `version` 为条件 CAS 激活；自动恢复超过阈值时通过业务中心人工工单端口创建工单，用户状态保持不变。

## 6. Controller、DTO 与协议适配器

接口架构以[总体系统分析第 12 章](./minialalipay-system-analysis.md#12-接口与集成分析)为准，可执行 HTTP/SSE 路径、字段和响应以 [OpenAPI](../../contracts/openapi/minialalipay-api.yaml)为准，本文不维护第二份端点目录。

| 适配器 | 目标位置 | 实现要求 |
|---|---|---|
| 外部 REST Controller | `<service>/interfaces/web` | 只接收和返回 OpenAPI API DTO，不返回聚合或 PO |
| 内部服务接口 | `<service>/interfaces/internal` | 使用独立内部认证、超时、版本和幂等语义 |
| SSE | `business-center/interfaces/web`、`ai-service/interfaces/web` | 从已持久化事件投影推送，断线后按契约回源 |
| MCP Server | `ai-service/interfaces` | 只暴露工具 Schema 和策略允许的端口，不访问数据库 |
| OpenAPI 客户端 | `infrastructure/client` | 由契约生成或严格对齐契约，统一超时和错误映射 |
| 事件消费者 | `interfaces/messaging` | Inbox 去重后调用 Application Service，不直接写多个领域表 |

统一响应、请求头、HTTP 状态和错误结构不得在 Controller 内自行定义。业务异常由全局异常转换器映射到统一错误码；网关异常和服务异常必须使用同一 `requestId/traceId` 关联规则。

身份派生、敏感字段禁采和令牌用途隔离由 Controller 参数白名单、Application Service 对象授权以及统一日志过滤器实现，具体规则引用[总体系统分析第 12.11 节](./minialalipay-system-analysis.md#1211-api-安全幂等与兼容规则)、[第 17.2 节](./minialalipay-system-analysis.md#172-身份与授权)和[第 17.3 节](./minialalipay-system-analysis.md#173-密码与令牌)。

## 7. PO、Mapper、Repository 与 Flyway

数据所有权和物理结构以[总体系统分析第 11 章](./minialalipay-system-analysis.md#11-数据架构与数据库分析)和[数据库设计](./minialalipay-database-design.md)为准。

| 模块 | 持久化实现目录 | Flyway 目录 | 允许写入的 Schema |
|---|---|---|---|
| `user-center` | `infrastructure/persistence` | `src/main/resources/db/migration/` | `user_db` |
| `business-center` | `infrastructure/persistence` | `src/main/resources/db/migration/` | `business_db`、其拥有的 `metrics_db` 投影 |
| `account-center` | `infrastructure/persistence` | `src/main/resources/db/migration/` | `account_db`、`ledger_db` |
| `ai-service` | `infrastructure/persistence` | `src/main/resources/db/migration/` | `agent_db` |

实现规则：

- PO 只用于持久化，不能从 Controller 返回，也不能跨服务共享。
- Mapper 由所属模块基础设施层使用；领域层只依赖 Repository 端口。
- 条件更新显式包含对象归属、当前状态和 `version`，并检查影响行数。
- 账本分录只允许插入；修复通过新冲正凭证，不提供更新和物理删除 Mapper。
- 一条 Flyway 迁移只修改一个服务拥有的 Schema，文件名遵循 `VYYYYMMDDHHMM__lower_snake_case_description.sql`。
- 已执行迁移不得修改；金额、状态、唯一键或 TCC 屏障变化必须增加 Testcontainers 集成测试。
- 注册相关仓储分别把 `registration_id` 持久化到 `user_db.app_user` 与 `account_db.account`；账户仓储必须提供按该键查询既有账户的端口，并由应用层校验资源绑定后返回开户结果。

## 8. TCC、Saga、可靠事件与终态实现

一致性语义以[总体系统分析第 13 章](./minialalipay-system-analysis.md#13-分布式事务与资金一致性分析)为准。本节只定义实现组件。

| 组件 | 目标模块与位置 | 实现职责 |
|---|---|---|
| 全局事务发起器 | `business-center/application/service` | 受理主单后启动或接管 Seata 全局事务 |
| TCC 参与者入口 | `account-center/interfaces/internal` | 接收 Try、Confirm、Cancel，并转换为应用命令 |
| 分支屏障服务 | `account-center/application/service` | 在本地事务中执行屏障检查、资源条件更新和分支状态更新 |
| 账本参与者 | `account-center/application/service` | 预留凭证、确认过账或取消未过账预留 |
| 全局事务记录适配器 | `business-center/infrastructure/persistence` | 持久化 xid、交易、状态、重试和恢复租约 |
| 终态发布器 | `business-center/application/service` | 核验交易级分支、冻结、入账、应收和凭证事实后发布终态 |
| Saga/恢复调度器 | `business-center/interfaces/scheduler` | 扫描超时主单，接管、重试、补偿或创建人工工单 |
| Outbox 发布器 | 各事实所有者 `infrastructure/messaging` | 扫描已提交 Outbox，投递 Redis Streams 并记录重试 |
| Inbox 消费器 | 各消费者 `interfaces/messaging` | 使用消费者名与事件 ID 去重，并与投影更新同事务提交 |

TCC 命令字段、分支原子性和 Redis 资金边界不在本文重复定义，分别引用[总体系统分析第 13.2 节](./minialalipay-system-analysis.md#132-tcc-执行规则)、[第 13.3 节](./minialalipay-system-analysis.md#133-幂等空回滚和防悬挂)和[第 18.3 节](./minialalipay-system-analysis.md#183-扩展策略)；实现通过内部 API DTO、应用事务测试和 ArchUnit/配置检查验证。

## 9. 安全、Trace、日志、缓存与调度落点

系统安全和可观测要求分别引用[总体系统分析第 16、17 章](./minialalipay-system-analysis.md#16-全链路监控与数据分析)与[第 17 章](./minialalipay-system-analysis.md#17-安全与合规分析)。

| 横切能力 | 目标位置 | 后端落地要求 |
|---|---|---|
| 网关鉴权与安全头 | `gateway/filter`、`gateway/security` | 建立主体上下文、CSRF/CORS、限流和对象级授权前置条件 |
| 服务对象授权 | 各服务 `application/service` | 从认证主体派生 user/account/merchant，不信任客户端覆盖字段 |
| 支付密码 | `user-center` | 独立强哈希、错误计数、支付锁定和确认凭证撤销 |
| Trace | 网关过滤器、HTTP 客户端、Seata、事件、SSE、MCP | 传播 `requestId`、`traceId` 及业务关联 ID |
| 日志脱敏 | `platform-common` 过滤器与各适配器 | 禁止记录密码、原始令牌、完整账号和模型内部推理 |
| 缓存 | 各服务 `infrastructure/cache` | 只缓存会话和查询结果，资金判断回源 MySQL |
| 限流 | 网关及服务安全适配器 | 覆盖登录、搜索、密码、令牌交换、确认、提交和 Agent 消息 |
| 配置 | 各模块 `resources/application.yml` 和环境变量 | 凭据不入库，超时、重试、租约和采样率显式配置 |

本地开发端口属于后端部署落地配置，统一维护如下：

| 进程或依赖 | 默认端口 |
|---|---:|
| Gateway | 8080 |
| User Center | 8081 |
| Business Center | 8082 |
| Account Center | 8083 |
| AI Service | 8084 |
| Seata | 8091 |
| MySQL | 3306 |
| Redis | 6379 |

浏览器只访问 Gateway；内部服务端口不得作为前端基础地址。端口通过环境变量覆盖时，网关路由、Docker Compose、健康检查和联调文档必须同步。

调度任务目标位置：

| 任务 | 模块 | 入口 |
|---|---|---|
| 事务恢复、终态核验、对账 | `business-center` | `interfaces/scheduler` |
| Outbox 发布 | 各事实所有者 | `infrastructure/messaging` |
| 二维码和请求过期 | `business-center` | `interfaces/scheduler` |
| `PROVISIONING` 注册开户恢复 | `user-center` | `interfaces/scheduler` |
| 信用出账和逾期检查 | `account-center` | `interfaces/scheduler` |
| T+1 聚合与质量检查 | `business-center` 监控模块 | `interfaces/scheduler` |

所有扫描任务使用租约或条件更新抢占，支持重复执行并记录 `traceId`、处理对象、结果和下次重试时间。

## 10. AI 与 MCP 后端落点

Agent 行为、意图、工具策略和越权防护以[总体系统分析第 14 章](./minialalipay-system-analysis.md#14-ai-agent-与-mcp-分析)为准。

| 实现单元 | 目标位置 | 责任 |
|---|---|---|
| Agent 会话服务 | `ai-service/application/service` | 串行处理消息、加载上下文、保存摘要和解释确定性结果 |
| Intent/Schema | `ai-service/domain/agent`、`tool` | 验证结构化意图和工具输入输出 |
| Tool Router | `ai-service/application/service` | 按风险等级、主体和确认上下文选择工具端口 |
| MCP 适配器 | `ai-service/interfaces` | 暴露受控工具目录，不暴露数据库和资金内部接口 |
| 三中心客户端 | `ai-service/infrastructure/client` | 只通过网关或批准的内部 OpenAPI 调用用户、业务和账户能力 |
| 工具审计 | `ai-service/infrastructure/persistence` | 保存脱敏参数摘要、结果码、耗时、风险级别和 Trace |

可信 UI 完成确认后，策略网关把不可见的一次性确认上下文注入 Tool Router；Agent 可按[总体系统分析第 9.3 节](./minialalipay-system-analysis.md#93-ai-talk-转账)、[第 12.4 节](./minialalipay-system-analysis.md#124-mcp-工具接口)和[第 14.5 节](./minialalipay-system-analysis.md#145-工具策略)调用 `submit_confirmed_transfer` 或 `submit_confirmed_credit_repayment` 端口。提交后只通过只读查询端口解释确定性交易状态，模型始终不能生成或接触确认句柄。

## 11. 测试落点与执行方式

测试目标和验收场景以[总体系统分析第 21 章](./minialalipay-system-analysis.md#21-测试与验证分析)和[第 22 章](./minialalipay-system-analysis.md#22-需求追踪矩阵)为准。

```text
<service>/src/test/java/com/minialalipay/<context>/
├── domain/                # 聚合不变量和状态迁移
├── application/           # 用例、权限、幂等和失败编排
├── infrastructure/        # MySQL、Redis、Flyway、Outbox、TCC
├── interfaces/            # REST、SSE、OpenAPI 契约
└── architecture/          # ArchUnit 分层和跨上下文依赖

tests/
├── api/
├── e2e/
├── performance/
└── fault-injection/
```

后端验证至少包括：

- `mvn test`：模块单元测试和架构测试。
- Testcontainers：唯一约束、乐观锁、Flyway、Outbox/Inbox 和 TCC 屏障。
- OpenAPI 契约测试：Controller、DTO、错误码、请求头和状态码。
- 跨服务集成测试：全局事务、分支重试、空回滚、防悬挂、恢复和终态发布。
- 注册恢复集成测试：重复 `registrationId` 返回同一账户、冲突资源绑定被拒绝、并发恢复只激活一次、超阈值保持 `PROVISIONING` 并只创建一个活动工单。
- 故障注入：受理提交后崩溃、Try/Confirm/Cancel 失败、事件重复和网络超时。
- 资金断言：交易状态、双方余额、冻结、信用应收、全部分支和借贷平衡同时满足。

资金测试的判定语义直接引用[总体系统分析第 21 章](./minialalipay-system-analysis.md#21-测试与验证分析)；本文只规定测试目录、工具和执行入口。

## 12. 开发顺序与发布门禁

总体实施边界以[总体系统分析第 23 章](./minialalipay-system-analysis.md#23-实施边界与风险分析)为准。后端依赖顺序如下：

1. 契约、Flyway、认证、统一错误、Trace、Outbox/Inbox 和 TCC 屏障。
2. 注册、开户、支付密码、模拟充值、余额与明细。
3. 用户搜索、草稿、风险、确认、普通转账、账本、回执和恢复。
4. 商户扫码、H5 令牌交换、余额支付、SSE 和跨端状态。
5. Agent 会话、只读工具、草稿工具和确定性结果解释。
6. 长期个人码、固定请求、CAS 仲裁和 C2C 对账。
7. 信用额度、信用支付、账单、还款和逾期。
8. 个人/商户统计、监控、T+1、质量检查和发布门禁。

合并前必须满足：

- Java 21 编译和受影响测试通过。
- OpenAPI、事件 Schema、数据库迁移、总体系统分析与实现一致。
- 无跨服务持久化依赖和无领域类型进入 `platform-common`。
- 资金场景覆盖幂等、TCC、复式记账、恢复和对账。
- 敏感字段未进入日志、Trace、浏览器存储、URL、事件或 MCP。
- 未确定结果不发布或展示成功。

## 13. 系统事实到代码与测试的追踪矩阵

| 系统事实来源 | 后端实现落点 | 主要验证 |
|---|---|---|
| [服务边界与架构](./minialalipay-system-analysis.md#6-功能分解与服务边界) | Maven 模块、包结构、端口和 ArchUnit | 模块构建、依赖门禁 |
| [领域模型](./minialalipay-system-analysis.md#8-领域模型分析) | `domain` 聚合、值对象、策略和仓储端口 | 领域单元测试 |
| [业务流程](./minialalipay-system-analysis.md#9-关键业务流程分析) | Application Service、Controller、客户端和调度器 | 应用测试、E2E |
| [状态模型](./minialalipay-system-analysis.md#10-状态模型分析) | 状态枚举、聚合迁移方法、条件更新 Mapper | 状态机和并发测试 |
| [数据架构](./minialalipay-system-analysis.md#11-数据架构与数据库分析) | PO、Mapper、Repository、Flyway | Testcontainers、迁移测试 |
| [接口与集成](./minialalipay-system-analysis.md#12-接口与集成分析) | Controller、API DTO、SSE、MCP 和事件适配器 | OpenAPI、Schema、SSE 契约测试 |
| [事务与一致性](./minialalipay-system-analysis.md#13-分布式事务与资金一致性分析) | Seata、屏障、Outbox、恢复和终态发布器 | TCC、故障注入和对账 |
| [AI 与 MCP](./minialalipay-system-analysis.md#14-ai-agent-与-mcp-分析) | Agent、Tool Router、策略和三中心客户端 | Schema、权限和注入测试 |
| [监控](./minialalipay-system-analysis.md#16-全链路监控与数据分析)与[安全](./minialalipay-system-analysis.md#17-安全与合规分析) | OTel、日志过滤、指标消费者和告警任务 | Trace、敏感字段和质量测试 |
| [部署](./minialalipay-system-analysis.md#19-部署与运行分析)与[测试](./minialalipay-system-analysis.md#21-测试与验证分析) | 配置、Docker Compose、测试目录和 CI | 启停、健康检查和发布门禁 |

## 附录 A：后端文档维护检查

后续修改本文前必须确认：

1. 是否在重复定义总体系统事实；如果是，改为引用总体章节。
2. 是否在重复定义 API 字段；如果是，修改或引用 OpenAPI。
3. 是否在重复定义物理表字段；如果是，修改或引用数据库设计。
4. 新内容能否明确落到模块、包、组件、事务、配置或测试；不能落地的系统说明应进入总体系统分析。
5. 实现重构未改变系统行为时只更新本文；改变架构事实时同步更新总体系统分析和相关契约。
