import { history } from '@umijs/max';
import { Button, Result } from 'antd';

export default function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="请检查访问地址或从左侧导航重新选择功能。"
      extra={
        <Button type="primary" onClick={() => history.push('/admin/dashboard')}>
          返回看板
        </Button>
      }
    />
  );
}
