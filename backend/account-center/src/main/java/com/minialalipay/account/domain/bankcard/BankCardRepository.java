package com.minialalipay.account.domain.bankcard;

import java.util.List;
import java.util.Optional;

/**
 * 银行卡仓储端口，由基础设施层以 account_db.bank_card 表实现。
 *
 * <p>所有查询仅面向绑卡事实；实现层禁止把完整卡号等明文写入日志或返回值。</p>
 */
public interface BankCardRepository {

    /**
     * 查询用户全部 ACTIVE 绑定，按绑定时间升序（最早绑定在前），
     * 默认卡排序由应用层处理。
     *
     * @param userId 用户 ID
     * @return 活动状态的银行卡列表
     */
    List<BankCard> findActiveByUserId(String userId);

    /**
     * 按银行卡 ID 查询绑定记录（含已解绑），用于详情与管理操作。
     *
     * @param cardId 银行卡 ID
     * @return 绑定记录；不存在时返回空
     */
    Optional<BankCard> findById(String cardId);

    /**
     * 统计用户 ACTIVE 绑定数量，用于绑卡上限校验。
     *
     * @param userId 用户 ID
     * @return 活动状态的银行卡数量
     */
    long countActiveByUserId(String userId);

    /**
     * 判断同一 BIN+尾号的卡是否已被该用户 ACTIVE 绑定，用于重复绑卡校验。
     *
     * @param userId 用户 ID
     * @param cardBin 卡号前 6 位 BIN
     * @param cardLast4 卡号后 4 位
     * @return 是否已存在活动绑定
     */
    boolean existsActiveByUserAndCard(String userId, String cardBin, String cardLast4);

    /**
     * 插入新绑定记录（绑卡事实只写一次，不做更新）。
     *
     * @param card 新建的银行卡聚合
     */
    void save(BankCard card);

    /**
     * 乐观锁 CAS 更新：仅当数据库版本与期望版本一致时更新，成功后聚合版本自增。
     * 用于设默认与解绑的条件更新，防止并发操作破坏默认卡互斥不变量。
     *
     * @param card 已变更的银行卡聚合
     * @param expectedVersion 更新前读取到的版本号
     * @return 是否更新成功；false 表示版本冲突，调用方应抛出版本冲突错误
     */
    boolean updateByCas(BankCard card, long expectedVersion);

    /**
     * 余额 CAS 更新：仅当数据库版本与期望版本一致时更新余额字段，成功后版本自增。
     * 用于充值/提现等余额变更操作，防止并发修改破坏余额不变量。
     *
     * @param card 已变更余额的银行卡聚合
     * @param expectedVersion 更新前读取到的版本号
     * @return 是否更新成功；false 表示版本冲突
     */
    boolean updateBalanceByCas(BankCard card, long expectedVersion);
}
