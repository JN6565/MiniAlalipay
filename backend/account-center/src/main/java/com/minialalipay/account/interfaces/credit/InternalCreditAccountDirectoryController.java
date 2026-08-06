package com.minialalipay.account.interfaces.credit;

import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向业务中心提供信用账户版本化只读引用的内部接口。
 *
 * <p>接口不返回额度数值，业务中心只能将该引用绑定到确认摘要和信用 TCC 分支，额度冻结及账务事实始终由账户中心执行。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/credit-accounts")
public class InternalCreditAccountDirectoryController {
    private final CreditAccountRepository creditAccounts;

    /** 创建信用账户内部目录 Controller。 */
    public InternalCreditAccountDirectoryController(CreditAccountRepository creditAccounts) {
        this.creditAccounts = creditAccounts;
    }

    /**
     * 按用户读取信用账户引用。
     *
     * <p>仅服务间鉴权调用；只读且可安全重试。不存在的信用账户统一返回 404，调用方不得据此创建或修改账户。</p>
     */
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<CreditAccountReference> findByUser(
            @PathVariable @Size(min = 26, max = 26)
            @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String userId) {
        var account = creditAccounts.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return ResponseEntity.ok(new CreditAccountReference(account.getCreditAccountId(), account.getUserId(),
                account.getStatus().name(), account.getVersion()));
    }

    /** 信用账户只读引用，不携带可用额度、已用额度、冻结额度或账务数据。 */
    public record CreditAccountReference(String creditAccountId, String userId, String status, long version) { }
}
