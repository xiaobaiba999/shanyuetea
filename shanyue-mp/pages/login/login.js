/**
 * pages/login/login.js —— 登录页
 *
 * 本页职责：
 *   品牌视觉展示 + 「微信一键登录」按钮（点击才调 loginFlow，不做静默登录）
 *
 * 涉及接口：
 *   POST /user/user/login { code } → UserLoginVO{ id, openid, token, nickName, avatarUrl }
 *   （wx.login 先拿 code，后端用 code 换 openid 并签发 JWT —— 苍穹登录模式）
 *
 * 新手要点：
 *   1. wx.login 获取的 code 是临时授权码（5 分钟有效），由后端去微信服务器换 openid
 *   2. token 存在本地 storage（utils/auth.js），之后每个请求自动带上 token 请求头
 *   3. 登录成功 switchTab 回菜单页（menu 是 tabBar 页，只能 switchTab 跳转）
 */
const { loginFlow } = require('../../utils/auth');
const app = getApp();

Page({
  data: {
    loading: false,
    shopName: ''
  },

  onLoad() {
    this.setData({ shopName: app.globalData.shop.name });
    // 已登录用户直接进入菜单（避免重复登录）
    const { isLoggedIn } = require('../../utils/auth');
    if (isLoggedIn()) {
      wx.switchTab({ url: '/pages/menu/menu' });
    }
  },

  /** 点击「微信一键登录」才发起登录流程 */
  onLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    loginFlow((err) => {
      this.setData({ loading: false });
      if (err) {
        wx.showToast({ title: err.message || '登录失败', icon: 'none' });
        return;
      }
      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/menu/menu' });
      }, 400);
    });
  }
});
