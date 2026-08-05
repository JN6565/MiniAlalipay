# T-12 余额、账本与交易 TCC 交叉评审报告

**评审人：** 王基哲（信用业务与质量保障）

**评审日期：** 2026-08-05

**评审阶段：** 阶段三余额/账本内核、阶段四统一交易状态机与 TCC 全局

**最终结果：** 通过；Java 21 下 `platform-common` 11 项、`business-center` 37 项、`account-center` 218 项测试全部通过

---

## 一、评审范围

| 评审对象 | 评审重点 |
|---|---|
| `AccountBalance`、`FreezeRecord` | 金额非负、余额守恒、冻结状态机、CAS 并发安全 |
| `BalanceApplicationService` | 幂等回读、余额不足、唯一键冲突、余额与冻结事实同事务 |
| `LedgerVoucher`、`LedgerTccApplicationService` | 借贷平衡、不可变分录、数据库汇总二次验平、Outbox 同事务 |
| `CreditTccParticipant` | `CREDIT_PAY` Try/Confirm/Cancel、额度与应收守恒、空回滚、防悬挂 |
| `CreditRepayTccParticipant` | `CREDIT_REPAY` 余额冻结、应收减少、额度恢复、空回滚、防悬挂 |
| `FundTransaction`、`HttpTccCoordinator` | 状态机、Confirm 未知结果、Cancel 恢复、终态事实核验 |
| `TransactionRecoveryScanner` | 服务重启后按原交易接管，不创建第二笔资金交易 |
| 阶段四 Flyway 迁移 | 来源唯一键、TCC 恢复索引、Schema 所有权边界 |

## 二、阶段三余额与账本结论

以下不变量已通过领域测试、应用服务测试和仓储集成测试验证：

1. 余额 Try 只在账户为 `ACTIVE` 且版本匹配时冻结，余额不足返回稳定错误码。
2. 冻结记录以 `transactionId + accountId + purpose` 唯一，重复同参回读，异参拒绝。
3. Confirm 只扣除已冻结金额，Cancel 只释放活动冻结；终态不可反向流转。
4. 复式凭证创建时校验借贷平衡，Confirm 前再次汇总数据库实际分录。
5. 凭证过账与账本 Outbox 在同一账户中心本地事务内提交。
6. 余额、冻结、凭证和分支更新均使用版本或状态条件防止并发覆盖。

## 三、阶段四 T-03 信用 TCC 结论

### 3.1 CREDIT_PAY

| 阶段 | 本地资金动作 | 幂等与屏障 |
|---|---|---|
| Try | 冻结信用额度并创建 `credit_freeze` | `xid + CREDIT_PAY + creditAccountId` 屏障；重复同参不重复冻结 |
| Confirm | 冻结转已用，创建消费明细，增加未出账应收 | 仅允许 `TRIED -> CONFIRMED`；重复确认不重复增加应收 |
| Cancel | 释放信用冻结 | Try 未到达时持久化 `CANCELLED/EMPTY`；正常取消记录 `NORMAL` |

### 3.2 CREDIT_REPAY

| 阶段 | 本地资金动作 | 幂等与屏障 |
|---|---|---|
| Try | 冻结还款余额 | `xid + CREDIT_REPAY + creditAccountId` 屏障；余额冻结复用唯一键和 CAS |
| Confirm | 扣减余额、减少应收、恢复额度、完成还款事实 | 金额必须与 Try 和还款事实一致；重复确认无副作用 |
| Cancel | 释放余额冻结并取消还款事实 | Try 未到达时持久化 `CANCELLED/EMPTY`，晚到 Try 被拒绝 |

信用支付和信用还款均已验证：重复 Try、重复 Confirm、重复 Cancel、额度或余额不足、同键异参、空回滚、晚到 Try、防止已确认分支被取消，以及完整资金生命周期。

## 四、阶段四 T-12 交易状态机与 TCC 全局结论

### 4.1 终态发布器

- Confirm 调用超时后交易保持 `PROCESSING`，全局事务记录 `COMMITTING/UNKNOWN` 并安排重试，不根据超时执行 Cancel。
- 只有账户分支已确认、冻结已确认、账本分支已确认且凭证已过账时，才调用 `finalizeTransaction` 原子发布 `SUCCESS`。
- Confirm 完成但资金事实不一致时写入对账差异并原子转入 `MANUAL_REVIEW`，不会伪造成功。
- Cancel 只有在账户和账本均已取消且没有活动冻结时，才原子发布 `CANCELLED`。

### 4.2 来源唯一约束

`business_db.fund_transaction` 已具备：

```sql
UNIQUE KEY uk_fund_transaction_source (source_type, source_order_id)
```

契约测试按完整列组合校验该约束，同时验证交易幂等键和恢复索引存在，避免同一业务来源创建第二笔资金主单。

### 4.3 Confirm 与 Cancel 恢复

- Confirm 首次失败后，恢复调用重新执行幂等 Try，再使用同一 `xid`、冻结 ID、凭证 ID、分录 ID 和事件 ID 正向 Confirm。
- Cancel 首次失败后保持 `COMPENSATING` 和 `ROLLING_BACK/UNKNOWN`，恢复调用继续按账本、收款方、付款方的逆序执行 Cancel。
- 两种恢复都必须在读取账户中心终态事实后才能发布确定终态。

### 4.4 服务重启恢复

`TransactionRecoveryScanner` 每次最多读取 100 笔超过恢复阈值的 `PROCESSING/COMPENSATING` 交易，并将持久化的原交易交给协调器。分支技术键由交易 ID 稳定派生，因此服务重启不会生成第二笔交易或第二套资金资源。

### 4.5 账本不平保护

账本 Confirm 以数据库实际分录汇总为准。借贷任一侧与交易金额不一致时：

- 拒绝调用 `postAndAppendOutbox`；
- TCC 账本分支保持 `TRIED`；
- 不发布交易成功，等待恢复或人工处理。

## 五、本次新增和强化的阶段四测试

| 测试文件 | 新增或强化内容 |
|---|---|
| `CreditTccParticipantTest` | 直接断言空回滚屏障为 `CANCELLED/EMPTY`，并验证晚到 Try 被拒绝 |
| `CreditRepayTccParticipantTest` | 直接断言还款空回滚屏障、还款事实取消和晚到 Try 拒绝 |
| `HttpTccCoordinatorTest` | Confirm 超时正向重试、Cancel 失败逆序重试、成功终态事实核验、事实不一致转人工 |
| `TransactionRecoveryScannerTest` | 重启扫描接管原交易、空扫描不触发协调器 |
| `LedgerTccApplicationServiceTest` | 数据库借贷不平时拒绝过账并保持 `TRIED` |
| `BusinessCoreMigrationContractTest` | 精确校验 `(source_type, source_order_id)` 来源唯一键 |

## 六、评审结论与后续边界

阶段三分配给王基哲的 T-01、T-02 和 T-12 工作已完成。信用领域模型、迁移契约、余额 CAS 并发安全与账本借贷平衡均有自动化测试覆盖；T-02 已在本机 MySQL 8.0.40 核验 Flyway 成功历史、11 张信用表及 8 项关键约束，详见 `T-02-credit-migration-verification.md`。

阶段四分配给王基哲的 T-03 和 T-12 工作已完成，交付条件中的重复请求、余额不足、Confirm 超时、Cancel 重试、服务重启恢复和账本不平场景均有自动化测试覆盖。

信用查询、还款业务编排、账单任务和面向用户的信用接口属于阶段五 T-04 至 T-06；本报告不将其计入阶段三、阶段四完成条件。当前机器未安装 Docker，因此没有执行容器重建；已用真实 MySQL 8.0.40 的 Flyway 历史和 `information_schema` 完成等价数据库事实核验，并由迁移契约测试持续保护表与约束定义。
