package com.minialalipay.business.interfaces.qrpay;

import com.minialalipay.business.application.qrpay.QrPayApplicationService;
import com.minialalipay.business.application.qrpay.QrPayEventApplicationService;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

/**
 * 动态扫码收款来源订单接口。
 *
 * <p>二维码原始令牌仅允许出现在创建响应中的短期 H5 地址和令牌交换请求；确认令牌只在签发响应和付款请求之间传输。</p>
 */
@RestController
@RequestMapping("/api/v1/qr-pay")
public class QrPayController {
    private final QrPayApplicationService service;
    private final QrPayEventApplicationService eventService;
    private final RequestIdGenerator requestIds;

    /** 创建二维码接口。 */
    public QrPayController(QrPayApplicationService service, RequestIdGenerator requestIds) {
        this(service, null, requestIds);
    }

    /** 创建包含可重放 SSE 订阅能力的二维码 Controller。 */
    @Autowired
    public QrPayController(QrPayApplicationService service, QrPayEventApplicationService eventService, RequestIdGenerator requestIds) {
        this.service = service;
        this.eventService = eventService;
        this.requestIds = requestIds;
    }

    /** 创建当前用户本人的动态收款订单，写操作要求幂等键。 */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<QrPayOrderResponse>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateQrPayOrderRequest body, HttpServletRequest request) {
        QrPayApplicationService.CreatedOrder created = service.create(userId, body.amountFen(), body.subject(), idempotencyKey);
        String url = created.rawToken() == null ? null : "/api/v1/qr-pay/orders/by-token?t=" + created.rawToken();
        return ResponseEntity.status(201).body(success(QrPayOrderResponse.from(created.order(), null, url), request));
    }

    /** 查询当前用户创建的二维码订单。 */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<QrPayOrderPageResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        List<QrPayOrderResponse> items = service.listForPayee(userId, status, limit).stream()
                .map(order -> QrPayOrderResponse.from(order, null, null)).toList();
        return ResponseEntity.ok(success(new QrPayOrderPageResponse(items, null), request));
    }

    /** 仅验证令牌并建立 HTTP 会话，不返回订单、金额或收款人信息。 */
    @GetMapping("/orders/by-token")
    public ResponseEntity<Void> bootstrap(@RequestParam("t") String token, HttpServletRequest request) {
        service.validateBootstrapToken(token);
        request.getSession(true);
        return ResponseEntity.noContent().build();
    }

    /** 用同一 H5 会话交换原始令牌并返回脱敏订单。 */
    @PostMapping("/token-exchanges")
    public ResponseEntity<ApiResponse<QrPayOrderResponse>> exchange(@Valid @RequestBody TokenExchangeRequest body,
                                                                      HttpServletRequest request) {
        QrPayOrder order = service.exchange(sessionId(request), body.token());
        return ResponseEntity.ok(success(QrPayOrderResponse.from(order, null, null), request));
    }

    /** 读取收款人、付款人或绑定 H5 会话可见的二维码订单权威状态。 */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<QrPayOrderResponse>> get(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                                 @PathVariable String id, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (userId == null || userId.isBlank()) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(success(QrPayOrderResponse.from(service.getForAuthorizedUser(userId,
                session == null ? null : session.getId(), id), null, null), request));
    }

    /** 订阅订单状态；首次发送权威快照，携带 Last-Event-ID 时只重放保留期内后续事件。 */
    @GetMapping(value = "/orders/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader(value = "X-User-Id", required = false) String userId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @PathVariable String id, HttpServletRequest request) {
        if (userId == null || userId.isBlank()) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        if (eventService == null) throw new IllegalStateException("二维码 SSE 服务未配置");
        HttpSession session = request.getSession(false);
        return eventService.subscribe(userId, session == null ? null : session.getId(), id, lastEventId);
    }

    /** 在已绑定的 H5 会话中推进扫码状态。 */
    @PostMapping("/orders/{id}/scan")
    public ResponseEntity<ApiResponse<QrPayOrderResponse>> scan(@PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok(success(QrPayOrderResponse.from(service.scan(id, sessionId(request)), null, null), request));
    }

    /** 取消尚未受理资金交易的订单。 */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<QrPayOrderResponse>> cancel(
            @RequestHeader("X-User-Id") String userId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id, @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(success(QrPayOrderResponse.from(service.cancel(userId, id, body.version(), idempotencyKey), null, null), request));
    }

    /** 校验支付证明并签发绑定订单快照的一次性确认令牌。 */
    @PostMapping("/orders/{id}/confirmations")
    public ResponseEntity<ApiResponse<ConfirmationResponse>> confirmation(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id,
            @Valid @RequestBody ConfirmationRequest body, HttpServletRequest request) {
        QrPayApplicationService.IssuedConfirmation issued = service.issueConfirmation(userId, id, sessionId(request),
                body.version(), body.paymentProof(), body.fundingSource(), body.cardId());
        return ResponseEntity.ok(success(new ConfirmationResponse(issued.confirmationToken(), issued.subjectHash(), issued.expiresAt()), request));
    }

    /** 消费确认令牌并通过统一交易和 TCC 受理余额扫码支付。 */
    @PostMapping("/orders/{id}/pay")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @PathVariable String id,
            @Valid @RequestBody PayRequest body, HttpServletRequest request) {
        FundTransaction transaction = service.pay(userId, id, sessionId(request), body.confirmationToken(), idempotencyKey,
                request.getHeader("X-Trace-Id"), body.cardId());
        return ResponseEntity.accepted().body(success(new PaymentResponse(id, transaction.getTransactionId(),
                transaction.getStatus().name(), "/api/v1/qr-pay/orders/" + id, transaction.getUpdatedAt()), request));
    }

    private String sessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        return session.getId();
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }

    /** 创建动态二维码订单请求，金额统一以分表示。 */
    public record CreateQrPayOrderRequest(@Min(1) @Max(5_000_000) long amountFen, String subject) { }

    /** 原始二维码令牌交换请求，字段不得写入日志。 */
    public record TokenExchangeRequest(@NotBlank @Size(min = 16, max = 256) String token) { }

    /** 客户端读取到的订单 CAS 版本。 */
    public record VersionRequest(@Min(0) long version) { }

    /** 动态扫码确认请求；付款账户始终从登录会话派生。选择 BANK_CARD 时必须指定 cardId。 */
    public record ConfirmationRequest(@Min(0) long version, @NotBlank @Size(max = 256) String paymentProof,
                                      @jakarta.validation.constraints.NotNull FundingSource fundingSource,
                                      @Size(max = 26) String cardId) { }

    /** 动态扫码付款请求；资金来源必须与确认令牌绑定，不能在此处覆盖。银行卡支付时需携带 cardId。 */
    public record PayRequest(@NotBlank @Size(min = 16, max = 256) String confirmationToken,
                             @Size(max = 26) String cardId) { }

    /** 一次性确认令牌响应。 */
    public record ConfirmationResponse(String confirmationToken, String subjectHash, Instant expiresAt) { }

    /** 资金受理响应；PROCESSING 或未知结果绝不表示成功。 */
    public record PaymentResponse(String orderId, String transactionId, String status, String statusUrl, Instant updatedAt) { }

    /** 脱敏二维码订单响应，不包含收款账户、付款账户或原始令牌。 */
    public record QrPayOrderResponse(String qrOrderId, String payeeDisplayName, long amountFen, String subject,
                                     String status, String transactionId, String qrCodeUrl, Instant expiresAt, long version) {
        static QrPayOrderResponse from(QrPayOrder order, String payeeDisplayName, String qrCodeUrl) {
            return new QrPayOrderResponse(order.getOrderId(), payeeDisplayName, order.getAmountFen(), order.getSubject(),
                    order.getStatus().name(), order.getTransactionId(), qrCodeUrl, order.getExpiresAt(), order.getVersion());
        }
    }

    /** 二维码订单单页；当前无游标时 nextCursor 为 null。 */
    public record QrPayOrderPageResponse(List<QrPayOrderResponse> items, String nextCursor) { }
}
