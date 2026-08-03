package com.minialalipay.account.interfaces.credit.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 触发信用批处理任务请求 DTO。
 *
 * @param businessDate 业务日期，用于幂等控制
 */
public record RunCreditJobRequest(
        @NotNull LocalDate businessDate
) {}
