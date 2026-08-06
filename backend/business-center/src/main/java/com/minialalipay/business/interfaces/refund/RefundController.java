package com.minialalipay.business.interfaces.refund;

import com.minialalipay.business.application.refund.RefundApplicationService;
import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 受控退款来源订单 API。
 *
 * <p>登录用户只能对本人作为收款方的已成功动态扫码交易创建退款并查询本人订单；
 * 写操作要求 {@code X-Request-Id} 与 {@code Idempotency-Key}。受理只表示进入 REFUND 统一交易，
 * 不代表冲正成功，资金事实由统一交易与账户中心 TCC 决定。</p>
 */
@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {
    private final RefundApplicationService service;
    private final RequestIdGenerator requestIds;

    /** 创建退款 Controller。 */
    public RefundController(RefundApplicationService service, RequestIdGenerator requestIds) {
        this.service = service;
        this.requestIds = requestIds;
    }

    /** 对本人已成功动态扫码交易创建受控退款订单；同一幂等键同参返回既有订单。 */
    @PostMapping
    public ResponseEntity<ApiResponse<RefundOrderResponse>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest body,
            HttpServletRequest request) {
        RefundOrder order = service.create(userId, body.originalTransactionId(), body.reasonCode(), idempotencyKey);
        return ResponseEntity.accepted().body(success(RefundOrderResponse.from(order), request));
    }

    /** 查询退款发起人本人的退款订单列表。 */
    @GetMapping
    public ResponseEntity<ApiResponse<RefundOrderPageResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        List<RefundOrderResponse> items = service.list(userId, status, limit).stream()
                .map(RefundOrderResponse::from).toList();
        return ResponseEntity.ok(success(new RefundOrderPageResponse(items, null), request));
    }

    /** 查询退款发起人本人的退款来源订单。 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RefundOrderResponse>> get(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok(success(RefundOrderResponse.from(service.get(userId, id)), request));
    }

    /** 提交执行退款，受理唯一 REFUND 统一交易并启动 TCC。 */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<RefundOrderResponse>> submit(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id, @Valid @RequestBody SubmitRefundRequest body, HttpServletRequest request) {
        RefundOrder order = service.submit(userId, id, body.version(), idempotencyKey,
                request.getHeader("X-Trace-Id"));
        return ResponseEntity.accepted().body(success(RefundOrderResponse.from(order), request));
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }

    /** 创建退款订单请求。 */
    public record CreateRefundRequest(@NotBlank @Size(max = 26) String originalTransactionId,
                                      @NotBlank @Size(max = 32) String reasonCode) { }

    /** 提交执行退款请求；version 为客户端读取到的订单 CAS 版本。 */
    public record SubmitRefundRequest(@Min(0) long version) { }

    /** 退款订单对外 DTO，不暴露资金事实或完整账户标识。 */
    public record RefundOrderResponse(String refundOrderId, String originalTransactionId, long amountFen,
                                      String status, String transactionId, long version, Instant createdAt) {
        static RefundOrderResponse from(RefundOrder order) {
            return new RefundOrderResponse(order.getRefundOrderId(), order.getOriginalTransactionId(),
                    order.getAmountFen(), order.getStatus().name(), order.getTransactionId(), order.getVersion(),
                    order.getCreatedAt());
        }
    }

    /** 退款订单单页；当前无游标时 nextCursor 为 null。 */
    public record RefundOrderPageResponse(List<RefundOrderResponse> items, String nextCursor) { }
}
