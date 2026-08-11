import React, { useState, useEffect } from 'react';
import { history } from 'umi';
import { Toast } from 'antd-mobile';
import * as creditService from '@/services/credit';
import { formatAmount } from '@/utils/format';
import { useTabActiveRefresh } from '@/utils/useTabActiveRefresh';
import { getProfilePreference } from '@/utils/profile';
import { Skeleton, IconSet, AvatarView, IconName } from '@/components/h5/common';
import './index.less';

/**
 * 快捷功能入口（V2.1 定稿）：两行 x 4 共 8 个入口。
 * 第一行为高频资金动作（扫一扫/收款/转账/钱包），第二行为资产与智能入口（账单/银行卡/花呗/AI 助手）。
 * tint 决定图标底色渐变（见 index.less 的 quick-icon 色系 class）。
 */
const QUICK_ACTIONS: { label: string; icon: IconName; path: string; tint?: 'brand' | 'orange' | 'green' | 'credit' | 'aipur' }[] = [
  { label: '扫一扫', icon: 'scan', path: '/h5/scan' },
  { label: '收款', icon: 'collect', path: '/h5/collection' },
  { label: '转账', icon: 'transfer', path: '/h5/transfer' },
  { label: '钱包', icon: 'wallet', path: '/h5/wallet' },
  { label: '账单', icon: 'receipt', path: '/h5/account/transactions', tint: 'orange' },
  { label: '银行卡', icon: 'card', path: '/h5/bank-cards', tint: 'green' },
  { label: '花呗', icon: 'huabei', path: '/h5/credit', tint: 'credit' },
  { label: 'AI助手', icon: 'ai', path: '/h5/ai-talk', tint: 'aipur' },
];

/** 生活服务区：仅 UI 占位，点击提示功能开发中，不做路由与后端。 */
const LIFE_SERVICES: { label: string; icon: IconName }[] = [
  { label: '生活缴费', icon: 'bill' },
  { label: '手机营业厅', icon: 'phone' },
  { label: '火车', icon: 'train' },
  { label: '机票', icon: 'plane' },
  { label: '医疗健康', icon: 'health' },
  { label: '市民中心', icon: 'citizen' },
  { label: '哈喽出行', icon: 'travel' },
  { label: '美团', icon: 'food' },
];

/** 按时段生成问候语（设计稿：昵称 + 时段问候）。 */
const getGreeting = (): string => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
};

const HomePage: React.FC = () => {
  const profilePreference = getProfilePreference();
  const nickname = profilePreference.nickname;
  const [loading, setLoading] = useState(true);
  const [credit, setCredit] = useState<creditService.CreditSummary | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  // 首页保活常驻，还款/消费等业务完成后回切时静默重拉花呗摘要。
  useTabActiveRefresh('/h5/home', () => loadData(true));

  const loadData = async (silent = false) => {
    // 静默刷新不进入加载态：保留旧内容直至新数据到达，避免回切时闪骨架屏。
    if (!silent) {
      setLoading(true);
    }
    try {
      // V2 改版：总资产与余额信息收敛至钱包页，首页只拉取花呗摘要
      const creditResult = await creditService.getCreditSummary();
      setCredit(creditResult as unknown as creditService.CreditSummary);
    } catch (error) {
      // 信用 API 失败时保持 credit 为 null，UI 层显示占位文案
      console.error('加载数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="home">
        <div className="home-hero home-hero-skeleton" />
        <div className="home-body">
          <Skeleton variant="card" height={110} />
          <Skeleton variant="card" height={96} />
          <Skeleton variant="list" rows={4} />
        </div>
      </div>
    );
  }

  return (
    <div className="home">
      {/* 顶部品牌区：柔渐变头图 + 头像昵称（点击进个人详情）+ 设置入口；不展示总资产 */}
      <div className="home-hero">
        <div className="hero-user" onClick={() => history.push('/h5/profile-detail')}>
          <AvatarView size={38} />
          <div className="hero-text">
            <div className="hero-nickname">{nickname}，{getGreeting()}</div>
            <div className="hero-sub">点击查看个人详情</div>
          </div>
        </div>
        <div className="hero-actions">
          <span onClick={() => history.push('/h5/settings')}>
            <IconSet name="setting" size={20} color="#fff" />
          </span>
        </div>
      </div>

      <div className="home-body">
        {/* 快捷功能：两行 x 4 共 8 个入口（品牌渐变图标底，账单橙/银行卡绿/花呗紫蓝/AI 紫粉） */}
        <div className="quick-card">
          {QUICK_ACTIONS.map((item) => (
            <div key={item.label} className="quick-item" onClick={() => history.push(item.path)}>
              <div className={`quick-icon${item.tint ? ` ${item.tint}` : ''}`}>
                <IconSet name={item.icon} size={20} color="#fff" />
              </div>
              <span>{item.label}</span>
            </div>
          ))}
        </div>

        {/* 花呗摘要卡：待还金额 + 额度入口 */}
        <div className="credit-card" onClick={() => history.push('/h5/credit')}>
          <div className="credit-left">
            <div className="credit-label">Mini 花呗 · 本期应还(元)</div>
            <div className="credit-amount amount-text">{formatAmount(credit?.billedFen || 0)}</div>
            <div className="credit-sub">
              {credit ? `可用额度 ${formatAmount(credit.availableFen)} 元` : '额度信息暂不可用'}
            </div>
          </div>
          <div className="credit-btn">去还款</div>
        </div>

        {/* 生活服务区：占位入口，点击仅提示开发中 */}
        <div className="life-card">
          <div className="card-title">生活服务</div>
          <div className="life-grid">
            {LIFE_SERVICES.map((item) => (
              <div
                key={item.label}
                className="life-item"
                onClick={() => Toast.show({ content: '功能开发中，敬请期待' })}
              >
                <div className="life-icon">
                  <IconSet name={item.icon} size={18} color="var(--h5-primary)" />
                </div>
                <span>{item.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default HomePage;
