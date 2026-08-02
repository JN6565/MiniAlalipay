import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Form, Input, Typography } from 'antd';
import styles from './index.less';

export default function Login() {
  return (
    <main className={styles.page}>
      <section className={styles.loginPanel} aria-labelledby="admin-login-title">
        <div className={styles.brandMark}>M</div>
        <Typography.Title id="admin-login-title" level={2}>
          MiniAlalipay 运营中心
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          仅允许运营人员、系统管理员和只读观察者访问。
        </Typography.Paragraph>
        <Form layout="vertical" disabled>
          <Form.Item label="运营账号" name="username">
            <Input prefix={<UserOutlined />} placeholder="身份接口待接入" />
          </Form.Item>
          <Form.Item label="登录密码" name="password">
            <Input.Password prefix={<LockOutlined />} placeholder="登录接口待接入" />
          </Form.Item>
          <Button block type="primary" icon={<SafetyCertificateOutlined />} htmlType="submit">
            登录
          </Button>
        </Form>
        <Typography.Text className={styles.notice} type="secondary">
          当前 OpenAPI 尚未定义 B 端登录操作，页面不会保存或提交凭据。
        </Typography.Text>
      </section>
    </main>
  );
}
