/**
 * pages/me/me.js —— 我的（tab 页）
 *
 * 本页职责：
 *   用户卡（avatarUrl 或默认头像 + nickName 或「山月茶咖用户」+ userId）；
 *   积分显示（优先 storage 缓存，再 GET /user/user/profile 拉取并回写缓存）；
 *   菜单列表：我的地址 / 我的订单 / 关于门店（弹层：营业时间 / 地址 / 电话）；
 *   USE_MOCK 模式额外提供「重置演示数据」；退出登录（确认弹窗 → 清 storage → 回登录页）。
 *
 * 涉及接口：
 *   GET /user/user/profile → { id, nickName, avatarUrl, phone, points }（山月补全契约）
 *
 * 新手要点：
 *   1. 登录接口 UserLoginVO 不含积分，积分从 profile 接口拿；这里再缓存到 storage，
 *      下次进入先展示缓存值，接口返回后覆盖（弱网/失败时页面不至于空白）
 *   2. 「我的订单」是 tabBar 页，必须用 switchTab（navigateTo 跳 tab 页会报错）
 *   3. 退出登录从 tab 页回登录页用 reLaunch —— 与 request.js 的 401 出口口径一致，
 *      redirectTo 从 tab 页跳非 tab 页会打乱返回栈
 *   4. 门店信息取 app.globalData.shop（与 submit 页「门店自取卡」同一份数据源，避免两处硬编码不一致）
 */
const config = require('../../utils/config');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');
const { resetDb } = require('../../utils/mock');

/** 积分 storage 缓存 key（仅本页使用） */
const POINTS_KEY = 'shanyue_points';

Page({
  data: {
    nickName: '',
    avatarUrl: '',
    userId: '',
    pointsText: '--',
    isMock: config.USE_MOCK,   // mock 模式才显示「重置演示数据」
    aboutVisible: false,
    shop: {}
  },

  onShow() {
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      this.refresh();
    });
  },

  /** 刷新用户信息 + 积分 */
  refresh() {
    const user = auth.getUser() || {};
    const patch = {
      nickName: user.nickName || '山月茶咖用户',
      avatarUrl: user.avatarUrl || '',
      userId: user.userId !== undefined ? user.userId : '',
      shop: getApp().globalData.shop
    };
    // 积分：先展示 storage 缓存，再拉 profile 覆盖
    const cached = wx.getStorageSync(POINTS_KEY);
    if (cached !== '' && cached !== null && cached !== undefined) {
      patch.pointsText = String(cached);
    }
    this.setData(patch);

    request({ url: '/user/user/profile' })
      .then((p) => {
        if (!p) return;
        const data = {};
        if (p.points !== undefined && p.points !== null) {
          data.pointsText = String(p.points);
          try { wx.setStorageSync(POINTS_KEY, p.points); } catch (e) { /* 忽略 */ }
        }
        // profile 返回最新昵称/头像时同步展示（与 storage 不一致以后端为准）
        if (p.nickName) data.nickName = p.nickName;
        if (p.avatarUrl) data.avatarUrl = p.avatarUrl;
        this.setData(data);
      })
      .catch(() => { /* profile 拉取失败时保留缓存展示，不打断页面 */ });
  },

  noop() { /* 阻止冒泡占位（弹层内容区拦截冒泡，点击不关闭） */ },

  /* ---------- 菜单入口 ---------- */
  onGoAddress() {
    wx.navigateTo({ url: '/pages/address-list/address-list' });
  },

  onGoOrders() {
    // tabBar 页必须 switchTab
    wx.switchTab({ url: '/pages/order-list/order-list' });
  },

  onOpenAbout() {
    this.setData({ aboutVisible: true });
  },

  onCloseAbout() {
    this.setData({ aboutVisible: false });
  },

  /** 拨打门店电话（去掉分隔符再拨） */
  onCallShop() {
    const phone = String(this.data.shop.phone || '').replace(/\D/g, '');
    if (!phone) return;
    wx.makePhoneCall({ phoneNumber: phone });
  },

  /* ---------- mock 演示辅助 ---------- */
  /** 重置演示数据：清掉 mock 本地库（下次请求自动重新播种）并回登录页重新走一遍流程 */
  onResetDemo() {
    wx.showModal({
      title: '重置演示数据',
      content: '将清空演示用的购物车、订单、地址等数据，并重新登录，确定吗？',
      confirmColor: '#D9534F',
      success: (res) => {
        if (!res.confirm) return;
        resetDb();
        try { wx.removeStorageSync(POINTS_KEY); } catch (e) { /* 忽略 */ }
        auth.logout();
        wx.showToast({ title: '已重置', icon: 'success' });
        setTimeout(() => wx.reLaunch({ url: '/pages/login/login' }), 400);
      }
    });
  },

  /* ---------- 退出登录 ---------- */
  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新登录才能下单，确定退出吗？',
      confirmColor: '#D9534F',
      success: (res) => {
        if (!res.confirm) return;
        auth.logout(); // 清本地登录态（苍穹 JWT 无状态，无需调后端）
        wx.reLaunch({ url: '/pages/login/login' });
      }
    });
  }
});
