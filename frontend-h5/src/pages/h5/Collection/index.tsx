import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Input, Toast, SpinLoading } from 'antd-mobile';
import { QRCodeSVG } from 'qrcode.react';
import * as collectionService from '@/services/collection';
import { AMOUNT_MIN, AMOUNT_MAX } from '@/constants';
import { AmountInput } from '@/components/h5/AmountInput';
import { AvatarView } from '@/components/h5/common';
import { getProfilePreference } from '@/utils/profile';
import './index.less';

const CollectionPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'code' | 'request'>('code');
  const [loading, setLoading] = useState(true);
  const [personalCode, setPersonalCode] = useState<collectionService.PersonalCode | null>(null);
  const [requestAmount, setRequestAmount] = useState(0);
  const [requestSubject, setRequestSubject] = useState('');
  const [creating, setCreating] = useState(false);

  // 收款码卡片展示的昵称：本地偏好（与首页/我的页同源）
  const nickname = getProfilePreference().nickname || '我';

  useEffect(() => {
    loadPersonalCode();
  }, []);

  const loadPersonalCode = async () => {
    try {
      // 先尝试获取已有收款码
      const data = await collectionService.getMyCode();
      if (data && data.status === 'ACTIVE' && !data.collectionUrl) {
        // 已有收款码但没有URL（安全设计：服务器不存储原始令牌），需要重新生成获取URL
        const regenData = await collectionService.regenerateCode();
        setPersonalCode(regenData);
      } else {
        setPersonalCode(data);
      }
    } catch (error: any) {
      console.error('加载失败', error);
      Toast.show({ content: error?.message || '当前网络环境较差，数据暂未返回，请稍后重试', icon: 'fail' });
    } finally {
      setLoading(false);
    }
  };

  const handleRegenerate = async () => {
    try {
      const data = await collectionService.regenerateCode();
      setPersonalCode(data);
      Toast.show({ icon: 'success', content: '重新生成成功' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '操作失败' });
    }
  };

  const handleCreateRequest = async () => {
    if (requestAmount < AMOUNT_MIN || requestAmount > AMOUNT_MAX) {
      Toast.show({ content: `金额范围 ${AMOUNT_MIN}-${AMOUNT_MAX} 元`, icon: 'fail' });
      return;
    }

    setCreating(true);
    try {
      const data = await collectionService.createRequest({
        amountFen: Math.round(requestAmount * 100),
        subject: requestSubject,
      });
      if (data.collectionUrl) {
        sessionStorage.setItem(`collection-qr-${data.requestId}`, data.collectionUrl);
      }
      history.push(`/h5/collection/request/${data.requestId}`, { collectionUrl: data.collectionUrl });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
    } finally {
      setCreating(false);
    }
  };

  // 保存二维码：将码卡内的 SVG 序列化后下载到本地
  const handleSaveQr = () => {
    const svg = document.querySelector('.collection-code-qr svg');
    if (!svg) {
      Toast.show({ icon: 'fail', content: '二维码未就绪' });
      return;
    }
    const source = new XMLSerializer().serializeToString(svg);
    const blob = new Blob([source], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'collection-code.svg';
    link.click();
    URL.revokeObjectURL(url);
    Toast.show({ icon: 'success', content: '二维码已保存' });
  };

  // 二维码内容直接使用后端返回的相对路径：App 内扫码按路径与令牌参数识别跳转，
  // 不拼接当前设备 origin，避免付款方设备地址不同导致二维码失效
  const getCollectionUrl = () => {
    return personalCode?.collectionUrl || '';
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading />
      </div>
    );
  }

  return (
    <div className="collection-page">
      {/* 品牌渐变头 + 白色双 Tab */}
      <div className="collection-hero">
        <div className="collection-tabs">
          {(
            [
              ['code', '个人收款码'],
              ['request', '设置金额'],
            ] as Array<['code' | 'request', string]>
          ).map(([key, label]) => (
            <div
              key={key}
              className={`collection-tab${activeTab === key ? ' active' : ''}`}
              onClick={() => setActiveTab(key)}
            >
              <div className="tab-text">{label}</div>
              {activeTab === key && <div className="tab-indicator" />}
            </div>
          ))}
        </div>
      </div>

      {activeTab === 'code' ? (
        <div className="collection-body">
          <div className="collection-card code-card">
            {personalCode ? (
              <>
                <div className="code-owner">
                  <AvatarView size={22} />
                  <span>{nickname} 的收款码</span>
                </div>
                {personalCode.status === 'ACTIVE' && getCollectionUrl() && (
                  <div className="collection-code-qr">
                    <QRCodeSVG value={getCollectionUrl()} size={150} level="H" includeMargin={true} />
                  </div>
                )}
                {personalCode.status === 'ACTIVE' && !getCollectionUrl() && (
                  <div className="code-empty">收款码加载中，请稍候...</div>
                )}
                {personalCode.status !== 'ACTIVE' && (
                  <div className="code-empty">收款码已停用，请重新生成</div>
                )}
                <div className="code-tips">对方扫码后可向你付款，码长期有效</div>
                <div className="code-actions">
                  <div className="code-btn outline" onClick={handleSaveQr}>
                    保存二维码
                  </div>
                  <div className="code-btn gradient" onClick={() => setActiveTab('request')}>
                    设置金额收款
                  </div>
                </div>
                <div className="code-regen" onClick={handleRegenerate}>
                  重新生成收款码
                </div>
              </>
            ) : (
              <>
                <div className="code-empty">暂无个人收款码</div>
                <div className="code-actions">
                  <div className="code-btn gradient full" onClick={handleRegenerate}>
                    生成收款码
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      ) : (
        <div className="collection-body">
          <div className="collection-card request-card">
            <div className="request-label">收款金额</div>
            <AmountInput
              value={requestAmount || undefined}
              onChange={setRequestAmount}
              placeholder="0.00"
            />
            <div className="request-divider" />
            <Input
              placeholder="备注（选填）"
              value={requestSubject}
              onChange={setRequestSubject}
              maxLength={50}
              className="request-subject"
            />
            <div
              className={`h5-btn-gradient request-submit${creating ? ' disabled' : ''}`}
              onClick={() => !creating && handleCreateRequest()}
            >
              {creating ? '生成中...' : '生成固定金额收款码'}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CollectionPage;
