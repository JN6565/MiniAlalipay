package com.minialalipay.account.domain.tcc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TccBranchStatusTest {

    @Test
    void branchStatusesMatchTccPersistenceContract() {
        assertThat(Arrays.stream(TccBranchStatus.values()).map(Enum::name))
                .containsExactly("INIT", "TRIED", "CONFIRMED", "CANCELLED", "MANUAL_REVIEW");
    }
}
