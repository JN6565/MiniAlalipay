package com.minialalipay.business.application.port;

/**
 * 银行卡目录端口，跨服务查询银行卡归属与余额。
 *
 * <p>业务中心只读引用银行卡，不持有银行卡事实；所有写操作由 account-center 的
 * Seata TCC 分支完成。</p>
 */
public interface BankCardPort {

    /**
     * 校验银行卡存在且属于当前用户，返回银行卡基本信息。
     *
     * @param userId 当前用户 ID
     * @param cardId 银行卡 ID
     * @return 银行卡只读引用
     * @throws com.minialalipay.common.error.BusinessException 银行卡不存在或不属于该用户
     */
    BankCardReference requireCard(String userId, String cardId);

    /**
     * 查询银行卡虚拟余额（分）。
     *
     * @param userId 当前用户 ID
     * @param cardId 银行卡 ID
     * @return 余额（分）
     */
    long getBalanceFen(String userId, String cardId);

    /** 银行卡只读引用。 */
    record BankCardReference(String cardId, String userId, String bankName, String cardNoMasked,
                             long balanceFen, String status) { }
}
