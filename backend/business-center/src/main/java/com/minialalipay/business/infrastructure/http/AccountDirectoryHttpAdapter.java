package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** 通过版本化内部 HTTP 契约读取账户中心个人账户引用。 */
@Component
public class AccountDirectoryHttpAdapter implements AccountDirectoryPort {
    private final RestClient client;
    public AccountDirectoryHttpAdapter(@LoadBalanced RestClient.Builder builder,
                                       @Value("${minialalipay.internal.account-center-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    /**
     * 解析用户个人账户引用。
     *
     * <p>账户中心返回任意 4xx（含 400 参数校验失败、404 未开户）都意味着无法解析出
     * 权威账户，统一映射为收款用户不存在；连接失败或 5xx 属于下游不可用，
     * 映射为服务暂不可用。绝不能把下游错误裸抛成 500 内部错误误导排查。</p>
     */
    @Override public AccountReference resolvePersonalAccount(String userId) {
        try {

            AccountReference result = client.get().uri("/internal/v1/accounts/by-user/{id}", userId)
                    .retrieve().body(AccountReference.class);
            if (result == null) throw new BusinessException(BusinessErrorCode.PAYEE_NOT_FOUND);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpClientErrorException clientError) {
            throw new BusinessException(BusinessErrorCode.PAYEE_NOT_FOUND);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** 通过账户中心只读预检接口校验信用状态与可用额度，不在业务中心复制额度事实。 */
    @Override
    public void requireCreditPaymentEligible(String userId, long amountFen) {
        try {
            CreditAccountReference credit = client.get()
                    .uri("/internal/v1/credit-accounts/by-user/{id}", userId)
                    .retrieve().body(CreditAccountReference.class);
            if (credit == null) throw new BusinessException(BusinessErrorCode.FUNDING_SOURCE_NOT_ALLOWED);
            CreditEligibility result = client.post()
                    .uri("/internal/v1/credit-accounts/{id}/eligibility", credit.creditAccountId())
                    .body(new CreditEligibilityRequest(amountFen))
                    .retrieve().body(CreditEligibility.class);
            if (result == null) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            if (!result.eligible()) throw new BusinessException(BusinessErrorCode.FUNDING_SOURCE_NOT_ALLOWED);
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new BusinessException(BusinessErrorCode.FUNDING_SOURCE_NOT_ALLOWED);
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** 账户中心信用账户只读引用。 */
    private record CreditAccountReference(String creditAccountId, String userId, boolean opened, String status, long version) { }

    /** 信用支付资格预检请求。 */
    private record CreditEligibilityRequest(long amountFen) { }

    /** 信用支付资格预检响应。 */
    private record CreditEligibility(boolean eligible, String reasonCode, long version) { }
}
