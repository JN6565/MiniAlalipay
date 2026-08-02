package com.minialalipay.business.domain.transfer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DraftStatusTest {

    @Test
    void draftStatusesMatchTransferDraftContract() {
        assertThat(Arrays.stream(DraftStatus.values()).map(Enum::name))
                .containsExactly("DRAFT", "VALIDATED", "PENDING_CONFIRMATION", "SUBMITTED", "EXPIRED");
    }
}
