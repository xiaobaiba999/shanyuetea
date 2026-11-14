/**
 * pages/menu/menu.js —— 点单主页（核心页面）
 *
 * 本页职责：
 *   左分类右商品浏览；顶部「饮品/套餐」切换（分类 type=1/2）；
 *   饮品点击弹规格底部弹层（动态渲染 specGroups、实时算价 price+ΣpriceDelta），
 *   无规格直接加购；套餐点击弹包含明细弹层后加购(setmealId)；
 *   底部购物车 bar（角标/合计/去结算）；未登录跳登录页；打烊显示遮罩。
 *   【v2】顶部轮播横幅（F1）+ 五个功能入口（AI点单/签到/券包/会员/评价墙）+
 *   商品区骨架屏（F9 极简启动：数据未到先出骨架，不白屏）。
 *
 * 涉及接口：
 *   GET /user/shop/status                       → data: 1 营业 / 0 打烊
 *   GET /user/banner/list                       → BannerVO[]（v2/F1：theme 渐变卡，linkUrl 跳转）
 *   GET /user/category/list?type=1|2            → 分类列表
 *   GET /user/drink/list?categoryId=             → DrinkVO[]（含 monthSales / specGroups 山月补全）
 *   GET /user/setmeal/list?categoryId=          → 套餐列表
 *   GET /user/setmeal/drink/{id}                 → DrinkItemVO[] 套餐明细
 *   POST /user/shoppingCart/add | /sub          → 加购 / 减购（specSelected 为山月补全字段）
 *   GET /user/shoppingCart/list                 → 购物车列表
 *   DELETE /user/shoppingCart/clean             → 清空
 *
 * 新手要点：
 *   1. onShow 里做登录守卫（tab 页被 switchTab 反复进出，onShow 每次都会触发）
 *   2. 金额一律用 utils/format.js 的 fenRound（转分取整）计算，避免浮点误差
 *   3. specGroups 每组单选，选中项的 priceDelta 累加到基础价
 *   4. specSelected 是 JSON 字符串（同苍穹 drinkFlavor 模式），传给购物车作为「同一杯」的判定
 *   5. 横幅跳转 goPage：tab 页必须 switchTab（navigateTo 跳 tab 页会报错），当前页则忽略
 */
const config = require('../../utils/config');
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

Page({
  data: {
    shopStatus: 1,          // 1 营业 / 0 打烊
    currentType: 1,         // 1 饮品 / 2 套餐
    categories: [],
    currentCatId: null,
    goods: [],
    loading: false,
    // 【v2/F1】轮播横幅（theme 渐变卡 + badge 印章字）
    banners: [],
    // 购物车
    cart: [],
    cartCount: 0,
    cartTotalText: '0.00',
    cartPopupVisible: false,
    // 规格弹层
    specPopupVisible: false,
    specGoods: null,
    specGroups: [],         // [{ name, options: [{name, priceDelta}], selected }]
    specPriceText: '0.00',
    // 套餐弹层
    setmealPopupVisible: false,
    setmealItem: null,
    setmealDetails: []
  },

  onShow() {
    // 登录守卫：未登录 → 登录页（先登录后下单）
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      this.loadShopStatus();
      this.loadBanners();
      this.bootstrap();
      this.refreshCart();
    });
  },

  noop() { /* 阻止冒泡占位 */ },

  /* ---------- 数据加载 ---------- */
  loadShopStatus() {
    request({ url: '/user/shop/status' })
      .then((status) => this.setData({ shopStatus: Number(status) }))
      .catch(() => { /* 店铺状态拉取失败不阻塞点单 */ });
  },

  /** 【v2/F1】轮播横幅：theme → 渐变底色 + badge 印章字（新/签/券） */
  loadBanners() {
    request({ url: '/user/banner/list' })
      .then((list) => {
        const badgeMap = { pine: '新', gold: '签', terracotta: '券' };
        const banners = (list || []).map((b) => ({ ...b, badge: badgeMap[b.theme] || '山' }));
        this.setData({ banners });
      })
      .catch(() => { /* 横幅拉取失败不阻塞点单，骨架位留白 */ });
  },

  /** 【v2/F1】横幅跳转：tab 页 switchTab，非 tab 页 navigateTo，当前页忽略 */
  onBannerTap(e) {
    this.goPage(e.currentTarget.dataset.url);
  },

  goPage(url) {
    if (!url || url === '/pages/menu/menu') return; // 当前页：新品横幅点击原地无动作
    const tabPages = ['/pages/menu/menu', '/pages/order-list/order-list', '/pages/me/me'];
    if (tabPages.indexOf(url) > -1) {
      wx.switchTab({ url });
    } else {
      wx.navigateTo({ url });
    }
  },

  /* ---------- 【v2】功能入口 ---------- */
  onGoAi() { wx.navigateTo({ url: '/pages/ai-order/ai-order' }); },
  onGoCheckin() { wx.navigateTo({ url: '/pages/checkin/checkin' }); },
  onGoCoupons() { wx.navigateTo({ url: '/pages/coupons/coupons' }); },
  onGoLevel() { wx.navigateTo({ url: '/pages/level/level' }); },
  onGoReviews() { wx.navigateTo({ url: '/pages/reviews/reviews' }); },

  bootstrap() {
    this.setData({ loading: true });
    request({ url: '/user/category/list', query: { type: this.data.currentType } })
      .then((categories) => {
        const first = categories.length ? categories[0].id : null;
        this.setData({ categories, currentCatId: first });
        // loading 维持到商品也到位（【v2/F9】骨架屏覆盖分类+商品两段加载）
        return this.loadGoods().then(() => this.setData({ loading: false }));
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载失败', icon: 'none' });
      });
  },

  /** 加载当前分类的商品（饮品 DrinkVO / 套餐 Setmeal） */
  loadGoods() {
    const catId = this.data.currentCatId;
    if (!catId) {
      this.setData({ goods: [] });
      return Promise.resolve();
    }
    const isDrink = this.data.currentType === 1;
    const url = isDrink ? '/user/drink/list' : '/user/setmeal/list';
    return request({ url, query: { categoryId: catId } })
      .then((list) => {
        const goods = (list || []).map((g) => ({
          ...g,
          priceText: F.fmtMoney(g.price),
          // 图片：兼容两种 image 值——相对路径（/upload/xxx.png）拼上后端地址 BASE_URL；
          // OSS 完整 http(s) URL 直接用（判断方式与 utils/request.js 第 66 行同款）；
          // 加载失败时 onThumbError 会置 imgFail 露出首字占位
          imgUrl: g.image ? (g.image.indexOf('http') === 0 ? g.image : config.BASE_URL + g.image) : '',
          imgFail: false,
          // 饮品是否有规格组（有 → 点击弹规格层；无 → 直接加购）
          hasSpecs: isDrink && Array.isArray(g.specGroups) && g.specGroups.length > 0,
          itemCount: isDrink ? 0 : this.estimateSetmealCount(g)
        }));
        this.setData({ goods });
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '加载商品失败', icon: 'none' });
      });
  },

  /** 套餐件数（能从描述里数出几行就算几件，纯展示用） */
  estimateSetmealCount(setmeal) {
    const d = setmeal.description || '';
    const matches = d.match(/×\s*\d+/g);
    if (!matches) return 1;
    return matches.reduce((sum, m) => sum + (parseInt(m.replace('×', ''), 10) || 1), 0);
  },

  /* ---------- 切换 ---------- */
  onSwitchType(e) {
    const type = Number(e.currentTarget.dataset.type);
    if (type === this.data.currentType) return;
    this.setData({ currentType: type, goods: [] });
    this.bootstrap();
  },

  onSwitchCat(e) {
    const id = Number(e.currentTarget.dataset.id);
    if (id === this.data.currentCatId) return;
    this.setData({ currentCatId: id });
    this.loadGoods();
  },

  /* ---------- 商品点击 ---------- */
  onGoodsTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    const item = this.data.goods[index];
    if (!item) return;
    if (this.data.currentType === 1) {
      // 饮品
      if (item.status === 0) {
        wx.showToast({ title: '「' + item.name + '」已售罄', icon: 'none' });
        return;
      }
      if (item.hasSpecs) {
        this.openSpecPopup(item);
      } else {
        this.addToCart({ drinkId: item.id }); // 无规格直接加购
      }
    } else {
      // 套餐：弹包含明细
      this.openSetmealPopup(item);
    }
  },

  /** 缩略图加载失败（如本地 upload 目录还没放图片文件）→ 露出首字占位，不阻塞浏览 */
  onThumbError(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ ['goods[' + index + '].imgFail']: true });
  },

  /* ---------- 规格弹层 ---------- */
  openSpecPopup(item) {
    // 每组默认选中第一个选项
    // 注意：id 必须保留：后端加购时用 optionId 匹配「该饮品的合法选项」重算金额，
    //   快照里缺 optionId 会被判定为非法规格选项（服务端防篡改校验）
    const groups = (item.specGroups || []).map((g) => ({
      id: g.id,
      name: g.name,
      options: (g.options || []).map((o) => ({
        id: o.id,
        name: o.name,
        priceDelta: Number(o.priceDelta) || 0
      })),
      selected: 0
    }));
    this.setData({
      specPopupVisible: true,
      specGoods: item,
      specGroups: groups
    });
    this.recalcSpecPrice();
  },

  onCloseSpec() {
    this.setData({ specPopupVisible: false });
  },

  onPickOption(e) {
    const g = Number(e.currentTarget.dataset.g);
    const o = Number(e.currentTarget.dataset.o);
    const key = 'specGroups[' + g + '].selected';
    const patch = {};
    patch[key] = o;
    this.setData(patch);
    this.recalcSpecPrice();
  },

  /** 实时算价：基础价 + Σ(选中项 priceDelta)，转分计算防浮点误差 */
  recalcSpecPrice() {
    const base = Math.round(Number(this.data.specGoods.price) * 100);
    let cents = base;
    this.data.specGroups.forEach((g) => {
      const opt = g.options[g.selected];
      if (opt) cents += Math.round(opt.priceDelta * 100);
    });
    this.setData({ specPriceText: F.fmtMoney(cents / 100) });
  },

  /**
   * 组装 specSelected JSON 字符串（山月契约格式，同苍穹 drinkFlavor 模式）
   * 完整快照：[{"groupId":1,"groupName":"杯型","optionId":2,"optionName":"大杯","priceDelta":3}]
   * groupId/optionId 是后端「合法选项校验 + 金额重算」的唯一依据，绝不能省略——
   * 只传名字的话后端拿不到 optionId，会直接拒绝（包含非法规格选项）
   */
  buildSpecSelected() {
    const pairs = this.data.specGroups.map((g) => {
      const opt = g.options[g.selected] || { id: null, name: '', priceDelta: 0 };
      return { groupId: g.id, groupName: g.name, optionId: opt.id, optionName: opt.name, priceDelta: opt.priceDelta };
    });
    return pairs.length ? JSON.stringify(pairs) : null;
  },

  onConfirmSpec() {
    const specSelected = this.buildSpecSelected();
    this.addToCart({ drinkId: this.data.specGoods.id, specSelected });
    this.onCloseSpec();
  },

  /* ---------- 套餐弹层 ---------- */
  openSetmealPopup(item) {
    this.setData({ setmealPopupVisible: true, setmealItem: { ...item, priceText: F.fmtMoney(item.price) }, setmealDetails: [] });
    request({ url: '/user/setmeal/drink/' + item.id })
      .then((details) => this.setData({ setmealDetails: details || [] }))
      .catch(() => { /* 明细拉取失败不阻塞加购 */ });
  },

  onCloseSetmeal() {
    this.setData({ setmealPopupVisible: false });
  },

  onAddSetmeal() {
    this.addToCart({ setmealId: this.data.setmealItem.id });
    this.onCloseSetmeal();
  },

  /* ---------- 购物车 ---------- */
  /** 统一加购入口：params = { drinkId, setmealId, specSelected } */
  addToCart(params) {
    request({ url: '/user/shoppingCart/add', method: 'POST', data: params })
      .then(() => {
        wx.showToast({ title: '已加入购物车', icon: 'success', duration: 600 });
        return this.refreshCart();
      })
      .catch((err) => wx.showToast({ title: err.message || '加购失败', icon: 'none' }));
  },

  refreshCart() {
    return request({ url: '/user/shoppingCart/list' })
      .then((cart) => {
        let count = 0;
        let cents = 0;
        const items = (cart || []).map((c) => {
          count += c.number;
          // 苍穹口径：cart.amount = 单价；小计 = 单价 × 数量
          const subtotal = F.fenRound(Number(c.amount) * c.number);
          cents += Math.round(subtotal * 100);
          return {
            ...c,
            specText: F.parseSpecText(c.specSelected || c.drinkFlavor),
            subtotalText: F.fmtMoney(subtotal)
          };
        });
        this.setData({
          cart: items,
          cartCount: count,
          cartTotalText: F.fmtMoney(cents / 100)
        });
      })
      .catch(() => { /* 购物车拉取失败不阻塞浏览 */ });
  },

  onToggleCart() {
    this.setData({ cartPopupVisible: !this.data.cartPopupVisible });
  },

  onCartPlus(e) {
    const item = this.data.cart[Number(e.currentTarget.dataset.index)];
    if (!item) return;
    this.addToCart({ drinkId: item.drinkId, setmealId: item.setmealId, specSelected: item.specSelected });
  },

  onCartMinus(e) {
    const item = this.data.cart[Number(e.currentTarget.dataset.index)];
    if (!item) return;
    request({ url: '/user/shoppingCart/sub', method: 'POST', data: { drinkId: item.drinkId, setmealId: item.setmealId, specSelected: item.specSelected } })
      .then(() => this.refreshCart())
      .catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }));
  },

  onCleanCart() {
    request({ url: '/user/shoppingCart/clean', method: 'DELETE' })
      .then(() => this.refreshCart())
      .catch((err) => wx.showToast({ title: err.message || '清空失败', icon: 'none' }));
  },

  onCheckout() {
    if (this.data.shopStatus !== 1) {
      wx.showToast({ title: '门店已打烊，暂不能下单', icon: 'none' });
      return;
    }
    if (!this.data.cartCount) {
      wx.showToast({ title: '先去挑一杯吧', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages/submit/submit' });
  }
});
