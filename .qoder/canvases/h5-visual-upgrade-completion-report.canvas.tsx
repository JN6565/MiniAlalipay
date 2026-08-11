import {
  Banner,
  Callout,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Grid,
  H1,
  H2,
  Stack,
  Stat,
  Table,
  Tag,
  Text,
  Timeline,
} from 'qoder/canvas';

export default function H5VisualUpgradeCompletionReport() {
  return (
    <Stack gap={20}>
      <H1>C 端 H5 视觉与体验升级 · 完成报告</H1>
      <Banner tone="success" title="全部阶段已完成并通过验证">
        按 Spec《C端H5视觉体验升级》逐阶段落地：设计稿评审 → 设计令牌与公共组件 →
        后端契约扩展（完整卡号 + 交易后余额）→ 五批页面实现 → tsc / Maven / 契约 / 文档四重验证。
      </Banner>

      <Grid columns={4} gap={16}>
        <Stat value="10 / 10" label="计划阶段完成" tone="success" />
        <Stat value="69 ≤ 75" label="tsc 错误行数（对比基线，零新增）" tone="success" />
        <Stat value="356" label="account-center 测试通过（0 失败）" tone="success" />
        <Stat value="30+" label="改造/新建前端文件" />
      </Grid>

      <Divider />

      <H2>执行时间线</H2>
      <Timeline
        events={[
          {
            title: '阶段一 · 设计稿输出（Canvas 评审）',
            description:
              'h5-design-tokens.canvas.tsx（色彩令牌 + 七行银行卡面渐变纹样）与 h5-ui-mockups.canvas.tsx（核心页面高保真线框）经用户四点反馈定稿：卡面去风格命名文字、全局移除冻结金额、首页只展示总资产、快捷功能一行 5 个。',
            tone: 'success',
          },
          {
            title: '阶段二 · 设计令牌与公共组件',
            description:
              'overrides.css + theme/index.ts 双源令牌（--h5-* 含暗色预留位）；Skeleton / RevealToggle / EmptyState / MonthGroupList / BankCardFace 五个公共组件。',
            tone: 'success',
          },
          {
            title: '阶段三 · 后端契约扩展',
            description:
              'POST /api/v1/bank-cards/{cardId}/full-card-number（一次性支付证明换取完整卡号）；ledger_entry 新增 balance_after_fen 展示列（Flyway 迁移 + 过账回填）。OpenAPI、数据库设计、系统分析文档同步。',
            tone: 'success',
          },
          {
            title: '阶段四 A · 核心资金视图',
            description:
              '首页（资产摘要 + 快捷宫格 + 生活服务区 + 花呗摘要）、钱包页（渐变余额卡 + 仿真卡面）、充值/提现令牌化、账户明细（月分组 + 收支汇总头 + 业务类型标签 + 交易后余额 + 双筛选）。',
            tone: 'success',
          },
          {
            title: '阶段四 B · 银行卡模块',
            description:
              '列表仿真卡面（七行银行渐变 + CSS 纹样）、详情页操作组（查看余额 / 密码验签查看完整卡号 / 查看账单）、新建卡账单页 /h5/bank-cards/:id/bills、绑卡页卡片化。',
            tone: 'success',
          },
          {
            title: '阶段四 C · 支付流程',
            description:
              '转账确认页订单卡片化、结果页三态 + 「返回首页 / 查看明细」、扫码支付与收款页商户信息卡令牌化。',
            tone: 'success',
          },
          {
            title: '阶段四 D · Mini 花呗',
            description:
              '花呗首页额度环形图（SVG）+ 待还大字 + 账单入口（移除冻结额度）；账单页月分组状态标签；还款页骨架屏 + 令牌化（流程：输金额 → 分配预览 → 密码 → 结果）。',
            tone: 'success',
          },
          {
            title: '阶段四 E · 门面与收尾',
            description:
              '登录/注册品牌视觉（渐变头图 + Logo + 表单卡片 + 错误提示可执行化）；AI Talk 局部令牌桥接全局 --h5-*；其余 15+ 页面令牌级跟随；/h5/profile/edit 保留不动。',
            tone: 'success',
          },
          {
            title: '验证 · tsc / Maven / 契约 / 文档',
            description:
              'npx tsc --noEmit 69 行（基线 75，零新增）；mvn test 356 通过 BUILD SUCCESS；OpenAPI 契约测试全绿；文档与实现一致。',
            tone: 'success',
          },
        ]}
      />

      <Divider />

      <H2>关键改动文件</H2>
      <Table
        headers={['模块', '文件', '改动要点']}
        rows={[
          [
            '设计令牌',
            'src/overrides.css · src/theme/index.ts · src/components/h5/common/*',
            '--h5-* 全量令牌（品牌渐变/信用渐变/状态色/圆角阴影）+ 五个公共组件 + BankCardFace 七行银行卡面',
          ],
          [
            '批次 A',
            'Home · Wallet · Transactions · BankCardRecharge/Withdraw · services/account.ts',
            '资产掩码切换、月分组明细（balanceAfterFen）、骨架屏加载态',
          ],
          [
            '批次 B',
            'BankCards · BankCardDetail · BankCardBills（新建）· BankCardAdd · services/bankCard.ts · config/routes.ts · H5Layout',
            '仿真卡面列表、完整卡号密码验签闭环（BANK_CARD_NUMBER_VIEW）、卡账单路由与标题映射',
          ],
          [
            '批次 C',
            'TransferConfirm · TransferResult · QrPay · CollectionPay',
            '订单卡片化、结果页三态 + 双入口、商户信息卡令牌化',
          ],
          [
            '批次 D',
            'Credit · CreditBills · CreditRepay',
            'LimitRing SVG 环形图、账单状态四态标签、还款页骨架屏 + 渐变主按钮',
          ],
          [
            '批次 E',
            'Login · Register · AITalk/index.less 等 18 个页面样式',
            '品牌渐变头图、错误提示可执行化、--ai-* 桥接 --h5-*、全量页面背景/主色令牌化',
          ],
          [
            '后端契约',
            'contracts/openapi/minialalipay-api.yaml · docs（系统分析/数据库设计/银行卡设计）',
            'full-card-number 端点 + balanceAfterFen 字段 + Flyway 迁移文档同步',
          ],
        ]}
      />

      <Divider />

      <H2>验证证据</H2>
      <Grid columns={2} gap={16}>
        <Card>
          <CardHeader title="前端类型检查" />
          <CardBody>
            <Stack gap={8}>
              <Text>npx tsc --noEmit 输出 69 行，基线 75 行，零新增错误；剩余均为既有 axios 拆包类型噪声。</Text>
              <Tag tone="success">通过</Tag>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="后端单元与契约测试" />
          <CardBody>
            <Stack gap={8}>
              <Text>mvn -pl account-center -am test：Tests run: 356, Failures: 0, Errors: 0，BUILD SUCCESS（含 OpenApiContractTest）。</Text>
              <Tag tone="success">通过</Tag>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="契约一致性" />
          <CardBody>
            <Stack gap={8}>
              <Text>OpenAPI 含 full-card-number 端点与 balanceAfterFen 字段；错误码无新增（复用 422/404）；契约测试全绿。</Text>
              <Tag tone="success">通过</Tag>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="文档同步（AGENTS.md 要求）" />
          <CardBody>
            <Stack gap={8}>
              <Text>系统分析（账本接口 + 银行卡模块）、数据库设计（balance_after_fen 写入规则）、银行卡设计（完整卡号端点行）均已与实现对齐。</Text>
              <Tag tone="success">通过</Tag>
            </Stack>
          </CardBody>
        </Card>
      </Grid>

      <Callout tone="info" title="边界与假设（与 Spec 一致）">
        生活服务区仅 UI 占位 + Toast；银行 Logo 为自绘 SVG 简化标识；存量「处理中」历史交易不在修复范围；深色模式仅令牌层预留变量。完整卡号明文仅内存展示，不落日志与本地存储。
      </Callout>

      <Text tone="secondary" size="small">
        Spec：C端H5视觉体验升级_task-c78.md · 设计稿：.qoder/canvases/h5-design-tokens.canvas.tsx、h5-ui-mockups.canvas.tsx
      </Text>
    </Stack>
  );
}
