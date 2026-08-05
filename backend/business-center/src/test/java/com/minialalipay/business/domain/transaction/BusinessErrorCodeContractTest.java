package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessErrorCodeContractTest {
    @Test
    void 业务错误码逐项匹配统一契约() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "error-codes", "error-codes.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Map<String, Object>> codes = (Map<String, Map<String, Object>>) root.get("codes");
            for (BusinessErrorCode code : BusinessErrorCode.values()) {
                assertThat(codes.get(code.code())).isNotNull()
                        .containsEntry("message", code.message()).containsEntry("httpStatus", code.httpStatus());
            }
        }
    }
}
