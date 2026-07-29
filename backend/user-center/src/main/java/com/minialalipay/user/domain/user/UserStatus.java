package com.minialalipay.user.domain.user;

public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED;

    public boolean allowsLogin() {
        return this == ACTIVE;
    }
}
