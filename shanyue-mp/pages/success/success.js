/**
 * pages/success/success.js —— 下单成功页
 *
 * 本页职责：
 *   自取：取餐号超大字 + 「到店出示」；外送：配送信息卡；
 *   未支付时显示「模拟支付」→ PUT /user/order/payment（第一步：生成支付参数）
 *   → PUT /user/order/paySuccess?orderNumber=xx（第二步：模拟支付成功回调）→ toast；
 *   「查看订单」跳订单详情，「返回首页」回菜单。
 *
 * 涉及接口：
 *   PUT /user/order/payment      { orderNumber, payMethod } → OrderPaymentVO（微信支付参数，模拟）
 *   PUT /user/order/paySuccess?orderNumber=xx（山月补全契约：payStatus→1、状态 1→2）
 *
 * 新手要点：
 *   1. 苍穹支付是「两步」：先 payment 拿微信支付参数（正常应调 wx.requestPayment），
 *      个人小程序无法拉起支付，所以第二步用 paySuccess 接口模拟支付回调——与课程做法一致
 *   2. 页面参数由 submit 页 redirectTo 带过来（自取有 pickupNumber，外送有 deliveryFee）
 */
const F = require('../../utils/format');
const { request } = require('../../utils/request');

Page({
  data: {
    mode: 1,               // 1 自取 / 2 外送
    id: null,
    orderNumber: '',
    pickupNumber: '',
    deliveryFeeText: '0.00',
    consignee: '',
    addressText: '',
    orderTime: '',
    amountText: '0.00',
    paid: false
  },

  onLoad(options) {
    const o = options || {};
    this.setData({
      mode: Number(o.deliveryMode || 1),
      id: o.id,
      orderNumber: o.orderNumber || '',
      pickupNumber: o.pickupNumber || '',
      deliveryFeeText: F.fmtMoney(o.deliveryFee || 0),
      consignee: o.consignee ? decodeURIComponent(o.consignee) : '',
      addressText: o.addressText ? decodeURIComponent(o.addressText) : '',
      orderTime: o.orderTime ? F.fmtTimeFull(decodeURIComponent(o.orderTime)) : '',
      amountText: F.fmtMoney(o.amount || 0)
    });
  },

  /** 模拟支付：两步走（生成支付参数 → 支付成功回调） */
  onPay() {
    if (this._paying) return;
    this._paying = true;
    wx.showLoading({ title: '支付中', mask: true });
    request({
      url: '/user/order/payment',
      method: 'PUT',
      data: { orderNumber: this.data.orderNumber, payMethod: 1 }
    })
      .then(() => {
        // 第一步拿到 OrderPaymentVO（模拟环境直接忽略参数），第二步模拟支付成功回调
        return request({
          url: '/user/order/paySuccess',
          method: 'PUT',
          query: { orderNumber: this.data.orderNumber }
        });
      })
      .then(() => {
        wx.hideLoading();
        this.setData({ paid: true });
        wx.showToast({ title: '支付成功', icon: 'success' });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '支付失败', icon: 'none' });
      })
      .finally(() => {
        this._paying = false;
      });
  },

  onBackHome() {
    wx.switchTab({ url: "/pages/home/home" });
  },

  onViewOrder() {
    wx.redirectTo({ url: '/pages/order-detail/order-detail?id=' + this.data.id });
  }
});
