package com.minialalipay.user.domain.auth;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用户中心错误码与统一 YAML 契约的一致性测试。 */
class UserErrorCodeContractTest {

    /** 逐项校验错误码、中文消息和 HTTP 状态。 */
    @Test
    void userErrorCodesMatchYamlContract() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "error-codes", "error-codes.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> codes = (Map<String, Map<String, Object>>) root.get("codes");
            for (UserErrorCode errorCode : UserErrorCode.values()) {
                Map<String, Object> expected = codes.get(errorCode.code());
                assertThat(expected).as("错误码 %s 已登记", errorCode.code()).isNotNull();
                assertThat(expected.get("message")).isEqualTo(errorCode.message());
                assertThat(expected.get("httpStatus")).isEqualTo(errorCode.httpStatus());
            }
        }
    }
}
