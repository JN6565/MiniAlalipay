import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as collectionService from '@/services/collection';
import { IconSet } from '@/components/h5/common';
import './index.less';

/** 花呗商户收款码开通页：开通后个人收款码和固定金额收款请求可被 Mini 花呗付款。 */
const CreditMerchantCollectionPage: React.FC = () => {
  const [code, setCode] = useState<collectionService.PersonalCode | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    collectionService.getMyCode()
      .then(data => setCode(data as unknown as collectionService.PersonalCode | null))
      .catch(() => setCode(null))
      .finally(() => setLoading(false));
  }, []);

  const handleOpen = async () => {
    setSubmitting(true);
    try {
      if (!code) {
        await collectionService.regenerateCode();
      }
      const opened = await collectionService.openCreditCollection();
      setCode(opened);
      Toast.show({ content: '花呗商户收款码已开通', icon: 'success' });
    } catch (error: any) {
      Toast.show({ content: error?.message || '开通失败，请稍后重试', icon: 'fail' });
    } finally {
      setSubmitting(false);
    }
  };

  const opened = !!code?.creditCollectionEnabled;

  return (
    <div className="merchant-credit-page">
      <div className="merchant-hero">
        <div>
          <div className="merchant-title">开通花呗商户收款码</div>
          <div className={`merchant-status${opened ? ' opened' : ''}`}>{opened ? '已开通' : '未开通'}</div>
        </div>
        <div className="merchant-qr">
          <IconSet name="qr" size={42} color="var(--h5-primary)" />
        </div>
      </div>

      <div className="merchant-body">
        <div className="merchant-card">
          <div className="merchant-desc">开通后，你的个人收款码和固定金额收款请求可被扫一扫中的 Mini 花呗支付。</div>
          {[
            ['wallet', '收款到账', '付款成功后，资金实时到达余额。'],
            ['receipt', '对方账单', '付款方账单展示为 Mini 花呗支付。'],
            ['bill', '你的明细', '可在余额明细中查看交易入账。'],
            ['shield', '范围限制', '仅支持已开通 Mini 花呗的用户付款。'],
          ].map(([icon, title, desc]) => (
            <div className="merchant-row" key={title}>
              <span className="merchant-row-icon">
                <IconSet name={icon as any} size={17} color="#fff" />
              </span>
              <span className="merchant-row-main">
                <span className="merchant-row-title">{title}</span>
                <span className="merchant-row-desc">{desc}</span>
              </span>
              <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
            </div>
          ))}
        </div>

        <div className={`merchant-submit${opened || submitting || loading ? ' disabled' : ''}`} onClick={() => !opened && !submitting && handleOpen()}>
          {opened ? '已开通' : submitting ? '开通中...' : '确认开通'}
        </div>
        <div className="merchant-link" onClick={() => history.push('/h5/collection')}>查看我的收款码</div>
      </div>
    </div>
  );
};

export default CreditMerchantCollectionPage;
