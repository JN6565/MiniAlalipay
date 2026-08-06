import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Form, Input, Typography } from 'antd';
import styles from './index.less';

/**
 * 运营中心登录页。
 *
 * 安全约束：B 端运营登录依赖 user-center（负责人闫泽华）提供的身份契约，OpenAPI 尚未定义该操作，
 * 因此整个表单处于 disabled 状态，不提交、不保存任何凭据，避免在无契约时伪造登录流程或在前端驻留账号密码。
 * 身份契约合入后，凭据只能通过网关提交，且密码不得写入日志、浏览器存储或 URL；当前本地演示由网关 dev Stub
 * 提供可控身份，见系统分析 16.8 的 B 端身份边界。
 */
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
        {/* 表单整体 disabled：身份接口未接入前禁止提交，placeholder 明确标注待接入状态。 */}
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
          运营身份接口待 user-center 合入（系统分析 16.8），当前页面不提交或保存凭据，本地演示由网关 dev Stub 提供身份。
        </Typography.Text>
      </section>
    </main>
  );
}
