/**
 * pages/order-detail/order-detail.js —— 订单详情
 *
 * 本页职责：
 *   GET /user/order/orderDetail/{id} 拉取 OrderVO（含 orderDetailList）；
 *   状态步骤条（6 状态映射视觉进度，已取消单独横幅）；
 *   自取 → 取餐号大卡（等宽大字）；外送 → 收货人/电话/地址卡；
 *   品项明细（specSelected JSON 快照解析展示）+ 金额明细 + 订单信息（单号可复制）；
 *   底部操作按钮按状态出没：去支付 / 催单 / 取消 / 再来一单。
 *
 * 涉及接口：
 *   GET  /user/order/orderDetail/{id}  → OrderVO
 *   PUT  /user/order/payment           → 支付第一步（生成支付参数）
 *   PUT  /user/order/paySuccess?orderNumber= → 支付第二步（模拟回调）
 *   GET  /user/order/reminder/{id}     → 催单（仅 2/3 状态）
 *   PUT  /user/order/cancel/{id}       → 取消（仅 1/2 状态）
 *   POST /user/order/repetition/{id}   → 再来一单（明细原样插回购物车）
 *
 * 新手要点：
 *   1. 苍穹口径：订单明细 OrderDetail.amount = 单价，行小计 = amount × number，
 *      Orders.amount = 总金额（已含配送费）——本页金额明细按此口径拆分
 *   2. specSelected 是 String JSON 快照，用 format.js 的 parseSpecText 解析成「大杯·冰·半糖」
 *   3. 复制单号用 wx.setClipboardData（个人小程序最常用的「复制」能力）
 *   4. 再来一单成功后 switchTab 回菜单页，底部购物车条立即可见
 */
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

Page({
  data: {
    id: null,
    order: null,
    loading: true,
    /* ---- 展示模型 ---- */
    statusText: '',
    statusColor: '#9AA0A6',
    cancelled: false,
    cancelReason: '',
    modeText: '',
    isPickup: true,
    pickupNumber: '',
    consignee: '',
    phone: '',
    addressText: '',
    /* 步骤条：6 状态映射为 5 个节点（待付款→待接单→制作中→待取餐/派送中→已完成） */
    steps: [],
    stepActive: -1,
    /* 明细与金额 */
    details: [],
    goodsTotalText: '0.00',
    deliveryFeeText: '0.00',
    amountText: '0.00',
    /* 订单信息 */
    timeText: '',
    payText: '',
    remark: '',
    /* 按钮可见性 */
    canPay: false,
    canRemind: false,
    canCancel: false,
    canRepeat: false
  },

  onLoad(options) {
    this.setData({ id: (options && options.id) || '' });
  },

  onShow() {
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      this.load();
    });
  },

  /* ---------- 数据加载 ---------- */
  load() {
    this.setData({ loading: true });
    request({ url: '/user/order/orderDetail/' + this.data.id })
      .then((o) => {
        this.setData({ loading: false });
        this.render(o);
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载订单失败', icon: 'none' });
      });
  },

  /** OrderVO → 页面展示模型 */
  render(o) {
    const status = Number(o.status);
    const isDelivery = Number(o.deliveryMode) === 2;
    const cancelled = status === 6;

    // 步骤条节点：第 4 节点文案按配送模式切换（自取「待取餐」/ 外送「派送中」）
    const steps = [
      { label: '待付款' },
      { label: '待接单' },
      { label: '制作中' },
      { label: isDelivery ? '派送中' : '待取餐' },
      { label: '已完成' }
    ];
    // 1~5 状态 → 当前所在节点下标（status-1）；已取消不展示步骤条
    const stepActive = status >= 1 && status <= 5 ? status - 1 : -1;

    // 明细：specSelected JSON 快照解析 + 行小计（单价 × 数量）
    let cents = 0;
    const details = (o.orderDetailList || []).map((d) => {
      const number = Number(d.number) || 0;
      const subtotal = F.fenRound(Number(d.amount) * number);
      cents += Math.round(subtotal * 100);
      return {
        ...d,
        specText: F.parseSpecText(d.specSelected || d.drinkFlavor),
        subtotalText: F.fmtMoney(subtotal)
      };
    });

    const isPickup = !isDelivery;
    this.setData({
      order: o,
      statusNum: status,
      statusText: F.statusText(o.status, o.deliveryMode),
      statusColor: F.statusColor(o.status),
      cancelled,
      cancelReason: cancelled ? (o.cancelReason || '订单已取消') : '',
      modeText: F.deliveryModeText(o.deliveryMode),
      isPickup,
      pickupNumber: isPickup ? (o.pickupNumber || '—') : '',
      consignee: o.consignee || '',
      phone: o.phone || '',
      addressText: o.address || '',
      steps,
      stepActive,
      details,
      goodsTotalText: F.fmtMoney(cents / 100),
      deliveryFeeText: F.fmtMoney(o.deliveryFee || 0),
      amountText: F.fmtMoney(o.amount),
      timeText: F.fmtTimeFull(o.orderTime),
      payText: F.payStatusText(o.payStatus),
      remark: o.remark || '',
      canPay: status === 1,
      canRemind: status === 2 || status === 3,
      canCancel: status === 1 || status === 2,
      canRepeat: status === 5 || status === 6
    });
  },

  /* ---------- 操作 ---------- */
  /** 复制订单号 */
  onCopyNumber() {
    if (!this.data.order) return;
    wx.setClipboardData({
      data: this.data.order.number,
      success: () => wx.showToast({ title: '单号已复制', icon: 'success' })
    });
  },

  /** 去支付：两步支付（payment 生成参数 → paySuccess 模拟回调），成功后刷新详情 */
  onPay() {
    if (!this.data.order || this._paying) return;
    this._paying = true;
    wx.showLoading({ title: '支付中', mask: true });
    request({
      url: '/user/order/payment',
      method: 'PUT',
      data: { orderNumber: this.data.order.number, payMethod: 1 }
    })
      .then(() => request({
        url: '/user/order/paySuccess',
        method: 'PUT',
        query: { orderNumber: this.data.order.number }
      }))
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '支付成功', icon: 'success' });
        this.load();
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '支付失败', icon: 'none' });
      })
      .finally(() => {
        this._paying = false;
      });
  },

  /** 催单：GET /user/order/reminder/{id} */
  onRemind() {
    request({ url: '/user/order/reminder/' + this.data.id })
      .then(() => wx.showToast({ title: '已提醒商家，请稍候', icon: 'none' }))
      .catch((err) => wx.showToast({ title: err.message || '催单失败', icon: 'none' }));
  },

  /** 取消：确认弹窗 → PUT /user/order/cancel/{id} → 刷新 */
  onCancel() {
    wx.showModal({
      title: '取消订单',
      content: '确定要取消这个订单吗？',
      confirmColor: '#B5432F',
      success: (res) => {
        if (!res.confirm) return;
        request({ url: '/user/order/cancel/' + this.data.id, method: 'PUT' })
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'success' });
            this.load();
          })
          .catch((err) => wx.showToast({ title: err.message || '取消失败', icon: 'none' }));
      }
    });
  },

  /** 再来一单：POST /user/order/repetition/{id}（明细原样插回购物车）→ 回菜单页 */
  onRepeat() {
    if (this._repeating) return;
    this._repeating = true;
    wx.showLoading({ title: '加入购物车', mask: true });
    request({ url: '/user/order/repetition/' + this.data.id, method: 'POST' })
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '已按原样加入购物车', icon: 'success' });
        setTimeout(() => wx.switchTab({ url: '/pages/menu/menu' }), 500);
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '操作失败', icon: 'none' });
      })
      .finally(() => {
        this._repeating = false;
      });
  }
});
