/**
 * pages/submit/submit.js —— 确认订单（自取 / 外送双模式）
 *
 * 本页职责：
 *   自取 = 门店地址卡（免配送费）；外送 = 收货地址卡（无默认地址去地址列表选）+ 配送费行；
 *   展示购物车商品明细与备注；金额 = 商品合计 + 配送费（全部转分计算防浮点误差）；
 *   提交 → POST /user/order/submit → 清购物车 → redirectTo success 带订单信息。
 *
 * 涉及接口：
 *   GET  /user/shoppingCart/list            → 购物车明细
 *   GET  /user/addressBook/default          → 默认地址（外送用；data 可能为 null）
 *   POST /user/order/submit                 → OrderSubmitVO{ id, orderNumber, orderAmount, orderTime,
 *                                             +山月补全 pickupNumber(自取) / deliveryFee(外送) }
 *
 * 新手要点：
 *   1. 下单 body（苍穹 OrdersSubmitDTO + 山月补全 deliveryMode）：
 *      { addressBookId, payMethod: 1, remark, deliveryMode: 1自取|2外送, amount }
 *   2. 苍穹信任前端提交的 amount；本项目在前端用「分」精确累加后再提交
 *   3. 下单成功后端会清空购物车（苍穹行为），本页无需再调 clean
 *   4. 地址列表页以「选择模式」打开，选完通过上一页 setData 回传
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
    address: null               // 外送收货地址（AddressBook）
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

  /** 合计 = 商品 + （外送 ? 配送费 : 0），转分计算 */
  recalcTotal() {
    const goods = Math.round(Number(this.data.goodsTotalText) * 100);
    const fee = this.data.mode === config.MODE_DELIVERY ? Math.round(config.DELIVERY_FEE * 100) : 0;
    this.setData({ totalText: F.fmtMoney((goods + fee) / 100) });
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
    const body = {
      addressBookId: isDelivery ? this.data.address.id : null, // 外送必填（苍穹契约）
      payMethod: 1, // 1 微信支付
      remark: this.data.remark || '',
      deliveryMode: this.data.mode, // 山月补全：1 门店自取 | 2 外送
      amount: Number(this.data.totalText) // 苍穹 OrdersSubmitDTO.amount（总金额）
    };
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
