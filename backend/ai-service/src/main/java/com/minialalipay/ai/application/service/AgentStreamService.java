package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.*;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.common.error.BusinessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AI Agent 流式消息处理服务。
 *
 * <p>与 {@link AgentMessageService} 共享相同的会话管理逻辑，
 * 但通过 {@link StreamCallback} 发射 SSE 事件实现流式输出。
 * 核心推理和工具调用委托给 {@link AgentLoop}（ReAct 主循环）。
 * 会话解析、上下文构建等公共逻辑委托给 {@link SessionContextHelper}。</p>
 *
 * <h3>与同步端点的关系</h3>
 * <ul>
 *   <li>{@code POST /messages}（同步）由 {@link AgentMessageService} 处理</li>
 *   <li>{@code POST /messages/stream}（SSE）由本服务处理</li>
 *   <li>两者共享相同的 Repository、AgentLoop 和 LLM 端口</li>
 * </ul>
 */
@Service
public class AgentStreamService {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamService.class);

    /**
     * 系统提示词默认值——傲娇猫娘财喵的完整行为指令。
     *
     * <p>采用结构化 Markdown 排版，包含角色定义、能力边界、7 条铁律。
     * 通过 {@code ai.prompt.system} 配置项可覆盖此默认值。</p>
     */
    static final String DEFAULT_SYSTEM_PROMPT = """
# 角色与身份
你是一只傲娇猫娘，名叫**财喵**，是 MiniAIalipay 的 AI 支付助手。你性格傲娇但内心热心，用简洁可爱的语气帮助用户完成金融操作。

---

## 能力边界——你**能做什么**

> ⚠️ 你只能使用下面列出的工具完成操作。对于不在表中的任何能力，必须直接告知用户「这个我帮不了，你可以在 App 对应功能页面操作喵」。

| 类别 | 能做什么 | 对应工具 | 返回的数据含义 |
|------|---------|----------|---------------|
| 平台余额 | 查 **MiniAIalipay 平台账户**的可用余额和冻结金额 | get_balance | availableFen = 平台可用余额（分），frozenFen = 平台冻结金额（分） |
| 账户摘要 | 查**平台账户**状态（余额、冻结、开户状态） | get_account_summary | 同上 + accountId、status |
| 花呗额度 | 查 **Mini 花呗**信用额度（总额度、已用、可用） | get_credit_summary | totalLimitFen、usedFen、availableFen（均为花呗额度，非平台余额） |
| 花呗账单 | 查 **Mini 花呗**历史账单列表 | list_credit_bills | 花呗账单条目 |
| 交易流水 | 查平台账户的交易明细（支持时间/方向/状态筛选） | list_transactions | 交易列表 items |
| 交易状态 | 按交易 ID 查询单笔交易的状态 | get_transaction_status | status = SUCCESS/PROCESSING/FAILED |
| 转账 | 搜索收款人→创建草稿→校验→生成确认卡 | search_payees → create_transfer_draft → validate_transfer_draft → prepare_confirmation_card | 平台内用户间转账 |
| 花呗还款 | 创建还款草稿→提交还款 | create_credit_repayment_draft → submit_confirmed_credit_repayment | 花呗还款流程 |

## 你**不能做什么**——严禁承诺或假装能做 ⛔

以下是用户可能问到但你**绝对没有能力**完成的事项。遇到这些请求时，必须直接告知用户「这个我帮不了」，**不得调用任何工具并假装结果就是答案**：

- ❌ **银行卡余额**：系统只能查 MiniAIalipay 平台账户余额，**无法查询任何银行卡的余额**
- ❌ **银行卡管理**：无法绑定/解绑银行卡、无法查银行卡列表
- ❌ **其他平台数据**：无法查微信、支付宝、银行 App 等外部平台的任何数据
- ❌ **修改个人信息**：无法修改昵称、手机号、头像、密码等
- ❌ **充值/提现**：无法执行充值或提现操作
- ❌ **预约/定期转账**：不支持
- ❌ **跨行转账**：仅支持平台内用户间转账
- ❌ **客服/投诉**：无法处理投诉、退款争议等人工客服职能
- ❌ **修改金融数据**：无法修改余额、利率、额度等
- ❌ **跳过流程**：无法跳过转账确认步骤或在用户未确认前提交

> **核心原则**：如果用户问的问题不在上面「能做什么」表格中，就**坦诚告知无法处理**，绝不能用工具返回的不相关数据来冒充答案。除此之外万事以事实数据为准，严禁编造事实，凭空臆造数据，诚实输出，不知道就是不知道。

---

## 铁律 1：禁止重复调用工具 ⛔

**一旦通过工具获取了数据，除非用户再次提出要求否则绝对不允许再次调用同一工具或同类工具。**
- 工具返回的结果已在你的上下文中，**直接基于已有结果生成自然语言回复**
- 即使用户换一种说法追问同一件事，也必须用已有数据回答，不得重新调用
- 如果上下文中已有余额数据，就**直接回复**，不要再调 get_balance
- 如果已查过交易记录，就**直接回复**，不要再调 list_transactions

## 铁律 2：工具选择必须精确 🎯

用户意图与工具的映射关系，**严禁混淆**：
- 问「余额 / 多少钱 / 账户资金 / 平台余额」→ **get_balance**（返回的是 **MiniAIalipay 平台账户余额**，不是银行卡余额）
- 问「花呗额度 / 信用额度 / 还能花多少」→ **get_credit_summary**（仅查花呗信用额度，**不是**平台余额）
- 问「交易记录 / 流水 / 消费明细」→ **list_transactions**
- 问「花呗账单 / 信用账单」→ **list_credit_bills**
- 问「交易状态 / 到账没」→ **get_transaction_status**
- 问「账户信息 / 账户摘要」→ **get_account_summary**

> **核心原则**：余额 ≠ 额度，交易 ≠ 账单，绝不可混用工具。

## 铁律 2.1：多项只读查询必须全部执行 🔎

当一条用户消息同时提出多个独立查询（例如“查花呗、查余额、查交易”）时，
必须分别调用对应的只读工具；工具结果全部返回后，逐项基于真实字段回复。
查询结果缺失或工具失败时，必须明确说明“数据不完整/暂时无法查询”，不得用默认金额、
旧会话数据或推测内容补齐。转账、还款等资金流程与查询混合时，先按资金流程规则串行处理，
不得为了并行而改变资金操作顺序。

### 超出能力时的应对策略

当用户问到以下类型的问题时，**不得调用任何工具**，必须直接回复：
- 「银行卡余额」「我的银行存了多少钱」→ 回复：「我只能查 MiniAIalipay 平台账户余额哦，银行卡余额需要去银行 App 查看喵~」
- 「帮我绑卡」「我的银行卡」→ 回复：「这个我帮不了，你可以在 App 的银行卡管理页面操作喵」
- 「帮我充值」「提现」→ 回复：「充值和提现我帮不了，你可以在 App 首页操作喵」
- 其他不在能力表中的请求 → 回复：「这个我帮不了，你可以在 App 对应功能页面操作喵」

## 铁律 3：转账流程严格 4 步——连续执行，禁止中断 🔄

转账必须严格按顺序完成全部 4 步，**每一步完成后立即调用下一步工具，中间禁止输出任何文本回复**：

1. **search_payees** — 从用户消息提取手机号或姓名作为 query 参数，搜索收款人
2. **create_transfer_draft** — 用上一步返回的 payeeId + 金额（分）创建草稿
3. **validate_transfer_draft** — 用上一步返回的 draftId 校验草稿
4. **prepare_confirmation_card** — 用上一步的 draftId 生成确认卡片

> ⚠️ **绝对禁止**：在第 1/2/3 步之后停下来生成过渡性文本（如"正在搜索…""找到了""正在创建…"）。必须连续调用 4 个工具，直到全部完成后才生成最终回复。

## 铁律 4：金额元转分 💰

从用户消息中提取金额时，**必须将元转换为分**（×100）：
- 100 元 → 10000 分
- 50.5 元 → 5050 分
- 传递 amountFen 参数时，值必须是整数（分）

## 铁律 5：输出纯净度——零容忍 🚫

最终回复中**绝对禁止**出现以下内容：
- **工具标记**：`[TOOL_RESULT:xxx]`、`[INTERNAL:xxx]` 或任何方括号内部标记
- **原始 JSON**：`{"resultCode":"0000",...}` 或任何形式的 JSON 数据
- **内部字段名**：resultCode、errorCode、draftId、payeeId、availableFen 等技术字段名
- **调试信息**：任何对用户无意义的系统内部数据

> 回复必须是**纯自然语言**，只包含用户能理解的中文描述。

## 铁律 6：收款人姓名完整 📛

提及收款人时，**必须使用工具返回的完整姓名**，不得截断、缩写或修改。

## 铁律 7：备注规则 📝

系统支持备注功能，但**除非用户主动要求填写备注，否则你不得提及备注字段**，也不要在转账流程中主动询问或确认备注。
""";

    /** 流式文本分块大小（中文字符数），通过 ai.streaming.chunk-size 配置 */
    private final int chunkSize;
    /** 流式文本分块间隔（毫秒），通过 ai.streaming.chunk-delay-ms 配置 */
    private final long chunkDelayMs;
    /** 内容输出前的思考等待（毫秒），通过 ai.streaming.thinking-delay-ms 配置 */
    private final long thinkingDelayMs;

    /** 系统提示词，通过 ai.prompt.system 配置 */
    private final String systemPrompt;

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final InjectionDetector injectionDetector;
    private final AgentLoop agentLoop;
    private final TaskExecutor taskExecutor;
    private final SessionContextHelper contextHelper;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentStreamService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            AgentLoop agentLoop,
            TaskExecutor taskExecutor,
            UserPreferenceService userPreferenceService,
            ObjectMapper objectMapper,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.prompt.system:}") String systemPrompt,
            @Value("${ai.streaming.chunk-size:5}") int chunkSize,
            @Value("${ai.streaming.chunk-delay-ms:50}") long chunkDelayMs,
            @Value("${ai.streaming.thinking-delay-ms:800}") long thinkingDelayMs
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.injectionDetector = injectionDetector;
        this.agentLoop = agentLoop;
        this.taskExecutor = taskExecutor;
        this.systemPrompt = systemPrompt.isEmpty() ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.chunkSize = chunkSize;
        this.chunkDelayMs = chunkDelayMs;
        this.thinkingDelayMs = thinkingDelayMs;
        this.contextHelper = new SessionContextHelper(
                sessionRepository, messageRepository, languageModelPort,
                userPreferenceService, objectMapper,
                (int) SessionContextHelper.parseDurationMinutes(sessionTimeout));
    }

    /**
     * 异步处理消息并通过回调发射 SSE 事件。
     *
     * <p>在独立线程中执行完整的消息处理流程，包括会话管理、注入检测、
     * 幂等检查、AgentLoop 推理和工具执行。每个关键步骤通过回调
     * 发射对应的 SSE 事件。</p>
     *
     * @param userId 用户 ID
     * @param clientMessageId 客户端幂等键
     * @param sessionId 会话 ID（可空）
     * @param rawContent 用户输入原文
     * @param callback SSE 流式回调
     * @param bearerToken 当前请求的 Bearer Token，用于异步线程中透传下游鉴权
     */
    public void processMessageStream(
            String userId, String clientMessageId,
            String sessionId, String rawContent,
            String sanitizedContent,
            StreamCallback callback, String bearerToken
    ) {
        taskExecutor.execute(() -> {
            try {
                if (bearerToken != null && !bearerToken.isEmpty()) {
                    RequestContext.setBearerToken(bearerToken);
                }
                doProcessStream(userId, clientMessageId, sessionId, rawContent, sanitizedContent, callback);
            } catch (Exception e) {
                log.error("流式消息处理异常: userId={}", userId, e);
                safeEmitError(callback, "INTERNAL_ERROR", "服务内部异常");
            } finally {
                RequestContext.clear();
            }
        });
    }

    /**
     * 流式处理核心逻辑，在异步线程中执行。
     */
    private void doProcessStream(
            String userId, String clientMessageId,
            String sessionId, String rawContent,
            String sanitizedContent,
            StreamCallback callback
    ) {
        Instant now = Instant.now();

        // 1. 获取或创建会话
        AgentSession session = contextHelper.resolveSession(userId, sessionId, now);

        // 2. 获取会话锁
        ReentrantLock lock = sessionLocks.computeIfAbsent(
                session.getSessionId(), k -> new ReentrantLock());
        boolean acquired = false;
        try {
            try {
                acquired = lock.tryLock(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                safeEmitError(callback, "AGENT_BUSY", "系统繁忙，请稍后重试");
                return;
            }
            if (!acquired) {
                safeEmitError(callback, "AGENT_BUSY", "系统繁忙，请稍后重试");
                return;
            }

            // 3. 注入检测
            InjectionDetector.InjectionCheckResult injectionCheck =
                    injectionDetector.check(rawContent);
            if (!injectionCheck.safe()) {
                log.warn("流式提示注入被拒绝: userId={}, pattern={}",
                        userId, injectionCheck.detectedPattern());
                safeEmitError(callback, "PROMPT_INJECTION_REJECTED", "检测到不安全的输入内容");
                return;
            }

            // 4. 幂等检查
            Optional<AgentMessage> existingUser = messageRepository.findByClientMessageId(
                    session.getSessionId(), clientMessageId, MessageRole.USER);
            if (existingUser.isPresent()) {
                Optional<AgentMessage> existingAssistant = messageRepository.findByClientMessageId(
                        session.getSessionId(), clientMessageId, MessageRole.ASSISTANT);
                String cachedContent = existingAssistant
                        .map(AgentMessage::getContentRedacted)
                        .orElse("正在处理您的请求……");
                emitContentDeltas(cachedContent, callback);
                callback.onDone(existingUser.get().getMessageId(),
                        session.getSessionId(), "UNKNOWN");
                return;
            }

            // 5. 发射意图理解状态
            callback.onStatus("INTENT", "正在理解您的意图…");

            // 6. 保存用户消息（使用脱敏后内容存储，避免敏感信息进入数据库）
            String storeContent = (sanitizedContent != null && !sanitizedContent.isBlank())
                    ? sanitizedContent : rawContent;
            String messageId = AiServiceUtils.generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, storeContent, AiServiceUtils.estimateTokens(storeContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 7. 构建上下文并调用 AgentLoop（使用原始内容，LLM 需要真实参数调用工具）
            List<ChatMessage> context = contextHelper.buildContext(session, rawContent);
            List<ChatMessage> history = context.subList(0, context.size() - 1);

            AgentLoop.AgentContext agentContext = new AgentLoop.AgentContext(
                    userId, session.getSessionId(), rawContent,
                    history, session, systemPrompt, callback);

            AgentLoop.AgentResult agentResult = agentLoop.executeStreaming(agentContext);

            String finalContent = agentResult.finalContent();
            String pendingText = agentResult.pendingText();
            Map<String, Object> finalSlots = agentResult.accumulatedSlots();

            // 7.5 记录最近完成的操作，防止 LLM 重复提及已完成任务
            if (finalSlots == null) {
                finalSlots = new HashMap<>();
            }
            String completedAction = contextHelper.inferCompletedAction(agentResult.executedTools(), rawContent);
            if (completedAction != null) {
                finalSlots.put("lastCompletedAction", completedAction);
            }

            // 8. 流式推送内容：先推送过渡文本，再推送最终内容
            callback.onStatus("GENERATING", "正在生成回复…");
            try {
                Thread.sleep(thinkingDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (pendingText != null && !pendingText.isBlank()) {
                emitContentDeltas(AiServiceUtils.sanitizeContent(pendingText), callback);
            }
            emitContentDeltas(finalContent, callback);

            // 9. 保存 AI 回复（使用当前时间确保排序在用户消息之后）
            String assistantMessageId = AiServiceUtils.generateUlid();
            String fullAssistantContent = (pendingText != null && !pendingText.isBlank())
                    ? pendingText + "\n" + finalContent : finalContent;
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, fullAssistantContent, agentResult.totalTokens(), Instant.now());
            messageRepository.insert(assistantMessage);

            // 9.5 保存工具结果消息（用于历史消息恢复时重建卡片）
            contextHelper.saveToolResultMessages(agentResult.toolResults(), session.getSessionId(),
                    clientMessageId, assistantMessage.getCreatedAt());

            // 10. 更新会话状态
            if (finalSlots != null && !finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 11. 上下文压缩（Token 超限或对话超过 10 轮时触发四部分结构化压缩）
            long totalTokens = contextHelper.estimateContextTokens(context, agentResult.totalTokens());
            if (contextHelper.needsCompression(session.getSessionId(), totalTokens)) {
                String summary = contextHelper.compressContext(context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            // 12. 推导意图并完成
            IntentType inferredIntent = contextHelper.inferIntent(agentResult.executedTools());
            callback.onDone(assistantMessageId, session.getSessionId(),
                    inferredIntent.name());

        } catch (BusinessException e) {
            log.warn("流式处理业务异常: userId={}, errorCode={}", userId, e.errorCode());
            safeEmitError(callback, e.errorCode().code(), e.errorCode().message());
        } catch (Exception e) {
            log.error("流式处理异常: userId={}", userId, e);
            safeEmitError(callback, "INTERNAL_ERROR", "服务内部异常");
        } finally {
            if (acquired) {
                lock.unlock();
            }
            // 不清理锁：cleanUpLock 存在 check-then-act 竞态条件，
            // 锁数量等于会话数量，内存开销可控。
        }
    }

    // ---- 流式文本分块推送 ----

    private void emitContentDeltas(String fullText, StreamCallback callback) {
        if (fullText == null || fullText.isBlank()) return;
        int len = fullText.length();
        for (int i = 0; i < len; i += chunkSize) {
            String delta = fullText.substring(i, Math.min(i + chunkSize, len));
            callback.onContentDelta(delta);
            if (i + chunkSize < len) {
                try {
                    Thread.sleep(chunkDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void safeEmitError(StreamCallback callback, String code, String message) {
        try {
            callback.onError(code, message);
        } catch (Exception ignored) {
        }
    }
}
