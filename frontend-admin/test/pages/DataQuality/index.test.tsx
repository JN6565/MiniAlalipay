import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import DataQuality from '@/pages/DataQuality';
import { listDataQuality } from '@/services/ops';

const mockLocationState: { current: unknown } = { current: undefined };

jest.mock('@umijs/max', () => ({
  useLocation: () => ({ state: mockLocationState.current }),
}));

jest.mock('@/components/PageHeader', () => () => <div>页面标题</div>);

jest.mock('@/services/ops', () => ({
  listDataQuality: jest.fn(),
}));

const listDataQualityMock = listDataQuality as unknown as jest.Mock;

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(),
    removeListener: jest.fn(),
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  }),
});

/** 使用独立缓存，避免测试间共享已查询的质量结果。 */
function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <DataQuality />
    </QueryClientProvider>,
  );
}

describe('数据质量页筛选', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockLocationState.current = undefined;
    listDataQualityMock.mockResolvedValue({
      code: 'SUCCESS',
      message: '成功',
      data: [
        {
          resultId: 'quality-1',
          taskCode: '交易完整性',
          ruleCode: '终态事件关联',
          status: 'PASSED',
          checkedCount: 100,
          failedCount: 0,
          completedAt: '2026-08-07T02:00:00Z',
        },
      ],
    });
  });

  it('初始只按数据日期读取一次结果，并移除编码筛选字段', async () => {
    renderPage();

    expect(screen.queryByLabelText('任务编码')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('规则编码')).not.toBeInTheDocument();
    await waitFor(() => expect(listDataQualityMock).toHaveBeenCalledTimes(1));
    expect(listDataQualityMock).toHaveBeenCalledWith(expect.any(String), undefined, undefined);
  });

  it('正式结果表不展示任务编码和规则编码', async () => {
    renderPage();

    await waitFor(() => expect(listDataQualityMock).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('质量任务')).not.toBeInTheDocument();
    expect(screen.queryByText('质量规则')).not.toBeInTheDocument();
  });

  it('展示从临时报表携带的未持久化质量检查结果', async () => {
    mockLocationState.current = {
      previewQuality: {
        windowStart: '2026-08-07T16:00:00Z',
        windowEnd: '2026-08-08T12:00:00Z',
        qualityChecks: [
          { ruleCode: 'INBOX_COMPLETE', status: 'FAILED', checkedCount: 136, failedCount: 136 },
        ],
      },
    };

    renderPage();

    expect(await screen.findByText('临时预览检查结果')).toBeInTheDocument();
    expect(screen.getAllByText('136')).toHaveLength(2);
    expect(screen.getByText('正式质量结果')).toBeInTheDocument();
  });

});
