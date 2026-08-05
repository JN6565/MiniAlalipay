package com.minialalipay.user.application.auth;

import com.minialalipay.common.error.BusinessException;

/**
 * 用户注册开户跨服务端口。
 *
 * <p>应用层只依赖此抽象，不感知 HTTP 客户端。实现方必须把相同 registrationId 的重试发送为
 * 同一开户请求；生产调用需要携带用户中心服务身份。调用成功返回账户 ID，网络或业务失败抛出异常，
 * 由注册编排保持用户为 PROVISIONING 并等待恢复。</p>
 */
public interface AccountProvisioningPort {

    /**
     * 为已经持久化的用户幂等创建账户体系。
     *
     * @param userId 用户中心持久化的 26 位用户 ID
     * @param registrationId 用户中心持久化的 26 位注册幂等编号
     * @return 账户中心创建或返回的账户 ID
     * @throws BusinessException 账户中心拒绝或调用失败时抛出
     */
    String openAccount(String userId, String registrationId);
}
