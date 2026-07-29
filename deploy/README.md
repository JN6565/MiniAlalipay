# 本地基础设施说明

使用以下命令启动本地依赖：

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

该配置启动 MySQL 8.4、Redis 7 和 Seata 2.0 协调器。Seata 当前采用文件存储，仅用于本地基础框架验证；在启用资金能力前，生产环境必须切换为数据库存储的协调器。MySQL 初始化脚本会按限界上下文创建独立 Schema，默认账号仅限本地开发使用。
