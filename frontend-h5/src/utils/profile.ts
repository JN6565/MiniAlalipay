/** 生成个人信息页的账户名展示文本。 */
export const formatAccountName = (accountNumber?: string): string =>
  `账户名：${accountNumber || '加载中...'}`;

/** C 端个人资料展示偏好，仅保存在当前浏览器（与现有昵称本地策略一致）。 */
export interface ProfilePreference {
  nickname: string;
  avatarCode: string;
  avatarDataUrl?: string;
  /** 性别（本地展示偏好）：男/女/保密。 */
  gender: string;
  /** 地区（本地展示偏好）。 */
  region: string;
  /** 个性签名（本地展示偏好）。 */
  signature: string;
}

/** 内置头像编码（V2 定稿：与 BuiltinAvatars 的四种 SVG 头像一一对应）。 */
export const PROFILE_AVATARS = [
  { code: 'USER', label: '人像头像' },
  { code: 'SMILE', label: '笑脸头像' },
  { code: 'HILLS', label: '山景头像' },
  { code: 'CAT', label: '猫耳头像' },
] as const;

const DEFAULT_AVATAR_CODE = PROFILE_AVATARS[0].code;

/** 旧版头像编码到新版内置头像的兼容映射，存量 localStorage 值自动跟随。 */
const LEGACY_AVATAR_MAP: Record<string, string> = {
  BLUE: 'USER',
  PURPLE: 'CAT',
  GREEN: 'HILLS',
  ORANGE: 'SMILE',
};

/** 将头像编码归一化为内置头像编码（未知/旧编码回退）。 */
export const normalizeAvatarCode = (avatarCode?: string): string => {
  if (!avatarCode) return DEFAULT_AVATAR_CODE;
  if (PROFILE_AVATARS.some((avatar) => avatar.code === avatarCode)) return avatarCode;
  return LEGACY_AVATAR_MAP[avatarCode] || DEFAULT_AVATAR_CODE;
};

/** 读取当前浏览器会话中的个人资料展示偏好。 */
export const getProfilePreference = (): ProfilePreference => ({
  nickname: localStorage.getItem('nickname') || '用户',
  avatarCode: normalizeAvatarCode(localStorage.getItem('avatarCode') || undefined),
  avatarDataUrl: localStorage.getItem('avatarDataUrl') || undefined,
  gender: localStorage.getItem('profileGender') || '保密',
  region: localStorage.getItem('profileRegion') || '',
  signature: localStorage.getItem('profileSignature') || '',
});

/** 仅首次在浏览器建立昵称展示偏好时用服务端昵称初始化，已有本地编辑则保留，保证退出重登后本地昵称仍生效。 */
export const seedNicknamePreference = (serverNickname?: string | null, fallback = '用户'): void => {
  if (!localStorage.getItem('nickname')) {
    localStorage.setItem('nickname', serverNickname || fallback);
  }
};

/** 将个人资料展示偏好保存到当前浏览器会话。 */
export const saveProfilePreference = (preference: ProfilePreference): void => {
  localStorage.setItem('nickname', preference.nickname);
  localStorage.setItem('avatarCode', preference.avatarCode);
  if (preference.avatarDataUrl) {
    localStorage.setItem('avatarDataUrl', preference.avatarDataUrl);
  } else {
    localStorage.removeItem('avatarDataUrl');
  }
  localStorage.setItem('profileGender', preference.gender);
  localStorage.setItem('profileRegion', preference.region);
  localStorage.setItem('profileSignature', preference.signature);
};

/**
 * 头像展示兼容导出：V2 起头像统一由 BuiltinAvatar SVG 组件渲染，
 * 本函数仅供存量调用点回退文本，不再作为主展示方式。
 */
export const getAvatarDisplay = (avatarCode?: string): string =>
  PROFILE_AVATARS.find((avatar) => avatar.code === normalizeAvatarCode(avatarCode))?.label || PROFILE_AVATARS[0].label;

/** 头像文件大小上限，避免浏览器本地存储被大文件耗尽。 */
export const AVATAR_FILE_MAX_SIZE = 1024 * 1024;

/** 将用户选择的图片读取为本地展示用 Data URL，不上传到服务端。 */
export const readAvatarFile = (file: File): Promise<string> => {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    return Promise.reject(new Error('头像仅支持 JPG、PNG 或 WebP 格式'));
  }
  if (file.size > AVATAR_FILE_MAX_SIZE) {
    return Promise.reject(new Error('头像文件不能超过 1 MB'));
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result);
      } else {
        reject(new Error('头像读取失败'));
      }
    };
    reader.onerror = () => reject(new Error('头像读取失败'));
    reader.readAsDataURL(file);
  });
};
