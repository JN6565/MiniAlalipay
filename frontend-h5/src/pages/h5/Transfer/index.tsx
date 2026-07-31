import React, { useState } from 'react';
import { history } from 'umi';
import { Form, Input, Button, Toast, SearchBar } from 'antd-mobile';
import * as userService from '@/services/user';
import * as transferService from '@/services/transfer';
import { AmountInput } from '@/components/h5/AmountInput';
import './index.less';

const TransferPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [payeeKeyword, setPayeeKeyword] = useState('');
  const [payeeCandidates, setPayeeCandidates] = useState<userService.PayeeInfo[]>([]);
  const [selectedPayee, setSelectedPayee] = useState<userService.PayeeInfo | null>(null);
  const [amount, setAmount] = useState(0);

  const handleSearch = async (keyword: string) => {
    setPayeeKeyword(keyword);
    if (keyword.length < 2) {
      setPayeeCandidates([]);
      return;
    }

    try {
      const result = await userService.searchUsers(keyword);
      setPayeeCandidates(result.items || []);
    } catch (error) {
      console.error('搜索失败', error);
    }
  };

  const handleSelectPayee = (payee: userService.PayeeInfo) => {
    setSelectedPayee(payee);
    setPayeeCandidates([]);
    setPayeeKeyword(payee.nickname);
  };

  const handleSubmit = async (values: any) => {
    if (!selectedPayee) {
      Toast.show({ content: '请选择收款人', icon: 'fail' });
      return;
    }

    if (amount < 0.01 || amount > 50000) {
      Toast.show({ content: '金额范围0.01-50000元', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      const draft = await transferService.createDraft({
        payeeUserId: selectedPayee.userId,
        amountFen: Math.round(amount * 100),
        remark: values.remark,
      });

      history.push(`/h5/transfer/confirm?draftId=${draft.draftId}`);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="transfer-page">
      <div className="transfer-form">
        <Form
          layout="vertical"
          onFinish={handleSubmit}
          footer={
            <Button
              block
              type="submit"
              color="primary"
              size="large"
              loading={loading}
            >
              下一步
            </Button>
          }
        >
          <Form.Item label="收款人" required>
            <SearchBar
              placeholder="输入姓名或昵称搜索"
              value={payeeKeyword}
              onChange={handleSearch}
            />
            {payeeCandidates.length > 0 && (
              <div className="payee-candidates">
                {payeeCandidates.map((payee) => (
                  <div
                    key={payee.userId}
                    className="payee-item"
                    onClick={() => handleSelectPayee(payee)}
                  >
                    <span className="payee-name">{payee.nickname}</span>
                    <span className="payee-account">{payee.loginNameMasked}</span>
                  </div>
                ))}
              </div>
            )}
            {selectedPayee && (
              <div className="selected-payee">
                已选择：{selectedPayee.nickname}
              </div>
            )}
          </Form.Item>

          <Form.Item label="转账金额" required>
            <AmountInput
              value={amount}
              onChange={setAmount}
              placeholder="请输入转账金额"
            />
          </Form.Item>

          <Form.Item name="remark" label="备注">
            <Input placeholder="选填，不超过50字" maxLength={50} />
          </Form.Item>
        </Form>
      </div>
    </div>
  );
};

export default TransferPage;
