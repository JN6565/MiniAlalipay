package com.minialalipay.business.interfaces.recharge;

import com.minialalipay.business.application.recharge.RechargeApplicationService;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受控模拟渠道结果的内部回调接口。
 *
 * <p>仅服务间鉴权调用，不在网关注册路由。渠道成功时推进充值订单并创建 {@code RECHARGE} 统一交易；
 * 接口幂等，重复回调返回同一受理事实。当前账户中心尚未提供充值 TCC 参与者，成功入账依赖后续资金内核交付。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/recharges")
public class InternalRechargeController {
    private final RechargeApplicationService service;

    /** 创建充值渠道回调 Controller。 */
    public InternalRechargeController(RechargeApplicationService service) {
        this.service = service;
    }

    /**
     * 记录模拟渠道结果。
     *
     * @param id 充值订单号
     * @param body 渠道结果；成功时必须拒绝原因码为空
     * @return 更新后的充值订单引用，不返回余额或敏感账户数据
     */
    @PostMapping("/{id}/channel-result")
    public ResponseEntity<RechargeOrderReference> channelResult(
            @PathVariable @Size(min = 26, max = 26) String id,
            @Valid @RequestBody ChannelResultRequest body) {
        RechargeOrder order = service.onChannelResult(id, body.success(), body.rejectReasonCode(), body.traceId());
        return ResponseEntity.ok(new RechargeOrderReference(order.getRechargeOrderId(), order.getUserId(),
                order.getAmountFen(), order.getStatus().name(), order.getTransactionId(), order.getVersion()));
    }

    /** 渠道结果请求；success 为 true 时忽略拒绝原因码。 */
    public record ChannelResultRequest(@NotNull Boolean success,
                                       @Size(max = 64) String rejectReasonCode,
                                       @Size(max = 32) String traceId) { }

    /** 渠道回调返回的脱敏订单引用。 */
    public record RechargeOrderReference(String rechargeOrderId, String userId, long amountFen,
                                         String status, String transactionId, long version) { }
}
