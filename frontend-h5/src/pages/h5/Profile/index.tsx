import React, { useEffect, useState } from 'react';
import { history } from '@umijs/max';
import * as userService from '@/services/user';
import * as creditService from '@/services/credit';
import { formatAmount } from '@/utils/format';
import { getIdentity } from '@/services/identity';
import { getProfilePreference } from '@/utils/profile';
import { AvatarView, IconSet, IconName } from '@/components/h5/common';
import './index.less';

/** 六入口配置（V2 定稿）：身份绑定/余额/花呗/银行卡/账单/设置。 */
const MENU_ITEMS: { label: string; icon: IconName; path: string; desc?: string }[] = [
  { label: '身份绑定', icon: 'shield', path: '/h5/identity-bind' },
  { label: '余额', icon: 'wallet', path: '/h5/wallet' },
  { label: '花呗', icon: 'huabei', path: '/h5/credit' },
  { label: '银行卡', icon: 'card', path: '/h5/bank-cards' },
  { label: '账单', icon: 'receipt', path: '/h5/account/transactions' },
  { label: '设置', icon: 'setting', path: '/h5/settings' },
];

/**
 * 我的页（V2 重设计）：渐变摘要头（头像+昵称+已绑定身份徽章+掩码账户信息，
 * 点击进个人详情）+ 六入口列表；密码修改与退出登录收敛至设置页。
 */
const ProfilePage: React.FC = () => {
  const profilePreference = getProfilePreference();
  const nickname = profilePreference.nickname;
  const [accountNumber, setAccountNumber] = useState(localStorage.getItem('accountNumber') || '');
  const [maskedPhone, setMaskedPhone] = useState<string>('');
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);
  /** 是否已绑定身份（identityStatus 为 VERIFIED），接口失败时保持未绑定展示。 */
  const [identityBound, setIdentityBound] = useState(false);

  useEffect(() => {
    userService.getMyInfo().then(info => {
      if (info.accountNumber) setAccountNumber(info.accountNumber);
      setMaskedPhone(info.maskedPhone || '');
    }).catch(() => {});
    getIdentity().then(info => {
      setIdentityBound(info.identityStatus === 'VERIFIED');
    }).catch(() => {});
    creditService.getCreditSummary().then(data => {
      setCredit(data as unknown as creditService.CreditSummary);
    }).catch(() => {});
  }, []);

  /** 账户号本地掩码：前 3 位 + ****，明文不进存储。 */
  const maskedAccount = accountNumber ? `${accountNumber.slice(0, 3)} **** ${accountNumber.slice(-4)}` : '';

  return (
    <div className="mine-page">
      {/* 渐变摘要头：点击整体进入个人详情页 */}
      <div className="mine-hero" onClick={() => history.push('/h5/profile-detail')}>
        <AvatarView size={46} />
        <div className="mine-info">
          <div className="mine-name-row">
            <span className="mine-name">{nickname}</span>
            {identityBound && <span className="mine-badge">已绑定身份</span>}
          </div>
          <div className="mine-account">{maskedPhone || '加载中...'}{maskedAccount ? ` · ${maskedAccount}` : ''}</div>
          <div className="mine-sub">点击查看个人详情</div>
        </div>
        <IconSet name="chevronRight" size={18} color="rgba(255,255,255,0.85)" />
      </div>

      {/* 六入口列表 */}
      <div className="mine-body">
        <div className="mine-credit-card" onClick={() => history.push(credit?.opened ? '/h5/credit' : '/h5/credit/open')}>
          <div className="mine-credit-icon">
            <IconSet name="huabei" size={28} color="var(--h5-primary)" />
          </div>
          <div className="mine-credit-main">
            <div className="mine-credit-title">Mini 花呗</div>
            <div className="mine-credit-desc">
              {credit?.opened ? '可用于扫一扫付款' : '开通后可用于扫一扫付款'}
            </div>
            <div className="mine-credit-limit">固定虚拟额度 ¥{formatAmount(credit?.totalLimitFen || 500000)}</div>
          </div>
          <div className="mine-credit-action">{credit?.opened ? '查看' : '立即开通'}</div>
        </div>

        <div className="mine-list">
          {MENU_ITEMS.map((item, index) => (
            <div
              key={item.label}
              className={`mine-item${index < MENU_ITEMS.length - 1 ? '' : ' last'}`}
              onClick={() => history.push(item.path)}
            >
              <div className="item-icon">
                <IconSet name={item.icon} size={17} color="var(--h5-primary)" />
              </div>
              <div className="item-content">
                <div className="item-title">{item.label}</div>
                {item.desc && <div className="item-desc">{item.desc}</div>}
              </div>
              <IconSet name="chevronRight" size={14} color="var(--h5-text-3)" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
