/** 生成个人信息页的账户名展示文本。 */
export const formatAccountName = (accountNumber?: string): string =>
  `账户名：${accountNumber || '加载中...'}`;

/** C 端个人资料展示偏好，仅保存在当前浏览器。 */
export interface ProfilePreference {
  nickname: string;
  avatarCode: string;
  avatarDataUrl?: string;
}

/** 可选头像及其展示内容，编码值用于浏览器端持久化。 */
export const PROFILE_AVATARS = [
  { code: 'BLUE', label: '蓝色头像', display: '👤' },
  { code: 'PURPLE', label: '紫色头像', display: '🧑' },
  { code: 'GREEN', label: '绿色头像', display: '🙂' },
  { code: 'ORANGE', label: '橙色头像', display: '😊' },
] as const;

const DEFAULT_AVATAR_CODE = PROFILE_AVATARS[0].code;

/** 读取当前浏览器会话中的个人资料展示偏好。 */
export const getProfilePreference = (): ProfilePreference => ({
  nickname: localStorage.getItem('nickname') || '用户',
  avatarCode: localStorage.getItem('avatarCode') || DEFAULT_AVATAR_CODE,
  avatarDataUrl: localStorage.getItem('avatarDataUrl') || undefined,
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
};

/** 将头像编码转换为展示内容，未知编码回退到默认头像。 */
export const getAvatarDisplay = (avatarCode?: string): string =>
  PROFILE_AVATARS.find((avatar) => avatar.code === avatarCode)?.display || PROFILE_AVATARS[0].display;

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
