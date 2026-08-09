# 生产环境部署手册

本目录包含 MiniAIalipay 前后端在服务器上的部署产物与编排配置。目标服务器：`121.43.51.164`（4 核，内存需升级至 16G），服务器上已运行 MySQL 8.4、Redis、Nacos（本地开发期即直连，数据库 Schema 已由 Flyway 迁移就绪）。

## 目录结构

| 文件 | 职责 |
|---|---|
| `docker-compose.yml` | 编排 5 个后端服务 + Nginx；Seata TC 通过 `--profile seata` 可选启用 |
| `Dockerfile.backend` | 后端通用运行镜像（JRE 21），jar 经构建参数注入 |
| `nginx/nginx.conf` | 80 端口托管 C 端 H5，81 端口托管 B 端管理端，`/api/` 反代网关 |
| `.env.example` | 环境变量模板，复制为 `.env` 后填写 |
| `build-and-upload.ps1` | 本地一键构建并上传（Windows PowerShell） |
| `jars/`、`web/` | 构建产物暂存（由脚本生成，不提交仓库） |

## 架构与端口

- 对外仅开放 **80（H5）与 81（管理端）**，由 Nginx 托管静态文件并将 `/api/` 反代到网关容器
- 网关（8080）及 4 个业务服务不暴露公网端口，服务间经 **Nacos 负载均衡**（服务名解析）互调
- 各容器注册到 Nacos 的是 compose 网络 IP，同一编排内互相可达
- JVM 堆预算：gateway/user-center/ai-service 各 512m，business-center/account-center 各 640m，合计约 2.8G

## 首次部署步骤

### 1. 服务器准备（用户侧）

1. 云控制台将内存升级至 16G，重启后确认：`free -h`
2. 确认 Docker 已安装：`docker --version && docker compose version`（未安装则按发行版文档安装）
3. 检查 Nacos 控制台（`http://121.43.51.164:8848/nacos`）服务列表 `SEATA_GROUP` 分组：
   - 已有 `seata-server` 且实例健康 → 无需额外操作
   - 没有 → 第 4 步启动时追加 `--profile seata`

### 2. 本地构建并上传（开发机，PowerShell）

```powershell
cd deploy\production
.\build-and-upload.ps1
```

常用变体：

```powershell
.\build-and-upload.ps1 -SkipFrontend   # 只更新后端
.\build-and-upload.ps1 -SkipUpload     # 只构建到 stage 目录验证，不上传
```

前提：本机可 SSH 到服务器（密钥或密码），已安装 Maven 与 Node.js。

### 3. 服务器填写密钥并启动

```bash
cd /opt/minialalipay
cp .env.example .env
vi .env    # 至少修改 INTERNAL_SERVICE_TOKEN 与 INTERNAL_AUTH_SERVICE_TOKEN 为同一强随机值
docker compose up -d --build        # Nacos 无 seata-server 时：docker compose --profile seata up -d --build
docker compose ps
```

等待约 1-2 分钟，`docker compose ps` 中各服务应变为 `healthy`。

## 验证清单

1. **注册中心**：Nacos 控制台服务列表出现 `minialalipay-gateway`、`user-center`、`business-center`、`account-center`、`ai-service` 各 1 个健康实例
2. **健康检查**：`curl http://121.43.51.164/actuator/health` 返回 `{"status":"UP"}`
3. **日志无异常**：`docker compose logs | grep -E "UnknownHostException|Service Instance cannot be null"` 应无输出
4. **页面冒烟**：
   - `http://121.43.51.164/`（H5）：登录、查余额、转账、明细、AI 对话
   - `http://121.43.51.164:81`（管理端）：登录与主要页面

## 升级与回滚

**升级单个服务**（本地改代码后）：

```powershell
# 开发机：只重建并上传（示例：只更新后端）
.\build-and-upload.ps1 -SkipFrontend
```

```bash
# 服务器：仅重建并重启目标服务，不影响其他容器
cd /opt/minialalipay
docker compose up -d --build --no-deps account-center
```

**回滚**：保留上一版 jar（建议升级前 `cp jars/account-center.jar jars/account-center.jar.bak`），恢复后重新执行上述 `--no-deps` 重建命令。

**查看日志**：`docker compose logs -f --tail=200 <服务名>`

## 常见问题

| 现象 | 排查方向 |
|---|---|
| 服务反复重启 | `docker compose logs <服务>`；常见为数据库/Redis/Nacos 不通或 `.env` 空值覆盖了默认配置 |
| 网关 503 / `No servers available` | 对应服务未注册到 Nacos；确认容器健康且 Nacos 控制台可见实例 |
| 转账报 TC 不可用 | Nacos `SEATA_GROUP` 无 `seata-server`；追加 `--profile seata` 重启编排 |
| AI 回复“暂时不可用” | 检查 `SPRING_AI_OPENAI_API_KEY`；非 `sk-` 开头会自动降级 Mock 模式 |
| 页面打开但接口 404 | 确认 Nginx 容器运行且 `web/h5`、`web/admin` 目录有内容 |

## 安全收敛建议（部署验证完成后）

1. 安全组关闭 **3306（MySQL）、6379（Redis）、8848/9848（Nacos）、8091（Seata）** 的公网放行，仅保留 80/81；本地开发如需继续连远端库，将开发机公网 IP 加入白名单
2. `.env` 中的 `INTERNAL_SERVICE_TOKEN` 必须为强随机值，且禁止提交仓库（`.env` 不入库，仅 `.env.example` 入库）
3. MySQL 建议将应用账号与 root 分离，并按 Schema 授权（后续迭代）
