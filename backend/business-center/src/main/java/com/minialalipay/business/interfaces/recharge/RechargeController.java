package com.minialalipay.business.interfaces.recharge;

import com.minialalipay.business.application.recharge.RechargeApplicationService;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 受控模拟充值订单 API。
 *
 * <p>登录用户只能创建和查询本人订单；写操作要求 {@code X-Request-Id} 与 {@code Idempotency-Key}。
 * 当前接口只返回待渠道状态，不代表账户入账或充值成功，也不调用统一交易和 TCC。</p>
 */
@RestController
@RequestMapping("/api/v1/recharges")
public class RechargeController {
    private final RechargeApplicationService service;
    private final RequestIdGenerator requestIds;

    /** 创建充值 Controller。 */
    public RechargeController(RechargeApplicationService service, RequestIdGenerator requestIds) {
        this.service = service;
        this.requestIds = requestIds;
    }

    /** 创建待渠道处理的模拟充值订单；同一幂等键同参返回既有订单。 */
    @PostMapping
    public ResponseEntity<ApiResponse<RechargeOrderResponse>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRechargeRequest body,
            HttpServletRequest request) {
        RechargeOrder order = service.create(userId, body.amountFen(), idempotencyKey);
        return ResponseEntity.accepted().body(success(RechargeOrderResponse.from(order), request));
    }

    /** 查询当前登录用户自己的充值来源订单。 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RechargeOrderResponse>> get(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok(success(RechargeOrderResponse.from(service.get(userId, id)), request));
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }

    /** 模拟充值创建请求，金额单位为分。 */
    public record CreateRechargeRequest(@Min(1) @Max(5_000_000) long amountFen) { }

    /** 充值订单对外 DTO，不暴露策略内部限额或任何账户资金事实。 */
    public record RechargeOrderResponse(String rechargeOrderId, long amountFen, String status, long version,
                                        Instant createdAt) {
        static RechargeOrderResponse from(RechargeOrder order) {
            return new RechargeOrderResponse(order.getRechargeOrderId(), order.getAmountFen(), order.getStatus().name(),
                    order.getVersion(), order.getCreatedAt());
        }
    }
}
