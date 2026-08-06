import { useAccess } from '@umijs/max';
import PermissionGuard from './PermissionGuard';

/**
 * 演示任务触发页面守卫。
 *
 * 演示任务会触发出账、到期检查等受审计动作，涉及资金应用边界，
 * 因此仅开放给管理员（canRunDemoTasks），运营人员与观察者不可见。
 */
export default function DemoTasksGuard() {
  const access = useAccess();

  return <PermissionGuard allowed={access.canRunDemoTasks} />;
}
