import type { ThemeConfig } from 'antd';

/**
 * B 端「晴空」浅蓝色主题设计令牌（淡蓝收敛版）。
 *
 * 这是 Ant Design 组件主题令牌的唯一来源，配合 src/global.less 中的同源 CSS 变量使用。
 * 修改主色、状态色或间距时，必须同步更新 global.less 对应的 `--admin-*` 变量，避免组件与自定义样式脱节。
 *
 * 设计定位：主色采用低饱和度天空蓝 #2f7ff2，用于按钮、选中态和品牌元素；
 * 背景、边框与表格使用带蓝色倾向的浅中性色，让运营工作台保持清爽、安静。
 * 本版在现役「晴空」基础上做了降噪收敛：背景/边框/表头淡蓝浓度下调、状态色柔化
 * （青绿 #2f8d7e、琥珀 #ae7f35、珊瑚 #b35c60），减少长时间高频运营的视觉疲劳。
 */
export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2f7ff2',
    colorInfo: '#2f7ff2',
    colorSuccess: '#2f8d7e',
    colorWarning: '#ae7f35',
    colorError: '#b35c60',
    colorText: '#202a38',
    colorTextSecondary: '#4e5c72',
    colorBgLayout: '#f6f8fb',
    colorBorder: '#e5eaf2',
    colorBorderSecondary: '#e9eef6',
    borderRadius: 8,
    fontFamily:
      "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, -apple-system, sans-serif",
    boxShadow: '0 1px 2px rgba(22, 60, 120, 0.04)',
    colorLink: '#2f7ff2',
    colorLinkHover: '#1d6de3',
    colorFillAlter: '#f3f6fa',
    controlItemBgHover: '#f0f5fb',
    lineHeight: 1.5714,
  },
  components: {
    Layout: {
      headerBg: 'rgba(255, 255, 255, 0.9)',
      bodyBg: '#f6f8fb',
      siderBg: '#f5f8fc',
      headerHeight: 64,
      headerPadding: '0 24px',
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: '#4e5c72',
      itemHoverBg: '#f0f5fb',
      itemHoverColor: '#1d6de3',
      itemSelectedBg: '#e7eef9',
      itemSelectedColor: '#1d6de3',
      itemBorderRadius: 8,
      itemHeight: 34,
      activeBarBorderWidth: 0,
    },
    Button: {
      primaryShadow: 'none',
      defaultShadow: 'none',
    },
    Card: {
      headerBg: 'transparent',
      colorBorderSecondary: '#e9eef6',
    },
    Table: {
      headerBg: '#f3f6fa',
      headerColor: '#33455e',
      rowHoverBg: '#f6f8fb',
      borderColor: '#e9eef6',
    },
    Tag: {
      defaultBg: '#f3f6fa',
      defaultColor: '#3a6b9a',
    },
    Input: {
      borderRadius: 8,
      controlHeight: 36,
      activeBorderColor: '#2f7ff2',
      hoverBorderColor: '#bcd0e8',
      activeShadow: '0 0 0 2px rgba(47, 127, 242, 0.1)',
    },
    Select: {
      borderRadius: 8,
      controlHeight: 36,
      activeBorderColor: '#2f7ff2',
      hoverBorderColor: '#bcd0e8',
      optionSelectedBg: '#e7eef9',
      optionSelectedColor: '#1d6de3',
    },
    DatePicker: {
      borderRadius: 8,
      controlHeight: 36,
      activeBorderColor: '#2f7ff2',
      hoverBorderColor: '#bcd0e8',
      cellHoverBg: '#f0f5fb',
      cellActiveWithRangeBg: '#e7eef9',
    },
    Tabs: {
      itemColor: '#4e5c72',
      itemHoverColor: '#2f7ff2',
      itemSelectedColor: '#1d6de3',
      itemActiveColor: '#1d6de3',
      inkBarColor: '#2f7ff2',
    },
    Breadcrumb: {
      itemColor: '#4e5c72',
      lastItemColor: '#202a38',
      linkColor: '#4e5c72',
      linkHoverColor: '#2f7ff2',
      separatorColor: '#b9c8dc',
    },
    Tooltip: {
      borderRadius: 6,
      colorBgSpotlight: '#202a38',
    },
    Modal: {
      borderRadiusLG: 12,
      headerBg: '#f6f8fb',
      titleFontSize: 16,
    },
    Pagination: {
      itemActiveBg: '#e7eef9',
      itemSize: 32,
      borderRadius: 6,
    },
    Alert: {
      borderRadius: 8,
      colorInfoBg: '#eef4fb',
      colorInfoBorder: '#cfe0f5',
      colorSuccessBg: '#e7f1ef',
      colorSuccessBorder: '#bcdbd4',
      colorWarningBg: '#f6f0e4',
      colorWarningBorder: '#e4cdaa',
      colorErrorBg: '#f6eaeb',
      colorErrorBorder: '#e3c6c8',
    },
    Segmented: {
      itemSelectedBg: '#ffffff',
      trackBg: '#eef2f8',
    },
    Empty: {
      colorTextDescription: '#8d9aae',
    },
    Result: {
      titleFontSize: 22,
      colorTextDescription: '#8d9aae',
    },
    Statistic: {
      contentFontSize: 26,
      titleFontSize: 13,
    },
    Descriptions: {
      labelBg: '#f6f8fb',
      borderRadius: 8,
    },
    Collapse: {
      headerBg: '#f6f8fb',
      contentBg: '#ffffff',
      borderRadius: 8,
    },
    Steps: {
      colorTextDescription: '#8d9aae',
    },
  },
};
