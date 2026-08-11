// @ts-nocheck
import React, { useState, useEffect } from 'react';
import { history, useLocation } from 'umi';
import { Input, Toast, Popup } from 'antd-mobile';
import * as userService from '@/services/user';
import * as transferService from '@/services/transfer';
import { getBankCards, formatBalance, type BankCard } from '@/services/bankCard';
import { getMyAccount, type AccountInfo } from '@/services/account';
import { AmountInput } from '@/components/h5/AmountInput';
import { IconSet, BuiltinAvatar, AVATAR_KINDS } from '@/components/h5/common';
import './index.less';

/**
 * 转账页（V2 重设计）：手机号搜索收款人 + 最近转账头像直选 +
 * 支付方式（余额/银行卡）+ 金额大字输入 + 备注选填。
 * 提交后创建草稿并携带展示信息跳转确认页（敏感信息不进 URL）。
 */
const TransferPage: React.FC = () => {
  const location = useLocation();
  const [loading, setLoading] = useState(false);
  const [payeeKeyword, setPayeeKeyword] = useState('');
  const [payeeCandidates, setPayeeCandidates] = useState<userService.PayeeInfo[]>([]);
  const [selectedPayee, setSelectedPayee] = useState<userService.PayeeInfo | null>(null);
  const [amount, setAmount] = useState(0);
  const [remark, setRemark] = useState('');
  const [contacts, setContacts] = useState<userService.Contact[]>([]);
  const [account, setAccount] = useState<AccountInfo | null>(null);
  // 支付来源：BALANCE（默认）或 BANK_CARD
  const [fundingSource, setFundingSource] = useState<'BALANCE' | 'BANK_CARD'>('BALANCE');
  const [bankCards, setBankCards] = useState<BankCard[]>([]);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [showCardPicker, setShowCardPicker] = useState(false);

  // 收款人展示名：优先服务端脱敏后的真实姓名（用于收款确认），未绑定身份时降级为昵称
  const payeeDisplayName = (payee: userService.PayeeInfo) => payee.maskedRealName || payee.nickname;

  // 收款人手机号展示：优先后端脱敏手机号，缺失时降级尾号
  const payeePhoneDisplay = (payee: userService.PayeeInfo) => {
    if (payee.maskedPhone) return payee.maskedPhone;
    if (payee.phoneTail) return `尾号 ${payee.phoneTail}`;
    return payee.accountNumber ? `尾号 ${payee.accountNumber.slice(-4)}` : '';
  };

  // 加载账户余额与银行卡列表（支付方式展示）
  useEffect(() => {
    getMyAccount()
      .then((resp) => setAccount(resp as unknown as AccountInfo))
      .catch(() => {});
    getBankCards().then(setBankCards).catch(() => {});
  }, []);

  // 加载常用联系人（最近转账）
  useEffect(() => {
    userService
      .getContacts(5)
      .then((result) => setContacts(result || []))
      .catch(() => {});
  }, []);

  // 从 URL 参数中读取收款人信息，自动填充（联系人页跳入）
  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const payeeUserId = searchParams.get('payeeUserId');
    const payeeName = searchParams.get('payeeName');
    const accountNumber = searchParams.get('accountNumber');
    if (payeeUserId && accountNumber) {
      setSelectedPayee({
        userId: payeeUserId,
        nickname: payeeName || '用户',
        accountNumber: accountNumber,
      });
      setPayeeKeyword(payeeName || '用户');
    }
  }, [location.search]);

  // 手机号格式校验：仅支持 11 位手机号精确搜索收款人
  const isValidPayeePhone = (value: string) => /^1\d{10}$/.test(value.trim());

  const handleSearch = async () => {
    if (!isValidPayeePhone(payeeKeyword)) {
      Toast.show({ content: '请输入正确的11位手机号', icon: 'fail' });
      return;
    }
    try {
      const result = await userService.searchUsers(payeeKeyword);
      setPayeeCandidates(Array.isArray(result) ? result : []);
      if (!Array.isArray(result) || result.length === 0) {
        Toast.show({ content: '未找到匹配的用户', icon: 'fail' });
      }
    } catch (error: any) {
      Toast.show({ content: error.message || '搜索失败', icon: 'fail' });
    }
  };

  const handleSelectPayee = (payee: userService.PayeeInfo) => {
    setSelectedPayee(payee);
    setPayeeCandidates([]);
    setPayeeKeyword(payeeDisplayName(payee));
  };

  const handleSelectContact = (contact: userService.Contact) => {
    setSelectedPayee({
      userId: contact.payeeUserId,
      nickname: contact.payeeName || contact.alias || '用户',
      accountNumber: contact.accountNumber || '',
    });
    setPayeeKeyword(contact.payeeName || contact.alias || '用户');
  };

  const handleSubmit = async () => {
    if (!selectedPayee) {
      Toast.show({ content: '请选择收款人', icon: 'fail' });
      return;
    }
    if (amount < 0.01 || amount > 50000) {
      Toast.show({ content: '金额范围 0.01-50000 元', icon: 'fail' });
      return;
    }
    if (fundingSource === 'BANK_CARD' && !selectedCardId) {
      Toast.show({ content: '请选择付款银行卡', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      const draft = await transferService.createDraft({
        payeeUserId: selectedPayee.userId,
        amountFen: Math.round(amount * 100),
        remark: remark || '',
      });

      // 后端草稿接口仅返回 payeeUserId，收款人昵称和账号通过路由 state 携带给确认页展示；
      // 确认令牌、支付密码等敏感信息不得进入 URL，这里仅传展示用的公开信息
      history.push(`/h5/transfer/confirm?draftId=${draft.draftId}`, {
        payeeNickname: selectedPayee.nickname,
        payeeAccountNumber: selectedPayee.accountNumber,
        fundingSource,
        cardId: fundingSource === 'BANK_CARD' ? selectedCardId : undefined,
      });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
    } finally {
      setLoading(false);
    }
  };

  const selectedCard = bankCards.find((c) => c.cardId === selectedCardId) || null;

  return (
    <div className="transfer-page">
      {/* 顶部品牌区：纯渐变过渡，内容卡片悬浮其上 */}
      <div className="transfer-hero" />

      {/* 收款人搜索卡 */}
      <div className="transfer-card">
        <div className="transfer-search">
          <IconSet name="search" size={15} color="var(--h5-text-3)" />
          <input
            value={payeeKeyword}
            placeholder="输入手机号搜索收款人"
            onChange={(e) => {
              setPayeeKeyword(e.target.value);
              if (!e.target.value) setPayeeCandidates([]);
            }}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
          {isValidPayeePhone(payeeKeyword) && (
            <span className="transfer-search-btn" onClick={handleSearch}>查询</span>
          )}
        </div>

        {/* 最近转账头像直选 */}
        {!selectedPayee && contacts.length > 0 && (
          <div className="transfer-recent">
            <div className="recent-label">最近转账</div>
            <div className="recent-list">
              {contacts.map((contact, index) => (
                <div
                  key={contact.payeeUserId}
                  className="recent-item"
                  onClick={() => handleSelectContact(contact)}
                >
                  <BuiltinAvatar kind={AVATAR_KINDS[index % AVATAR_KINDS.length]} size={40} />
                  <div className="recent-name">{contact.alias || contact.payeeName}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 已选收款人 */}
        {selectedPayee && (
          <div className="transfer-selected">
            <BuiltinAvatar kind={AVATAR_KINDS[0]} size={34} />
            <div className="selected-info">
              <div className="selected-name">{payeeDisplayName(selectedPayee)}</div>
              <div className="selected-sub">{payeePhoneDisplay(selectedPayee)}</div>
            </div>
            <span
              className="selected-change"
              onClick={() => {
                setSelectedPayee(null);
                setPayeeKeyword('');
              }}
            >
              更换
            </span>
          </div>
        )}

        {/* 搜索结果 */}
        {payeeCandidates.length > 0 && (
          <div className="transfer-candidates">
            {payeeCandidates.map((payee) => (
              <div
                key={payee.userId}
                className="candidate-row"
                onClick={() => handleSelectPayee(payee)}
              >
                <BuiltinAvatar kind={AVATAR_KINDS[1]} size={32} />
                <div className="candidate-info">
                  <div className="candidate-name">{payeeDisplayName(payee)}</div>
                  <div className="candidate-sub">{payeePhoneDisplay(payee)}</div>
                </div>
                <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 支付方式卡 */}
      <div className="transfer-card">
        <div className="card-label">
          <IconSet name="wallet" size={14} color="var(--h5-primary)" /> 支付方式
        </div>
        <div className="funding-tiles">
          <div
            className={`funding-tile${fundingSource === 'BALANCE' ? ' active' : ''}`}
            onClick={() => {
              setFundingSource('BALANCE');
              setSelectedCardId(null);
            }}
          >
            账户余额 ¥{formatBalance(account?.availableFen || 0)}
          </div>
          <div
            className={`funding-tile${fundingSource === 'BANK_CARD' ? ' active' : ''}`}
            onClick={() => {
              setFundingSource('BANK_CARD');
              if (bankCards.length > 0 && !selectedCardId) {
                setSelectedCardId(bankCards.find((c) => c.isDefault)?.cardId || bankCards[0].cardId);
              }
              if (bankCards.length > 0) setShowCardPicker(true);
              else Toast.show({ content: '暂无银行卡，请先绑定', icon: 'fail' });
            }}
          >
            {fundingSource === 'BANK_CARD' && selectedCard
              ? `${selectedCard.bankName}（${selectedCard.cardLast4}）`
              : '银行卡'}
          </div>
        </div>
      </div>

      {/* 金额与备注卡 */}
      <div className="transfer-card amount-card">
        <div className="amount-label">转账金额</div>
        <div className="amount-input-wrapper">
          <AmountInput value={amount} onChange={setAmount} placeholder="0.00" />
        </div>
        {amount > 0 && (amount < 0.01 || amount > 50000) && (
          <div className="amount-warning">金额范围 0.01-50000 元</div>
        )}
        <div className="amount-divider" />
        <div className="remark-row">
          <Input
            placeholder="备注（选填）"
            value={remark}
            onChange={setRemark}
            maxLength={128}
            className="remark-input"
          />
        </div>
      </div>

      {/* 下一步 */}
      <div
        className={`h5-btn-gradient transfer-submit${!selectedPayee || amount <= 0 || loading ? ' disabled' : ''}`}
        onClick={() => !loading && handleSubmit()}
      >
        {loading ? '创建中...' : '下一步'}
      </div>

      {/* 银行卡选择 Popup */}
      <Popup
        visible={showCardPicker}
        onMaskClick={() => setShowCardPicker(false)}
        bodyStyle={{ borderTopLeftRadius: '18px', borderTopRightRadius: '18px', padding: '14px 14px 20px' }}
      >
        <div className="card-picker">
          <div className="picker-handle" />
          <div className="picker-title">选择付款银行卡</div>
          {bankCards.map((card) => {
            const on = card.cardId === selectedCardId;
            return (
              <div
                key={card.cardId}
                className={`picker-row${on ? ' active' : ''}`}
                onClick={() => {
                  setSelectedCardId(card.cardId);
                  setShowCardPicker(false);
                }}
              >
                <span className={`picker-logo${on ? ' active' : ''}`}>
                  {on ? <IconSet name="check" size={14} color="#fff" /> : card.bankName.slice(0, 1)}
                </span>
                <span className="picker-name">
                  {card.bankName} · {card.cardType === 'DEBIT' ? '储蓄卡' : '信用卡'}（{card.cardLast4}）
                </span>
                {on ? (
                  <IconSet name="check" size={16} color="var(--h5-primary)" />
                ) : (
                  <span className="picker-radio" />
                )}
              </div>
            );
          })}
        </div>
      </Popup>
    </div>
  );
};

export default TransferPage;
