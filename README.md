# MiniAlalipay

一个结合 AI 自然语言交互与确定性交易核心的微服务虚拟支付系统。支持 C2C 转账、商户扫码支付、Mini 花呗信用消费，采用 DDD 分层架构与 Seata TCC 分布式事务保障资金安全。

> 本系统处理虚拟资金，不接入真实人民币支付通道，仅用于技术演示与学习。

## 核心功能

- **AI 转账** — 通过自然语言对话完成意图识别、槽位补全、多轮澄清，确认后进入交易流程
- **扫码支付** — 支持动态码、个人收款码、固定金额请求，可选余额或花呗付款
- **Mini 花呗** — 完整的信用消费闭环：开通 → 授信 → 消费 → 出账 → 还款
- **复式记账** — 所有资金变动通过借贷分录记录，保证账本平衡
- **TCC 分布式事务** — Try/Confirm/Cancel 三阶段提交，保障跨服务资金操作一致性
- **全链路可观测** — 请求追踪、结构化日志、业务监控

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 21 + Spring Cloud Alibaba |
| 分布式事务 | Seata TCC 2.6.0（注册到 Nacos） |
| 架构模式 | DDD 领域驱动设计（interfaces → application → domain ← infrastructure） |
| 数据库 | MySQL 8.0 + Flyway 版本管理 |
| 缓存 | Redis（AOF 持久化） |
| 服务注册/配置 | Nacos 3.1.0 |
| 前端框架 | React + TypeScript + Umi |
| B 端 UI | Ant Design |
| C 端 UI | Ant Design Mobile |
| 容器化 | Docker Compose |

## 项目结构

```
MiniAlalipay/
├── backend/                 # Java 21 Maven 多模块后端
│   ├── gateway/             # API 网关（路由、鉴权、限流）— 端口 8080
│   ├── user-center/         # 身份、用户、联系人 — 端口 8081
│   ├── business-center/     # 转账、扫码、C2C、交易、监控 — 端口 8082
│   ├── account-center/      # 账户、信用、账单、银行卡 — 端口 8083
│   ├── ai-service/          # AI Agent 能力 — 端口 8084
│   └── platform-common/     # 技术通用类型
── frontend-admin/          # B 端管理后台（React + Umi）
├── frontend-h5/             # C 端 H5 应用（React + Umi）
├── contracts/               # OpenAPI、事件、错误码契约
├── tests/                   # 跨服务集成测试
── deploy/                  # Docker Compose 与数据库初始化脚本
│   ├── docker-compose.yml   # 本地开发中间件编排
│   ── mysql/init/          # 数据库初始化 SQL（建库、建表、默认账号）
── docs/                    # PRD、系统分析、库表设计文档
```

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| Docker Desktop | 最新稳定版 |
| MySQL Client | 8.0+（用于执行初始化脚本） |

## 快速开始

### 第一步：启动中间件（Docker Compose）

```powershell
cd deploy
docker compose up -d
```

这将启动以下服务：

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| MySQL | mysql:8.0 | 3306:3306 | 业务数据库 |
| Redis | redis:latest | 6379:6379 | 缓存与分布式锁，AOF 持久化 |
| Nacos | nacos/nacos-server:v3.1.0 | 8848:8848 (API)<br>9848:9848 (gRPC)<br>8080:8080 (Console) | 服务注册与配置中心，单机模式 |
| Seata | apache/seata-server:2.6.0 | 8091:8091 | 分布式事务协调器，注册到 Nacos SEATA_GROUP |

> **注意**：Seata TC 通过 Nacos 服务发现，启动后会自动注册到 Nacos 的 `SEATA_GROUP` 分组，后端服务的 Seata 客户端通过 Nacos 发现 TC 实例。

等待所有服务健康就绪：

```powershell
docker compose ps
```

### 第二步：初始化数据库

执行初始化脚本，创建 6 个逻辑库、业务表和默认账号：

```powershell
# 方式一：使用 MySQL 客户端执行
mysql -h 127.0.0.1 -P 3306 -u root -p < deploy/mysql/init/00-create-schemas.sql

# 方式二：进入 MySQL 容器执行
docker exec -i minialalipay-mysql mysql -uroot -p < deploy/mysql/init/00-create-schemas.sql
```

脚本会创建以下数据库：

| 数据库 | 所属服务 | 说明 |
|--------|----------|------|
| `user_db` | user-center | 用户、凭证、联系人、角色 |
| `account_db` | account-center | 账户、余额、信用额度、银行卡 |
| `ledger_db` | account-center | 复式账本、凭证、分录、账单 |
| `business_db` | business-center | 交易、转账草稿、扫码订单、TCC 全局事务 |
| `agent_db` | ai-service | AI 会话、消息、工具调用 |
| `metrics_db` | business-center | 监控指标、对账投影、告警 |

### 第三步：配置环境变量（可选）

如果中间件运行在非默认地址或端口，通过环境变量覆盖：

```powershell
# MySQL
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_ROOT_PASSWORD = "your_password"

# Redis
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = "your_password"

# Nacos
$env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
$env:NACOS_USERNAME = "nacos"
$env:NACOS_PASSWORD = "nacos"

# Seata
$env:SEATA_ENABLED = "true"
$env:SEATA_REGISTRY_TYPE = "nacos"
```

### 第四步：编译并启动后端服务

```powershell
cd backend
mvn clean install -DskipTests
```

按以下顺序启动各服务（每个服务一个终端窗口）：

```powershell
# 1. 用户中心
cd backend/user-center
mvn spring-boot:run

# 2. 账户中心
cd backend/account-center
mvn spring-boot:run

# 3. 业务中心
cd backend/business-center
mvn spring-boot:run

# 4. AI 服务
cd backend/ai-service
mvn spring-boot:run

# 5. API 网关（最后启动）
cd backend/gateway
mvn spring-boot:run
```

启动完成后，服务会自动注册到 Nacos。访问 Nacos 控制台 `http://localhost:8080/nacos` 查看服务列表（默认账号 `nacos/nacos`）。

### 第五步：启动前端

```powershell
# B 端管理后台
cd frontend-admin
npm install
npm start

# C 端 H5 应用（新终端）
cd frontend-h5
npm install
npm start
```

### 第六步：验证部署

1. 访问网关健康检查：`http://localhost:8080/actuator/health`，应返回 `{"status":"UP"}`
2. 访问 Nacos 控制台：`http://localhost:8080/nacos`，确认 5 个服务均已注册
3. 访问 B 端管理后台：`http://localhost:8000`（Umi 默认端口）
4. 访问 C 端 H5 应用：`http://localhost:8001`（Umi 默认端口）

## 默认账号

### 中间件账号

| 服务 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| MySQL | root | `teamuser2026` | 数据库 root 账号 |
| MySQL | mini_app | `mini_app_dev_only` | 应用账号（脚本自动创建） |
| Redis | — | `teamuser2026` | Redis 访问密码 |
| Nacos | nacos | nacos | Nacos 控制台与 API |

> **安全提醒**：以上为开发环境默认密码，生产部署请务必修改。

### 业务账号

系统启动后，通过 B 端管理后台或 API 注册用户。初始系统无预置业务用户，需通过注册接口创建。

## 架构设计

### DDD 四层架构

依赖方向严格由外向内：

```
interfaces（Controller / DTO / 网关路由）
    ↓
application（应用服务 / 命令 / 编排）
    ↓
domain（聚合根 / 领域服务 / 值对象 / 领域事件）
    ↑
infrastructure（仓储实现 / 中间件适配 / 外部调用）
```

### 微服务与限界上下文

| 服务 | 限界上下文 | 核心职责 |
|------|-----------|----------|
| gateway | 网关层 | 路由、鉴权、限流、JWT 校验 |
| user-center | 用户域 | 身份、凭证、联系人、角色 |
| account-center | 账户域 | 余额、信用额度、复式账本、银行卡 |
| business-center | 交易域 | 转账、扫码、TCC 协调、监控 |
| ai-service | AI 域 | 自然语言交互、工具调用、会话管理 |

跨服务仅通过版本化 HTTP/OpenAPI 契约或 Outbox 事件交互，禁止直接共享领域对象。

### Seata TCC 事务流程

```
business-center (TM)
    ↓ 发起全局事务
Seata TC (经 Nacos 发现)
    ↓ Try 阶段
account-center (RM) — 冻结余额/额度
account-center (RM) — 记录账本分录
    ↓ Confirm 阶段
account-center (RM) — 扣减余额/确认分录
    ↓ 或 Cancel 阶段
account-center (RM) — 释放冻结/冲正分录
```

## 文档

- [产品需求文档](docs/minialalipay/minialalipay-prd.md)
- [系统分析](docs/minialalipay/minialalipay-system-analysis.md)
- [数据库设计](docs/minialalipay/minialalipay-database-design.md)
- [接口契约](contracts/openapi/minialalipay-api.yaml)
- [错误码定义](contracts/error-codes/error-codes.yaml)
- [事件契约](contracts/events/event-types.yaml)

## 常见问题

### Q: Seata TC 未注册到 Nacos？

检查 Seata 容器日志：

```powershell
docker logs minialalipay-seata
```

确认 Nacos 地址可达，且 `SEATA_GROUP` 分组下能看到 `seata-server` 实例。

### Q: 后端服务启动失败，报 Nacos 连接错误？

确保 Nacos 已完全启动（健康检查通过）：

```powershell
curl http://localhost:8848/nacos/v1/console/health/readiness
```

### Q: Flyway 迁移校验失败？

已执行的迁移不可修改。如需修正，新增向前迁移文件，或使用 `scripts/validate-flyway.ps1 -Repair` 对齐校验和。

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。
