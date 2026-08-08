import React, { useState } from 'react';
import { history } from '@umijs/max';
import { Button, Input, Toast } from 'antd-mobile';
import { bindIdentity } from '@/services/identity';
import { ApiError } from '@/services/request';
import './index.less';

/** 身份证号格式：17 位数字加 1 位数字或 X/x。 */
const ID_CARD_PATTERN = /^\d{17}[\dXx]$/;

/**
 * 身份绑定页：填写真实姓名 + 身份证号，绑定到用户账户。
 * 绑定成功后跳转到「我的」页面。
 */
const IdentityBindPage: React.FC = () => {
  const [realName, setRealName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async () => {
    if (!realName.trim()) {
      Toast.show({ icon: 'fail', content: '请输入真实姓名' });
      return;
    }
    if (realName.trim().length < 2 || realName.trim().length > 32) {
      Toast.show({ icon: 'fail', content: '真实姓名长度必须为2-32位' });
      return;
    }
    if (!ID_CARD_PATTERN.test(idCard.trim())) {
      Toast.show({ icon: 'fail', content: '身份证号格式不正确' });
      return;
    }

    setSubmitting(true);
    try {
      await bindIdentity({
        realName: realName.trim(),
        idCard: idCard.trim(),
      });
      Toast.show({ icon: 'success', content: '身份绑定成功' });
      history.replace('/h5/profile');
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        COMMON_INVALID_REQUEST: '身份信息格式不正确',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ content: messages[code] || error.message || '绑定失败', icon: 'fail' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="identity-bind-page">
      <div className="bind-section">
        <div className="section-label">真实姓名</div>
        <Input
          placeholder="请输入真实姓名"
          value={realName}
          onChange={setRealName}
          maxLength={32}
          clearable
        />

        <div className="section-label">身份证号</div>
        <Input
          placeholder="请输入 18 位身份证号"
          value={idCard}
          onChange={setIdCard}
          maxLength={18}
          clearable
        />
      </div>

      <div className="bind-tip">
        身份信息用于银行卡绑定时的三要素交叉校验，绑定后不可修改。平台只保存身份证号哈希值，不保存明文。
      </div>

      <Button
        block
        color="primary"
        size="large"
        loading={submitting}
        onClick={handleSubmit}
      >
        绑定身份
      </Button>
    </div>
  );
};

export default IdentityBindPage;
