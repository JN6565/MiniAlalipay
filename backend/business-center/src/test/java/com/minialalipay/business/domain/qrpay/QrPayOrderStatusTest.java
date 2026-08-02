package com.minialalipay.business.domain.qrpay;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class QrPayOrderStatusTest {

    @Test
    void orderStatusesMatchQrPaymentStateMachine() {
        assertThat(Arrays.stream(QrPayOrderStatus.values()).map(Enum::name))
                .containsExactly(
                        "CREATED",
                        "SCANNED",
                        "PENDING_CONFIRMATION",
                        "RISK_REVIEW",
                        "PROCESSING",
                        "COMPENSATING",
                        "MANUAL_REVIEW",
                        "SUCCESS",
                        "REJECTED",
                        "CANCELLED",
                        "EXPIRED"
                );
    }
}
