import React, { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { Toast } from 'antd-mobile';
import { bindIdentity, getIdentity, IdentityInfo } from '@/services/identity';
import { ApiError } from '@/services/request';
import { maskRealName, validateIdCard } from '@/services/utils';
import { IconSet, Skeleton } from '@/components/h5/common';
import './index.less';

/**
 * 身份绑定页：
 * - 未绑定：填写真实姓名 + 身份证号完成绑定
 * - 已绑定：认证状态头 + 脱敏只读展示，提供「更新身份信息」入口
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
        <Skeleton variant="card" height={180} />
      </div>
    );
  }

  return (
    <div className="identity-bind-page">
      {viewMode && identity ? (
        <>
          {/* 认证状态头：绿色盾牌徽章 */}
          <div className="ib-status-head">
            <div className="ib-status-icon">
              <IconSet name="shield" size={20} color="#fff" />
            </div>
            <div className="ib-status-meta">
              <div className="ib-status-title">已完成实名认证</div>
              <div className="ib-status-sub">实名信息已通过三要素校验</div>
            </div>
            <span className="ib-status-pill">已认证</span>
          </div>

          {/* 证件信息卡：只读掩码展示 */}
          <div className="ib-card">
            <div className="ib-row">
              <span className="ib-row-label">真实姓名</span>
              <span className="ib-row-value">{maskRealName(identity.realName || '')}</span>
            </div>
            <div className="ib-row">
              <span className="ib-row-label">证件类型</span>
              <span className="ib-row-value">居民身份证</span>
            </div>
            <div className="ib-row last">
              <span className="ib-row-label">证件号码</span>
              <span className="ib-row-value">{identity.idCardMasked || '****'}</span>
            </div>
          </div>

          <div className="ib-tip">
            实名信息提交后不可自助随意修改，身份信息用于银行卡绑定时的三要素交叉校验。平台只保存身份证号哈希值，不保存明文。
          </div>

          <div className="ib-submit" onClick={handleStartUpdate}>
            更新身份信息
          </div>
        </>
      ) : (
        <>
          {/* 未认证/编辑态：表单引导 */}
          <div className="ib-card">
            <div className="ib-field">
              <div className="ib-field-label">真实姓名</div>
              <div className="ib-field-box">
                <input
                  className="ib-field-input"
                  placeholder="请输入真实姓名"
                  value={realName}
                  maxLength={32}
                  onChange={(e) => setRealName(e.target.value)}
                />
              </div>
            </div>
            <div className="ib-field">
              <div className="ib-field-label">身份证号</div>
              <div className="ib-field-box">
                <input
                  className="ib-field-input"
                  placeholder="请输入 18 位身份证号"
                  value={idCard}
                  maxLength={18}
                  onChange={(e) => setIdCard(e.target.value)}
                />
              </div>
              {idCardHint && (
                <div className={`ib-field-hint ib-hint-${idCardHint.type}`}>{idCardHint.text}</div>
              )}
            </div>
          </div>

          <div className="ib-tip">
            身份信息用于银行卡绑定时的三要素交叉校验。平台只保存身份证号哈希值，不保存明文。
          </div>

          <div
            className={`ib-submit ${submitting ? 'disabled' : ''}`}
            onClick={() => { if (!submitting) handleSubmit(); }}
          >
            {submitting ? '提交中...' : bound ? '保存更新' : '绑定身份'}
          </div>
          {bound && (
            <div className="ib-cancel" onClick={() => setViewMode(true)}>
              取消
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default IdentityBindPage;
