package com.minialalipay.ai.domain.agent;

/**
 * AI 意图类型枚举。
 *
 * <p>定义 Agent 在当前项目中支持的八类用户意图，每类意图对应一组必填槽位和可选工具。
 * 意图识别置信度不足时，Agent 必须展示支持范围并引导用户澄清，不得臆造默认值。</p>
 *
 * <h3>各意图说明</h3>
 * <ul>
 *   <li>{@link #TRANSFER}：主动转账，必填槽位为收款人和金额</li>
 *   <li>{@link #BALANCE_QUERY}：查询本人余额，无需槽位</li>
 *   <li>{@link #TRANSACTION_LIST}：查询交易明细，可选时间范围</li>
 *   <li>{@link #TRANSACTION_STATUS}：查询单笔交易状态，必填交易标识</li>
 *   <li>{@link #USER_SEARCH}：搜索候选收款人，必填搜索关键词</li>
 *   <li>{@link #CREDIT_SUMMARY}：查询花呗额度摘要，无需槽位</li>
 *   <li>{@link #CREDIT_BILL}：查询花呗账单，可选账期</li>
 *   <li>{@link #CREDIT_REPAYMENT}：创建花呗还款草稿，必填金额</li>
 * </ul>
 */
public enum IntentType {
    /** 主动转账：从本人账户向指定收款人转账。 */
    TRANSFER,

    /** 查询余额：查看本人账户当前可用余额。 */
    BALANCE_QUERY,

    /** 查询交易列表：按时间或类型查看历史交易明细。 */
    TRANSACTION_LIST,

    /** 查询交易状态：查看单笔已发起交易的当前处理状态。 */
    TRANSACTION_STATUS,

    /** 搜索用户：按昵称或手机号查找候选收款人。 */
    USER_SEARCH,

    /** 查询花呗摘要：查看额度、已用、可用和最近账单概况。 */
    CREDIT_SUMMARY,

    /** 查询花呗账单：查看指定账期的消费明细和还款状态。 */
    CREDIT_BILL,

    /** 花呗还款：创建还款草稿，确认后从余额扣款偿还花呗。 */
    CREDIT_REPAYMENT,

    /** 无法识别用户意图，需展示支持范围并引导用户澄清。 */
    UNKNOWN
}
