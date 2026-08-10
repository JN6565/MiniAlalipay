# 生产环境部署手册

本目录包含 MiniAIalipay 前后端在服务器上的部署产物与编排配置。目标服务器：`121.43.51.164`（4 核，内存需升级至 16G），服务器上已运行 MySQL 8.4、Redis、Nacos（本地开发期即直连，数据库 Schema 已由 Flyway 迁移就绪）。

## 目录结构

| 文件 | 职责 |
|---|---|
| `docker-compose.yml` | 编排 5 个后端服务 + Nginx；Seata TC 通过 `--profile seata` 可选启用 |
| `Dockerfile.backend` | 后端通用运行镜像（JRE 21），jar 经构建参数注入 |
| `nginx/nginx.conf` | 443（TLS）托管 C 端 H5，80 仅 301 跳转，81 托管 B 端管理端，`/api/` 反代网关 |
| `nginx/certs/`（仅服务器） | H5 站点 TLS 证书（`h5.crt`/`h5.key`），在服务器上生成，不入库不经脚本上传 |
| `.env.example` | 环境变量模板，复制为 `.env` 后填写 |
| `build-and-upload.ps1` | 本地一键构建并上传（Windows PowerShell） |
| `enable-https.sh` | 服务器侧 HTTPS 一键启用：校验新配置、生成自签证书、重写 CORS、重建 nginx/gateway |
| `jars/`、`web/` | 构建产物暂存（由脚本生成，不提交仓库） |

## 架构与端口

- 对外开放 **443（H5，HTTPS）、80（仅 301 跳转 HTTPS）与 81（管理端）**，由 Nginx 托管静态文件并将 `/api/` 反代到网关容器
- H5 必须经 HTTPS：扫码页调用摄像头 API（getUserMedia），浏览器规定该 API 只能在安全上下文（HTTPS/localhost）下使用
- 网关（8080）及 4 个业务服务不暴露公网端口，服务间经 **Nacos 负载均衡**（服务名解析）互调
- 各容器注册到 Nacos 的是 compose 网络 IP，同一编排内互相可达
- JVM 堆预算：gateway/user-center/ai-service 各 512m，business-center/account-center 各 640m，合计约 2.8G

## HTTPS 与自签证书

H5 站点（443）当前使用**自签证书**（无域名、无需备案的过渡方案），证书在服务器上生成，存放于 `/opt/minialalipay/nginx/certs/`，经 compose 只读挂载进 Nginx 容器。

**首次部署时生成证书**：推荐直接用一键脚本（含配置版本校验、证书生成、CORS 重写、`nginx -t` 预检与容器重建）：

```bash
cd /opt/minialalipay
sed -i 's/\r$//' enable-https.sh && bash enable-https.sh   # 前置去 CRLF：脚本经 Windows 上传可能带 \r
```

也可手动生成证书（服务器上执行，SAN 绑定公网 IP，有效期 10 年）：

```bash
mkdir -p /opt/minialalipay/nginx/certs
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout /opt/minialalipay/nginx/certs/h5.key \
  -out /opt/minialalipay/nginx/certs/h5.crt \
  -subj "/CN=121.43.51.164" \
  -addext "subjectAltName=IP:121.43.51.164"
```

**适用范围与局限**：

- 桌面 Chrome/Edge、安卓 Chrome：首次访问提示「连接不安全」，点「高级 → 继续访问」后即可正常使用扫码
- iPhone Safari、微信等内置浏览器：无法跳过自签证书告警，扫码暂不可用
- `.env` 的 `GATEWAY_CORS_ORIGINS` 必须包含 `https://121.43.51.164`（模板已默认包含），否则浏览器登录会被网关拒绝为 403 空体

**后续换正式证书**（购买域名并完成备案后）：将正式证书文件覆盖为 `nginx/certs/h5.crt` 与 `h5.key`，执行 `docker compose exec nginx nginx -s reload` 即可，无需改代码；同步把新域名来源追加到 `GATEWAY_CORS_ORIGINS` 并重建网关。

## 首次部署步骤

### 1. 服务器准备（用户侧）

1. 云控制台将内存升级至 16G，重启后确认：`free -h`
2. 确认 Docker 已安装：`docker --version && docker compose version`（未安装则按发行版文档安装）
3. 检查 Nacos 控制台（`http://121.43.51.164:8848/nacos`）服务列表 `SEATA_GROUP` 分组：
   - 已有 `seata-server` 且实例健康 → 无需额外操作
   - 没有 → 第 4 步启动时追加 `--profile seata`
4. 生成 H5 站点自签证书（见上文「HTTPS 与自签证书」章节的 openssl 命令）
5. 安全组放行 TCP **443**（与 80/81 并列）

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
2. **健康检查**：`curl -kL https://121.43.51.164/actuator/health` 返回 `{"status":"UP"}`（80 会 301 到 HTTPS，需 `-L` 跟随）
3. **HTTPS 入口**：`curl -k https://121.43.51.164/` 返回 200 且为 H5 首页；`curl -i http://121.43.51.164/` 返回 301 指向 https
4. **日志无异常**：`docker compose logs | grep -E "UnknownHostException|Service Instance cannot be null"` 应无输出
5. **页面冒烟**：
   - `https://121.43.51.164/`（H5，需先跳过自签证书告警）：登录、查余额、转账、明细、AI 对话、**扫一扫（摄像头应能拉起）**
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
| 浏览器提示「连接不安全」 | 自签证书的正常告警；点「高级 → 继续访问」即可（iPhone/微信内置浏览器无法跳过） |
| Nginx 启动失败报证书错误 | 确认 `nginx/certs/h5.crt`、`h5.key` 已在服务器生成且路径正确 |
| 扫码页提示「不支持摄像头」 | 确认经 `https://` 访问（80 会自动跳转）；HTTP 下浏览器不提供摄像头 API |
| H5 登录报 403 空体 | `GATEWAY_CORS_ORIGINS` 缺 `https://121.43.51.164`；补充后 `docker compose up -d --force-recreate gateway` |

## 安全收敛建议（部署验证完成后）

1. 安全组关闭 **3306（MySQL）、6379（Redis）、8848/9848（Nacos）、8091（Seata）** 的公网放行，仅保留 443/80/81；本地开发如需继续连远端库，将开发机公网 IP 加入白名单
2. `.env` 中的 `INTERNAL_SERVICE_TOKEN` 必须为强随机值，且禁止提交仓库（`.env` 不入库，仅 `.env.example` 入库）
3. MySQL 建议将应用账号与 root 分离，并按 Schema 授权（后续迭代）
