import React, { useEffect, useState } from 'react';
import { history, useSearchParams, useLocation } from 'umi';
import { Card, Button, Toast, SpinLoading, Dialog } from 'antd-mobile';
import * as transferService from '@/services/transfer';
import * as userService from '@/services/user';
import { generateIdempotencyKey } from '@/services/utils';
import { AmountDisplay } from '@/components/h5/AmountDisplay';
import { PasswordInput } from '@/components/h5/PasswordInput';
import { maskName, maskAccount } from '@/utils/format';
import './index.less';

const TransferConfirmPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const draftId = searchParams.get('draftId');
  // 转账页跳转时携带的收款人展示信息；草稿详情接口已返回脱敏字段，navState 仅作降级
  const navState = (location.state || {}) as {
    payeeNickname?: string;
    payeeAccountNumber?: string;
  };
  const [loading, setLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(true);
  const [draft, setDraft] = useState<transferService.TransferDraft | null>(null);
  // 付款方本人资料（脱敏真实姓名 + 账户号），仅用于确认页展示
  const [myInfo, setMyInfo] = useState<userService.UserInfo | null>(null);
  const [password, setPassword] = useState('');
  // 校验通过后服务端返回的新版本，签发确认令牌必须使用它
  const [validatedVersion, setValidatedVersion] = useState<number | null>(null);

  useEffect(() => {
    if (!draftId) return;
    const ac = new AbortController();
    loadDraft(ac.signal);
    return () => ac.abort();
  }, [draftId]);

  const loadDraft = async (signal?: AbortSignal) => {
    try {
      setDraftLoading(true);
      // 本人资料仅用于展示，失败不阻断确认流程；使用独立请求不受 signal 取消影响
      userService
        .getMyInfo()
        .then(setMyInfo)
        .catch(() => setMyInfo(null));
      const draftData = await transferService.getDraft(draftId!, signal);
      if (signal?.aborted) return;
      setDraft(draftData);

      // 风控预检；后端当前仅返回 PASS，不通过时抛异常由 catch 处理
      const riskResult = await transferService.validateDraft(draftId!, draftData.version, signal);
      if (signal?.aborted) return;
      setValidatedVersion(riskResult.version);
    } catch (error: any) {
      if (signal?.aborted) return;
      Toast.show({ content: error.message || '加载草稿失败', icon: 'fail' });
      history.back();
    } finally {
      if (!signal?.aborted) setDraftLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!password || password.length !== 6) {
      Toast.show({ content: '请输入6位支付密码', icon: 'fail' });
      return;
    }

    if (!draft || validatedVersion === null) {
      Toast.show({ content: '草稿未就绪，请返回重试', icon: 'fail' });
      return;
    }

    // 大额转账二次确认
    if (draft.amountFen >= 500000) {
      const confirmed = await Dialog.confirm({
        content: `确认转账 ¥${(draft.amountFen / 100).toFixed(2)} 元？`,
        confirmText: '确认',
        cancelText: '取消',
      });
      if (!confirmed) return;
    }

    setLoading(true);
    try {
      // 1. 验证支付密码并签发一次性支付证明（TRANSFER_CONFIRM 用途）
      const { paymentProof } = await paymentPasswordService.issuePaymentProof(password);

      // 2. 用支付证明签发一次性确认令牌；令牌不得写入日志、URL 或浏览器存储
      let currentVersion = validatedVersion!;
      let confirmResult: transferService.IssuedConfirmation;
      try {
        confirmResult = await transferService.issueConfirmation(draftId!, paymentProof, currentVersion);
      } catch (confirmError: any) {
        // 版本冲突时重新读取草稿获取最新版本并重试一次
        if (confirmError.status === 409 && confirmError.code === 'VERSION_CONFLICT') {
          const freshDraft = await transferService.getDraft(draftId!);
          currentVersion = freshDraft.version;
          confirmResult = await transferService.issueConfirmation(draftId!, paymentProof, currentVersion);
        } else {
          throw confirmError;
        }
      }

      // 3. 提交转账
      const result = await transferService.submitTransfer({
        draftId: draftId!,
        confirmationToken: confirmResult!.confirmationToken,
      });

      // 跳转到结果页；后端状态接口不含收款人昵称，通过路由 state 携带展示；
      // 同时携带提交接口返回的初始数据，后端不可用时降级展示
      history.push(`/h5/transfer/result/${result.transactionId}`, {
        payeeNickname: navState.payeeNickname,
        initialResult: result,
      });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '转账失败' });
    } finally {
      setLoading(false);
    }
  };

  if (!draftId) {
    return <div className="error-state">缺少草稿ID</div>;
  }

  if (draftLoading) {
    return (
      <div className="loading-container">
        <SpinLoading />
        <div className="loading-text">加载中...</div>
      </div>
    );
  }

  // 付款方展示：脱敏真实姓名（无则昵称）+ 脱敏账户号
  const formatPayer = () => {
    if (!myInfo) return '我的账户';
    const name = maskName(myInfo.maskedRealName || myInfo.nickname);
    const account = maskAccount(myInfo.accountNumber);
    return account ? `${name} (${account})` : name;
  };

  // 收款方展示：优先用草稿详情返回的后端脱敏字段，navState 降级；姓名与账号均脱敏
  const formatPayeeName = () => {
    if (draft?.payeeMaskedName) return draft.payeeMaskedName;
    if (navState.payeeNickname) return maskName(navState.payeeNickname);
    return draft?.payeeUserId || '-';
  };
  const formatPayeeAccount = () => {
    if (draft?.payeeMaskedAccountNumber) return draft.payeeMaskedAccountNumber;
    if (navState.payeeAccountNumber) return maskAccount(navState.payeeAccountNumber);
    return null;
  };

  return (
    <div className="transfer-confirm-page">
      <Card className="confirm-card">
        <div className="confirm-title">请确认转账信息</div>

        <div className="confirm-row">
          <span className="confirm-label">付款方</span>
          <span className="confirm-value">{formatPayer()}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">收款方</span>
          <span className="confirm-value">
            {formatPayeeName()}
            {formatPayeeAccount() && ` (${formatPayeeAccount()})`}
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">金额</span>
          <span className="confirm-value amount">
            {draft && <AmountDisplay amountFen={draft.amountFen} size="large" />}
          </span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">备注</span>
          <span className="confirm-value">{draft?.remark || '无'}</span>
        </div>

        <div className="confirm-row">
          <span className="confirm-label">手续费</span>
          <span className="confirm-value">¥0.00</span>
        </div>
      </Card>

      <Card className="password-card">
        <div className="password-title">请输入支付密码</div>
        <PasswordInput value={password} onChange={setPassword} length={6} />
      </Card>

      <div className="confirm-actions">
        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleConfirm}
          disabled={!password || password.length !== 6}
        >
          确认转账
        </Button>
        <Button block size="large" onClick={() => history.back()}>
          返回修改
        </Button>
      </div>
    </div>
  );
};

export default TransferConfirmPage;
