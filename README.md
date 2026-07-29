# MiniAIalipay

MiniAIalipay 是一个虚拟资金支付系统，用于演示 C2C 转账、商户扫码模拟支付、复式记账、Seata TCC 分布式事务、AI 助理和全链路可观测性。系统不接入真实人民币支付通道。

## 仓库结构

- `backend/`：Java 21 Maven 多模块后端，包含网关和各限界上下文服务。
- `frontend/`：预留的单一 Umi 前端工程，后续承载 `/admin/**` B 端与 `/h5/**` C 端路由。
- `contracts/`：OpenAPI、事件和错误码契约。
- `tests/`：跨服务验证测试。
- `deploy/`：本地 MySQL、Redis、Seata 运行依赖。
- `docs/`：PRD、系统分析、库表设计和其他项目文档。

## 环境要求

- JDK 21
- Maven 3.9 及以上
- Docker Desktop，用于启动本地依赖


## 启动本地依赖

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

当前仓库已提供基础框架，不包含可执行的资金业务接口。任何余额、信用额度或账本相关能力，必须在完成 TCC、复式记账、幂等、对账和验收测试后才能实现。
