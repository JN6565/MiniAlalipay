package com.minialalipay.business.domain.monitoring;

/**
 * 运营告警生命周期状态。
 *
 * <p>告警必须先确认再恢复，恢复后可以因新事件重开，关闭后为终态。</p>
 */
public enum AlertStatus {
    /** 新触发且尚未领取的告警。 */
    OPEN,
    /** 已由运营人员确认并开始处置。 */
    ACKNOWLEDGED,
    /** 指标已恢复，等待观察或关闭。 */
    RESOLVED,
    /** 观察完成后的终态。 */
    CLOSED
}
