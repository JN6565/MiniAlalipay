package com.minialalipay.account.domain.account;

import java.util.Optional;

/** 余额冻结记录仓储端口，负责唯一业务键查询和状态 CAS。 */
public interface FreezeRecordRepository {

    /** @return 交易、账户与用途唯一确定的冻结记录 */
    Optional<FreezeRecord> find(String transactionId, String accountId, FreezePurpose purpose);

    /**
     * 锁定业务键对应的冻结记录直到当前事务结束，串行化 Confirm 与 Cancel。
     *
     * @return 冻结记录，不存在时为空
     */
    Optional<FreezeRecord> findForUpdate(String transactionId, String accountId, FreezePurpose purpose);

    /** 创建活动冻结记录；唯一键冲突必须由事务回滚或转为幂等读取。 */
    void create(FreezeRecord record);

    /** @return 状态版本更新成功返回 true，版本冲突返回 false */
    boolean update(FreezeRecord record, long expectedVersion);
}
