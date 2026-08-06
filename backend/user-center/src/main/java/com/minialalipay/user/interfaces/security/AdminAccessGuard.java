package com.minialalipay.user.interfaces.security;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B 端管理接口的角色守卫。
 *
 * <p>角色只读取网关清洗并注入的 {@code X-User-Roles}，不接受请求体或查询参数中的角色。
 * 用户管理属系统管理能力，仅 {@code ADMIN} 可访问；运营人员与普通用户一律 403。</p>
 */
@Component
public final class AdminAccessGuard {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    /** 校验系统管理员权限；用于用户管理等非资金管理类操作。 */
    public void requireAdmin(String trustedRolesHeader) {
        Set<String> roles = parse(trustedRolesHeader);
        if (roles.stream().noneMatch(ADMIN_ROLES::contains)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
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
