package com.minialalipay.ai.application;

/**
 * AI 服务内部共享的工具方法和常量，消除应用层与基础设施层的重复定义。
 *
 * <p>仅限 ai-service 内部使用，不对外暴露。禁止放入 {@code platform-common}，
 * 因为这些方法包含 AI 领域特有的逻辑（token 估算、ULID 生成等）。</p>
 */
public final class AiServiceUtils {

    private AiServiceUtils() {}

    // ── 上下文窗口常量 ──────────────────────────────────────────

    /** 上下文窗口：保留最近 10 轮对话 */
    public static final int CONTEXT_TURN_LIMIT = 10;
    /** 10 轮对话对应 20 条消息（每轮一问一答） */
    public static final int CONTEXT_MESSAGE_LIMIT = CONTEXT_TURN_LIMIT * 2;
    /** 上下文 token 上限，超出后截断早期消息 */
    public static final int MAX_CONTEXT_TOKENS = 4096;

    /** GAP-3：大额风险提示阈值（分），单笔转账 ≥ 此值时主动确认 */
    public static final long LARGE_AMOUNT_THRESHOLD_FEN = 500_000L;

    // ── 关键词匹配 ──────────────────────────────────────────────

    /**
     * 检查文本是否包含任一关键词。
     *
     * @param text     待检测文本
     * @param keywords 关键词列表
     * @return 包含任一关键词时返回 {@code true}
     */
    public static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ── ID 生成 ─────────────────────────────────────────────────

    /**
     * 基于 UUID 生成 26 位伪 ULID 标识符，用于消息 ID、会话 ID 等内部标识。
     *
     * <p>注意：这不是严格的 ULID 规范（不含时间戳排序），仅利用 UUID 随机性
     * 截取前 26 位作为轻量标识。适用于不需要时间有序的内部场景。</p>
     */
    public static String generateUlid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    // ── 金额格式化 ──────────────────────────────────────────────

    /**
     * 将分金额格式化为可读的元字符串，仅用于展示。
     *
     * @param fen 金额（单位：分）
     * @return 格式化后的元字符串，如 {@code "1,000.00"}
     */
    public static String formatFenDisplay(long fen) {
        return String.format("%,.2f", fen / 100.0);
    }

    // ── Token 估算 ──────────────────────────────────────────────

    /**
     * 粗略估算文本的 token 数量。
     *
     * <p>中文每字符约 0.5 token，其他非空白字符约 0.25 token。
     * 此估算用于上下文窗口截断判断，不需要精确到模型分词器级别。</p>
     *
     * @param text 待估算文本
     * @return 估算的 token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) (chineseChars * 0.5 + otherChars * 0.25);
    }
}
