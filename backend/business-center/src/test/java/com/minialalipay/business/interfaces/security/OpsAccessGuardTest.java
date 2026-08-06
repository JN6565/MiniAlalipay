package com.minialalipay.business.interfaces.security;

import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsAccessGuardTest {
    private final OpsAccessGuard guard = new OpsAccessGuard();

    @Test
    void 三类运营角色可访问只读投影观察者不能处置() {
        assertDoesNotThrow(() -> guard.requireRead("ADMIN"));
        assertDoesNotThrow(() -> guard.requireRead("OPERATOR"));
        assertDoesNotThrow(() -> guard.requireRead("OBSERVER"));
        assertThrows(BusinessException.class, () -> guard.requireWrite("OBSERVER"));
    }

    @Test
    void 管理员运营人员可处置缺失或普通角色被拒绝() {
        assertDoesNotThrow(() -> guard.requireWrite("ADMIN,OBSERVER"));
        assertDoesNotThrow(() -> guard.requireWrite("OPERATOR"));
        assertThrows(BusinessException.class, () -> guard.requireRead(null));
        assertThrows(BusinessException.class, () -> guard.requireRead("USER"));
    }
}
