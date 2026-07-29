package com.minialalipay.account.domain.account;

public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED;

    public boolean allowsDebit() {
        return this == ACTIVE;
    }
}
