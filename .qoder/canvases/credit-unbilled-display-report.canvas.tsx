import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function CreditUnbilledDisplayReport() {
  return (
    <Stack gap={20}>
      <H1>补齐花呗未出账明细展示 完成报告</H1>
      <Text tone="secondary">
        用户使用花呗付款后，"最近账单"显示为空。根因是账单每月 1 日才将上月 UNBILLED
        消费汇总为月度账单，当月消费在出账前不会出现在账单列表；而前端此前未接入
        /api/v1/credit/purchases 消费明细接口，导致用户完全看不到自己的消费记录。
        本次在 frontend-h5 补齐未出账消费明细展示与出账规则提示，后端与契约零改动。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="6" label="变更文件（纯前端）" />
        <Stat value="19/19" label="jest 测试通过" tone="success" />
        <Stat value="0" label="后端/契约变更" tone="success" />
        <Stat value="+2" label="新增契约测试" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '定位根因',
            'CreditJobService.runStatement 每月 1 日才出账；GET /credit/bills 只返回已出账月度账单，用户本月 ¥154 消费处于 UNBILLED 状态，数据正确但前端无展示入口',
            '确认为展示缺口而非数据丢失，符合 PRD"未出账消费不伪造为已出账账单"规则',
          ],
          [
            '服务层接入',
            'credit.ts 新增 CreditPurchase 类型（与 OpenAPI schema 一致）与 getPurchases(billingStatus?)，无参时不发送查询参数',
            '前端打通已有的 GET /api/v1/credit/purchases 网关接口',
          ],
          [
            '账单列表页增强',
            'CreditBills 页并行拉取账单与 UNBILLED 明细，顶部新增"未出账消费"区块（时间、金额、状态、提前还款入口、出账规则说明）',
            '用户可立即查看当月花呗消费并发起提前还款',
          ],
          [
            '空状态优化',
            '账单页区分"暂无已出账账单"与"暂无账单，本月消费将于下月 1 日出账"；花呗首页在有未出账金额时提示出账规则并提供"查看未出账明细"入口',
            '消除"消费丢失"的误解，出账规则全程可见',
          ],
        ]}
      />

      <H2>变更文件</H2>
      <Table
        headers={['文件', '变更说明']}
        rows={[
          ['frontend-h5/src/services/credit.ts', '新增 CreditPurchase 接口与 getPurchases 服务方法'],
          ['frontend-h5/src/constants/index.ts', '新增 BILLING_STATUS_TEXT 出账状态中文映射（含枚举含义注释）'],
          ['frontend-h5/src/pages/h5/CreditBills/index.tsx', '并行加载账单与未出账明细，新增未出账消费区块与分级空状态'],
          ['frontend-h5/src/pages/h5/CreditBills/index.less', '新增未出账区块与 bill-status-unbilled 样式'],
          ['frontend-h5/src/pages/h5/Credit/index.tsx', '最近账单空状态增加出账提示与"查看未出账明细"入口'],
          ['frontend-h5/src/pages/h5/Credit/index.less', '新增 empty-state-link 样式'],
          ['frontend-h5/test/services/credit.test.ts', '新增 getPurchases 无参/带筛选两个契约测试'],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['frontend-h5 全量 jest（含新增契约测试）', '5 个套件 19/19 全部通过'],
          ['getPurchases 契约测试', '无参调用 /api/v1/credit/purchases；带 UNBILLED 拼接 ?billingStatus=UNBILLED，均通过'],
          ['tsc --noEmit 类型检查', '本次变更文件无新增错误（仅存 Collection 等页面历史遗留错误）'],
          ['契约与文档一致性', '复用已实现的 OpenAPI 接口，无新增接口/字段，PRD 与系统分析无需变更'],
        ]}
        rowTone={['success', 'success', 'success', undefined]}
      />

      <Divider />

      <H2>最终结果与剩余事项</H2>
      <Text>
        Spec 全部条目已实现并通过测试验证：账单页可展示当月未出账花呗消费明细并支持提前还款，
        花呗首页在有未出账金额时给出出账规则提示与明细入口。如需立即体验出账效果，
        可由管理员触发 /api/v1/ops/credit/** 账单日任务；完整后端联调（可选步骤）
        需启动 MySQL/Redis/Seata 与网关、account-center 后人工验证。
      </Text>
      <Text tone="secondary" size="small">生成于"补齐花呗未出账明细展示"任务完成时。</Text>
    </Stack>
  );
}
