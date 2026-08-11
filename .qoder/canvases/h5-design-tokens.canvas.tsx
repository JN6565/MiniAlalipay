import { Divider, Grid, H1, H2, Stack, Table, Text } from 'qoder/canvas';

/** 色板卡片：展示单个色彩令牌的名称、变量名与色值。 */
function Swatch(props: { name: string; token: string; color: string; darkText?: boolean }) {
  return (
    <div style={{ borderRadius: 10, overflow: 'hidden', border: '1px solid #e8eef7', minWidth: 0 }}>
      <div style={{ height: 52, background: props.color }} />
      <div style={{ padding: '6px 10px', background: '#fff' }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: '#16223a' }}>{props.name}</div>
        <div style={{ fontSize: 11, color: '#8d9aae', fontFamily: 'monospace' }}>
          {props.token} · {props.color}
        </div>
      </div>
    </div>
  );
}

/** 仿真银行卡预览：渐变底 + CSS 纹样 + 银行标识与卡号（卡面不出现风格命名文字，命名仅作设计稿标注）。 */
function BankCardPreview(props: {
  bank: string;
  gradient: string;
  pattern: string;
  patternName: string;
}) {
  return (
    <div style={{ minWidth: 0 }}>
      <div
        style={{
          position: 'relative',
          borderRadius: 14,
          padding: '14px 16px 16px',
          color: '#fff',
          background: `${props.pattern}, ${props.gradient}`,
          overflow: 'hidden',
          minHeight: 118,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
        }}
      >
        <span style={{ fontSize: 14, fontWeight: 700, letterSpacing: 1 }}>{props.bank}</span>
        <div style={{ fontSize: 15, letterSpacing: 3, fontFamily: 'Courier New, monospace' }}>
          **** **** **** 8888
        </div>
        <span style={{ fontSize: 11, opacity: 0.85 }}>储蓄卡 · 默认卡</span>
      </div>
      <div style={{ fontSize: 11, color: '#8d9aae', marginTop: 6, textAlign: 'center' }}>
        纹样：{props.patternName}（仅设计稿标注，不上卡面）
      </div>
    </div>
  );
}

export default function H5DesignTokens() {
  return (
    <Stack gap={20}>
      <Stack gap={6}>
        <H1>MiniAlalipay C 端 H5 视觉设计稿 · 一、色彩令牌体系</H1>
        <Text tone="secondary">
          设计定位：在 B 端「晴空」浅蓝（#2f7ff2）基础上提纯增亮，主色采用更鲜活的电光蓝，
          辅以青蓝渐变营造科技感；中性色带蓝倾向保持家族感。所有令牌落地为 --h5-* CSS 变量，
          本期仅实现亮色，暗色位预留。金额一律整数分换算，掩码默认、点击可见。
        </Text>
      </Stack>

      <H2>1. 品牌主色与辅助色</H2>
      <Grid columns={4} gap={12}>
        <Swatch name="主色 · 电光蓝" token="--h5-primary" color="#256cff" />
        <Swatch name="主色悬停" token="--h5-primary-hover" color="#4285ff" />
        <Swatch name="主色按下" token="--h5-primary-active" color="#1a56d9" />
        <Swatch name="主色浅底" token="--h5-primary-bg" color="#eaf1ff" />
      </Grid>
      <Grid columns={4} gap={12}>
        <Swatch name="辅助 · 青蓝" token="--h5-accent-cyan" color="#18c0e8" />
        <Swatch name="辅助 · 星紫" token="--h5-accent-violet" color="#7a6cff" />
        <Swatch name="品牌渐变起" token="--h5-grad-start" color="#256cff" />
        <Swatch name="品牌渐变止" token="--h5-grad-end" color="#18c0e8" />
      </Grid>

      <H2>2. 状态色与中性色</H2>
      <Grid columns={4} gap={12}>
        <Swatch name="成功" token="--h5-success" color="#16b387" />
        <Swatch name="警告" token="--h5-warning" color="#f59f2d" />
        <Swatch name="错误" token="--h5-danger" color="#f0484e" />
        <Swatch name="收入红" token="--h5-amount-in" color="#f0484e" />
      </Grid>
      <Grid columns={4} gap={12}>
        <Swatch name="主文本" token="--h5-text" color="#16223a" />
        <Swatch name="次文本" token="--h5-text-2" color="#5a6b85" />
        <Swatch name="弱文本" token="--h5-text-3" color="#94a3ba" />
        <Swatch name="页面背景" token="--h5-bg" color="#f4f6fa" />
      </Grid>
      <Grid columns={4} gap={12}>
        <Swatch name="卡片背景" token="--h5-card-bg" color="#ffffff" />
        <Swatch name="分割线" token="--h5-divider" color="#e8eef7" />
        <Swatch name="填充底" token="--h5-fill" color="#f0f4fa" />
        <Swatch name="暗色预留位" token="--h5-dark-*" color="#0f1727" darkText />
      </Grid>

      <Divider />

      <H2>3. 银行卡卡面体系（混合风格：科技基底 + 中国风限定款）</H2>
      <Text tone="secondary">
        七家银行差异化设计，全部纯 CSS 渐变 + 纹样叠加实现，不使用真实商标图片。
        工商银行「龙纹」、招商银行「凤纹」、中国银行「祥云」为中国风限定款；
        其余为科技几何纹。卡面信息层级：银行名 → 卡号尾段 → 类型与角标。
      </Text>
      <Grid columns={2} gap={14}>
        <BankCardPreview
          bank="中国工商银行"
          patternName="金鳞龙纹（中国风限定）"
          gradient="linear-gradient(135deg, #c8353f 0%, #8f1d2c 100%)"
          pattern="radial-gradient(circle at 82% 18%, rgba(255,215,160,0.35) 0%, rgba(255,215,160,0) 34%), repeating-radial-gradient(circle at 88% 10%, rgba(255,215,160,0.14) 0 6px, transparent 6px 14px)"
        />
        <BankCardPreview
          bank="招商银行"
          patternName="凤羽流光（中国风限定）"
          gradient="linear-gradient(135deg, #d9483b 0%, #96282b 100%)"
          pattern="repeating-conic-gradient(from 200deg at 85% 15%, rgba(255,235,210,0.16) 0deg 8deg, transparent 8deg 20deg)"
        />
        <BankCardPreview
          bank="中国银行"
          patternName="祥云环绕（中国风限定）"
          gradient="linear-gradient(135deg, #b03a3a 0%, #7d2226 100%)"
          pattern="radial-gradient(circle at 78% 22%, rgba(255,255,255,0.22) 0 8px, transparent 9px), radial-gradient(circle at 88% 30%, rgba(255,255,255,0.18) 0 6px, transparent 7px), radial-gradient(circle at 82% 34%, rgba(255,255,255,0.14) 0 10px, transparent 11px)"
        />
        <BankCardPreview
          bank="中国建设银行"
          patternName="光栅线条（科技几何）"
          gradient="linear-gradient(135deg, #2b6cb0 0%, #153e6e 100%)"
          pattern="repeating-linear-gradient(115deg, rgba(255,255,255,0.10) 0 2px, transparent 2px 18px)"
        />
        <BankCardPreview
          bank="中国农业银行"
          patternName="麦浪弧线（科技几何）"
          gradient="linear-gradient(135deg, #2f9e63 0%, #17603c 100%)"
          pattern="repeating-radial-gradient(ellipse at 50% 130%, rgba(255,255,255,0.12) 0 3px, transparent 3px 16px)"
        />
        <BankCardPreview
          bank="交通银行"
          patternName="数据波纹（科技几何）"
          gradient="linear-gradient(135deg, #33569e 0%, #1a2f5e 100%)"
          pattern="repeating-linear-gradient(0deg, rgba(255,255,255,0.08) 0 2px, transparent 2px 14px)"
        />
        <BankCardPreview
          bank="中国邮政储蓄银行"
          patternName="极光晕染（科技几何）"
          gradient="linear-gradient(135deg, #1f8a5b 0%, #0e5c3a 60%, #0b4a30 100%)"
          pattern="radial-gradient(ellipse at 20% 0%, rgba(120,255,214,0.20) 0%, transparent 55%)"
        />
        <div
          style={{
            borderRadius: 14,
            border: '1px dashed #c3d2e8',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
            gap: 6,
            color: '#8d9aae',
            minHeight: 118,
            background: '#f7f9fc',
          }}
        >
          <span style={{ fontSize: 22 }}>＋</span>
          <span style={{ fontSize: 12 }}>未识别银行 · 通用科技渐变兜底</span>
          <span style={{ fontSize: 11, fontFamily: 'monospace' }}>linear-gradient(135deg,#3a4a63,#232f42)</span>
        </div>
      </Grid>

      <Divider />

      <H2>4. 圆角 / 阴影 / 字号层级</H2>
      <Grid columns={2} gap={14}>
        <Table
          headers={['令牌', '取值', '用途']}
          rows={[
            ['--h5-radius-card', '14px', '卡片、弹窗、卡面'],
            ['--h5-radius-btn', '24px', '按钮、标签胶囊'],
            ['--h5-radius-input', '10px', '输入框、选择器'],
            ['--h5-shadow-card', '0 2px 12px rgba(22,60,120,0.06)', '卡片浮起'],
            ['--h5-shadow-float', '0 8px 28px rgba(37,108,255,0.18)', '悬浮按钮/弹层'],
          ]}
        />
        <div style={{ background: '#fff', border: '1px solid #e8eef7', borderRadius: 10, padding: 16 }}>
          <div style={{ fontSize: 30, fontWeight: 700, color: '#16223a' }}>¥ 12,480.50</div>
          <div style={{ fontSize: 11, color: '#94a3ba', margin: '2px 0 10px' }}>H1 资产数字 · 30px/700</div>
          <div style={{ fontSize: 17, fontWeight: 600, color: '#16223a' }}>区块标题 · 17px/600</div>
          <div style={{ fontSize: 14, color: '#5a6b85', margin: '6px 0' }}>正文说明 · 14px/400 次文本色</div>
          <div style={{ fontSize: 12, color: '#94a3ba' }}>辅助标注 · 12px/400 弱文本色</div>
        </div>
      </Grid>

      <Divider />

      <H2>5. 关键交互与细节约定</H2>
      <Table
        headers={['维度', '约定']}
        rows={[
          ['金额输入', 'AmountInput 支持 11.11 小数（最多两位），内部字符串承载避免小数点被吞'],
          ['金额展示', '+¥100.00 收入红 / −¥50.00 深色支出；明细附交易后余额（后端 balanceAfterFen）'],
          ['时间格式', 'yyyy-MM-dd HH:mm 精确到分'],
          ['掩码安全', '余额、卡号、手机号默认掩码，RevealToggle 点击显示，不落日志与本地存储'],
          ['余额实时性', '资金操作成功后返回页自动重拉，无需手动刷新'],
          ['加载与空态', '异步区域 Skeleton 骨架屏；空数据 EmptyState 引导文案 + 按钮'],
          ['错误提示', '中文提示 + 可执行建议，如「余额不足，可先充值」'],
        ]}
      />
      <Text tone="secondary" size="small">
        评审说明：确认本色彩体系与卡面风格后，进入界面设计稿评审（h5-ui-mockups），两者均确认后开始编码。
      </Text>
    </Stack>
  );
}
