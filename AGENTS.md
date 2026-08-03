q
## 中文说明强制约束

- 项目文档、需求说明、系统分析、数据库说明、接口说明、代码注释、日志说明、错误提示和 Git 提交摘要必须使用中文。
- 仅以下内容保留必要英文：Java/TypeScript 标识符、包名、类名、HTTP 方法与路径、OpenAPI 字段、数据库对象名、框架/库名称、标准错误码、Conventional Commit 类型。
- 新增文档不得以英文段落替代中文解释；引用英文技术术语时，应在首次出现处给出中文含义。

## 需求依据与变更流程

修改代码前，必须阅读与任务相关的：

1. `docs/minialalipay/minialalipay-prd.md`
2. `docs/minialalipay/minialalipay-system-analysis.md`
3. 涉及数据库时阅读 `docs/minialalipay/minialalipay-database-design.md`
4. 涉及接口时阅读 `contracts/openapi/minialalipay-api.yaml`

只实现 PRD 和系统分析已经说明的能力。如果实现中必须新增或改变系统分析未提及的功能、接口字段、状态流转、数据库表、事件、安全规则或部署依赖，必须在同一次修改中同步更新对应的系统分析文档和接口契约。不得把未说明的行为隐藏在代码实现中。

## 仓库边界

- `backend/`：Java 21 Maven 多模块后端工程。
- `frontend-admin/`：B 端独立 React + TypeScript + Umi 工程。
- `frontend-h5/`：C 端独立 React + TypeScript + Umi H5 工程。
- `contracts/`：接口、事件和错误码契约。
- `tests/`：跨服务接口、端到端、性能和故障注入测试。
- `deploy/`：本地运行依赖配置。
- `platform-common`：只能放技术通用类型，禁止放用户、账户、交易、账本、金额、Mapper、实体、仓储或其他领域类型。

## 前后端联调接口规范

### 访问规则

- 前端只能调用网关，本地基础地址为 `http://localhost:8080`。
- 前端禁止直接调用 `8081` 至 `8084` 服务端口，禁止直接访问 MySQL 或 Redis。
- 请求使用 `application/json`，透传 `X-Request-Id`；生成 OpenAPI 类型和客户端后必须统一使用。
- 金额字段统一使用整数 `amountFen`，禁止发送或计算 `float`、`double` 或 JavaScript 小数金额。
- 写操作接口完成后必须使用服务端定义的幂等键。支付密码、确认令牌和二维码原始令牌不得写入日志、浏览器存储、埋点或 URL。

### 已实现基础接口

| 方法 | 网关路径 | 状态 | 返回内容 |
|---|---|---|---|
| `GET` | `/actuator/health` | 已实现 | Spring Boot 健康检查，正常返回 `{"status":"UP"}` |

### 网关预留路由

下列前缀仅表示路由预留，不代表业务接口已经实现。前端页面必须同时确认 OpenAPI 中存在对应操作、后端存在 Controller 且测试通过后，才能调用。

| 路径前缀 | 目标服务 | 预期能力 |
|---|---|---|
| `/api/v1/auth/**`、`/api/v1/users/**`、`/api/v1/contacts/**` | `user-center` | 身份、用户、联系人 |
| `/api/v1/transfers/**`、`/api/v1/qr-pay/**`、`/api/v1/p2p-collections/**`、`/api/v1/transactions/**`、`/api/v1/monitoring/**` | `business-center` | 转账、扫码、C2C、交易、监控 |
| `/api/v1/accounts/**`、`/api/v1/credit/**`、`/api/v1/bills/**` | `account-center` | 账户、信用、账单 |
| `/api/v1/agent/**` | `ai-service` | AI Agent 能力 |

## 后端代码规范

- 使用 Java 21、UTF-8、四个空格缩进，包根为 `com.minialalipay`。
- 每个服务遵循 `interfaces -> application -> domain <- infrastructure` 分层方向。
- Controller 只能接收和返回 API DTO，禁止返回持久化 PO 或聚合根。
- `domain` 禁止依赖 Spring MVC、MyBatis、Feign、Redis、Controller 或其他限界上下文的领域包。
- 一个服务禁止导入另一个服务的 Mapper、仓储、PO、实体或聚合根；跨上下文只能通过版本化 HTTP/OpenAPI 契约或 Outbox 事件交互。
- 只有 `account-center` 可以修改余额、信用额度、账本凭证和账本分录。
- 金额使用 `long` 分；仅在展示/转换边界使用 `BigDecimal`；时间使用 `Instant` 或 `OffsetDateTime`。
- `contracts/error-codes/error-codes.yaml` 是对外错误码事实来源。开发每个服务时，必须在所属服务内建立实现 `ErrorCode` 的领域错误码枚举；禁止把用户、账户、交易、信用或 AI 领域错误码集中放入 `platform-common`。
- 服务错误码枚举的 `code`、中文 `message` 和 `httpStatus` 必须与 `error-codes.yaml` 完全一致，并增加契约测试逐项校验。新增、删除或修改错误码时，必须在同一次变更中同步更新 YAML、服务枚举、OpenAPI 错误响应和契约测试；任一项不一致时禁止合并。
- 新行为必须先编写测试，重点覆盖不变量、幂等、状态流转和失败处理。

## 注释与目录说明规范

- 新增服务、前端工程、功能子域或职责不直观的目录时，必须通过模块 `README.md`、Java `package-info.java` 或就近说明文件，用中文写明目录职责、边界、主要入口、允许依赖和禁止事项；不得只创建目录而不说明用途。
- 对外公共类、接口、抽象类、注解、`record`、API DTO、应用服务、领域服务、聚合根和值对象必须编写中文 Javadoc，说明业务用途和职责边界。无法从名称直接判断的字段、构造参数和返回值也必须说明含义、单位、格式或取值限制。
- Java `enum` 和 TypeScript 枚举或状态联合类型必须说明枚举整体用途；每个枚举值必须说明业务含义。涉及状态机时，还需说明是否为终态、允许的来源或后续状态以及是否允许重试，禁止只写英文常量名而无业务解释。
- Controller 接口、应用层命令方法、跨服务端口、仓储接口和事件处理器必须说明输入、输出、权限、幂等要求、事务边界及主要异常；与 OpenAPI 已有字段说明重复时可以引用契约，不得形成相互矛盾的第二套定义。
- 资金、TCC、幂等、CAS 并发、状态流转、复式记账、补偿、对账、安全校验和敏感数据处理等复杂逻辑，必须在关键判断或代码块前用中文解释“为什么这样处理”和必须保护的不变量，不得只复述代码动作。
- 数据库迁移中的金融表、关键约束、唯一索引、条件更新和不可逆数据修复必须使用 SQL 注释说明设计目的；普通列定义可由数据库设计文档统一说明，不要求重复无信息量的注释。
- 禁止为了注释率添加“赋值变量”“调用方法”等复述代码的注释；简单 getter/setter、标准框架覆写和语义清晰的局部实现不强制注释。注释应解释业务语义、边界、取舍和风险。
- 修改行为、状态、参数或约束时必须同步更新相关注释。与代码、OpenAPI、数据库设计或系统分析不一致的过期注释视为缺陷，不得通过评审。
- 注释、示例和目录说明不得包含真实密码、令牌、私钥、完整账号、生产地址或其他敏感数据。

## 前端代码规范

- 使用 React + TypeScript，禁止引入 Vue。
- B 端使用 Ant Design/AntV；H5 使用 Ant Design Mobile/AntV F2。
- B/C 端为两个独立工程，分别构建、部署和维护依赖；仅可通过 `contracts` 共享契约类型、错误码和无副作用工具函数。
- 默认使用 Umi `request`；TanStack Query 管理服务端缓存，Zustand 只保存客户端 UI 状态，禁止复制余额、账本事实或交易终态。
- 必须启用路由级拆包，H5 不得下载 B 端大盘代码。

## 数据库变更规范

- 使用 Flyway，迁移文件位置为 `backend/<service>/src/main/resources/db/migration/`。
- 文件命名：`VYYYYMMDDHHMM__lower_snake_case_description.sql`，例如 `V202607291030__create_account_tables.sql`。
- 一条迁移只修改一个服务拥有的 Schema，禁止跨 Schema 联表、外键或 DDL。
- 已执行迁移不可修改、删除或重命名；修正必须新增向前迁移。
- 金融表必须包含主键、创建时间；可变数据需包含更新时间和版本控制策略；必须为查询和幂等路径建立索引。
- 修改金额、账户、账本、TCC、交易或事件表时，必须同步更新库表设计和系统分析，并增加集成测试。
- 本地凭据使用环境变量或被忽略的 `.env` 文件；禁止提交真实密码、令牌、私钥、数据库备份或生产配置。

## Git 与评审规范

- 分支格式：`feature/<scope>-<summary>`、`fix/<scope>-<summary>`、`docs/<summary>`、`chore/<summary>`。
- 提交格式：`type(scope): 中文简洁说明`。
- 允许的 `type`：`feat`、`fix`、`docs`、`refactor`、`test`、`build`、`ci`、`chore`。
- 示例：`feat(account): 增加余额冻结命令`；`docs(api): 定义转账草稿接口契约`。
- 一次提交只包含一个完整变更，禁止把纯格式化与行为、Schema 或契约修改混在一起。
- 提交评审前，必须执行受影响的 Maven 测试、前端检查（前端工程建立后）和契约校验；行为、接口、表结构或文档不一致时禁止合并。

## 完成检查

每个功能修改必须确认：需求追踪、OpenAPI/事件契约、错误码 YAML 与服务枚举一致性测试、必要的数据库迁移、错误处理、可观测字段、目录说明和必要注释与实现保持同步。涉及资金时，还必须确认幂等、TCC、复式记账和对账覆盖。
