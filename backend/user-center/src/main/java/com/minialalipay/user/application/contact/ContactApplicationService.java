package com.minialalipay.user.application.contact;

import com.minialalipay.user.application.contact.dto.ContactDTO;
import com.minialalipay.user.application.user.PhoneMasker;
import com.minialalipay.user.domain.contact.Contact;
import com.minialalipay.user.domain.contact.ContactRepository;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import com.minialalipay.user.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 联系人应用服务。
 *
 * <p>编排联系人领域操作，包括查询常用联系人列表、转账成功归档和属性更新。
 * 本服务不包含业务规则——业务规则由 {@link Contact} 聚合根封装。</p>
 */
@Service
public class ContactApplicationService {

    /** 常用联系人列表默认最大数量。 */
    private static final int DEFAULT_CONTACT_LIMIT = 5;

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    public ContactApplicationService(ContactRepository contactRepository, UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
    }

    /**
     * 查询当前用户的常用联系人列表。
     *
     * <p>按置顶优先、最近成功转账时间倒序排列，最多返回指定数量。
     * 已隐藏的联系人不包含在结果中。每条记录会按收款人用户 ID
     * 补齐昵称、账户号和脱敏手机号等展示字段；收款人不存在或非
     * ACTIVE 时展示字段置空，由前端自行降级，避免前端拿用户 ID 片段充当名字。</p>
     *
     * @param ownerUserId 当前用户 ID
     * @param limit       最大返回数量（为 null 时使用默认值 5）
     * @return 联系人 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<ContactDTO> listContacts(String ownerUserId, Integer limit) {
        int effectiveLimit = limit != null && limit > 0 ? Math.min(limit, 20) : DEFAULT_CONTACT_LIMIT;
        List<Contact> contacts = contactRepository.listByOwner(ownerUserId, effectiveLimit);
        return contacts.stream()
                .map(c -> {
                    // 仅 ACTIVE 收款人才下发展示信息，完整手机号不出服务边界，只输出脱敏结果
                    User payee = userRepository.findById(c.getPayeeUserId())
                            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                            .orElse(null);
                    return new ContactDTO(
                            c.getPayeeUserId(),
                            c.getAlias(),
                            c.getSuccessCount(),
                            c.getLastSuccessAt(),
                            c.isPinned(),
                            payee == null ? null : payee.getNickname(),
                            payee == null ? null : payee.getAccountNumber(),
                            payee == null ? null : PhoneMasker.mask(payee.getPhoneNumber()),
                            payee == null ? null : payee.getPhoneTail()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 归档收款人（转账成功后调用）。
     *
     * <p>如果联系人已存在，递增成功次数并更新最近成功时间；
     * 如果不存在，创建新的联系人记录。操作天然幂等（upsert 语义）。</p>
     *
     * @param ownerUserId 付款人用户 ID
     * @param payeeUserId 收款人用户 ID
     */
    @Transactional
    public void archivePayee(String ownerUserId, String payeeUserId) {
        Contact contact = new Contact(ownerUserId, payeeUserId, Instant.now());
        contactRepository.upsert(contact);
    }

    /**
     * 更新联系人属性。
     *
     * <p>支持修改备注别名、置顶和隐藏状态。使用乐观锁保证并发安全。</p>
     *
     * @param ownerUserId 当前用户 ID
     * @param payeeUserId 收款人用户 ID
     * @param alias       新的备注别名（可为 null 表示不修改）
     * @param pinned      新的置顶状态（可为 null 表示不修改）
     * @param hidden      新的隐藏状态（可为 null 表示不修改）
     */
    @Transactional
    public void updateContact(
            String ownerUserId,
            String payeeUserId,
            String alias,
            Boolean pinned,
            Boolean hidden
    ) {
        contactRepository.findByOwnerAndPayee(ownerUserId, payeeUserId)
                .ifPresent(contact -> {
                    if (alias != null) {
                        contact.updateAlias(alias);
                    }
                    if (pinned != null) {
                        contact.setPinned(pinned);
                    }
                    if (hidden != null) {
                        contact.setHidden(hidden);
                    }
                    contactRepository.update(contact);
                });
    }
}
