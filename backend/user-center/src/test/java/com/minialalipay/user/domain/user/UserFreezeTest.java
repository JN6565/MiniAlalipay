package com.minialalipay.user.domain.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用户管理冻结/解冻状态流转测试。 */
class UserFreezeTest {

    /** 构造一个处于 ACTIVE 状态的用户。 */
    private User activeUser() {
        return new User("USER12345678901234567890", "REG123456789012345678901",
                "6200000000000001", "13800138000", "张三", "小张");
    }

    @Test
    void freezeTransitionsActiveToDisabledAndRecordsAudit() {
        User user = activeUser();
        // 模拟开户完成后进入 ACTIVE。
        user.activate();

        user.freeze("adm-001", "风险账户冻结");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.canLogin()).isFalse();
        assertThat(user.getDisabledBy()).isEqualTo("adm-001");
        assertThat(user.getDisabledReason()).isEqualTo("风险账户冻结");
    }

    @Test
    void unfreezeTransitionsDisabledBackToActiveAndClearsAudit() {
        User user = activeUser();
        user.activate();
        user.freeze("adm-001", "风险账户冻结");

        user.unfreeze();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.canLogin()).isTrue();
        assertThat(user.getDisabledBy()).isNull();
        assertThat(user.getDisabledReason()).isNull();
    }

    @Test
    void freezeRejectsNonActiveUser() {
        // 未开户（PROVISIONING）用户不可冻结。
        User provisioning = new User("USER12345678901234567890", "REG123456789012345678901",
                "6200000000000001", "13800138000", "张三", "小张");
        assertThatThrownBy(() -> provisioning.freeze("adm-001", "理由"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unfreezeRejectsNonDisabledUser() {
        User user = activeUser();
        user.activate();
        assertThatThrownBy(user::unfreeze).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void freezeRequiresOperatorAndReason() {
        User user = activeUser();
        user.activate();
        assertThatThrownBy(() -> user.freeze("  ", "理由")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.freeze("adm-001", "  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
