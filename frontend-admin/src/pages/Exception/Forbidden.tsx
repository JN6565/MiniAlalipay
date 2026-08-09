import { HomeOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Result } from 'antd';

/**
 * 403 无权访问页。
 *
 * 由 PermissionGuard 在身份不具备目标页面权限时统一跳转至此。
 * 提供返回看板入口，避免无权限运营停留在无导航的空白路由。
 */
export default function Forbidden() {
  return (
    <Result
      status="403"
      title="无权访问"
      subTitle="当前身份没有该运营页面的访问权限。"
      extra={
        <Button type="primary" icon={<HomeOutlined />} onClick={() => history.push('/admin/dashboard')}>
          返回看板
        </Button>
      }
    />
  );
}
