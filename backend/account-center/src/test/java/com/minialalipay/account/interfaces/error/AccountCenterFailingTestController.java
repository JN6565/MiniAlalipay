package com.minialalipay.account.interfaces.error;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestController
class AccountCenterFailingTestController {

    @GetMapping("/test/business-error")
    void businessError() {
        throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    @GetMapping("/test/internal-error")
    void internalError() {
        throw new IllegalStateException("数据库地址不得泄露");
    }

    @GetMapping("/test/not-found")
    void notFound() throws NoResourceFoundException {
        throw new NoResourceFoundException(HttpMethod.GET, "/missing");
    }

    @GetMapping("/test/number")
    void number(@RequestParam int value) {
        // 仅用于触发 Spring 参数类型转换。
    }

    @GetMapping("/test/request-id")
    String requestId(HttpServletRequest request) {
        return (String) request.getAttribute("minialalipay.requestId");
    }

    @PostMapping(value = "/test/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    void json() {
        // 仅用于触发 Spring 请求媒体类型校验。
    }
}
