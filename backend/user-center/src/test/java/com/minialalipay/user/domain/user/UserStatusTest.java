package com.minialalipay.user.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void onlyActiveUserCanStartSession() {
        assertThat(UserStatus.ACTIVE.allowsLogin()).isTrue();
        assertThat(UserStatus.LOCKED.allowsLogin()).isFalse();
        assertThat(UserStatus.DISABLED.allowsLogin()).isFalse();
    }
}
