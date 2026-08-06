package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.OpsTransactionQueryPort;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionDetail;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionQuery;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionRow;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.TraceSpan;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * B 端运营交易查询应用服务。
 *
 * <p>只读投影 {@code business_db} 中业务中心拥有的资金交易事实，供管理员与运营人员查看全平台脱敏交易
 * 和链路追溯；本服务不修改余额、账本或交易状态，也不调用任何资金写入端口。</p>
 */
@Service
public class OpsTransactionQueryService {
    private final OpsTransactionQueryPort store;

    /** 创建运营交易查询应用服务。 */
    public OpsTransactionQueryService(OpsTransactionQueryPort store) {
        this.store = store;
    }

    /** 分页查询脱敏交易摘要；status/businessType 为空表示不限。 */
    @Transactional(readOnly = true)
    public List<OpsTransactionRow> listTransactions(String status, String businessType, String cursor, int limit,
                                                    Instant from, Instant to) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("交易分页数量必须在 1 到 100 之间");
        return store.listTransactionsForOps(new OpsTransactionQuery(status, businessType, cursor, limit, from, to));
    }

    /** 查询单笔脱敏交易详情；交易不存在时按资源不存在返回。 */
    @Transactional(readOnly = true)
    public OpsTransactionDetail getTransaction(String transactionId) {
        return store.findTransactionForOps(transactionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 查询交易链路片段；交易不存在时按资源不存在返回。 */
    @Transactional(readOnly = true)
    public List<TraceSpan> getTrace(String transactionId) {
        store.findTransactionForOps(transactionId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return store.findTraceSpans(transactionId);
    }
}
