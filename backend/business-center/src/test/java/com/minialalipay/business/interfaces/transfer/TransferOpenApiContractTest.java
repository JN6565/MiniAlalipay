package com.minialalipay.business.interfaces.transfer;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransferOpenApiContractTest {
    @Test
    void openApi包含阶段四普通转账全部端点和严格请求Schema() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Object> paths = (Map<String, Object>) root.get("paths");
            assertThat(paths).containsKeys("/api/v1/transfer-drafts", "/api/v1/transfer-drafts/{id}",
                    "/api/v1/transfer-drafts/{id}/validate", "/api/v1/confirmations",
                    "/api/v1/transfers", "/api/v1/transfers/{id}", "/api/v1/transfers/{id}/receipt");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            @SuppressWarnings("unchecked")
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            @SuppressWarnings("unchecked")
            Map<String, Object> submitSchema = (Map<String, Object>) schemas.get("SubmitTransferRequest");
            assertThat(submitSchema)
                    .containsEntry("additionalProperties", false);
            @SuppressWarnings("unchecked")
            Map<String, Object> transactionSchema = (Map<String, Object>) schemas.get("TransferTransaction");
            @SuppressWarnings("unchecked")
            java.util.List<String> required = (java.util.List<String>) transactionSchema.get("required");
            assertThat(required).contains("payerUserId", "payerDisplayName", "payeeUserId",
                    "payeeDisplayName", "remark", "createdAt");
        }
    }

    @Test
    void openApi包含阶段五场景与运营端点且资金受理要求幂等键() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Object> paths = (Map<String, Object>) root.get("paths");
            assertThat(paths).containsKeys(
                    "/api/v1/recharges", "/api/v1/qr-pay/orders", "/api/v1/qr-pay/token-exchanges",
                    "/api/v1/p2p-collections/codes/me", "/api/v1/p2p-collections/requests",
                    "/api/v1/manual-cases", "/api/v1/ops/realtime-metrics", "/api/v1/ops/alerts"
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> recharge = (Map<String, Object>) paths.get("/api/v1/recharges");
            @SuppressWarnings("unchecked")
            Map<String, Object> post = (Map<String, Object>) recharge.get("post");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> parameters = (java.util.List<Map<String, Object>>) post.get("parameters");
            assertThat(parameters).anySatisfy(parameter -> assertThat(parameter.get("$ref"))
                    .isEqualTo("#/components/parameters/IdempotencyKeyHeader"));
            assertThat(parameters).anySatisfy(parameter -> assertThat(parameter.get("$ref"))
                    .isEqualTo("#/components/parameters/RequestIdHeader"));
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            @SuppressWarnings("unchecked")
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            @SuppressWarnings("unchecked")
            Map<String, Object> rechargeRequest = (Map<String, Object>) schemas.get("CreateRechargeRequest");
            @SuppressWarnings("unchecked")
            Map<String, Object> rechargeProperties = (Map<String, Object>) rechargeRequest.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> rechargeAmount = (Map<String, Object>) rechargeProperties.get("amountFen");
            assertThat(rechargeAmount).containsEntry("maximum", 5000000);
            assertThat(post.get("description").toString()).contains("25000000").contains("5 次");
            @SuppressWarnings("unchecked")
            Map<String, Object> qrRequest = (Map<String, Object>) schemas.get("CreateQrPayOrderRequest");
            assertThat(qrRequest).containsEntry("additionalProperties", false);
        }
    }
}
