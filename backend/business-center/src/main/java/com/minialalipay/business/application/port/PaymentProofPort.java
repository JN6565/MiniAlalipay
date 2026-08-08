package com.minialalipay.business.application.port;

/**
 * 用户中心支付密码证明端口。
 *
 * <p>业务中心不校验、不记录、不持久化原始支付密码和原始证明；合并提交端点仅将原始
 * 支付密码透传给用户中心完成验密与证明签发。</p>
 */
public interface PaymentProofPort {
    /** 校验一次性证明的主体、用途与有效期，并返回当前支付密码版本。 */
    VerifiedProof verify(String userId, String paymentProof, String purpose);
    /**
     * 透传原始支付密码由用户中心验密并签发一次性证明，返回原始证明令牌。
     *
     * <p>仅供转账合并提交端点使用；密码错误抛 PAY_PASSWORD_INVALID，被锁定抛 PAYMENT_LOCKED。</p>
     */
    String verifyAndIssueProof(String userId, String paymentPassword, String purpose);
    /** 查询用户当前支付密码版本，确认消费时用于撤销改密前令牌。 */
    long currentPayPasswordVersion(String userId);
    /** 已验证支付密码证明的逻辑引用。 */
    record VerifiedProof(String paymentProofId, long payPasswordVersion) { }
}
