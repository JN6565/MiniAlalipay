package com.minialalipay.account.application.credit;

/**
 * 支付密码证明校验端口。
 *
 * <p>账户中心只传递一次性原始证明给用户中心，不保存、不记录证明内容；校验成功后证明即被消费。</p>
 */
public interface PaymentProofPort {

    /**
     * 校验并消费支付证明。
     *
     * @param userId 证明所属用户
     * @param paymentProof 一次性原始支付证明
     * @param purpose 证明用途
     * @return 已消费证明的安全逻辑引用
     */
    VerifiedProof verify(String userId, String paymentProof, String purpose);

    /** 已验证证明的非敏感结果。 */
    record VerifiedProof(String paymentProofId, long payPasswordVersion) { }
}
