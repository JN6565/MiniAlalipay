import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function FixedQrHuabeiProjectionFixReport() {
  return (
    <Stack gap={20}>
      <H1>固定收款码花呗支付"系统内部错误"修复完成报告</H1>
      <Text tone="secondary">
        固定收款码下付款方用花呗支付时，资金实际已到账（收款方看到支付成功），
        付款方却收到 500"系统内部错误"，重试又提示"资源版本已变化"。
        根因是终态投影仍按已弃用的 active_order_id 单笔回填固定请求，
        本次按一码多收语义重写投影闸门，并对受理后协调异常做兜底，彻底消除该故障路径。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="1" label="修改的后端服务" />
        <Stat value="3" label="变更文件数" />
        <Stat value="242" label="通过测试总数" tone="success" />
        <Stat value="0" label="契约/错误码变更" tone="success" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '定位根因',
            '一码多收模型下 collection_request.active_order_id 已弃用恒为 NULL，但 projectCollectionTerminalState 仍按 active_order_id + transaction_id 回填请求终态，更新 0 行后抛"固定请求终态投影版本已经变化"',
            '确认故障链：finalizeTransaction 回滚 → afterCommit 冒泡为 HTTP 500 → 恢复扫描重试同一异常直至卡死',
          ],
          [
            '重写请求终态投影（核心）',
            '改为按 request_id + status IN (PROCESSING, MANUAL_REVIEW) CAS；先查该请求是否仍有非终态订单（终态闸门），全部终态后按订单事实收敛：存在成功订单则请求 SUCCESS 并回填其交易号，否则 CANCELLED',
            '逐笔发布订单终态不再误伤请求；"先成功一笔、后一笔取消"不会被误投影为 CANCELLED',
          ],
          [
            '收敛异常语义',
            '请求 CAS 更新 0 行时回读请求状态：已是终态视为并发幂等收敛直接跳过，只有状态确实意外才抛异常',
            '并发发布或重试不再回滚真实资金终态',
          ],
          [
            'afterCommit 兜底加固',
            'pay() 中受理提交后的协调调用包 try/catch，失败只记 ERROR 日志交由恢复扫描接管',
            '受理已成功时客户端永远得到 202 PROCESSING，不再出现"受理后 500"',
          ],
        ]}
      />

      <H2>主要变更文件</H2>
      <Table
        headers={['服务', '文件', '变更说明']}
        rows={[
          ['business-center', 'JdbcBusinessStore', '重写 projectCollectionTerminalState 请求级投影：终态闸门 + 成功优先收敛 + 幂等跳过（核心修复）'],
          ['business-center', 'CollectionPaymentApplicationService', 'pay() afterCommit 协调异常兜底，受理结果不被协调失败污染'],
          ['business-center', 'CollectionTerminalProjectionTest', '数据改为一码多收真实形态（active_order_id 为 NULL），新增在途闸门、成功优先收敛、幂等跳过、人工态投影等用例，共 6 个'],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['business-center Maven 全量测试（含投影/协调器/恢复扫描新用例）', '242 个全部通过，BUILD SUCCESS'],
          ['CollectionTerminalProjectionTest', '6 个用例全部通过'],
          ['HttpTccCoordinatorTest / TransactionRecoveryScannerTest', '10 / 4 个用例全部通过'],
          ['契约与文档', '本次是让实现对齐系统分析 9.4.4、13.3 与数据库设计 9.11，OpenAPI、错误码、文档零变更'],
        ]}
        rowTone={['success', 'success', 'success', undefined]}
      />

      <Divider />

      <H2>最终结果与剩余事项</H2>
      <Text>
        代码与测试全部完成且全绿。上线步骤：重启 business-center 使修复生效，然后对存量卡死交易执行一次性修复 SQL：
        UPDATE business_db.tcc_global g JOIN business_db.fund_transaction t ON g.transaction_id = t.transaction_id
        SET g.retry_count = 0, g.next_retry_at = NULL
        WHERE t.status IN ('PROCESSING','COMPENSATING','MANUAL_REVIEW')。
        恢复扫描会自动收敛此前卡住的花呗交易。端到端复测预期：付款方花呗支付得到 202 并跳转结果页，
        收款方订单显示"支付成功"，不再出现 500 与"资源版本已变化"。
      </Text>
      <Text tone="secondary" size="small">
        注："资源版本已变化"是支付已受理后用旧版本重试的预期冲突提示，根因修复后首次支付即成功，无需前端改动。
      </Text>
    </Stack>
  );
}
