# 本地基础设施说明

使用以下命令启动本地依赖：

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

该配置启动 MySQL 8.4、Redis 7、Seata 2.0 协调器和 Nacos。Seata 当前采用文件存储，仅用于本地基础框架验证；在启用资金能力前，生产环境必须切换为数据库存储的协调器。MySQL 初始化脚本会按限界上下文创建独立 Schema，默认账号仅限本地开发使用。

## 生产环境部署

服务器部署（Docker Compose 编排后端 5 个服务与 Nginx 前端入口）的编排文件、构建上传脚本与部署手册位于 [`production/`](production/README.md)。本目录其余内容仅服务于本地开发，不用于生产。

## Seata 事务协调接入说明

`business-center` 与 `account-center` 通过 Seata TCC 编排转账资金分支。客户端按以下条件定位 TC：

- Seata Server 必须以应用名 `seata-server`、分组 `SEATA_GROUP` 注册到 Nacos（集群 `default`）；本仓库 compose 中的 Seata 默认未注册 Nacos，接入资金链路前需为 Seata Server 配置 Nacos 注册中心，或直接指向已注册的远端 TC。
- 客户端事务分组为 `minialalipay-tx-group`，映射到 Seata 集群 `default`；纯 TCC 模式关闭了自动数据源代理，无需 `undo_log` 表。
- 可用环境变量覆盖：`SEATA_ENABLED`（默认 true）、`SEATA_TX_SERVICE_GROUP`、`SEATA_REGISTRY_TYPE`、`SEATA_REGISTRY_APPLICATION`、`SEATA_REGISTRY_GROUP`、`SEATA_REGISTRY_CLUSTER`、`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`SEATA_CLUSTER`。
- 回退开关：`business-center` 设置 `TCC_COORDINATOR=http` 时改回自研 HTTP TCC 编排（充值链路始终走 HTTP 编排）。
- 单元测试默认 `seata.enabled=false`，不依赖外部 TC。
