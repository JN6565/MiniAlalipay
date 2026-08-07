package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.user.domain.contact.Contact;
import com.minialalipay.user.domain.contact.ContactRepository;
import com.minialalipay.user.infrastructure.persistence.mapper.ContactMapper;
import com.minialalipay.user.infrastructure.persistence.po.ContactPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 联系人仓储实现。
 *
 * <p>实现 {@link ContactRepository} 接口，使用 MyBatis {@link ContactMapper}
 * 进行数据库操作。负责领域模型与持久化对象之间的转换。</p>
 *
 * @see ContactRepository 联系人仓储接口
 * @see ContactMapper 联系人 Mapper 接口
 */
@Repository
public class ContactRepositoryImpl implements ContactRepository {

    private final ContactMapper contactMapper;

    public ContactRepositoryImpl(ContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @Override
    public List<Contact> listByOwner(String ownerUserId, int limit) {
        return contactMapper.listByOwner(ownerUserId, limit)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Contact> findByOwnerAndPayee(String ownerUserId, String payeeUserId) {
        ContactPO po = contactMapper.selectByOwnerAndPayee(ownerUserId, payeeUserId);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public void upsert(Contact contact) {
        contactMapper.upsert(
                contact.getOwnerUserId(),
                contact.getPayeeUserId(),
                contact.getLastSuccessAt()
        );
    }

    @Override
    public void update(Contact contact) {
        ContactPO po = toPO(contact);
        int rows = contactMapper.update(po);
        if (rows == 0) {
            throw new BusinessException(new ContactErrorCode());
        }
    }

    /**
     * 将持久化对象转换为领域模型。
     */
    private Contact toDomain(ContactPO po) {
        return new Contact(
                po.getOwnerUserId(),
                po.getPayeeUserId(),
                po.getAlias(),
                po.getSuccessCount(),
                po.getLastSuccessAt(),
                po.isPinned(),
                po.isHidden(),
                po.getVersion(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 将领域模型转换为持久化对象。
     */
    private ContactPO toPO(Contact contact) {
        return new ContactPO(
                contact.getOwnerUserId(),
                contact.getPayeeUserId(),
                contact.getAlias(),
                contact.getSuccessCount(),
                contact.getLastSuccessAt(),
                contact.isPinned(),
                contact.isHidden(),
                contact.getVersion(),
                contact.getCreatedAt(),
                contact.getUpdatedAt()
        );
    }

    /**
     * 联系人版本冲突错误码。
     */
    private record ContactErrorCode() implements com.minialalipay.common.error.ErrorCode {
        @Override
        public String code() { return "CONTACT_VERSION_CONFLICT"; }
        @Override
        public String message() { return "联系人版本冲突，请重试"; }
        @Override
        public int httpStatus() { return 409; }
    }
}
