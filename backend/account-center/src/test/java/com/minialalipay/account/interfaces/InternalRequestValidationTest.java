package com.minialalipay.account.interfaces;

import com.minialalipay.account.interfaces.account.AccountOpeningController.OpenAccountRequestDTO;
import com.minialalipay.account.interfaces.reconciliation.ReconciliationDiffController.ReconciliationDiffRequest;
import com.minialalipay.account.interfaces.tcc.BalanceTccController.BranchRequest;
import com.minialalipay.account.interfaces.tcc.LedgerTccController.Request;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 账户中心内部请求与 OpenAPI 字段边界一致性测试。 */
class InternalRequestValidationTest {

    private static final String ID = "01K1ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String TOO_LONG_ID = ID + "0";
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBusinessIdsThatAreNotTwentySixCharacters() {
        assertThat(validator.validate(new OpenAccountRequestDTO(TOO_LONG_ID))).isNotEmpty();
        assertThat(validator.validate(new BranchRequest("tcc:" + ID, ID, TOO_LONG_ID, 1L, ID))).isNotEmpty();
        assertThat(validator.validate(new Request("tcc:" + ID, ID, ID, ID, 1L,
                TOO_LONG_ID, 1L, 2L, ID, "0123456789abcdef0123456789abcdef"))).isNotEmpty();
    }

    @Test
    void rejectsMalformedTraceAndNonJsonEvidence() {
        ReconciliationDiffRequest request = new ReconciliationDiffRequest(
                ID, ID, "FACT_MISMATCH", "x", "x", ID, "not-a-trace", Instant.now());

        assertThat(validator.validate(request)).hasSizeGreaterThanOrEqualTo(3);
    }
}
