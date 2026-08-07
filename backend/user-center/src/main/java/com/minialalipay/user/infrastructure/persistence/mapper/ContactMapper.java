package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.ContactPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 联系人 MyBatis Mapper 接口。
 *
 * <p>负责 {@code contact} 表的数据库操作。
 * 所有 SQL 语句定义在对应的 XML 映射文件中（{@code ContactMapper.xml}）。</p>
 */
@Mapper
public interface ContactMapper {

    /**
     * 查询指定用户的常用联系人列表。
     *
     * @param ownerUserId 联系人列表所有者
     * @param limit       最大返回数量
     * @return 联系人列表
     */
    List<ContactPO> listByOwner(@Param("ownerUserId") String ownerUserId, @Param("limit") int limit);

    /**
     * 查找指定所有者和收款人的联系人。
     *
     * @param ownerUserId 联系人列表所有者
     * @param payeeUserId 收款用户
     * @return 联系人持久化对象，如果不存在则返回 null
     */
    ContactPO selectByOwnerAndPayee(
            @Param("ownerUserId") String ownerUserId,
            @Param("payeeUserId") String payeeUserId
    );

    /**
     * 插入或更新联系人（归档）。
     *
     * <p>如果联系人已存在，则递增 success_count 并更新 last_success_at；
     * 如果不存在，则插入新记录。</p>
     *
     * @param ownerUserId  联系人列表所有者
     * @param payeeUserId  收款用户
     * @param lastSuccessAt 最近成功时间
     */
    void upsert(
            @Param("ownerUserId") String ownerUserId,
            @Param("payeeUserId") String payeeUserId,
            @Param("lastSuccessAt") Instant lastSuccessAt
    );

    /**
     * 更新联系人属性（别名、置顶、隐藏）。
     *
     * @param contact 联系人持久化对象
     * @return 更新的行数（成功时返回 1，版本冲突时返回 0）
     */
    int update(ContactPO contact);
}
