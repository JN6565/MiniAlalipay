import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { Tabs, Card, Button, Input, Toast, SpinLoading } from 'antd-mobile';
import * as collectionService from '@/services/collection';
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
      const data = await collectionService.getMyCode();
      setPersonalCode(data);
    } catch (error) {
      console.error('加载失败', error);
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
    if (requestAmount < 0.01 || requestAmount > 50000) {
      Toast.show({ content: '金额范围0.01-50000元', icon: 'fail' });
      return;
    }

    setCreating(true);
    try {
      const data = await collectionService.createRequest({
        amountFen: Math.round(requestAmount * 100),
        subject: requestSubject,
      });
      Toast.show({ icon: 'success', content: '创建成功' });
      history.push(`/h5/collection/request/${data.requestId}`);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
    } finally {
      setCreating(false);
    }
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
        <Tabs.Tab title="固定收款请求" key="request" />
      </Tabs>

      {activeTab === 'code' ? (
        <div className="code-section">
          {personalCode ? (
            <Card className="code-card">
              <div className="code-status">
                状态：{personalCode.status === 'ACTIVE' ? '正常' : '已停用'}
              </div>
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
            <div className="form-title">创建固定收款请求</div>
            <AmountInput
              value={requestAmount}
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
              创建请求
            </Button>
          </Card>
        </div>
      )}
    </div>
  );
};

export default CollectionPage;
