import { history, useParams } from 'umi';
import { Button, Dialog, List, Toast } from 'antd-mobile';
import { useCallback, useEffect, useState } from 'react';
import {
  getBankCardDetail,
  setDefaultBankCard,
  unbindBankCard,
  type BankCard,
} from '@/services/bankCard';
import { formatTime } from '@/utils/format';
import './index.less';

/** 卡类型中文展示名。 */
const CARD_TYPE_LABEL: Record<string, string> = {
  DEBIT: '储蓄卡',
  CREDIT: '信用卡',
};

/**
 * 银行卡详情页：展示全掩码卡片信息，
 * 提供「设为默认卡」与「解除绑定」（二次确认）管理操作。
 */
const BankCardDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const [card, setCard] = useState<BankCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await getBankCardDetail(id);
      setCard(data);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '卡片不存在' });
      history.replace('/h5/bank-cards');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const handleSetDefault = async () => {
    if (!card || busy) return;
    setBusy(true);
    try {
      const updated = await setDefaultBankCard(card.cardId);
      setCard(updated);
      Toast.show({ icon: 'success', content: '已设为默认卡' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '设置失败，请重试' });
    } finally {
      setBusy(false);
    }
  };

  const handleUnbind = async () => {
    if (!card || busy) return;
    // 解绑会移除卡片并释放注册记录，二次确认防误操作；解绑后可凭完整卡号重新绑定
    const confirmed = await Dialog.confirm({
      title: '解除绑定',
      content: `确定解绑 ${card.bankName}（尾号 ${card.cardLast4}）吗？解绑后如需使用请重新绑定。`,
    });
    if (!confirmed) return;

    setBusy(true);
    try {
      await unbindBankCard(card.cardId);
      Toast.show({ icon: 'success', content: '解绑成功，可随时重新绑定该卡' });
      history.push('/h5/bank-cards');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error?.message || '解绑失败，请重试' });
      setBusy(false);
    }
  };

  if (loading || !card) {
    return <div className="bank-card-detail-page" />;
  }

  return (
    <div className="bank-card-detail-page">
      <div className="detail-card-head">
        <div className="detail-bank-name">{card.bankName}</div>
        <div className="detail-card-number">**** **** **** {card.cardLast4}</div>
        {card.isDefault && <div className="detail-default-badge">默认卡</div>}
      </div>

      <List header="卡片信息">
        <List.Item title="卡类型" extra={CARD_TYPE_LABEL[card.cardType] || card.cardType} />
        <List.Item title="持卡人" extra={card.holderMasked} />
        <List.Item title="身份证号" extra={card.idCardMasked} />
        <List.Item title="预留手机号" extra={card.phoneMasked} />
        <List.Item title="绑定时间" extra={formatTime(card.boundAt)} />
      </List>

      <div className="detail-actions">
        {!card.isDefault && (
          <Button block color="primary" size="large" loading={busy} onClick={handleSetDefault}>
            设为默认卡
          </Button>
        )}
        <Button block color="danger" fill="outline" size="large" disabled={busy} onClick={handleUnbind}>
          解除绑定
        </Button>
      </div>
    </div>
  );
};

export default BankCardDetailPage;
