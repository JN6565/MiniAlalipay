package com.minialalipay.business.application.port;

/**
 * 跨服务信用账户目录端口。
 *
 * <p>只读取确认摘要和信用 TCC 所需的版本化账户引用，禁止返回或缓存可用额度、已用额度、冻结额度及任何账务事实。</p>
 */
public interface CreditAccountDirectoryPort {
    /** 按用户解析账户中心权威信用账户引用。 */
    CreditAccountReference resolveCreditAccount(String userId);

    /** 账户中心返回的信用账户版本化只读引用。 */
    record CreditAccountReference(String creditAccountId, String userId, String status, long version) { }
}
