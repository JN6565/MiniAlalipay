package com.minialalipay.account.interfaces.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 阶段五信用公开接口与 OpenAPI 契约完整性测试。 */
class CreditOpenApiContractTest {

    @Test
    void shouldDeclareAllStageFiveCreditOperations() throws IOException {
        String yaml = Files.readString(Path.of("..", "..", "contracts", "openapi", "minialalipay-api.yaml"));

        assertThat(yaml).contains(
                "/api/v1/credit/me:", "operationId: getMyCredit",
                "/api/v1/credit/purchases:", "operationId: listCreditPurchases",
                "/api/v1/credit/bills:", "operationId: listCreditBills",
                "/api/v1/credit/bills/{id}:", "operationId: getCreditBill",
                "/api/v1/credit/repayment-drafts:", "operationId: createCreditRepaymentDraft",
                "/api/v1/credit/repayments:", "operationId: submitCreditRepayment",
                "/api/v1/credit/repayments/{id}:", "operationId: getCreditRepayment",
                "/api/v1/ops/credit/statement-runs:", "operationId: runCreditStatement",
                "/api/v1/ops/credit/due-check-runs:", "operationId: runCreditDueCheck");
    }
}
