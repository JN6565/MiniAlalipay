package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供业务中心解析用户个人账户的版本化内部接口，不接受客户端账户归属。 */
@RestController
@RequestMapping("/internal/v1/accounts")
public class InternalAccountDirectoryController {
    private final AccountRepository accountRepository;
    public InternalAccountDirectoryController(AccountRepository accountRepository) { this.accountRepository = accountRepository; }

    /** 按用户 ID 返回其唯一 CNY 个人账户；不存在时返回统一 404。 */
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<AccountReference> findByUser(@PathVariable String userId) {
        var account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return ResponseEntity.ok(new AccountReference(account.getAccountId(), account.getUserId(),
                account.getStatus().name()));
    }

    /** 跨服务账户只读引用，不暴露余额或持久化对象。 */
    public record AccountReference(String accountId, String userId, String status) { }
}
