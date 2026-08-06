package com.minialalipay.business.application.port;

/**
 * 业务 ID、短期原始令牌和摘要生成端口。
 *
 * <p>应用层只通过本端口取得原始令牌，并在持久化边界立即转换为摘要；端口实现不得记录原始令牌，
 * 从而避免二维码、个人收款码和确认令牌进入日志或业务表。</p>
 */
public interface SecurityMaterialPort {
    /** 生成不可预测的业务资源标识。 */
    String newId();

    /** 生成链路追踪标识。 */
    String newTraceId();

    /** 生成一次性确认流程使用的短期原始令牌。 */
    String newConfirmationToken();

    /** 生成动态扫码收款订单使用的短期原始二维码令牌。 */
    String newQrToken();

    /** 生成长期个人收款码或固定请求使用的原始收款令牌。 */
    String newCollectionToken();

    /** 计算仅可持久化的敏感值摘要。 */
    byte[] digest(String value);

    /** 从稳定业务键派生可重试复用的资源标识。 */
    String stableId(String value);

    /** 从稳定业务键派生正数技术主键。 */
    long stablePositiveLong(String value);
}
