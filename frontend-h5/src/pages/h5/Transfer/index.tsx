// @ts-nocheck
import React, { useState, useEffect } from 'react';
import { history, useLocation } from 'umi';
import { Form, Input, Button, Toast, SearchBar, Avatar, List, Divider } from 'antd-mobile';
import { UserOutline, DownOutline } from 'antd-mobile-icons';
import * as userService from '@/services/user';
import * as transferService from '@/services/transfer';
import { AmountInput } from '@/components/h5/AmountInput';
import './index.less';

const TransferPage: React.FC = () => {
  const location = useLocation();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [payeeKeyword, setPayeeKeyword] = useState('');
  const [payeeCandidates, setPayeeCandidates] = useState<userService.PayeeInfo[]>([]);
  const [selectedPayee, setSelectedPayee] = useState<userService.PayeeInfo | null>(null);
  const [amount, setAmount] = useState(0);
  const [contacts, setContacts] = useState<userService.Contact[]>([]);
  const [contactsLoading, setContactsLoading] = useState(false);
  const [showRecentTransfers, setShowRecentTransfers] = useState(true);
  const [recentExpanded, setRecentExpanded] = useState(false);

  // 收款人展示名：搜索结果优先展示服务端脱敏后的真实姓名（用于收款确认），未绑定身份时降级为昵称；
  // 姓名与手机号脱敏均在服务端完成，前端不再重复脱敏
  const payeeDisplayName = (payee: userService.PayeeInfo) => payee.maskedRealName || payee.nickname;

  // 收款人手机号展示：优先后端脱敏手机号（如 138****9150），缺失时降级手机尾号；
  // 联系人/URL 带入的场景两者都缺失，才降级展示账户号尾号
  const payeePhoneDisplay = (payee: userService.PayeeInfo) => {
    if (payee.maskedPhone) return payee.maskedPhone;
    if (payee.phoneTail) return `尾号 ${payee.phoneTail}`;
    return payee.accountNumber ? `尾号 ${payee.accountNumber.slice(-4)}` : '';
  };

  // 加载常用联系人（最近转账）
  useEffect(() => {
    const loadContacts = async () => {
      setContactsLoading(true);
      try {
        const result = await userService.getContacts(5); // 最多5条
        setContacts(result || []);
      } catch (error) {
      } finally {
        setContactsLoading(false);
      }
    };
    loadContacts();
  }, []);

  // 从 URL 参数中读取收款人信息，自动填充
  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const payeeUserId = searchParams.get('payeeUserId');
    const payeeName = searchParams.get('payeeName');
    const accountNumber = searchParams.get('accountNumber');
    if (payeeUserId && accountNumber) {
      setSelectedPayee({
        userId: payeeUserId,
        nickname: payeeName || '用户',
        accountNumber: accountNumber,
      });
      setPayeeKeyword(payeeName || '用户');
      setShowRecentTransfers(false);
    }
  }, [location.search]);

  // 手机号格式校验：仅支持 11 位手机号精确搜索收款人
  const isValidPayeePhone = (value: string) => /^1\d{10}$/.test(value.trim());

  // 非实时搜索：点击查询按钮触发
  const handleSearch = async () => {
    if (!isValidPayeePhone(payeeKeyword)) {
      Toast.show({ content: '请输入正确的11位手机号', icon: 'fail' });
      return;
    }

    try {
      const result = await userService.searchUsers(payeeKeyword);
      setPayeeCandidates(Array.isArray(result) ? result : []);
      setShowRecentTransfers(false); // 搜索时隐藏最近转账列表
      if (!Array.isArray(result) || result.length === 0) {
        Toast.show({ content: '未找到匹配的用户', icon: 'fail' });
      }
    } catch (error: any) {
      Toast.show({ content: error.message || '搜索失败', icon: 'fail' });
    }
  };

  const handleSelectPayee = (payee: userService.PayeeInfo) => {
    setSelectedPayee(payee);
    setPayeeCandidates([]);
    setPayeeKeyword(payeeDisplayName(payee));
  };

  const handleSelectContact = (contact: userService.Contact) => {
    // 从联系人中选择收款人，直接使用联系人中的信息
    setSelectedPayee({
      userId: contact.payeeUserId,
      nickname: contact.payeeName || contact.alias || '用户',
      accountNumber: contact.accountNumber || '',
    });
    setPayeeKeyword(contact.payeeName || contact.alias || '用户');
    setShowRecentTransfers(false);
  };

  const handleSubmit = async () => {
    if (!selectedPayee) {
      Toast.show({ content: '请选择收款人', icon: 'fail' });
      return;
    }

    if (amount < 0.01 || amount > 50000) {
      Toast.show({ content: '金额范围 0.01-50000 元', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      const values = form.getFieldsValue();
      const draft = await transferService.createDraft({
        payeeUserId: selectedPayee.userId,
        amountFen: Math.round(amount * 100),
        remark: values.remark || '',
      });

      // 后端草稿接口仅返回 payeeUserId，收款人昵称和账号通过路由 state 携带给确认页展示；
      // 确认令牌、支付密码等敏感信息不得进入 URL，这里仅传展示用的公开信息
      history.push(`/h5/transfer/confirm?draftId=${draft.draftId}`, {
        payeeNickname: selectedPayee.nickname,
        payeeAccountNumber: selectedPayee.accountNumber,
      });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '创建失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="transfer-page">
      {/* 页面标题 */}
      <div className="page-header">
        <h2 className="page-title">转账</h2>
      </div>

      {/* 收款人搜索区域 */}
      <div className="payee-section">
        <div className="section-label">收款人</div>
        <SearchBar
          placeholder="请输入手机号搜索"
          value={payeeKeyword}
          onChange={(val) => {
            setPayeeKeyword(val);
            if (!val) {
              setShowRecentTransfers(true); // 清空搜索时显示最近转账
              setPayeeCandidates([]);
            }
          }}
          onSearch={handleSearch}
          action={
            isValidPayeePhone(payeeKeyword) && (
              <Button size="small" color="primary" onClick={handleSearch}>
                查询
              </Button>
            )
          }
        />
      </div>

      {/* 最近转账列表（未搜索时显示，可折叠）*/}
      {showRecentTransfers && contacts.length > 0 && !selectedPayee && (
        <div className="recent-transfers-section">
          <div className="section-header" onClick={() => setRecentExpanded(!recentExpanded)}>
            <span className="section-title">最近转账</span>
            <span className={`section-arrow ${recentExpanded ? 'expanded' : ''}`}>
              <DownOutline />
            </span>
          </div>
          {recentExpanded && (
            <List className="contacts-list">
              {contacts.map((contact) => (
                <List.Item
                  key={contact.payeeUserId}
                  prefix={
                    <Avatar style={{ '--size': '36px', '--border-radius': '50%', background: '#1677ff' }}>
                      {contact.payeeName?.charAt(0) || '?'}
                    </Avatar>
                  }
                  description={contact.maskedPhone || (contact.phoneTail ? `尾号 ${contact.phoneTail}` : `尾号 ${(contact.accountNumber || '').slice(-4)}`)}
                  extra={<Button size="mini" fill="none" onClick={() => handleSelectContact(contact)}>选择</Button>}
                  onClick={() => handleSelectContact(contact)}
                >
                  {contact.alias || contact.payeeName}
                </List.Item>
              ))}
            </List>
          )}
        </div>
      )}

      {/* 搜索结果列表 */}
      {payeeCandidates.length > 0 && (
        <div className="search-results-section">
          <div className="section-header">
            <span className="section-title">搜索结果</span>
          </div>
          <List className="payee-candidates">
            {payeeCandidates.map((payee) => (
              <List.Item
                key={payee.userId}
                prefix={
                  <Avatar style={{ '--size': '36px', '--border-radius': '50%', background: '#f0f0f0' }}>
                    <UserOutline />
                  </Avatar>
                }
                description={payeePhoneDisplay(payee)}
                onClick={() => handleSelectPayee(payee)}
                arrow
              >
                {payeeDisplayName(payee)}
              </List.Item>
            ))}
          </List>
        </div>
      )}

      {/* 已选收款人卡片 */}
      {selectedPayee && (
        <div className="selected-payee-card">
          <Avatar style={{ '--size': '40px', '--border-radius': '50%', background: '#1677ff' }}>
            <UserOutline />
          </Avatar>
          <div className="payee-info">
            <div className="payee-name">{payeeDisplayName(selectedPayee)}</div>
            <div className="payee-account">{payeePhoneDisplay(selectedPayee)}</div>
          </div>
          <Button size="small" fill="none" onClick={() => setSelectedPayee(null)}>
            更换
          </Button>
        </div>
      )}

      <Divider />

      {/* 金额输入区域 */}
      <div className="amount-section">
        <div className="section-label">金额</div>
        <div className="amount-input-wrapper">
          <AmountInput
            value={amount}
            onChange={setAmount}
            placeholder="0.00"
          />
        </div>
        {amount > 0 && (amount < 0.01 || amount > 50000) && (
          <div className="amount-warning">
            ️ 金额范围 0.01-50000 元
          </div>
        )}
      </div>

      {/* 备注输入 */}
      <div className="remark-section">
        <div className="section-label">备注（选填）</div>
        <Form form={form} initialValues={{ remark: '' }}>
          <Form.Item name="remark" noStyle>
            <Input 
              placeholder="请输入备注" 
              maxLength={128}
              showCount
            />
          </Form.Item>
        </Form>
      </div>

      {/* 提交按钮 */}
      <div className="submit-section">
        <Button
          block
          color="primary"
          size="large"
          loading={loading}
          onClick={handleSubmit}
          disabled={!selectedPayee || amount <= 0}
          className="submit-btn"
        >
          确认转账
        </Button>
      </div>
    </div>
  );
};

export default TransferPage;
