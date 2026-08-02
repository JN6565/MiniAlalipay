package com.minialalipay.account.domain.account;

/**
 * 虚拟账户类型，与{@code account.account_type}持久化取值保持一致。
 *
 * <p>该枚举用于表达领域语义，不得据此推导用户角色或B端权限。物理表
 * {@code account.account_type} 的兼容取值以数据库设计为准。</p>
 */
public enum AccountType {
    /** 普通用户的唯一虚拟余额账户，也是当前MVP唯一允许新建的账户类型。 */
    PERSONAL,

    /**
     * 历史兼容的商户账户标识。当前MVP不创建该类型，也不得据此授予商户角色或B端权限。
     */
    MERCHANT
}
