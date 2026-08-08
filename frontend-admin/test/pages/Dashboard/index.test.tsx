import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));

import Dashboard from '@/pages/Dashboard';
import { getDashboardSummary } from '@/services/ops';

jest.mock('@/services/ops', () => ({ getDashboardSummary: jest.fn() }));

const getDashboardSummaryMock = getDashboardSummary as unknown as jest.Mock;

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: () => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() }),
});

/** 使用独立查询缓存，防止其他页面测试遗留的服务端投影影响看板断言。 */
function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><Dashboard /></QueryClientProvider>);
}

describe('可信运行看板', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getDashboardSummaryMock.mockResolvedValue({
      code: 'SUCCESS', message: '成功', data: {
        generatedAt: '2026-08-08T08:00:00Z',
        kpis: { todayTransactionAmountFen: 8426310, paymentSuccessRateBps: 9998, pendingManualCaseCount: 17, openAlertCount: 2 },
        transactionTrend: [{ metricCode: 'transaction.status.changed', bucketAt: '2026-08-08T07:55:00Z', value: 3, metricVersion: 'v1', qualityStatus: 'PASSED' }],
        dataQuality: [{ resultId: 'quality-1', taskCode: '交易完整率', ruleCode: '终态关联', status: 'PASSED', checkedCount: 100, failedCount: 1, completedAt: '2026-08-08T02:00:00Z' }],
        services: [{ serviceCode: 'gateway', serviceName: '网关 gateway', status: 'UP', probeLatencyMs: 18, checkedAt: '2026-08-08T08:00:00Z' }],
        recentTransactions: [{ transactionId: 'TX-1', businessType: 'TRANSFER', sourceType: 'TRANSFER_DRAFT', sourceOrderId: 'O-1', initiatorMasked: 'u***1', amountFen: 126000, status: 'SUCCESS', riskLevel: 'LOW', traceId: 'trace-1', createdAt: '2026-08-08T08:00:00Z', updatedAt: '2026-08-08T08:00:00Z' }],
      },
    });
  });

  it('只读取服务端汇总投影并展示交易、质量、探针与最近交易', async () => {
    renderPage();

    await waitFor(() => expect(getDashboardSummaryMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText('¥ 84263.10')).toBeInTheDocument());
    expect(screen.getByText('99.98%')).toBeInTheDocument();
    expect(screen.getByText('交易完整率')).toBeInTheDocument();
    expect(screen.getByText('网关 gateway')).toBeInTheDocument();
    expect(screen.getByText('TX-1')).toBeInTheDocument();
  });

  it('默认使用紧凑布局且不提供密度或快捷操作控件', async () => {
    renderPage();
    await waitFor(() => expect(getDashboardSummaryMock).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('舒适')).not.toBeInTheDocument();
    expect(screen.queryByText('紧凑')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('快速操作')).not.toBeInTheDocument();
  });
});
