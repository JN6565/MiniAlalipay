package com.minialalipay.account.interfaces.reconciliation;

import com.minialalipay.account.application.reconciliation.TransactionFactApplicationService;
import com.minialalipay.account.application.reconciliation.TransactionFactApplicationService.TransactionFacts;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供业务中心终态发布器调用的版本化资金与账本事实接口。 */
@RestController
@Validated
@RequestMapping("/internal/v1/transaction-facts")
public class TransactionFactController {
    private final TransactionFactApplicationService service;
    public TransactionFactController(TransactionFactApplicationService service) { this.service = service; }
    /**
     * 返回脱敏的一致性布尔事实，不返回账户余额和完整账号。
     *
     * <p>仅限业务中心终态发布器调用，应用服务使用只读事务汇总分支、冻结和账本事实；
     * GET 请求可安全重试。参数格式错误返回 400，依赖查询失败返回统一内部错误。</p>
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionFacts> inspect(
            @PathVariable @Size(min = 26, max = 26)
            @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId) {
        return ResponseEntity.ok(service.inspect(transactionId));
    }
}
