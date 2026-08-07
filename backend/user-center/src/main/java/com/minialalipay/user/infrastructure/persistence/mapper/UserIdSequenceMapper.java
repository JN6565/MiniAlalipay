package com.minialalipay.user.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 用户 ID 日序列 Mapper。
 *
 * <p>操作 {@code user_id_sequence} 表，为每日序列号提供原子递增。
 * 注册时由 {@link com.minialalipay.user.infrastructure.id.UserIdGenerator} 调用，
 * 通过 {@code INSERT ... ON DUPLICATE KEY UPDATE} + {@code LAST_INSERT_ID()} 保证
 * 并发安全地获取当日下一个序列值。</p>
 *
 * <p>使用两步完成（避免依赖 JDBC {@code allowMultiQueries}）：
 * <ol>
 *   <li>{@link #incrementValue}：原子递增，并将新值写入 MySQL 会话状态</li>
 *   <li>{@link #lastInsertId}：读取会话状态中的值</li>
 * </ol>
 * 两步必须在同一事务内执行，由调用方的 {@code @Transactional} 保证。</p>
 *
 * <p>SQL 语句定义在 {@code UserIdSequenceMapper.xml}。</p>
 */
@Mapper
public interface UserIdSequenceMapper {

    /**
     * 原子递增当日序列值。
     *
     * <p>首次调用某日期时插入初始值 1；后续调用在既有值上加 1。
     * 通过 {@code LAST_INSERT_ID(current_value + 1)} 将新值写入 MySQL 会话状态，
     * 随后由 {@link #lastInsertId()} 读取。</p>
     *
     * @param seqDate 序列所属日期（通常为注册当天）
     * @return 受影响的行数（INSERT 返回 1，UPDATE 返回 2，调用方无需关心）
     */
    int incrementValue(@Param("seqDate") LocalDate seqDate);

    /**
     * 读取当前会话 {@code LAST_INSERT_ID()} 值。
     *
     * <p>必须在 {@link #incrementValue} 之后、同一连接（同一事务）内调用，
     * 否则返回 0。</p>
     *
     * @return 最近一次 {@code incrementValue} 设置/递增后的序列值
     */
    int lastInsertId();
}
