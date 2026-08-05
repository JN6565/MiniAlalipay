package com.minialalipay.ai.application.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 提示注入检测器。
 *
 * <p>编译正则检测用户输入和工具返回中的注入模式。
 * 命中任一模式即刻拒绝并引导至确认流程。</p>
 */
@Component
public class InjectionDetector {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("忽略.*[规则限制约束]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("直接转[账帳]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("不[需必要用]*确认", Pattern.CASE_INSENSITIVE),
            Pattern.compile("跳过[确认验证校验]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("系统[提示指令]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("忽略[前置安全]检查", Pattern.CASE_INSENSITIVE),
            Pattern.compile("以.*身份|伪装成", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 检查消息内容是否包含注入模式。
     *
     * @param message 待检查的用户消息或工具返回
     * @return 安全则返回 safe=true；否则返回具体匹配模式
     */
    public InjectionCheckResult check(String message) {
        if (message == null || message.isBlank()) {
            return InjectionCheckResult.SAFE;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return new InjectionCheckResult(
                        false, pattern.pattern(),
                        "请求包含禁止的指令模式，已拒绝。请通过标准确认流程完成操作。"
                );
            }
        }
        return InjectionCheckResult.SAFE;
    }

    /**
     * 注入检测结果。
     */
    public record InjectionCheckResult(boolean safe, String detectedPattern, String reason) {
        /** 通过检测的常量结果 */
        public static final InjectionCheckResult SAFE = new InjectionCheckResult(true, null, null);
    }
}
