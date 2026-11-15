/**
 * pages/order-list/order-list.js —— 我的订单（tab 页）
 *
 * 本页职责：
 *   顶部状态 tab（全部/待付款/待接单/制作中/待取餐·派送中/已完成）→ historyOrders 的 status 参数；
 *   分页加载（触底加载更多 + 下拉刷新）；订单卡：状态彩色文案 / 品项摘要 / 金额 /
 *   自取显示取餐号 / 外送显示收货摘要 / 未支付徽标；
 *   卡片操作：待付款→去支付；待接单→催单 + 取消；点击卡片→订单详情。
 *
 * 涉及接口：
 *   GET  /user/order/historyOrders?page&pageSize&status → PageResult{ total, records }（OrderVO 含 orderDetailList）
 *   GET  /user/order/reminder/{id}    → 催单（仅 2 待接单 / 3 制作中）
 *   PUT  /user/order/cancel/{id}      → 取消（仅 1 待付款 / 2 待接单）
 *   PUT  /user/order/payment          → 支付第一步（生成支付参数，不改状态）
 *   PUT  /user/order/paySuccess?orderNumber= → 支付第二步（模拟回调：payStatus→1、1→2）
 *
 * 新手要点：
 *   1. 「全部」不传 status —— request.js 的 buildQuery 对空参数自动不拼接（专为这里设计）
 *   2. 分页判据：本页每次请求 pageSize 条，返回不足 pageSize 视为没有更多
 *   3. 本页是 tabBar 页：页面内跳转登录用 reLaunch（redirectTo 从 tab 页跳非 tab 页会打乱返回栈），
 *      跳订单详情用 navigateTo，回菜单/我的用 switchTab
 *   4. 支付两步走（苍穹模式）：payment 只生成参数，paySuccess 模拟支付成功回调
 */
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');
const aiBall = require('../../utils/ai-ball');

const PAGE_SIZE = 10;

/**
 * 顶部 tab 定义（label → 契约 status 参数）
 * 1 待付款 / 2 待接单 / 3 已接单·制作中 / 4 待取餐(自取)·派送中(外送) / 5 已完成
 */
const TABS = [
  { label: '全部', status: '' },
  { label: '待付款', status: 1 },
  { label: '待接单', status: 2 },
  { label: '制作中', status: 3 },
  { label: '待取餐/派送中', status: 4 },
  { label: '已完成', status: 5 }
];

/** 品项摘要：单品 →「名称（规格）×N」，多品 →「首个名称 等 N 件商品」 */
function buildSummary(details) {
  const list = details || [];
  if (!list.length) return '暂无品项';
  const totalNumber = list.reduce((s, d) => s + (Number(d.number) || 0), 0);
  const first = list[0];
  if (list.length === 1) {
    const spec = F.parseSpecText(first.specSelected || first.drinkFlavor);
    return first.name + (spec ? '（' + spec + '）' : '') + ' ×' + totalNumber;
  }
  return first.name + ' 等 ' + totalNumber + ' 件商品';
}

Page({
  data: {
    tabs: TABS,
    tabIndex: 0,        // 当前 tab 下标
    records: [],        // 订单卡列表（已加工展示字段）
    page: 1,
    hasMore: true,
    loading: false
  },

  onShow() {
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      // 每次进入都重新拉第一页（下单/支付/取消后回到本页能看到最新状态）
      this.reload();
    });
  },

  /** 当前 tab 的 status 参数（「全部」为 ''，buildQuery 会自动跳过） */
  currentStatus() {
    return this.data.tabs[this.data.tabIndex].status;
  },

  /* ---------- 数据加载 ---------- */
  /** 重置到第一页重新加载（切 tab / 下拉刷新 / 操作后刷新共用） */
  reload() {
    this.setData({ page: 1, records: [], hasMore: true });
    return this.fetchPage(1);
  },

  fetchPage(page) {
    if (this.data.loading) return Promise.resolve();
    this.setData({ loading: true });
    return request({
      url: '/user/order/historyOrders',
      query: { page, pageSize: PAGE_SIZE, status: this.currentStatus() }
    })
      .then((res) => {
        const fetched = (res && res.records) || [];
        const cards = fetched.map((o) => this.mapCard(o));
        this.setData({
          records: page === 1 ? cards : this.data.records.concat(cards),
          page,
          hasMore: fetched.length >= PAGE_SIZE,
          loading: false
        });
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载订单失败', icon: 'none' });
      });
  },

  /** OrderVO → 订单卡展示模型（状态文案/颜色来自 format.js 的 STATUS_MAP 口径） */
  mapCard(o) {
    const status = Number(o.status);
    return {
      ...o,
      statusText: F.statusText(o.status, o.deliveryMode),
      statusColor: F.statusColor(o.status),
      timeText: F.fmtTimeShort(o.orderTime),
      amountText: F.fmtMoney(o.amount),
      summary: buildSummary(o.orderDetailList),
      isPickup: Number(o.deliveryMode) !== 2,
      addrText: o.consignee ? o.consignee + ' ' + (o.phone || '') + ' · ' + (o.address || '') : '',
      unpaid: Number(o.payStatus) === 0 && status === 1, // 待付款徽标（已取消未支付不再提示）
      canPay: status === 1,
      canAct: status === 2 // 待接单：催单 + 取消
    };
  },

  /* ---------- 交互 ---------- */
  onTabTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.tabIndex) return;
    this.setData({ tabIndex: index });
    this.reload();
  },

  onCardTap(e) {
    wx.navigateTo({ url: '/pages/order-detail/order-detail?id=' + e.currentTarget.dataset.id });
  },

  /** 去支付：两步支付（payment 生成参数 → paySuccess 模拟回调），成功后刷新列表 */
  onPay(e) {
    const { id, number } = e.currentTarget.dataset;
    wx.showLoading({ title: '支付中', mask: true });
    request({ url: '/user/order/payment', method: 'PUT', data: { orderNumber: number, payMethod: 1 } })
      .then(() => request({ url: '/user/order/paySuccess', method: 'PUT', query: { orderNumber: number } }))
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '支付成功', icon: 'success' });
        this.reload();
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '支付失败', icon: 'none' });
      });
  },

  /** 催单：GET /user/order/reminder/{id}（后端校验仅 2/3 状态可催） */
  onRemind(e) {
    request({ url: '/user/order/reminder/' + e.currentTarget.dataset.id })
      .then(() => wx.showToast({ title: '已提醒商家，请稍候', icon: 'none' }))
      .catch((err) => wx.showToast({ title: err.message || '催单失败', icon: 'none' }));
  },

  /** 取消：确认弹窗 → PUT /user/order/cancel/{id}（后端校验仅 1/2 状态可取消） */
  onCancel(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '取消订单',
      content: '确定要取消这个订单吗？',
      confirmColor: '#B5432F',
      success: (res) => {
        if (!res.confirm) return;
        request({ url: '/user/order/cancel/' + id, method: 'PUT' })
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'success' });
            this.reload();
          })
          .catch((err) => wx.showToast({ title: err.message || '取消失败', icon: 'none' }));
      }
    });
  },

  /* ---------- 分页触底 / 下拉刷新 ---------- */
  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) return;
    this.fetchPage(this.data.page + 1);
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh());
  },

  /* ---------- AI 悬浮球：滑动隐藏 · 静置 2 秒再现 ---------- */
  onPageScroll() {
    aiBall.onPageScroll(this);
  }
});
