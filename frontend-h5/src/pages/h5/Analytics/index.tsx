import React from 'react';
import AnalyticsPanel from './AnalyticsPanel';

/**
 * 独立分析页（/h5/account/analytics）：原账单页「分析」Tab 迁出后的独立路由页面。
 * 设计与实现完整保留 AnalyticsPanel，仅补充页面级容器与背景。
 */
const AnalyticsPage: React.FC = () => (
  <div className="analytics-page">
    <AnalyticsPanel />
  </div>
);

export default AnalyticsPage;
