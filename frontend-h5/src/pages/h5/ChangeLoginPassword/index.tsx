import React, { useState } from 'react';
import { Form, Input, Button, Toast } from 'antd-mobile';
import * as authService from '@/services/auth';
import { ApiError, clearSession } from '@/services/request';
import { history } from '@umijs/max';
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
      // 密码修改成功后旧会话必须立即废弃，避免重新登录前继续携带旧令牌。
      clearSession();
      Toast.show({ icon: 'success', content: '密码修改成功，请重新登录' });
      history.replace('/h5/login');
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        CURRENT_LOGIN_PASSWORD_INVALID: '当前登录密码错误',
        PASSWORD_REUSE_NOT_ALLOWED: '新密码不能与当前密码相同',
        PASSWORD_POLICY_VIOLATION: '新密码必须为8-32位，并包含大小写字母和数字',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ icon: 'fail', content: messages[code] || error.message || '修改失败' });
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
              { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/, message: '必须包含大写字母、小写字母和数字' },
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
