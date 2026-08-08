import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Tabs, Card, Button, Input, Toast, SpinLoading } from 'antd-mobile';
import { QRCodeSVG } from 'qrcode.react';
import * as collectionService from '@/services/collection';
import { AMOUNT_MIN, AMOUNT_MAX } from '@/constants';
import { AmountInput } from '@/components/h5/AmountInput';
import { formatTime } from '@/utils/format';
import './index.less';

const CollectionPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('code');
  const [loading, setLoading] = useState(true);
  const [personalCode, setPersonalCode] = useState<collectionService.PersonalCode | null>(null);
  const [requestAmount, setRequestAmount] = useState(0);
  const [requestSubject, setRequestSubject] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    loadPersonalCode();
  }, []);

  const loadPersonalCode = async () => {
    try {
      // 先尝试获取已有收款码
      console.log('正在获取收款码...');
      const data = await collectionService.getMyCode();
      console.log('获取收款码结果:', data);

      if (data && data.status === 'ACTIVE' && !data.collectionUrl) {
        // 已有收款码但没有URL（安全设计：服务器不存储原始令牌），需要重新生成获取URL
        console.log('收款码存在但没有URL，正在重新生成...');
        const regenData = await collectionService.regenerateCode();
        console.log('重新生成结果:', regenData);
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
      <Tabs activeKey={activeTab} onChange={setActiveTab}>
        <Tabs.Tab title="个人收款码" key="code" />
        <Tabs.Tab title="设置金额" key="request" />
      </Tabs>

      {activeTab === 'code' ? (
        <div className="code-section">
          {personalCode ? (
            <Card className="code-card">
              <div className="code-status">
                状态：{personalCode.status === 'ACTIVE' ? '正常' : '已停用'}
              </div>
              {personalCode.status === 'ACTIVE' && getCollectionUrl() && (
                <div className="code-qr">
                  <QRCodeSVG
                    value={getCollectionUrl()}
                    size={160}
                    level="H"
                    includeMargin={true}
                  />
                </div>
              )}
              {personalCode.status === 'ACTIVE' && !getCollectionUrl() && (
                <div className="code-empty">收款码加载中，请稍候...</div>
              )}
              <div className="code-actions">
                <Button size="small" onClick={handleRegenerate}>
                  重新生成
                </Button>
              </div>
            </Card>
          ) : (
            <Card className="code-card">
              <div className="code-empty">暂无个人收款码</div>
              <Button color="primary" onClick={handleRegenerate}>
                生成收款码
              </Button>
            </Card>
          )}
        </div>
      ) : (
        <div className="request-section">
          <Card className="request-form">
            <div className="form-title">设置金额</div>
            <AmountInput
              value={requestAmount || undefined}
              onChange={setRequestAmount}
              placeholder="请输入收款金额"
            />
            <Input
              placeholder="备注（选填）"
              value={requestSubject}
              onChange={setRequestSubject}
              maxLength={50}
            />
            <Button
              block
              color="primary"
              loading={creating}
              onClick={handleCreateRequest}
              style={{ marginTop: 16 }}
            >
              生成固定金额收款码
            </Button>
          </Card>
        </div>
      )}
    </div>
  );
};

export default CollectionPage;
