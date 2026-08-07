import React, { useEffect, useRef, useState, useCallback } from 'react';
import { history } from 'umi';
import { Toast, Dialog } from 'antd-mobile';
import { Html5Qrcode } from 'html5-qrcode';
import './index.less';

const ScanPage: React.FC = () => {
  const [initializing, setInitializing] = useState(true);
  const [scanning, setScanning] = useState(false);
  const html5QrCodeRef = useRef<Html5Qrcode | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    // 等待DOM渲染完成后再初始化
    const timer = setTimeout(() => {
      if (mountedRef.current) {
        initScanner();
      }
    }, 200);

    return () => {
      mountedRef.current = false;
      clearTimeout(timer);
      // 强制停止扫码器
      if (html5QrCodeRef.current) {
        try {
          html5QrCodeRef.current.stop().catch(() => {});
        } catch (e) {
          // 忽略错误
        }
        html5QrCodeRef.current = null;
      }
    };
  }, []);

  const initScanner = async () => {
    if (!mountedRef.current) return;

    try {
      // 检查DOM元素是否存在
      const qrReaderElement = document.getElementById('qr-reader');
      if (!qrReaderElement) {
        console.error('qr-reader元素不存在，重试中...');
        // 重试一次
        setTimeout(() => {
          if (mountedRef.current) initScanner();
        }, 200);
        return;
      }

      const html5QrCode = new Html5Qrcode('qr-reader');
      html5QrCodeRef.current = html5QrCode;
      if (mountedRef.current) {
        setInitializing(false);
      }

      // 自动开始扫码
      startScanner();
    } catch (error) {
      console.error('初始化扫码器失败:', error);
      if (mountedRef.current) {
        Toast.show({ content: '初始化扫码器失败', icon: 'fail' });
        setInitializing(false);
      }
    }
  };

  const startScanner = async () => {
    if (!html5QrCodeRef.current || scanning || !mountedRef.current) return;

    setScanning(true);
    try {
      await html5QrCodeRef.current.start(
        { facingMode: 'environment' },
        {
          fps: 10,
          qrbox: { width: 250, height: 250 },
          aspectRatio: 1.0,
        },
        onScanSuccess,
        onScanFailure,
      );
    } catch (error: any) {
      console.error('启动扫码失败:', error);
      if (!mountedRef.current) return;
      let errorMessage = '启动扫码失败';
      if (error?.message?.includes('NotAllowedError') || error?.message?.includes('Permission')) {
        errorMessage = '请允许摄像头权限后重试';
      } else if (error?.message?.includes('NotFoundError')) {
        errorMessage = '未找到摄像头设备';
      } else if (error?.message?.includes('NotReadableError')) {
        errorMessage = '摄像头被其他应用占用';
      } else if (window.location.protocol !== 'https:' && window.location.hostname !== 'localhost') {
        errorMessage = '需要HTTPS环境才能使用摄像头';
      }
      Toast.show({ content: errorMessage, icon: 'fail' });
      setScanning(false);
    }
  };

  const stopScanner = async () => {
    if (html5QrCodeRef.current) {
      try {
        await html5QrCodeRef.current.stop();
      } catch (error) {
        // 忽略停止错误
      }
    }
    if (mountedRef.current) {
      setScanning(false);
    }
  };

  const onScanSuccess = async (decodedText: string) => {
    if (!mountedRef.current) return;

    // 停止扫码
    await stopScanner();

    // 解析二维码内容
    handleQrCodeContent(decodedText);
  };

  const onScanFailure = (error: string) => {
    // 扫码失败时不做处理，继续扫描（不输出日志避免刷屏）
  };

  const handleQrCodeContent = (content: string) => {
    try {
      let pathname = '';
      let searchParams = new URLSearchParams();

      // 尝试解析为完整URL
      if (content.startsWith('http://') || content.startsWith('https://')) {
        const url = new URL(content);
        pathname = url.pathname;
        searchParams = url.searchParams;
      }
      // 相对路径形式：/api/v1/p2p-collections/by-token?t=xxx
      else if (content.startsWith('/')) {
        const [path, query] = content.split('?');
        pathname = path;
        searchParams = new URLSearchParams(query || '');
      }

      // 检查是否是收款码URL
      if (pathname.includes('/p2p-collections/by-token')) {
        const token = searchParams.get('t');
        if (token) {
          history.push(`/h5/collection/pay/${token}`);
          return;
        }
      }

      // 检查是否是扫码支付URL
      if (pathname.includes('/qr-pay/orders/by-token')) {
        const token = searchParams.get('t');
        if (token) {
          history.push(`/h5/qr-pay/${token}`);
          return;
        }
      }

      // 如果不是识别的二维码格式，显示内容
      Dialog.alert({
        content: `扫描结果：${content}`,
        confirmText: '确定',
      });
    } catch (error) {
      // 不是URL格式，显示内容
      Dialog.alert({
        content: `扫描结果：${content}`,
        confirmText: '确定',
      });
    }
  };

  // 检查是否支持摄像头
  const isCameraSupported = () => {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  };

  // 检查是否是HTTPS环境
  const isSecureContext = () => {
    return window.location.protocol === 'https:' || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
  };

  return (
    <div className="scan-page">
      <div className="scan-header">
        <span className="back-btn" onClick={() => history.back()}>←</span>
        <span className="title">扫一扫</span>
        <span className="placeholder"></span>
      </div>

      <div className="scan-content">
        {!isCameraSupported() ? (
          <div className="error-container">
            <div className="error-icon">📷</div>
            <div className="error-text">您的浏览器不支持摄像头功能</div>
            <div className="error-hint">请使用现代浏览器访问</div>
          </div>
        ) : !isSecureContext() ? (
          <div className="error-container">
            <div className="error-icon">🔒</div>
            <div className="error-text">需要HTTPS环境才能使用摄像头</div>
            <div className="error-hint">请使用HTTPS访问或使用localhost</div>
          </div>
        ) : (
          <>
            <div className="qr-reader-container" ref={containerRef}>
              <div id="qr-reader" className="qr-reader"></div>
              {initializing && (
                <div className="loading-overlay">
                  <div className="loading-text">初始化扫码器...</div>
                </div>
              )}
            </div>

            <div className="scan-tips">
              <div className="tip-text">将二维码放入框内，即可自动扫描</div>
            </div>

            <div className="scan-actions">
              {!scanning ? (
                <button className="scan-btn" onClick={startScanner}>
                  开始扫码
                </button>
              ) : (
                <button className="scan-btn stop" onClick={stopScanner}>
                  停止扫码
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default ScanPage;
