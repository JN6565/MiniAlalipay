package com.minialalipay.user.domain.user;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void userStatusesMatchRegistrationLifecycle() {
        assertThat(Arrays.stream(UserStatus.values()).map(Enum::name))
                .containsExactly("PROVISIONING", "ACTIVE", "DISABLED");
    }

    @Test
    void onlyActiveUserCanStartSession() {
        assertThat(UserStatus.ACTIVE.allowsLogin()).isTrue();
        assertThat(Arrays.stream(UserStatus.values())
                .filter(status -> status != UserStatus.ACTIVE)
                .allMatch(status -> !status.allowsLogin()))
                .isTrue();
    }
}
