import React from 'react';
import { formatBalance } from '@/services/bankCard';
import './common.less';

/** 卡面样式：渐变底色 + CSS 纹样叠加，纯样式实现，不引入真实商标图片。 */
export interface BankFaceStyle {
  gradient: string;
  pattern: string;
}

/**
 * 七家银行差异化卡面体系（混合风格：科技基底 + 中国风限定款）。
 * 工商银行「金鳞龙纹」、招商银行「凤羽流光」、中国银行「祥云环绕」为中国风限定款，
 * 其余为科技几何纹；配色与 h5-design-tokens 设计稿一致，修改需同步。
 */
export const BANK_FACE_STYLES: Record<string, BankFaceStyle> = {
  ICBC: {
    gradient: 'linear-gradient(135deg, #c8353f 0%, #8f1d2c 100%)',
    pattern:
      'radial-gradient(circle at 82% 18%, rgba(255,215,160,0.35) 0%, rgba(255,215,160,0) 34%), repeating-radial-gradient(circle at 88% 10%, rgba(255,215,160,0.14) 0 6px, transparent 6px 14px)',
  },
  CMB: {
    gradient: 'linear-gradient(135deg, #d9483b 0%, #96282b 100%)',
    pattern:
      'repeating-conic-gradient(from 200deg at 85% 15%, rgba(255,235,210,0.16) 0deg 8deg, transparent 8deg 20deg)',
  },
  BOC: {
    gradient: 'linear-gradient(135deg, #b03a3a 0%, #7d2226 100%)',
    pattern:
      'radial-gradient(circle at 78% 22%, rgba(255,255,255,0.22) 0 8px, transparent 9px), radial-gradient(circle at 88% 30%, rgba(255,255,255,0.18) 0 6px, transparent 7px), radial-gradient(circle at 82% 34%, rgba(255,255,255,0.14) 0 10px, transparent 11px)',
  },
  CCB: {
    gradient: 'linear-gradient(135deg, #2b6cb0 0%, #153e6e 100%)',
    pattern: 'repeating-linear-gradient(115deg, rgba(255,255,255,0.10) 0 2px, transparent 2px 18px)',
  },
  ABC: {
    gradient: 'linear-gradient(135deg, #2f9e63 0%, #17603c 100%)',
    pattern: 'repeating-radial-gradient(ellipse at 50% 130%, rgba(255,255,255,0.12) 0 3px, transparent 3px 16px)',
  },
  BCM: {
    gradient: 'linear-gradient(135deg, #33569e 0%, #1a2f5e 100%)',
    pattern: 'repeating-linear-gradient(0deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 14px)',
  },
  PSBC: {
    gradient: 'linear-gradient(135deg, #1f8a5b 0%, #0e5c3a 60%, #0b4a30 100%)',
    pattern: 'radial-gradient(ellipse at 20% 0%, rgba(120,255,214,0.20) 0%, transparent 55%)',
  },
};

/** 未收录银行的兜底卡面：深空灰科技渐变。 */
const DEFAULT_FACE_STYLE: BankFaceStyle = {
  gradient: 'linear-gradient(135deg, #3a4a63 0%, #232f42 100%)',
  pattern: 'repeating-linear-gradient(115deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 18px)',
};

/**
 * 仿真银行卡面组件：银行专属渐变 + CSS 纹样 + 银行标识、卡类型、尾号与默认角标。
 *
 * <p>卡面不出现任何风格命名文字与真实商标；余额默认掩码，
 * 由调用方通过 balanceRevealed 控制明文切换（明文仅在内存）。</p>
 */
const BankCardFace: React.FC<{
  bankCode: string;
  bankName: string;
  /** DEBIT 储蓄卡，CREDIT 信用卡。 */
  cardType: 'DEBIT' | 'CREDIT';
  /** 卡号后四位。 */
  cardLast4: string;
  /** 是否默认卡，渲染角标。 */
  isDefault?: boolean;
  /** 卡内虚拟余额（分）；不传则不展示余额行。 */
  balanceFen?: number;
  /** 余额是否明文展示，默认掩码。 */
  balanceRevealed?: boolean;
  onClick?: () => void;
  className?: string;
}> = ({ bankCode, bankName, cardType, cardLast4, isDefault, balanceFen, balanceRevealed, onClick, className }) => {
  const style = BANK_FACE_STYLES[bankCode] || DEFAULT_FACE_STYLE;
  return (
    <div
      className={`bank-card-face${className ? ` ${className}` : ''}`}
      style={{ background: `${style.pattern}, ${style.gradient}` }}
      onClick={onClick}
    >
      <div className="face-top">
        <div className="face-bank">
          <span className="face-logo">{bankCode}</span>
          <span className="face-name">{bankName}</span>
        </div>
        {isDefault && <span className="face-default">默认</span>}
      </div>
      <div className="face-number">**** **** **** {cardLast4}</div>
      <div className="face-bottom">
        <span className="face-type">{cardType === 'DEBIT' ? '储蓄卡' : '信用卡'}</span>
        {balanceFen !== undefined && (
          <span className="face-balance">余额 {balanceRevealed ? `¥ ${formatBalance(balanceFen)}` : '****'}</span>
        )}
      </div>
    </div>
  );
};

export default BankCardFace;
