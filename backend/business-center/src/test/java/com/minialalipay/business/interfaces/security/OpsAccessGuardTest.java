package com.minialalipay.business.interfaces.security;

import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsAccessGuardTest {
    private final OpsAccessGuard guard = new OpsAccessGuard();

    @Test
    void 管理员与运营人员可访问只读投影其他角色被拒绝() {
        assertDoesNotThrow(() -> guard.requireRead("ADMIN"));
        assertDoesNotThrow(() -> guard.requireRead("OPERATOR"));
        assertThrows(BusinessException.class, () -> guard.requireRead("OBSERVER"));
        assertThrows(BusinessException.class, () -> guard.requireRead("USER"));
    }

    @Test
    void 管理员运营人员可处置缺失或普通角色被拒绝() {
        assertDoesNotThrow(() -> guard.requireWrite("ADMIN,OPERATOR"));
        assertDoesNotThrow(() -> guard.requireWrite("OPERATOR"));
        assertThrows(BusinessException.class, () -> guard.requireRead(null));
        assertThrows(BusinessException.class, () -> guard.requireRead("USER"));
    }
}
