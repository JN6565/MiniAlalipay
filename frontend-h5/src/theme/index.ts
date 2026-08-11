/**
 * C 端 H5 设计令牌体系（亮色版）。
 *
 * 设计定位：在 B 端「晴空」浅蓝（#2f7ff2）基础上提纯增亮，主色采用更鲜活的电光蓝，
 * 辅以青蓝渐变营造科技感。令牌与 src/overrides.css 中的 `--h5-*` CSS 变量同源，
 * 修改主色或状态色时必须同步两处，避免组件与自定义样式脱节。
 *
 * 本期仅实现亮色；`dark` 段为暗色模式预留位，暂不启用切换逻辑。
 */

/** 品牌与状态色令牌：驱动 antd-mobile ConfigProvider 与业务组件配色。 */
export const h5Tokens = {
  /** 主色 · 电光蓝：主按钮、选中态、链接、品牌元素。 */
  primary: '#256cff',
  /** 主色悬停/强调态。 */
  primaryHover: '#4285ff',
  /** 主色按下态。 */
  primaryActive: '#1a56d9',
  /** 主色浅底：标签、次级按钮背景。 */
  primaryBg: '#eaf1ff',
  /** 辅助 · 青蓝：品牌渐变收尾色。 */
  accentCyan: '#18c0e8',
  /** 辅助 · 星紫：花呗等次级品牌元素。 */
  accentViolet: '#7b6cff',
  /** 成功：还款/到账成功等正向结果。 */
  success: '#16b387',
  /** 警告：处理中、限额提示。 */
  warning: '#f59f2d',
  /** 错误/危险：失败提示、危险操作。 */
  danger: '#f0484e',
  /** 收入金额色：与错误同源，用于 +¥ 收入展示。 */
  amountIn: '#f0484e',
  /** 主文本。 */
  text: '#16223a',
  /** 次级文本。 */
  text2: '#5a6b85',
  /** 弱文本/占位。 */
  text3: '#94a3ba',
  /** 页面背景（带蓝倾向的浅中性色）。 */
  bg: '#f4f6fa',
  /** 卡片背景。 */
  cardBg: '#ffffff',
  /** 分割线。 */
  divider: '#e8eef7',
  /** 填充底：输入框/次级区块背景。 */
  fill: '#f0f4fa',
} as const;

/** 品牌渐变：首页头图、余额卡、花呗头图、主按钮等（V2 设计稿定稿）。 */
export const h5Gradients = {
  /** 品牌主渐变（蓝 → 青）：主按钮、宫格图标底。 */
  brand: `linear-gradient(135deg, ${h5Tokens.primary} 0%, ${h5Tokens.accentCyan} 100%)`,
  /** 花呗品牌渐变（紫 → 蓝）。 */
  credit: `linear-gradient(135deg, ${h5Tokens.accentViolet} 0%, #4a8dff 100%)`,
  /** 头部柔渐变（三段蓝）：首页/转账/收款等页面头部背景。 */
  soft: 'linear-gradient(160deg, #2f74ff 0%, #4d9bff 55%, #6fb6ff 100%)',
  /** 财喵（AI Talk）蓝色轻语底：上淡蓝下白纵向渐变，与 overrides.css 的 --h5-grad-ai-deep 同源。 */
  aiDeep: 'linear-gradient(180deg, #dcebff 0%, #e8f2ff 30%, #f7faff 65%, #ffffff 100%)',
} as const;

/** 阴影分层：与 overrides.css 的 --h5-shadow-* 同源，供内联样式引用。 */
export const h5Shadows = {
  /** 常规卡片。 */
  card: '0 2px 12px rgba(22, 60, 120, 0.06)',
  /** 悬浮弹层（Popup/模态）。 */
  float: '0 8px 28px rgba(22, 60, 120, 0.14)',
  /** 品牌渐变图标底。 */
  icon: '0 5px 12px rgba(37, 108, 255, 0.28)',
  /** 渐变主按钮。 */
  btn: '0 6px 16px rgba(37, 108, 255, 0.3)',
  /** 财喵蓝色系柔和投影：AI 对话页白底气泡/输入胶囊，与 --h5-shadow-ai 同源。 */
  ai: '0 4px 16px rgba(22, 119, 255, 0.16)',
} as const;

/**
 * 财喵（AI Talk）蓝色点缀色组：与页面淡蓝渐变同源，仅用于 AI 对话页的品牌点缀
 * （气泡描边、发送按钮、欢迎 Orb、光晕等），禁止用于全局通用组件。
 * 与 overrides.css 的 --h5-ai-pink-* 变量同源，修改需两处同步。
 */
export const h5AiPink = {
  /** 品牌深蓝：渐变强调、发送按钮就绪态、确认按钮主色。 */
  deep: '#1677ff',
  /** 品牌蓝：渐变中段、点缀图标。 */
  brand: '#4285ff',
  /** 中蓝：渐变收尾、描边浅化。 */
  mid: '#8fc2ff',
  /** 淡蓝：填充底色、选中态背景。 */
  soft: '#e3efff',
  /** 极淡蓝洗：近白过渡底色。 */
  wash: '#f5f9ff',
} as const;

/** antd-mobile ConfigProvider 主题变量：统一组件库内部配色。 */
export const h5ThemeVars: Record<string, string> = {
  '--adm-color-primary': h5Tokens.primary,
  '--adm-color-success': h5Tokens.success,
  '--adm-color-warning': h5Tokens.warning,
  '--adm-color-danger': h5Tokens.danger,
  '--adm-color-text': h5Tokens.text,
  '--adm-color-text-secondary': h5Tokens.text2,
  '--adm-color-background': h5Tokens.bg,
  '--adm-color-border': h5Tokens.divider,
  '--adm-color-fill-content': h5Tokens.fill,
  '--adm-border-radius': '10px',
  '--adm-font-size-main': '14px',
};
