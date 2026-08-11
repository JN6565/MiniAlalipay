import { Divider, H1, Stack, Table, Text } from 'qoder/canvas';

/* ============================================================
 * H5 V2 设计稿 4/4：模块页（AI Talk/联系人/好友请求/花呗/银行卡/登录注册/改密/身份绑定/信息页）
 * 与 h5-v2-core / h5-v2-flows 同令牌体系；图标 path 与 h5-v2-icons 一致
 * ============================================================ */

const P = {
  primary: '#256cff',
  grad: 'linear-gradient(135deg, #256cff 0%, #18c0e8 100%)',
  gradSoft: 'linear-gradient(160deg, #2f74ff 0%, #4d9bff 55%, #6fb6ff 100%)',
  credit: 'linear-gradient(135deg, #7b6cff 0%, #4a8dff 100%)',
  aiDeep: 'linear-gradient(165deg, #0e1c3f 0%, #16306b 55%, #1f4a8f 100%)',
  text: '#16223a',
  text2: '#5a6b85',
  text3: '#94a3ba',
  bg: '#f4f6fa',
  divider: '#e8eef7',
  fill: '#eaf1ff',
  danger: '#f0484e',
  success: '#16b387',
};

const IP: Record<string, React.ReactNode> = {
  back: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  chev: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  search: (<><circle cx="11" cy="11" r="6.4" /><path d="m15.8 15.8 4.2 4.2" /></>),
  send: <path d="M4.5 11 19.5 4l-3.6 15.6-4.2-5.9ZM11.7 13.7 19.5 4" />,
  ai: (<><path d="M12 3.5c4.7 0 8.5 3.1 8.5 7 0 3.9-3.8 7-8.5 7-.9 0-1.9-.1-2.7-.4L5 19.5l.7-3.2c-1.4-1.3-2.2-3-2.2-4.8 0-3.9 3.8-8 8.5-8Z" /><path d="m12.6 8-2.4 3.4h3.6L11.4 15" /></>),
  huabei: (<><path d="M19.2 13.6A7.6 7.6 0 0 1 10.4 5 7.6 7.6 0 1 0 19.2 13.6Z" /><path d="m15.4 5.4.5 1.5 1.5.5-1.5.5-.5 1.5-.5-1.5-1.5-.5 1.5-.5Z" fill="currentColor" stroke="none" /></>),
  wallet: (<><path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h10A2.5 2.5 0 0 1 19 7.5v.3" /><path d="M4 8v9a2.5 2.5 0 0 0 2.5 2.5h11A2.5 2.5 0 0 0 20 17v-6.5A2.5 2.5 0 0 0 17.5 8H4Z" /><circle cx="16" cy="13.9" r="1.15" fill="currentColor" stroke="none" /></>),
  card: (<><rect x="3.4" y="5.4" width="17.2" height="13.2" rx="2.6" /><path d="M3.4 9.8h17.2M6.6 14.6h4" /></>),
  receipt: (<><path d="M6.4 3.8h11.2V20l-2.2-1.5L13.2 20l-2.2-1.5L8.8 20l-2.4-1.5Z" /><path d="M9.2 8.2h5.6M9.2 11.6h5.6M9.2 15h3.2" /></>),
  shield: (<><path d="M12 3.6 19 6v5.4c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6Z" /><path d="m9.2 11.8 2 2 3.6-3.8" /></>),
  chart: (<><path d="M4 4v15a1 1 0 0 0 1 1h15" /><path d="M8 15.5v-4M12.5 15.5V8M17 15.5v-6.5" /></>),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  plus: <path d="M12 5.5v13M5.5 12h13" />,
  eyeOn: (<><path d="M3 12c1.2-2.8 4.6-6 9-6s7.8 3.2 9 6c-1.2 2.8-4.6 6-9 6s-7.8-3.2-9-6Z" /><circle cx="12" cy="12" r="2.8" /></>),
  eyeOff: (<><path d="M4 5.5 20 19" /><path d="M9.2 6.4A8.6 8.6 0 0 1 12 6c4.4 0 7.8 3.2 9 6-.4 1-1.1 2.1-2 3.1M6.3 8.1C5 9.3 4 10.7 3 12c1.2 2.8 4.6 6 9 6 1 0 2-.2 2.9-.5" /><path d="M10 10.3a2.8 2.8 0 0 0 3.9 3.9" /></>),
  clock: (<><circle cx="12" cy="12" r="8.4" /><path d="M12 7.6V12l3 2.2" /></>),
  more: <path d="M5.5 12h.01M12 12h.01M18.5 12h.01" strokeWidth="2.6" />,
  drawer: <path d="M4 6.5h16M4 12h11M4 17.5h16" />,
  lock: (<><rect x="5.4" y="10.4" width="13.2" height="9.6" rx="2.6" /><path d="M8.4 10.4V8a3.6 3.6 0 0 1 7.2 0v2.4" /></>),
  camera: (<><path d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.4l1.3-2h5.8l1.3 2h1.4A2.4 2.4 0 0 1 20 8.4v8.2a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 16.6Z" /><circle cx="12" cy="12.4" r="3.2" /></>),
};

function Ic(props: { n: string; s?: number; c?: string; w?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={props.s ?? 20} height={props.s ?? 20} fill="none" stroke={props.c ?? 'currentColor'} strokeWidth={props.w ?? 1.8} strokeLinecap="round" strokeLinejoin="round">
      {IP[props.n]}
    </svg>
  );
}

function Phone(props: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div style={{ minWidth: 0 }}>
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

function NavBar(props: { title: string; light?: boolean; right?: string }) {
  const c = props.light ? '#fff' : P.text;
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', color: c }}>
      <Ic n="back" s={16} />
      <span style={{ fontSize: 13, fontWeight: 600 }}>{props.title}</span>
      {props.right ? <Ic n={props.right} s={16} /> : <span style={{ width: 16 }} />}
    </div>
  );
}

function Avatar(props: { kind: number; size?: number }) {
  const s = props.size ?? 32;
  const grads = ['linear-gradient(135deg,#3d7bff,#22c3e6)', 'linear-gradient(135deg,#ffb648,#ff7a59)', 'linear-gradient(135deg,#2ed3a3,#12a4c9)', 'linear-gradient(135deg,#9a7bff,#5f6cff)'];
  return (
    <div style={{ width: s, height: s, borderRadius: '50%', background: grads[props.kind % 4], display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
      <svg viewBox="0 0 24 24" width={s * 0.58} height={s * 0.58} fill="#fff" opacity={0.94}>
        <circle cx="12" cy="9" r="4" />
        <path d="M4.5 21c1-4.3 4-6.5 7.5-6.5s6.5 2.2 7.5 6.5Z" />
      </svg>
    </div>
  );
}

function Row(props: { label: string; value?: string; icon?: string; last?: boolean; danger?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '11px 2px', borderBottom: props.last ? 'none' : `1px solid ${P.divider}` }}>
      {props.icon && (
        <div style={{ width: 30, height: 30, borderRadius: 9, background: P.fill, color: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
          <Ic n={props.icon} s={16} />
        </div>
      )}
      <span style={{ fontSize: 11.5, color: props.danger ? P.danger : P.text, flex: 1, fontWeight: 500 }}>{props.label}</span>
      {props.value && <span style={{ fontSize: 10.5, color: P.text3 }}>{props.value}</span>}
      {!props.danger && <Ic n="chev" s={13} c={P.text3} />}
    </div>
  );
}

/* ---- AI Talk：主对话页（高端简约） ---- */
function AITalkScreen() {
  return (
    <Phone title="AI Talk · 对话页（高端大气简约）" note="深蓝渐变沉浸头部+柔光氛围；欢迎区居中（发光 Orb + 问候语 + 快捷建议 chips）；气泡左 AI 白卡 / 右用户渐变；底部输入区悬浮圆角。">
      <div style={{ background: P.aiDeep, minHeight: 460, display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', color: '#fff' }}>
          <Ic n="drawer" s={16} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Ic n="ai" s={15} c="#8fc2ff" />
            <span style={{ fontSize: 13, fontWeight: 600 }}>小智 AI 助手</span>
          </div>
          <Ic n="more" s={16} />
        </div>
        {/* 欢迎区 */}
        <div style={{ textAlign: 'center', padding: '20px 18px 12px' }}>
          <div style={{ width: 52, height: 52, margin: '0 auto 10px', borderRadius: '50%', background: 'radial-gradient(circle at 32% 28%, #9fd2ff 0%, #4d9bff 45%, #256cff 100%)', boxShadow: '0 0 26px rgba(77,155,255,0.55)' }} />
          <div style={{ fontSize: 14, fontWeight: 700, color: '#fff' }}>你好，我是小智</div>
          <div style={{ fontSize: 10, color: 'rgba(255,255,255,0.62)', marginTop: 4 }}>转账 · 查账单 · 花呗还款，说句话就能办</div>
          <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginTop: 12, flexWrap: 'wrap' }}>
            {['帮我转 100 元给小李', '本月花了多少', '花呗什么时候还'].map((t) => (
              <span key={t} style={{ fontSize: 9, color: '#cfe3ff', border: '1px solid rgba(143,194,255,0.35)', borderRadius: 14, padding: '5px 10px', background: 'rgba(255,255,255,0.06)' }}>{t}</span>
            ))}
          </div>
        </div>
        {/* 气泡区 */}
        <div style={{ flex: 1, padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 9 }}>
          <div style={{ alignSelf: 'flex-end', maxWidth: '72%', background: P.grad, color: '#fff', fontSize: 10.5, lineHeight: 1.6, padding: '8px 11px', borderRadius: '14px 4px 14px 14px', boxShadow: '0 4px 12px rgba(37,108,255,0.35)' }}>
            帮我转 100 元给小李
          </div>
          <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
            <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'radial-gradient(circle at 32% 28%, #9fd2ff, #256cff)', flex: 'none', marginTop: 2 }} />
            <div style={{ maxWidth: '78%', background: '#fff', color: P.text, fontSize: 10.5, lineHeight: 1.6, padding: '8px 11px', borderRadius: '4px 14px 14px 14px', boxShadow: '0 2px 10px rgba(8,20,50,0.25)' }}>
              好的，已为你生成转账确认单，请核对收款人与金额。
              {/* 确认卡片 */}
              <div style={{ marginTop: 8, border: `1px solid ${P.divider}`, borderRadius: 12, padding: 10, background: '#fafcff' }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: P.text, marginBottom: 7 }}>转账确认</div>
                {[['收款人', '小李（138****6688）'], ['金额', '¥100.00'], ['支付方式', '账户余额']].map(([k, v]) => (
                  <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9.5, padding: '2.5px 0' }}>
                    <span style={{ color: P.text3 }}>{k}</span>
                    <span style={{ color: P.text, fontWeight: 500 }}>{v}</span>
                  </div>
                ))}
                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                  <div style={{ flex: 1, textAlign: 'center', fontSize: 10, padding: '6px 0', borderRadius: 16, border: `1px solid ${P.divider}`, color: P.text2 }}>取消</div>
                  <div style={{ flex: 1, textAlign: 'center', fontSize: 10, padding: '6px 0', borderRadius: 16, background: P.grad, color: '#fff', fontWeight: 700, boxShadow: '0 4px 10px rgba(37,108,255,0.3)' }}>确认支付</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        {/* 输入区 */}
        <div style={{ padding: '8px 12px 14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.14)', borderRadius: 22, padding: '8px 8px 8px 14px' }}>
            <span style={{ flex: 1, fontSize: 10.5, color: 'rgba(255,255,255,0.45)' }}>和小智说点什么…</span>
            <div style={{ width: 30, height: 30, borderRadius: '50%', background: P.grad, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', boxShadow: '0 4px 10px rgba(37,108,255,0.4)' }}>
              <Ic n="send" s={14} />
            </div>
          </div>
        </div>
      </div>
    </Phone>
  );
}

/* ---- AI Talk：会话抽屉 ---- */
function AITalkDrawerScreen() {
  return (
    <Phone title="AI Talk · 会话抽屉" note="左侧滑出抽屉（宽度 72%），右侧半透明深色遮罩；顶部「新建会话」渐变按钮，下方会话列表（标题+时间），当前会话高亮浅蓝底。">
      <div style={{ position: 'relative', background: P.aiDeep, minHeight: 430 }}>
        <div style={{ position: 'absolute', inset: 0, background: 'rgba(8,16,38,0.55)' }} />
        <div style={{ position: 'relative', width: '74%', height: 430, background: '#fff', borderRadius: '0 18px 18px 0', padding: 12, display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
            <Ic n="ai" s={15} c={P.primary} />
            <span style={{ fontSize: 12, fontWeight: 700, color: P.text }}>历史会话</span>
          </div>
          <div style={{ textAlign: 'center', padding: '8px 0', borderRadius: 18, background: P.grad, color: '#fff', fontSize: 11, fontWeight: 700, boxShadow: '0 5px 12px rgba(37,108,255,0.28)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5 }}>
            <Ic n="plus" s={13} />新建会话
          </div>
          <div style={{ marginTop: 10, flex: 1 }}>
            {[
              { t: '给小李转账 100 元', d: '今天 14:20', on: true },
              { t: '查询本月支出', d: '昨天 21:08', on: false },
              { t: '花呗还款提醒', d: '8月12日', on: false },
              { t: '绑定银行卡咨询', d: '8月10日', on: false },
            ].map((s) => (
              <div key={s.t} style={{ padding: '9px 10px', borderRadius: 12, background: s.on ? P.fill : 'transparent', marginBottom: 4 }}>
                <div style={{ fontSize: 10.5, color: s.on ? P.primary : P.text, fontWeight: s.on ? 600 : 500, overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{s.t}</div>
                <div style={{ fontSize: 8.5, color: P.text3, marginTop: 2 }}>{s.d}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Phone>
  );
}

/* ---- 联系人页 ---- */
function ContactsScreen() {
  const groups: Array<[string, Array<[string, string, number]>]> = [
    ['A', [['安琪', '139****2211', 1]]],
    ['L', [['小李', '138****6688', 0], ['刘敏', '150****7799', 2]]],
    ['Z', [['张伟', '137****3344', 3]]],
  ];
  return (
    <Phone title="联系人页（重设计）" note="顶部搜索框；按首字母分组（字母吸顶小标题）；头像+昵称+掩码手机号；右侧悬浮字母索引条；点击进入转账选人。">
      <div style={{ background: P.gradSoft, paddingBottom: 16 }}>
        <NavBar title="联系人" light />
      </div>
      <div style={{ margin: '-8px 12px 0', paddingBottom: 12, position: 'relative' }}>
        <Card style={{ padding: '10px 12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, background: P.bg, borderRadius: 20, padding: '8px 12px' }}>
            <Ic n="search" s={13} c={P.text3} />
            <span style={{ fontSize: 10.5, color: P.text3 }}>搜索联系人</span>
          </div>
          <div style={{ marginTop: 8 }}>
            {groups.map(([letter, items]) => (
              <div key={letter}>
                <div style={{ fontSize: 9.5, color: P.text3, fontWeight: 600, padding: '8px 2px 4px' }}>{letter}</div>
                {items.map(([name, phone, kind]) => (
                  <div key={name} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 2px', borderBottom: `1px solid ${P.divider}` }}>
                    <Avatar kind={kind} size={32} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 11.5, fontWeight: 600, color: P.text }}>{name}</div>
                      <div style={{ fontSize: 9, color: P.text3, marginTop: 1 }}>{phone}</div>
                    </div>
                    <span style={{ fontSize: 9, color: P.primary, background: P.fill, borderRadius: 12, padding: '3px 9px', fontWeight: 600 }}>转账</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </Card>
        <div style={{ position: 'absolute', right: 2, top: 70, display: 'flex', flexDirection: 'column', gap: 7, alignItems: 'center' }}>
          {['A', 'L', 'Z'].map((l, i) => (
            <span key={l} style={{ fontSize: 8.5, fontWeight: i === 1 ? 700 : 400, color: i === 1 ? P.primary : P.text3, background: i === 1 ? P.fill : 'transparent', borderRadius: '50%', width: 15, height: 15, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{l}</span>
          ))}
        </div>
      </div>
    </Phone>
  );
}

/* ---- 好友请求页 ---- */
function FriendRequestsScreen() {
  return (
    <Phone title="好友请求页" note="请求列表：头像+昵称+附言；未处理显示「接受」渐变按钮，已处理显示「已添加」灰态；空态用线性插画+文案。">
      <div style={{ background: P.gradSoft, paddingBottom: 16 }}>
        <NavBar title="好友请求" light />
      </div>
      <div style={{ margin: '-8px 12px 0', paddingBottom: 12 }}>
        <Card>
          {[
            { name: '王芳', msg: '我是王芳，加个好友', done: false, kind: 1 },
            { name: '陈杰', msg: '转账需要添加好友', done: false, kind: 2 },
            { name: '赵雪', msg: '好久不见', done: true, kind: 3 },
          ].map((r, i) => (
            <div key={r.name} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 2px', borderBottom: i === 2 ? 'none' : `1px solid ${P.divider}` }}>
              <Avatar kind={r.kind} size={34} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11.5, fontWeight: 600, color: P.text }}>{r.name}</div>
                <div style={{ fontSize: 9, color: P.text3, marginTop: 1, overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{r.msg}</div>
              </div>
              {r.done ? (
                <span style={{ fontSize: 9.5, color: P.text3 }}>已添加</span>
              ) : (
                <span style={{ fontSize: 9.5, color: '#fff', background: P.grad, borderRadius: 14, padding: '5px 13px', fontWeight: 700, boxShadow: '0 4px 10px rgba(37,108,255,0.28)' }}>接受</span>
              )}
            </div>
          ))}
        </Card>
      </div>
    </Phone>
  );
}

/* ---- 花呗首页 ---- */
function HuabeiHomeScreen() {
  return (
    <Phone title="花呗首页（重设计）" note="紫蓝渐变（credit 令牌）沉浸头部：本月应还大字+还款日；额度行（总额度/可用）；下方白卡入口：账单/还款；底部「立即还款」渐变按钮。">
      <div style={{ background: P.credit, paddingBottom: 44 }}>
        <NavBar title="花呗" light />
        <div style={{ textAlign: 'center', color: '#fff', padding: '8px 0 4px' }}>
          <div style={{ fontSize: 9.5, opacity: 0.85 }}>本月应还（元）</div>
          <div style={{ fontSize: 26, fontWeight: 700, margin: '4px 0', letterSpacing: 0.5 }}>268.50</div>
          <div style={{ fontSize: 9, opacity: 0.8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
            <Ic n="clock" s={11} />9月9日自动还款
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 26, marginTop: 12, color: '#fff' }}>
          {[['总额度', '¥3,000.00'], ['可用额度', '¥2,731.50']].map(([k, v]) => (
            <div key={k} style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 8.5, opacity: 0.75 }}>{k}</div>
              <div style={{ fontSize: 11.5, fontWeight: 700, marginTop: 2 }}>{v}</div>
            </div>
          ))}
        </div>
      </div>
      <div style={{ margin: '-30px 12px 0', paddingBottom: 12 }}>
        <Card>
          <Row icon="receipt" label="花呗账单" value="查看每月消费明细" />
          <Row icon="wallet" label="立即还款" value="支持余额/银行卡" last />
        </Card>
        <div style={{ marginTop: 12, textAlign: 'center', padding: '10px 0', borderRadius: 24, background: P.credit, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 6px 16px rgba(90,108,255,0.35)' }}>
          立即还款
        </div>
      </div>
    </Phone>
  );
}

/* ---- 花呗账单 ---- */
function HuabeiBillScreen() {
  return (
    <Phone title="花呗账单页" note="月份切换胶囊；月汇总（消费总额/已还/待还）；消费列表（商户+时间+金额），点击进账单详情（复用全局详情样式）。">
      <NavBar title="花呗账单" />
      <div style={{ padding: '0 12px 12px' }}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
          {['2026-06', '2026-07', '2026-08'].map((m, i) => (
            <span key={m} style={{ fontSize: 9.5, padding: '4px 11px', borderRadius: 13, fontWeight: i === 2 ? 700 : 400, color: i === 2 ? '#fff' : P.text2, background: i === 2 ? P.credit : '#fff' }}>{m}</span>
          ))}
        </div>
        <Card style={{ marginBottom: 8 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', textAlign: 'center' }}>
            {[['消费总额', '¥458.50', P.text], ['已还款', '¥190.00', P.success], ['待还款', '¥268.50', '#7b6cff']].map(([k, v, c]) => (
              <div key={k as string} style={{ flex: 1 }}>
                <div style={{ fontSize: 8.5, color: P.text3 }}>{k}</div>
                <div style={{ fontSize: 12, fontWeight: 700, marginTop: 2, color: c as string }}>{v}</div>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          {[
            { m: '星巴克咖啡', d: '08-15 10:22', a: '-¥38.00' },
            { m: '线上超市购物', d: '08-12 19:40', a: '-¥156.50' },
            { m: '还款', d: '08-09 09:00', a: '+¥190.00', rep: true },
          ].map((b, i) => (
            <div key={b.m} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 2px', borderBottom: i === 2 ? 'none' : `1px solid ${P.divider}` }}>
              <div style={{ width: 30, height: 30, borderRadius: 9, background: b.rep ? '#e6f8f2' : '#f3efff', color: b.rep ? P.success : '#7b6cff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Ic n={b.rep ? 'check' : 'receipt'} s={15} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: P.text }}>{b.m}</div>
                <div style={{ fontSize: 8.5, color: P.text3, marginTop: 1 }}>{b.d}</div>
              </div>
              <span style={{ fontSize: 11.5, fontWeight: 700, color: b.rep ? P.success : P.text }}>{b.a}</span>
            </div>
          ))}
        </Card>
      </div>
    </Phone>
  );
}

/* ---- 花呗还款 ---- */
function HuabeiRepayScreen() {
  return (
    <Phone title="花呗还款页" note="还款金额卡（默认全部待还，可改）；支付方式单选（余额/银行卡）；6 位支付密码格；确认还款渐变按钮。">
      <NavBar title="花呗还款" />
      <div style={{ padding: '0 12px 12px' }}>
        <Card style={{ marginBottom: 8, textAlign: 'center' }}>
          <div style={{ fontSize: 9, color: P.text3 }}>本月待还</div>
          <div style={{ fontSize: 24, fontWeight: 700, color: P.text, margin: '4px 0' }}>¥268.50</div>
          <div style={{ display: 'inline-block', fontSize: 9.5, color: '#7b6cff', background: '#f3efff', borderRadius: 12, padding: '4px 12px', fontWeight: 600 }}>修改金额</div>
        </Card>
        <Card style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 6 }}>支付方式</div>
          {[
            { label: '账户余额', sub: '¥3,990.00', icon: 'wallet', on: true },
            { label: '工商银行（5345）', sub: '储蓄卡', icon: 'card', on: false },
          ].map((m) => (
            <div key={m.label} style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '8px 9px', borderRadius: 11, border: `1.5px solid ${m.on ? P.primary : P.divider}`, background: m.on ? P.fill : '#fff', marginBottom: 6 }}>
              <Ic n={m.icon} s={16} c={m.on ? P.primary : P.text3} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 10.5, fontWeight: 600, color: P.text }}>{m.label}</div>
                <div style={{ fontSize: 8.5, color: P.text3 }}>{m.sub}</div>
              </div>
              <div style={{ width: 16, height: 16, borderRadius: '50%', border: m.on ? `5px solid ${P.primary}` : `1.5px solid ${P.text3}` }} />
            </div>
          ))}
        </Card>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 8, textAlign: 'center' }}>请输入支付密码</div>
          <div style={{ display: 'flex', gap: 5, justifyContent: 'center' }}>
            {[1, 1, 1, 0, 0, 0].map((f, i) => (
              <div key={i} style={{ width: 30, height: 34, borderRadius: 8, border: `1px solid ${P.divider}`, background: P.bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {f ? <div style={{ width: 6, height: 6, borderRadius: '50%', background: P.text }} /> : null}
              </div>
            ))}
          </div>
        </Card>
        <div style={{ textAlign: 'center', padding: '10px 0', borderRadius: 24, background: P.credit, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 6px 16px rgba(90,108,255,0.35)' }}>
          确认还款 ¥268.50
        </div>
      </div>
    </Phone>
  );
}

/* ---- 银行卡详情 ---- */
function BankCardDetailScreen() {
  return (
    <Phone title="银行卡详情页" note="大卡面（银行渐变底+完整卡号默认掩码，眼睛切换需二次确认）；卡信息行（类型/预留手机掩码）；入口：账单/解绑（红字）。">
      <NavBar title="银行卡详情" right="more" />
      <div style={{ padding: '0 12px 12px' }}>
        <div style={{ borderRadius: 16, padding: 14, color: '#fff', background: 'linear-gradient(135deg,#e0455c 0%,#ff8a5c 100%)', boxShadow: '0 8px 20px rgba(224,69,92,0.3)', marginBottom: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12, fontWeight: 700 }}>工商银行</span>
            <span style={{ fontSize: 8.5, background: 'rgba(255,255,255,0.22)', padding: '2px 8px', borderRadius: 9 }}>储蓄卡</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 16, letterSpacing: 2.4, fontFamily: 'Courier New, monospace', margin: '14px 0 10px' }}>
            <span>6222 **** **** 5345</span>
            <Ic n="eyeOff" s={15} c="rgba(255,255,255,0.9)" />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 8.5, opacity: 0.9 }}>
            <span>预留手机 138****6688</span>
            <span>余额 ¥12,800.00</span>
          </div>
        </div>
        <Card>
          <Row icon="receipt" label="交易账单" value="查看该卡收支" />
          <Row icon="shield" label="卡片安全" value="已启用验签" last />
        </Card>
        <div style={{ marginTop: 12, textAlign: 'center', padding: '10px 0', borderRadius: 24, border: `1.5px solid ${P.danger}`, color: P.danger, fontSize: 12, fontWeight: 600 }}>
          解除绑定
        </div>
      </div>
    </Phone>
  );
}

/* ---- 银行卡账单 ---- */
function BankCardBillScreen() {
  return (
    <Phone title="银行卡账单页" note="与全局账单页同款列表结构（月分组+方向 chips）；金额为该卡相关充值/提现/支付流水。">
      <NavBar title="银行卡账单" />
      <div style={{ padding: '0 12px 12px' }}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
          {['全部', '收入', '支出'].map((t, i) => (
            <span key={t} style={{ fontSize: 9.5, padding: '4px 12px', borderRadius: 13, fontWeight: i === 0 ? 700 : 400, color: i === 0 ? '#fff' : P.text2, background: i === 0 ? P.primary : '#fff' }}>{t}</span>
          ))}
        </div>
        <Card>
          <div style={{ fontSize: 9.5, fontWeight: 700, color: P.text2, padding: '2px 2px 6px' }}>2026年8月</div>
          {[
            { m: '账户充值', d: '08-16 11:02', a: '-¥500.00', out: true },
            { m: '转账支出', d: '08-14 16:30', a: '-¥200.00', out: true },
            { m: '提现入账', d: '08-10 09:15', a: '+¥1,000.00', out: false },
          ].map((b, i) => (
            <div key={b.m} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 2px', borderBottom: i === 2 ? 'none' : `1px solid ${P.divider}` }}>
              <div style={{ width: 30, height: 30, borderRadius: 9, background: P.fill, color: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Ic n={b.out ? 'send' : 'wallet'} s={14} />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 11, fontWeight: 600, color: P.text }}>{b.m}</div>
                <div style={{ fontSize: 8.5, color: P.text3, marginTop: 1 }}>{b.d}</div>
              </div>
              <span style={{ fontSize: 11.5, fontWeight: 700, color: b.out ? P.text : P.danger }}>{b.a}</span>
            </div>
          ))}
        </Card>
      </div>
    </Phone>
  );
}

/* ---- 绑定银行卡 ---- */
function AddBankCardScreen() {
  return (
    <Phone title="绑定银行卡页" note="表单卡：卡号（自动四位分组）/开户行/预留手机号/短信验证码；验证码同样为前端模拟（Toast 展示演示码+60s 倒计时，后端零变更）；勾选协议后「确认绑定」渐变按钮；完整卡号验签流程保留。">
      <NavBar title="绑定银行卡" />
      <div style={{ padding: '0 12px 12px' }}>
        <Card>
          {[
            { label: '银行卡号', value: '6222 0202 0000 5345', icon: 'card' },
            { label: '开户银行', value: '中国工商银行', icon: 'shield' },
            { label: '预留手机号', value: '138 0000 6688', icon: 'phone2' },
          ].map((f, i) => (
            <div key={f.label}>
              <div style={{ fontSize: 9, color: P.text3, padding: '7px 2px 4px' }}>{f.label}</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 11, padding: '9px 11px' }}>
                <span style={{ fontSize: 11, color: P.text, flex: 1, fontFamily: i === 0 ? 'Courier New, monospace' : 'inherit', letterSpacing: i === 0 ? 1 : 0 }}>{f.value}</span>
                {i === 0 && <Ic n="camera" s={14} c={P.text3} />}
              </div>
            </div>
          ))}
          <div style={{ fontSize: 9, color: P.text3, padding: '7px 2px 4px' }}>短信验证码</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 11, padding: '9px 11px' }}>
              <span style={{ fontSize: 11, color: P.text, letterSpacing: 2 }}>4 8 2 9</span>
            </div>
            <span style={{ fontSize: 9.5, color: P.primary, fontWeight: 600, flex: 'none' }}>56s 后重发</span>
          </div>
        </Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, margin: '10px 4px' }}>
          <div style={{ width: 13, height: 13, borderRadius: 4, background: P.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
            <Ic n="check" s={9} w={2.6} />
          </div>
          <span style={{ fontSize: 8.5, color: P.text3 }}>我已阅读并同意《银行卡绑定协议》</span>
        </div>
        <div style={{ textAlign: 'center', padding: '10px 0', borderRadius: 24, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 6px 16px rgba(37,108,255,0.3)' }}>
          确认绑定
        </div>
      </div>
    </Phone>
  );
}

/* ---- 登录/注册 ---- */
function AuthScreens() {
  return (
    <>
      <Phone title="登录页（重设计）" note="渐变头图+品牌 Orb 标识；登录表单卡（手机号/密码+眼睛切换）；主按钮渐变；底部「注册账号」链接；错误提示红色可执行文案。">
        <div style={{ background: P.gradSoft, padding: '26px 0 40px', textAlign: 'center' }}>
          <div style={{ width: 46, height: 46, margin: '0 auto 8px', borderRadius: 14, background: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
            <Ic n="wallet" s={24} />
          </div>
          <div style={{ fontSize: 15, fontWeight: 700, color: '#fff' }}>MiniAI 支付</div>
          <div style={{ fontSize: 9.5, color: 'rgba(255,255,255,0.8)', marginTop: 3 }}>安全 · 便捷 · 智能</div>
        </div>
        <div style={{ margin: '-22px 14px 0', paddingBottom: 16 }}>
          <Card style={{ padding: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 12, padding: '10px 12px', marginBottom: 9 }}>
              <span style={{ fontSize: 11, color: P.text, flex: 1 }}>138 0000 6688</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 12, padding: '10px 12px' }}>
              <span style={{ fontSize: 11, color: P.text3, flex: 1, letterSpacing: 2 }}>••••••••</span>
              <Ic n="eyeOff" s={14} c={P.text3} />
            </div>
            <div style={{ textAlign: 'right', fontSize: 9, color: P.primary, margin: '7px 2px 10px' }}>忘记密码？</div>
            <div style={{ textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12.5, fontWeight: 700, boxShadow: '0 6px 16px rgba(37,108,255,0.3)' }}>
              登录
            </div>
            <div style={{ textAlign: 'center', fontSize: 9.5, color: P.text3, marginTop: 11 }}>
              还没有账号？<span style={{ color: P.primary, fontWeight: 600 }}>立即注册</span>
            </div>
          </Card>
        </div>
      </Phone>
      <Phone title="注册页（重设计）" note="与登录页同款骨架；字段：手机号/验证码/登录密码/确认密码；验证码为前端模拟：点击获取后 Toast 展示 4 位演示码（无真实短信，后端零变更）+60s 倒计时；错误提示可执行（如「该手机号已注册，请直接登录」）。">
        <div style={{ background: P.gradSoft, padding: '22px 0 36px', textAlign: 'center' }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: '#fff' }}>创建账号</div>
          <div style={{ fontSize: 9.5, color: 'rgba(255,255,255,0.8)', marginTop: 3 }}>一分钟完成注册</div>
        </div>
        <div style={{ margin: '-22px 14px 0', paddingBottom: 16 }}>
          <Card style={{ padding: 14 }}>
            {['手机号', '登录密码', '确认密码'].map((f) => (
              <div key={f} style={{ display: 'flex', alignItems: 'center', background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 12, padding: '10px 12px', marginBottom: 9 }}>
                <span style={{ fontSize: 11, color: P.text3, flex: 1 }}>{f}</span>
              </div>
            ))}
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 12, padding: '10px 12px' }}>
                <span style={{ fontSize: 11, color: P.text3 }}>短信验证码</span>
              </div>
              <span style={{ fontSize: 9.5, color: P.primary, fontWeight: 600, alignSelf: 'center', flex: 'none' }}>获取验证码</span>
            </div>
            <div style={{ textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12.5, fontWeight: 700, boxShadow: '0 6px 16px rgba(37,108,255,0.3)' }}>
              注册
            </div>
          </Card>
        </div>
      </Phone>
    </>
  );
}

/* ---- 修改密码（登录密码 / 支付密码同款） ---- */
function ChangePasswordScreen() {
  return (
    <Phone title="修改登录密码 / 修改支付密码（同款骨架）" note="三字段：原密码/新密码/确认密码，均带眼睛切换；密码强度提示条；提交后 Toast 成功并返回设置页。支付密码为 6 位数字，输入态为密码格。">
      <NavBar title="修改登录密码" />
      <div style={{ padding: '4px 12px 12px' }}>
        <Card>
          {[
            { label: '原密码', v: '••••••••' },
            { label: '新密码', v: '' },
            { label: '确认新密码', v: '' },
          ].map((f, i) => (
            <div key={f.label}>
              <div style={{ fontSize: 9, color: P.text3, padding: '7px 2px 4px' }}>{f.label}</div>
              <div style={{ display: 'flex', alignItems: 'center', background: P.bg, border: `1.5px solid ${i === 1 ? P.primary : P.divider}`, borderRadius: 11, padding: '10px 12px' }}>
                <span style={{ fontSize: 11, color: f.v ? P.text : P.text3, flex: 1, letterSpacing: 2 }}>{f.v || '请输入'}</span>
                <Ic n={f.v ? 'eyeOn' : 'eyeOff'} s={14} c={P.text3} />
              </div>
            </div>
          ))}
          <div style={{ display: 'flex', gap: 4, margin: '10px 2px 4px' }}>
            {[P.success, P.success, P.warning, '#e2e8f2'].map((c, i) => (
              <div key={i} style={{ flex: 1, height: 3, borderRadius: 2, background: c }} />
            ))}
          </div>
          <div style={{ fontSize: 8.5, color: P.text3, marginBottom: 2 }}>密码强度：中 · 建议包含字母与数字，长度 ≥ 8 位</div>
        </Card>
        <div style={{ marginTop: 14, textAlign: 'center', padding: '10px 0', borderRadius: 24, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 6px 16px rgba(37,108,255,0.3)' }}>
          确认修改
        </div>
      </div>
    </Phone>
  );
}

/* ---- 身份绑定页 ---- */
function IdentityBindScreen() {
  return (
    <Phone title="身份绑定页" note="认证状态头（已认证：绿色盾牌徽章）；证件信息只读掩码展示（姓名/证件号）；未认证态显示表单引导；信息不可自助修改，提示联系客服。">
      <NavBar title="身份绑定" />
      <div style={{ padding: '4px 12px 12px' }}>
        <div style={{ borderRadius: 16, padding: 14, background: 'linear-gradient(135deg,#e9fff6 0%,#f2fbff 100%)', border: '1px solid #d2f2e6', marginBottom: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <div style={{ width: 36, height: 36, borderRadius: 12, background: P.success, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Ic n="shield" s={19} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: P.text }}>已完成实名认证</div>
              <div style={{ fontSize: 9, color: P.text3, marginTop: 2 }}>认证时间 2026-08-01 10:22</div>
            </div>
            <span style={{ fontSize: 9, color: P.success, background: '#e6f8f2', borderRadius: 10, padding: '3px 9px', fontWeight: 700 }}>已认证</span>
          </div>
        </div>
        <Card>
          {[['真实姓名', '王 *'], ['证件类型', '居民身份证'], ['证件号码', '110***********0032']].map(([k, v], i) => (
            <div key={k} style={{ display: 'flex', alignItems: 'center', padding: '10px 2px', borderBottom: i === 2 ? 'none' : `1px solid ${P.divider}` }}>
              <span style={{ fontSize: 10.5, color: P.text3, flex: 1 }}>{k}</span>
              <span style={{ fontSize: 11, color: P.text, fontWeight: 500 }}>{v}</span>
            </div>
          ))}
        </Card>
        <div style={{ fontSize: 9, color: P.text3, padding: '10px 6px 0', lineHeight: 1.7 }}>
          实名信息提交后不可自助修改，如需变更请联系客服处理。
        </div>
      </div>
    </Phone>
  );
}

/* ---- 版本信息 / 协议 / 隐私 / 关于 ---- */
function InfoPagesScreen() {
  return (
    <Phone title="版本信息 / 用户协议 / 隐私政策 / 关于（统一模板）" note="版本信息页：大版本号+应用图标+更新说明列表；协议/隐私/关于共用长文阅读模板（标题+正文段落），顶部返回导航。">
      <NavBar title="版本信息" />
      <div style={{ padding: '8px 12px 14px', textAlign: 'center' }}>
        <div style={{ width: 52, height: 52, margin: '8px auto 8px', borderRadius: 15, background: P.grad, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', boxShadow: '0 8px 18px rgba(37,108,255,0.3)' }}>
          <Ic n="wallet" s={26} />
        </div>
        <div style={{ fontSize: 13, fontWeight: 700, color: P.text }}>MiniAI 支付</div>
        <div style={{ fontSize: 10, color: P.text3, margin: '3px 0 12px' }}>当前版本 V1.0.0</div>
        <Card style={{ textAlign: 'left' }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, color: P.text, marginBottom: 6 }}>版本更新说明</div>
          {['全新视觉升级，卡片质感与图标体系焕新', '账单页新增收支分析视图', '扫一扫支持手动输入码内容'].map((t) => (
            <div key={t} style={{ display: 'flex', gap: 7, alignItems: 'flex-start', padding: '4px 0' }}>
              <div style={{ width: 4, height: 4, borderRadius: '50%', background: P.primary, marginTop: 5, flex: 'none' }} />
              <span style={{ fontSize: 10, color: P.text2, lineHeight: 1.6 }}>{t}</span>
            </div>
          ))}
        </Card>
        <div style={{ marginTop: 12, padding: 12, background: '#fff', borderRadius: 16, boxShadow: '0 2px 12px rgba(22,60,120,0.06)', textAlign: 'left' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: P.text, marginBottom: 6 }}>用户协议（长文模板示意）</div>
          <div style={{ fontSize: 9.5, color: P.text2, lineHeight: 1.9 }}>
            欢迎使用 MiniAI 支付。在使用本服务前，请您仔细阅读并充分理解本协议各条款。当您按照注册页面提示填写信息、阅读并同意本协议且完成全部注册程序后，即表示您已充分阅读、接受本协议的全部内容……
          </div>
          <div style={{ textAlign: 'center', fontSize: 9, color: P.text3, marginTop: 8 }}>— 协议/隐私政策/关于页共用此阅读模板 —</div>
        </div>
      </div>
    </Phone>
  );
}

/* ---- 汇总 ---- */

export default function H5V2Modules() {
  return (
    <Stack gap={18}>
      <div>
        <H1>H5 V2 设计稿 4/4 · 模块页</H1>
        <Text tone="secondary">
          AI Talk（高端简约）/ 联系人 / 好友请求 / 花呗三部曲 / 银行卡系列 / 登录注册 / 改密 / 身份绑定 / 信息页 —— 与 icons、core、flows 稿同令牌、同图标体系。
        </Text>
      </div>
      <Divider />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(220px, 1fr))', gap: 18 }}>
        <AITalkScreen />
        <AITalkDrawerScreen />
        <ContactsScreen />
        <FriendRequestsScreen />
        <HuabeiHomeScreen />
        <HuabeiBillScreen />
        <HuabeiRepayScreen />
        <BankCardDetailScreen />
        <BankCardBillScreen />
        <AddBankCardScreen />
        <AuthScreens />
        <ChangePasswordScreen />
        <IdentityBindScreen />
        <InfoPagesScreen />
      </div>
      <Divider />
      <Table
        headers={['模块', '关键设计决策', '实现落点']}
        rows={[
          ['AI Talk', '深蓝沉浸底色区别于全局浅蓝；发光 Orb 欢迎区；确认卡片内嵌 AI 气泡；抽屉 74% 宽', 'frontend-h5/src/pages/h5/AITalk/'],
          ['花呗', 'credit 紫蓝渐变专属品牌色；首页/账单/还款三页统一卡结构；还款走 6 位支付密码', 'frontend-h5/src/pages/h5/Credit*/'],
          ['银行卡', '卡面大渐变底+掩码切换；绑卡表单含验证码倒计时与协议勾选；验签流程保留', 'frontend-h5/src/pages/h5/BankCard*/'],
          ['登录注册/改密', '渐变头图+白卡表单；密码眼睛切换；强度条；错误提示可执行化；验证码前端模拟（Toast 演示码）', 'frontend-h5/src/pages/h5/Login|Register|Settings'],
          ['身份绑定', '认证状态徽章头；证件信息只读掩码；不可自助修改提示', 'frontend-h5/src/pages/h5/IdentityBind'],
          ['信息页', '版本信息独立版式；协议/隐私/关于共用长文阅读模板', 'frontend-h5/src/pages/h5/VersionInfo|UserAgreement|About'],
        ]}
      />
    </Stack>
  );
}
