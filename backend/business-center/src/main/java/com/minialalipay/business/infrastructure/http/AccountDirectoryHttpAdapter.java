package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** 通过版本化内部 HTTP 契约读取账户中心个人账户引用。 */
@Component
public class AccountDirectoryHttpAdapter implements AccountDirectoryPort {
    private final RestClient client;
    public AccountDirectoryHttpAdapter(RestClient.Builder builder,
                                       @Value("${minialalipay.internal.account-center-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }
    @Override public AccountReference resolvePersonalAccount(String userId) {
        try {

            AccountReference result = client.get().uri("/internal/v1/accounts/by-user/{id}", userId)
                    .retrieve().body(AccountReference.class);
            if (result == null) throw new BusinessException(BusinessErrorCode.PAYEE_NOT_FOUND);
            return result;
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new BusinessException(BusinessErrorCode.PAYEE_NOT_FOUND);
        }
    }
}
