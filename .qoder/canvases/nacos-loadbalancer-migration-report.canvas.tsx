import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function NacosLoadBalancerMigrationReport() {
  return (
    <Stack gap={20}>
      <H1>Nacos 负载均衡改造 完成报告</H1>
      <Text tone="secondary">
        五个服务与网关虽已注册 Nacos，但网关路由和服务间调用仍写死 localhost 直连，且缺少
        spring-cloud-starter-loadbalancer 依赖，lb:// 无法生效。本次为全部后端模块引入
        Spring Cloud LoadBalancer，网关路由与服务间 HTTP 调用默认经 Nacos 服务发现做负载均衡，
        同时保留环境变量覆盖直连地址的回退能力，为前后端部署服务器做准备。前端只调网关 8080，无需改动。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="27" label="变更文件" />
        <Stat value="5/5" label="模块接入 LoadBalancer" tone="success" />
        <Stat value="5 模块" label="Maven 测试通过" tone="success" />
        <Stat value="2 条" label="lb:// 链路实测验证" tone="success" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '补齐依赖',
            'gateway、user-center、business-center、account-center、ai-service 五个 pom.xml 新增 spring-cloud-starter-loadbalancer（SCA 2023.0.x 的 nacos-discovery starter 不自带）',
            'lb:// 与服务名解析具备运行时基础',
          ],
          [
            '网关路由切换',
            'application.yml 全部路由默认值由 http://localhost:808X 改为 lb://user-center、lb://business-center、lb://account-center、lb://ai-service，环境变量仍可覆盖回退直连',
            '网关按 Nacos 健康实例轮询转发，多实例部署零配置',
          ],
          [
            '网关鉴权适配',
            'UserCenterAuthenticationAdapter 注入 ReactorLoadBalancerExchangeFilterFunction，WebClient 支持 lb://user-center 基址；鉴权地址默认值同步改为 lb://',
            '会话 introspect 调用也走负载均衡，测试上下文缺失过滤器时自动退回直连',
          ],
          [
            '服务间客户端',
            'user-center/account-center 的 RestTemplate 加 @LoadBalanced，默认地址改为 http://account-center、http://user-center；business-center 内部地址与 TCC 客户端经容器 RestClient.Builder 自动获得 LB 装饰',
            '开户、实名、支付证明、TCC 资金链路的跨服务调用全部服务名化',
          ],
          [
            '健康探针重构',
            'ActuatorServiceHealthProbe 由 java.net.http.HttpClient 改为注入 RestClient.Builder，默认地址改为 http://minialalipay-gateway、http://account-center、http://ai-service 的 Actuator 端点',
            '部署后运营看板健康列无需手工配置环境变量即可工作',
          ],
          [
            'ai-service 统一走网关',
            '三个 Http 客户端改注入容器 RestClient.Builder，基址统一为网关服务名 http://minialalipay-gateway，环境变量改名 AI_GATEWAY_BASE_URL 避免与网关自身变量冲突',
            'AI 调用链经网关完成鉴权限流审计，且支持网关多实例',
          ],
          [
            '测试配置隔离',
            'business-center/account-center/gateway/ai-service 测试配置加 spring.cloud.loadbalancer.enabled=false；user-center 在两个 @SpringBootTest 上显式加同属性',
            'localhost stub 地址不被负载均衡拦截，测试行为不变',
          ],
        ]}
      />

      <H2>变更文件</H2>
      <Table
        headers={['模块', '文件', '变更说明']}
        rows={[
          ['通用', '5 个 pom.xml', '新增 spring-cloud-starter-loadbalancer 依赖及中文注释'],
          ['gateway', 'src/main/resources/application.yml', '全部路由与鉴权地址默认 lb://，更新直连回退注释'],
          ['gateway', 'auth/UserCenterAuthenticationAdapter.java', 'WebClient 增加负载均衡过滤器支持，默认 lb://user-center'],
          ['gateway', 'src/test/resources/application-test.yml', '测试关闭负载均衡'],
          ['user-center', 'config/UserCenterCommonConfiguration.java', 'RestTemplate 加 @LoadBalanced 并补充中文 Javadoc'],
          ['user-center', 'client/AccountCenterClient.java + application.yml', '默认地址改为 http://account-center'],
          ['account-center', 'config/AccountCenterCommonConfiguration.java', 'RestTemplate 加 @LoadBalanced'],
          ['account-center', 'UserInfoHttpAdapter / UserCenterIdentityClient + application.yml', '默认地址改为 http://user-center'],
          ['business-center', 'application.yml', '内部调用与健康探针地址默认服务名化'],
          ['business-center', 'health/ActuatorServiceHealthProbe.java', 'HttpClient 重构为负载均衡感知的 RestClient'],
          ['ai-service', '3 个 Http 客户端 + application.yml', '注入容器 RestClient.Builder，基址统一为网关服务名'],
          ['测试', '4 个测试 yml + 3 个测试类', '测试上下文统一关闭负载均衡'],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          [
            'Maven 全量测试',
            'platform-common、gateway、user-center、business-center、account-center 全部通过；ai-service 6 失败 + 1 错误经 git stash 对照确认为既有问题（H2 不兼容 UNIQUE KEY、ArchUnit 分层违规、SSE 控制器等），与本次改造无关',
          ],
          [
            '重点回归',
            'GatewayRouteConfigurationTest、GatewayRouteForwardingIntegrationTest、business-center 依赖 18081/18083 stub 的集成测试、user-center AccountCenterClientTest 全部通过',
          ],
          [
            '网关注册',
            '网关在 8090 启动成功，gRPC 连接 Nacos（121.43.51.164:8848）并完成 minialalipay-gateway 实例注册；/actuator/health 返回 200',
          ],
          [
            'lb://user-center 链路',
            '鉴权请求触发 Nacos 订阅，发现 2 个健康实例（192.168.169.1:8081、192.168.108.1:18081），introspect 正常返回 401 令牌无效而非实例解析失败',
          ],
          [
            'lb://business-center 链路',
            'GET /api/v1/p2p-collections/by-token 经网关转发到 business-center 实例（192.168.169.1:8082），返回标准业务错误 COLLECTION_TOKEN_INVALID 且 X-Request-Id 透传，P0 路由转发成功',
          ],
        ]}
        rowTone={['success', 'success', 'success', 'success', 'success']}
      />

      <Divider />

      <H2>最终结果与部署注意事项</H2>
      <Text>
        Spec 全部条目已实现并验证：网关路由与服务间调用默认走 Nacos 负载均衡，环境变量保留直连回退，
        测试上下文不受影响，代码与系统分析文档中"网关 lb:// 动态路由"的既有描述对齐。
      </Text>
      <Text tone="secondary">
        部署提示：Docker 部署需通过 SPRING_CLOUD_NACOS_DISCOVERY_IP 保证注册容器外可达 IP；
        Nacos 中存留的历史实例 192.168.108.1:18081（user-center）若已废弃建议下线，
        否则负载均衡会轮询到该实例；本地 8080 上的旧网关进程重启后即可生效新路由。
      </Text>
      <Text tone="secondary" size="small">生成于"Nacos 负载均衡改造"任务完成时。</Text>
    </Stack>
  );
}
