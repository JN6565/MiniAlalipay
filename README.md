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
| 分布式事务 | Seata TCC 2.0 |
| 架构模式 | DDD 领域驱动设计（interfaces → application → domain ← infrastructure） |
| 数据库 | MySQL 8 + Flyway 版本管理 |
| 缓存 | Redis 7 |
| 服务注册/配置 | Nacos 2.3 |
| 前端框架 | React + TypeScript + Umi |
| B 端 UI | Ant Design |
| C 端 UI | Ant Design Mobile |
| 容器化 | Docker Compose |

## 项目结构

```
MiniAlalipay/
├── backend/                 # Java 21 Maven 多模块后端
│   ├── gateway/             # API 网关（路由、鉴权、限流）
│   ├── account-center/      # 账户、信用、账单、银行卡
│   ├── business-center/     # 转账、扫码、C2C、交易、监控
│   ├── user-center/         # 身份、用户、联系人
│   ├── ai-service/          # AI Agent 能力
│   └── platform-common/     # 技术通用类型
├── frontend-admin/          # B 端管理后台（React + Umi）
├── frontend-h5/             # C 端 H5 应用（React + Umi）
├── contracts/               # OpenAPI、事件、错误码契约
├── tests/                   # 跨服务集成测试
├── deploy/                  # Docker Compose 本地依赖
└── docs/                    # PRD、系统分析、库表设计文档
```

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- Node.js 18+
- Docker Desktop

### 1. 启动本地依赖

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

这将启动 MySQL 8、Redis 7、Seata 2.0 和 Nacos 2.3。

### 2. 启动后端服务

```powershell
cd backend
mvn clean install -DskipTests
# 分别启动各服务（端口 8080-8084）
```

### 3. 启动前端

```powershell
# B 端管理后台
cd frontend-admin
npm install
npm start

# C 端 H5 应用
cd frontend-h5
npm install
npm start
```

## 架构设计

系统采用 DDD 四层架构，依赖方向严格由外向内：

```
interfaces（Controller/DTO）
    ↓
application（应用服务/命令）
    ↓
domain（聚合根/领域服务/值对象）
    ↑
infrastructure（仓储实现/中间件适配）
```

每个微服务独立限界上下文，跨服务仅通过版本化 HTTP/OpenAPI 契约或 Outbox 事件交互，禁止直接共享领域对象。

## 文档

- [产品需求文档](docs/minialalipay/minialalipay-prd.md)
- [系统分析](docs/minialalipay/minialalipay-system-analysis.md)
- [数据库设计](docs/minialalipay/minialalipay-database-design.md)
- [接口契约](contracts/openapi/minialalipay-api.yaml)
- [错误码定义](contracts/error-codes/error-codes.yaml)

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。
