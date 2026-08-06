package com.minialalipay.business.interfaces.security;

import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B 端运营接口的角色守卫。
 *
 * <p>角色只读取网关清洗并注入的 {@code X-User-Roles}，不接受请求体或查询参数中的角色。观察者可以访问
 * 只读投影，工单和告警处置只允许管理员或运营人员执行。</p>
 */
@Component
public final class OpsAccessGuard {
    private static final Set<String> READ_ROLES = Set.of("ADMIN", "OPERATOR", "OBSERVER");
    private static final Set<String> WRITE_ROLES = Set.of("ADMIN", "OPERATOR");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    /** 校验运营只读权限。 */
    public void requireRead(String trustedRolesHeader) {
        requireAny(trustedRolesHeader, READ_ROLES);
    }

    /** 校验运营处置权限。 */
    public void requireWrite(String trustedRolesHeader) {
        requireAny(trustedRolesHeader, WRITE_ROLES);
    }

    /** 校验管理员权限；用于非资金配置类操作（如告警阈值配置），运营人员与观察者无权限。 */
    public void requireAdmin(String trustedRolesHeader) {
        requireAny(trustedRolesHeader, ADMIN_ROLES);
    }

    private void requireAny(String trustedRolesHeader, Set<String> allowed) {
        Set<String> roles = parse(trustedRolesHeader);
        if (roles.stream().noneMatch(allowed::contains)) {
            throw new BusinessException(BusinessErrorCode.OPS_PERMISSION_REQUIRED);
        }
    }

    private Set<String> parse(String trustedRolesHeader) {
        if (trustedRolesHeader == null || trustedRolesHeader.isBlank()) return Set.of();
        return Arrays.stream(trustedRolesHeader.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
