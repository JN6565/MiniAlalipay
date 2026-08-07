import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import { List, Avatar, Button, Toast, SwipeAction, Modal, Input, SpinLoading, Empty } from 'antd-mobile';
import * as userService from '@/services/user';
import './index.less';

const ContactsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [contacts, setContacts] = useState<userService.Contact[]>([]);
  const [aliasModalVisible, setAliasModalVisible] = useState(false);
  const [editingContact, setEditingContact] = useState<userService.Contact | null>(null);
  const [aliasValue, setAliasValue] = useState('');

  useEffect(() => {
    loadContacts();
  }, []);

  const loadContacts = async () => {
    setLoading(true);
    try {
      const result = await userService.getContacts(20);
      setContacts(result || []);
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '加载失败' });
    } finally {
      setLoading(false);
    }
  };

  const handleTogglePin = async (contact: userService.Contact) => {
    try {
      await userService.updateContact(contact.payeeUserId, {
        pinned: !contact.pinned,
      });
      setContacts((prev) =>
        prev.map((c) =>
          c.payeeUserId === contact.payeeUserId ? { ...c, pinned: !c.pinned } : c
        )
      );
      Toast.show({ content: contact.pinned ? '已取消置顶' : '已置顶' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '操作失败' });
    }
  };

  const handleHide = async (contact: userService.Contact) => {
    const result = await Modal.confirm({
      content: '确定隐藏该联系人？隐藏后不再显示在列表中',
    });
    if (result) {
      try {
        await userService.updateContact(contact.payeeUserId, { hidden: true });
        setContacts((prev) => prev.filter((c) => c.payeeUserId !== contact.payeeUserId));
        Toast.show({ content: '已隐藏' });
      } catch (error: any) {
        Toast.show({ icon: 'fail', content: error.message || '操作失败' });
      }
    }
  };

  const handleEditAlias = (contact: userService.Contact) => {
    setEditingContact(contact);
    setAliasValue(contact.alias || '');
    setAliasModalVisible(true);
  };

  const handleSaveAlias = async () => {
    if (!editingContact) return;
    try {
      await userService.updateContact(editingContact.payeeUserId, {
        alias: aliasValue || undefined,
      });
      setContacts((prev) =>
        prev.map((c) =>
          c.payeeUserId === editingContact.payeeUserId ? { ...c, alias: aliasValue || undefined } : c
        )
      );
      setAliasModalVisible(false);
      Toast.show({ content: '已保存' });
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '保存失败' });
    }
  };

  const handleContactClick = (contact: userService.Contact) => {
    // 跳转到转账页面，通过 URL 参数传递收款人
    history.push(`/h5/transfer?payeeUserId=${contact.payeeUserId}`);
  };

  if (loading) {
    return (
      <div className="loading-container">
        <SpinLoading color="primary" />
      </div>
    );
  }

  return (
    <div className="contacts-page">
      <div className="contacts-header">
        <h3>常用联系人</h3>
      </div>

      {contacts.length === 0 ? (
        <Empty
          description="暂无常用联系人，转账后自动添加"
          style={{ padding: '40px 0' }}
        />
      ) : (
        <List className="contacts-list">
          {contacts.map((contact) => (
            <SwipeAction
              key={contact.payeeUserId}
              rightActions={[
                {
                  key: 'pin',
                  text: contact.pinned ? '取消置顶' : '置顶',
                  color: 'primary',
                  onClick: () => handleTogglePin(contact),
                },
                {
                  key: 'alias',
                  text: '备注',
                  color: 'warning',
                  onClick: () => handleEditAlias(contact),
                },
                {
                  key: 'hide',
                  text: '隐藏',
                  color: 'danger',
                  onClick: () => handleHide(contact),
                },
              ]}
            >
              <List.Item
                prefix={
                  <Avatar
                    style={{ '--size': '40px', '--border-radius': '50%' }}
                    src={undefined}
                  />
                }
                description={
                  <div className="contact-meta">
                    <span>成功转账 {contact.successCount} 次</span>
                    {contact.pinned && <span className="pinned-tag">置顶</span>}
                  </div>
                }
                onClick={() => handleContactClick(contact)}
              >
                <div className="contact-name">
                  {contact.alias || contact.payeeNickname || contact.payeeUserId.slice(0, 8)}
                </div>
              </List.Item>
            </SwipeAction>
          ))}
        </List>
      )}

      {/* 备注别名弹窗 */}
      <Modal
        visible={aliasModalVisible}
        title="设置备注名"
        content={
          <Input
            placeholder="输入备注名"
            value={aliasValue}
            onChange={setAliasValue}
            maxLength={64}
          />
        }
        actions={[
          {
            key: 'cancel',
            text: '取消',
            onClick: () => setAliasModalVisible(false),
          },
          {
            key: 'save',
            text: '保存',
            bold: true,
            onClick: handleSaveAlias,
          },
        ]}
      />

      {/* 底部导航栏 */}
      <div className="tabbar">
        <div className="tab" onClick={() => history.push('/h5/home')}>
          <span className="tab-icon">🏠</span>
          <span className="tab-label">首页</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/ai-talk')}>
          <span className="tab-icon">💬</span>
          <span className="tab-label">AI助手</span>
        </div>
        <div className="tab on">
          <span className="tab-icon">👥</span>
          <span className="tab-label">联系人</span>
        </div>
        <div className="tab" onClick={() => history.push('/h5/profile')}>
          <span className="tab-icon">👤</span>
          <span className="tab-label">我的</span>
        </div>
      </div>
    </div>
  );
};

export default ContactsPage;
