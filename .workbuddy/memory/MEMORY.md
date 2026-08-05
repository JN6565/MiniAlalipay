# MiniAIalipay 项目长期记忆

## 项目概述
MiniAIalipay 是一个仿支付宝的迷你支付系统，采用 Java 21 + Spring Boot 3.3.4 + Maven 多模块后端工程，DDD 分层架构。

## 团队分工
- 用户王基哲负责"信用业务与质量保障"任务（Mini 花呗）
- 开发文档位于 `docs/minialalipay/wang-jizhe-dev-guide.md`

## 技术栈与约定
- Java 21、UTF-8、四空格缩进，包根 `com.minialalipay`
- DDD 分层：interfaces → application → domain ← infrastructure
- 金额使用 long 分，禁止 float/double
- MyBatis 注解 SQL（非 XML），带 Schema 前缀，UPDATE 带 CAS 乐观锁
- Flyway 迁移文件命名：VYYYYMMDDHHMM__lower_snake_case_description.sql
- 错误码枚举必须与 contracts/error-codes/error-codes.yaml 完全一致
- 信用表跨 Schema：account_db（3 张表）、ledger_db（8 张表）

## Maven 编译方式
IntelliJ 内置 Maven 3.9.6，使用 JDK 21 直接运行 classworlds Launcher：
```
JAVA_HOME="D:/develop/Java/jdk-21"
MVN_HOME="D:/develop/idea/IntelliJ IDEA 2024.1.4/plugins/maven/lib/maven3"
```

## 后端模块结构
- `platform-common` — 技术通用类型（禁止放领域类型）
- `account-center` — 账户、信用、账单（端口 8083）
- `business-center` — 转账、扫码、C2C、交易（端口 8082）
- `user-center` — 身份、用户、联系人（端口 8081）
- `ai-service` — AI Agent（端口 8084）
- 网关端口 8080

## 已完成工作
- account-center 信用子域全部后端代码（领域模型、仓储、应用服务、Controller、Flyway 迁移）
- 9 个 P0 接口端点已实现
- 94 个源文件编译通过
- 领域模型单元测试：8 个测试文件、96 个测试用例全部通过（T-01 交付条件完成）
- Flyway 迁移表结构验证：与 00-create-schemas.sql 对比一致（T-02 交付条件完成）
- 阶段二独立质量门禁集成测试（复测）：54 个用例，52 通过 0 失败 2 跳过，通过率 96.3%（条件通过）
- T-12 余额/账本内核交叉评审：37 个测试用例全部通过，评审报告已生成
- T-03 信用 TCC 分支参与者（逻辑层）：CreditTccParticipant + CreditRepayTccParticipant 已实现
- T-03 TCC 分支测试：25 个用例全部通过
- account-center 全量测试：205 个用例，全部通过

## 阶段四进展
- Spring Cloud Alibaba 版本：2023.0.1.0（王钧平选定）
- Nacos Server 2.5.3 + Seata Server 2.0.0 已在服务器部署（hrms-net 网桥，db 存储模式）
- TCC 参与者逻辑已实现，待 Seata 客户端依赖引入后添加 @TwoPhaseBusinessAction 注解

## 遗留 TODO
1. Seata 客户端依赖引入（pom.xml）+ @TwoPhaseBusinessAction 注解
2. CreditRepaymentService.submitRepayment 去除 TODO，对接 CreditRepayTccParticipant
3. TCC 全局协调器（business-center）— TransactionOrder + 状态机 + 恢复扫描
4. 到期检查分页查询完善（CreditJobService.doDueCheckForAllAccounts）
5. Flyway 迁移在 MySQL 容器中实际执行验证
6. 事件发布（Outbox 模式）：6 种信用事件未实现
7. /actuator/healthcheck 端点应读取 Exchange 中的 requestId（当前缺失）
8. 下游服务统一异常处理：user-center 等 404 仍用 Spring Boot 默认格式，未包装为 ApiResponse
