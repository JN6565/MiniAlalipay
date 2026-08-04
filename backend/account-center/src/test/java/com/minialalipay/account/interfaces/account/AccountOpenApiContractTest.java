package com.minialalipay.account.interfaces.account;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountOpenApiContractTest {

    @Test
    void accountReadOperationsHaveCompleteSchemas() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) root.get("paths");
            assertOperation(paths, "/api/v1/accounts/me", "get", "getMyAccount");
            assertOperation(paths, "/api/v1/accounts/me/entries", "get", "listMyEntries");
        }
    }

    private void assertOperation(Map<String, Map<String, Object>> paths, String path, String method,
                                 String operationId) {
        assertThat(paths).containsKey(path);
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = (Map<String, Object>) paths.get(path).get(method);
        assertThat(operation.get("operationId")).isEqualTo(operationId);
        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        assertThat(responses).containsKeys("200", "401", "404", "500");
    }
}
