package com.minialalipay.business.application.port;

/** 跨服务账户目录端口，只读取业务执行所需的账户引用。 */
public interface AccountDirectoryPort {
    /** 按用户解析服务端权威个人账户。 */
    AccountReference resolvePersonalAccount(String userId);

    /**
     * 校验当前用户可否用指定金额进行动态扫码信用支付。
     *
     * <p>测试替身可沿用默认放行；生产 HTTP 适配器必须调用账户中心权威预检接口。</p>
     */
    default void requireCreditPaymentEligible(String userId, long amountFen) { }

    /** 账户中心返回的版本化只读引用。 */
    record AccountReference(String accountId, String userId, String status) { }
}
