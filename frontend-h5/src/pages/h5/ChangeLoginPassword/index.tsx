import React, { useState } from 'react';
import { history } from 'umi';
import { Form, Input, Button, Toast } from 'antd-mobile';
import * as authService from '@/services/auth';
import './index.less';

const ChangeLoginPasswordPage: React.FC = () => {
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: any) => {
    if (values.newPassword !== values.confirmPassword) {
      Toast.show({ content: '两次密码不一致', icon: 'fail' });
      return;
    }

    setLoading(true);
    try {
      await authService.changeLoginPassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      Toast.show({ icon: 'success', content: '密码修改成功，请重新登录' });
      history.push('/h5/login');
    } catch (error: any) {
      Toast.show({ icon: 'fail', content: error.message || '修改失败' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="change-password-page">
      <div className="change-password-form">
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
              确认修改
            </Button>
          }
        >
          <Form.Item
            name="currentPassword"
            label="当前密码"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input type="password" placeholder="请输入当前登录密码" />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 8, max: 32, message: '密码长度为8-32位' },
            ]}
          >
            <Input type="password" placeholder="请输入新密码" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            rules={[{ required: true, message: '请再次输入新密码' }]}
          >
            <Input type="password" placeholder="请再次输入新密码" />
          </Form.Item>
        </Form>
      </div>
    </div>
  );
};

export default ChangeLoginPasswordPage;
