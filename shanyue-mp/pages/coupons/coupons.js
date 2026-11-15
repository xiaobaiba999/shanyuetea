/**
 * pages/coupons/coupons.js —— F4 券包（优惠券领取 / 使用）
 *
 * 本页职责：
 *   三个胶囊 tab 客户端过滤：可领取（!received）/ 可使用（received && status==1）/
 *   已使用·过期（received && status 2|3）；
 *   票据式券卡：左票根竖条（满减衬线大字「¥N」或折扣「N折」+ 门槛小字 +
 *   有效期小字 + 右缘虚线 + 打孔凹口），右侧（券名衬线 + 使用条件 + 有效期至 +
 *   秒杀小标与库存进度条 + 操作按钮）；
 *   已使用/过期卡整体降透明度 + 右上角 -12deg 陶红描边印章；
 *   领取流程：POST receive → toast 领取成功 → 刷新并切到「可使用」tab。
 *
 * 涉及接口：
 *   GET  /user/coupon/list          → CouponVO[]（模板 + 领取状态，status 0未领取|1可用|2已使用|3已过期）
 *   POST /user/coupon/receive/{id}  → null；失败 reject：
 *                                    「您已领取过该券，一人限领一张」/「手慢了，本场秒杀券已抢光」
 *
 * 新手要点（写 Java 时对照）：
 *   1. 一人一单：真实后端用 Redis SETNX user:coupon:{userId}:{couponId} 抢防重锁，
 *      抢到锁才允许插 user_coupon 表——mock 里 userCoupons 查重模拟同语义
 *   2. 秒杀防超卖：remain-- 不能「先查库存再扣减」（并发下会超卖），要用 Lua 脚本把
 *      「查库存 + 扣减」原子化（DECR 到负数即回滚报「已抢光」）——对应本页「抢券」按钮
 *   3. 用券发生在下单事务里：校验门槛 → 算抵扣 → 置已使用（见 mock-v2.js 的 applyCoupon），
 *      本页「去使用」只负责 switchTab 跳去点单页选饮品
 *   4. 券卡全部展示串（大字 / 门槛 / 库存百分比 / 按钮文案）都在 mapCard 里预计算，
 *      WXML 零函数调用；_receiving 防重复点击与后端 SETNX 互为双保险
 */
const auth = require('../../utils/auth');
const aiBall = require('../../utils/ai-ball');
const { request } = require('../../utils/request');

/** tab 定义（label + 过滤 key；过滤纯客户端做） */
const TABS = [
  { key: 'available', label: '可领取' },
  { key: 'usable', label: '可使用' },
  { key: 'used', label: '已使用·过期' }
];

/** 各 tab 空态文案 */
const EMPTY_TEXT = {
  available: '暂无可领取的券，常来山月坐坐',
  usable: '暂无可使用的券',
  used: '还没有已使用或过期的券'
};

Page({
  data: {
    loading: true,
    tabs: TABS,
    tabIndex: 0,
    list: [],                                  // 当前 tab 的券卡（已加工展示模型）
    emptyText: EMPTY_TEXT.available
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
    request({ url: '/user/coupon/list' })
      .then((list) => {
        this.setData({ loading: false });
        this._raw = list || [];                // 原始列表挂实例上，不进 data（不直接渲染）
        this.applyFilter();
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载券包失败', icon: 'none' });
      });
  },

  /** 按 tab 过滤（可领取 !received / 可使用 received&&status==1 / 已使用·过期 status 2|3） */
  applyFilter() {
    const key = TABS[this.data.tabIndex].key;
    const filtered = (this._raw || []).filter((c) => {
      const received = !!c.received;
      const status = Number(c.status);
      if (key === 'available') return !received;
      if (key === 'usable') return received && status === 1;
      return received && (status === 2 || status === 3);
    });
    this.setData({
      list: filtered.map((c) => this.mapCard(c)),
      emptyText: EMPTY_TEXT[key]
    });
  },

  /**
   * CouponVO → 券卡展示模型
   * 左票根大字：满减「¥8」/ 折扣「8.8折」（88 折扣率换算 /10）；
   * 秒杀库存百分比、按钮文案、印章文案全部预计算。
   */
  mapCard(c) {
    const type = Number(c.type);               // 1 满减 | 2 折扣 | 3 秒杀满减
    const threshold = Number(c.thresholdAmount) || 0;
    const received = !!c.received;
    const status = Number(c.status);
    const used = received && (status === 2 || status === 3);
    const seckill = !!c.seckill;
    const validDays = Number(c.validDays) || 0;
    const discountAmount = Number(c.discountAmount) || 0;
    const stock = Number(c.stock) || 0;
    const remain = Number(c.remain) || 0;

    /* 左侧票根大字 */
    let mainPrefix = '';
    let mainNum = '';
    let mainSuffix = '';
    if (type === 2) {
      mainNum = String((Number(c.discountRate) || 100) / 10);  // 88 -> '8.8'
      mainSuffix = '折';
    } else {
      mainPrefix = '¥';
      mainNum = String(discountAmount);
    }

    return {
      id: c.id,
      name: c.name,
      seckill,
      received,
      used,
      stampText: status === 3 ? '已过期' : '已使用',
      /* 左票根 */
      mainPrefix,
      mainNum,
      mainSuffix,
      thresholdText: threshold > 0 ? '满' + threshold + '可用' : '无门槛',
      validShort: received && c.expireDate
        ? '至 ' + c.expireDate
        : (validDays ? '领后' + validDays + '天' : ''),
      /* 右侧信息 */
      conditionText: type === 2
        ? '全场饮品 ' + mainNum + ' 折 · 无门槛'
        : '满 ' + threshold + ' 元可用 · 立减 ' + discountAmount + ' 元',
      expireText: received
        ? '有效期至 ' + (c.expireDate || '—')
        : '领取后 ' + validDays + ' 天内有效',
      /* 秒杀库存（进度条宽度百分比 + 文案） */
      stockPercent: stock > 0 ? Math.round((remain / stock) * 100) : 0,
      stockText: '仅剩 ' + remain + ' / ' + stock + ' 张',
      /* 操作：receive 领取 | use 去使用 | none 已使用/过期 */
      action: used ? 'none' : (received ? 'use' : 'receive'),
      receiveText: seckill ? '抢券' : '领取'
    };
  },

  /* ---------- 交互 ---------- */
  onTabTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.tabIndex) return;
    this.setData({ tabIndex: index });
    this.applyFilter();
  },

  /** 领取 / 抢券：POST /user/coupon/receive/{id} → toast + 刷新并切到「可使用」 */
  onReceive(e) {
    if (this._receiving) return;
    const id = e.currentTarget.dataset.id;
    this._receiving = true;
    wx.showLoading({ title: '领取中', mask: true });
    request({ url: '/user/coupon/receive/' + id, method: 'POST' })
      .then(() => {
        wx.hideLoading();
        wx.showToast({ title: '领取成功', icon: 'success' });
        this.setData({ tabIndex: 1 });         // 切到「可使用」看新到手的券
        this.load();
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: err.message || '领取失败', icon: 'none' });
      })
      .finally(() => {
        this._receiving = false;
      });
  },

  /** 去使用：跳点单页选饮品（真正用券在下单事务里按门槛校验抵扣） */
  onUse() {
    wx.switchTab({ url: '/pages/menu/menu' });
  },

  /* ---------- AI 悬浮球：滑动隐藏 · 静置 2 秒再现 ---------- */
  onPageScroll() {
    aiBall.onPageScroll(this);
  }
});
