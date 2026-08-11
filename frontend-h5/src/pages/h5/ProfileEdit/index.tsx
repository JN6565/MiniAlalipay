import React, { useState } from 'react';
import { Button, Input, Toast } from 'antd-mobile';
import { history } from '@umijs/max';
import {
  getProfilePreference,
  getAvatarDisplay,
  PROFILE_AVATARS,
  readAvatarFile,
  saveProfilePreference,
} from '@/utils/profile';
import './index.less';

/**
 * 个人资料编辑页：修改仅作用于 C 端当前浏览器的展示偏好，不修改用户中心事实。
 */
const ProfileEditPage: React.FC = () => {
  const initialPreference = getProfilePreference();
  const [nickname, setNickname] = useState(initialPreference.nickname);
  const [avatarCode, setAvatarCode] = useState(initialPreference.avatarCode);
  const [avatarDataUrl, setAvatarDataUrl] = useState(initialPreference.avatarDataUrl);
  const [saving, setSaving] = useState(false);

  const handleAvatarFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    try {
      setAvatarDataUrl(await readAvatarFile(file));
      setAvatarCode('CUSTOM');
    } catch (error) {
      Toast.show({ icon: 'fail', content: error instanceof Error ? error.message : '头像读取失败' });
    }
  };

  const handleSave = async () => {
    const normalizedNickname = nickname.trim();
    if (normalizedNickname.length < 1 || normalizedNickname.length > 20) {
      Toast.show({ icon: 'fail', content: '昵称长度需为 1-20 个字符' });
      return;
    }

    setSaving(true);
    try {
      saveProfilePreference({ nickname: normalizedNickname, avatarCode, avatarDataUrl });
      Toast.show({ icon: 'success', content: '个人资料已保存' });
      history.replace('/h5/profile');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="profile-edit-page">
      <section className="profile-preview" aria-label="资料预览">
        <div className="preview-avatar">
          {avatarDataUrl ? <img src={avatarDataUrl} alt="头像预览" /> : getAvatarDisplay(avatarCode)}
        </div>
        <div className="preview-name">{nickname.trim() || '用户'}</div>
      </section>

      <section className="profile-edit-section">
        <div className="profile-edit-section-title">头像</div>
        <div className="avatar-options" role="radiogroup" aria-label="选择头像">
          {PROFILE_AVATARS.map((avatar) => (
            <button
              key={avatar.code}
              type="button"
              className={`avatar-option ${avatarCode === avatar.code ? 'selected' : ''}`}
              role="radio"
              aria-label={avatar.label}
              aria-checked={avatarCode === avatar.code}
              onClick={() => {
                setAvatarCode(avatar.code);
                setAvatarDataUrl(undefined);
              }}
            >
              <span aria-hidden="true">{avatar.display}</span>
            </button>
          ))}
        </div>
        <label className="avatar-upload">
          <span>从手机选择图片</span>
          <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleAvatarFileChange} />
        </label>
        <div className="avatar-hint">支持 JPG、PNG、WebP，文件不超过 1 MB，仅保存在当前浏览器</div>
      </section>

      <section className="profile-edit-section">
        <div className="profile-edit-section-title">昵称</div>
        <Input
          className="nickname-input"
          value={nickname}
          maxLength={20}
          clearable
          placeholder="请输入昵称"
          onChange={setNickname}
        />
        <div className="field-hint">1-20 个字符</div>
      </section>

      <Button
        block
        color="primary"
        loading={saving}
        className="save-button"
        onClick={handleSave}
      >
        保存
      </Button>
    </div>
  );
};

export default ProfileEditPage;
