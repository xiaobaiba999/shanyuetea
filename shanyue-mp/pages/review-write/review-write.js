/**
 * pages/review-write/review-write.js —— F6 写评价
 *
 * 本页职责：
 *   onLoad(options.orderId) 进页；onShow 登录守卫；
 *   GET /user/order/orderDetail/{id} 拉订单 → 「评价对象卡」
 *   （订单号 + 下单时间 + 品项列表，specSelected 是 JSON 字符串快照，
 *   用 F.parseSpecText 解析成「大杯·冰·半糖」，与 order-detail.js 同一套口径）；
 *   星级选择（5 颗大星，默认 5 星，选中金心 ★ / 未选空心 ☆，随分值切文案）；
 *   印象标签多选 chips；120 字文字评价（右下字数统计）；
 *   POST /user/review/submit 提交 → 成功弹窗「+10 山月积分已到账」→ navigateBack。
 *
 * 涉及接口：
 *   GET  /user/order/orderDetail/{id} → OrderVO（既有接口）
 *        含 orderDetailList: [{ name, number, amount, specSelected(JSON字符串) }]、status、orderTime
 *   POST /user/review/submit → { pointsGained: 10, points }
 *        body: { orderId, rating, content, tags }
 *        失败口径：'订单完成后才能评价' / '该订单已评价过啦，一单一评'
 *
 * 新手要点（面向 Java 学习者）：
 *   1. 一单一评由后端校验（reviewedOrderIds 唯一记录 / 评价表 order_id 唯一索引），
 *      前端只做 _submitting 防重复提交锁；重复评价会收到后端错误 toast——演示口径
 *   2. 评价奖励 +10 积分要在后端事务里加（写评价 + 加积分要么都成功要么都不发生），
 *      成长值 +10 同理（见 mock-v2.js 的 submit 分支）
 *   3. 星级 / 标签 / 文字都是前端本地状态，提交时一次性 POST；
 *      rating 的口水文案（'封神之作'…）是前端字典，不用发请求
 *   4. specSelected 是下单时刻的规格 JSON 快照（防止之后商品改规格影响历史订单展示），
 *      解析工具直接复用 utils/format.js 的 parseSpecText——require 即用，不要重写
 *   5. textarea 字数统计在 bindinput 里用 e.detail.value 同步（maxlength 120 由组件兜底）
 */
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

/** 印象标签候选池（多选；后端 submit 也按 6 个截断） */
const TAG_POOL = ['回甘明显', '香气足', '出餐快', '颜值高', '甜度刚好', '分量足', '口感顺滑', '会回购'];

/** rating → 口径文案 */
const RATING_TEXTS = { 1: '一言难尽', 2: '尚可', 3: '还不错', 4: '很满意', 5: '封神之作' };

/** 生成 5 颗星的展示模型：选中金心 ★ / 未选空心 ☆（JS 预计算，WXML 不做函数运算） */
function buildStars(rating) {
  const stars = [];
  for (let i = 1; i <= 5; i++) stars.push({ n: i, ch: i <= rating ? '★' : '☆' });
  return stars;
}

Page({
  data: {
    orderId: null,
    loading: true,
    order: null,
    /* ---- 评价对象卡 ---- */
    timeText: '',
    details: [],            // [{ id, name, number, specText }]
    /* ---- 星级 ---- */
    rating: 5,              // 默认 5 星
    ratingText: RATING_TEXTS[5],
    stars: buildStars(5),
    /* ---- 印象标签（多选） ---- */
    tags: TAG_POOL.map((t) => ({ tag: t, on: false })),
    /* ---- 文字评价 ---- */
    content: '',
    contentCount: 0,
    /* ---- 提交 ---- */
    submitting: false
  },

  onLoad(options) {
    this.setData({ orderId: (options && options.orderId) || '' });
  },

  onShow() {
    // 登录守卫：未登录统一踢回登录页
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
    request({ url: '/user/order/orderDetail/' + this.data.orderId })
      .then((o) => {
        this.setData({ loading: false });
        this.render(o);
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载订单失败', icon: 'none' });
      });
  },

  /** OrderVO → 评价对象卡展示模型（规格快照解析成「大杯·冰·半糖」） */
  render(o) {
    const details = (o.orderDetailList || []).map((d) => ({
      id: d.id,
      name: d.name,
      number: Number(d.number) || 1,
      specText: F.parseSpecText(d.specSelected || d.drinkFlavor)
    }));
    this.setData({
      order: o,
      timeText: F.fmtTimeFull(o.orderTime),
      details
    });
  },

  /* ---------- 星级选择 ---------- */
  onStarTap(e) {
    const star = Number(e.currentTarget.dataset.star);
    if (!star || star === this.data.rating) return;
    this.setData({
      rating: star,
      ratingText: RATING_TEXTS[star],
      stars: buildStars(star)
    });
  },

  /* ---------- 标签多选 ---------- */
  onTagTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    const tags = this.data.tags.map((t, i) => (
      i === index ? { tag: t.tag, on: !t.on } : t
    ));
    this.setData({ tags });
  },

  /* ---------- 文字评价输入 ---------- */
  onContentInput(e) {
    const val = e.detail.value || '';
    this.setData({ content: val, contentCount: val.length });
  },

  /* ---------- 提交 ---------- */
  onSubmit() {
    // 防重复提交：前端锁（一单一评的真实校验在后端，重复评价收到错误 toast）
    if (this._submitting) return;

    const content = String(this.data.content || '').trim();
    if (content.length < 4) {
      wx.showToast({ title: '写满 4 个字再提交吧', icon: 'none' });
      return;
    }

    this._submitting = true;
    this.setData({ submitting: true });
    const tags = this.data.tags.filter((t) => t.on).map((t) => t.tag);
    request({
      url: '/user/review/submit',
      method: 'POST',
      data: {
        orderId: Number(this.data.orderId),
        rating: this.data.rating,
        content,
        tags
      }
    })
      .then((res) => {
        // +10 积分已由后端入账（事务内），弹窗致谢后返回上一页
        const gained = (res && Number(res.pointsGained)) || 10;
        wx.showModal({
          title: '评价成功',
          content: '感谢分享，+' + gained + ' 山月积分已到账',
          showCancel: false,
          confirmColor: '#2F5233',
          success: () => wx.navigateBack()
        });
      })
      .catch((err) => {
        // 失败口径：'订单完成后才能评价' / '该订单已评价过啦，一单一评'
        wx.showToast({ title: err.message || '提交失败，请稍后再试', icon: 'none' });
      })
      .finally(() => {
        this._submitting = false;
        this.setData({ submitting: false });
      });
  }
});
