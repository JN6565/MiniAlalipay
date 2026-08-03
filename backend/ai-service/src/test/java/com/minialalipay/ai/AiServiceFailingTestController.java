package com.minialalipay.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅用于验证 AI 服务统一异常响应的测试入口。
 */
@RestController
class AiServiceFailingTestController {

    @GetMapping("/test/ai-error")
    void fail() {
        throw new IllegalStateException("内部模型地址不得泄露");
    }
}
