export default function access(initialState: {
  currentUser?: API.CurrentUser;
}) {
  const { currentUser } = initialState || {};

  return {
    // 是否已登录
    isLoggedIn: !!currentUser,

    // 普通用户
    canUser: currentUser && currentUser.userType === 'NORMAL',

    // 商户用户
    canMerchant: currentUser && currentUser.userType === 'MERCHANT',

    // 运营人员
    canOperator: currentUser && currentUser.userType === 'OPERATOR',

    // 管理员
    isAdmin: currentUser && currentUser.userType === 'ADMIN',
  };
}
