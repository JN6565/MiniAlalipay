package com.minialalipay.account.interfaces.reconciliation;

import com.minialalipay.account.application.reconciliation.TransactionFactApplicationService;
import com.minialalipay.account.application.reconciliation.TransactionFactApplicationService.TransactionFacts;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供业务中心终态发布器调用的版本化资金与账本事实接口。 */
@RestController
@RequestMapping("/internal/v1/transaction-facts")
public class TransactionFactController {
    private final TransactionFactApplicationService service;
    public TransactionFactController(TransactionFactApplicationService service) { this.service = service; }
    /** 返回脱敏的一致性布尔事实，不返回账户余额和完整账号。 */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionFacts> inspect(@PathVariable String transactionId) {
        return ResponseEntity.ok(service.inspect(transactionId));
    }
}
