package com.minialalipay.business.interfaces.scenario;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段五场景接口契约门禁。
 *
 * <p>本测试只解析事实来源 OpenAPI，防止 Controller 在令牌保护、幂等、对象权限和 SSE 断线续传约束缺失时提前暴露路径。</p>
 */
class StageFiveOpenApiContractTest {
    @Test
    void 阶段五公开路径与状态流订阅均已定义() throws Exception {
        Map<String, Object> paths = contractPaths();

        assertThat(paths).containsKeys(
                "/api/v1/recharges", "/api/v1/recharges/{id}",
                "/api/v1/qr-pay/orders", "/api/v1/qr-pay/orders/by-token", "/api/v1/qr-pay/token-exchanges",
                "/api/v1/qr-pay/orders/{id}", "/api/v1/qr-pay/orders/{id}/scan",
                "/api/v1/qr-pay/orders/{id}/confirmations", "/api/v1/qr-pay/orders/{id}/pay",
                "/api/v1/qr-pay/orders/{id}/events",
                "/api/v1/p2p-collections/codes/me", "/api/v1/p2p-collections/codes/me/regenerations",
                "/api/v1/p2p-collections/codes/me/disable", "/api/v1/p2p-collections/requests",
                "/api/v1/p2p-collections/requests/{id}", "/api/v1/p2p-collections/requests/{id}/cancel",
                "/api/v1/p2p-collections/by-token", "/api/v1/p2p-collections/token-exchanges",
                "/api/v1/p2p-collections/orders/{id}", "/api/v1/p2p-collections/orders/{id}/confirmations",
                "/api/v1/p2p-collections/orders/{id}/pay", "/api/v1/p2p-collections/requests/{id}/events",
                "/api/v1/manual-cases", "/api/v1/manual-cases/{id}/decisions",
                "/api/v1/ops/realtime-metrics", "/api/v1/ops/daily-reports", "/api/v1/ops/alerts",
                "/api/v1/ops/alerts/{id}/acknowledge", "/api/v1/ops/alerts/{id}/resolve",
                "/api/v1/ops/alerts/{id}/close", "/api/v1/ops/data-quality", "/api/v1/ops/metric-definitions",
                "/api/v1/ops/transactions", "/api/v1/ops/transactions/{id}", "/api/v1/ops/transactions/{id}/trace",
                "/api/v1/ops/alert-rules", "/api/v1/ops/alert-rules/{ruleCode}/thresholds"
        );
        assertEventStream(paths, "/api/v1/qr-pay/orders/{id}/events");
        assertEventStream(paths, "/api/v1/p2p-collections/requests/{id}/events");
    }

    @Test
    void 高风险写接口要求请求编号幂等键与严格请求对象() throws Exception {
        Map<String, Object> paths = contractPaths();
        assertHeaders(paths, "/api/v1/recharges", "post", true);
        assertHeaders(paths, "/api/v1/qr-pay/orders", "post", true);
        assertHeaders(paths, "/api/v1/qr-pay/orders/{id}/pay", "post", true);
        assertHeaders(paths, "/api/v1/p2p-collections/codes/me/regenerations", "post", true);
        assertHeaders(paths, "/api/v1/p2p-collections/orders/{id}/pay", "post", true);
        assertHeaders(paths, "/api/v1/manual-cases/{id}/decisions", "post", true);
        assertHeaders(paths, "/api/v1/ops/alerts/{id}/acknowledge", "post", true);
        assertHeaders(paths, "/api/v1/ops/alert-rules/{ruleCode}/thresholds", "post", true);

        Map<String, Object> schemas = contractSchemas();
        for (String name : List.of("CreateRechargeRequest", "CreateQrPayOrderRequest", "TokenExchangeRequest",
                "IssueScenarioConfirmationRequest", "PayScenarioOrderRequest", "CreateCollectionRequest",
                "LockPersonalCollectionOrderRequest", "IssueCollectionConfirmationRequest", "PayCollectionOrderRequest",
                "DecideManualCaseRequest", "AcknowledgeAlertRequest", "ResolveAlertRequest", "CloseAlertRequest")) {
            assertThat(schema(schemas, name)).containsEntry("additionalProperties", false);
        }
        assertAmountFen(schema(schemas, "CreateQrPayOrderRequest"));
        assertAmountFen(schema(schemas, "CreateCollectionRequest"));
        assertAmountFen(schema(schemas, "LockPersonalCollectionOrderRequest"));
    }

    @Test
    void 敏感令牌不在读取模型且未知资金结果明确为非成功() throws Exception {
        Map<String, Object> schemas = contractSchemas();
        assertThat(properties(schema(schemas, "QrPayOrder"))).doesNotContainKeys("payerAccountId", "payeeAccountId", "rawToken");
        assertThat(properties(schema(schemas, "CollectionOrder"))).doesNotContainKeys("payerAccountId", "payeeAccountId", "payeeUserId");
        assertThat(map(properties(schema(schemas, "PayScenarioOrderRequest")).get("confirmationToken"))).containsEntry("writeOnly", true);
        assertThat(map(properties(schema(schemas, "PayCollectionOrderRequest")).get("confirmationToken"))).containsEntry("writeOnly", true);
        assertThat(schema(schemas, "ScenarioPayment").toString()).contains("UNKNOWN").contains("transactionId");

        Map<String, Object> pay = operation(contractPaths(), "/api/v1/qr-pay/orders/{id}/pay", "post");
        Map<String, Object> responses = map(pay.get("responses"));
        assertThat(map(responses.get("503")).get("$ref")).isEqualTo("#/components/responses/ScenarioOutcomeUnknown");
    }

    private static void assertEventStream(Map<String, Object> paths, String path) {
        Map<String, Object> operation = operation(paths, path, "get");
        assertThat(parameterReferences(operation)).contains("#/components/parameters/LastEventIdHeader");
        Map<String, Object> response = map(map(operation.get("responses")).get("200"));
        assertThat(map(response.get("content"))).containsKey("text/event-stream");
    }

    private static void assertHeaders(Map<String, Object> paths, String path, String method, boolean idempotency) {
        List<String> refs = parameterReferences(operation(paths, path, method));
        assertThat(refs).contains("#/components/parameters/RequestIdHeader");
        if (idempotency) {
            assertThat(refs).contains("#/components/parameters/IdempotencyKeyHeader");
        }
    }

    private static void assertAmountFen(Map<String, Object> schema) {
        Map<String, Object> amount = map(properties(schema).get("amountFen"));
        assertThat(amount).containsEntry("type", "integer").containsEntry("format", "int64")
                .containsEntry("minimum", 1).containsEntry("maximum", 5000000);
    }

    private static Map<String, Object> contractPaths() throws Exception {
        return map(contract().get("paths"));
    }

    private static Map<String, Object> contractSchemas() throws Exception {
        return map(map(contract().get("components")).get("schemas"));
    }

    private static Map<String, Object> contract() throws Exception {
        Path file = Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml").normalize();
        try (InputStream input = Files.newInputStream(file)) {
            return map(new Yaml().load(input));
        }
    }

    private static Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return map(map(paths.get(path)).get(method));
    }

    private static Map<String, Object> schema(Map<String, Object> schemas, String name) {
        return map(schemas.get(name));
    }

    private static Map<String, Object> properties(Map<String, Object> schema) {
        return map(schema.get("properties"));
    }

    private static List<String> parameterReferences(Map<String, Object> operation) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.getOrDefault("parameters", List.of());
        return parameters.stream().map(parameter -> (String) parameter.get("$ref")).filter(java.util.Objects::nonNull).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
