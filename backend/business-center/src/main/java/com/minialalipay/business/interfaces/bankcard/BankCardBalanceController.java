package com.minialalipay.business.interfaces.bankcard;

import com.minialalipay.business.application.bankcard.BankCardRechargeApplicationService;
import com.minialalipay.business.application.bankcard.BankCardWithdrawApplicationService;
import com.minialalipay.business.application.port.BankCardPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端银行卡充值/提现接口。
 *
 * <p>所有端点要求有效登录会话，用户 ID 来自网关可信请求头 X-User-Id；
 * 支付密码明文只透传用户中心验密，不落日志、URL 或持久化。</p>
 */
@RestController
@Validated
@RequestMapping("/api/v1/bank-cards")
public class BankCardBalanceController {

    private final BankCardRechargeApplicationService rechargeService;
    private final BankCardWithdrawApplicationService withdrawService;
    private final BankCardPort bankCards;
    private final BusinessStore store;
    private final RequestIdGenerator requestIdGenerator;

    public BankCardBalanceController(BankCardRechargeApplicationService rechargeService,
                                     BankCardWithdrawApplicationService withdrawService,
                                     BankCardPort bankCards,
                                     BusinessStore store,
                                     RequestIdGenerator requestIdGenerator) {
        this.rechargeService = rechargeService;
        this.withdrawService = withdrawService;
        this.bankCards = bankCards;
        this.store = store;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 发起银行卡充值：银行卡虚拟余额减少，账户余额增加。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param body 充值请求（金额、支付密码、幂等键）
     * @param request HTTP 请求
     * @return 交易摘要
     */
    @PostMapping("/{cardId}/recharge")
    public ResponseEntity<ApiResponse<TransactionResult>> recharge(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable @NotBlank @Size(max = 128) String cardId,
            @Valid @RequestBody RechargeRequest body,
            HttpServletRequest request) {
        FundTransaction tx = rechargeService.recharge(userId, cardId, body.amountFen(),
                body.paymentPassword(), body.idempotencyKey());
        return ResponseEntity.ok(ApiResponse.success(
                new TransactionResult(tx.getTransactionId(), tx.getStatus().name()),
                requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 发起银行卡提现：账户余额减少，银行卡虚拟余额增加。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param body 提现请求（金额、支付密码、幂等键）
     * @param request HTTP 请求
     * @return 交易摘要
     */
    @PostMapping("/{cardId}/withdraw")
    public ResponseEntity<ApiResponse<TransactionResult>> withdraw(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable @NotBlank @Size(max = 128) String cardId,
            @Valid @RequestBody WithdrawRequest body,
            HttpServletRequest request) {
        FundTransaction tx = withdrawService.withdraw(userId, cardId, body.amountFen(),
                body.paymentPassword(), body.idempotencyKey());
        return ResponseEntity.ok(ApiResponse.success(
                new TransactionResult(tx.getTransactionId(), tx.getStatus().name()),
                requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询银行卡虚拟余额。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param request HTTP 请求
     * @return 余额（分）
     */
    @GetMapping("/{cardId}/balance")
    public ResponseEntity<ApiResponse<BalanceResult>> getBalance(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable @NotBlank @Size(max = 128) String cardId,
            HttpServletRequest request) {
        long balanceFen = bankCards.getBalanceFen(userId, cardId);
        return ResponseEntity.ok(ApiResponse.success(
                new BalanceResult(cardId, balanceFen),
                requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询银行卡交易明细（充值/提现历史），按创建时间倒序。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param limit  最大返回条数，默认 20，最大 50
     * @param request HTTP 请求
     * @return 交易明细列表
     */
    @GetMapping("/{cardId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionHistoryItem>>> getTransactions(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable @NotBlank @Size(max = 128) String cardId,
            @RequestParam(defaultValue = "20") @Positive int limit,
            HttpServletRequest request) {
        // 校验银行卡归属
        bankCards.requireCard(userId, cardId);
        int maxLimit = Math.min(limit, 50);
        List<BusinessStore.FundTransactionRecord> records = store.findBankCardTransactions(userId, cardId, maxLimit);
        List<TransactionHistoryItem> items = records.stream()
                .map(r -> TransactionHistoryItem.from(r.transaction()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(items,
                requestId(request), request.getHeader("X-Trace-Id")));
    }

    private String requestId(HttpServletRequest request) {
        return requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
    }

    /** 充值请求体；支付密码属敏感字段，禁止进入日志或 URL。 */
    public record RechargeRequest(@Positive long amountFen,
                                  @NotBlank @Pattern(regexp = "^\\d{6}$") String paymentPassword,
                                  @NotBlank @Size(max = 64) String idempotencyKey) { }

    /** 提现请求体；支付密码属敏感字段，禁止进入日志或 URL。 */
    public record WithdrawRequest(@Positive long amountFen,
                                  @NotBlank @Pattern(regexp = "^\\d{6}$") String paymentPassword,
                                  @NotBlank @Size(max = 64) String idempotencyKey) { }

    /** 交易摘要响应。 */
    public record TransactionResult(String transactionId, String status) { }

    /** 余额查询响应。 */
    public record BalanceResult(String cardId, long balanceFen) { }

    /**
     * 银行卡交易明细项。
     *
     * <p>包含交易 ID、业务类型（充值/提现）、金额、状态和创建时间。
     * 充值表示银行卡给账户充钱，提现表示账户余额转到银行卡。</p>
     */
    public record TransactionHistoryItem(String transactionId, String businessType, long amountFen,
                                          String status, String createdAt) {
        /** 从领域对象转换为响应 DTO。 */
        static TransactionHistoryItem from(FundTransaction tx) {
            return new TransactionHistoryItem(
                    tx.getTransactionId(), tx.getBusinessType().name(),
                    tx.getAmountFen(), tx.getStatus().name(),
                    tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
        }
    }
}
