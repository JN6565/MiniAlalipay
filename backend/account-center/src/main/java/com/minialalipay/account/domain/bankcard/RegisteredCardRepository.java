package com.minialalipay.account.domain.bankcard;

import java.util.List;
import java.util.Optional;

/**
 * 银行卡注册仓储端口。
 *
 * <p>管理 bank_card_registration 表的持久化操作。</p>
 */
public interface RegisteredCardRepository {

    /**
     * 保存注册记录。
     *
     * @param card 注册聚合
     */
    void save(RegisteredCard card);

    /**
     * 根据注册 ID 查询注册记录。
     *
     * @param registrationId 注册记录 ID
     * @return 注册记录；不存在时返回空
     */
    Optional<RegisteredCard> findById(String registrationId);

    /**
     * 根据完整卡号查询注册记录。
     *
     * @param cardNumber 完整卡号
     * @return 注册记录；不存在时返回空
     */
    Optional<RegisteredCard> findByCardNumber(String cardNumber);

    /**
     * 查询用户已注册但未绑定的卡列表。
     *
     * @param userId 用户 ID
     * @return REGISTERED 状态的注册记录列表
     */
    List<RegisteredCard> findRegisteredByUserId(String userId);

    /**
     * CAS 更新注册记录状态。
     *
     * @param card 已变更的注册聚合
     * @return 是否更新成功
     */
    boolean updateStatus(RegisteredCard card);
}
