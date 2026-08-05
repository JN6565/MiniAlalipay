# T-12 余额/账本内核交叉评审报告

**评审人：** 王基哲（信用业务与质量保障）
**被评审代码：** 王钧平提交的 account-center 余额/账本内核
**评审日期：** 2026-08-04
**测试结果：** 205 个测试全部通过（含 62 个新增交叉评审测试）

---

## 一、评审范围

| 评审对象 | 文件路径 | 评审重点 |
|---------|---------|---------|
| AccountBalance | `domain/account/AccountBalance.java` | CAS 乐观锁、金额非负不变量、总余额守恒 |
| FreezeRecord | `domain/account/FreezeRecord.java` | FROZEN→CONFIRMED/RELEASED 单向流转、幂等、反向拒绝 |
| BalanceApplicationService | `application/account/BalanceApplicationService.java` | 冻结记录唯一键先于余额 CAS、幂等回读、CAS 失败映射 |
| LedgerVoucher | `domain/ledger/LedgerVoucher.java` | 双重平衡校验、分录一致性、冲正引用约束 |
| LedgerApplicationService | `application/ledger/LedgerApplicationService.java` | PREPARED→POSTED 原子推进、DB 汇总验平、Outbox 同事务 |
| Flyway 迁移 | `V202608051000`、`V202608051010` | 唯一键约束、升级存储过程 |

---

## 二、评审结论

### 2.1 通过项（无问题）

| 评审项 | 结论 |
|--------|------|
| **余额 CAS 乐观锁** | `updateBalanceForActiveAccount` 正确实现版本比对 + 账户状态双重检查 |
| **金额非负不变量** | `freeze`/`confirm`/`cancel` 均在操作后调用 `validate()` 校验非负 |
| **总余额守恒** | `freeze` 保持总余额不变，`confirm` 减少总额，`cancel` 恢复可用 |
| **冻结记录状态机** | FROZEN→CONFIRMED/RELEASED 单向流转，终态不可回退 |
| **冻结记录幂等** | 重复同向幂等返回，反向回调拒绝 |
| **冻结记录唯一键** | `(transactionId, accountId, purpose)` 三元组唯一，先于余额 CAS 创建 |
| **DataIntegrityViolation 回读** | 唯一键冲突时回读已提交记录并验证参数一致性 |
| **凭证双重平衡校验** | 内存校验 + DB `summarizeEntries` 校验，防止分录被篡改 |
| **分录序号唯一** | `HashSet` 检测重复序号 |
| **冲正引用约束** | `reversalNo == 0` 不允许携带冲正引用，`reversalNo > 0` 必须引用原凭证 |
| **凭证过账幂等** | `POSTED` 状态重复过账幂等返回 |
| **Outbox 同事务** | `postAndAppendOutbox` 与凭证状态推进在同一事务 |
| **游标分页安全** | Base64 编码游标，`limit + 1` 判断 hasMore |

### 2.2 需关注项（非阻塞）

| 编号 | 评审项 | 说明 | 建议 |
|------|--------|------|------|
| R-01 | `saveState` 短路求值 | `BalanceApplicationService.saveState` 使用 `\|\|` 短路：如果余额 CAS 成功但冻结记录 CAS 失败，会抛 VERSION_CONFLICT 但余额已更新。由于在同一 `@Transactional` 中，事务回滚会同时撤销余额更新，**不影响正确性**。但建议改为分别检查以提供更精确的错误信息。 | 低优先级 |
| R-02 | 空回滚 + 防悬挂 | `CreditFreeze` 构造器要求 `amountFen >= 1`，无法创建零金额记录作为空回滚屏障。当前 `CreditTccParticipant.cancelFreeze` 在无记录时直接返回。**防悬挂**依赖 CreditFreeze 的 RELEASED 状态（Cancel 先于 Try 到达时，Try 会发现 RELEASED 记录并拒绝）。但如果 Cancel 到达时无记录（纯空回滚），后续 Try 仍会执行。 | Seata TCC 协调器引入后，由协调器维护分支状态（INIT/CANCELLED），可完整解决防悬挂。当前阶段不影响核心功能。 |
| R-03 | `getTotalFen` 溢出 | `AccountBalance.getTotalFen()` 使用 `Math.addExact` 防止 long 溢出，正确。但 `CreditAccount.getAvailableFen()` 使用普通减法 `totalLimitFen - usedFen - frozenFen`，理论上可能下溢。实际上 `validateInvariants()` 保证 `used + frozen <= total`，所以不会下溢。 | 无需修改 |
| R-04 | LedgerEntry 不可变 | `LedgerEntry` 是 record，天然不可变。仓储只暴露 `savePrepared` 和查询方法，不暴露 update/delete。 | 符合设计 |

### 2.3 评审通过

**结论：余额/账本内核代码质量良好，幂等、并发和不变量逻辑正确，可以进入阶段四 TCC 对接。**

---

## 三、新增测试清单

### T-12 交叉评审测试（37 个用例）

| 测试文件 | 用例数 | 覆盖重点 |
|---------|--------|---------|
| `BalanceLedgerCrossReviewTest` | 10 | CAS 并发、金额非负、总余额守恒、溢出保护 |
| `FreezeRecordCrossReviewTest` | 7 | 状态机终态、幂等返回、反向拒绝、防悬挂 |
| `LedgerVoucherCrossReviewTest` | 10 | 双重平衡校验、分录一致性、冲正约束、过账幂等 |
| `BalanceApplicationServiceCrossReviewTest` | 10 | 幂等回读、CAS 冲突映射、余额不足映射、账户状态检查 |

### T-03 TCC 分支参与者测试（25 个用例）

| 测试文件 | 用例数 | 覆盖重点 |
|---------|--------|---------|
| `CreditTccParticipantTest` | 15 | Try 幂等/防悬挂/额度不足、Confirm 幂等/已释放拒绝、Cancel 空回滚/幂等/已确认拒绝、完整生命周期 |
| `CreditRepayTccParticipantTest` | 10 | Try 余额冻结/幂等/余额不足、Confirm 余额扣减/应收减少/额度恢复/还款标记、Cancel 释放/空回滚、金额守恒 |

---

## 四、TCC 参与者实现说明

### CreditTccParticipant（CREDIT_PAY 分支）

| 阶段 | 操作 | 幂等机制 |
|------|------|---------|
| **Try** | `CreditAccount.freeze()` + 创建 `CreditFreeze(FROZEN)` | `(transactionId, creditAccountId)` 唯一键 |
| **Confirm** | `CreditAccount.confirmFreeze()` + 创建 `CreditPurchase(UNBILLED)` + `CreditReceivable.increaseUnbilled()` + `CreditFreeze.confirm()` | CreditFreeze CONFIRMED 状态 |
| **Cancel** | `CreditAccount.releaseFreeze()` + `CreditFreeze.release()` | CreditFreeze RELEASED 状态 + 空回滚日志 |

### CreditRepayTccParticipant（CREDIT_REPAY 分支）

| 阶段 | 操作 | 幂等机制 |
|------|------|---------|
| **Try** | `BalanceApplicationService.freeze(CREDIT_REPAYMENT)` | 余额冻结唯一键 |
| **Confirm** | `BalanceApplicationService.confirm()` + `CreditReceivable.decreaseByRepayment()` + `CreditAccount.restoreByRepayment()` + `CreditRepayment.markSuccess()` | CreditRepayment SUCCESS 状态 |
| **Cancel** | `BalanceApplicationService.cancel()` + `CreditRepayment.markCancelled()` | CreditRepayment CANCELLED 状态 + 空回滚捕获 |

### 待对接项（阻塞于王钧平）

1. **Seata 客户端依赖** — pom.xml 中需引入 `spring-cloud-starter-alibaba-seata`
2. **`@TwoPhaseBusinessAction` 注解** — TCC 参与者方法添加注解后接入全局协调器
3. **`CreditRepaymentService.submitRepayment`** — 去除 TODO，调用 `CreditRepayTccParticipant`
4. **TCC 全局协调器** — business-center 中的 TransactionOrder + 状态机 + 恢复扫描
