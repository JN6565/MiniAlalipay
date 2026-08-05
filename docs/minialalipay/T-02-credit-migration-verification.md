# T-02 信用 Flyway 迁移验证记录

**负责人：** 王基哲

**验证日期：** 2026-08-05

**迁移文件：** `backend/account-center/src/main/resources/db/migration/V202608050900__create_credit_tables.sql`

## 一、验证依据

- 信用额度与冻结事实归属 `account_db`。
- 信用应收、消费、账单、还款分配及批处理事实归属 `ledger_db`。
- 金额统一使用整数分，信用固定额度为 500000 分。
- 幂等路径必须由交易、来源或业务日期唯一键保护。
- 已执行 Flyway 迁移不得修改、删除或重命名。

## 二、真实 MySQL 验证结果

本机 MySQL 8.0.40 已完成只读核验：

1. `account_db.flyway_schema_history` 中版本 `202608050900` 的 `success` 为 `1`。
2. `account_db` 已存在 `credit_account`、`credit_freeze`、`credit_repayment_draft`。
3. `ledger_db` 已存在 `credit_receivable`、`credit_purchase`、`credit_bill`、`credit_bill_item`、
   `credit_repayment`、`credit_repayment_allocation`、`credit_repayment_allocation_detail`、`credit_job_run`。
4. `information_schema.TABLE_CONSTRAINTS` 已确认额度上限、额度占用、冻结幂等、逾期应收、消费交易、
   账单明细、还款交易和任务业务日期等 8 个关键约束实际存在。
5. `CreditMigrationContractTest` 校验上述 11 张表的建表语句，以及额度、应收、消费、账单、还款和任务幂等关键约束。

## 三、历史迁移处理决定

该迁移已经执行并形成 Flyway 校验和，因此不能再按当前规范拆成两个文件。保留它属于不可变历史兼容处理，
不是允许后续迁移继续跨 Schema。此后的 `account_db` 与 `ledger_db` 变更必须分别新增迁移文件，禁止跨 Schema DDL、
外键或联表。

## 四、结论

T-02 的迁移文件、真实 MySQL 执行事实、信用表完整性和关键约束均已验证通过。阶段三代码交付条件已满足；
提交给王钧平的代码评审与最终合入属于协作流程状态，不改变本地功能完成结论。
