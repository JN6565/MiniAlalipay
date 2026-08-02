package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTypeTest {

    @Test
    void accountTypesMatchDatabaseContract() {
        assertThat(Arrays.stream(AccountType.values()).map(Enum::name))
                .containsExactly("PERSONAL", "MERCHANT");
    }
}
