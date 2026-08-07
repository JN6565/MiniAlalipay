import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Form, Input, Typography } from 'antd';
import { history, useModel } from '@umijs/max';
import { useState } from 'react';
import styles from './index.less';
import { adminLogin } from '@/services/auth';
import { rememberToken } from '@/utils/adminToken';

/**
 * 运营中心登录页。
 *
 * 安全约束：B 端运营登录依赖 user-center 的身份契约（系统分析 16.8），凭据只能通过网关提交，
 * 密码不得写入日志、浏览器存储或 URL；令牌在登录成功后持久化，供请求层注入 Authorization。
 * 本地演示未配置登录口令时，网关 dev Stub 提供受控身份。
 */

interface LoginFormValues {
  loginIdentifier: string;
  loginPassword: string;
}

export default function Login() {
  const { refresh } = useModel('@@initialState');
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: LoginFormValues) => {
    setSubmitting(true);
    try {
      const result = await adminLogin(values.loginIdentifier, values.loginPassword);
      // 令牌持久化到 localStorage，请求层自动注入 Authorization；密码不驻留任何客户端存储。
      rememberToken(result.data.accessToken);
      message.success('登录成功');
      // 重新加载初始身份（/api/v1/auth/me）后再回到看板。
      await refresh();
      history.replace('/admin/dashboard');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className={styles.page}>
      <section className={styles.loginPanel} aria-labelledby="admin-login-title">
        <div className={styles.brandMark}>M</div>
        <Typography.Title id="admin-login-title" level={2}>
          MiniAlalipay 运营中心
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          仅允许运营人员与系统管理员访问。
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item
            label="运营账号"
            name="loginIdentifier"
            rules={[{ required: true, message: '请输入运营账号' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="手机号或登录名" autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="登录密码"
            name="loginPassword"
            rules={[{ required: true, message: '请输入登录密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="登录密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Button
            block
            type="primary"
            icon={<SafetyCertificateOutlined />}
            htmlType="submit"
            loading={submitting}
          >
            登录
          </Button>
        </Form>
        <Typography.Text className={styles.notice} type="secondary">
          凭据仅通过网关提交，不写入日志或 URL；本地演示未配置登录口令时，网关 dev Stub 提供受控身份（系统分析 16.8）。
        </Typography.Text>
      </section>
    </main>
  );
}
