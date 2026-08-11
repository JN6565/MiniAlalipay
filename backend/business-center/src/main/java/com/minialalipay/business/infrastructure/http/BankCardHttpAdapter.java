package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.BankCardPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 调用 account-center 内部银行卡查询端点，返回银行卡只读引用。
 *
 * <p>account-center 不存在该卡或不属于该用户时返回 4xx，适配器转换为通用 NOT_FOUND 错误。</p>
 */
@Component
public class BankCardHttpAdapter implements BankCardPort {
    private final RestClient client;

    public BankCardHttpAdapter(@LoadBalanced RestClient.Builder builder,
                               @Value("${minialalipay.internal.account-center-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BankCardReference requireCard(String userId, String cardId) {
        try {
            CardResponse response = client.get()
                    .uri("/internal/v1/bank-cards/{cardId}?userId={userId}", cardId, userId)
                    .retrieve().body(CardResponse.class);
            if (response == null) {
                throw new BusinessException(BusinessErrorCode.BANK_CARD_NOT_FOUND);
            }
            return new BankCardReference(response.cardId(), response.userId(), response.bankName(),
                    response.cardLast4(), response.balanceFen(), response.status());
        } catch (RestClientResponseException rejected) {
            throw new BusinessException(BusinessErrorCode.BANK_CARD_NOT_FOUND);
        } catch (ResourceAccessException unreachable) {
            // 账户中心不可达属于下游服务故障，不得裸抛为 500 内部错误
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public long getBalanceFen(String userId, String cardId) {
        return requireCard(userId, cardId).balanceFen();
    }

    private record CardResponse(String cardId, String bankName, String cardLast4,
                                long balanceFen, String status, String userId) { }
}
