package com.minialalipay.common.error;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonExceptionMapperTest {

    private final CommonExceptionMapper mapper = new CommonExceptionMapper();

    @Test
    void mapsBusinessExceptionWithoutExposingInternalDetails() {
        MappedError mapped = mapper.map(
                new BusinessException(CommonErrorCode.FORBIDDEN),
                "req-001",
                "trace-001"
        );

        assertThat(mapped.httpStatus()).isEqualTo(403);
        assertThat(mapped.body().code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(mapped.body().message()).isEqualTo("无权访问该资源");
        assertThat(mapped.body().requestId()).isEqualTo("req-001");
        assertThat(mapped.body().traceId()).isEqualTo("trace-001");
        assertThat(mapped.body().data()).isNull();
    }

    @Test
    void hidesUnexpectedExceptionMessage() {
        MappedError mapped = mapper.map(
                new IllegalStateException("数据库密码不应泄露"),
                "req-002",
                "trace-002"
        );

        assertThat(mapped.httpStatus()).isEqualTo(500);
        assertThat(mapped.body().code()).isEqualTo("COMMON_INTERNAL_ERROR");
        assertThat(mapped.body().message()).isEqualTo("系统内部错误");
    }

    @Test
    void preservesOnlyExplicitSafeBusinessDetails() {
        BusinessException exception = new BusinessException(
                CommonErrorCode.INVALID_REQUEST,
                Map.of("currentVersion", 3L)
        );

        MappedError mapped = mapper.map(exception, "req-003", "trace-003");

        assertThat(mapped.body().data()).isEqualTo(Map.of("currentVersion", 3L));
        assertThatThrownBy(() -> exception.safeDetails().put("internal", "禁止泄露"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
