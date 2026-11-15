/**
 * pages/home/home.js —— 首页（tab 首位，参照一点点小程序首页版式）
 *
 * 本页职责（从原点单页抽离的门户内容）：
 *   品牌头（logo 小人 + 店名 + 营业状态）；轮播横幅（F1）；
 *   功能宫格（签到/券包/会员/AI点单 —— 评价墙按需求移除）；
 *   热销推荐（拉全部分类饮品按月售 Top6 横滑展示，点击跳点单页）；
 *   门店信息卡（globalData.shop，电话可一键拨打）+「去点单」大按钮。
 *   右下角茶小月 AI 悬浮球（onPageScroll 驱动滑动隐藏 / 静置 2 秒再现）。
 *
 * 涉及接口：
 *   GET /user/shop/status                       → 1 营业 / 0 打烊
 *   GET /user/banner/list                       → BannerVO[]
 *   GET /user/category/list?type=1              → 饮品分类列表
 *   GET /user/drink/list?categoryId=            → DrinkVO[]（含 monthSales）
 *
 * 新手要点：
 *   1. 热销推荐是「分类维度的多次聚合」：先拉分类，再并行拉各分类饮品，
 *      Promise.all 合并后按 monthSales 排序取前 6（真实后端可做成聚合接口 /user/drink/hot）；
 *   2. 金额展示统一走 utils/format.js 的 fenRound + fmtMoney；
 *   3. onPageScroll 触发频率很高，ai-ball 的显隐里做了「值没变不 setData」防抖。
 */
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');
const aiBall = require('../../utils/ai-ball');

Page({
  data: {
    shop: {},               // 门店静态信息（app.globalData）
    shopStatus: 1,          // 1 营业 / 0 打烊
    banners: [],            // 轮播横幅
    hotDrinks: [],          // 热销推荐 Top6
    loading: false
  },

  onShow() {
    // 登录守卫（首页是首个 tab：未登录先进登录页）
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      this.setData({ shop: getApp().globalData.shop });
      this.loadShopStatus();
      this.loadBanners();
      this.loadHotDrinks();
    });
  },

  /* ---------- 数据加载 ---------- */
  loadShopStatus() {
    request({ url: '/user/shop/status' })
      .then((status) => this.setData({ shopStatus: Number(status) }))
      .catch(() => { /* 拉取失败不阻塞首页展示 */ });
  },

  /** 轮播横幅：theme → 渐变底色 + badge 印章字（新/签/券） */
  loadBanners() {
    request({ url: '/user/banner/list' })
      .then((list) => {
        const badgeMap = { pine: '新', gold: '签', terracotta: '券' };
        const banners = (list || []).map((b) => ({ ...b, badge: badgeMap[b.theme] || '山' }));
        this.setData({ banners });
      })
      .catch(() => { /* 横幅拉取失败不阻塞，骨架位留白 */ });
  },

  /** 热销推荐：分类 → 并行拉各分类饮品 → 合并按月售排序取 Top6 */
  loadHotDrinks() {
    this.setData({ loading: true });
    request({ url: '/user/category/list', query: { type: 1 } })
      .then((categories) => {
        const catIds = (categories || []).map((c) => c.id);
        return Promise.all(
          catIds.map((id) => request({ url: '/user/drink/list', query: { categoryId: id } }))
        );
      })
      .then((lists) => {
        const all = (lists || []).reduce((acc, l) => acc.concat(l || []), []);
        const hot = all
          .filter((d) => d.status === 1)
          .sort((a, b) => (b.monthSales || 0) - (a.monthSales || 0))
          .slice(0, 6)
          .map((d) => ({
            ...d,
            priceText: F.fmtMoney(F.fenRound(d.price)),
            monthSales: d.monthSales || 0
          }));
        this.setData({ hotDrinks: hot, loading: false });
      })
      .catch(() => this.setData({ loading: false }));
  },

  /* ---------- 交互 ---------- */
  onBannerTap(e) {
    this.goPage(e.currentTarget.dataset.url);
  },

  /** 横幅跳转：tab 页 switchTab，非 tab 页 navigateTo，当前页忽略 */
  goPage(url) {
    if (!url || url === '/pages/home/home') return; // 当前页无动作
    const tabPages = ['/pages/home/home', '/pages/menu/menu', '/pages/order-list/order-list', '/pages/me/me'];
    if (tabPages.indexOf(url) > -1) {
      wx.switchTab({ url });
    } else {
      wx.navigateTo({ url });
    }
  },

  onGoMenu() { wx.switchTab({ url: '/pages/menu/menu' }); },
  onGoCheckin() { wx.navigateTo({ url: '/pages/checkin/checkin' }); },
  onGoCoupons() { wx.navigateTo({ url: '/pages/coupons/coupons' }); },
  onGoLevel() { wx.navigateTo({ url: '/pages/level/level' }); },
  onGoAi() { wx.navigateTo({ url: '/pages/ai-order/ai-order' }); },

  onCallShop() {
    wx.makePhoneCall({ phoneNumber: String(this.data.shop.phone || '') })
      .catch(() => { /* 用户取消拨打 */ });
  },

  /* ---------- AI 悬浮球：滑动隐藏 · 静置 2 秒再现 ---------- */
  onPageScroll() {
    aiBall.onPageScroll(this);
  }
});
