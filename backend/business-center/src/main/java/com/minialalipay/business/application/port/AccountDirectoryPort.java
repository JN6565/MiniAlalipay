package com.minialalipay.business.application.port;

/** 跨服务账户目录端口，只读取业务执行所需的账户引用。 */
public interface AccountDirectoryPort {
    /** 按用户解析服务端权威个人账户。 */
    AccountReference resolvePersonalAccount(String userId);

    /** 账户中心返回的版本化只读引用。 */
    record AccountReference(String accountId, String userId, String status) { }
}
