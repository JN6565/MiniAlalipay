package com.minialalipay.user.infrastructure.client;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.auth.UserErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * 账户中心 HTTP 客户端。
 *
 * <p>负责调用账户中心的开户接口，完成用户注册时的账户创建。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责调用账户中心的开户接口</li>
 *   <li>不负责业务逻辑（由应用服务负责）</li>
 *   <li>处理网络异常和业务异常</li>
 * </ul>
 * </p>
 */
@Component
public class AccountCenterClient {

    private static final Logger log = LoggerFactory.getLogger(AccountCenterClient.class);

    private final RestTemplate restTemplate;
    private final String accountCenterUrl;

    public AccountCenterClient(
            RestTemplate restTemplate,
            @Value("${account-center.url:http://localhost:8083}") String accountCenterUrl
    ) {
        this.restTemplate = restTemplate;
        this.accountCenterUrl = accountCenterUrl;
    }

    /**
     * 调用账户中心开户接口。
     *
     * @param userId         用户 ID
     * @param registrationId 注册幂等键
     * @return 账户 ID
     * @throws BusinessException 如果开户失败
     */
    public String openAccount(String userId, String registrationId) {
        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-Id", UUID.randomUUID().toString());

            Map<String, String> requestBody = Map.of(
                    "userId", userId,
                    "registrationId", registrationId
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // 调用账户中心
            String url = accountCenterUrl + "/api/v1/accounts/open";
            log.info("调用账户中心开户接口: userId={}, registrationId={}", userId, registrationId);

            ResponseEntity<AccountOpenResponse> response = restTemplate.postForEntity(
                    url, request, AccountOpenResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AccountOpenResponse body = response.getBody();
                if ("OK".equals(body.code())) {
                    log.info("账户中心开户成功: accountId={}", body.data().accountId());
                    return body.data().accountId();
                } else {
                    log.error("账户中心开户失败: code={}, message={}", body.code(), body.message());
                    throw new BusinessException(UserErrorCode.REGISTRATION_PROCESSING);
                }
            } else {
                log.error("账户中心开户请求失败: status={}", response.getStatusCode());
                throw new BusinessException(UserErrorCode.REGISTRATION_PROCESSING);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用账户中心开户接口异常", e);
            throw new BusinessException(UserErrorCode.REGISTRATION_PROCESSING);
        }
    }

    /**
     * 账户中心开户响应。
     */
    public record AccountOpenResponse(
            String code,
            String message,
            AccountData data
    ) {
    }

    /**
     * 账户数据。
     */
    public record AccountData(
            String accountId,
            String accountType,
            String currency,
            String status,
            long availableFen,
            long frozenFen,
            long totalFen,
            long version
    ) {
    }
}
