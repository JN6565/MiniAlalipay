package com.minialalipay.user.interfaces.contact;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.contact.ContactApplicationService;
import com.minialalipay.user.application.contact.dto.ContactDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 联系人 Controller。
 *
 * <p>提供常用收款人列表查询和联系人属性更新端点。
 * 所有接口经网关访问，禁止直连服务端口。</p>
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET /api/v1/contacts — 查询当前用户的常用联系人列表</li>
 *   <li>PATCH /api/v1/contacts/{payeeUserId} — 更新联系人属性（别名、置顶、隐藏）</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactApplicationService contactApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public ContactController(
            ContactApplicationService contactApplicationService,
            RequestIdGenerator requestIdGenerator
    ) {
        this.contactApplicationService = contactApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 查询当前用户的常用联系人列表。
     *
     * <p>按置顶优先、最近成功转账时间倒序排列，最多返回指定数量（默认 5）。</p>
     *
     * @param userId      当前用户 ID（由网关透传）
     * @param limit       最大返回数量（默认 5）
     * @param httpRequest HTTP 请求
     * @return 联系人列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactDTO>>> listContacts(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "limit", required = false, defaultValue = "5") Integer limit,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        List<ContactDTO> contacts = contactApplicationService.listContacts(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(contacts, requestId, traceId));
    }

    /**
     * 更新联系人属性。
     *
     * <p>支持修改备注别名（alias）、置顶（pinned）和隐藏（hidden）。</p>
     *
     * @param payeeUserId 收款人用户 ID
     * @param userId      当前用户 ID（由网关透传）
     * @param request     更新请求体
     * @param httpRequest HTTP 请求
     * @return 更新结果
     */
    @PatchMapping("/{payeeUserId}")
    public ResponseEntity<ApiResponse<Void>> updateContact(
            @PathVariable("payeeUserId") String payeeUserId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody UpdateContactRequest request,
            HttpServletRequest httpRequest
    ) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        contactApplicationService.updateContact(userId, payeeUserId, request.alias(), request.pinned(), request.hidden());
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 更新联系人请求体。
     *
     * @param alias  备注别名（最大 64 字符，可空）
     * @param pinned 是否置顶（可空表示不修改）
     * @param hidden 是否隐藏（可空表示不修改）
     */
    public record UpdateContactRequest(String alias, Boolean pinned, Boolean hidden) {
    }
}
