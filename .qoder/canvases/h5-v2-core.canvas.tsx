import { Divider, H1, Stack, Text } from 'qoder/canvas';

/* ============================================================
 * H5 V2 设计稿 2/4：核心页面（首页/钱包/充值提现/账单/个人详情/我的/设置）
 * 令牌：primary #256cff · grad 135deg(#256cff→#18c0e8) · credit(紫蓝)
 * 卡片：白底 · 圆角 16 · shadow 0 2px 12px rgba(22,60,120,0.06)
 * ============================================================ */

const P = {
  primary: '#256cff',
  grad: 'linear-gradient(135deg, #256cff 0%, #18c0e8 100%)',
  gradSoft: 'linear-gradient(160deg, #2f74ff 0%, #4d9bff 55%, #6fb6ff 100%)',
  credit: 'linear-gradient(135deg, #7b6cff 0%, #4a8dff 100%)',
  text: '#16223a',
  text2: '#5a6b85',
  text3: '#94a3ba',
  bg: '#f4f6fa',
  divider: '#e8eef7',
  fill: '#eaf1ff',
  danger: '#f0484e',
  amountIn: '#f0484e',
};

/* ---- 图标（与 h5-v2-icons 稿 path 一致，实现统一进 IconSet） ---- */
const IP: Record<string, React.ReactNode> = {
  home: (<><path d="M4 10.5 12 4l8 6.5" /><path d="M6 9.5V19a1 1 0 0 0 1 1h3.2v-4.4a1.8 1.8 0 0 1 3.6 0V20H17a1 1 0 0 0 1-1V9.5" /></>),
  ai: (<><path d="M12 3.5c4.7 0 8.5 3.1 8.5 7 0 3.9-3.8 7-8.5 7-.9 0-1.9-.1-2.7-.4L5 19.5l.7-3.2c-1.4-1.3-2.2-3-2.2-4.8 0-3.9 3.8-8 8.5-8Z" /><path d="m12.6 8-2.4 3.4h3.6L11.4 15" /></>),
  contacts: (<><circle cx="9" cy="8.6" r="3.1" /><path d="M3.6 19.5c.5-3 2.7-5 5.4-5s4.9 2 5.4 5" /><path d="M15.4 5.9a3.1 3.1 0 0 1 0 5.4M17.4 14.9c1.6.8 2.7 2.4 3 4.6" /></>),
  me: (<><circle cx="12" cy="8.2" r="3.6" /><path d="M4.8 20c.8-3.8 3.7-6.2 7.2-6.2s6.4 2.4 7.2 6.2" /></>),
  scan: (<><path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2" /><path d="M4.5 12h15" strokeDasharray="2.4 2.2" /><path d="M9 8.5h6M9 15.5h6" opacity="0.55" /></>),
  collect: (<><path d="M12 3.6v8.6" /><path d="m8.6 9 3.4 3.4L15.4 9" /><path d="M5 13.5v3.7A2.8 2.8 0 0 0 7.8 20h8.4a2.8 2.8 0 0 0 2.8-2.8v-3.7" /></>),
  transfer: (<><path d="M4 8.4h13.4" /><path d="m14.6 5.4 3 3-3 3" /><path d="M20 15.6H6.6" /><path d="m9.4 12.6-3 3 3 3" /></>),
  wallet: (<><path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h10A2.5 2.5 0 0 1 19 7.5v.3" /><path d="M4 8v9a2.5 2.5 0 0 0 2.5 2.5h11A2.5 2.5 0 0 0 20 17v-6.5A2.5 2.5 0 0 0 17.5 8H4Z" /><circle cx="16" cy="13.9" r="1.15" fill="currentColor" stroke="none" /></>),
  huabei: (<><path d="M19.2 13.6A7.6 7.6 0 0 1 10.4 5 7.6 7.6 0 1 0 19.2 13.6Z" /><path d="m15.4 5.4.5 1.5 1.5.5-1.5.5-.5 1.5-.5-1.5-1.5-.5 1.5-.5Z" fill="currentColor" stroke="none" /></>),
  bill: <path d="M13 3 6.4 13h4.2L11 21l6.6-10h-4.2Z" />,
  phone: (<><rect x="7" y="3.4" width="10" height="17.2" rx="2.4" /><path d="M10.6 5.6h2.8" /><circle cx="12" cy="17.6" r="1.05" fill="currentColor" stroke="none" /></>),
  train: (<><rect x="5.5" y="3.8" width="13" height="12.4" rx="3" /><path d="M5.5 10h13" /><circle cx="9.2" cy="13.2" r="1" fill="currentColor" stroke="none" /><circle cx="14.8" cy="13.2" r="1" fill="currentColor" stroke="none" /><path d="m8 20 1.6-3.4M16 20l-1.6-3.4M7 20h10" /></>),
  plane: <path d="M10.2 20.6 12 14l6.6-6.6a1.9 1.9 0 0 0-2.7-2.7L9.3 11.3l-5.9 1.6 1.9 1.9 3.4-.7 1.9 1.9-.7 3.4Z" />,
  health: (<><rect x="4" y="4" width="16" height="16" rx="4.2" /><path d="M12 8.4v7.2M8.4 12h7.2" /></>),
  citizen: (<><path d="m12 3.4 8 4.2H4Z" /><path d="M5.6 7.6v8M10 7.6v8M14 7.6v8M18.4 7.6v8" /><path d="M4 15.6h16M3.4 19.4h17.2" /></>),
  travel: (<><circle cx="6.4" cy="16" r="3.4" /><circle cx="17.6" cy="16" r="3.4" /><path d="M6.4 16 9.6 8.6h4.2M13.8 8.6 17.6 16M13.8 8.6l-1.4-2.6h-2" /></>),
  food: (<><path d="M4.4 11.4h15.2a7.6 7.6 0 0 1-6 6.8l-.2 1.6h-2.8l-.2-1.6a7.6 7.6 0 0 1-6-6.8Z" /><path d="M9 8.4c0-1.1.9-1.2.9-2.3M13.2 8.4c0-1.1.9-1.2.9-2.3" /></>),
  back: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  eyeOn: (<><path d="M3 12c1.2-2.8 4.6-6 9-6s7.8 3.2 9 6c-1.2 2.8-4.6 6-9 6s-7.8-3.2-9-6Z" /><circle cx="12" cy="12" r="2.8" /></>),
  eyeOff: (<><path d="M4 5.5 20 19" /><path d="M9.2 6.4A8.6 8.6 0 0 1 12 6c4.4 0 7.8 3.2 9 6-.4 1-1.1 2.1-2 3.1M6.3 8.1C5 9.3 4 10.7 3 12c1.2 2.8 4.6 6 9 6 1 0 2-.2 2.9-.5" /><path d="M10 10.3a2.8 2.8 0 0 0 3.9 3.9" /></>),
  chev: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  shield: (<><path d="M12 3.6 19 6v5.4c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6Z" /><path d="m9.2 11.8 2 2 3.6-3.8" /></>),
  card: (<><rect x="3.4" y="5.4" width="17.2" height="13.2" rx="2.6" /><path d="M3.4 9.8h17.2M6.6 14.6h4" /></>),
  receipt: (<><path d="M6.4 3.8h11.2V20l-2.2-1.5L13.2 20l-2.2-1.5L8.8 20l-2.4-1.5Z" /><path d="M9.2 8.2h5.6M9.2 11.6h5.6M9.2 15h3.2" /></>),
  setting: (<><circle cx="12" cy="12" r="2.9" /><path d="M12 3.8v2M12 18.2v2M3.8 12h2M18.2 12h2M6.2 6.2l1.4 1.4M16.4 16.4l1.4 1.4M17.8 6.2l-1.4 1.4M7.6 16.4l-1.4 1.4" /></>),
  camera: (<><path d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.4l1.3-2h5.8l1.3 2h1.4A2.4 2.4 0 0 1 20 8.4v8.2a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 16.6Z" /><circle cx="12" cy="12.4" r="3.2" /></>),
  chart: (<><path d="M4 4v15a1 1 0 0 0 1 1h15" /><path d="M8 15.5v-4M12.5 15.5V8M17 15.5v-6.5" /></>),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
};

function Ic(props: { n: string; s?: number; c?: string; w?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={props.s ?? 20} height={props.s ?? 20} fill="none" stroke={props.c ?? 'currentColor'} strokeWidth={props.w ?? 1.8} strokeLinecap="round" strokeLinejoin="round">
      {IP[props.n]}
    </svg>
  );
}

/* ---- 基础构件 ---- */

function Phone(props: { title: string; note?: string; children: React.ReactNode; width?: number }) {
  return (
    <div style={{ minWidth: 0, width: props.width ?? '100%' }}>
      <div style={{ fontSize: 13, fontWeight: 600, color: P.text, marginBottom: 4 }}>{props.title}</div>
      <div style={{ width: '100%', borderRadius: 22, border: '6px solid #1c2740', background: P.bg, overflow: 'hidden', boxShadow: '0 8px 28px rgba(22,60,120,0.14)' }}>
        {props.children}
      </div>
      {props.note && <div style={{ fontSize: 11, color: P.text3, marginTop: 6, lineHeight: 1.6 }}>{props.note}</div>}
    </div>
  );
}

function Card(props: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{ background: '#fff', borderRadius: 16, padding: 12, boxShadow: '0 2px 12px rgba(22,60,120,0.06)', ...props.style }}>
      {props.children}
    </div>
  );
}

function NavBar(props: { title: string; light?: boolean }) {
  const c = props.light ? '#fff' : P.text;
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', color: c }}>
      <Ic n="back" s={16} />
      <span style={{ fontSize: 13, fontWeight: 600 }}>{props.title}</span>
      <span style={{ width: 16 }} />
    </div>
  );
}

function TabBar(props: { active: number }) {
  const tabs = [['home', '首页'], ['ai', 'AI 助手'], ['contacts', '联系人'], ['me', '我的']];
  return (
    <div style={{ display: 'flex', background: '#fff', borderTop: `1px solid ${P.divider}`, padding: '7px 0 9px' }}>
      {tabs.map(([icon, label], i) => {
        const on = i === props.active;
        return (
          <div key={label} style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 1 }}>
              <Ic n={icon} s={19} c={on ? P.primary : P.text3} w={on ? 2 : 1.8} />
            </div>
            <div style={{ fontSize: 9, fontWeight: on ? 600 : 400, color: on ? P.primary : P.text3 }}>{label}</div>
            {on && <div style={{ width: 12, height: 2.5, borderRadius: 2, background: P.grad, margin: '2px auto 0' }} />}
          </div>
        );
      })}
    </div>
  );
}

function GridIcon(props: { label: string; icon: string; credit?: boolean }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ width: 38, height: 38, margin: '0 auto 4px', borderRadius: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', background: props.credit ? P.credit : P.grad, boxShadow: '0 5px 12px rgba(37,108,255,0.28)' }}>
        <Ic n={props.icon} s={19} />
      </div>
      <div style={{ fontSize: 9, color: P.text2 }}>{props.label}</div>
    </div>
  );
}

function ServiceIcon(props: { label: string; icon: string }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ width: 34, height: 34, margin: '0 auto 3px', borderRadius: 10, background: P.fill, color: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Ic n={props.icon} s={17} />
      </div>
      <div style={{ fontSize: 8.5, color: P.text2 }}>{props.label}</div>
    </div>
  );
}

function BankFace(props: { bank: string; grad: string; tag?: string }) {
  return (
    <div style={{ position: 'relative', borderRadius: 13, padding: '9px 11px', color: '#fff', background: props.grad, minHeight: 66, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: 10, fontWeight: 700 }}>{props.bank}</span>
        {props.tag && <span style={{ fontSize: 8, background: 'rgba(255,255,255,0.22)', padding: '1px 6px', borderRadius: 8 }}>{props.tag}</span>}
      </div>
      <div style={{ fontSize: 11, letterSpacing: 1.6, fontFamily: 'Courier New, monospace' }}>**** **** 5345</div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 8, opacity: 0.85 }}>
        <span>储蓄卡</span>
        <span>余额 ****</span>
      </div>
    </div>
  );
}

function Avatar(props: { kind: number; size?: number; ring?: boolean }) {
  const s = props.size ?? 40;
  const grads = ['linear-gradient(135deg,#3d7bff,#22c3e6)', 'linear-gradient(135deg,#ffb648,#ff7a59)', 'linear-gradient(135deg,#2ed3a3,#12a4c9)', 'linear-gradient(135deg,#9a7bff,#5f6cff)'];
  return (
    <div style={{ width: s, height: s, borderRadius: '50%', background: grads[props.kind % 4], display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: props.ring ? `0 0 0 2px #fff, 0 0 0 4px ${P.primary}` : 'none', flex: 'none' }}>
      <svg viewBox="0 0 24 24" width={s * 0.58} height={s * 0.58} fill="#fff" opacity={0.94}>
        {props.kind === 1 ? (
          <><circle cx="9" cy="10" r="1.3" fill="#7a3c11" /><circle cx="15" cy="10" r="1.3" fill="#7a3c11" /><path d="M8.5 14.5c1.1 1.7 2.3 2.4 3.5 2.4s2.4-.7 3.5-2.4" stroke="#7a3c11" strokeWidth="1.2" strokeLinecap="round" fill="none" /></>
        ) : props.kind === 2 ? (
          <><circle cx="16.5" cy="7.5" r="2.6" /><path d="M2 22 9.5 10l4.5 7 2.6-3.4L22 22Z" /></>
        ) : (
          <><circle cx="12" cy="9" r="4" /><path d="M4.5 21c1-4.3 4-6.5 7.5-6.5s6.5 2.2 7.5 6.5Z" /></>
        )}
      </svg>
    </div>
  );
}

/* ---- 页面稿 ---- */

function HomeScreen() {
  return (
    <Phone title="首页（重设计：头像+昵称，移除总资产与右上角扫码）" note="头像+昵称点击 → 个人详情页；右上角改为设置入口；余额信息全部收敛至钱包页。">
      <div style={{ background: P.gradSoft, paddingBottom: 30 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 14px 10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar kind={0} size={36} />
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 700, color: '#fff' }}>BBFPS，下午好</div>
              <div style={{ fontSize: 8.5, color: 'rgba(255,255,255,0.8)' }}>点击查看个人详情</div>
            </div>
          </div>
          <div style={{ width: 30, height: 30, borderRadius: '50%', background: 'rgba(255,255,255,0.16)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
            <Ic n="setting" s={15} />
          </div>
        </div>
      </div>
      <div style={{ margin: '-20px 12px 0', position: 'relative' }}>
        <Card style={{ padding: '14px 10px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-around' }}>
            <GridIcon label="扫一扫" icon="scan" />
            <GridIcon label="收款" icon="collect" />
            <GridIcon label="转账" icon="transfer" />
            <GridIcon label="钱包" icon="wallet" />
            <GridIcon label="花呗" icon="huabei" credit />
          </div>
        </Card>
        <Card style={{ marginTop: 10, padding: '11px 12px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 32, height: 32, borderRadius: 10, background: P.credit, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
            <Ic n="huabei" s={16} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11.5, fontWeight: 600, color: P.text }}>Mini 花呗 · 本期应还</div>
            <div style={{ fontSize: 9, color: P.text3 }}>¥320.00 · 10-08 到期</div>
          </div>
          <div style={{ fontSize: 9.5, color: '#fff', background: P.credit, padding: '4px 10px', borderRadius: 20, fontWeight: 600 }}>去还款</div>
        </Card>
        <Card style={{ marginTop: 10 }}>
          <div style={{ fontSize: 11.5, fontWeight: 700, color: P.text, marginBottom: 10 }}>生活服务</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
            <ServiceIcon label="生活缴费" icon="bill" />
            <ServiceIcon label="手机营业厅" icon="phone" />
            <ServiceIcon label="火车票" icon="train" />
            <ServiceIcon label="机票" icon="plane" />
            <ServiceIcon label="医疗健康" icon="health" />
            <ServiceIcon label="市民中心" icon="citizen" />
            <ServiceIcon label="哈喽出行" icon="travel" />
            <ServiceIcon label="美团" icon="food" />
          </div>
        </Card>
      </div>
      <div style={{ height: 10 }} />
      <TabBar active={0} />
    </Phone>
  );
}

function WalletScreen() {
  return (
    <Phone title="钱包页（原充值提现，改名钱包）" note="只展示账户余额，不展示总资产；充值/提现按钮上移至银行卡列表上方。">
      <div style={{ background: P.gradSoft, paddingBottom: 26 }}>
        <NavBar title="钱包" light />
        <div style={{ padding: '2px 16px 0', color: '#fff' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 9.5, opacity: 0.9 }}>
            账户余额（元）<Ic n="eyeOn" s={11} />
          </div>
          <div style={{ fontSize: 27, fontWeight: 800, letterSpacing: 0.5, marginTop: 2 }}>3,990.00</div>
        </div>
      </div>
      <div style={{ margin: '-14px 12px 0', position: 'relative' }}>
        <Card style={{ display: 'flex', gap: 10, padding: 10 }}>
          <div style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 20, background: P.grad, color: '#fff', fontSize: 11.5, fontWeight: 600, boxShadow: '0 5px 12px rgba(37,108,255,0.28)' }}>充值</div>
          <div style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 20, border: `1.5px solid ${P.primary}`, color: P.primary, fontSize: 11.5, fontWeight: 600, background: '#fff' }}>提现</div>
        </Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '12px 2px 8px' }}>
          <span style={{ fontSize: 11.5, fontWeight: 700, color: P.text }}>银行卡</span>
          <span style={{ fontSize: 9.5, color: P.primary, display: 'flex', alignItems: 'center', gap: 1 }}>管理银行卡 <Ic n="chev" s={10} /></span>
        </div>
        <BankFace bank="中国工商银行" grad="linear-gradient(135deg,#e0455e 0%,#9e1f3f 100%)" tag="默认" />
        <div style={{ height: 8 }} />
        <BankFace bank="招商银行" grad="linear-gradient(135deg,#c2452d 0%,#7d1f14 100%)" />
        <Card style={{ marginTop: 10, padding: '9px 12px', fontSize: 9, color: P.text3, lineHeight: 1.7 }}>
          · 充值：银行卡余额转入账户余额<br />· 提现：账户余额转入银行卡余额
        </Card>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function RechargeScreen() {
  return (
    <Phone title="充值 · 底部 Popup 选卡（替代下拉框）" note="流程：输入金额 → 确认充值 → 底部弹出卡片选银行卡 → 支付密码 → 完成返回钱包页（余额自动刷新）。">
      <div style={{ background: P.gradSoft, paddingBottom: 20 }}>
        <NavBar title="充值" light />
      </div>
      <div style={{ margin: '-8px 12px 0', position: 'relative' }}>
        <Card>
          <div style={{ fontSize: 10, color: P.text2 }}>充值金额</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 4 }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: P.text }}>¥</span>
            <span style={{ fontSize: 24, fontWeight: 800, color: P.text }}>100.00</span>
          </div>
          <div style={{ height: 1, background: P.divider, margin: '8px 0' }} />
          <div style={{ fontSize: 9, color: P.text3 }}>可用余额 ¥3,890.00</div>
        </Card>
      </div>
      {/* 半透明遮罩 + Popup */}
      <div style={{ marginTop: 14, background: 'rgba(15,25,45,0.45)', padding: '14px 0 0' }}>
        <div style={{ background: '#fff', borderRadius: '18px 18px 0 0', padding: '14px 14px 12px' }}>
          <div style={{ width: 30, height: 3.5, borderRadius: 2, background: P.divider, margin: '0 auto 10px' }} />
          <div style={{ fontSize: 12, fontWeight: 700, color: P.text, marginBottom: 10 }}>选择充值银行卡</div>
          {[
            ['中国工商银行 · 储蓄卡 (5345)', true],
            ['招商银行 · 储蓄卡 (8888)', false],
          ].map(([label, on]) => (
            <div key={label as string} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 10px', borderRadius: 12, border: `1.5px solid ${on ? P.primary : P.divider}`, background: on ? P.fill : '#fff', marginBottom: 8 }}>
              <div style={{ width: 22, height: 22, borderRadius: 6, background: on ? P.grad : '#e0455e', flex: 'none' }} />
              <span style={{ flex: 1, fontSize: 10.5, color: P.text }}>{label}</span>
              {on ? <Ic n="check" s={14} c={P.primary} /> : <span style={{ width: 14, height: 14, borderRadius: '50%', border: `1.5px solid ${P.divider}` }} />}
            </div>
          ))}
          <div style={{ marginTop: 4, textAlign: 'center', padding: '9px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 5px 12px rgba(37,108,255,0.28)' }}>
            确认充值 ¥100.00
          </div>
        </div>
      </div>
    </Phone>
  );
}

function BillsScreen() {
  return (
    <Phone title="账单页（原明细页，文案统一为账单）" note="双 Tab：账单（月分组+收支汇总头）/ 分析（迁移自资产分析页）；删除独立资产分析入口。">
      <div style={{ background: '#fff', borderBottom: `1px solid ${P.divider}` }}>
        <NavBar title="账单" />
        <div style={{ display: 'flex', justifyContent: 'center', gap: 26, paddingBottom: 0 }}>
          {['账单', '分析'].map((t, i) => (
            <div key={t} style={{ textAlign: 'center', paddingBottom: 8 }}>
              <div style={{ fontSize: 12, fontWeight: i === 0 ? 700 : 400, color: i === 0 ? P.text : P.text3 }}>{t}</div>
              {i === 0 && <div style={{ width: 18, height: 3, borderRadius: 2, background: P.grad, margin: '4px auto 0' }} />}
            </div>
          ))}
        </div>
      </div>
      <div style={{ padding: '10px 12px' }}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          {['全部', '收入', '支出'].map((t, i) => (
            <span key={t} style={{ fontSize: 9.5, padding: '4px 12px', borderRadius: 20, background: i === 0 ? P.grad : '#fff', color: i === 0 ? '#fff' : P.text2, fontWeight: 600 }}>{t}</span>
          ))}
        </div>
        <Card style={{ padding: 0, overflow: 'hidden', marginBottom: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', background: P.fill, fontSize: 9.5 }}>
            <span style={{ fontWeight: 700, color: P.text }}>2026年8月</span>
            <span style={{ color: P.text2 }}>收入 ¥1,200.00 · 支出 ¥328.50</span>
          </div>
          {[
            ['转账', '来自 187****0030', '+¥1,200.00', P.amountIn, '08-10 14:22 · 余额 ¥3,990.00'],
            ['扫码支付', '美团外卖', '−¥28.50', P.text, '08-09 12:01 · 余额 ¥2,790.00'],
            ['花呗还款', 'Mini 花呗', '−¥300.00', P.text, '08-08 09:30 · 余额 ¥2,818.50'],
          ].map(([tag, desc, amount, color, time]) => (
            <div key={time} style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '9px 12px', borderTop: `1px solid ${P.divider}` }}>
              <div style={{ width: 28, height: 28, borderRadius: 9, background: P.fill, color: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
                <Ic n="receipt" s={14} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 10.5, color: P.text, fontWeight: 600 }}>{tag} · {desc}</div>
                <div style={{ fontSize: 8.5, color: P.text3 }}>{time}</div>
              </div>
              <div style={{ fontSize: 11.5, fontWeight: 700, color: color }}>{amount}</div>
            </div>
          ))}
        </Card>
      </div>
      <TabBar active={0} />
    </Phone>
  );
}

function AnalyticsTabScreen() {
  const bars = [['5月', 42, 26], ['6月', 68, 31], ['7月', 55, 44], ['8月', 80, 33]];
  return (
    <Phone title="账单页 · 分析 Tab" note="顶部收支胶囊（本月收/支/结余）+ 近 4 月趋势柱图 + 支出分类占比条。">
      <div style={{ background: '#fff', borderBottom: `1px solid ${P.divider}` }}>
        <NavBar title="账单" />
        <div style={{ display: 'flex', justifyContent: 'center', gap: 26 }}>
          {['账单', '分析'].map((t, i) => (
            <div key={t} style={{ textAlign: 'center', paddingBottom: 8 }}>
              <div style={{ fontSize: 12, fontWeight: i === 1 ? 700 : 400, color: i === 1 ? P.text : P.text3 }}>{t}</div>
              {i === 1 && <div style={{ width: 18, height: 3, borderRadius: 2, background: P.grad, margin: '4px auto 0' }} />}
            </div>
          ))}
        </div>
      </div>
      <div style={{ padding: '10px 12px' }}>
        <div style={{ display: 'flex', gap: 8 }}>
          {[['本月收入', '¥1,200.00', P.amountIn], ['本月支出', '¥328.50', P.text], ['结余', '+¥871.50', '#16b387']].map(([l, v, c]) => (
            <Card key={l} style={{ flex: 1, padding: '9px 10px', textAlign: 'center' }}>
              <div style={{ fontSize: 8.5, color: P.text3 }}>{l}</div>
              <div style={{ fontSize: 11.5, fontWeight: 800, color: c, marginTop: 2 }}>{v}</div>
            </Card>
          ))}
        </div>
        <Card style={{ marginTop: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11, fontWeight: 700, color: P.text, marginBottom: 8 }}>
            <Ic n="chart" s={13} c={P.primary} /> 收支趋势（近 4 月）
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 14, height: 70, padding: '0 6px' }}>
            {bars.map(([m, inH, outH]) => (
              <div key={m as string} style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'flex-end', gap: 3, height: 56 }}>
                  <div style={{ width: 8, height: `${inH}%`, borderRadius: 3, background: P.grad }} />
                  <div style={{ width: 8, height: `${outH}%`, borderRadius: 3, background: '#d5e2f5' }} />
                </div>
                <div style={{ fontSize: 8, color: P.text3, marginTop: 3 }}>{m}</div>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center', fontSize: 8.5, color: P.text2, marginTop: 6 }}>
            <span><span style={{ display: 'inline-block', width: 7, height: 7, borderRadius: 2, background: P.grad, marginRight: 3 }} />收入</span>
            <span><span style={{ display: 'inline-block', width: 7, height: 7, borderRadius: 2, background: '#d5e2f5', marginRight: 3 }} />支出</span>
          </div>
        </Card>
        <Card style={{ marginTop: 10 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: P.text, marginBottom: 8 }}>支出分类占比</div>
          {[['餐饮美食', 46, '#256cff'], ['转账红包', 32, '#18c0e8'], ['花呗还款', 22, '#9db8e0']].map(([l, w, c]) => (
            <div key={l as string} style={{ marginBottom: 7 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9, color: P.text2, marginBottom: 3 }}>
                <span>{l}</span><span>{w}%</span>
              </div>
              <div style={{ height: 6, borderRadius: 3, background: P.fill, overflow: 'hidden' }}>
                <div style={{ width: `${w}%`, height: '100%', borderRadius: 3, background: c }} />
              </div>
            </div>
          ))}
        </Card>
      </div>
      <TabBar active={0} />
    </Phone>
  );
}

function ProfileDetailScreen() {
  return (
    <Phone title="个人详情页（新路由 /h5/profile-detail）" note="可编辑：昵称/性别/地区/个性签名（本地偏好）；只读：手机号/账户号（掩码）；不展示：密码/证件等敏感信息。">
      <div style={{ background: P.gradSoft, paddingBottom: 22 }}>
        <NavBar title="个人详情" light />
        <div style={{ textAlign: 'center', paddingBottom: 6 }}>
          <div style={{ display: 'inline-block' }}><Avatar kind={0} size={54} ring /></div>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#fff', marginTop: 6 }}>BBFPS</div>
        </div>
      </div>
      <div style={{ margin: '-10px 12px 0', position: 'relative', paddingBottom: 12 }}>
        <Card>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 8 }}>头像</div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            {[0, 1, 2, 3].map((k) => <Avatar key={k} kind={k} size={34} ring={k === 0} />)}
            <div style={{ width: 34, height: 34, borderRadius: '50%', border: `1.5px dashed ${P.text3}`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: P.text2 }}>
              <Ic n="camera" s={14} />
            </div>
          </div>
          <div style={{ fontSize: 8, color: P.text3, marginTop: 6 }}>上传：JPG/PNG/WebP ≤1MB，仅保存在当前浏览器</div>
        </Card>
        <Card style={{ marginTop: 10 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 4 }}>基本资料（可编辑）</div>
          {[['昵称', 'BBFPS'], ['地区', '河南 · 洛阳'], ['个性签名', '一句话介绍自己…']].map(([l, v]) => (
            <div key={l} style={{ display: 'flex', alignItems: 'center', padding: '8px 0', borderBottom: `1px solid ${P.divider}` }}>
              <span style={{ width: 56, fontSize: 10, color: P.text2 }}>{l}</span>
              <span style={{ flex: 1, fontSize: 10.5, color: P.text }}>{v}</span>
              <Ic n="chev" s={11} c={P.text3} />
            </div>
          ))}
          <div style={{ display: 'flex', alignItems: 'center', padding: '8px 0' }}>
            <span style={{ width: 56, fontSize: 10, color: P.text2 }}>性别</span>
            <div style={{ display: 'flex', gap: 6 }}>
              {['男', '女', '保密'].map((g, i) => (
                <span key={g} style={{ fontSize: 9.5, padding: '3px 12px', borderRadius: 20, background: i === 2 ? P.fill : '#fff', border: `1px solid ${i === 2 ? P.primary : P.divider}`, color: i === 2 ? P.primary : P.text2, fontWeight: 600 }}>{g}</span>
              ))}
            </div>
          </div>
        </Card>
        <Card style={{ marginTop: 10 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 4 }}>账户信息（只读）</div>
          {[['手机号', '187****0030'], ['账户号', '6252 54** **** 4209']].map(([l, v]) => (
            <div key={l} style={{ display: 'flex', alignItems: 'center', padding: '8px 0', borderBottom: `1px solid ${P.divider}` }}>
              <span style={{ width: 56, fontSize: 10, color: P.text2 }}>{l}</span>
              <span style={{ flex: 1, fontSize: 10.5, color: P.text }}>{v}</span>
              <Ic n="eyeOff" s={12} c={P.text3} />
            </div>
          ))}
          <div style={{ fontSize: 8, color: P.text3, marginTop: 6 }}>密码、证件号等敏感信息不展示</div>
        </Card>
        <div style={{ marginTop: 10, textAlign: 'center', padding: '9px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700 }}>保存</div>
      </div>
    </Phone>
  );
}

function MineScreen() {
  const entries: [string, string][] = [['shield', '身份绑定'], ['wallet', '余额'], ['huabei', '花呗'], ['card', '银行卡'], ['receipt', '账单'], ['setting', '设置']];
  return (
    <Phone title="我的页（重设计）" note="顶部头像+摘要点击进入个人详情；功能入口六项；TabBar 选中态。">
      <div style={{ background: P.gradSoft, paddingBottom: 30 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '16px 14px 8px' }}>
          <Avatar kind={0} size={44} />
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 13.5, fontWeight: 700, color: '#fff' }}>BBFPS</span>
              <span style={{ fontSize: 8, color: '#fff', background: 'rgba(255,255,255,0.22)', padding: '1.5px 6px', borderRadius: 8 }}>已绑定身份</span>
            </div>
            <div style={{ fontSize: 9, color: 'rgba(255,255,255,0.85)', marginTop: 3 }}>账户号 6252****4209 · 187****0030</div>
          </div>
          <Ic n="chev" s={14} c="#fff" />
        </div>
      </div>
      <div style={{ margin: '-20px 12px 0', position: 'relative' }}>
        <Card style={{ padding: '4px 6px' }}>
          {entries.map(([icon, label], i) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9.5px 8px', borderBottom: i < entries.length - 1 ? `1px solid ${P.divider}` : 'none' }}>
              <div style={{ width: 30, height: 30, borderRadius: 9, background: P.fill, color: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Ic n={icon} s={15} />
              </div>
              <span style={{ flex: 1, fontSize: 11.5, color: P.text, fontWeight: 500 }}>{label}</span>
              <Ic n="chev" s={12} c={P.text3} />
            </div>
          ))}
        </Card>
      </div>
      <div style={{ height: 12 }} />
      <TabBar active={3} />
    </Phone>
  );
}

function SettingsScreen() {
  const rows: [string, string?][] = [['版本信息', 'V1.0.0'], ['用户协议'], ['隐私政策'], ['修改登录密码'], ['修改支付密码']];
  return (
    <Phone title="设置页" note="固定内容入口 + 修改密码保留；底部「切换账号」（清除会话→登录页）与「退出登录」。">
      <div style={{ background: '#fff' }}><NavBar title="设置" /></div>
      <div style={{ padding: '10px 12px' }}>
        <Card style={{ padding: '4px 6px' }}>
          {rows.map(([label, value], i) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', padding: '10px 8px', borderBottom: i < rows.length - 1 ? `1px solid ${P.divider}` : 'none' }}>
              <span style={{ flex: 1, fontSize: 11.5, color: P.text }}>{label}</span>
              {value && <span style={{ fontSize: 10, color: P.text3, marginRight: 6 }}>{value}</span>}
              <Ic n="chev" s={12} c={P.text3} />
            </div>
          ))}
        </Card>
        <div style={{ marginTop: 16, textAlign: 'center', padding: '9px 0', borderRadius: 22, border: `1.5px solid ${P.primary}`, color: P.primary, fontSize: 12, fontWeight: 600, background: '#fff' }}>切换账号</div>
        <div style={{ marginTop: 10, textAlign: 'center', padding: '9px 0', borderRadius: 22, border: `1.5px solid ${P.danger}`, color: P.danger, fontSize: 12, fontWeight: 600, background: '#fff' }}>退出登录</div>
      </div>
    </Phone>
  );
}

export default function H5V2Core() {
  return (
    <Stack gap={22}>
      <H1>H5 V2 设计稿 2/4 · 核心页面</H1>
      <Text tone="secondary">
        科技蓝升级：主色 #256cff，头部柔渐变（160deg #2f74ff→#6fb6ff），卡片圆角 16 / 阴影 rgba(22,60,120,0.06)，
        页底 #f4f6fa。图标全部来自图标体系稿（h5-v2-icons），实现阶段 1:1 还原。
      </Text>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 18 }}>
        <HomeScreen />
        <WalletScreen />
        <RechargeScreen />
        <BillsScreen />
        <AnalyticsTabScreen />
        <ProfileDetailScreen />
        <MineScreen />
        <SettingsScreen />
      </div>
      <Divider />
      <Text tone="secondary" size="small">
        关键变更：首页去总资产改头像昵称 · 右上角扫码入口移除 · 钱包页充值/提现按钮上移 · 充值选卡改底部 Popup ·
        明细全站改称账单并合并分析 Tab · 新增个人详情页（/h5/profile-detail）· 设置页增加切换账号/退出登录。
      </Text>
    </Stack>
  );
}
