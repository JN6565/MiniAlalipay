import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import Trace from '@/pages/Trace';
import { getOpsTraceByTraceId, getOpsTransactionTrace } from '@/services/ops';

jest.mock('@/components/PageHeader', () => () => <div>页面标题</div>);

jest.mock('@/services/ops', () => ({
  getOpsTraceByTraceId: jest.fn(),
  getOpsTransactionTrace: jest.fn(),
}));

const getOpsTransactionTraceMock = getOpsTransactionTrace as unknown as jest.Mock;
const getOpsTraceByTraceIdMock = getOpsTraceByTraceId as unknown as jest.Mock;

/** 使用独立查询缓存，避免测试之间共享链路查询状态。 */
function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <App>
      <QueryClientProvider client={queryClient}>
        <Trace />
      </QueryClientProvider>
    </App>,
  );
}

describe('链路追溯页错误状态', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getOpsTransactionTraceMock.mockRejectedValue(new Error('资源不存在'));
    getOpsTraceByTraceIdMock.mockRejectedValue(new Error('资源不存在'));
  });

  it('查询失败时不显示内联错误提醒，仍显示空结果状态', async () => {
    renderPage();

    fireEvent.change(screen.getByLabelText('交易号或链路编号'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: '查询链路' }));

    await waitFor(() => expect(getOpsTransactionTraceMock).toHaveBeenCalledWith('1'));
    expect(screen.queryByText('链路查询失败')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '重试' })).not.toBeInTheDocument();
    expect(screen.getByText('未查询到该交易的链路片段')).toBeInTheDocument();
  });
});
