package com.minialalipay.account.domain.bankcard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 银行卡错误码契约测试：逐项校验服务枚举与 error-codes.yaml 完全一致，
 * 任一项不一致时禁止合并。
 */
class BankCardErrorCodeContractTest {

    @Test
    void bankCardErrorCodesMatchYamlContract() throws IOException {
        Path contract = Path.of("..", "..", "contracts", "error-codes", "error-codes.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> codes = (Map<String, Map<String, Object>>) root.get("codes");
            for (BankCardErrorCode errorCode : BankCardErrorCode.values()) {
                Map<String, Object> expected = codes.get(errorCode.code());
                assertThat(expected).as("错误码 %s 已登记", errorCode.code()).isNotNull();
                assertThat(expected.get("message")).isEqualTo(errorCode.message());
                assertThat(expected.get("httpStatus")).isEqualTo(errorCode.httpStatus());
            }
        }
    }
}
