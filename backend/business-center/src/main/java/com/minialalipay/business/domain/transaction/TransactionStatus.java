package com.minialalipay.business.domain.transaction;

public enum TransactionStatus {
    PROCESSING,
    COMPENSATING,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
