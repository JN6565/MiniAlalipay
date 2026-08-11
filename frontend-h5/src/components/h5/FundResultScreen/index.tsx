import { SpinLoading } from 'antd-mobile';
import { IconSet } from '@/components/h5/common';
import { formatBalance } from '@/services/bankCard';
import './index.less';

/**
 * 充值/提现结果屏（页面内嵌三态中的 processing / result 两态）。
 *
 * 为什么做成页面内嵌而非独立路由：充值/提现为异步 TCC 入账，接口返回仅代表受理，
 * 结果屏在同一页面轮询确认余额后展示"最新余额"，用户确认后由页面负责跳回钱包/首页，
 * 避免新增结果路由带来的返回栈歧义。
 */
export interface FundResultScreenProps {
  /** 当前阶段：processing 等待入账确认；result 展示最终结果 */
  stage: 'processing' | 'result';
  /** 资金动作名称，用于文案拼接（充值 / 提现） */
  actionLabel: string;
  /** 本次操作金额（分） */
  amountFen: number;
  /** 资金方向：IN 为充值（余额增加，绿色 +），OUT 为提现（余额减少，深色 −） */
  direction: 'IN' | 'OUT';
  /** result 阶段：余额是否已按预期方向变化确认 */
  confirmed: boolean;
  /** 最近一次拉到的账户可用余额（分）；null 表示拉取失败不展示 */
  newBalanceFen: number | null;
  /** 超时未确认时的"刷新余额"回调：再次发起余额确认轮询 */
  onRefresh: () => void;
  /** 返回首页 */
  onBackHome: () => void;
  /** 返回钱包页（跳转时由调用方携带 balanceDirty 标记） */
  onBackWallet: () => void;
}

const FundResultScreen = (props: FundResultScreenProps) => {
  const { stage, actionLabel, amountFen, direction, confirmed, newBalanceFen, onRefresh, onBackHome, onBackWallet } = props;
  const signText = direction === 'IN' ? '+' : '−';

  /* 处理中态：加载动画 + 确认中说明，余额确认后由调用方切入 result 态 */
  if (stage === 'processing') {
    return (
      <div className="fund-result-screen">
        <div className="fund-result-card">
          <SpinLoading color="primary" style={{ '--size': '38px' }} />
          <div className="fund-result-title">{actionLabel}处理中</div>
          <div className="fund-result-hint">正在确认入账，请稍候</div>
          <div className="fund-result-amount">{signText}¥{formatBalance(amountFen)}</div>
          <div className="fund-result-tip">确认完成后自动展示最新余额</div>
        </div>
      </div>
    );
  }

  /* 结果态：确认成功展示对勾与最新余额；超时未确认展示受理提示与手动刷新入口 */
  return (
    <div className="fund-result-screen">
      <div className="fund-result-card">
        {confirmed ? (
          <>
            <div className="fund-result-icon success">
              <IconSet name="check" size={30} color="#fff" />
            </div>
            <div className="fund-result-title">{actionLabel}成功</div>
            <div className={`fund-result-amount${direction === 'IN' ? ' in' : ''}`}>
              {signText}¥{formatBalance(amountFen)}
            </div>
            <div className="fund-result-hint">
              {newBalanceFen !== null ? `最新余额 ¥${formatBalance(newBalanceFen)}` : '余额以钱包页为准'}
            </div>
          </>
        ) : (
          <>
            <div className="fund-result-icon pending">
              <IconSet name="clock" size={30} color="#fff" />
            </div>
            <div className="fund-result-title">{actionLabel}已受理</div>
            <div className="fund-result-amount">{signText}¥{formatBalance(amountFen)}</div>
            <div className="fund-result-hint">入账确认较慢，余额更新可能有延迟</div>
            <div className="fund-result-refresh" onClick={onRefresh}>
              <IconSet name="refresh" size={14} />
              刷新余额
            </div>
          </>
        )}

        <div className="fund-result-actions">
          <div className="fund-result-btn outline" onClick={onBackHome}>返回首页</div>
          <div className="fund-result-btn primary" onClick={onBackWallet}>返回钱包</div>
        </div>
      </div>
    </div>
  );
};

export default FundResultScreen;
