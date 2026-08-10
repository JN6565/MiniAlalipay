import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function HuabeiFactCheckFixReport() {
  return (
    <Stack gap={20}>
      <H1>花呗支付误判"人工审核中"修复完成报告</H1>
      <Text tone="secondary">
        花呗扫码支付资金已正常到账，却被终态事实核验误判为"资金事实不一致"而转入人工审核。
        本次修复让事实核验按资金路径选择规则集，并让人工态交易可被恢复任务自动复核收敛，
        花呗支付从此与余额转账一样直接显示"支付成功"。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="2" label="修改的后端服务" />
        <Stat value="13" label="主代码变更文件" />
        <Stat value="576" label="通过测试总数" tone="success" />
        <Stat value="0" label="契约/错误码变更" tone="success" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '定位根因',
            '终态事实核验使用余额转账专用规则：只认 LEDGER 账本分支与 TRANSFER_OUT 余额冻结，花呗写的是 CREDIT_PAY_LEDGER 分支与 credit_freeze 表，两项必不通过',
            '确认误判路径：SUCCESS_FACT_MISMATCH → MANUAL_REVIEW 且永不收敛',
          ],
          [
            '修核验规则（account-center）',
            'inspect() 先查交易是否存在 CREDIT_PAY 账户分支：有则按信用规则集核验（credit_freeze 已确认/已释放 + CREDIT_PAY_LEDGER 分支），无则保持余额规则不动',
            '花呗成功/取消两套事实均可正确判定一致',
          ],
          [
            '自动复核收敛（business-center）',
            'FundTransaction 新增人工态转在途态领域方法；恢复扫描器定期复核人工态交易，用原稳定分支键重驱 TCC 并重新核验；事实一致即发布终态并自动处置工单',
            '存量卡住的 ¥22 花呗交易将被自动恢复为支付成功',
          ],
          [
            '投影与工单配套',
            'C2C 订单投影允许从 MANUAL_REVIEW 更新为终态；终态发布同事务把 manual_case 置为 RESOLVED；再次进入人工态复用既有工单',
            '订单不再永远卡在人工态，人工队列不产生重复工单',
          ],
        ]}
      />

      <H2>主要变更文件</H2>
      <Table
        headers={['服务', '文件', '变更说明']}
        rows={[
          ['account-center', 'TransactionFactApplicationService', '按资金路径选择核验规则集（核心修复）'],
          ['account-center', 'TccBranchRepository / JdbcTccBranchRepository', '新增按分支类型存在性查询 hasAccountBranch'],
          ['account-center', 'CreditFreezeRepository / Mapper / Impl', '新增按交易号查询信用冻结记录'],
          ['business-center', 'FundTransaction', '新增 resumeFromManualReview 状态机方法'],
          ['business-center', 'HttpTccCoordinator / SeataTccCoordinator', '新增 recheckManualReview 复核入口，工单复用'],
          ['business-center', 'TransactionRecoveryScanner', '新增人工态定期复核调度任务'],
          ['business-center', 'JdbcBusinessStore', '人工态查询、工单自动处置与复用、C2C 投影条件放宽'],
          ['docs', 'minialalipay-system-analysis.md', '补充核验规则集选择与人工态复核收敛设计'],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['account-center Maven 测试（含新增三组核验规则测试）', '338 个全部通过，BUILD SUCCESS'],
          ['business-center Maven 测试（含状态机/扫描器/协调器复核新用例）', '238 个全部通过，BUILD SUCCESS'],
          ['前端改动', '无需改动：结果页返回首页与首页静默刷新余额机制已具备'],
          ['契约影响', 'transaction-facts 内部接口出入参不变，OpenAPI 与错误码零变更'],
        ]}
        rowTone={['success', 'success', undefined, undefined]}
      />

      <Divider />

      <H2>最终结果与剩余事项</H2>
      <Text>
        代码与文档修改全部完成且测试全绿。重启 account-center 与 business-center 后：
        花呗扫码支付将直接显示"支付成功"；返回首页可见"花呗可用"额度减少、最近交易出现该笔支出；
        此前卡住的 ¥22 交易将由复核任务自动恢复为成功。回归验证步骤请由您重启服务后执行。
      </Text>
      <Text tone="secondary" size="small">生成于花呗支付事实核验修复任务完成时。</Text>
    </Stack>
  );
}
