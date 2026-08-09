import { Divider, Grid, H1, H2, Stack, Stat, Table, Text } from 'qoder/canvas';

export default function CreditRepayGeneratedColumnFixReport() {
  return (
    <Stack gap={20}>
      <H1>修复还款 500：生成列 outstanding_fen 误写 完成报告</H1>
      <Text tone="secondary">
        联调中 POST /api/v1/credit/repayments 返回 500 COMMON_INTERNAL_ERROR，
        数据库侧无任何残留（草稿仍 DRAFT、无还款记录、无冻结、无 TCC 分支），事务被整体回滚。
        根因：ledger_db.credit_purchase.outstanding_fen 是迁移文件定义的
        GENERATED ALWAYS STORED 生成列（amount_fen - repaid_fen - refunded_fen），
        而 CreditPurchaseMapper.updateByCas 的 UPDATE 显式对其赋值，触发 MySQL 3105；
        该异常未被登记为业务异常，映射为 500。修复方式为从 UPDATE 移除生成列赋值，
        由数据库按生成表达式自动计算，无需迁移、不改契约、不改前端。
      </Text>

      <Grid columns={4} gap={16}>
        <Stat value="1" label="变更文件（后端 Mapper）" />
        <Stat value="3105" label="MySQL 生成列错误码" tone="danger" />
        <Stat value="5/5" label="还款相关单测通过" tone="success" />
        <Stat value="全链路" label="真实库复现验证通过" tone="success" />
      </Grid>

      <Divider />

      <H2>排查关键步骤</H2>
      <Table
        headers={['步骤', '内容', '结论']}
        rows={[
          [
            '数据侧取证',
            '直连远端 MySQL 核查失败用户：5 个草稿全部 DRAFT/version=0，credit_repayment、freeze_record(CREDIT_REPAYMENT)、tcc_branch(CREDIT_REPAY) 均零行',
            '事务整体回滚，异常发生在支付证明校验之后、属未登记异常（500 路径）',
          ],
          [
            '静态排除',
            '逐一排除：proof 适配器、freeze_record 约束、tcc_branch 表结构与 CHECK、Mapper INSERT 列、还款三表外键与插入顺序',
            '锁定嫌疑收敛到 applyAllocations 的消费 CAS 更新',
          ],
          [
            '堆栈确认',
            '用户从 IDEA 控制台提供 requestId 对应堆栈：UncategorizedSQLException，SQL 含 outstanding_fen = ?，MySQL 3105',
            '根因实锤：生成列禁止显式赋值',
          ],
          [
            '影响面核查',
            '查询两库 information_schema 全部生成列：仅 credit_purchase.outstanding_fen 一个；credit_bill.outstanding_fen 为普通列；INSERT 本就未写该列',
            '仅需修 CreditPurchaseMapper.updateByCas 一处',
          ],
        ]}
      />

      <H2>变更文件</H2>
      <Table
        headers={['文件', '变更说明']}
        rows={[
          [
            'backend/account-center/.../credit/mapper/CreditPurchaseMapper.java',
            'updateByCas 移除 outstanding_fen = #{outstandingFen}；补充中文 Javadoc 说明生成列由 MySQL 自动计算、显式赋值触发 3105 的原因',
          ],
        ]}
      />

      <H2>验证证据</H2>
      <Table
        headers={['验证项', '方式', '结果']}
        rows={[
          [
            '真实库端到端复现',
            '临时 @SpringBootTest（真实远端 DB + mock PaymentProofPort + @Transactional 回滚）：新建草稿 16500 分 → submitRepayment',
            '日志输出 Try 成功 → Confirm 成功 → 信用还款成功，还款全流程走通',
          ],
          [
            '回归单测',
            'CreditRepaymentServiceTest + CreditControllerTest',
            '5/5 通过，BUILD SUCCESS',
          ],
          [
            '现场清理',
            '临时复现测试 CreditRepayReproTest.java 与 scripts/debug-repayment.sql 已删除并确认不存在',
            '工作区无调试残留',
          ],
        ]}
      />

      <H2>最终结果与用户操作</H2>
      <Text>
        源码修复与验证已全部完成。16:20 的报错堆栈 SQL 仍带 outstanding_fen = ?，
        说明 IDEA 中运行的 account-center 仍是旧字节码。请在 IDEA 中 Rebuild Project
        并重启 account-center，再到 H5 还款页重新确认还款（¥165），预期返回 200 且还款成功。
      </Text>
      <Text tone="secondary" size="small">
        报告生成时间：2026-08-09。依据：Spec 计划文件与本次联调排查执行记录。
      </Text>
    </Stack>
  );
}
