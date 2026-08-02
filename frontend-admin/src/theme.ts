import type { ThemeConfig } from 'antd';

/**
 * B 端「晴空」浅蓝色主题设计令牌。
 *
 * 主色采用低饱和度的天空蓝，用于按钮、选中态和品牌元素；
 * 背景、边框与表格使用带蓝色倾向的浅中性色，让运营工作台保持清爽、安静。
 * 状态色补充青绿、琥珀和珊瑚色，避免界面退化为单一蓝色。
 */
export const adminTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2f7ff2',
    colorInfo: '#2f7ff2',
    colorSuccess: '#12a594',
    colorWarning: '#ef9f13',
    colorError: '#e5484d',
    colorText: '#1b2a41',
    colorTextSecondary: '#5b6b82',
    colorBgLayout: '#f2f7fc',
    colorBorder: '#d5e5f7',
    colorBorderSecondary: '#e4eef9',
    borderRadius: 8,
    fontFamily:
      "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, -apple-system, sans-serif",
    boxShadow: '0 1px 2px rgba(22, 60, 120, 0.06)',
  },
  components: {
    Layout: {
      headerBg: 'rgba(255, 255, 255, 0.96)',
      bodyBg: '#f2f7fc',
      siderBg: '#f8fbff',
      headerHeight: 64,
      headerPadding: '0 24px',
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: '#49617e',
      itemHoverBg: '#eef5fd',
      itemHoverColor: '#2568c8',
      itemSelectedBg: '#e3f0fe',
      itemSelectedColor: '#1d6de3',
      itemBorderRadius: 8,
      activeBarBorderWidth: 0,
    },
    Button: {
      primaryShadow: 'none',
      defaultShadow: 'none',
    },
    Card: {
      headerBg: 'transparent',
      colorBorderSecondary: '#e4eef9',
    },
    Table: {
      headerBg: '#f4f9ff',
      headerColor: '#33506f',
      rowHoverBg: '#f5faff',
      borderColor: '#e4eef9',
    },
    Tag: {
      defaultBg: '#f3f8ff',
      defaultColor: '#3b6ea5',
    },
  },
};
