package com.minialalipay.business.interfaces.transfer;

import com.minialalipay.business.application.transfer.TransferApplicationService;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transfer.TransferDraft;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 普通转账草稿、确认、提交和查询 API。
 *
 * <p>所有接口要求网关注入可信 `X-User-Id`。写接口按契约使用幂等键或版本 CAS；
 * 原始确认令牌只允许出现在确认响应和提交请求体，不写日志、URL 或持久化字段。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class TransferController {
    private final TransferApplicationService service;
    private final RequestIdGenerator requestIds;
    public TransferController(TransferApplicationService service, RequestIdGenerator requestIds) {
        this.service = service; this.requestIds = requestIds;
    }

    /** 创建转账草稿，双方账户均由服务端按用户身份解析。 */
    @PostMapping("/transfer-drafts")
    public ResponseEntity<ApiResponse<DraftResponse>> createDraft(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateDraftRequest body, HttpServletRequest request) {
        return ok(DraftResponse.from(service.createDraft(userId, body.payeeUserId(), body.amountFen(), body.remark(), key)), request);
    }

    /** 查询本人草稿，非本人按不存在处理。 */
    @GetMapping("/transfer-drafts/{id}")
    public ResponseEntity<ApiResponse<DraftResponse>> getDraft(@RequestHeader("X-User-Id") String userId,
            @PathVariable String id, HttpServletRequest request) {
        return ok(DraftResponse.from(service.getDraft(userId, id)), request);
    }

    /** 使用客户端读取版本 CAS 编辑草稿。 */
    @PatchMapping("/transfer-drafts/{id}")
    public ResponseEntity<ApiResponse<DraftResponse>> editDraft(@RequestHeader("X-User-Id") String userId,
            @PathVariable String id, @Valid @RequestBody EditDraftRequest body, HttpServletRequest request) {
        return ok(DraftResponse.from(service.editDraft(userId, id, body.version(), body.amountFen(), body.remark())), request);
    }

    /** 校验草稿但不修改任何余额。 */
    @PostMapping("/transfer-drafts/{id}/validate")
    public ResponseEntity<ApiResponse<TransferApplicationService.ValidationResult>> validateDraft(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id,
            @Valid @RequestBody VersionRequest body, HttpServletRequest request) {
        return ok(service.validateDraft(userId, id, body.version()), request);
    }

    /** 使用用户中心的一次性支付证明签发确认令牌。 */
    @PostMapping("/confirmations")
    public ResponseEntity<ApiResponse<TransferApplicationService.IssuedConfirmation>> confirm(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ConfirmationRequest body, HttpServletRequest request) {
        if (!"TRANSFER_DRAFT".equals(body.subjectType())) throw new IllegalArgumentException("不支持的确认主体类型");
        return ok(service.issueConfirmation(userId, body.subjectId(), body.subjectVersion(), body.paymentProof()), request);
    }

    /** 原子受理转账并在事务提交后启动 TCC，初次响应通常为 PROCESSING。 */
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<TransactionResponse>> submit(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SubmitTransferRequest body,
            HttpServletRequest request) {
        FundTransaction value = service.submit(userId, body.draftId(), body.confirmationToken(), key,
                request.getHeader("X-Trace-Id"));
        return ResponseEntity.accepted().body(success(
                TransactionResponse.from(service.getTransactionDetail(userId, value.getTransactionId())), request));
    }

    /** 查询付款人或收款人本人参与的转账权威状态，无关用户按不存在处理。 */
    @GetMapping("/transfers/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@RequestHeader("X-User-Id") String userId,
            @PathVariable String id, HttpServletRequest request) {
        return ok(TransactionResponse.from(service.getTransactionDetail(userId, id)), request);
    }

    /** 查询确定终态回执；在途或人工复核返回 RECEIPT_NOT_READY。 */
    @GetMapping("/transfers/{id}/receipt")
    public ResponseEntity<ApiResponse<TransferApplicationService.TransferReceipt>> getReceipt(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, HttpServletRequest request) {
        return ok(service.getReceipt(userId, id), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, HttpServletRequest request) {
        return ResponseEntity.ok(success(data, request));
    }
    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }

    /** 创建草稿请求，金额单位为分。 */
    public record CreateDraftRequest(@NotBlank String payeeUserId,
                                     @Min(1) @Max(5_000_000) long amountFen,
                                     @Size(max = 128) String remark) { }
    /** 编辑草稿请求，version 为读取草稿时的 CAS 版本。 */
    public record EditDraftRequest(@Min(0) long version, @Min(1) @Max(5_000_000) long amountFen,
                                   @Size(max = 128) String remark) { }
    /** 草稿版本请求。 */
    public record VersionRequest(@Min(0) long version) { }
    /** 签发确认请求，paymentProof 是用户中心签发的一次性短期证明。 */
    public record ConfirmationRequest(@NotBlank String subjectType, @NotBlank String subjectId,
                                      @Min(0) long subjectVersion, @NotBlank String paymentProof) { }
    /** 提交转账请求，确认令牌不得进入 URL 或日志。 */
    public record SubmitTransferRequest(@NotBlank String draftId, @NotBlank String confirmationToken) { }

    /** 转账草稿 API DTO，不暴露内部持久化对象。 */
    public record DraftResponse(String draftId, String payeeUserId, long amountFen, String remark,
                                String status, long version, Instant expiresAt) {
        static DraftResponse from(TransferDraft d) {
            return new DraftResponse(d.getDraftId(), d.getPayeeUserId(), d.getAmountFen(), d.getRemark(),
                    d.getStatus().name(), d.getVersion(), d.getExpiresAt());
        }
    }
    /** 普通转账详情 DTO，包含付款人、收款人和来源草稿中的不可变展示信息。 */
    public record TransactionResponse(String transactionId, String businessType, String status,
                                      long amountFen, String payerUserId, String payerDisplayName,
                                      String payeeUserId, String payeeDisplayName, String remark,
                                      String statusUrl, Instant createdAt, Instant updatedAt) {
        static TransactionResponse from(TransferApplicationService.TransferDetail detail) {
            FundTransaction t = detail.transaction();
            return new TransactionResponse(t.getTransactionId(), t.getBusinessType().name(), t.getStatus().name(),
                    t.getAmountFen(), detail.payerUserId(), detail.payerDisplayName(), detail.payeeUserId(),
                    detail.payeeDisplayName(), detail.remark(), "/api/v1/transfers/" + t.getTransactionId(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
