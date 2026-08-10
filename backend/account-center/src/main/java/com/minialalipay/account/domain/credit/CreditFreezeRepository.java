package com.minialalipay.account.domain.credit;

import java.util.Optional;

/**
 * 信用额度冻结记录仓储接口。
 */
public interface CreditFreezeRepository {
    Optional<CreditFreeze> findByTransactionIdAndAccountId(String transactionId, String creditAccountId);

    /**
     * 按统一交易 ID 查询信用额度冻结记录。
     *
     * <p>信用支付每笔交易至多一条冻结记录；Try 未实际冻结额度时（如空回滚）无记录，
     * 返回 {@link Optional#empty()}。终态事实核验据此判断额度占用是否已确认或已释放。</p>
     *
     * @param transactionId 统一交易 ID
     * @return 冻结记录，无记录时为空
     */
    default Optional<CreditFreeze> findByTransactionId(String transactionId) { return Optional.empty(); }

    void save(CreditFreeze freeze);
}
