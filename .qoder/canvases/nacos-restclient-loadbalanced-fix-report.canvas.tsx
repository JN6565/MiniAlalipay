import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function NacosRestClientLoadBalancedFixReport() {
  return (
    <Stack gap={20}>
      <H1>Nacos 负载均衡修复：RestClient 缺失 LB 拦截器 完成报告</H1>
      <Text tone="secondary">
        Nacos 负载均衡改造上线后，转账、充值、交易记录、明细、AI 等功能大面积失败，账户中心抛出
        NullPointerException。反编译 spring-cloud-commons-4.1.4 证实根因：Spring Cloud 2023.0.x 的
        LoadBalancerRestClientBuilderBeanPostProcessor 只装饰标注 @LoadBalanced 的 RestClient.Builder
        Bean，此前注入 Boot 默认 builder，服务名 URL（如 http://user-center）被当作普通域名走 DNS
        解析，报 UnknownHostException 后适配器降级返回 null，Collectors.toMap 对 null 值抛 NPE。
        本次按官方模式修复三个服务共 14 个注入点，并补齐明细接口的容错。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="19" label="变更文件" />
        <Stat value="14 个" label="注入点加 @LoadBalanced" tone="success" />
        <Stat value="3 个" label="服务新增 LB 构建器 Bean" tone="success" />
        <Stat value="200" label="故障接口实测恢复" tone="success" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '定位根因',
            '用 javap 反编译本地 Maven 仓库的 spring-cloud-commons-4.1.4，确认后置处理器通过 findAnnotationOnBean 检查 @LoadBalanced，未标注的 RestClient.Builder 一律不挂拦截器；结合运行日志 UnknownHostException: user-center 锁定链路',
            '从"疑似脏实例"转向"拦截器未装配"的准确结论',
          ],
          [
            '定义 LB 构建器',
            'account-center、business-center、ai-service 三个通用配置类各新增 @Bean @LoadBalanced RestClient.Builder；Boot 默认 builder 因 @ConditionalOnMissingBean 自动退让，无注入歧义',
            '容器内存在唯一的负载均衡感知 RestClient.Builder',
          ],
          [
            '注入点加限定符',
            '14 个构造器参数统一加 @LoadBalanced：account-center 2 个（用户信息、支付证明适配器），business-center 9 个（5 个 HTTP 适配器、3 个 TCC 协调器/执行器、健康探针），ai-service 3 个（经网关的 Http 客户端）',
            '转账、充值、明细、联系人归档、AI 等全部服务名调用恢复 LB 解析',
          ],
          [
            '清理遗漏与注释',
            'account-center PaymentProofHttpAdapter 残留的 http://localhost:8081 默认值改为 http://user-center；修正 4 处"会被自动装饰"的错误 Javadoc',
            '配置默认值与注释与实际装配机制一致',
          ],
          [
            'NPE 健壮性修复',
            'LedgerApplicationService.batchResolveCounterparties 由 Collectors.toMap 改为容忍 null 的循环收集，与方法注释"失败时降级为空名称"语义一致',
            '用户中心暂时不可用时明细接口降级展示而非整体 500',
          ],
          [
            '治理 Nacos 脏实例',
            '用户在控制台将外部机器注册的 user-center 实例（192.168.108.1:18081）下线；本机网卡与端口核查确认该实例非本机所有',
            '负载均衡不再轮询到不可达或他人环境的实例',
          ],
        ]}
      />

      <H2>变更文件</H2>
      <Table
        headers={['模块', '文件', '变更说明']}
        rows={[
          ['account-center', 'config/AccountCenterCommonConfiguration.java', '新增 @LoadBalanced RestClient.Builder Bean 及中文 Javadoc'],
          ['account-center', 'http/UserInfoHttpAdapter.java、credit/PaymentProofHttpAdapter.java', '注入点加 @LoadBalanced；支付证明默认地址补改 http://user-center'],
          ['account-center', 'application/ledger/LedgerApplicationService.java', 'toMap 改为容忍 null 的循环收集，移除 Collectors 引用'],
          ['business-center', 'config/BusinessCenterCommonConfiguration.java', '新增 @LoadBalanced RestClient.Builder Bean'],
          ['business-center', 'http/ 5 个适配器', 'AccountDirectory、CreditAccountDirectory、UserInfo、PaymentProof、ContactArchive 注入点加 @LoadBalanced'],
          ['business-center', 'tcc/ 3 个类', 'SeataTccCoordinator、SeataGlobalTransactionExecutor、HttpTccCoordinator 注入点加 @LoadBalanced'],
          ['business-center', 'health/ActuatorServiceHealthProbe.java', '注入点加 @LoadBalanced，修正装配机制说明注释'],
          ['ai-service', 'config/AiServiceCommonConfiguration.java', '新增 @LoadBalanced RestClient.Builder Bean'],
          ['ai-service', 'client/ 3 个 Http 客户端', 'HttpUserCenter、HttpBusinessCenter、HttpAccountCenter 注入点加 @LoadBalanced 并修正过期 Javadoc'],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          [
            'Maven 测试',
            'account-center、business-center 全部通过；ai-service 仅剩既有失败（ArchUnit 分层、H2 兼容、SSE 等，与上次改造前基线完全一致，与本次修复无关）',
          ],
          [
            '静态核查',
            '正则全仓扫描确认 src/main 下不再有未标注 @LoadBalanced 的 RestClient.Builder 注入点',
          ],
          [
            '装配证据',
            '验证实例启动日志出现 lbRestClientPostProcessor 与 deferringLoadBalancerInterceptor Bean 装配记录',
          ],
          [
            '故障路径实测',
            '新代码验证实例（18085，关闭 Nacos 注册）请求 GET /api/v1/accounts/me/entries：返回 HTTP 200 且 counterpartyName 成功解析；此前同请求报 UnknownHostException: user-center 并触发 toMap NPE',
          ],
          [
            'Nacos 解析证据',
            '请求触发 [SUBSCRIBE-SERVICE] service:user-center，current ips:(1) 192.168.169.1:8081 healthy=true，脏实例已不在轮询列表；日志无任何服务名 DNS 解析类 WARN',
          ],
        ]}
        rowTone={['success', 'success', 'success', 'success', 'success']}
      />

      <Divider />

      <H2>最终结果</H2>
      <Text>
        Spec 全部条目已实现并验证：@LoadBalanced RestClient.Builder 官方装配模式落地到三个服务，
        14 个注入点全部带限定符，明细接口 NPE 容错补齐，原故障接口实测恢复 200。转账、充值、AI 与
        明细共用同一装饰机制，机制层面已统一验证。
      </Text>
      <Text tone="secondary">
        后续操作：在 IDEA 重启 account-center、business-center、ai-service 三个服务（网关与 user-center
        本次未改动无需重启），随后对转账、充值、AI 做一笔前端冒烟确认。经验已沉淀：Spring Cloud
        2023.0.x 中 RestClient.Builder 必须标注 @LoadBalanced 才会挂载负载均衡拦截器。
      </Text>
      <Text tone="secondary" size="small">生成于"Nacos 负载均衡修复：RestClient 缺失 LB 拦截器"任务完成时。</Text>
    </Stack>
  );
}
