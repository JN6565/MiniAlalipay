package com.minialalipay.business.interfaces.qrpay;

import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import com.minialalipay.business.application.qrpay.QrCollectionAnalyticsService;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端本人动态扫码收款统计接口。
 *
 * <p>只返回本人作为收款方的确定终态收款摘要，金额统一为整数分；
 * 页面不得据此自行推断交易终态。</p>
 */
@RestController
@RequestMapping("/api/v1/qr-pay/me")
public class QrCollectionAnalyticsController {
    private final QrCollectionAnalyticsService service;
    private final RequestIdGenerator requestIds;

    /** 创建收款统计接口。 */
    public QrCollectionAnalyticsController(QrCollectionAnalyticsService service, RequestIdGenerator requestIds) {
        this.service = service;
        this.requestIds = requestIds;
    }

    /** 查询本人动态扫码收款、订单、支付方式、退款和净收款摘要。 */
    @GetMapping("/qr-collection-analytics")
    public ResponseEntity<ApiResponse<QrCollectionAnalyticsResponse>> analytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "month") String range,
            HttpServletRequest request) {
        PayeeAnalyticsStore.PayeeAnalytics result = service.analytics(userId, parseRange(range));
        return ResponseEntity.ok(ApiResponse.success(QrCollectionAnalyticsResponse.from(range, result),
                requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id")));
    }

    /** 校验统计范围，非法范围返回契约错误码。 */
    private static QrCollectionAnalyticsService.Range parseRange(String range) {
        return switch (range) {
            case "today" -> QrCollectionAnalyticsService.Range.TODAY;
            case "month" -> QrCollectionAnalyticsService.Range.MONTH;
            default -> throw new BusinessException(BusinessErrorCode.RANGE_NOT_SUPPORTED);
        };
    }

    /** 收款统计响应；金额全部为整数分，时间与 OpenAPI date-time 一致。 */
    public record QrCollectionAnalyticsResponse(long orderCount, long transactionCount, long grossAmountFen,
                                                long refundAmountFen, long netAmountFen, String range,
                                                List<PaymentMethodStatResponse> byPaymentMethod,
                                                String since, String now) {

        /** 支付方式统计响应。 */
        public record PaymentMethodStatResponse(String businessType, long orderCount, long amountFen) { }

        static QrCollectionAnalyticsResponse from(String range, PayeeAnalyticsStore.PayeeAnalytics result) {
            List<PaymentMethodStatResponse> methods = result.byPaymentMethod().stream()
                    .map(stat -> new PaymentMethodStatResponse(stat.businessType(), stat.orderCount(), stat.amountFen()))
                    .toList();
            return new QrCollectionAnalyticsResponse(result.orderCount(), result.transactionCount(),
                    result.grossAmountFen(), result.refundAmountFen(), result.netAmountFen(), range, methods,
                    result.since().toString(), result.now().toString());
        }
    }
}
