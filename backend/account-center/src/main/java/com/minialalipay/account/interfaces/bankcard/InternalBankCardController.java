package com.minialalipay.account.interfaces.bankcard;

import com.minialalipay.account.application.bankcard.BankCardApplicationService;
import com.minialalipay.account.application.bankcard.dto.BankCardDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 银行卡内部查询端点，仅供 business-center 等服务间调用，不经网关暴露。
 *
 * <p>用于业务中心在发起充值/提现交易前校验银行卡归属与余额。</p>
 */
@RestController
@Validated
@RequestMapping("/internal/v1/bank-cards")
public class InternalBankCardController {

    private final BankCardApplicationService bankCardApplicationService;

    public InternalBankCardController(BankCardApplicationService bankCardApplicationService) {
        this.bankCardApplicationService = bankCardApplicationService;
    }

    /**
     * 按用户和卡号查询银行卡信息（含虚拟余额）。
     *
     * @param userId 银行卡所属用户 ID
     * @param cardId 银行卡 ID
     * @return 银行卡 DTO（含 balanceFen）；不属于该用户或不存在时由服务层抛异常
     */
    @GetMapping("/{cardId}")
    public BankCardDTO getCard(@RequestParam @NotBlank @Size(max = 128) String userId,
                               @PathVariable @NotBlank @Size(max = 128) String cardId) {
        return bankCardApplicationService.getCard(userId, cardId);
    }
}
