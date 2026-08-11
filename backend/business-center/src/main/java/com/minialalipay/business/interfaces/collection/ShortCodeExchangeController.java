package com.minialalipay.business.interfaces.collection;

import com.minialalipay.business.application.collection.ShortCodeExchangeService;
import com.minialalipay.business.application.port.ShortCodeAttemptLimiter;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 手动输入收款短码兑换端点。
 *
 * <p>短码兑换与扫码等价：登录付款人获得新建订单或绑定既有动态订单，再凭返回的订单标识进入既有付款流程。
 * 失败尝试按会话计数，超限锁定返回 429。</p>
 */
@RestController
@RequestMapping("/api/v1/p2p-collections")
public class ShortCodeExchangeController {
    private final ShortCodeExchangeService service;
    private final ShortCodeAttemptLimiter limiter;
    private final RequestIdGenerator requestIds;

    /** 创建短码兑换端点。 */
    @Autowired
    public ShortCodeExchangeController(ShortCodeExchangeService service, ShortCodeAttemptLimiter limiter,
                                       RequestIdGenerator requestIds) {
        this.service = service;
        this.limiter = limiter;
        this.requestIds = requestIds;
    }

    /** 登录付款人使用 8 位短码兑换收款订单；响应只含码类型与订单标识，不含令牌与账户。 */
    @PostMapping("/short-code-exchanges")
    public ResponseEntity<ApiResponse<ShortCodeExchangeResponse>> exchange(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ShortCodeRequest body, HttpServletRequest request) {
        if (userId == null || userId.isBlank()) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        // 无引导会话时当场创建：兑换产生的会话绑定供后续订单查询、确认与支付复用
        String sessionId = request.getSession(true).getId();
        limiter.requireAllowed(sessionId);
        ShortCodeExchangeService.ExchangeResult result;
        try {
            result = service.exchange(userId, sessionId, body.shortCode());
        } catch (BusinessException error) {
            // 仅码本身无效计入失败尝试；自付、账户不可用等业务拒绝不计入限流
            if (BusinessErrorCode.SHORT_CODE_INVALID.code().equals(error.errorCode().code())) {
                limiter.recordFailure(sessionId);
            }
            throw error;
        }
        limiter.reset(sessionId);
        return ResponseEntity.ok(ApiResponse.success(new ShortCodeExchangeResponse(result.codeType().name(), result.orderId()),
                requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id")));
    }

    /** 短码兑换请求，仅接受 8 位纯数字短码。 */
    public record ShortCodeRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{8}") String shortCode) { }

    /** 兑换结果：码类型与订单标识。 */
    public record ShortCodeExchangeResponse(String codeType, String orderId) { }
}
