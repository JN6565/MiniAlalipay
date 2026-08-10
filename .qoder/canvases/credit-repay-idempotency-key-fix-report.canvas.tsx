import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function CreditRepayIdempotencyKeyFixReport() {
  return (
    <Stack gap={20}>
      <H1>修复还款缺少幂等键 完成报告</H1>
      <Text tone="secondary">
        用户在 H5 还款页点击"确认还款"失败，repayment-drafts 请求返回
        COMMON_INVALID_REQUEST / 请求参数不合法。根因并非金额或支付密码问题：后端
        POST /api/v1/credit/repayment-drafts 与 /api/v1/credit/repayments
        均要求必填 Idempotency-Key 请求头，缺失时被异常处理器映射为该错误码；而前端
        credit.ts 的两个写接口从未携带该头。本次为两个接口补齐幂等键并新增契约测试，
        后端与契约零改动。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="2" label="变更文件（纯前端）" />
        <Stat value="9/9" label="credit 测试通过" tone="success" />
        <Stat value="23/23" label="services 套件通过" tone="success" />
        <Stat value="+4" label="新增契约测试" />
      </Grid>

      <Divider />

      <H2>关键步骤</H2>
      <Table
        headers={['步骤', '内容', '效果']}
        rows={[
          [
            '定位根因',
            'CreditController 两个写接口声明 @RequestHeader("Idempotency-Key") 必填；缺头抛 MissingRequestHeaderException，被 AccountCenterExceptionHandler 映射为 COMMON_INVALID_REQUEST 400',
            '确认与截图报错完全一致，排除金额格式、DTO 校验（@NotNull @Min(1) 均满足）等方向',
          ],
          [
            '补齐服务层',
            'credit.ts 的 createRepaymentDraft 与 submitRepayment 增加可选幂等键参数，缺省时通过 generateIdempotencyKey() 生成 idem_<uuid>（符合后端 [A-Za-z0-9._:-]{16,64} 校验），与 transfer.ts 既有模式一致',
            '两个写接口均满足契约 idempotency: REQUIRED 要求，重试同一笔可复用原键',
          ],
          [
            '补充契约测试',
            'credit.test.ts 新增 4 条用例：请求路径、请求体、自动生成 idem_ 前缀幂等键、显式传键原样透传',
            '防止幂等键再次被回归删除',
          ],
          [
            'API 级复现验证',
            '注册新用户经网关调用 repayment-drafts：不带键复现 COMMON_INVALID_REQUEST；带键通过头校验进入业务校验（新用户应收为 0，返回 REPAYMENT_AMOUNT_INVALID 属正常业务拦截）',
            '真实链路证明根因与修复机制有效',
          ],
        ]}
      />

      <H2>变更文件</H2>
      <Table
        headers={['文件', '变更说明']}
        rows={[
          [
            'frontend-h5/src/services/credit.ts',
            'createRepaymentDraft / submitRepayment 携带 Idempotency-Key 请求头，新增可选幂等键参数与中文注释',
          ],
          [
            'frontend-h5/test/services/credit.test.ts',
            '新增 4 条写接口契约测试（路径、请求体、幂等键自动生成与显式透传）',
          ],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '结果']}
        rows={[
          ['jest test/services/credit.test.ts', '9/9 通过（含新增 4 条）'],
          ['jest test/services 全量', '5 个套件 23/23 通过，无回归'],
          ['tsc --noEmit 与 typecheck-run2.log 基线比对', 'credit.ts 与测试文件零错误，未引入新增类型错误'],
          ['网关 API 复现（修复前行为）', '不带 Idempotency-Key → COMMON_INVALID_REQUEST / 请求参数不合法，与用户截图一致'],
          ['网关 API 复现（修复后行为）', '携带 Idempotency-Key → 通过请求头校验，返回业务错误码 REPAYMENT_AMOUNT_INVALID（新用户应收为 0，属正常业务拦截）'],
        ]}
        rowTone={['success', 'success', 'success', 'success', 'success']}
      />

      <Divider />

      <H2>最终结果与剩余事项</H2>
      <Text>
        Spec 全部条目已实现并通过自动化验证：还款草稿与提交还款请求均携带契约要求的
        Idempotency-Key，COMMON_INVALID_REQUEST 根因已消除。剩余事项：用户将在 H5
        还款页对已有应收账户（截图场景：应收 ¥154.00）执行手动联调，确认完整还款流程
        成功跳转花呗页；验证通过后可按规范提交，建议提交信息
        fix(h5): 还款草稿与提交还款补齐 Idempotency-Key 请求头。
      </Text>
    </Stack>
  );
}
