package com.minialalipay.account.interfaces.account;

import com.minialalipay.account.application.account.AccountApplicationService;
import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.account.application.ledger.LedgerApplicationService;
import com.minialalipay.account.application.ledger.dto.LedgerEntryPageDTO;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * C 端本人账户与账本明细只读接口。
 *
 * <p>两个端点都要求有效登录会话，用户 ID 只能来自网关可信请求头，不接受客户端提交的账户 ID。
 * 查询无副作用、无需幂等键，事务边界位于应用服务；账户不存在返回 404，分页参数错误返回 400，
 * 未分类依赖异常由统一异常处理器转换为 500。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountController {

    private final AccountApplicationService accountApplicationService;
    private final LedgerApplicationService ledgerApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public AccountController(AccountApplicationService accountApplicationService,
                             LedgerApplicationService ledgerApplicationService,
                             RequestIdGenerator requestIdGenerator) {
        this.accountApplicationService = accountApplicationService;
        this.ledgerApplicationService = ledgerApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 查询当前登录用户的账户和实时余额。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param request HTTP 请求上下文
     * @return 统一账户摘要响应
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AccountSummaryDTO>> getMyAccount(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request
    ) {
        AccountSummaryDTO data = accountApplicationService.getMyAccount(userId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询当前登录用户拥有科目的不可变账本明细。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cursor 上一页返回的不透明复合游标，首页不传
     * @param limit 单页数量，1 至 100
     * @param request HTTP 请求上下文
     * @return 统一游标分页响应
     */
    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<LedgerEntryPageDTO>> listMyEntries(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request
    ) {
        LedgerEntryPageDTO data = ledgerApplicationService.listMyEntries(userId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    private String requestId(HttpServletRequest request) {
        return requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
    }
}
