/**
 * pages/submit/submit.js —— 确认订单（自取 / 外送双模式）
 *
 * 本页职责：
 *   自取 = 门店地址卡（免配送费）；外送 = 收货地址卡（无默认地址去地址列表选）+ 配送费行；
 *   展示购物车商品明细与备注；金额 = 商品合计 + 配送费 - 优惠券抵扣（【v2/F4】，全部转分计算防浮点误差）；
 *   提交 → POST /user/order/submit → 清购物车 → redirectTo success 带订单信息。
 *
 * 涉及接口：
 *   GET  /user/shoppingCart/list            → 购物车明细
 *   GET  /user/addressBook/default          → 默认地址（外送用；data 可能为 null）
 *   GET  /user/coupon/usable?amount=        → 【v2/F4】可用券列表（按商品金额过滤门槛，预计算抵扣额）
 *   POST /user/order/submit                 → OrderSubmitVO{ id, orderNumber, orderAmount, orderTime,
 *                                             +山月补全 pickupNumber(自取) / deliveryFee(外送) }
 *
 * 新手要点：
 *   1. 下单 body（苍穹 OrdersSubmitDTO + 山月补全 deliveryMode + 【v2/F4】用券三字段）：
 *      { addressBookId, payMethod: 1, remark, deliveryMode: 1自取|2外送,
 *        amount(未抵扣总额), userCouponId?, couponBaseAmount?(商品金额，不含配送费) }
 *   2. 【v2/F4】用券口径：前端展示「券后合计」，但提交的 amount 是未抵扣总额；
 *      后端在下单事务里重新校验门槛 → 算抵扣 → 置券已使用，回传 OrderSubmitVO.orderAmount 为券后实付
 *      （与 mock.js 的 applyCoupon 挂钩、Java CouponController 桩注释三处同口径）
 *   3. 苍穹信任前端提交的 amount；本项目在前端用「分」精确累加后再提交
 *   4. 下单成功后端会清空购物车（苍穹行为），本页无需再调 clean
 *   5. 地址列表页以「选择模式」打开，选完通过上一页 setData 回传
 */
const config = require('../../utils/config');
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

Page({
  data: {
    mode: config.MODE_PICKUP,   // 1 自取 / 2 外送
    shop: {},
    cart: [],
    goodsTotalText: '0.00',
    deliveryFeeText: F.fmtMoney(config.DELIVERY_FEE),
    totalText: '0.00',
    remark: '',
    address: null,             // 外送收货地址（AddressBook）
    // 【v2/F4】优惠券
    coupons: [],               // 可用券（GET coupon/usable，按商品金额过滤）
    selectedCoupon: null,      // 已选券 {userCouponId, name, discountAmount, ...}
    discountText: '0.00',      // 已选券抵扣额展示
    couponPopupVisible: false
  },

  onLoad() {
    this.setData({ shop: getApp().globalData.shop });
    // 记住上次选择的模式
    const last = wx.getStorageSync(config.LAST_MODE_KEY);
    if (last === config.MODE_PICKUP || last === config.MODE_DELIVERY) {
      this.setData({ mode: last });
    }
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

  /** 加载购物车 + （外送时）默认地址 */
  refresh() {
    request({ url: '/user/shoppingCart/list' })
      .then((cart) => {
        let cents = 0;
        const items = (cart || []).map((c) => {
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
          goodsTotalText: F.fmtMoney(cents / 100)
        });
        this.recalcTotal();
        // 【v2/F4】按商品金额拉可用券（门槛比对不含配送费）
        this.loadCoupons(cents / 100);
        // 购物车为空时提示（从菜单进入正常不会为空）
        if (!items.length) {
          wx.showToast({ title: '购物车是空的', icon: 'none' });
        }
      })
      .catch((err) => wx.showToast({ title: err.message || '加载购物车失败', icon: 'none' }));

    // 外送模式且尚未选择地址时，拉默认地址
    if (this.data.mode === config.MODE_DELIVERY && !this.data.address && !this._addressPicked) {
      request({ url: '/user/addressBook/default' })
        .then((addr) => this.setData({ address: addr || null }))
        .catch(() => { /* 拉取失败则显示「请选择地址」 */ });
    }
  },

  /** 合计 = 商品 + （外送 ? 配送费 : 0）- 已选券抵扣，转分计算 */
  recalcTotal() {
    const goods = Math.round(Number(this.data.goodsTotalText) * 100);
    const fee = this.data.mode === config.MODE_DELIVERY ? Math.round(config.DELIVERY_FEE * 100) : 0;
    // 【v2/F4】券后合计（下限 0）
    const disc = this.data.selectedCoupon ? Math.round(Number(this.data.selectedCoupon.discountAmount) * 100) : 0;
    const pay = Math.max(0, goods + fee - disc);
    this.setData({
      totalText: F.fmtMoney(pay / 100),
      discountText: F.fmtMoney(disc / 100)
    });
  },

  /* ---------- 【v2/F4】优惠券 ---------- */
  /** 拉可用券：商品金额变化后重算；已选券若不再可用（门槛不够）则自动取消选择 */
  loadCoupons(amount) {
    request({ url: '/user/coupon/usable', query: { amount: F.fenRound(amount) } })
      .then((list) => {
        const coupons = (list || []).map((c) => ({ ...c, discountAmountText: F.fmtMoney(c.discountAmount) }));
        const patch = { coupons };
        if (this.data.selectedCoupon) {
          const still = coupons.find((c) => c.userCouponId === this.data.selectedCoupon.userCouponId);
          if (!still) patch.selectedCoupon = null;
        }
        this.setData(patch);
        this.recalcTotal();
      })
      .catch(() => { /* 券列表拉取失败不阻塞下单（当作无券可用） */ });
  },

  noop() { /* 阻止冒泡占位（弹层内容区拦截冒泡） */ },

  onToggleCouponPopup() {
    this.setData({ couponPopupVisible: !this.data.couponPopupVisible });
  },

  /** 选券：index=-1 表示「不使用优惠券」 */
  onPickCoupon(e) {
    const index = Number(e.currentTarget.dataset.index);
    const selectedCoupon = index > -1 ? (this.data.coupons[index] || null) : null;
    this.setData({ selectedCoupon, couponPopupVisible: false });
    this.recalcTotal();
  },

  onSwitchMode(e) {
    const mode = Number(e.currentTarget.dataset.mode);
    if (mode === this.data.mode) return;
    this.setData({ mode });
    wx.setStorageSync(config.LAST_MODE_KEY, mode);
    this.recalcTotal();
    if (mode === config.MODE_DELIVERY && !this.data.address) {
      request({ url: '/user/addressBook/default' })
        .then((addr) => this.setData({ address: addr || null }))
        .catch(() => {});
    }
  },

  /** 去地址列表选择（select 模式回传） */
  onChooseAddress() {
    this._addressPicked = false;
    wx.navigateTo({ url: '/pages/address-list/address-list?mode=select' });
  },

  /** 供 address-list 选择模式回传（getCurrentPages 跨页调用） */
  onAddressPicked(address) {
    this._addressPicked = true;
    this.setData({ address });
  },

  onRemarkInput(e) {
    this.setData({ remark: e.detail.value });
  },

  /** 提交订单 */
  onSubmit() {
    if (!this.data.cart.length) {
      wx.showToast({ title: '购物车是空的', icon: 'none' });
      return;
    }
    const isDelivery = this.data.mode === config.MODE_DELIVERY;
    if (isDelivery && !this.data.address) {
      wx.showToast({ title: '请先选择收货地址', icon: 'none' });
      return;
    }
    // 【v2/F4】提交口径：amount = 未抵扣总额（商品 + 配送费）；
    // 券后实付由后端在下单事务里算（回传 orderAmount），避免前后端两套抵扣口径不一致
    const goodsCents = Math.round(Number(this.data.goodsTotalText) * 100);
    const feeCents = isDelivery ? Math.round(config.DELIVERY_FEE * 100) : 0;
    const submitAmount = F.fenRound((goodsCents + feeCents) / 100);
    const body = {
      addressBookId: isDelivery ? this.data.address.id : null, // 外送必填（苍穹契约）
      payMethod: 1, // 1 微信支付
      remark: this.data.remark || '',
      deliveryMode: this.data.mode, // 山月补全：1 门店自取 | 2 外送
      amount: submitAmount // 未抵扣总额（后端事务内用券后回传 orderAmount）
    };
    if (this.data.selectedCoupon) {
      body.userCouponId = this.data.selectedCoupon.userCouponId;      // 用哪张券
      body.couponBaseAmount = Number(this.data.goodsTotalText);        // 门槛比对基准：商品金额（不含配送费）
    }
    wx.showLoading({ title: '提交中', mask: true });
    request({ url: '/user/order/submit', method: 'POST', data: body })
      .then((vo) => {
        wx.hideLoading();
        // 后端已清购物车；跳转成功页并带上订单信息
        const q = [
          'id=' + vo.id,
          'orderNumber=' + vo.orderNumber,
          'amount=' + (vo.orderAmount !== undefined ? vo.orderAmount : body.amount),
          'orderTime=' + encodeURIComponent(vo.orderTime || ''),
          'deliveryMode=' + this.data.mode
        ];
        if (vo.pickupNumber) q.push('pickupNumber=' + vo.pickupNumber);       // 自取才有
        if (vo.deliveryFee) q.push('deliveryFee=' + vo.deliveryFee);          // 外送才有
        if (isDelivery) {
          const a = this.data.address;
          q.push('consignee=' + encodeURIComponent(a.consignee + ' ' + a.phone));
          q.push('addressText=' + encodeURIComponent(a.provinceName + a.cityName + a.districtName + a.detail));
        }
        wx.redirectTo({ url: '/pages/success/success?' + q.join('&') });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '下单失败', icon: 'none' });
      });
  }
});
