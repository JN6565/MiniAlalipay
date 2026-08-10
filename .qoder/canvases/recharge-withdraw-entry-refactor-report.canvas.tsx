import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function RechargeWithdrawEntryRefactorReport() {
  return (
    <Stack gap={20}>
      <H1>充值提现入口重构 完成报告</H1>
      <Text tone="secondary">
        移除不符合真实场景的模拟充值（SYSTEM_ISSUANCE 凭空加钱）C 端入口，新建充值提现页
        /h5/wallet 作为银行卡充值/提现统一入口，银行卡详情页移除操作按钮，并修复金额输入框
        无法输入小数（如 11.11）的缺陷。资金流入渠道收口为：他人转账 + 银行卡充值；
        模拟充值后端链路保留并标记为测试专用。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="12" label="变更文件" />
        <Stat value="3" label="删除文件/目录" />
        <Stat value="0" label="新增类型错误" tone="success" />
        <Stat value="6/6" label="计划项完成" tone="success" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容']}
        rows={[
          ['修复小数输入缺陷', 'AmountInput 根因：受控组件用 number 作 value，输入 11. 被 parseFloat 归约为 11 后重渲染吞掉小数点；改为内部字符串 state 承载显示值，正则校验格式，外部 value 变化时同步（全部充值一键填充不受影响）。转账、充值、提现、还款共用组件全部受益'],
          ['新建钱包页', '/h5/wallet：账户余额卡片（可用/冻结/总资产）+ 银行卡列表（含卡内余额），每卡充值/提现按钮跳转既有 TCC 操作页；无卡空态引导绑卡'],
          ['入口收口', '首页"充值"入口改为"充值提现"指向 /h5/wallet；路由与 H5Layout 标题映射同步更新'],
          ['移除模拟充值前端', '删除 Recharge 页面目录、services/recharge.ts、DAILY_RECHARGE_* 常量、/h5/recharge 路由，全仓 grep 零残留'],
          ['银行卡详情页瘦身', '移除充值/提现按钮区块与样式，保留卡片信息、交易明细、设为默认卡、解绑'],
          ['后端标记废弃', 'RechargeController、RechargeApplicationService、FundingSource.SYSTEM_ISSUANCE 补充中文 Javadoc：测试专用、C 端入口已下线；不删代码不动 TCC 链路'],
          ['文档同步', 'PRD 与系统分析中模拟充值作为资金入口的段落（7.1、FR-UC-001、纳入范围、UC-01A、功能表、API 目录）均标注 C 端入口已下线'],
        ]}
      />

      <Divider />

      <H2>变更文件清单</H2>
      <Table
        headers={['文件', '操作']}
        rows={[
          ['frontend-h5/src/components/h5/AmountInput/index.tsx', '修改：小数输入缺陷修复'],
          ['frontend-h5/src/pages/h5/Wallet/index.tsx + index.less', '新增：充值提现页'],
          ['frontend-h5/config/routes.ts', '修改：删 /h5/recharge，增 /h5/wallet'],
          ['frontend-h5/src/pages/h5/Home/index.tsx', '修改：首页入口指向 /h5/wallet'],
          ['frontend-h5/src/layouts/H5Layout/index.tsx', '修改：标题映射增删'],
          ['frontend-h5/src/pages/h5/BankCardDetail/index.tsx + index.less', '修改：移除充值/提现按钮'],
          ['frontend-h5/src/pages/h5/Recharge/（整目录）', '删除'],
          ['frontend-h5/src/services/recharge.ts', '删除'],
          ['frontend-h5/src/constants/index.ts', '修改：删 DAILY_RECHARGE_LIMIT_FEN、DAILY_RECHARGE_COUNT'],
          ['backend/business-center RechargeController / RechargeApplicationService / FundingSource', '修改：废弃注释（仅注释，不改行为）'],
          ['docs/minialalipay/minialalipay-prd.md', '修改：资金入口描述同步'],
          ['docs/minialalipay/minialalipay-system-analysis.md', '修改：资金入口描述同步（4 处）'],
        ]}
      />

      <Divider />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['npx tsc --noEmit', '通过：存量错误 82 行降至 75 行，零新增（git stash 前后对比确认）'],
          ['mvn compile -pl business-center -am', '通过：注释修改编译无误'],
          ['残留引用检查', '全仓 grep /h5/recharge、services/recharge、DAILY_RECHARGE 均零匹配'],
          ['文件审计', 'Recharge 目录与 recharge.ts 已删除，Wallet 页面文件存在，路由/入口/标题映射逐项 Read 确认'],
          ['前端手工验证', '由用户在本机执行（dev server 运行于 http://localhost:8002，网关 8080 正常 UP）'],
        ]}
        rowTone={[undefined, undefined, undefined, undefined, 'warning']}
      />

      <Divider />

      <H2>最终结果</H2>
      <Text>
        模拟充值 C 端入口完全下线，充值/提现统一收口到 /h5/wallet；银行卡充值（BANK_CARD_RECHARGE）
        与提现（BANK_CARD_WITHDRAW）的 Seata TCC 链路和转账功能一律未动。金额输入支持
        11.11 等小数（最多两位），分换算 Math.round(amount × 100) 精确无损。后端模拟充值保留为
        测试专用通道并已加中文废弃注释，PRD 与系统分析同步更新，符合 AGENTS.md 文档一致性要求。
      </Text>
      <Text tone="secondary" size="small">生成于充值提现入口重构任务完成时。</Text>
    </Stack>
  );
}
