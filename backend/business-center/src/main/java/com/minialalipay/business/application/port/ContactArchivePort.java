package com.minialalipay.business.application.port;

/**
 * 联系人归档端口。
 *
 * <p>转账成功后异步通知用户中心归档收款人到付款人的常用联系人列表。
 * 归档失败不应影响转账主流程，仅记录告警日志。</p>
 */
public interface ContactArchivePort {

    /**
     * 归档收款人。
     *
     * @param ownerUserId 付款人用户 ID（联系人列表所有者）
     * @param payeeUserId 收款人用户 ID
     */
    void archivePayee(String ownerUserId, String payeeUserId);
}
