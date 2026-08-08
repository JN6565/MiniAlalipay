import React, { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { Button, Input, Toast } from 'antd-mobile';
import { bindIdentity, getIdentity, IdentityInfo } from '@/services/identity';
import { ApiError } from '@/services/request';
import { maskRealName, validateIdCard } from '@/services/utils';
import './index.less';

/**
 * 身份绑定页：
 * - 未绑定：填写真实姓名 + 身份证号完成绑定
 * - 已绑定：脱敏只读展示姓名和身份证号，提供「更新身份信息」入口
 * 绑定/更新成功后停留在查看态展示最新脱敏信息。
 */
const IdentityBindPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  /** 已绑定且未进入编辑时为 true，展示脱敏只读信息。 */
  const [viewMode, setViewMode] = useState(false);
  /** 已绑定用户进入编辑态时为 true，用于区分「绑定身份」和「保存更新」。 */
  const [bound, setBound] = useState(false);
  const [identity, setIdentity] = useState<IdentityInfo | null>(null);
  const [realName, setRealName] = useState('');
  const [idCard, setIdCard] = useState('');
  const [submitting, setSubmitting] = useState(false);

  /** 拉取当前身份绑定状态，决定进入查看态还是编辑态。 */
  const loadIdentity = useCallback(async () => {
    try {
      const info = await getIdentity();
      setIdentity(info);
      const verified = info.identityStatus === 'VERIFIED';
      setBound(verified);
      setViewMode(verified);
    } catch {
      // 查询失败按未绑定处理，允许用户继续填写绑定
      setBound(false);
      setViewMode(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadIdentity();
  }, [loadIdentity]);

  const handleSubmit = async () => {
    if (!realName.trim()) {
      Toast.show({ icon: 'fail', content: '请输入真实姓名' });
      return;
    }
    if (realName.trim().length < 2 || realName.trim().length > 32) {
      Toast.show({ icon: 'fail', content: '真实姓名长度必须为2-32位' });
      return;
    }
    // 身份证号合规校验：格式、出生日期、MOD 11-2 校验位，不合规给出具体提示
    const idCardError = validateIdCard(idCard);
    if (idCardError) {
      Toast.show({ icon: 'fail', content: idCardError });
      return;
    }

    setSubmitting(true);
    try {
      await bindIdentity({
        realName: realName.trim(),
        idCard: idCard.trim().toUpperCase(),
      });
      const isUpdate = bound;
      Toast.show({ icon: 'success', content: isUpdate ? '身份信息更新成功' : '身份绑定成功' });
      setRealName('');
      setIdCard('');
      if (isUpdate) {
        // 更新场景：重新拉取脱敏信息回到查看态
        await loadIdentity();
      } else {
        history.replace('/h5/profile');
      }
    } catch (error: any) {
      const code = error instanceof ApiError ? error.code : 'UNKNOWN';
      const messages: Record<string, string> = {
        COMMON_INVALID_REQUEST: '身份信息格式不正确',
        ID_CARD_ALREADY_BOUND: '该身份证号已被其他账户绑定',
        NETWORK_ERROR: '网络异常，请检查网络连接',
      };
      Toast.show({ content: messages[code] || error.message || '绑定失败', icon: 'fail' });
    } finally {
      setSubmitting(false);
    }
  };

  /** 已绑定用户点击「更新身份信息」：清空表单进入编辑态。 */
  const handleStartUpdate = () => {
    setRealName('');
    setIdCard('');
    setViewMode(false);
  };

  /**
   * 身份证号输入过程内联提示：渲染时派生计算，不落 state。
   * 非法字符立即报错；未满 18 位显示进度；满 18 位后按 validateIdCard 结果提示。
   */
  const idCardHint: { type: 'error' | 'info' | 'ok'; text: string } | null = (() => {
    const value = idCard.trim();
    if (!value) {
      return null;
    }
    if (/[^\dXx]/.test(value)) {
      return { type: 'error', text: '身份证号只能包含数字，末位可为 X' };
    }
    if (value.length < 18) {
      return { type: 'info', text: `已输入 ${value.length}/18 位` };
    }
    const error = validateIdCard(value);
    if (error) {
      return { type: 'error', text: error };
    }
    return { type: 'ok', text: '身份证号格式正确' };
  })();

  if (loading) {
    return (
      <div className="identity-bind-page">
        <div className="bind-tip">加载中...</div>
      </div>
    );
  }

  return (
    <div className="identity-bind-page">
      {viewMode && identity ? (
        <div className="bind-section">
          <div className="section-label">真实姓名</div>
          <div className="readonly-value">{maskRealName(identity.realName || '')}</div>

          <div className="section-label">身份证号</div>
          <div className="readonly-value">{identity.idCardMasked || '****'}</div>
        </div>
      ) : (
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
          {idCardHint && (
            <div className={`field-hint field-hint-${idCardHint.type}`}>{idCardHint.text}</div>
          )}
        </div>
      )}

      <div className="bind-tip">
        身份信息用于银行卡绑定时的三要素交叉校验，身份信息如需变更可在本页更新。平台只保存身份证号哈希值，不保存明文。
      </div>

      {viewMode ? (
        <Button block color="primary" size="large" onClick={handleStartUpdate}>
          更新身份信息
        </Button>
      ) : (
        <>
          <Button
            block
            color="primary"
            size="large"
            loading={submitting}
            onClick={handleSubmit}
          >
            {bound ? '保存更新' : '绑定身份'}
          </Button>
          {bound && (
            <Button
              block
              size="large"
              style={{ marginTop: 12 }}
              onClick={() => setViewMode(true)}
            >
              取消
            </Button>
          )}
        </>
      )}
    </div>
  );
};

export default IdentityBindPage;
