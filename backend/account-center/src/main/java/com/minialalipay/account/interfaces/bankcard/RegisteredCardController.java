package com.minialalipay.account.interfaces.bankcard;

import com.minialalipay.account.application.bankcard.RegisteredCardApplicationService;
import com.minialalipay.account.application.bankcard.dto.RegisteredCardDTO;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端银行卡注册接口：注册银行卡（自动生成卡号）、查询已注册但未绑定的卡列表。
 *
 * <p>注册时根据用户选择的银行自动生成合法卡号（BIN + 随机数字 + Luhn 校验位），
 * 完整卡号仅在注册响应中返回一次，前端提示用户记住卡号后去绑定。</p>
 */
@RestController
@RequestMapping("/api/v1/bank-card-registrations")
public class RegisteredCardController {

    private final RegisteredCardApplicationService registeredCardApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public RegisteredCardController(RegisteredCardApplicationService registeredCardApplicationService,
                                    RequestIdGenerator requestIdGenerator) {
        this.registeredCardApplicationService = registeredCardApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 注册银行卡：根据银行编码自动生成卡号，保存三要素哈希。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param body 注册请求（银行编码、姓名、身份证号、手机号）
     * @param request HTTP 请求上下文
     * @return 注册结果（包含完整卡号，仅此次返回）
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RegisteredCardDTO>> registerCard(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody RegisterBankCardRequest body,
            HttpServletRequest request) {
        RegisteredCardDTO data = registeredCardApplicationService.registerCard(
                userId, body.bankCode(), body.holderName(), body.idCard(), body.phone());
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询本人已注册但未绑定的卡列表（不返回完整卡号）。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param request HTTP 请求上下文
     * @return 已注册卡列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RegisteredCardDTO>>> listRegisteredCards(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request) {
        List<RegisteredCardDTO> data = registeredCardApplicationService.listRegisteredCards(userId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    private String requestId(HttpServletRequest request) {
        return requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
    }
}
