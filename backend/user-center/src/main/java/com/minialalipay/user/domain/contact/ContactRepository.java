package com.minialalipay.user.domain.contact;

import java.util.List;
import java.util.Optional;

/**
 * 联系人仓储接口。
 *
 * <p>定义联系人聚合根的持久化操作，由基础设施层实现。
 * 联系人表保存由成功转账自动形成的单向常用收款人投影。</p>
 *
 * @see Contact 联系人聚合根
 * @see com.minialalipay.user.infrastructure.persistence.ContactRepositoryImpl 仓储实现
 */
public interface ContactRepository {

    /**
     * 查询指定用户的常用联系人列表。
     *
     * <p>按置顶优先、最近成功时间倒序排列，排除已隐藏的联系人。</p>
     *
     * @param ownerUserId 联系人列表所有者
     * @param limit       最大返回数量
     * @return 联系人列表
     */
    List<Contact> listByOwner(String ownerUserId, int limit);

    /**
     * 查找指定所有者和收款人的联系人。
     *
     * @param ownerUserId 联系人列表所有者
     * @param payeeUserId 收款用户
     * @return 联系人，如果不存在则返回 empty
     */
    Optional<Contact> findByOwnerAndPayee(String ownerUserId, String payeeUserId);

    /**
     * 插入或更新联系人。
     *
     * <p>如果联系人已存在（联合主键相同），则递增成功次数并更新最近成功时间；
     * 如果不存在，则插入新记录。使用 MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE} 实现。</p>
     *
     * @param contact 联系人
     */
    void upsert(Contact contact);

    /**
     * 更新联系人属性（别名、置顶、隐藏）。
     *
     * <p>使用乐观锁（version）保证并发安全。</p>
     *
     * @param contact 联系人（包含更新后的数据）
     */
    void update(Contact contact);
}
