package com.minialalipay.account.application.credit.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 信用定时任务运行记录 DTO。用于运维接口响应。
 *
 * @param runId 运行 ID
 * @param jobType 任务类型（STATEMENT/DUE_CHECK）
 * @param businessDate 业务日期
 * @param status 任务状态（PENDING/RUNNING/SUCCESS/FAILED/MANUAL_REVIEW）
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 * @param errorCode 错误码（失败时）
 */
public record CreditJobRunDTO(
        String runId,
        String jobType,
        LocalDate businessDate,
        String status,
        Instant startedAt,
        Instant completedAt,
        String errorCode
) {}
