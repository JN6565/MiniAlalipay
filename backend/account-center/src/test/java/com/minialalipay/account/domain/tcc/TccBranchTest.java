package com.minialalipay.account.domain.tcc;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TccBranchTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void cancel先到时记录空回滚且拒绝晚到try() {
        TccBranch branch = TccBranch.emptyRollback("xid-1", TccBranchType.PAYER_BALANCE,
                "account-1", "tx-1", 100L, NOW);

        assertEquals(TccBranchStatus.CANCELLED, branch.getStatus());
        assertEquals(RollbackType.EMPTY, branch.getRollbackType());
        assertThrows(IllegalStateException.class, () -> branch.markTried(NOW.plusSeconds(1)));
    }

    @Test
    void try后重复确认保持终态幂等() {
        TccBranch branch = TccBranch.initialize("xid-1", TccBranchType.PAYEE_BALANCE,
                "account-2", "tx-1", 100L, NOW);
        branch.markTried(NOW.plusSeconds(1));

        branch.confirm(NOW.plusSeconds(2));
        branch.confirm(NOW.plusSeconds(3));

        assertEquals(TccBranchStatus.CONFIRMED, branch.getStatus());
    }
}
