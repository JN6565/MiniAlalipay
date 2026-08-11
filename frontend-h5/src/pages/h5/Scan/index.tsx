import React, { useEffect, useRef, useState } from 'react';
import { history } from 'umi';
import { Toast, Dialog } from 'antd-mobile';
import { Html5Qrcode } from 'html5-qrcode';
import { IconSet } from '@/components/h5/common';
import * as collectionService from '@/services/collection';
import './index.less';

/** 示例码：摄像头不可用环境下一键体验完整解析流程（与真实码同前缀规则）。 */
const SAMPLE_CODES = [
  { label: '个人收款码', value: 'MINI_COLLECT:tok_demo' },
  { label: '固定金额收款码', value: 'MINI_COLLECT_REQ:req_demo' },
  { label: '商户付款码', value: 'MINI_QRPAY:pay_demo' },
];

/**
 * 扫一扫页（V2）：深色沉浸式取景态 + 摄像头不可用时手动输入降级态。
 *
 * 解析规则：
 * - MINI_COLLECT:<token>        个人收款码 → 收款支付页
 * - MINI_COLLECT_REQ:<id>       固定金额收款码 → 收款请求支付页
 * - MINI_QRPAY:<token>          商户付款码 → 扫码支付页
 * - URL 形式（含 /p2p-collections/by-token、/qr-pay/orders/by-token）同样兼容
 */
const ScanPage: React.FC = () => {
  // 摄像头环境不可用或启动失败时自动进入手动输入降级态
  const cameraUnavailable = !isCameraSupported() || !isSecureContext();
  const [mode, setMode] = useState<'camera' | 'manual'>(cameraUnavailable ? 'manual' : 'camera');
  const [initializing, setInitializing] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [exchanging, setExchanging] = useState(false);
  const html5QrCodeRef = useRef<Html5Qrcode | null>(null);
  const mountedRef = useRef(true);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    mountedRef.current = true;
    if (mode === 'camera') {
      // 等待 DOM 渲染完成后再初始化
      const timer = setTimeout(() => {
        if (mountedRef.current) initScanner();
      }, 200);
      return () => {
        mountedRef.current = false;
        clearTimeout(timer);
        stopScannerInstance();
      };
    }
    return () => {
      mountedRef.current = false;
      stopScannerInstance();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  const stopScannerInstance = () => {
    if (html5QrCodeRef.current) {
      try {
        html5QrCodeRef.current.stop().catch(() => {});
      } catch (e) {
        // 忽略停止错误
      }
      html5QrCodeRef.current = null;
    }
  };

  const initScanner = async () => {
    if (!mountedRef.current) return;
    try {
      const qrReaderElement = document.getElementById('qr-reader');
      if (!qrReaderElement) {
        setTimeout(() => {
          if (mountedRef.current && mode === 'camera') initScanner();
        }, 200);
        return;
      }
      html5QrCodeRef.current = new Html5Qrcode('qr-reader');
      if (mountedRef.current) setInitializing(false);
      startScanner();
    } catch (error) {
      console.error('初始化扫码器失败:', error);
      if (mountedRef.current) {
        setInitializing(false);
        fallbackToManual('初始化扫码器失败，已切换为手动输入');
      }
    }
  };

  const startScanner = async () => {
    if (!html5QrCodeRef.current || scanning || !mountedRef.current) return;
    setScanning(true);
    try {
      await html5QrCodeRef.current.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 }, aspectRatio: 1.0 },
        onScanSuccess,
        () => {}, // 连续识别回调：未识别帧不提示
      );
    } catch (error: any) {
      console.error('启动扫码失败:', error);
      if (!mountedRef.current) return;
      let errorMessage = '启动扫码失败';
      if (error?.message?.includes('NotAllowedError') || error?.message?.includes('Permission')) {
        errorMessage = '摄像头权限被拒绝';
      } else if (error?.message?.includes('NotFoundError')) {
        errorMessage = '未找到摄像头设备';
      } else if (error?.message?.includes('NotReadableError')) {
        errorMessage = '摄像头被其他应用占用';
      }
      setScanning(false);
      fallbackToManual(`${errorMessage}，已切换为手动输入`);
    }
  };

  const fallbackToManual = (message: string) => {
    Toast.show({ content: message });
    setMode('manual');
  };

  const stopScanner = async () => {
    if (html5QrCodeRef.current) {
      try {
        await html5QrCodeRef.current.stop();
      } catch (error) {
        // 忽略停止错误
      }
    }
    if (mountedRef.current) setScanning(false);
  };

  const onScanSuccess = async (decodedText: string) => {
    if (!mountedRef.current) return;
    await stopScanner();
    handleQrCodeContent(decodedText);
  };

  /** 相册选图识别：复用 Html5Qrcode 的本地文件解析能力。 */
  const handleAlbumFile = async (file: File) => {
    try {
      const instance = html5QrCodeRef.current || new Html5Qrcode('qr-reader');
      const text = await instance.scanFile(file, true);
      handleQrCodeContent(text);
    } catch {
      Toast.show({ content: '未识别到二维码，请换一张清晰的图片', icon: 'fail' });
    }
  };

  /** 解析码内容并跳转对应流程（摄像头识别与手动输入共用）。 */
  const handleQrCodeContent = (content: string) => {
    const raw = content.trim();
    try {
      // 自定义前缀令牌：MINI_COLLECT / MINI_COLLECT_REQ / MINI_QRPAY
      const prefixMatch = raw.match(/^MINI_(COLLECT_REQ|COLLECT|QRPAY):(.+)$/);
      if (prefixMatch) {
        const [, type, token] = prefixMatch;
        if (type === 'COLLECT') {
          history.push(`/h5/collection/pay/${token}`);
        } else if (type === 'COLLECT_REQ') {
          history.push(`/h5/collection/request/${token}`);
        } else {
          history.push(`/h5/qr-pay/${token}`);
        }
        return;
      }

      let pathname = '';
      let searchParams = new URLSearchParams();
      if (raw.startsWith('http://') || raw.startsWith('https://')) {
        const url = new URL(raw);
        pathname = url.pathname;
        searchParams = url.searchParams;
      } else if (raw.startsWith('/')) {
        const [path, query] = raw.split('?');
        pathname = path;
        searchParams = new URLSearchParams(query || '');
      }

      if (pathname.includes('/p2p-collections/by-token')) {
        const token = searchParams.get('t');
        if (token) {
          history.push(`/h5/collection/pay/${token}`);
          return;
        }
      }
      if (pathname.includes('/qr-pay/orders/by-token')) {
        const token = searchParams.get('t');
        if (token) {
          history.push(`/h5/qr-pay/${token}`);
          return;
        }
      }

      Dialog.alert({ content: `扫描结果：${raw}`, confirmText: '确定' });
    } catch (error) {
      Dialog.alert({ content: `扫描结果：${raw}`, confirmText: '确定' });
    }
  };

  /** 手动输入降级：8 位纯数字按收款短码兑换，其余按码内容解析并跳转。 */
  const handleManualParse = async () => {
    const raw = manualCode.trim();
    if (!raw) {
      Toast.show({ content: '请输入或粘贴码内容', icon: 'fail' });
      return;
    }
    // 8 位纯数字：收款短码，兑换结果与扫到对应二维码等价
    if (/^\d{8}$/.test(raw)) {
      if (exchanging) return;
      setExchanging(true);
      try {
        const result = await collectionService.exchangeShortCode(raw);
        if (result.codeType === 'QR_PAY_ORDER') {
          history.push(`/h5/qr-pay/${result.orderId}?via=short-code`);
        } else {
          // 个人码与固定请求兑换后都是 C2C 订单，统一进入收款支付页
          history.push(`/h5/collection/pay/${result.orderId}?via=short-code`);
        }
      } catch (error: any) {
        if (error?.code === 'SHORT_CODE_INVALID') {
          Toast.show({ content: '码不存在或已过期', icon: 'fail' });
        } else if (error?.code === 'SHORT_CODE_RATE_LIMITED') {
          Toast.show({ content: '尝试过于频繁，请稍后再试', icon: 'fail' });
        } else {
          Toast.show({ content: error?.message || '兑换失败，请稍后重试', icon: 'fail' });
        }
      } finally {
        setExchanging(false);
      }
      return;
    }
    handleQrCodeContent(raw);
  };

  return (
    <div className="scan-page">
      <div className="scan-header">
        <span className="back-btn" onClick={() => history.back()}>
          <IconSet name="back" size={18} color="#fff" />
        </span>
        <span className="title">扫一扫</span>
        <span className="placeholder" />
      </div>

      {mode === 'camera' ? (
        <div className="scan-content">
          <div className="qr-reader-container">
            <div id="qr-reader" className="qr-reader" />
            {/* 取景框四角 + 扫描线（品牌渐变） */}
            <div className="frame-corner tl" />
            <div className="frame-corner tr" />
            <div className="frame-corner bl" />
            <div className="frame-corner br" />
            <div className="scan-line" />
            {initializing && (
              <div className="loading-overlay">
                <div className="loading-text">初始化扫码器...</div>
              </div>
            )}
          </div>

          <div className="scan-tips">将收款码 / 付款码放入框内，自动识别</div>

          <div className="scan-bottom-actions">
            <div className="scan-action" onClick={() => fileInputRef.current?.click()}>
              <span className="scan-action-circle">
                <IconSet name="camera" size={18} color="#fff" />
              </span>
              <span className="scan-action-label">相册</span>
            </div>
            {!scanning ? (
              <div className="scan-action" onClick={startScanner}>
                <span className="scan-action-circle">
                  <IconSet name="scan" size={18} color="#fff" />
                </span>
                <span className="scan-action-label">开始扫码</span>
              </div>
            ) : (
              <div className="scan-action" onClick={stopScanner}>
                <span className="scan-action-circle">
                  <IconSet name="close" size={18} color="#fff" />
                </span>
                <span className="scan-action-label">停止扫码</span>
              </div>
            )}
            <div className="scan-action" onClick={() => setMode('manual')}>
              <span className="scan-action-circle">
                <IconSet name="keyboard" size={18} color="#fff" />
              </span>
              <span className="scan-action-label">手动输入</span>
            </div>
          </div>

          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleAlbumFile(file);
              e.target.value = '';
            }}
          />
        </div>
      ) : (
        <div className="scan-manual">
          {/* 手动输入卡片 */}
          <div className="manual-card">
            <div className="manual-head">
              <IconSet name="keyboard" size={17} color="var(--h5-primary)" />
              <span>手动输入码内容</span>
            </div>
            <div className="manual-hint">
              {cameraUnavailable
                ? !isSecureContext()
                  // 非安全上下文（HTTP/IP 直连）：浏览器禁止调用摄像头，明确提示 HTTPS 要求与降级路径
                  ? '摄像头扫码需要 HTTPS 环境，请使用域名 HTTPS 访问，或在下方输入 8 位收款短码 / 粘贴码内容'
                  : '当前环境无法使用摄像头，可输入 8 位收款短码或粘贴二维码内容完成识别'
                : '摄像头暂不可用时，可输入 8 位收款短码或粘贴二维码内容完成识别'}
            </div>
            <div className="manual-input">
              <IconSet name="qr" size={16} color="var(--h5-text-3)" />
              <input
                value={manualCode}
                onChange={(e) => setManualCode(e.target.value)}
                placeholder="输入 8 位收款短码，或粘贴码内容"
              />
              {manualCode && (
                <span className="manual-clear" onClick={() => setManualCode('')}>
                  <IconSet name="close" size={13} color="var(--h5-text-3)" />
                </span>
              )}
            </div>
            <div className="h5-btn-gradient manual-submit" onClick={handleManualParse}>
              {exchanging ? '兑换中...' : '解析并跳转'}
            </div>
          </div>

          {/* 示例码：一键体验解析流程 */}
          <div className="manual-samples">
            <div className="samples-title">试试示例码（点击进入对应流程）</div>
            {SAMPLE_CODES.map((s) => (
              <div
                key={s.value}
                className="sample-row"
                onClick={() => handleQrCodeContent(s.value)}
              >
                <span className="sample-label">{s.label}</span>
                <span className="sample-value">{s.value}</span>
              </div>
            ))}
          </div>

          {!cameraUnavailable && (
            <div className="manual-back" onClick={() => setMode('camera')}>
              返回摄像头扫码
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// 检查是否支持摄像头
function isCameraSupported() {
  return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
}

// 检查是否是 HTTPS 环境（摄像头 API 要求安全上下文）
function isSecureContext() {
  return window.location.protocol === 'https:' || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
}

export default ScanPage;
