package com.minialalipay.user.interfaces.contact;

import com.minialalipay.user.application.contact.ContactApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部联系人归档 Controller。
 *
 * <p>仅供 business-center 转账成功后回调归档收款人，不经过前端网关公开路由。
 * 调用方必须携带正确的内部服务令牌（{@code X-Internal-Service-Token}）。</p>
 */
@RestController
@RequestMapping("/internal/v1/contacts")
public class InternalContactController {

    private final ContactApplicationService contactApplicationService;
    private final String serviceToken;

    public InternalContactController(
            ContactApplicationService contactApplicationService,
            @Value("${internal.auth.service-token:local-internal-token}") String serviceToken
    ) {
        this.contactApplicationService = contactApplicationService;
        this.serviceToken = serviceToken;
    }

    /**
     * 归档收款人。
     *
     * <p>转账成功后由 business-center 异步调用，将收款人添加到付款人的常用联系人列表。
     * 操作天然幂等（upsert 语义），重复调用安全。</p>
     *
     * @param suppliedServiceToken 调用方服务令牌
     * @param request              归档请求
     * @return 归档结果
     */
    @PostMapping("/archive")
    public ResponseEntity<Void> archivePayee(
            @RequestHeader("X-Internal-Service-Token") String suppliedServiceToken,
            @RequestBody ArchiveRequest request
    ) {
        if (!serviceToken.equals(suppliedServiceToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        contactApplicationService.archivePayee(request.ownerUserId(), request.payeeUserId());
        return ResponseEntity.ok().build();
    }

    /**
     * 归档请求体。
     *
     * @param ownerUserId 付款人用户 ID（联系人列表所有者）
     * @param payeeUserId 收款人用户 ID
     */
    public record ArchiveRequest(String ownerUserId, String payeeUserId) {
    }
}
