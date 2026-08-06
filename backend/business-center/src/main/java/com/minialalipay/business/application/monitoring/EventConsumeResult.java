package com.minialalipay.business.application.monitoring;

/** 运营事件消费结果，供消息监听器决定确认、重试或隔离。 */
public enum EventConsumeResult {
    /** 已完成投影，可以确认消息。 */
    PROJECTED,
    /** 事件此前已成功消费，重复投递可以确认。 */
    DUPLICATE,
    /** 事件版本或字段不符合契约，已经隔离，不能静默丢弃。 */
    QUARANTINED,
    /** 投影暂时失败，消息应按退避策略重试。 */
    RETRYABLE_FAILURE
}
