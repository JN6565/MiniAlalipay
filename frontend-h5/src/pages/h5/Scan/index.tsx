import React, { useEffect, useRef, useState } from 'react';
import { history } from 'umi';
import { Toast, SpinLoading, Dialog } from 'antd-mobile';
import { Html5Qrcode } from 'html5-qrcode';
import './index.less';

const ScanPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const html5QrCodeRef = useRef<Html5Qrcode | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    initScanner();
    return () => {
      stopScanner();
    };
  }, []);

  const initScanner = async () => {
    try {
      const html5QrCode = new Html5Qrcode('qr-reader');
      html5QrCodeRef.current = html5QrCode;
      setLoading(false);
    } catch (error) {
      console.error('初始化扫码器失败:', error);
      Toast.show({ content: '初始化扫码器失败', icon: 'fail' });
    }
  };

  const startScanner = async () => {
    if (!html5QrCodeRef.current || scanning) return;

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
    } catch (error) {
      console.error('启动扫码失败:', error);
      Toast.show({ content: '启动扫码失败，请检查摄像头权限', icon: 'fail' });
      setScanning(false);
    }
  };

  const stopScanner = async () => {
    if (html5QrCodeRef.current && scanning) {
      try {
        await html5QrCodeRef.current.stop();
      } catch (error) {
        console.error('停止扫码失败:', error);
      }
    }
    setScanning(false);
  };

  const onScanSuccess = async (decodedText: string) => {
    // 停止扫码
    await stopScanner();

    // 解析二维码内容
    handleQrCodeContent(decodedText);
  };

  const onScanFailure = (error: string) => {
    // 扫码失败时不做处理，继续扫描
    console.debug('扫码失败:', error);
  };

  const handleQrCodeContent = (content: string) => {
    try {
      // 尝试解析为URL
      const url = new URL(content);

      // 检查是否是收款码URL
      if (url.pathname.includes('/p2p-collections/by-token')) {
        // 个人收款码
        const token = url.searchParams.get('t');
        if (token) {
          history.push(`/h5/collection/pay/${token}`);
          return;
        }
      }

      // 检查是否是扫码支付URL
      if (url.pathname.includes('/qr-pay/orders/by-token')) {
        // 动态扫码收款
        const token = url.searchParams.get('t');
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

  return (
    <div className="scan-page">
      <div className="scan-header">
        <span className="back-btn" onClick={() => history.back()}>←</span>
        <span className="title">扫一扫</span>
        <span className="placeholder"></span>
      </div>

      <div className="scan-content">
        {loading ? (
          <div className="loading-container">
            <SpinLoading />
            <div className="loading-text">初始化扫码器...</div>
          </div>
        ) : (
          <>
            <div className="qr-reader-container" ref={containerRef}>
              <div id="qr-reader" className="qr-reader"></div>
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
