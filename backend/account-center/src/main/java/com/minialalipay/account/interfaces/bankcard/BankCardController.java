package com.minialalipay.account.interfaces.bankcard;

import com.minialalipay.account.application.bankcard.BankCardApplicationService;
import com.minialalipay.account.application.bankcard.dto.BankCardDTO;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端银行卡管理接口：列表、绑卡、详情、设默认、解绑。
 *
 * <p>所有端点要求有效登录会话，用户 ID 只能来自网关可信请求头 X-User-Id；
 * 资源归属校验在应用服务完成，访问他人卡片统一返回银行卡不存在。
 * 绑卡请求包含卡号与四要素明文，禁止记录请求体日志；
 * 响应统一 ApiResponse 包装并透传 X-Request-Id / X-Trace-Id。</p>
 */
@RestController
@RequestMapping("/api/v1/bank-cards")
public class BankCardController {

    private final BankCardApplicationService bankCardApplicationService;
    private final RequestIdGenerator requestIdGenerator;

    public BankCardController(BankCardApplicationService bankCardApplicationService,
                              RequestIdGenerator requestIdGenerator) {
        this.bankCardApplicationService = bankCardApplicationService;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 查询本人银行卡列表（仅 ACTIVE，默认卡在前）。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param request HTTP 请求上下文
     * @return 掩码卡片列表响应
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BankCardDTO>>> listMyCards(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest request) {
        List<BankCardDTO> data = bankCardApplicationService.listMyCards(userId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 绑定银行卡：卡号 Luhn 校验、BIN 识别与四要素格式校验（模拟）通过后绑定。
     *
     * <p>请求体含敏感明文，不得记录日志；重复绑同一张卡返回 409 已绑定，
     * 首张卡自动设为默认卡。绑卡由「BIN+尾号已绑定」校验天然防重，无需幂等键；
     * 解绑后的卡可凭注册时获得的完整卡号与三要素重新绑定。</p>
     *
     * @param userId 网关从会话解析的用户 ID
     * @param body 绑卡请求（卡号、姓名、身份证号、手机号明文）
     * @param request HTTP 请求上下文
     * @return 新绑定卡片的掩码视图
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BankCardDTO>> bindCard(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody BindBankCardRequest body,
            HttpServletRequest request) {
        BankCardDTO data = bankCardApplicationService.bindCard(
                userId, body.cardNumber(), body.holderName(), body.idCard(), body.phone());
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查询银行卡详情（全掩码字段）。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param request HTTP 请求上下文
     * @return 掩码卡片详情；不属于本人或不存在返回 404
     */
    @GetMapping("/{cardId}")
    public ResponseEntity<ApiResponse<BankCardDTO>> getCard(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String cardId,
            HttpServletRequest request) {
        BankCardDTO data = bankCardApplicationService.getCard(userId, cardId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 设为默认卡：事务内先清旧默认再置新；已是默认卡时幂等返回。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param request HTTP 请求上下文
     * @return 更新后的卡片视图
     */
    @PutMapping("/{cardId}/default")
    public ResponseEntity<ApiResponse<BankCardDTO>> setDefault(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String cardId,
            HttpServletRequest request) {
        BankCardDTO data = bankCardApplicationService.setDefault(userId, cardId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 解绑银行卡（软删，状态置为 UNBOUND 终态），同时释放对应注册记录使其可重新绑定；
     * 解绑默认卡后递补最早活动卡为默认。
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param request HTTP 请求上下文
     * @return 空响应体
     */
    @DeleteMapping("/{cardId}")
    public ResponseEntity<ApiResponse<Void>> unbind(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String cardId,
            HttpServletRequest request) {
        bankCardApplicationService.unbind(userId, cardId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId(request), request.getHeader("X-Trace-Id")));
    }

    /**
     * 查看本人银行卡完整卡号：需先以 BANK_CARD_NUMBER_VIEW 用途验密签发一次性支付证明，
     * 再凭证明换取明文；证明校验成功后即被消费，不可重放。
     *
     * <p>权限：有效登录会话且卡片属于本人；幂等：证明一次性消费，重复请求需重新验密；
     * 事务边界在应用服务；主要异常：卡片不存在（404）、证明无效（422）、注册记录缺失（404）。
     * 响应体含卡号明文，禁止记录响应日志；前端仅内存展示，不落存储。</p>
     *
     * @param userId 网关从会话解析的用户 ID
     * @param cardId 银行卡 ID
     * @param body 含一次性支付证明的请求体，证明不得进入 URL 与日志
     * @param request HTTP 请求上下文
     * @return 完整卡号明文响应
     */
    @PostMapping("/{cardId}/full-card-number")
    public ResponseEntity<ApiResponse<FullCardNumberResponse>> getFullCardNumber(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String cardId,
            @Valid @RequestBody FullCardNumberRequest body,
            HttpServletRequest request) {
        String fullCardNumber = bankCardApplicationService.getFullCardNumber(userId, cardId, body.paymentProof());
        return ResponseEntity.ok(ApiResponse.success(
                new FullCardNumberResponse(fullCardNumber), requestId(request), request.getHeader("X-Trace-Id")));
    }

    private String requestId(HttpServletRequest request) {
        return requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
    }

    /** 完整卡号响应；卡号明文属敏感数据，仅本次响应可见，禁止落日志与存储。 */
    public record FullCardNumberResponse(String fullCardNumber) { }
}
