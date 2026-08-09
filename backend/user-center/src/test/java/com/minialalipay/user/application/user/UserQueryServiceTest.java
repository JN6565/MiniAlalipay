package com.minialalipay.user.application.user;

import com.minialalipay.user.application.user.dto.UserSearchResult;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用户搜索应用服务测试。
 *
 * <p>重点覆盖搜索结果字段口径：真实姓名与手机号均在服务边界脱敏，
 * 明文姓名与完整手机号不得离开服务边界。</p>
 */
class UserQueryServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserQueryService service = new UserQueryService(userRepository);

    /** 构造已绑定身份的 ACTIVE 用户，用于模拟搜索结果。 */
    private User userWithIdentity(String phoneNumber, String realName, String nickname) {
        return new User(
                "USRABCDEFGHI20260807000001", "REGABCDEFGHI20260807000001",
                "6200000000000001", phoneNumber, realName, nickname,
                phoneNumber.substring(phoneNumber.length() - 4), "VERIFIED",
                com.minialalipay.user.domain.user.UserStatus.ACTIVE, 0,
                Instant.now(), Instant.now(), null, null, null, null);
    }

    /** 验证搜索结果返回脱敏真实姓名和脱敏手机号，明文姓名与完整手机号不出服务边界。 */
    @Test
    void 搜索结果包含脱敏真实姓名和脱敏手机号() {
        User user = userWithIdentity("13800138000", "张飞", "燕人老张");
        when(userRepository.searchByKeyword("13800138000", "current-user", 20))
                .thenReturn(List.of(user));

        List<UserSearchResult> results = service.searchUsers("13800138000", "current-user");

        assertEquals(1, results.size());
        UserSearchResult result = results.get(0);
        assertEquals("张*", result.maskedRealName());
        assertEquals("燕人老张", result.nickname());
        assertEquals("138****8000", result.maskedPhone());
        assertEquals("8000", result.phoneTail());
        // 明文姓名与完整手机号不得出现在任何对外字段中
        assertTrue(results.stream().flatMap(r -> java.util.stream.Stream.of(
                        r.maskedRealName(), r.nickname(), r.maskedPhone(), r.phoneTail(), r.accountNumber()))
                .noneMatch(field -> field != null
                        && (field.contains("13800138000") || field.contains("张飞"))));
    }

    /** 验证未绑定身份的用户搜索时 maskedRealName 为 null，前端可降级展示昵称。 */
    @Test
    void 未绑定身份时脱敏真实姓名为空() {
        User user = userWithIdentity("13900139000", null, "匿名昵称");
        when(userRepository.searchByKeyword(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(user));

        List<UserSearchResult> results = service.searchUsers("13900139000", "current-user");

        assertEquals(1, results.size());
        assertNull(results.get(0).maskedRealName());
        assertEquals("139****9000", results.get(0).maskedPhone());
    }

    /** 验证空白关键词直接返回空列表，不访问仓储。 */
    @Test
    void 空白关键词返回空列表() {
        assertTrue(service.searchUsers(null, "current-user").isEmpty());
        assertTrue(service.searchUsers("   ", "current-user").isEmpty());
    }
}
