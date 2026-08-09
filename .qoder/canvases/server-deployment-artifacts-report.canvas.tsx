import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function ServerDeploymentArtifactsReport() {
  return (
    <Stack gap={20}>
      <H1>前后端服务器部署产物 完成报告</H1>
      <Text tone="secondary">
        项目功能验证通过后，目标是将前后端部署到服务器 121.43.51.164（4 核，内存待升级至 16G）。
        服务器上已运行 MySQL、Redis、Nacos（本地开发期即直连），本次交付 Docker Compose
        部署方案的全部仓库侧产物：编排文件、通用后端镜像、Nginx 入口配置、环境变量模板、
        本地一键构建上传脚本与中文部署手册，并完成本地构建与脚本实跑验证。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="6" label="新增部署文件" />
        <Stat value="5" label="后端 fat jar 构建验证" />
        <Stat value="~2.8G" label="JVM 堆总预算" />
        <Stat value="80 / 81" label="对外端口（H5 / 管理端）" />
      </Grid>

      <Divider />

      <H2>架构要点</H2>
      <Table
        headers={['层面', '设计']}
        rows={[
          ['入口', 'Nginx 80 端口托管 C 端 H5、81 端口托管 B 端管理端，/api/ 同源反代网关，AI 对话 SSE 关闭代理缓冲'],
          ['服务互调', '网关与 4 个业务服务不暴露公网端口，经 Nacos 负载均衡（服务名解析）互调，容器注册 compose 网络 IP'],
          ['内存预算', 'gateway / user-center / ai-service 各 512m 堆，business-center / account-center 各 640m 堆'],
          ['Seata TC', '默认复用服务器已有 TC（Nacos SEATA_GROUP 发现）；缺失时用 --profile seata 启用编排内置 TC'],
          ['配置注入', '.env 覆盖代码默认值；空值赋值会覆盖默认导致故障，模板中以注释说明'],
        ]}
      />

      <Divider />

      <H2>变更文件</H2>
      <Table
        headers={['文件', '职责']}
        rows={[
          ['deploy/production/docker-compose.yml', '编排 5 个后端服务 + Nginx + 可选 Seata；锚点复用公共配置，逐服务健康检查与自动重启'],
          ['deploy/production/Dockerfile.backend', 'eclipse-temurin:21-jre-alpine 通用运行镜像，jar 经 JAR_FILE 构建参数注入'],
          ['deploy/production/nginx/nginx.conf', '双站点反代配置，SPA 路由回退，X-Real-IP 透传'],
          ['deploy/production/.env.example', 'Nacos / Redis / 四库凭据 / 内部令牌 / AI Key / JVM 参数模板'],
          ['deploy/production/build-and-upload.ps1', '本地一键构建上传（UTF-8 BOM，Windows PowerShell 5.1 可运行）'],
          ['deploy/production/README.md', '中文部署手册：首次部署、验证清单、升级回滚、常见问题、安全收敛'],
          ['deploy/README.md', '新增生产环境部署指引段落'],
          ['.gitignore', '排除 deploy/production/stage/ 构建暂存目录'],
        ]}
      />

      <Divider />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['mvn -DskipTests package', 'EXIT=0，5 个 fat jar 全部产出'],
          ['构建脚本实跑（-SkipFrontend -SkipUpload）', '成功收集 gateway 55.5M、user-center 61.1M、business-center 92.6M、account-center 88.4M、ai-service 57.2M'],
          ['compose YAML 校验', 'js-yaml 解析通过：7 服务、锚点合并继承 restart/env_file/networks、seata profile 正确'],
          ['脚本编码', 'UTF-8 BOM（EF BB BF）确认，规避 PowerShell 5.1 GBK 解析陷阱'],
          ['暂存清理', '验证用 stage 目录（约 355MB）已清理'],
        ]}
        rowTone={[undefined, undefined, undefined, undefined, undefined]}
      />

      <Divider />

      <H2>最终结果与后续步骤</H2>
      <Text>
        Spec 中全部仓库侧交付物与可验证项已完成并逐项审计通过。剩余为用户侧执行动作：
        服务器内存升级至 16G；检查 Nacos SEATA_GROUP 是否已有 seata-server；本地运行
        build-and-upload.ps1 上传；服务器填写 .env 密钥后 docker compose up -d --build，
        随后按部署手册验证清单冒烟（Nacos 实例、健康检查、H5 与管理端页面）。
      </Text>
      <Text tone="secondary" size="small">
        生成于 MiniAIalipay 前后端服务器部署任务完成时。
      </Text>
    </Stack>
  );
}
