package com.minialalipay.ai.interfaces.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 会话重命名请求 DTO。
 *
 * @param title 新会话标题，最大 100 字符，空字符串表示清除自定义标题
 */
public record RenameSessionRequest(
        @Size(max = 100, message = "会话标题不得超过 100 个字符")
        String title
) {
}
