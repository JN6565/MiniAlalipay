package com.minialalipay.account.interfaces.credit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建还款草稿请求 DTO。
 *
 * @param amountFen 还款金额（分），必须为正整数
 */
public record CreateRepaymentDraftRequest(
        @NotNull @Min(1) Long amountFen
) {}
