package com.minialalipay.ai.application.port;

import java.util.Map;

/**
 * 账户中心端口，定义 AI 服务与账户中心的调用契约。
 *
 * <p>负责账户摘要查询、余额查询、交易明细、信用额度及账单查询。
 * 只读查询，不产生资金副作用。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>只能通过网关或已批准的公开契约调用，不直连数据库</li>
 *   <li>返回的金额统一使用 {@code long} 分，不传输浮点数</li>
 *   <li>响应在基础设施边界转换为 AI 所需最小结果，原响应不直接进入 Prompt</li>
 * </ul>
 */
public interface AccountCenterPort {

    /**
     * 查询本人账户摘要（开户状态、可用余额、冻结金额）。
     *
     * @param userId 当前用户 ID（由服务端派生）
     * @return 账户摘要信息
     */
    Map<String, Object> getAccountSummary(String userId);

    /**
     * 查询实时可用余额。
     *
     * @param userId 当前用户 ID
     * @return 余额信息，含 availableFen 和 frozenFen
     */
    Map<String, Object> getBalance(String userId);

    /**
     * 查询本人交易明细。
     *
     * @param userId 当前用户 ID
     * @param limit 返回条数上限
     * @return 交易列表
     */
    Map<String, Object> listTransactions(String userId, int limit);

    /**
     * 查询本人 Mini 花呗额度摘要。
     *
     * @param userId 当前用户 ID
     * @return 额度信息，含 totalLimitFen、usedFen、availableFen
     */
    Map<String, Object> getCreditSummary(String userId);

    /**
     * 查询本人账单列表。
     *
     * @param userId 当前用户 ID
     * @param limit 返回条数上限
     * @return 账单列表
     */
    Map<String, Object> listCreditBills(String userId, int limit);
}
