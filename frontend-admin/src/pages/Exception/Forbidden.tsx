import { history } from '@umijs/max';
import { Button, Result } from 'antd';

export default function Forbidden() {
  return (
    <Result
      status="403"
      title="无权访问"
      subTitle="当前身份没有该运营页面的访问权限。"
      extra={
        <Button type="primary" onClick={() => history.push('/admin/dashboard')}>
          返回看板
        </Button>
      }
    />
  );
}
