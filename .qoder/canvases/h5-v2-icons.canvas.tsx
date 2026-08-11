import { Divider, H1, H2, Stack, Table, Text } from 'qoder/canvas';

/* ============================================================
 * H5 V2 设计稿 1/4：统一 SVG 图标体系
 * 规范：24x24 viewBox，strokeWidth 1.8，圆角线帽，线性风格统一
 * 选中态：品牌蓝 #256cff；未选中态：#94a3ba；宫格底：渐变/浅色底
 * 实现要求：IconSet.tsx 必须按本稿 path 数据 1:1 绘制，禁止替换 emoji
 * ============================================================ */

const P = {
  primary: '#256cff',
  grad: 'linear-gradient(135deg, #256cff 0%, #18c0e8 100%)',
  text: '#16223a',
  text2: '#5a6b85',
  text3: '#94a3ba',
  bg: '#f4f6fa',
  card: '#ffffff',
  divider: '#e8eef7',
  fill: '#eaf1ff',
  credit: 'linear-gradient(135deg, #7b6cff 0%, #4a8dff 100%)',
};

/** 统一线性图标：path 数据即设计稿定稿，实现阶段原样搬入 IconSet.tsx */
const ICON_PATHS: Record<string, React.ReactNode> = {
  /* ---- TabBar ---- */
  home: (
    <>
      <path d="M4 10.5 12 4l8 6.5" />
      <path d="M6 9.5V19a1 1 0 0 0 1 1h3.2v-4.4a1.8 1.8 0 0 1 3.6 0V20H17a1 1 0 0 0 1-1V9.5" />
    </>
  ),
  ai: (
    <>
      <path d="M12 3.5c4.7 0 8.5 3.1 8.5 7 0 3.9-3.8 7-8.5 7-.9 0-1.9-.1-2.7-.4L5 19.5l.7-3.2c-1.4-1.3-2.2-3-2.2-4.8 0-3.9 3.8-8 8.5-8Z" />
      <path d="m12.6 8-2.4 3.4h3.6L11.4 15" />
    </>
  ),
  contacts: (
    <>
      <circle cx="9" cy="8.6" r="3.1" />
      <path d="M3.6 19.5c.5-3 2.7-5 5.4-5s4.9 2 5.4 5" />
      <path d="M15.4 5.9a3.1 3.1 0 0 1 0 5.4M17.4 14.9c1.6.8 2.7 2.4 3 4.6" />
    </>
  ),
  me: (
    <>
      <circle cx="12" cy="8.2" r="3.6" />
      <path d="M4.8 20c.8-3.8 3.7-6.2 7.2-6.2s6.4 2.4 7.2 6.2" />
    </>
  ),
  /* ---- 首页快捷宫格 ---- */
  scan: (
    <>
      <path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2" />
      <path d="M4.5 12h15" strokeDasharray="2.4 2.2" />
      <path d="M9 8.5h6M9 15.5h6" opacity="0.55" />
    </>
  ),
  collect: (
    <>
      <path d="M12 3.6v8.6" />
      <path d="m8.6 9 3.4 3.4L15.4 9" />
      <path d="M5 13.5v3.7A2.8 2.8 0 0 0 7.8 20h8.4a2.8 2.8 0 0 0 2.8-2.8v-3.7" />
    </>
  ),
  transfer: (
    <>
      <path d="M4 8.4h13.4" />
      <path d="m14.6 5.4 3 3-3 3" />
      <path d="M20 15.6H6.6" />
      <path d="m9.4 12.6-3 3 3 3" />
    </>
  ),
  wallet: (
    <>
      <path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h10A2.5 2.5 0 0 1 19 7.5v.3" />
      <path d="M4 8v9a2.5 2.5 0 0 0 2.5 2.5h11A2.5 2.5 0 0 0 20 17v-6.5A2.5 2.5 0 0 0 17.5 8H4Z" />
      <circle cx="16" cy="13.9" r="1.15" fill="currentColor" stroke="none" />
    </>
  ),
  huabei: (
    <>
      <path d="M19.2 13.6A7.6 7.6 0 0 1 10.4 5 7.6 7.6 0 1 0 19.2 13.6Z" />
      <path d="m15.4 5.4.5 1.5 1.5.5-1.5.5-.5 1.5-.5-1.5-1.5-.5 1.5-.5Z" fill="currentColor" stroke="none" />
    </>
  ),
  /* ---- 生活服务区 ---- */
  bill: <path d="M13 3 6.4 13h4.2L11 21l6.6-10h-4.2Z" />,
  phone: (
    <>
      <rect x="7" y="3.4" width="10" height="17.2" rx="2.4" />
      <path d="M10.6 5.6h2.8" />
      <circle cx="12" cy="17.6" r="1.05" fill="currentColor" stroke="none" />
    </>
  ),
  train: (
    <>
      <rect x="5.5" y="3.8" width="13" height="12.4" rx="3" />
      <path d="M5.5 10h13" />
      <circle cx="9.2" cy="13.2" r="1" fill="currentColor" stroke="none" />
      <circle cx="14.8" cy="13.2" r="1" fill="currentColor" stroke="none" />
      <path d="m8 20 1.6-3.4M16 20l-1.6-3.4M7 20h10" />
    </>
  ),
  plane: (
    <path d="M10.2 20.6 12 14l6.6-6.6a1.9 1.9 0 0 0-2.7-2.7L9.3 11.3l-5.9 1.6 1.9 1.9 3.4-.7 1.9 1.9-.7 3.4Z" />
  ),
  health: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="4.2" />
      <path d="M12 8.4v7.2M8.4 12h7.2" />
    </>
  ),
  citizen: (
    <>
      <path d="m12 3.4 8 4.2H4Z" />
      <path d="M5.6 7.6v8M10 7.6v8M14 7.6v8M18.4 7.6v8" />
      <path d="M4 15.6h16M3.4 19.4h17.2" />
    </>
  ),
  travel: (
    <>
      <circle cx="6.4" cy="16" r="3.4" />
      <circle cx="17.6" cy="16" r="3.4" />
      <path d="M6.4 16 9.6 8.6h4.2M13.8 8.6 17.6 16M13.8 8.6l-1.4-2.6h-2" />
    </>
  ),
  food: (
    <>
      <path d="M4.4 11.4h15.2a7.6 7.6 0 0 1-6 6.8l-.2 1.6h-2.8l-.2-1.6a7.6 7.6 0 0 1-6-6.8Z" />
      <path d="M9 8.4c0-1.1.9-1.2.9-2.3M13.2 8.4c0-1.1.9-1.2.9-2.3" />
    </>
  ),
  /* ---- 通用操作 ---- */
  back: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  more: (
    <path d="M5.5 12h.01M12 12h.01M18.5 12h.01" strokeWidth="2.6" />
  ),
  eyeOff: (
    <>
      <path d="M4 5.5 20 19" />
      <path d="M9.2 6.4A8.6 8.6 0 0 1 12 6c4.4 0 7.8 3.2 9 6-.4 1-1.1 2.1-2 3.1M6.3 8.1C5 9.3 4 10.7 3 12c1.2 2.8 4.6 6 9 6 1 0 2-.2 2.9-.5" />
      <path d="M10 10.3a2.8 2.8 0 0 0 3.9 3.9" />
    </>
  ),
  eyeOn: (
    <>
      <path d="M3 12c1.2-2.8 4.6-6 9-6s7.8 3.2 9 6c-1.2 2.8-4.6 6-9 6s-7.8-3.2-9-6Z" />
      <circle cx="12" cy="12" r="2.8" />
    </>
  ),
  chevronRight: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  search: (
    <>
      <circle cx="11" cy="11" r="6.4" />
      <path d="m15.8 15.8 4.2 4.2" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.6 19 6v5.4c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6Z" />
      <path d="m9.2 11.8 2 2 3.6-3.8" />
    </>
  ),
  setting: (
    <>
      <circle cx="12" cy="12" r="2.9" />
      <path d="M12 3.8v2M12 18.2v2M3.8 12h2M18.2 12h2M6.2 6.2l1.4 1.4M16.4 16.4l1.4 1.4M17.8 6.2l-1.4 1.4M7.6 16.4l-1.4 1.4" />
    </>
  ),
  card: (
    <>
      <rect x="3.4" y="5.4" width="17.2" height="13.2" rx="2.6" />
      <path d="M3.4 9.8h17.2M6.6 14.6h4" />
    </>
  ),
  receipt: (
    <>
      <path d="M6.4 3.8h11.2V20l-2.2-1.5L13.2 20l-2.2-1.5L8.8 20l-2.4-1.5Z" />
      <path d="M9.2 8.2h5.6M9.2 11.6h5.6M9.2 15h3.2" />
    </>
  ),
  chart: (
    <>
      <path d="M4 4v15a1 1 0 0 0 1 1h15" />
      <path d="M8 15.5v-4M12.5 15.5V8M17 15.5v-6.5" />
    </>
  ),
  plus: <path d="M12 5.5v13M5.5 12h13" />,
  close: <path d="m6.5 6.5 11 11M17.5 6.5l-11 11" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  send: <path d="M4.5 11 19.5 4l-3.6 15.6-4.2-5.9ZM11.7 13.7 19.5 4" />,
  camera: (
    <>
      <path d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.4l1.3-2h5.8l1.3 2h1.4A2.4 2.4 0 0 1 20 8.4v8.2a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 16.6Z" />
      <circle cx="12" cy="12.4" r="3.2" />
    </>
  ),
  keyboard: (
    <>
      <rect x="3.4" y="6.4" width="17.2" height="11.2" rx="2.4" />
      <path d="M6.6 10h.01M10 10h.01M13.4 10h.01M16.8 10h.01M6.6 13.4h.01M17.4 13.4h.01M9.4 13.4h5.2" strokeWidth="2" />
    </>
  ),
};

function Icon(props: { name: string; size?: number; color?: string; width?: number }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={props.size ?? 24}
      height={props.size ?? 24}
      fill="none"
      stroke={props.color ?? 'currentColor'}
      strokeWidth={props.width ?? 1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {ICON_PATHS[props.name]}
    </svg>
  );
}

/* ---- 展示容器 ---- */

function Section(props: { title: string; spec: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 10 }}>
        <span style={{ fontSize: 15, fontWeight: 700, color: P.text }}>{props.title}</span>
        <span style={{ fontSize: 11, color: P.text3 }}>{props.spec}</span>
      </div>
      {props.children}
    </div>
  );
}

function IconCell(props: {
  name: string;
  label: string;
  tile?: 'grad' | 'light' | 'credit' | 'none';
  iconColor?: string;
  size?: number;
}) {
  const tile = props.tile ?? 'none';
  const tileStyle: React.CSSProperties =
    tile === 'none'
      ? { background: 'transparent' }
      : {
          background: tile === 'grad' ? P.grad : tile === 'credit' ? P.credit : P.fill,
          boxShadow: tile === 'light' ? 'none' : '0 6px 14px rgba(37,108,255,0.28)',
        };
  return (
    <div style={{ textAlign: 'center', minWidth: 0 }}>
      <div
        style={{
          width: 44,
          height: 44,
          margin: '0 auto 6px',
          borderRadius: 14,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: tile === 'light' ? P.primary : '#fff',
          ...tileStyle,
        }}
      >
        <Icon name={props.name} size={props.size ?? 22} color={props.iconColor} />
      </div>
      <div style={{ fontSize: 11, color: P.text2 }}>{props.label}</div>
    </div>
  );
}

/* ---- 内置头像（4 个，SVG 自绘） ---- */

function Avatar(props: { kind: 'user' | 'smile' | 'hills' | 'cat'; size?: number }) {
  const s = props.size ?? 56;
  const common = { width: s, height: s, viewBox: '0 0 64 64', style: { borderRadius: '50%', display: 'block' } };
  if (props.kind === 'user') {
    return (
      <svg {...common}>
        <defs>
          <linearGradient id="av1" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#3d7bff" />
            <stop offset="1" stopColor="#22c3e6" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" fill="url(#av1)" />
        <circle cx="32" cy="25" r="10.5" fill="#fff" opacity="0.94" />
        <path d="M12 58c2.6-11.4 10.2-17 20-17s17.4 5.6 20 17Z" fill="#fff" opacity="0.94" />
      </svg>
    );
  }
  if (props.kind === 'smile') {
    return (
      <svg {...common}>
        <defs>
          <linearGradient id="av2" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#ffb648" />
            <stop offset="1" stopColor="#ff7a59" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" fill="url(#av2)" />
        <circle cx="23" cy="27" r="3" fill="#7a3c11" />
        <circle cx="41" cy="27" r="3" fill="#7a3c11" />
        <path d="M22 39c3 4.6 6.6 6.6 10 6.6S39 43.6 42 39" stroke="#7a3c11" strokeWidth="3" strokeLinecap="round" fill="none" />
      </svg>
    );
  }
  if (props.kind === 'hills') {
    return (
      <svg {...common}>
        <defs>
          <linearGradient id="av3" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#2ed3a3" />
            <stop offset="1" stopColor="#12a4c9" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" fill="url(#av3)" />
        <circle cx="44" cy="20" r="7" fill="#fff" opacity="0.92" />
        <path d="M0 64 22 30l14 20 8-10 20 24Z" fill="#0b6b58" opacity="0.75" />
      </svg>
    );
  }
  return (
    <svg {...common}>
      <defs>
        <linearGradient id="av4" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#9a7bff" />
          <stop offset="1" stopColor="#5f6cff" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" fill="url(#av4)" />
      <path d="M14 24 20 10l8 10h8l8-10 6 14" fill="none" />
      <path d="M14 26c0-8 8-13 18-13s18 5 18 13c0 10-8 19-18 19s-18-9-18-19Z" fill="#fff" opacity="0.94" />
      <path d="M14 26 12 12l10 8M50 26l2-14-10 8" fill="#fff" opacity="0.94" />
      <circle cx="25" cy="27" r="2.6" fill="#4338a8" />
      <circle cx="39" cy="27" r="2.6" fill="#4338a8" />
      <path d="M29 35c1 1.4 2 2 3 2s2-.6 3-2" stroke="#4338a8" strokeWidth="2" strokeLinecap="round" fill="none" />
    </svg>
  );
}

/* ---- TabBar 示意 ---- */

function TabBarDemo(props: { active: number }) {
  const tabs = [
    { icon: 'home', label: '首页' },
    { icon: 'ai', label: 'AI 助手' },
    { icon: 'contacts', label: '联系人' },
    { icon: 'me', label: '我的' },
  ];
  return (
    <div
      style={{
        display: 'flex',
        background: '#fff',
        borderTop: `1px solid ${P.divider}`,
        padding: '8px 0 10px',
        borderRadius: '0 0 18px 18px',
      }}
    >
      {tabs.map((t, i) => {
        const on = i === props.active;
        return (
          <div key={t.label} style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 2 }}>
              <Icon name={t.icon} size={22} color={on ? P.primary : P.text3} width={on ? 2 : 1.8} />
            </div>
            <div style={{ fontSize: 10, fontWeight: on ? 600 : 400, color: on ? P.primary : P.text3 }}>
              {t.label}
            </div>
            {on && (
              <div style={{ width: 14, height: 3, borderRadius: 2, background: P.grad, margin: '3px auto 0' }} />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function H5V2Icons() {
  return (
    <Stack gap={22}>
      <H1>H5 V2 设计稿 1/4 · 统一 SVG 图标体系</H1>
      <Text tone="secondary">
        规范：24x24 viewBox · strokeWidth 1.8 · 圆头线帽 · 纯线性风格；选中态 #256cff（TabBar 选中加粗 2.0 并附渐变指示条）；
        宫格底：品牌渐变（资金类）或浅蓝底 #eaf1ff（服务类）。实现阶段 path 数据原样进入 IconSet.tsx，禁止替换 emoji。
      </Text>

      <Section title="底部 TabBar（4 项 · 选中/未选中两态）" spec="22px · 选中加粗 + 渐变指示条">
        <div style={{ background: P.bg, borderRadius: 18, padding: 12 }}>
          <TabBarDemo active={0} />
          <div style={{ height: 8 }} />
          <TabBarDemo active={1} />
          <div style={{ height: 8 }} />
          <TabBarDemo active={3} />
        </div>
      </Section>

      <Section title="首页快捷宫格（5 项）" spec="44px 圆角容器 · 图标 22px · 资金功能渐变底/白图标">
        <div style={{ display: 'flex', gap: 22, background: '#fff', borderRadius: 14, padding: '14px 10px', boxShadow: '0 2px 12px rgba(22,60,120,0.06)' }}>
          <IconCell name="scan" label="扫一扫" tile="grad" />
          <IconCell name="collect" label="收款" tile="grad" />
          <IconCell name="transfer" label="转账" tile="grad" />
          <IconCell name="wallet" label="钱包" tile="grad" />
          <IconCell name="huabei" label="花呗" tile="credit" />
        </div>
        <Text tone="secondary" size="small">
          扫一扫为重绘图标：圆角取景框 + 虚线扫描线 + 上下内容线，替代旧版罗盘/相机样式。
        </Text>
      </Section>

      <Section title="生活服务区（8 项 · 占位功能）" spec="浅蓝底 #eaf1ff · 品牌蓝线性图标">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, background: '#fff', borderRadius: 14, padding: 14, boxShadow: '0 2px 12px rgba(22,60,120,0.06)' }}>
          <IconCell name="bill" label="生活缴费" tile="light" />
          <IconCell name="phone" label="手机营业厅" tile="light" />
          <IconCell name="train" label="火车票" tile="light" />
          <IconCell name="plane" label="机票" tile="light" />
          <IconCell name="health" label="医疗健康" tile="light" />
          <IconCell name="citizen" label="市民中心" tile="light" />
          <IconCell name="travel" label="哈喽出行" tile="light" />
          <IconCell name="food" label="美团" tile="light" />
        </div>
      </Section>

      <Section title="我的页功能入口图标" spec="浅蓝底 · 18px">
        <div style={{ display: 'flex', gap: 20, background: '#fff', borderRadius: 14, padding: '14px 10px', boxShadow: '0 2px 12px rgba(22,60,120,0.06)' }}>
          <IconCell name="shield" label="身份绑定" tile="light" size={18} />
          <IconCell name="wallet" label="余额" tile="light" size={18} />
          <IconCell name="huabei" label="花呗" tile="light" size={18} />
          <IconCell name="card" label="银行卡" tile="light" size={18} />
          <IconCell name="receipt" label="账单" tile="light" size={18} />
          <IconCell name="setting" label="设置" tile="light" size={18} />
        </div>
      </Section>

      <Section title="通用操作图标" spec="随文颜色 · 16-20px">
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, background: '#fff', borderRadius: 14, padding: 14, boxShadow: '0 2px 12px rgba(22,60,120,0.06)' }}>
          {[
            ['back', '返回'],
            ['more', '更多'],
            ['eyeOn', '显示'],
            ['eyeOff', '掩码'],
            ['chevronRight', '进入'],
            ['search', '搜索'],
            ['plus', '新建'],
            ['close', '关闭'],
            ['check', '成功'],
            ['send', '发送'],
            ['camera', '相册上传'],
            ['keyboard', '手动输入'],
            ['chart', '分析'],
          ].map(([name, label]) => (
            <div key={name} style={{ textAlign: 'center', width: 48 }}>
              <div style={{ color: P.text2, display: 'flex', justifyContent: 'center', marginBottom: 4 }}>
                <Icon name={name} size={20} />
              </div>
              <div style={{ fontSize: 10, color: P.text3 }}>{label}</div>
            </div>
          ))}
        </div>
      </Section>

      <Divider />

      <Section title="内置头像（4 个免费 · SVG 自绘 · 56px 圆形）" spec="另支持上传本地图片 ≤1MB">
        <div style={{ display: 'flex', gap: 18, background: '#fff', borderRadius: 14, padding: 16, boxShadow: '0 2px 12px rgba(22,60,120,0.06)', alignItems: 'center' }}>
          <Avatar kind="user" />
          <Avatar kind="smile" />
          <Avatar kind="hills" />
          <Avatar kind="cat" />
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: '50%',
              border: `1.5px dashed ${P.text3}`,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              color: P.text2,
              gap: 2,
            }}
          >
            <Icon name="camera" size={18} />
            <span style={{ fontSize: 8 }}>上传</span>
          </div>
        </div>
        <Text tone="secondary" size="small">
          选中态：2px 品牌蓝描边 + 外圈浅蓝光晕；头像仅保存在当前浏览器（localStorage），不上传后端。
        </Text>
      </Section>

      <Table
        headers={['规范项', '取值']}
        rows={[
          ['画布/线宽', '24x24 viewBox · strokeWidth 1.8（TabBar 选中 2.0）· strokeLinecap/Join round'],
          ['TabBar 颜色', '未选中 #94a3ba · 选中 #256cff + 14x3 渐变指示条'],
          ['宫格容器', '44x44 · 圆角 14 · 资金类品牌渐变 + 阴影 rgba(37,108,255,0.28) · 服务类 #eaf1ff 底'],
          ['头像', '56px 圆形 · 4 内置 + 上传位 · 选中 2px #256cff 描边'],
          ['实现落点', 'frontend-h5/src/components/h5/common/IconSet.tsx（path 数据与本稿一致）'],
        ]}
      />
      <Text tone="secondary" size="small">设计稿 h5-v2-icons · 评审通过后冻结图标 path，实现不得偏差</Text>
    </Stack>
  );
}
