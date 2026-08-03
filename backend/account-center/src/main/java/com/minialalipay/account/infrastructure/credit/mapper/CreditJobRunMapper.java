package com.minialalipay.account.infrastructure.credit.mapper;

import com.minialalipay.account.infrastructure.credit.po.CreditJobRunPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 信用定时任务执行记录 Mapper，对应 {@code ledger_db.credit_job_run} 表。
 *
 * <p>提供定时任务执行记录的按任务类型和业务日期查询、插入及乐观锁 CAS 更新能力。
 * 通过 (jobType, businessDate) 唯一键保证同一业务日期同一类型任务不重复执行。</p>
 */
@Mapper
public interface CreditJobRunMapper {

    /**
     * 根据任务类型和业务日期查询执行记录。
     *
     * @param jobType 任务类型
     * @param businessDate 业务日期
     * @return 任务执行记录 PO，未找到时返回 null
     */
    @Select("SELECT * FROM ledger_db.credit_job_run "
            + "WHERE job_type = #{jobType} AND business_date = #{businessDate}")
    CreditJobRunPO findByJobTypeAndBusinessDate(
            @Param("jobType") String jobType,
            @Param("businessDate") LocalDate businessDate);

    /**
     * 插入任务执行记录。
     *
     * @param po 任务执行记录持久化对象
     * @return 受影响行数
     */
    @Insert("INSERT INTO ledger_db.credit_job_run "
            + "(job_run_id, job_type, business_date, status, started_at, finished_at, "
            + "error_message, version, created_at, updated_at) "
            + "VALUES (#{jobRunId}, #{jobType}, #{businessDate}, #{status}, #{startedAt}, "
            + "#{finishedAt}, #{errorMessage}, #{version}, #{createdAt}, #{updatedAt})")
    int insert(CreditJobRunPO po);

    /**
     * 乐观锁 CAS 更新任务执行记录。
     *
     * <p>仅当数据库中的 version 与传入的 version 一致时才更新，
     * 更新成功后 version 自增 1。</p>
     *
     * @param po 包含最新字段值及当前版本号的任务执行记录 PO
     * @return 受影响行数，0 表示版本号不匹配（并发冲突）
     */
    @Update("UPDATE ledger_db.credit_job_run "
            + "SET status = #{status}, started_at = #{startedAt}, finished_at = #{finishedAt}, "
            + "error_message = #{errorMessage}, version = version + 1, updated_at = #{updatedAt} "
            + "WHERE job_run_id = #{jobRunId} AND version = #{version}")
    int updateByCas(CreditJobRunPO po);
}
