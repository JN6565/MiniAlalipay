package com.minialalipay.ai.application.port;

import java.util.Map;

/**
 * 业务中心端口，定义 AI 服务与业务中心的调用契约。
 *
 * <p>负责转账草稿的创建、校验、提交和状态查询。
 * 所有写操作必须携带服务端派生的幂等键和确认上下文。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>只能调用已合入的公开契约，不直连数据库</li>
 *   <li>创建草稿的 {@code payeeId} 必须来自 {@link UserCenterPort#searchPayees} 的返回值</li>
 *   <li>高风险提交操作的确认句柄由可信 UI 产生，通过确认上下文注入，不暴露给 Agent</li>
 *   <li>超时后查询原资源状态，不生成新幂等键盲重试</li>
 * </ul>
 */
public interface BusinessCenterPort {

    /**
     * 创建结构化转账草稿。
     *
     * @param userId 当前用户 ID（由服务端派生）
     * @param payeeId 收款人用户 ID（必须来自受控查询工具返回值）
     * @param amountFen 转账金额（分），1-5000000
     * @param remark 转账备注，0-50 字符
     * @param idempotencyKey 幂等键（服务端生成）
     * @return 草稿信息，含 draftId 和 version
     */
    Map<String, Object> createTransferDraft(
            String userId, String payeeId, long amountFen,
            String remark, String idempotencyKey);

    /**
     * 执行账户、限额和风控预检。
     *
     * <p>下游 {@code TransferController.validateDraft()} 要求传入草稿版本号（CAS），
     * 版本号由 {@link #createTransferDraft} 返回，必须与前驱工具结果中的 {@code version} 一致。</p>
     *
     * @param userId 当前用户 ID
     * @param draftId 草稿 ID
     * @param version 草稿当前版本号（CAS），由创建草稿时返回
     * @param idempotencyKey 幂等键
     * @return 校验结果，含 valid 标志和各检查项结果
     */
    Map<String, Object> validateTransferDraft(
            String userId, String draftId, long version, String idempotencyKey);

    /**
     * 查询单笔草稿。
     *
     * @param userId 当前用户 ID
     * @param draftId 草稿 ID
     * @return 草稿详情
     */
    Map<String, Object> getTransferDraft(String userId, String draftId);

    /**
     * 查询单笔交易状态。
     *
     * @param userId 当前用户 ID
     * @param transactionId 交易 ID
     * @return 交易状态信息
     */
    Map<String, Object> getTransferStatus(String userId, String transactionId);

    /**
     * 提交已由可信 UI 确认的转账。
     *
     * <p>确认句柄由可信 UI 完成支付密码校验后生成，通过确认上下文注入到调用参数中。
     * 模型可见的工具参数仅包含 {@code draftId}，不含确认句柄。</p>
     *
     * @param userId 当前用户 ID
     * @param draftId 草稿 ID
     * @param confirmationHandle 确认句柄（由策略网关注入，不暴露给 LLM）
     * @param idempotencyKey 幂等键（服务端生成）
     * @return 交易结果，含 transactionId 和 status
     */
    Map<String, Object> submitConfirmedTransfer(
            String userId, String draftId, String confirmationHandle, String idempotencyKey);

    /**
     * 生成待用户确认的结构化卡片。
     *
     * @param userId 当前用户 ID
     * @param draftId 草稿 ID
     * @return 确认卡片数据
     */
    Map<String, Object> prepareConfirmationCard(String userId, String draftId);
}
