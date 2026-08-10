package com.minialalipay.business.infrastructure.http;

import com.minialalipay.business.application.port.ContactArchivePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 联系人归档 HTTP 适配器。
 *
 * <p>调用用户中心内部归档接口 {@code POST /internal/v1/contacts/archive}。
 * 归档失败仅记录告警日志，不影响转账主流程。</p>
 */
@Component
public class ContactArchiveHttpAdapter implements ContactArchivePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactArchiveHttpAdapter.class);

    private final RestClient client;
    private final String serviceToken;

    public ContactArchiveHttpAdapter(
            @LoadBalanced RestClient.Builder builder,
            @Value("${minialalipay.internal.user-center-url}") String baseUrl,
            @Value("${minialalipay.internal.service-token:}") String serviceToken
    ) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }

    @Async
    @Override
    public void archivePayee(String ownerUserId, String payeeUserId) {
        try {
            client.post()
                    .uri("/internal/v1/contacts/archive")
                    .header("X-Internal-Service-Token", serviceToken)
                    .body(new ArchiveRequest(ownerUserId, payeeUserId))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("联系人归档成功：owner={}, payee={}", ownerUserId, payeeUserId);
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                    "联系人归档失败（不影响转账）：owner={}, payee={}, status={}",
                    ownerUserId, payeeUserId, exception.getStatusCode()
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "联系人归档异常（不影响转账）：owner={}, payee={}, cause={}",
                    ownerUserId, payeeUserId, exception.getMessage()
            );
        }
    }

    private record ArchiveRequest(String ownerUserId, String payeeUserId) {
    }
}
