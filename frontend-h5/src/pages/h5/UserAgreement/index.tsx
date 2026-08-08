import React from 'react';
import './index.less';

const UserAgreementPage: React.FC = () => {
  return (
    <div className="agreement-page">
      <div className="agreement-header">
        <h1>用户协议</h1>
        <div className="update-time">更新时间：2026年8月8日</div>
      </div>

      <div className="agreement-content">
        <div className="section">
          <h2>一、服务条款的确认</h2>
          <p>
            欢迎使用 MiniAlalipay（以下简称"本平台"）。在使用本平台提供的各项服务之前，请您仔细阅读以下协议条款。
            使用本平台服务即表示您同意并遵守本协议的所有条款和条件。
          </p>
        </div>

        <div className="section">
          <h2>二、服务内容</h2>
          <p>本平台提供以下服务：</p>
          <ul>
            <li>虚拟资金管理与转账服务</li>
            <li>收款与付款服务</li>
            <li>信用服务（Mini 花呗）</li>
            <li>AI 智能助手服务</li>
            <li>好友系统与联系人管理</li>
          </ul>
        </div>

        <div className="section">
          <h2>三、用户注册与账户</h2>
          <p>1. 用户需提供真实、准确的注册信息，包括手机号、真实姓名等。</p>
          <p>2. 用户应妥善保管账户信息和密码，因用户原因导致的账户安全问题由用户自行承担。</p>
          <p>3. 用户不得将账户转让、出借给他人使用。</p>
        </div>

        <div className="section">
          <h2>四、用户行为规范</h2>
          <p>用户在使用本平台时，不得进行以下行为：</p>
          <ul>
            <li>违反法律法规的行为</li>
            <li>欺诈、洗钱等违法行为</li>
            <li>侵犯他人合法权益的行为</li>
            <li>干扰平台正常运行的行为</li>
            <li>其他违反本协议的行为</li>
          </ul>
        </div>

        <div className="section">
          <h2>五、知识产权</h2>
          <p>
            本平台的所有内容，包括但不限于文字、图片、音频、视频、软件、程序代码、界面设计等，
            均受知识产权法律法规保护。未经本平台书面许可，用户不得以任何方式复制、修改、传播上述内容。
          </p>
        </div>

        <div className="section">
          <h2>六、免责声明</h2>
          <p>1. 本平台为虚拟资金管理平台，不涉及真实货币交易。</p>
          <p>2. 因系统维护、升级等原因导致的服务中断，本平台不承担责任。</p>
          <p>3. 因用户自身原因导致的损失，本平台不承担责任。</p>
        </div>

        <div className="section">
          <h2>七、协议修改</h2>
          <p>
            本平台有权根据需要修改本协议条款。修改后的协议将在平台上公布，
            用户继续使用本平台服务即视为同意修改后的协议。
          </p>
        </div>

        <div className="section">
          <h2>八、争议解决</h2>
          <p>
            因本协议引起的或与本协议有关的任何争议，双方应友好协商解决。
            协商不成的，任何一方均可向本平台所在地人民法院提起诉讼。
          </p>
        </div>
      </div>
    </div>
  );
};

export default UserAgreementPage;
