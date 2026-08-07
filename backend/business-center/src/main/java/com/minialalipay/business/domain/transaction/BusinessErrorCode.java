package com.minialalipay.business.domain.transaction;

import com.minialalipay.common.error.ErrorCode;

/** 业务中心转账、场景订单、运营处置和监控投影对外错误码。 */
public enum BusinessErrorCode implements ErrorCode {
    /** 收款用户不存在。 */ PAYEE_NOT_FOUND("PAYEE_NOT_FOUND", "收款用户不存在", 404),
    /** 禁止付款给本人。 */ SELF_PAYMENT_FORBIDDEN("SELF_PAYMENT_FORBIDDEN", "不允许向本人账户付款", 422),
    /** 账户非正常状态。 */ ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "账户当前不可用", 422),
    /** 模拟充值超过单笔、单日或次数限制。 */ RECHARGE_LIMIT_EXCEEDED("RECHARGE_LIMIT_EXCEEDED", "模拟充值超过限额", 422),
    /** 转账金额不在产品边界内。 */ AMOUNT_OUT_OF_RANGE("AMOUNT_OUT_OF_RANGE", "金额超出允许范围", 422),
    /** 草稿不存在或不属于当前用户。 */ DRAFT_NOT_FOUND("DRAFT_NOT_FOUND", "交易草稿不存在", 404),
    /** 草稿状态不允许编辑或提交。 */ DRAFT_NOT_EDITABLE("DRAFT_NOT_EDITABLE", "交易草稿当前不可编辑", 409),
    /** CAS 版本已变化。 */ VERSION_CONFLICT("VERSION_CONFLICT", "资源版本已经变化", 409),
    /** 风险规则拒绝本次操作。 */ RISK_REJECTED("RISK_REJECTED", "风险检查拒绝本次操作", 422),
    /** 风险规则要求运营人工审核，尚未进入资金执行。 */ RISK_MANUAL_REVIEW("RISK_MANUAL_REVIEW", "操作已进入人工审核", 202),
    /** 支付密码证明失效。 */ PAYMENT_PROOF_INVALID("PAYMENT_PROOF_INVALID", "支付密码证明无效或已过期", 409),
    /** 确认已过期。 */ CONFIRMATION_EXPIRED("CONFIRMATION_EXPIRED", "确认令牌已过期", 409),
    /** 确认主体或用户不匹配。 */ CONFIRMATION_MISMATCH("CONFIRMATION_MISMATCH", "确认内容与当前业务对象不一致", 409),
    /** 草稿字段或密码版本已变化。 */ CONFIRMATION_STALE("CONFIRMATION_STALE", "业务对象已变化，请重新确认", 409),
    /** 同一幂等键用于不同请求。 */ IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "相同幂等键对应的请求参数不一致", 409),
    /** 交易不存在或不属于当前用户。 */ TRANSACTION_NOT_FOUND("TRANSACTION_NOT_FOUND", "交易不存在", 404),
    /** 交易正在处理中，客户端应轮询终态。 */ TRANSACTION_PROCESSING("TRANSACTION_PROCESSING", "交易正在处理中", 202),
    /** 交易结果待确认，尚未形成确定终态。 */ TRANSACTION_PENDING("TRANSACTION_PENDING", "交易结果待确认", 202),
    /** 回执尚未达到确定终态。 */ RECEIPT_NOT_READY("RECEIPT_NOT_READY", "交易尚未形成确定回执", 409),
    /** 订单不存在或当前主体无权感知其存在。 */ ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在", 404),
    /** 订单已超过业务有效期。 */ ORDER_EXPIRED("ORDER_EXPIRED", "订单已过期", 410),
    /** 订单当前不可编辑。 */ ORDER_NOT_EDITABLE("ORDER_NOT_EDITABLE", "订单当前不可编辑", 409),
    /** 订单当前不可取消。 */ ORDER_NOT_CANCELLABLE("ORDER_NOT_CANCELLABLE", "订单当前不可取消", 409),
    /** 订单状态不允许执行当前操作。 */ ORDER_STATE_INVALID("ORDER_STATE_INVALID", "订单状态不允许该操作", 409),
    /** 订单已关联统一交易，不能再次受理。 */ ORDER_ALREADY_CLAIMED("ORDER_ALREADY_CLAIMED", "订单已经关联交易", 409),
    /** 动态二维码令牌无效、过期或与订单不匹配。 */ QR_TOKEN_INVALID("QR_TOKEN_INVALID", "扫码令牌无效", 404),
    /** 动态二维码令牌已被其他 H5 会话绑定。 */ QR_TOKEN_CONSUMED("QR_TOKEN_CONSUMED", "扫码令牌已绑定其他会话", 409),
    /** 个人收款码无效、停用或已换码。 */ P2P_CODE_INVALID("P2P_CODE_INVALID", "个人收款码无效", 404),
    /** 收款令牌无效、过期或与请求不匹配。 */ COLLECTION_TOKEN_INVALID("COLLECTION_TOKEN_INVALID", "收款令牌无效", 404),
    /** 固定收款请求已过期。 */ COLLECTION_REQUEST_EXPIRED("COLLECTION_REQUEST_EXPIRED", "固定收款请求已过期", 410),
    /** 固定收款请求已取消。 */ COLLECTION_REQUEST_CANCELLED("COLLECTION_REQUEST_CANCELLED", "固定收款请求已取消", 409),
    /** 固定收款请求已有付款正在处理中。 */ COLLECTION_REQUEST_PROCESSING("COLLECTION_REQUEST_PROCESSING", "固定收款请求已有付款处理中", 409),
    /** 固定收款请求已经产生最终成功付款。 */ COLLECTION_REQUEST_PAID("COLLECTION_REQUEST_PAID", "固定收款请求已经支付", 409),
    /** 固定收款请求不存在。 */ REQUEST_NOT_FOUND("REQUEST_NOT_FOUND", "收款请求不存在", 404),
    /** 固定收款请求当前不可取消。 */ REQUEST_NOT_CANCELLABLE("REQUEST_NOT_CANCELLABLE", "收款请求当前不可取消", 409),
    /** 已锁定的订单金额不可修改。 */ AMOUNT_IMMUTABLE("AMOUNT_IMMUTABLE", "金额已经锁定，不允许修改", 422),
    /** 当前业务场景禁止指定资金来源。 */ FUNDING_SOURCE_NOT_ALLOWED("FUNDING_SOURCE_NOT_ALLOWED", "当前场景不允许该资金来源", 422),
    /** 工单状态不允许当前处置动作。 */ CASE_STATE_INVALID("CASE_STATE_INVALID", "工单状态不允许该操作", 409),
    /** 请求主体没有运营读写权限。 */ OPS_PERMISSION_REQUIRED("OPS_PERMISSION_REQUIRED", "需要运营权限", 403),
    /** 告警状态不允许当前处置动作。 */ ALERT_STATE_INVALID("ALERT_STATE_INVALID", "告警状态不允许该操作", 409),
    /** 告警不存在或当前主体无权感知其存在。 */ ALERT_NOT_FOUND("ALERT_NOT_FOUND", "告警不存在", 404),
    /** 运营处置缺少不可抵赖的证据。 */ EVIDENCE_REQUIRED("EVIDENCE_REQUIRED", "处理证据不能为空", 422),
    /** 同一业务日期的任务已经运行。 */ JOB_ALREADY_RUNNING("JOB_ALREADY_RUNNING", "同一业务日期的任务正在执行", 409),
    /** 指定报表尚未通过质量门禁发布。 */ REPORT_NOT_PUBLISHED("REPORT_NOT_PUBLISHED", "报表尚未发布", 404),
    /** 运营查询时间范围不符合约束。 */ INVALID_TIME_RANGE("INVALID_TIME_RANGE", "时间范围不合法", 400),
    /** SSE 事件游标已过期，客户端必须回源查询。 */ EVENT_CURSOR_EXPIRED("EVENT_CURSOR_EXPIRED", "事件游标已过期，请回源查询", 410),
    /** 指定的统计范围不被支持。 */ RANGE_NOT_SUPPORTED("RANGE_NOT_SUPPORTED", "不支持该统计范围", 400),
    /** 原交易不支持受控退款。 */ REFUND_NOT_ALLOWED("REFUND_NOT_ALLOWED", "当前交易不支持受控退款", 422),
    /** 原交易已存在退款订单。 */ REFUND_ALREADY_EXISTS("REFUND_ALREADY_EXISTS", "该交易已经存在退款订单", 409),
    /** 分页游标无效或已过期。 */ INVALID_CURSOR("INVALID_CURSOR", "分页游标无效", 400);

    private final String code; private final String message; private final int httpStatus;
    BusinessErrorCode(String code, String message, int httpStatus) {
        this.code = code; this.message = message; this.httpStatus = httpStatus;
    }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int httpStatus() { return httpStatus; }
}
