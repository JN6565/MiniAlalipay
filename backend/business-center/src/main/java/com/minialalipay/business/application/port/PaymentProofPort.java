package com.minialalipay.business.application.port;

/** 用户中心支付密码证明校验端口，业务中心不接收或校验原始支付密码。 */
public interface PaymentProofPort {
    /** 校验一次性证明的主体、用途与有效期，并返回当前支付密码版本。 */
    VerifiedProof verify(String userId, String paymentProof, String purpose);
    /** 查询用户当前支付密码版本，确认消费时用于撤销改密前令牌。 */
    long currentPayPasswordVersion(String userId);
    /** 已验证支付密码证明的逻辑引用。 */
    record VerifiedProof(String paymentProofId, long payPasswordVersion) { }
}
