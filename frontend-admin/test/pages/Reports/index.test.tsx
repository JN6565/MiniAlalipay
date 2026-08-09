import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';

const mockHistoryPush = jest.fn();

jest.mock('@umijs/max', () => ({
  useAccess: () => ({ canRunDemoTasks: true }),
  history: { push: mockHistoryPush },
}));

import Reports from '@/pages/Reports';
import { generateDailyReport, generateDailyReportPreview, getDailyReportDetail, listDailyReports, listMetricDefinitions } from '@/services/ops';

jest.mock('@/components/PageHeader', () => () => <div>页面标题</div>);

jest.mock('@/services/ops', () => ({
  generateDailyReport: jest.fn(),
  generateDailyReportPreview: jest.fn(),
  getDailyReportDetail: jest.fn(),
  listDailyReports: jest.fn(),
  listMetricDefinitions: jest.fn(),
}));

const generateDailyReportPreviewMock = generateDailyReportPreview as unknown as jest.Mock;
const generateDailyReportMock = generateDailyReport as unknown as jest.Mock;
const getDailyReportDetailMock = getDailyReportDetail as unknown as jest.Mock;
const listDailyReportsMock = listDailyReports as unknown as jest.Mock;
const listMetricDefinitionsMock = listMetricDefinitions as unknown as jest.Mock;

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: () => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() }),
});

/** 使用独立查询缓存，避免其他用例缓存掩盖临时报表的门禁状态。 */
function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <App>
      <QueryClientProvider client={queryClient}>
        <Reports />
      </QueryClientProvider>
    </App>,
  );
}

describe('T+1 临时报表质量门禁', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    listDailyReportsMock.mockResolvedValue({ code: 'SUCCESS', message: '成功', data: [] });
    listMetricDefinitionsMock.mockResolvedValue({ code: 'SUCCESS', message: '成功', data: [] });
    getDailyReportDetailMock.mockResolvedValue({ code: 'SUCCESS', message: '成功', data: undefined });
  });

  it('临时报表被门禁阻断时展示失败检查及数量，不展示指标', async () => {
    generateDailyReportPreviewMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: {
        windowStart: '2026-08-07T16:00:00Z',
        windowEnd: '2026-08-08T12:00:00Z',
        status: 'BLOCKED',
        metrics: [],
        qualityChecks: [
          { ruleCode: 'INBOX_COMPLETE', status: 'FAILED', checkedCount: 3, failedCount: 3 },
          { ruleCode: 'EVENT_QUARANTINE_EMPTY', status: 'FAILED', checkedCount: 1, failedCount: 1 },
        ],
      },
    });

    renderPage();

    fireEvent.click(screen.getByRole('button', { name: /预\s*览/ }));

    await waitFor(() => expect(generateDailyReportPreviewMock).toHaveBeenCalledTimes(1));
    const qualityGate = await screen.findByRole('alert');
    expect(qualityGate).toHaveTextContent('临时报表未通过质量门禁');
    expect(qualityGate).toHaveTextContent('事件消费未完成：失败 3 条，检查 3 条');
    expect(qualityGate).toHaveTextContent('隔离事件：失败 1 条，检查 1 条');
    fireEvent.click(screen.getByRole('button', { name: '查看数据质量' }));
    expect(mockHistoryPush).toHaveBeenCalledWith('/admin/data-quality', {
      previewQuality: {
        windowStart: '2026-08-07T16:00:00Z',
        windowEnd: '2026-08-08T12:00:00Z',
        qualityChecks: [
          { ruleCode: 'INBOX_COMPLETE', status: 'FAILED', checkedCount: 3, failedCount: 3 },
          { ruleCode: 'EVENT_QUARANTINE_EMPTY', status: 'FAILED', checkedCount: 1, failedCount: 1 },
        ],
      },
    });
    expect(screen.queryByText('临时报表已生成')).not.toBeInTheDocument();
  });

  it('正式日报生成成功后展示已发布状态，并按定义显示中文名称和单位', async () => {
    listDailyReportsMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: [{ metricCode: 'PAYMENT_SUCCESS_RATE', reportDate: '2026-08-08', value: 9998, metricVersion: 'v1', qualityStatus: 'PASSED' }],
    });
    listMetricDefinitionsMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: [{ metricCode: 'PAYMENT_SUCCESS_RATE', version: 'v1', name: '支付成功率', unit: '万分比' }],
    });
    generateDailyReportMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: {
        reportDate: '2026-08-08',
        status: 'PUBLISHED',
        generatedAt: '2026-08-09T02:00:00Z',
        metrics: [],
        qualityChecks: [],
        failures: [],
      },
    });

    renderPage();

    await waitFor(() => expect(listDailyReportsMock).toHaveBeenCalled());
    expect(await screen.findByText('支付成功率')).toBeInTheDocument();
    expect(screen.getByText('9998 万分比')).toBeInTheDocument();
    expect(screen.queryByText('PAYMENT_SUCCESS_RATE')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '生成日报' }));
    await waitFor(() => expect(generateDailyReportMock).toHaveBeenCalledTimes(1));
    expect(await screen.findAllByText('已发布')).not.toHaveLength(0);
  });

  it('正式日报被质量门禁阻断时展示失败检查', async () => {
    generateDailyReportMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: {
        reportDate: '2026-08-08',
        status: 'BLOCKED',
        generatedAt: '2026-08-09T02:00:00Z',
        metrics: [],
        qualityChecks: [{ ruleCode: 'INBOX_COMPLETE', status: 'FAILED', checkedCount: 8, failedCount: 2 }],
        failures: [],
      },
    });

    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '生成日报' }));

    await waitFor(() => expect(generateDailyReportMock).toHaveBeenCalledTimes(1));
    const qualityGate = await screen.findByRole('alert');
    expect(qualityGate).toHaveTextContent('正式日报未通过质量门禁');
    expect(qualityGate).toHaveTextContent('事件消费未完成：失败 2 条，检查 8 条');
  });

  it('日报详情将技术编码转换为运营中文', async () => {
    getDailyReportDetailMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: {
        reportMeta: {
          reportDate: '2026-08-08', generatedAt: '2026-08-09T02:00:00Z', reportVersion: 'v1',
          dataWindow: { from: '2026-08-07T16:00:00Z', to: '2026-08-08T16:00:00Z' },
          dataSources: ['metrics_db.monitoring_transaction_final_projection', 'metrics_db.quality_result', 'metrics_db.monitor_alert'],
        },
        metrics: [],
        overview: {
          transactionCount: 1, transactionAmountFen: 100, successRateBps: 10_000, averageLatencyMs: 10,
          dayOverDayChanges: { transactionCountBps: 0, transactionAmountBps: 0, successRateBps: 0, averageLatencyBps: 0 },
        },
        transactionTrend: [{ timeBucket: '2026-08-08T02:00:00Z', transactionCount: 1, amountFen: 100 }],
        reconciliation: [],
        quality: [{ dimension: 'INBOX_COMPLETE', definition: '事件消费完整性', currentValue: 0, threshold: 0, conclusion: 'PASSED' }],
        alerts: [],
      },
    });

    renderPage();

    expect(await screen.findByText('最终交易投影、数据质量检查、运营告警')).toBeInTheDocument();
    expect(screen.getAllByText('事件消费完整性')).not.toHaveLength(0);
    expect(screen.queryByText('metrics_db.analytics_event')).not.toBeInTheDocument();
    expect(screen.queryByText('INBOX_COMPLETE')).not.toBeInTheDocument();
  });
});
