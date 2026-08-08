import React from 'react';
import './index.less';

const PrivacyPolicyPage: React.FC = () => {
  return (
    <div className="privacy-page">
      <div className="privacy-header">
        <h1>隐私政策</h1>
        <div className="update-time">更新时间：2026年8月8日</div>
      </div>

      <div className="privacy-content">
        <div className="section">
          <h2>一、信息收集</h2>
          <p>我们收集以下信息以提供服务：</p>
          <ul>
            <li><strong>基本信息：</strong>手机号、真实姓名、昵称</li>
            <li><strong>账户信息：</strong>登录密码、支付密码（加密存储）</li>
            <li><strong>交易信息：</strong>转账记录、收款记录、充值记录</li>
            <li><strong>设备信息：</strong>设备型号、操作系统版本</li>
          </ul>
        </div>

        <div className="section">
          <h2>二、信息使用</h2>
          <p>我们使用收集的信息用于：</p>
          <ul>
            <li>提供、维护和改进服务</li>
            <li>处理交易和发送通知</li>
            <li>验证用户身份和保障账户安全</li>
            <li>提供客户支持</li>
            <li>防止欺诈和违规行为</li>
          </ul>
        </div>

        <div className="section">
          <h2>三、信息保护</h2>
          <p>我们采取以下措施保护您的信息：</p>
          <ul>
            <li>使用加密技术存储敏感信息（如密码、手机号）</li>
            <li>实施严格的访问控制和权限管理</li>
            <li>定期进行安全审计和漏洞扫描</li>
            <li>对员工进行安全培训</li>
          </ul>
        </div>

        <div className="section">
          <h2>四、信息共享</h2>
          <p>未经您的同意，我们不会向第三方共享您的个人信息，但以下情况除外：</p>
          <ul>
            <li>根据法律法规要求</li>
            <li>根据政府机关的合法要求</li>
            <li>为保护我们或公众的合法权益</li>
          </ul>
        </div>

        <div className="section">
          <h2>五、用户权利</h2>
          <p>您享有以下权利：</p>
          <ul>
            <li>访问和查看您的个人信息</li>
            <li>更正不准确的信息</li>
            <li>删除您的账户和相关信息</li>
            <li>撤回您的同意</li>
            <li>获取您的个人信息副本</li>
          </ul>
        </div>

        <div className="section">
          <h2>六、Cookie 和本地存储</h2>
          <p>
            我们使用本地存储（localStorage）来保存您的登录状态和偏好设置。
            这些信息仅存储在您的设备上，不会上传到我们的服务器。
          </p>
        </div>

        <div className="section">
          <h2>七、未成年人保护</h2>
          <p>
            我们非常重视对未成年人个人信息的保护。如果您是未满18周岁的未成年人，
            请在法定监护人的陪同下阅读本政策，并在获得法定监护人的同意后使用我们的服务。
          </p>
        </div>

        <div className="section">
          <h2>八、政策更新</h2>
          <p>
            我们可能会不时更新本隐私政策。更新后的政策将在平台上公布，
            继续使用我们的服务即表示您同意更新后的政策。
          </p>
        </div>

        <div className="section">
          <h2>九、联系我们</h2>
          <p>
            如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们：
          </p>
          <div className="contact-info">
            <p>邮箱：xxx@xxx.com</p>
            <p>电话：400-XXX-XXXX</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PrivacyPolicyPage;
