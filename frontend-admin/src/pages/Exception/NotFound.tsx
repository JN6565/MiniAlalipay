import { HomeOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Result } from 'antd';

/**
 * 404 页面不存在页。
 *
 * 由路由通配符匹配兜底渲染，覆盖未定义路径，避免进入空白页。
 * 提供返回看板入口，引导运营回到可信运行看板。
 */
export default function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="请检查访问地址或从左侧导航重新选择功能。"
      extra={
        <Button type="primary" icon={<HomeOutlined />} onClick={() => history.push('/admin/dashboard')}>
          返回看板
        </Button>
      }
    />
  );
}
