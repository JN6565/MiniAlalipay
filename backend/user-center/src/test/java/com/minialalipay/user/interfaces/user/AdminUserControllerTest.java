package com.minialalipay.user.interfaces.user;

import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.user.AdminUserService;
import com.minialalipay.user.domain.auth.UserErrorCode;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserAdminView;
import com.minialalipay.user.domain.user.UserStatus;
import com.minialalipay.user.interfaces.security.AdminAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B 端用户管理接口测试：列表、冻结、解冻、角色门禁与状态流转。 */
@WebMvcTest(value = AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AdminAccessGuard.class, AdminUserControllerTest.TestSupportConfiguration.class})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    /** 构造处于 ACTIVE 状态的用户重建对象。 */
    private User activeUser(String userId) {
        return new User(userId, "REGTESTUSER0120260801000001", "6200000000000001", "13800138000", "张三", "小张",
                "8000", "VERIFIED", UserStatus.ACTIVE, 3, Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"), null, null, null, null);
    }

    @Test
    void 管理员可分页查询脱敏用户列表() throws Exception {
        User user = activeUser("USRTESTUSER0120260801000001");
        when(adminUserService.list(UserStatus.ACTIVE, null, 50))
                .thenReturn(List.of(new UserAdminView(user, null)));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value("USRTESTUSER0120260801000001"))
                .andExpect(jsonPath("$.data.items[0].loginNameMasked").value("620****0001"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].version").value(3));
    }

    @Test
    void 运营人员访问用户列表返回403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Id", "ops-001")
                        .header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 管理员冻结用户并返回最新版本() throws Exception {
        User user = activeUser("USRTESTUSER0120260801000001");
        User frozen = activeUser("USRTESTUSER0120260801000001");
        frozen.freeze("adm-001", "风险账户冻结");
        when(adminUserService.freeze("USRTESTUSER0120260801000001", 3, "adm-001", "风险账户冻结"))
                .thenReturn(new AdminUserService.UserUpdateResult(frozen, 4));

        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/freeze")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3,"reason":"风险账户冻结"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.disabledBy").value("adm-001"))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    @Test
    void 冻结缺失理由返回400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/freeze")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 冻结版本冲突返回409() throws Exception {
        when(adminUserService.freeze("USRTESTUSER0120260801000001", 2, "adm-001", "理由"))
                .thenThrow(new BusinessException(UserErrorCode.VERSION_CONFLICT));

        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/freeze")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2,"reason":"理由"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void 冻结非ACTIVE用户返回409() throws Exception {
        when(adminUserService.freeze("USRTESTUSER0120260801000001", 3, "adm-001", "理由"))
                .thenThrow(new BusinessException(UserErrorCode.USER_STATE_INVALID));

        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/freeze")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3,"reason":"理由"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_STATE_INVALID"));
    }

    @Test
    void 运营人员冻结用户返回403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/freeze")
                        .header("X-User-Id", "ops-001")
                        .header("X-User-Roles", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":3,"reason":"理由"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void 管理员解冻用户() throws Exception {
        User user = activeUser("USRTESTUSER0120260801000001");
        user.freeze("adm-001", "风险账户冻结");
        user.unfreeze();
        when(adminUserService.unfreeze("USRTESTUSER0120260801000001", 4, "adm-001"))
                .thenReturn(new AdminUserService.UserUpdateResult(user, 5));

        mockMvc.perform(post("/api/v1/admin/users/USRTESTUSER0120260801000001/unfreeze")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(5));
    }

    @Test
    void 非法状态参数返回400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Id", "adm-001")
                        .header("X-User-Roles", "ADMIN")
                        .param("status", "FROZEN"))
                .andExpect(status().isBadRequest());
    }

    /** 为 Web MVC 切片提供平台通用异常映射组件与请求编号生成器。 */
    @TestConfiguration
    static class TestSupportConfiguration {
        @Bean
        CommonExceptionMapper commonExceptionMapper() {
            return new CommonExceptionMapper();
        }

        @Bean
        RequestIdGenerator requestIdGenerator() {
            return new RequestIdGenerator();
        }
    }
}
