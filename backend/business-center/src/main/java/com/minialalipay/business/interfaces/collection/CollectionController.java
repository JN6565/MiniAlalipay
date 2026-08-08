package com.minialalipay.business.interfaces.collection;

import com.minialalipay.business.application.collection.CollectionApplicationService;
import com.minialalipay.business.application.collection.CollectionEventApplicationService;
import com.minialalipay.business.application.collection.CollectionPaymentApplicationService;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.infrastructure.http.UserInfoHttpAdapter;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

/**
 * 个人码与固定收款请求接口。
 *
 * <p>接口只管理收款入口和来源请求；原始公开令牌仅拼入本次响应的入口地址，不写入日志、DTO 持久化或后续查询响应。</p>
 */
@RestController
@RequestMapping("/api/v1/p2p-collections")
public class CollectionController {
    private final CollectionApplicationService service;
    private final CollectionPaymentApplicationService paymentService;
    private final CollectionEventApplicationService eventService;
    private final RequestIdGenerator requestIds;
    private final UserInfoHttpAdapter userInfoAdapter;

    /** 创建个人收款接口。 */
    public CollectionController(CollectionApplicationService service, RequestIdGenerator requestIds) {
        this(service, null, null, requestIds, null);
    }

    /** 创建 C2C Controller，付款能力只通过统一资金交易服务受理。 */
    public CollectionController(CollectionApplicationService service, CollectionPaymentApplicationService paymentService,
                                RequestIdGenerator requestIds) {
        this(service, paymentService, null, requestIds, null);
    }

    /** 创建 C2C Controller，状态流仅消费持久化事件，不直接决定任何资金终态。 */
    @Autowired
    public CollectionController(CollectionApplicationService service, CollectionPaymentApplicationService paymentService,
                                CollectionEventApplicationService eventService, RequestIdGenerator requestIds,
                                UserInfoHttpAdapter userInfoAdapter) {
        this.service = service;
        this.paymentService = paymentService;
        this.eventService = eventService;
        this.requestIds = requestIds;
        this.userInfoAdapter = userInfoAdapter;
    }

    /** 查询本人当前有效个人码；尚未生成时 data 为 null。 */
    @GetMapping("/codes/me")
    public ResponseEntity<ApiResponse<CodeResponse>> getCode(@RequestHeader("X-User-Id") String userId, HttpServletRequest request) {
        PersonalCollectionCode code = service.getActiveCode(userId);
        return ResponseEntity.ok(success(code == null ? null : CodeResponse.from(code, null), request));
    }

    /** 首次生成或原子换发个人码。 */
    @PostMapping("/codes/me/regenerations")
    public ResponseEntity<ApiResponse<CodeResponse>> regenerate(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        CollectionApplicationService.CreatedCode created = service.regenerateCode(userId, key);
        String url = created.rawToken() == null ? null : "/api/v1/p2p-collections/by-token?t=" + created.rawToken();
        return ResponseEntity.ok(success(CodeResponse.from(created.code(), url), request));
    }

    /** 停用本人当前个人码。 */
    @PostMapping("/codes/me/disable")
    public ResponseEntity<ApiResponse<CodeResponse>> disable(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(success(CodeResponse.from(service.disableCode(userId, body.version(), key), null), request));
    }

    /** 创建固定金额收款请求；收款令牌仅在创建响应中可见。 */
    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<RequestResponse>> createRequest(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateRequest body, HttpServletRequest request) {
        CollectionApplicationService.CreatedRequest created = service.createRequest(userId, body.amountFen(), body.subject(), key);
        String url = "/api/v1/p2p-collections/by-token?t=" + created.rawToken();
        return ResponseEntity.status(201).body(success(RequestResponse.from(created.request(), url), request));
    }

    /** 查询本人创建的固定收款请求。 */
    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<RequestResponse>> getRequest(@RequestHeader("X-User-Id") String userId,
            @PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok(success(RequestResponse.from(service.getRequest(userId, id)), request));
    }

    /** 取消尚未有支付受理的固定收款请求。 */
    @PostMapping("/requests/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestResponse>> cancelRequest(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key, @PathVariable String id, @Valid @RequestBody VersionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(success(RequestResponse.from(service.cancelRequest(userId, id, body.version(), key)), request));
    }

    /**
     * 查询固定收款请求的全部来源订单（创建时间倒序），仅请求创建者可读。
     *
     * <p>一码多收下收款方需要实时看到多笔订单及脱敏付款人信息；付款人姓名通过用户中心
     * 查询后脱敏返回，不返回账户、令牌或会话标识。</p>
     */
    @GetMapping("/requests/{id}/orders")
    public ResponseEntity<ApiResponse<java.util.List<RequestOrderResponse>>> getRequestOrders(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, HttpServletRequest request) {
        java.util.List<RequestOrderResponse> orders = service.getRequestOrders(userId, id).stream()
                .map(order -> RequestOrderResponse.from(order, lookupMaskedName(order.getPayeeUserId()),
                        lookupMaskedName(order.getPayerUserId())))
                .toList();
        return ResponseEntity.ok(success(orders, request));
    }

    /** 匿名扫码仅建立无业务数据的 H5 引导会话，令牌不消费。 */
    @GetMapping("/by-token")
    public ResponseEntity<Void> bootstrap(@RequestParam("t") String token, HttpServletRequest request) {
        service.validateBootstrapToken(token);
        request.getSession(true);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    /** 登录付款人在同一引导会话中交换令牌并获得脱敏 C2C 订单。 */
    @PostMapping("/token-exchanges")
    public ResponseEntity<ApiResponse<OrderResponse>> exchange(@RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody TokenExchangeRequest body, HttpServletRequest request) {
        CollectionOrder order = service.exchange(authenticatedUser(userId), sessionId(request), body.token());
        return ResponseEntity.ok(success(OrderResponse.from(order, lookupMaskedName(order.getPayeeUserId()),
                lookupMaskedName(order.getPayerUserId())), request));
    }

    /** 仅个人码订单允许付款人锁定金额和备注。 */
    @PatchMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> lockOrder(@RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id, @Valid @RequestBody LockOrderRequest body, HttpServletRequest request) {
        CollectionOrder order = service.lockPersonalOrder(authenticatedUser(userId), sessionId(request), id,
                body.version(), body.amountFen(), body.subject());
        return ResponseEntity.ok(success(OrderResponse.from(order, lookupMaskedName(order.getPayeeUserId()),
                lookupMaskedName(order.getPayerUserId())), request));
    }

    /** 查询订单；付款人、收款人或绑定 H5 会话可读取脱敏结果。 */
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                                 @PathVariable String id, HttpServletRequest request) {
        String sessionId = request.getSession(false) == null ? null : request.getSession(false).getId();
        CollectionOrder order = service.getOrderForAuthorizedUser(authenticatedUser(userId), sessionId, id);
        return ResponseEntity.ok(success(OrderResponse.from(order, lookupMaskedName(order.getPayeeUserId()),
                lookupMaskedName(order.getPayerUserId())), request));
    }

    /** 查询用户真实姓名并加密显示：只显示第一个字，后面用星号代替。 */
    private String lookupMaskedName(String userId) {
        if (userId == null || userInfoAdapter == null) return "收款人";
        String realName = userInfoAdapter.getRealName(userId);
        if (realName == null || realName.isBlank()) return "收款人";
        return maskName(realName);
    }

    /** 姓名加密：保留第一个字，其余替换为星号。 */
    private static String maskName(String name) {
        if (name == null || name.isEmpty()) return "收款人";
        if (name.length() == 1) return name + "**";
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 为绑定 H5 会话的付款人签发绑定余额或 Mini 花呗资金来源的 C2C 确认令牌。 */
    @PostMapping("/orders/{id}/confirmations")
    public ResponseEntity<ApiResponse<ConfirmationResponse>> confirmation(
            @RequestHeader(value = "X-User-Id", required = false) String userId, @PathVariable String id,
            @Valid @RequestBody ConfirmationRequest body, HttpServletRequest request) {
        CollectionPaymentApplicationService.IssuedConfirmation issued = payment().issueConfirmation(authenticatedUser(userId), id,
                sessionId(request), body.version(), body.paymentProof(), body.fundingSource());
        return ResponseEntity.ok(success(new ConfirmationResponse(issued.confirmationToken(), issued.subjectHash(), issued.expiresAt()), request));
    }

    /** 消费确认令牌并受理唯一的 C2C TRANSFER 主单，不在 HTTP 层决定资金终态。 */
    @PostMapping("/orders/{id}/pay")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @PathVariable String id,
            @Valid @RequestBody PayRequest body, HttpServletRequest request) {
        FundTransaction transaction = payment().pay(authenticatedUser(userId), id, sessionId(request), body.confirmationToken(),
                idempotencyKey, request.getHeader("X-Trace-Id"));
        return ResponseEntity.accepted().body(success(new PaymentResponse(id, transaction.getTransactionId(),
                transaction.getStatus().name(), "/api/v1/p2p-collections/orders/" + id, transaction.getUpdatedAt()), request));
    }

    /**
     * 仅固定请求创建者可订阅可重放状态事件。
     *
     * <p>首次连接发送当前权威状态；携带 {@code Last-Event-ID} 时仅补发后续持久化事件，游标超出保留期统一返回 410。</p>
     */
    @GetMapping(value = "/requests/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter requestEvents(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                    @PathVariable String id) {
        if (eventService == null) throw new IllegalStateException("C2C SSE 服务未配置");
        return eventService.subscribeRequest(authenticatedUser(userId), id, lastEventId);
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }
    private String sessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) throw new com.minialalipay.common.error.BusinessException(com.minialalipay.common.error.CommonErrorCode.UNAUTHORIZED);
        return session.getId();
    }
    private String authenticatedUser(String userId) {
        if (userId == null || userId.isBlank()) throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        return userId;
    }
    private CollectionPaymentApplicationService payment() {
        if (paymentService == null) throw new IllegalStateException("C2C 付款服务未配置");
        return paymentService;
    }

    /** CAS 版本请求。 */
    public record VersionRequest(@Min(0) long version) { }
    /** 固定金额请求体，金额单位为分。 */
    public record CreateRequest(@Min(1) @Max(5_000_000) long amountFen, String subject) { }
    /** 原始收款令牌交换请求；令牌只用于本次请求，不得记录。 */
    /**
     * 原始收款令牌交换请求；令牌只用于本次请求，不得记录。
     *
     * <p>显式拒绝未知字段，防止付款人、付款账户或收款账户等敏感事实绕过服务端派生。</p>
     */
    public static final class TokenExchangeRequest {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 16, max = 256)
        private final String token;

        @JsonCreator
        public TokenExchangeRequest(@JsonProperty("token") String token) { this.token = token; }
        public String token() { return token; }
        @JsonAnySetter
        void rejectUnknownField(String field, Object ignored) { throw new BusinessException(CommonErrorCode.INVALID_REQUEST); }
    }
    /** 个人码订单金额与备注锁定请求。 */
    /**
     * 个人码订单金额与备注锁定请求。
     *
     * <p>仅允许金额、备注和版本，禁止客户端覆盖付款或收款身份及账户。</p>
     */
    public static final class LockOrderRequest {
        @Min(0)
        private final long version;
        @Min(1)
        @Max(5_000_000)
        private final long amountFen;
        @jakarta.validation.constraints.Size(max = 50)
        private final String subject;

        @JsonCreator
        public LockOrderRequest(@JsonProperty("version") long version, @JsonProperty("amountFen") long amountFen,
                                @JsonProperty("subject") String subject) {
            this.version = version;
            this.amountFen = amountFen;
            this.subject = subject;
        }
        public long version() { return version; }
        public long amountFen() { return amountFen; }
        public String subject() { return subject; }
        @JsonAnySetter
        void rejectUnknownField(String field, Object ignored) { throw new BusinessException(CommonErrorCode.INVALID_REQUEST); }
    }
    /** C2C 确认请求，付款账户和资金来源以服务端事实为准。 */
    public record ConfirmationRequest(@Min(0) long version,
                                      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 256) String paymentProof,
                                      @jakarta.validation.constraints.NotNull FundingSource fundingSource) { }
    /** C2C 付款请求，确认令牌不得进入 URL、日志或持久化字段。 */
    public record PayRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 16, max = 256) String confirmationToken) { }
    /** 一次性确认令牌响应。 */
    public record ConfirmationResponse(String confirmationToken, String subjectHash, Instant expiresAt) { }
    /** 资金受理响应；PROCESSING 不代表资金已成功。 */
    public record PaymentResponse(String collectionOrderId, String transactionId, String status, String statusUrl, Instant updatedAt) { }
    /** 脱敏个人码响应。 */
    public record CodeResponse(String codeId, String status, String collectionUrl, long version) {
        static CodeResponse from(PersonalCollectionCode code, String url) { return new CodeResponse(code.getCodeId(), code.getStatus().name(), url, code.getVersion()); }
    }
    /** 固定收款请求响应；collectionUrl 仅创建响应携带，令牌服务端只存摘要不可重读。 */
    public record RequestResponse(String requestId, long amountFen, String subject, String status, String activeOrderId,
                                  String transactionId, Instant expiresAt, long version, String collectionUrl) {
        static RequestResponse from(CollectionRequest request) { return from(request, null); }
        static RequestResponse from(CollectionRequest request, String collectionUrl) {
            return new RequestResponse(request.getRequestId(), request.getAmountFen(), request.getSubject(),
                    request.getStatus().name(), request.getActiveOrderId(), null, request.getExpiresAt(), request.getVersion(), collectionUrl);
        }
    }
    /** C2C 订单响应，禁止返回双方账户、原始令牌和会话标识。 */
    public record OrderResponse(String collectionOrderId, String kind, Long amountFen, String subject, String status,
                                String transactionId, Instant expiresAt, long version,
                                String payeeName, String payerName, boolean editable) {
        static OrderResponse from(CollectionOrder order, String payeeName, String payerName) {
            String kind = order.getPersonalCodeId() == null ? "FIXED_REQUEST" : "PERSONAL_QR";
            boolean editable = "PERSONAL_QR".equals(kind) && "DRAFT".equals(order.getStatus().name());
            return new OrderResponse(order.getOrderId(), kind,
                    order.getAmountFen(), order.getSubject(), order.getStatus().name(), order.getTransactionId(),
                    order.getExpiresAt(), order.getVersion(), payeeName, payerName, editable);
        }
    }
    /**
     * 收款方订单列表条目：在订单响应基础上补充创建时间，供收款记录按时间展示；
     * 付款人、收款人姓名均已脱敏，不返回账户、令牌或会话标识。
     */
    public record RequestOrderResponse(String collectionOrderId, String kind, Long amountFen, String subject, String status,
                                       String transactionId, Instant expiresAt, long version,
                                       String payeeName, String payerName, Instant createdAt) {
        static RequestOrderResponse from(CollectionOrder order, String payeeName, String payerName) {
            String kind = order.getPersonalCodeId() == null ? "FIXED_REQUEST" : "PERSONAL_QR";
            return new RequestOrderResponse(order.getOrderId(), kind, order.getAmountFen(), order.getSubject(),
                    order.getStatus().name(), order.getTransactionId(), order.getExpiresAt(), order.getVersion(),
                    payeeName, payerName, order.getCreatedAt());
        }
    }
}
