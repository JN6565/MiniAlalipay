import React, { useEffect, useRef, useState } from 'react';
import { history } from '@umijs/max';
import { Toast } from 'antd-mobile';
import * as userService from '@/services/user';
import {
  getProfilePreference,
  saveProfilePreference,
  readAvatarFile,
  PROFILE_AVATARS,
} from '@/utils/profile';
import { BuiltinAvatar, IconSet, RevealToggle } from '@/components/h5/common';
import './index.less';

/** 性别选项（本地展示偏好，chips 单选）。 */
const GENDERS = ['男', '女', '保密'];

/**
 * 个人详情页（V2 重设计）：
 * - 头像区：4 个内置 SVG 头像 + 本地上传位（≤1MB，仅存浏览器 localStorage）；
 * - 可编辑资料：昵称/地区/个性签名/性别（本地展示偏好）；
 * - 只读账户信息：掩码手机号/账户号（服务端脱敏，掩码可切换）；
 * - 密码、证件号等敏感信息一律不展示。
 */
const ProfileDetailPage: React.FC = () => {
  const initial = getProfilePreference();
  const [avatarCode, setAvatarCode] = useState(initial.avatarCode);
  const [avatarDataUrl, setAvatarDataUrl] = useState<string | undefined>(initial.avatarDataUrl);
  const [nickname, setNickname] = useState(initial.nickname);
  const [region, setRegion] = useState(initial.region);
  const [signature, setSignature] = useState(initial.signature);
  const [gender, setGender] = useState(initial.gender);
  const [userInfo, setUserInfo] = useState<userService.UserInfo | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    userService.getMyInfo().then(setUserInfo).catch(() => {});
  }, []);

  /** 选择内置头像：清除上传头像，回到内置体系。 */
  const handlePickBuiltin = (code: string) => {
    setAvatarCode(code);
    setAvatarDataUrl(undefined);
  };

  /** 上传本地图片：校验格式与大小后读为 Data URL，仅保存在浏览器。 */
  const handleUpload = async (file: File) => {
    try {
      const dataUrl = await readAvatarFile(file);
      setAvatarDataUrl(dataUrl);
    } catch (error) {
      Toast.show({ icon: 'fail', content: (error as Error).message });
    }
  };

  const handleSave = () => {
    if (!nickname.trim()) {
      Toast.show({ icon: 'fail', content: '昵称不能为空' });
      return;
    }
    if (nickname.trim().length > 20) {
      Toast.show({ icon: 'fail', content: '昵称不能超过20位' });
      return;
    }
    saveProfilePreference({
      nickname: nickname.trim(),
      avatarCode,
      avatarDataUrl,
      gender,
      region: region.trim(),
      signature: signature.trim(),
    });
    Toast.show({ icon: 'success', content: '已保存' });
    history.back();
  };

  return (
    <div className="profile-detail">
      {/* 头像区 */}
      <div className="pd-card">
        <div className="pd-card-title">头像</div>
        <div className="pd-avatar-row">
          {PROFILE_AVATARS.map((avatar) => (
            <div
              key={avatar.code}
              className={`pd-avatar-item${!avatarDataUrl && avatarCode === avatar.code ? ' on' : ''}`}
              onClick={() => handlePickBuiltin(avatar.code)}
            >
              <BuiltinAvatar
                kind={(avatar.code.toLowerCase() as 'user' | 'smile' | 'hills' | 'cat')}
                size={52}
                selected={!avatarDataUrl && avatarCode === avatar.code}
              />
              <span>{avatar.label}</span>
            </div>
          ))}
          {/* 上传位：仅保存在当前浏览器，不上传后端 */}
          <div
            className={`pd-avatar-item upload${avatarDataUrl ? ' on' : ''}`}
            onClick={() => fileInputRef.current?.click()}
          >
            {avatarDataUrl ? (
              <img src={avatarDataUrl} alt="上传头像" className="pd-upload-preview" />
            ) : (
              <div className="pd-upload-tile">
                <IconSet name="camera" size={22} color="var(--h5-primary)" />
              </div>
            )}
            <span>上传图片</span>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            style={{ display: 'none' }}
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) handleUpload(file);
              event.target.value = '';
            }}
          />
        </div>
        <div className="pd-tip">上传的图片仅保存在当前浏览器，不会上传服务端（≤1MB）</div>
      </div>

      {/* 可编辑资料 */}
      <div className="pd-card">
        <div className="pd-card-title">个人资料</div>
        <div className="pd-field">
          <span className="pd-label">昵称</span>
          <input value={nickname} maxLength={20} placeholder="请输入昵称" onChange={(e) => setNickname(e.target.value)} />
        </div>
        <div className="pd-field">
          <span className="pd-label">地区</span>
          <input value={region} maxLength={20} placeholder="请输入地区" onChange={(e) => setRegion(e.target.value)} />
        </div>
        <div className="pd-field">
          <span className="pd-label">个性签名</span>
          <input value={signature} maxLength={40} placeholder="写一句话介绍自己" onChange={(e) => setSignature(e.target.value)} />
        </div>
        <div className="pd-field">
          <span className="pd-label">性别</span>
          <div className="pd-chips">
            {GENDERS.map((item) => (
              <span key={item} className={`pd-chip${gender === item ? ' on' : ''}`} onClick={() => setGender(item)}>
                {item}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* 只读账户信息 */}
      <div className="pd-card">
        <div className="pd-card-title">账户信息</div>
        <div className="pd-readonly">
          <span className="pd-label">手机号</span>
          <div className="pd-value">
            {userInfo?.maskedPhone || '加载中...'}
            <IconSet name="eyeOff" size={15} color="var(--h5-text-3)" />
          </div>
        </div>
        <div className="pd-readonly">
          <span className="pd-label">账户号</span>
          <div className="pd-value">
            {userInfo ? (
              <RevealToggle defaultRevealed={false} mask={`${userInfo.accountNumber.slice(0, 3)}****`} value={userInfo.accountNumber} />
            ) : (
              '加载中...'
            )}
          </div>
        </div>
        <div className="pd-tip">密码、证件号等敏感信息不在此展示</div>
      </div>

      {/* 保存 */}
      <div className="pd-footer">
        <div className="h5-btn-gradient" onClick={handleSave}>保存</div>
      </div>
    </div>
  );
};

export default ProfileDetailPage;
