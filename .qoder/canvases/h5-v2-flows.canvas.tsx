import { Divider, H1, Stack, Text } from 'qoder/canvas';

/* ============================================================
 * H5 V2 设计稿 3/4：资金流程（扫码/转账三部曲/收款）
 * 与 h5-v2-core 同令牌体系；图标 path 与 h5-v2-icons 一致
 * ============================================================ */

const P = {
  primary: '#256cff',
  grad: 'linear-gradient(135deg, #256cff 0%, #18c0e8 100%)',
  gradSoft: 'linear-gradient(160deg, #2f74ff 0%, #4d9bff 55%, #6fb6ff 100%)',
  text: '#16223a',
  text2: '#5a6b85',
  text3: '#94a3ba',
  bg: '#f4f6fa',
  divider: '#e8eef7',
  fill: '#eaf1ff',
  danger: '#f0484e',
  warning: '#ff9f18',
  success: '#16b387',
};

const IP: Record<string, React.ReactNode> = {
  back: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  scan: (<><path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2" /><path d="M4.5 12h15" strokeDasharray="2.4 2.2" /><path d="M9 8.5h6M9 15.5h6" opacity="0.55" /></>),
  collect: (<><path d="M12 3.6v8.6" /><path d="m8.6 9 3.4 3.4L15.4 9" /><path d="M5 13.5v3.7A2.8 2.8 0 0 0 7.8 20h8.4a2.8 2.8 0 0 0 2.8-2.8v-3.7" /></>),
  transfer: (<><path d="M4 8.4h13.4" /><path d="m14.6 5.4 3 3-3 3" /><path d="M20 15.6H6.6" /><path d="m9.4 12.6-3 3 3 3" /></>),
  wallet: (<><path d="M4 7.5A2.5 2.5 0 0 1 6.5 5h10A2.5 2.5 0 0 1 19 7.5v.3" /><path d="M4 8v9a2.5 2.5 0 0 0 2.5 2.5h11A2.5 2.5 0 0 0 20 17v-6.5A2.5 2.5 0 0 0 17.5 8H4Z" /><circle cx="16" cy="13.9" r="1.15" fill="currentColor" stroke="none" /></>),
  search: (<><circle cx="11" cy="11" r="6.4" /><path d="m15.8 15.8 4.2 4.2" /></>),
  keyboard: (<><rect x="3.4" y="6.4" width="17.2" height="11.2" rx="2.4" /><path d="M6.6 10h.01M10 10h.01M13.4 10h.01M16.8 10h.01M6.6 13.4h.01M17.4 13.4h.01M9.4 13.4h5.2" strokeWidth="2" /></>),
  camera: (<><path d="M4 8.4A2.4 2.4 0 0 1 6.4 6h1.4l1.3-2h5.8l1.3 2h1.4A2.4 2.4 0 0 1 20 8.4v8.2a2.4 2.4 0 0 1-2.4 2.4H6.4A2.4 2.4 0 0 1 4 16.6Z" /><circle cx="12" cy="12.4" r="3.2" /></>),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  close: <path d="m6.5 6.5 11 11M17.5 6.5l-11 11" />,
  clock: (<><circle cx="12" cy="12" r="8.4" /><path d="M12 7.6V12l3 2.2" /></>),
  chev: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  shield: (<><path d="M12 3.6 19 6v5.4c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6Z" /><path d="m9.2 11.8 2 2 3.6-3.8" /></>),
  qr: (<><rect x="4" y="4" width="6.4" height="6.4" rx="1.4" /><rect x="13.6" y="4" width="6.4" height="6.4" rx="1.4" /><rect x="4" y="13.6" width="6.4" height="6.4" rx="1.4" /><path d="M13.6 13.6h2.6v2.6M20 13.6v2.6M16.2 20h-2.6M20 20h.01" /></>),
  home: (<><path d="M4 10.5 12 4l8 6.5" /><path d="M6 9.5V19a1 1 0 0 0 1 1h3.2v-4.4a1.8 1.8 0 0 1 3.6 0V20H17a1 1 0 0 0 1-1V9.5" /></>),
  receipt: (<><path d="M6.4 3.8h11.2V20l-2.2-1.5L13.2 20l-2.2-1.5L8.8 20l-2.4-1.5Z" /><path d="M9.2 8.2h5.6M9.2 11.6h5.6M9.2 15h3.2" /></>),
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

function MiniAvatar(props: { kind: number; size?: number }) {
  const grads = ['linear-gradient(135deg,#3d7bff,#22c3e6)', 'linear-gradient(135deg,#ffb648,#ff7a59)', 'linear-gradient(135deg,#2ed3a3,#12a4c9)'];
  return (
    <div style={{ width: props.size ?? 28, height: props.size ?? 28, borderRadius: '50%', background: grads[props.kind % 3], display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 'none' }}>
      <svg viewBox="0 0 24 24" width={(props.size ?? 28) * 0.55} height={(props.size ?? 28) * 0.55} fill="#fff" opacity={0.94}>
        <circle cx="12" cy="9" r="4" />
        <path d="M4.5 21c1-4.3 4-6.5 7.5-6.5s6.5 2.2 7.5 6.5Z" />
      </svg>
    </div>
  );
}

/* ---- 扫一扫：真实取景框态 ---- */
function ScanCameraScreen() {
  return (
    <Phone title="扫一扫 · 摄像头取景态" note="深色沉浸式背景；取景框四角与扫描线用品牌渐变；底部操作：相册 / 手动输入。">
      <div style={{ background: '#0d1526', minHeight: 430 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', color: '#fff' }}>
          <Ic n="back" s={16} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>扫一扫</span>
          <span style={{ width: 16 }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 34 }}>
          <div style={{ position: 'relative', width: 180, height: 180 }}>
            <div style={{ position: 'absolute', inset: 0, borderRadius: 16, border: '1px solid rgba(255,255,255,0.14)' }} />
            {[[0, 0, '30px 4px 4px 0', '12px 0 0 12px'], [100, 0, '30px 4px 4px 0', '12px 12px 0 0'], [0, 100, '4px 30px 0 0', '-12px 0 0 12px'], [100, 100, '4px 30px 0 0', '0 12px 12px 0']].map(([l, t], i) => (
              <div key={i} style={{ position: 'absolute', left: `calc(${l}% - ${(l as number) > 0 ? 2 : 0}px)`, top: `calc(${t}% - ${(t as number) > 0 ? 2 : 0}px)`, width: 30, height: 30, borderTop: (t as number) === 0 ? '3.5px solid #4d9bff' : 'none', borderBottom: (t as number) > 0 ? '3.5px solid #4d9bff' : 'none', borderLeft: (l as number) === 0 ? '3.5px solid #4d9bff' : 'none', borderRight: (l as number) > 0 ? '3.5px solid #4d9bff' : 'none', borderRadius: (l as number) === 0 && (t as number) === 0 ? '12px 0 0 0' : (l as number) > 0 && (t as number) === 0 ? '0 12px 0 0' : (l as number) === 0 ? '0 0 0 12px' : '0 0 12px 0' }} />
            ))}
            <div style={{ position: 'absolute', left: 8, right: 8, top: '46%', height: 2.5, borderRadius: 2, background: P.grad, boxShadow: '0 0 12px rgba(72,155,255,0.8)' }} />
          </div>
        </div>
        <div style={{ textAlign: 'center', color: 'rgba(255,255,255,0.75)', fontSize: 10.5, marginTop: 22 }}>
          将收款码 / 付款码放入框内，自动识别
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 44, marginTop: 30 }}>
          {[['camera', '相册'], ['keyboard', '手动输入']].map(([icon, label]) => (
            <div key={label} style={{ textAlign: 'center', color: '#fff' }}>
              <div style={{ width: 38, height: 38, margin: '0 auto 5px', borderRadius: '50%', background: 'rgba(255,255,255,0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Ic n={icon} s={17} />
              </div>
              <span style={{ fontSize: 9.5, opacity: 0.85 }}>{label}</span>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

/* ---- 扫一扫：手动输入降级态 ---- */
function ScanManualScreen() {
  return (
    <Phone title="扫一扫 · 摄像头不可用降级（手动输入码内容）" note="检测不到摄像头时自动进入本态；粘贴/输入码内容后解析：个人收款码→收款支付页、付款码→扫码支付页；提供示例码一键体验。">
      <div style={{ background: '#0d1526', minHeight: 430 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', color: '#fff' }}>
          <Ic n="back" s={16} />
          <span style={{ fontSize: 13, fontWeight: 600 }}>扫一扫</span>
          <span style={{ width: 16 }} />
        </div>
        <div style={{ margin: '26px 16px 0', padding: '14px 14px 16px', background: '#fff', borderRadius: 16, boxShadow: '0 8px 24px rgba(0,0,0,0.25)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 10 }}>
            <Ic n="keyboard" s={16} c={P.primary} />
            <span style={{ fontSize: 12.5, fontWeight: 700, color: P.text }}>手动输入码内容</span>
          </div>
          <div style={{ fontSize: 9.5, color: P.text2, marginBottom: 8 }}>当前环境无法使用摄像头，可直接粘贴二维码内容完成识别</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: P.bg, border: `1.5px solid ${P.divider}`, borderRadius: 12, padding: '9px 10px' }}>
            <Ic n="qr" s={15} c={P.text3} />
            <span style={{ fontSize: 10.5, color: P.text, flex: 1 }}>MINI_COLLECT:tok_a1b2c3</span>
            <Ic n="close" s={12} c={P.text3} />
          </div>
          <div style={{ marginTop: 12, textAlign: 'center', padding: '9px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700, boxShadow: '0 5px 12px rgba(37,108,255,0.28)' }}>
            解析并跳转
          </div>
        </div>
        <div style={{ margin: '14px 16px 0', padding: 12, background: 'rgba(255,255,255,0.07)', borderRadius: 14 }}>
          <div style={{ fontSize: 9.5, color: 'rgba(255,255,255,0.6)', marginBottom: 8 }}>试试示例码（点击进入对应流程）</div>
          {[['个人收款码', 'MINI_COLLECT:tok_demo'], ['固定金额收款码', 'MINI_COLLECT_REQ:req_demo'], ['商户付款码', 'MINI_QRPAY:pay_demo']].map(([l, v]) => (
            <div key={l} style={{ display: 'flex', alignItems: 'center', padding: '7px 2px', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
              <span style={{ fontSize: 10, color: '#fff', flex: 1 }}>{l}</span>
              <span style={{ fontSize: 8.5, color: 'rgba(255,255,255,0.5)', fontFamily: 'Courier New, monospace' }}>{v}</span>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

/* ---- 转账页 ---- */
function TransferScreen() {
  return (
    <Phone title="转账页（重设计）" note="手机号搜索收款人；最近转账横排头像直选；支付方式（余额/银行卡）；金额大字输入；备注选填。">
      <div style={{ background: P.gradSoft, paddingBottom: 24 }}>
        <NavBar title="转账" light />
      </div>
      <div style={{ margin: '-12px 12px 0', position: 'relative', paddingBottom: 12 }}>
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, background: P.bg, borderRadius: 22, padding: '9px 12px' }}>
            <Ic n="search" s={14} c={P.text3} />
            <span style={{ fontSize: 10.5, color: P.text3 }}>输入手机号搜索收款人</span>
          </div>
          <div style={{ marginTop: 12 }}>
            <div style={{ fontSize: 9.5, color: P.text3, marginBottom: 8 }}>最近转账</div>
            <div style={{ display: 'flex', gap: 16 }}>
              {[['张三', 0], ['李四', 1], ['王五', 2]].map(([name, k]) => (
                <div key={name as string} style={{ textAlign: 'center' }}>
                  <MiniAvatar kind={k as number} size={34} />
                  <div style={{ fontSize: 8.5, color: P.text2, marginTop: 3 }}>{name}</div>
                </div>
              ))}
            </div>
          </div>
        </Card>
        <Card style={{ marginTop: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 9.5, color: P.text2 }}>
            <Ic n="wallet" s={13} c={P.primary} /> 支付方式
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <div style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 12, background: P.fill, border: `1.5px solid ${P.primary}`, color: P.primary, fontSize: 10.5, fontWeight: 600 }}>
              账户余额 ¥3,990.00
            </div>
            <div style={{ flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 12, border: `1.5px solid ${P.divider}`, color: P.text2, fontSize: 10.5 }}>
              银行卡
            </div>
          </div>
        </Card>
        <Card style={{ marginTop: 10, textAlign: 'center' }}>
          <div style={{ fontSize: 9.5, color: P.text2 }}>转账金额</div>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'center', gap: 4, margin: '6px 0 2px' }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: P.text }}>¥</span>
            <span style={{ fontSize: 26, fontWeight: 800, color: P.text }}>100.00</span>
          </div>
          <div style={{ height: 1, background: P.divider, margin: '8px 22px' }} />
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, fontSize: 9, color: P.text3, paddingTop: 2 }}>
            备注（选填）：请你喝奶茶
          </div>
        </Card>
        <div style={{ marginTop: 12, textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12.5, fontWeight: 700, boxShadow: '0 5px 12px rgba(37,108,255,0.28)' }}>
          下一步
        </div>
      </div>
    </Phone>
  );
}

/* ---- 转账确认页 ---- */
function TransferConfirmScreen() {
  return (
    <Phone title="转账确认页" note="订单信息卡片化；支付方式行；支付密码 6 位输入；底部确认按钮吸底。">
      <div style={{ background: '#fff' }}><NavBar title="确认转账" /></div>
      <div style={{ padding: '10px 12px' }}>
        <Card style={{ textAlign: 'center', padding: '16px 12px' }}>
          <MiniAvatar kind={0} size={44} />
          <div style={{ fontSize: 11.5, fontWeight: 600, color: P.text, marginTop: 6, display: 'inline-block' }}>转账给 张三</div>
          <div style={{ fontSize: 26, fontWeight: 800, color: P.text, marginTop: 6 }}>¥100.00</div>
          <div style={{ fontSize: 9, color: P.text3, marginTop: 4 }}>备注：请你喝奶茶</div>
        </Card>
        <Card style={{ marginTop: 10, padding: '4px 12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', padding: '10px 0', borderBottom: `1px solid ${P.divider}` }}>
            <span style={{ flex: 1, fontSize: 10.5, color: P.text2 }}>支付方式</span>
            <span style={{ fontSize: 10.5, color: P.text, fontWeight: 600 }}>账户余额 ¥3,990.00</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', padding: '10px 0' }}>
            <span style={{ flex: 1, fontSize: 10.5, color: P.text2 }}>到账方式</span>
            <span style={{ fontSize: 10.5, color: P.text }}>实时到账</span>
          </div>
        </Card>
        <Card style={{ marginTop: 10, textAlign: 'center' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: P.text, marginBottom: 10 }}>请输入支付密码</div>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 6 }}>
            {[1, 1, 1, 0, 0, 0].map((f, i) => (
              <div key={i} style={{ width: 26, height: 32, borderRadius: 8, border: `1.5px solid ${i < 3 ? P.primary : P.divider}`, background: i < 3 ? P.fill : '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {f ? <span style={{ width: 6, height: 6, borderRadius: '50%', background: P.text }} /> : null}
              </div>
            ))}
          </div>
        </Card>
        <div style={{ marginTop: 14, textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12.5, fontWeight: 700 }}>
          确认转账
        </div>
      </div>
    </Phone>
  );
}

/* ---- 转账结果三态 ---- */
function ResultScreen(props: { kind: 'success' | 'fail' | 'processing' }) {
  const cfg = {
    success: { c: P.success, icon: 'check', title: '转账成功', sub: '¥100.00 已实时到账对方账户' },
    fail: { c: P.danger, icon: 'close', title: '转账失败', sub: '余额不足，可先充值后重试' },
    processing: { c: P.warning, icon: 'clock', title: '处理中', sub: '系统处理中，请稍后在账单中查看结果' },
  }[props.kind];
  return (
    <Phone title={`结果页 · ${cfg.title}`} note="三态统一版式：状态大图标 + 金额 + 说明 + 双入口（返回首页 / 查看账单）。">
      <div style={{ background: '#fff' }}><NavBar title="转账结果" /></div>
      <div style={{ background: '#fff', textAlign: 'center', padding: '30px 20px 24px' }}>
        <div style={{ width: 52, height: 52, margin: '0 auto', borderRadius: '50%', background: cfg.c, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 8px 18px ${cfg.c}55` }}>
          <Ic n={cfg.icon} s={26} w={2.4} />
        </div>
        <div style={{ fontSize: 15, fontWeight: 800, color: P.text, marginTop: 12 }}>{cfg.title}</div>
        <div style={{ fontSize: 20, fontWeight: 800, color: P.text, marginTop: 6 }}>¥100.00</div>
        <div style={{ fontSize: 9.5, color: P.text3, marginTop: 4 }}>{cfg.sub}</div>
        <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
          <div style={{ flex: 1, padding: '9px 0', borderRadius: 22, border: `1.5px solid ${P.primary}`, color: P.primary, fontSize: 11, fontWeight: 600 }}>返回首页</div>
          <div style={{ flex: 1, padding: '9px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 11, fontWeight: 600 }}>查看账单</div>
        </div>
      </div>
    </Phone>
  );
}

/* ---- 收款页（个人收款码 / 设置金额） ---- */
function CollectionScreen() {
  return (
    <Phone title="收款页（原收付款，改名收款）" note="双 Tab：个人收款码 / 设置金额；码卡片居中品牌渐变描边；支持保存与分享提示。">
      <div style={{ background: P.gradSoft, paddingBottom: 26 }}>
        <NavBar title="收款" light />
        <div style={{ display: 'flex', justifyContent: 'center', gap: 24 }}>
          {['个人收款码', '设置金额'].map((t, i) => (
            <div key={t} style={{ textAlign: 'center', paddingBottom: 6 }}>
              <div style={{ fontSize: 11.5, fontWeight: i === 0 ? 700 : 400, color: i === 0 ? '#fff' : 'rgba(255,255,255,0.7)' }}>{t}</div>
              {i === 0 && <div style={{ width: 20, height: 3, borderRadius: 2, background: '#fff', margin: '5px auto 0' }} />}
            </div>
          ))}
        </div>
      </div>
      <div style={{ margin: '-14px 14px 0', position: 'relative' }}>
        <Card style={{ padding: '18px 14px', textAlign: 'center' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
            <MiniAvatar kind={0} size={22} />
            <span style={{ fontSize: 10.5, color: P.text2 }}>BBFPS 的收款码</span>
          </div>
          <div style={{ width: 140, height: 140, margin: '0 auto', borderRadius: 14, border: `2px solid ${P.fill}`, background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ width: 110, height: 110, background: 'repeating-linear-gradient(0deg,#16223a 0 4px,#fff 4px 8px), repeating-linear-gradient(90deg,#16223a 0 4px,#fff 4px 8px)', backgroundBlendMode: 'xor', borderRadius: 6, opacity: 0.9 }} />
          </div>
          <div style={{ fontSize: 9, color: P.text3, marginTop: 10 }}>对方扫码后可向你付款，码长期有效</div>
          <div style={{ display: 'flex', gap: 10, marginTop: 12 }}>
            <div style={{ flex: 1, padding: '8px 0', borderRadius: 20, border: `1.5px solid ${P.primary}`, color: P.primary, fontSize: 10.5, fontWeight: 600 }}>保存二维码</div>
            <div style={{ flex: 1, padding: '8px 0', borderRadius: 20, background: P.grad, color: '#fff', fontSize: 10.5, fontWeight: 600 }}>设置金额收款</div>
          </div>
        </Card>
      </div>
      <div style={{ height: 14 }} />
    </Phone>
  );
}

/* ---- 收款支付页（被扫码方付款） ---- */
function CollectionPayScreen() {
  return (
    <Phone title="收款支付页（扫他人收款码）" note="商户/收款人信息卡 + 金额大字（固定金额码锁定）+ 支付方式 + 密码确认。">
      <div style={{ background: P.gradSoft, paddingBottom: 24 }}>
        <NavBar title="向商户付款" light />
      </div>
      <div style={{ margin: '-12px 12px 0', position: 'relative' }}>
        <Card style={{ textAlign: 'center', padding: '14px 12px' }}>
          <div style={{ width: 38, height: 38, margin: '0 auto', borderRadius: 11, background: P.grad, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Ic n="collect" s={17} />
          </div>
          <div style={{ fontSize: 11.5, fontWeight: 700, color: P.text, marginTop: 6 }}>收款方：BBFPS</div>
          <div style={{ fontSize: 8.5, color: P.text3, marginTop: 2 }}>备注：拼单咖啡</div>
        </Card>
        <Card style={{ marginTop: 10, textAlign: 'center' }}>
          <div style={{ fontSize: 9.5, color: P.text2 }}>支付金额</div>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'center', gap: 4, margin: '4px 0' }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: P.text }}>¥</span>
            <span style={{ fontSize: 25, fontWeight: 800, color: P.text }}>28.50</span>
          </div>
          <div style={{ fontSize: 8.5, color: P.text3 }}>固定金额收款码，金额不可修改</div>
        </Card>
        <Card style={{ marginTop: 10, padding: '4px 12px' }}>
          {[['支付方式', '账户余额 ¥3,990.00', true], ['Mini 花呗', '可用额度 ¥4,680.00', false]].map(([l, v, on]) => (
            <div key={l as string} style={{ display: 'flex', alignItems: 'center', padding: '9.5px 0', borderBottom: `1px solid ${P.divider}` }}>
              <span style={{ flex: 1, fontSize: 10.5, color: P.text }}>{l}</span>
              <span style={{ fontSize: 9.5, color: P.text2, marginRight: 8 }}>{v}</span>
              <div style={{ width: 15, height: 15, borderRadius: '50%', border: `1.5px solid ${on ? P.primary : P.divider}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {on && <span style={{ width: 7, height: 7, borderRadius: '50%', background: P.primary }} />}
              </div>
            </div>
          ))}
        </Card>
        <div style={{ marginTop: 12, textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12.5, fontWeight: 700 }}>
          确认付款
        </div>
        <div style={{ height: 12 }} />
      </div>
    </Phone>
  );
}

/* ---- 回执页 ---- */
function ReceiptScreen() {
  return (
    <Phone title="支付回执页（扫码支付成功）" note="凭证样式：状态 + 金额 + 关键字段（时间/单号/支付方式）+ 完成按钮。">
      <div style={{ background: '#fff' }}><NavBar title="支付回执" /></div>
      <div style={{ background: '#fff', padding: '16px 16px 22px' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ width: 46, height: 46, margin: '0 auto', borderRadius: '50%', background: P.success, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Ic n="check" s={22} w={2.4} />
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, color: P.text, marginTop: 8 }}>支付成功</div>
          <div style={{ fontSize: 22, fontWeight: 800, color: P.text, marginTop: 4 }}>¥28.50</div>
        </div>
        <div style={{ margin: '14px 0', borderTop: `1px dashed ${P.divider}` }} />
        {[['收款方', 'BBFPS'], ['支付时间', '2026-08-11 10:22'], ['支付方式', '账户余额'], ['交易单号', 'FT2026081100123']].map(([l, v]) => (
          <div key={l} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: 10 }}>
            <span style={{ color: P.text3 }}>{l}</span>
            <span style={{ color: P.text }}>{v}</span>
          </div>
        ))}
        <div style={{ marginTop: 14, textAlign: 'center', padding: '10px 0', borderRadius: 22, background: P.grad, color: '#fff', fontSize: 12, fontWeight: 700 }}>
          完成
        </div>
      </div>
    </Phone>
  );
}

export default function H5V2Flows() {
  return (
    <Stack gap={22}>
      <H1>H5 V2 设计稿 3/4 · 资金流程（扫码 / 转账 / 收款）</H1>
      <Text tone="secondary">
        扫码页双态：摄像头取景态 + 手动输入降级态（解析 MINI_COLLECT / MINI_COLLECT_REQ / MINI_QRPAY 前缀跳转对应流程）；
        转账三部曲与收款全链路按新令牌体系重绘，结果页三态统一版式。
      </Text>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 18 }}>
        <ScanCameraScreen />
        <ScanManualScreen />
        <TransferScreen />
        <TransferConfirmScreen />
        <ResultScreen kind="success" />
        <ResultScreen kind="fail" />
        <ResultScreen kind="processing" />
        <CollectionScreen />
        <CollectionPayScreen />
        <ReceiptScreen />
      </div>
      <Divider />
      <Text tone="secondary" size="small">
        交互要点：扫码结果解析失败时 Toast「无法识别的码内容，请检查后重试」；转账失败提示附下一步建议（如余额不足→先充值）；
        收款支付成功后跳回执页；所有金额展示为整数分 / 100，时间 YYYY-MM-DD HH:mm。
      </Text>
    </Stack>
  );
}
