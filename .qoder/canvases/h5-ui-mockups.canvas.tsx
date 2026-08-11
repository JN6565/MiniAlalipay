import { Divider, Grid, H1, H2, Stack, Text } from 'qoder/canvas';

/* ============ 设计稿基础构件 ============ */

const P = {
  primary: '#256cff',
  grad: 'linear-gradient(135deg, #256cff 0%, #18c0e8 100%)',
  text: '#16223a',
  text2: '#5a6b85',
  text3: '#94a3ba',
  bg: '#f4f6fa',
  divider: '#e8eef7',
  danger: '#f0484e',
  success: '#16b387',
};

/** 手机框：390 设计稿等比缩小的容器。 */
function Phone(props: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 13, fontWeight: 600, color: P.text, marginBottom: 4 }}>{props.title}</div>
      <div
        style={{
          width: '100%',
          borderRadius: 24,
          border: '6px solid #1c2740',
          background: P.bg,
          overflow: 'hidden',
          boxShadow: '0 8px 28px rgba(22,60,120,0.14)',
        }}
      >
        {props.children}
      </div>
      {props.note && (
        <div style={{ fontSize: 11, color: P.text3, marginTop: 6, lineHeight: 1.6 }}>{props.note}</div>
      )}
    </div>
  );
}

/** 页头导航条。 */
function NavBar(props: { title: string; light?: boolean }) {
  const color = props.light ? '#fff' : P.text;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '10px 14px',
        color,
      }}
    >
      <span style={{ fontSize: 14 }}>‹</span>
      <span style={{ fontSize: 13, fontWeight: 600 }}>{props.title}</span>
      <span style={{ fontSize: 12 }}>⋯</span>
    </div>
  );
}

/** 白卡。 */
function Card(props: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div
      style={{
        background: '#fff',
        borderRadius: 14,
        padding: 12,
        boxShadow: '0 2px 12px rgba(22,60,120,0.06)',
        ...props.style,
      }}
    >
      {props.children}
    </div>
  );
}

function GridIcon(props: { label: string; icon: string; hot?: boolean }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div
        style={{
          width: 34,
          height: 34,
          margin: '0 auto 4px',
          borderRadius: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 16,
          background: props.hot ? P.grad : '#eaf1ff',
          color: props.hot ? '#fff' : P.primary,
        }}
      >
        {props.icon}
      </div>
      <div style={{ fontSize: 9, color: P.text2 }}>{props.label}</div>
    </div>
  );
}

/** 仿真卡面（设计稿缩小版）：卡面不出现风格命名文字与角标。 */
function MiniBankCard(props: { bank: string; gradient: string; pattern: string }) {
  return (
    <div
      style={{
        position: 'relative',
        borderRadius: 12,
        padding: '10px 12px',
        color: '#fff',
        background: `${props.pattern}, ${props.gradient}`,
        minHeight: 84,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
      }}
    >
      <span style={{ fontSize: 11, fontWeight: 700 }}>{props.bank}</span>
      <div style={{ fontSize: 12, letterSpacing: 2, fontFamily: 'Courier New, monospace' }}>**** **** **** 8888</div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9, opacity: 0.85 }}>
        <span>储蓄卡 · 默认</span>
        <span>余额 ****</span>
      </div>
    </div>
  );
}

/** 明细行。 */
function TxRow(props: {
  title: string;
  tag: string;
  time: string;
  amount: string;
  income?: boolean;
  after: string;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '7px 0', borderBottom: `1px solid ${P.divider}` }}>
      <div>
        <div style={{ fontSize: 11, color: P.text, fontWeight: 500 }}>
          {props.title}
          <span
            style={{
              fontSize: 8,
              color: P.primary,
              background: '#eaf1ff',
              borderRadius: 8,
              padding: '1px 5px',
              marginLeft: 5,
            }}
          >
            {props.tag}
          </span>
        </div>
        <div style={{ fontSize: 9, color: P.text3, marginTop: 2 }}>
          {props.time} · 余额 ¥{props.after}
        </div>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: props.income ? P.danger : P.text }}>
        {props.amount}
      </div>
    </div>
  );
}

/* ============ 各界面设计稿 ============ */

function HomeMock() {
  return (
    <Phone
      title="首页 /h5/home"
      note="信息层级：总资产摘要（冻结金额不展示）→ 快捷功能一行 5 个 → AI 助手横幅 → 生活服务区（占位 Toast）→ 花呗摘要；不放最近交易列表，资产卡提供「明细」入口。"
    >
      <div style={{ background: P.grad, paddingBottom: 30 }}>
        <NavBar title="MiniAlalipay" light />
        <div style={{ padding: '0 16px', color: '#fff' }}>
          <div style={{ fontSize: 10, opacity: 0.85, display: 'flex', alignItems: 'center', gap: 4 }}>
            总资产（元）<span style={{ opacity: 0.8 }}>👁 掩码切换</span>
            <span style={{ marginLeft: 'auto', fontSize: 10, border: '1px solid rgba(255,255,255,0.6)', borderRadius: 12, padding: '1px 8px' }}>
              明细 ›
            </span>
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, margin: '4px 0' }}>¥ 12,480.50</div>
          <div style={{ fontSize: 9, opacity: 0.7 }}>仅展示总资产，冻结金额全站不展示</div>
        </div>
      </div>
      <div style={{ padding: '0 12px', marginTop: -20 }}>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 4 }}>
            <GridIcon icon="⌖" label="扫一扫" hot />
            <GridIcon icon="¥" label="收付款" hot />
            <GridIcon icon="⇄" label="转账" />
            <GridIcon icon="⇅" label="充值提现" />
            <GridIcon icon="◐" label="花呗" />
          </div>
        </Card>
        <Card style={{ marginBottom: 10, display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #7a6cff 0%, #256cff 100%)',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              flexShrink: 0,
            }}
          >
            ✦
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: P.text }}>AI 助手</div>
            <div style={{ fontSize: 9, color: P.text3 }}>问余额、查账单、智能记账</div>
          </div>
          <span style={{ fontSize: 10, color: P.primary }}>去问问 ›</span>
        </Card>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: P.text, marginBottom: 8 }}>生活服务</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
            {['生活缴费', '手机营业厅', '火车票', '机票', '医疗健康', '市民中心', '哈喽出行', '美团'].map((n, i) => (
              <GridIcon key={n} icon={['⚡', '☎', '🚄', '✈', '⚕', '☖', '🛵', '🍜'][i]} label={n} />
            ))}
          </div>
          <div style={{ fontSize: 9, color: P.text3, marginTop: 6 }}>点击提示「功能开发中，敬请期待」（仅占位不实现）</div>
        </Card>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div style={{ fontSize: 11, fontWeight: 600, color: P.text }}>Mini 花呗</div>
              <div style={{ fontSize: 9, color: P.text3, marginTop: 2 }}>下月应还 ¥ 320.00 · 10-08 到期</div>
            </div>
            <span style={{ fontSize: 10, color: P.primary, background: '#eaf1ff', borderRadius: 12, padding: '3px 10px' }}>
              去还款 ›
            </span>
          </div>
        </Card>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function WalletMock() {
  return (
    <Phone
      title="钱包 /h5/wallet"
      note="余额卡渐变升级 + 掩码切换；银行卡以仿真卡面呈现；充值/提现按钮保留语义（针对账户余额）。"
    >
      <NavBar title="充值提现" />
      <div style={{ padding: '0 12px' }}>
        <div style={{ background: P.grad, borderRadius: 14, padding: 14, color: '#fff', marginBottom: 10 }}>
          <div style={{ fontSize: 10, opacity: 0.85 }}>账户余额（元）👁</div>
          <div style={{ fontSize: 24, fontWeight: 700, margin: '2px 0' }}>12,480.50</div>
        </div>
        <div style={{ fontSize: 11, fontWeight: 600, color: P.text, margin: '8px 2px' }}>
          银行卡 <span style={{ float: 'right', fontSize: 10, color: P.text3 }}>管理银行卡 ›</span>
        </div>
        <MiniBankCard
          bank="中国工商银行"
          gradient="linear-gradient(135deg, #c8353f 0%, #8f1d2c 100%)"
          pattern="radial-gradient(circle at 82% 18%, rgba(255,215,160,0.35) 0%, transparent 34%)"
        />
        <div style={{ display: 'flex', gap: 8, margin: '6px 0 10px' }}>
          <span style={{ flex: 1, textAlign: 'center', fontSize: 10, color: P.primary, background: '#eaf1ff', borderRadius: 14, padding: '5px 0' }}>
            充值到余额
          </span>
          <span style={{ flex: 1, textAlign: 'center', fontSize: 10, color: P.text2, background: '#f0f4fa', borderRadius: 14, padding: '5px 0' }}>
            提现到卡
          </span>
        </div>
        <div style={{ fontSize: 9, color: P.text3, lineHeight: 1.7 }}>
          · 充值：银行卡 → 账户余额 · 提现：账户余额 → 银行卡<br />
          · 资金流入仅两种：他人转账、银行卡充值
        </div>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function BankCardsMock() {
  return (
    <Phone
      title="银行卡列表 /h5/bank-cards"
      note="每卡仿真卡面：银行专属渐变 + 纹样 + 尾号 + 余额掩码（风格命名不上卡面）；底部「添加银行卡」。"
    >
      <NavBar title="银行卡" />
      <div style={{ padding: '0 12px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <MiniBankCard
          bank="中国工商银行"
          gradient="linear-gradient(135deg, #c8353f 0%, #8f1d2c 100%)"
          pattern="repeating-radial-gradient(circle at 88% 10%, rgba(255,215,160,0.14) 0 6px, transparent 6px 14px)"
        />
        <MiniBankCard
          bank="招商银行"
          gradient="linear-gradient(135deg, #d9483b 0%, #96282b 100%)"
          pattern="repeating-conic-gradient(from 200deg at 85% 15%, rgba(255,235,210,0.16) 0deg 8deg, transparent 8deg 20deg)"
        />
        <MiniBankCard
          bank="中国建设银行"
          gradient="linear-gradient(135deg, #2b6cb0 0%, #153e6e 100%)"
          pattern="repeating-linear-gradient(115deg, rgba(255,255,255,0.10) 0 2px, transparent 2px 18px)"
        />
        <div
          style={{
            border: `1px dashed #c3d2e8`,
            borderRadius: 12,
            padding: 12,
            textAlign: 'center',
            fontSize: 11,
            color: P.primary,
            background: '#fff',
          }}
        >
          ＋ 添加银行卡
        </div>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function BankCardDetailMock() {
  return (
    <Phone
      title="银行卡详情 /h5/bank-cards/:id"
      note="操作按钮组：查看余额（掩码切换）、查看完整卡号（支付密码校验 + 后端新接口）、查看账单（跳账单页）。"
    >
      <NavBar title="卡片详情" />
      <div style={{ padding: '0 12px' }}>
        <MiniBankCard
          bank="中国工商银行"
          gradient="linear-gradient(135deg, #c8353f 0%, #8f1d2c 100%)"
          pattern="radial-gradient(circle at 82% 18%, rgba(255,215,160,0.35) 0%, transparent 34%)"
        />
        <Card style={{ margin: '10px 0' }}>
          <div style={{ display: 'flex', gap: 6 }}>
            {[
              ['👁', '查看余额'],
              ['#', '完整卡号'],
              ['≡', '查看账单'],
            ].map(([icon, label]) => (
              <div
                key={label}
                style={{
                  flex: 1,
                  textAlign: 'center',
                  background: '#eaf1ff',
                  borderRadius: 10,
                  padding: '8px 0',
                  fontSize: 10,
                  color: P.primary,
                }}
              >
                <div style={{ fontSize: 13 }}>{icon}</div>
                {label}
              </div>
            ))}
          </div>
        </Card>
        <Card>
          {[
            ['卡类型', '储蓄卡'],
            ['持卡人', '张*三'],
            ['预留手机号', '138****5678'],
            ['绑定时间', '2026-08-08 15:20'],
          ].map(([k, v]) => (
            <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: 10, borderBottom: `1px solid ${P.divider}` }}>
              <span style={{ color: P.text3 }}>{k}</span>
              <span style={{ color: P.text }}>{v}</span>
            </div>
          ))}
          <div style={{ fontSize: 9, color: P.text3, marginTop: 6 }}>余额默认掩码：**** → 点击显示 ¥ 1,000.00</div>
        </Card>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function TransactionsMock() {
  return (
    <Phone
      title="账户明细 /h5/account/transactions"
      note="月分组 + 月度收支汇总头；每笔含业务类型标签、±金额、精确时间、交易后余额；充值/提现/花呗消费均在此可见。"
    >
      <NavBar title="账单明细" />
      <div style={{ padding: '0 12px' }}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
          {['全部', '收入', '支出', '转账', '花呗'].map((t, i) => (
            <span
              key={t}
              style={{
                fontSize: 9,
                padding: '3px 10px',
                borderRadius: 12,
                background: i === 0 ? P.primary : '#fff',
                color: i === 0 ? '#fff' : P.text2,
              }}
            >
              {t}
            </span>
          ))}
        </div>
        <div style={{ fontSize: 10, color: P.text2, padding: '4px 2px', display: 'flex', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 600, color: P.text }}>2026 年 8 月</span>
          <span>
            收入 <b style={{ color: P.danger }}>¥1,100.00</b> · 支出 ¥320.00
          </span>
        </div>
        <Card style={{ padding: '4px 12px' }}>
          <TxRow title="银行卡充值" tag="充值" time="08-11 10:24" amount="+¥1,000.00" income after="12,480.50" />
          <TxRow title="花呗账单还款" tag="花呗" time="08-10 20:11" amount="−¥320.00" after="11,480.50" />
          <TxRow title="来自 王*平 的转账" tag="转账" time="08-09 14:02" amount="+¥100.00" income after="11,800.50" />
          <div style={{ border: 'none', padding: '7px 0', fontSize: 10, color: P.text3 }}>… 下拉加载更多（骨架屏）</div>
        </Card>
        <div style={{ fontSize: 10, color: P.text2, padding: '10px 2px 4px', display: 'flex', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 600, color: P.text }}>2026 年 7 月</span>
          <span>收入 ¥2,500.00 · 支出 ¥860.00</span>
        </div>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function CreditMock() {
  const r = 42;
  const c = 2 * Math.PI * r;
  const used = 0.24;
  return (
    <Phone
      title="Mini 花呗 /h5/credit"
      note="额度环形图（SVG）：已用比例可视化；待还金额大字 + 到期日；入口：账单、还款。"
    >
      <div style={{ background: 'linear-gradient(135deg, #7a6cff 0%, #256cff 100%)', paddingBottom: 24 }}>
        <NavBar title="Mini 花呗" light />
        <div style={{ display: 'flex', alignItems: 'center', padding: '0 20px', gap: 16, color: '#fff' }}>
          <svg width={104} height={104} viewBox="0 0 104 104">
            <circle cx={52} cy={52} r={r} fill="none" stroke="rgba(255,255,255,0.25)" strokeWidth={9} />
            <circle
              cx={52}
              cy={52}
              r={r}
              fill="none"
              stroke="#ffffff"
              strokeWidth={9}
              strokeLinecap="round"
              strokeDasharray={`${c * used} ${c}`}
              transform="rotate(-90 52 52)"
            />
            <text x={52} y={48} textAnchor="middle" fill="#fff" fontSize={15} fontWeight={700}>24%</text>
            <text x={52} y={63} textAnchor="middle" fill="rgba(255,255,255,0.8)" fontSize={8}>额度已用</text>
          </svg>
          <div>
            <div style={{ fontSize: 9, opacity: 0.85 }}>下月应还（元）</div>
            <div style={{ fontSize: 22, fontWeight: 700 }}>320.00</div>
            <div style={{ fontSize: 9, opacity: 0.8 }}>09-08 前还款 · 总额度 5,000.00</div>
          </div>
        </div>
      </div>
      <div style={{ padding: '0 12px', marginTop: -14 }}>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <span style={{ flex: 1, textAlign: 'center', fontSize: 11, color: '#fff', background: P.grad, borderRadius: 16, padding: '7px 0' }}>
              立即还款
            </span>
            <span style={{ flex: 1, textAlign: 'center', fontSize: 11, color: P.primary, background: '#eaf1ff', borderRadius: 16, padding: '7px 0' }}>
              查看账单
            </span>
          </div>
        </Card>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>总额度</span><span>¥ 5,000.00</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>已用额度</span><span>¥ 1,200.00</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>可用额度</span><span style={{ color: P.success }}>¥ 3,800.00</span>
          </div>
        </Card>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function LoginMock() {
  return (
    <Phone
      title="登录 /h5/login"
      note="品牌渐变头图 + Logo + 表单卡片；错误提示中文化并可执行（如密码错误提示找回入口）。"
    >
      <div style={{ background: P.grad, padding: '36px 0 30px', textAlign: 'center', color: '#fff' }}>
        <div
          style={{
            width: 52,
            height: 52,
            margin: '0 auto 8px',
            borderRadius: 16,
            background: 'rgba(255,255,255,0.92)',
            color: P.primary,
            fontSize: 26,
            fontWeight: 800,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          M
        </div>
        <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: 2 }}>MiniAlalipay</div>
        <div style={{ fontSize: 9, opacity: 0.85, marginTop: 2 }}>小而美的口袋银行</div>
      </div>
      <div style={{ padding: '16px 14px' }}>
        <Card>
          <div style={{ fontSize: 9, color: P.text3 }}>手机号</div>
          <div style={{ fontSize: 11, color: P.text, borderBottom: `1px solid ${P.divider}`, padding: '5px 0 7px' }}>138****5678</div>
          <div style={{ fontSize: 9, color: P.text3, marginTop: 10 }}>登录密码</div>
          <div style={{ fontSize: 11, color: P.text3, borderBottom: `1px solid ${P.divider}`, padding: '5px 0 7px' }}>••••••••</div>
          <div style={{ fontSize: 11, color: '#fff', background: P.grad, borderRadius: 18, textAlign: 'center', padding: '8px 0', marginTop: 14 }}>
            登 录
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 9, color: P.primary, marginTop: 10 }}>
            <span>忘记密码</span><span>注册账号 ›</span>
          </div>
        </Card>
      </div>
    </Phone>
  );
}

function RechargeMock() {
  return (
    <Phone
      title="充值 /h5/bank-cards/:id/recharge"
      note="以账户余额为中心：充值目标卡（当前可用余额）→ 付款方式行（不展示卡内余额）→ 金额输入（支持小数）→ 密码确认。"
    >
      <NavBar title="充值" />
      <div style={{ padding: '0 12px' }}>
        <div style={{ background: P.grad, borderRadius: 14, padding: 14, color: '#fff', marginBottom: 10 }}>
          <div style={{ fontSize: 10, opacity: 0.85 }}>充值到账户余额</div>
          <div style={{ fontSize: 22, fontWeight: 700, margin: '2px 0' }}>¥ 12,480.50</div>
          <div style={{ fontSize: 9, opacity: 0.8 }}>当前账户可用余额</div>
        </div>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10 }}>
            <span style={{ color: P.text3 }}>付款方式</span>
            <span>中国工商银行（尾号 8888）›</span>
          </div>
        </Card>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 10, color: P.text3 }}>充值金额</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: P.text, padding: '4px 0' }}>
            ¥ <span>11.11</span>
          </div>
          <div style={{ fontSize: 9, color: P.text3 }}>支持小数，最多两位 · 单笔 0.01-50000.00 元</div>
        </Card>
        <div style={{ fontSize: 11, color: '#fff', background: P.grad, borderRadius: 18, textAlign: 'center', padding: '8px 0' }}>
          确认充值
        </div>
        <div style={{ fontSize: 9, color: P.text3, textAlign: 'center', marginTop: 8 }}>
          点击后弹出支付密码键盘 → 成功返回钱包页刷新余额
        </div>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function TransferConfirmMock() {
  return (
    <Phone
      title="转账确认 /h5/transfer/confirm"
      note="订单卡片化：收款人（脱敏实名）→ 金额大字 → 备注；底部确认唤起密码弹窗；流程与支付宝一致。"
    >
      <NavBar title="确认转账" />
      <div style={{ padding: '0 12px' }}>
        <Card style={{ marginBottom: 10, textAlign: 'center', padding: 16 }}>
          <div
            style={{
              width: 40,
              height: 40,
              margin: '0 auto 6px',
              borderRadius: '50%',
              background: '#eaf1ff',
              color: P.primary,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
            }}
          >
            王
          </div>
          <div style={{ fontSize: 12, fontWeight: 600, color: P.text }}>王*平</div>
          <div style={{ fontSize: 9, color: P.text3 }}>转账金额（元）</div>
          <div style={{ fontSize: 26, fontWeight: 700, color: P.text }}>11.11</div>
          <div style={{ fontSize: 9, color: P.text3, marginTop: 4 }}>备注：请你吃饭</div>
        </Card>
        <Card style={{ marginBottom: 10 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>付款方式</span><span>账户余额（¥ 12,480.50）</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>到账方式</span><span>实时到账</span>
          </div>
        </Card>
        <div style={{ fontSize: 11, color: '#fff', background: P.grad, borderRadius: 18, textAlign: 'center', padding: '8px 0' }}>
          确认转账
        </div>
      </div>
      <div style={{ height: 12 }} />
    </Phone>
  );
}

function ResultMock() {
  return (
    <Phone
      title="结果页（转账/充值/提现/还款通用）"
      note="三态明确：成功 / 失败 / 处理中；提供「返回首页」「查看明细」后续入口；余额实时刷新。"
    >
      <NavBar title="转账结果" />
      <div style={{ padding: '20px 12px', textAlign: 'center' }}>
        <div
          style={{
            width: 52,
            height: 52,
            margin: '0 auto 10px',
            borderRadius: '50%',
            background: '#e6f7f1',
            color: P.success,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 24,
          }}
        >
          ✓
        </div>
        <div style={{ fontSize: 14, fontWeight: 600, color: P.text }}>转账成功</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: P.text, margin: '6px 0' }}>¥ 11.11</div>
        <div style={{ fontSize: 9, color: P.text3 }}>已转给 王*平 · 2026-08-11 10:24 · 实时到账</div>
        <Card style={{ margin: '14px 0', textAlign: 'left' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>交易单号</span><span>MC6E…ZM4T</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, padding: '3px 0' }}>
            <span style={{ color: P.text3 }}>交易后余额</span><span>¥ 12,469.39</span>
          </div>
        </Card>
        <div style={{ display: 'flex', gap: 8 }}>
          <span style={{ flex: 1, fontSize: 11, color: P.primary, background: '#eaf1ff', borderRadius: 16, padding: '7px 0' }}>
            查看明细
          </span>
          <span style={{ flex: 1, fontSize: 11, color: '#fff', background: P.grad, borderRadius: 16, padding: '7px 0' }}>
            返回首页
          </span>
        </div>
      </div>
    </Phone>
  );
}

export default function H5UiMockups() {
  return (
    <Stack gap={20}>
      <Stack gap={6}>
        <H1>MiniAlalipay C 端 H5 视觉设计稿 · 二、核心页面界面稿</H1>
        <Text tone="secondary">
          基于已确认的色彩令牌体系绘制 10 个核心页面。布局遵循信息层级原则：资金信息优先、
          操作入口次之、说明性内容靠后。所有金额整数分换算展示，掩码默认可切换。
        </Text>
      </Stack>

      <H2>核心资金视图</H2>
      <Grid columns={3} gap={16}>
        <HomeMock />
        <WalletMock />
        <TransactionsMock />
      </Grid>

      <Divider />

      <H2>银行卡模块</H2>
      <Grid columns={3} gap={16}>
        <BankCardsMock />
        <BankCardDetailMock />
        <RechargeMock />
      </Grid>

      <Divider />

      <H2>花呗 / 转账 / 结果</H2>
      <Grid columns={3} gap={16}>
        <CreditMock />
        <TransferConfirmMock />
        <ResultMock />
      </Grid>

      <Divider />

      <H2>门面页</H2>
      <Grid columns={3} gap={16}>
        <LoginMock />
        <div style={{ fontSize: 12, color: P.text2, lineHeight: 2 }}>
          <b>未单独出稿、按令牌统一的页面：</b>
          <div>注册页（与登录页同构，多昵称/密码确认字段）</div>
          <div>提现页（与充值页镜像：来源=账户余额，到账=银行卡）</div>
          <div>银行卡账单页（复用账户明细的月分组 + 汇总头结构，筛选：本月收入/本月支出/全部）</div>
          <div>扫码支付 / 收款页（商户信息卡 + 金额大字 + 密码确认）</div>
          <div>花呗账单 / 账单详情 / 还款页（复用明细列表与充值页表单结构）</div>
          <div>AI Talk（气泡与输入区套用品牌令牌）</div>
        </div>
      </Grid>

      <Divider />

      <H2>评审确认项</H2>
      <Text>
        1. 主色电光蓝 #256cff 与渐变方向是否认可；2. 卡面混合风格（龙纹/凤纹/祥云限定 + 科技基底）是否认可；
        3. 首页结构（资产摘要 + 快捷宫格 + 生活服务区占位 + 花呗摘要 + 明细入口）是否符合预期；
        4. 明细分组与花呗环形图样式是否认可。确认后进入编码实现阶段。
      </Text>
      <Text tone="secondary" size="small">生成于 C 端 H5 视觉体验升级任务 · 阶段一设计稿。</Text>
    </Stack>
  );
}
