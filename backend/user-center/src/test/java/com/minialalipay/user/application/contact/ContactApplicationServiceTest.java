package com.minialalipay.user.application.contact;

import com.minialalipay.user.application.contact.dto.ContactDTO;
import com.minialalipay.user.domain.contact.Contact;
import com.minialalipay.user.domain.contact.ContactRepository;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 联系人应用服务测试。
 *
 * <p>重点覆盖常用联系人列表的展示字段口径：收款人姓名在服务边界脱敏，
 * 手机号仅下发脱敏形式，明文姓名与完整手机号不得离开服务边界。</p>
 */
class ContactApplicationServiceTest {

    private final ContactRepository contactRepository = mock(ContactRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ContactApplicationService service =
            new ContactApplicationService(contactRepository, userRepository);

    /** 构造已绑定身份的 ACTIVE 收款人，用于模拟联系人展示信息。 */
    private User payeeUser(String phoneNumber, String realName, String nickname) {
        return new User(
                "USRABCDEFGHI20260807000002", "REGABCDEFGHI20260807000002",
                "6200000000000002", phoneNumber, realName, nickname,
                phoneNumber.substring(phoneNumber.length() - 4), "VERIFIED",
                UserStatus.ACTIVE, 0,
                Instant.now(), Instant.now(), null, null, null, null);
    }

    /** 验证联系人列表返回脱敏展示名与脱敏手机号，明文姓名与完整手机号不出服务边界。 */
    @Test
    void 联系人列表返回脱敏展示名和脱敏手机号() {
        Contact contact = new Contact("owner-user", "payee-user", Instant.now());
        when(contactRepository.listByOwner("owner-user", 5)).thenReturn(List.of(contact));
        when(userRepository.findById("payee-user"))
                .thenReturn(Optional.of(payeeUser("13800138000", "张飞", "燕人老张")));

        List<ContactDTO> results = service.listContacts("owner-user", 5);

        assertEquals(1, results.size());
        ContactDTO dto = results.get(0);
        assertEquals("张*", dto.payeeName());
        assertEquals("138****8000", dto.maskedPhone());
        assertEquals("8000", dto.phoneTail());
        assertEquals("6200000000000002", dto.accountNumber());
        // 明文姓名与完整手机号不得出现在任何对外字段中
        assertTrue(java.util.stream.Stream.of(
                        dto.payeeName(), dto.maskedPhone(), dto.phoneTail(), dto.accountNumber(), dto.alias())
                .noneMatch(field -> field != null
                        && (field.contains("13800138000") || field.contains("张飞"))));
    }

    /** 验证收款人不存在时展示字段降级：姓名 ***、账号空串、手机号字段为 null。 */
    @Test
    void 收款人不存在时展示字段降级() {
        Contact contact = new Contact("owner-user", "missing-user", Instant.now());
        when(contactRepository.listByOwner("owner-user", 5)).thenReturn(List.of(contact));
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        List<ContactDTO> results = service.listContacts("owner-user", null);

        assertEquals(1, results.size());
        ContactDTO dto = results.get(0);
        assertEquals("***", dto.payeeName());
        assertEquals("", dto.accountNumber());
        assertNull(dto.maskedPhone());
        assertNull(dto.phoneTail());
    }

    /** 验证未绑定身份的收款人降级展示脱敏昵称。 */
    @Test
    void 未绑定身份时降级展示脱敏昵称() {
        Contact contact = new Contact("owner-user", "payee-user", Instant.now());
        when(contactRepository.listByOwner("owner-user", 5)).thenReturn(List.of(contact));
        when(userRepository.findById("payee-user"))
                .thenReturn(Optional.of(payeeUser("13900139000", null, "匿名昵称")));

        List<ContactDTO> results = service.listContacts("owner-user", 5);

        assertEquals("匿***", results.get(0).payeeName());
        assertEquals("139****9000", results.get(0).maskedPhone());
    }
}
