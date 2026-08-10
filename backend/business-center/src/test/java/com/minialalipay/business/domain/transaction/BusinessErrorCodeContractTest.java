package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessErrorCodeContractTest {
    private static final Set<String> BUSINESS_ERROR_CODES = Set.of(
            "PAYEE_NOT_FOUND", "SELF_PAYMENT_FORBIDDEN", "ACCOUNT_UNAVAILABLE", "RECHARGE_LIMIT_EXCEEDED",
            "AMOUNT_OUT_OF_RANGE", "DRAFT_NOT_FOUND", "DRAFT_NOT_EDITABLE", "VERSION_CONFLICT",
            "RISK_REJECTED", "RISK_MANUAL_REVIEW", "PAYMENT_PROOF_INVALID", "CONFIRMATION_EXPIRED",
            "CONFIRMATION_MISMATCH", "CONFIRMATION_STALE", "IDEMPOTENCY_CONFLICT", "TRANSACTION_NOT_FOUND",
            "TRANSACTION_PROCESSING", "TRANSACTION_PENDING", "PAY_PASSWORD_INVALID", "PAYMENT_LOCKED", "INVALID_CURSOR",
            "RECEIPT_NOT_READY", "ORDER_NOT_FOUND", "ORDER_EXPIRED", "ORDER_NOT_EDITABLE",
            "ORDER_NOT_CANCELLABLE", "ORDER_STATE_INVALID", "ORDER_ALREADY_CLAIMED", "QR_TOKEN_INVALID",
            "QR_TOKEN_CONSUMED", "P2P_CODE_INVALID", "COLLECTION_TOKEN_INVALID", "COLLECTION_REQUEST_EXPIRED",
            "COLLECTION_REQUEST_CANCELLED", "COLLECTION_REQUEST_PROCESSING", "COLLECTION_REQUEST_PAID",
            "REQUEST_NOT_FOUND", "REQUEST_NOT_CANCELLABLE", "AMOUNT_IMMUTABLE", "FUNDING_SOURCE_NOT_ALLOWED",
            "CASE_STATE_INVALID", "OPS_PERMISSION_REQUIRED", "ALERT_STATE_INVALID", "ALERT_NOT_FOUND", "EVIDENCE_REQUIRED",
            "JOB_ALREADY_RUNNING", "REPORT_NOT_PUBLISHED", "INVALID_TIME_RANGE", "EVENT_CURSOR_EXPIRED", "RANGE_NOT_SUPPORTED",
            "REFUND_NOT_ALLOWED", "REFUND_ALREADY_EXISTS"
    );

    @Test
    void 业务错误码与统一契约双向完全匹配() throws Exception {
        Path contract = Path.of("..", "..", "contracts", "error-codes", "error-codes.yaml").normalize();
        try (InputStream input = Files.newInputStream(contract)) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Map<String, Object>> codes = (Map<String, Map<String, Object>>) root.get("codes");
            Set<String> enumCodes = java.util.Arrays.stream(BusinessErrorCode.values())
                    .map(BusinessErrorCode::code).collect(Collectors.toUnmodifiableSet());
            assertThat(enumCodes).containsExactlyInAnyOrderElementsOf(BUSINESS_ERROR_CODES);
            assertThat(codes.keySet()).containsAll(BUSINESS_ERROR_CODES);
            for (BusinessErrorCode code : BusinessErrorCode.values()) {
                assertThat(codes.get(code.code())).isNotNull()
                        .containsEntry("message", code.message()).containsEntry("httpStatus", code.httpStatus());
            }
        }
    }
}
