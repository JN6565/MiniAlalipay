package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.CreditAccountDirectoryPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 通过版本化内部 HTTP 契约读取账户中心信用账户引用。 */
@Component
public class CreditAccountDirectoryHttpAdapter implements CreditAccountDirectoryPort {
    private final RestClient client;

    /** 创建信用账户内部目录 HTTP 适配器。 */
    public CreditAccountDirectoryHttpAdapter(@LoadBalanced RestClient.Builder builder,
                                              @Value("${minialalipay.internal.account-center-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    /**
     * 读取信用账户 ID、状态和版本。
     *
     * <p>账户中心不可用或返回空引用时只返回业务层账户不可用错误，绝不以本地缓存额度或账户资料代替权威结果。</p>
     */
    @Override
    public CreditAccountReference resolveCreditAccount(String userId) {
        try {
            CreditAccountReference result = client.get().uri("/internal/v1/credit-accounts/by-user/{id}", userId)
                    .retrieve().body(CreditAccountReference.class);
            if (result == null || !userId.equals(result.userId())) {
                throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        }
    }
}
