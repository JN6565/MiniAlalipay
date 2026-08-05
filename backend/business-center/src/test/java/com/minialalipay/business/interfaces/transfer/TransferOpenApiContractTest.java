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
        }
    }
}
