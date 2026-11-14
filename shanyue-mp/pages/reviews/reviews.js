/**
 * pages/reviews/reviews.js —— F6 评价墙
 *
 * 本页职责：
 *   头部统计卡（累计评价 total 条 + 客户端由 records 算的平均星级，保留 1 位小数）；
 *   标签筛选行（「全部」+ 热门标签横向滚动 chips，客户端对已加载 records 按 tags 过滤）；
 *   评价卡列表（头像兜底品牌默认头像 / 昵称 / 星级串 / 时间 + 饮品小胶囊 +
 *   正文 + 标签 chips + 底部 ♥ 点赞数，不放分享图标）；
 *   触底分页（page++ 追加 append）、支持 onLoad options.drinkId 传参入口过滤。
 *
 * 涉及接口：
 *   GET /user/review/wall?page&pageSize&drinkId → PageResult{ total, records }
 *     record: { id, userId, nickName, avatarUrl, orderId, drinkId, drinkName,
 *               rating(1-5), content, tags:['回甘明显',...], images:[],
 *               createTime:'yyyy-MM-dd HH:mm', likeCount }
 *   GET /user/review/tags → [{ tag, count }]（按热度降序）
 *
 * 新手要点（面向 Java 学习者）：
 *   1. 评价墙 = 典型「一对多查询 + 分页」：评价表 JOIN 用户表取昵称头像，
 *      ORDER BY create_time DESC + LIMIT 分页；total 用 COUNT(*) 一并返回
 *   2. 标签筛选：演示前端在客户端对「已加载的 records」过滤（切标签不发请求，
 *      体验即时）；真实后端可改成 query 参数（标签表 JOIN 或 tags LIKE），契约不变前端免改
 *   3. 平均星级由前端用 records 算（toFixed(1) 保留 1 位）；
 *      也可以让后端 AVG(rating) 聚合返回——两种口径二选一即可
 *   4. 星级串 '★★★★☆' 在 JS 预计算（WXML 不做函数运算）：
 *      rating 个 ★ + (5 - rating) 个 ☆
 *   5. 头像为空统一兜底品牌默认头像图（微信头像授权是可选项，空值必须有兜底）
 */
const F = require('../../utils/format');
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

const PAGE_SIZE = 10;
const DEFAULT_AVATAR = '/images/brand/chaxiaoyue-avatar.jpg';

/** rating → '★★★★☆' 星级串（展示串一律 JS 预计算） */
function starsText(rating) {
  const n = Math.max(0, Math.min(5, Number(rating) || 0));
  let s = '';
  for (let i = 0; i < 5; i++) s += i < n ? '★' : '☆';
  return s;
}

/** record → 评价卡展示模型（头像兜底 / 星级串 / 时间短格式） */
function mapCard(r) {
  return {
    id: r.id,
    nickName: r.nickName || '山月茶咖用户',
    avatar: r.avatarUrl || DEFAULT_AVATAR,
    drinkName: r.drinkName || '',
    rating: Number(r.rating) || 0,
    starsText: starsText(r.rating),
    content: r.content || '',
    tags: r.tags || [],
    timeText: F.fmtTimeShort(r.createTime) || '—',
    likeCount: Number(r.likeCount) || 0
  };
}

Page({
  data: {
    loading: true,
    /* ---- 头部统计 ---- */
    total: 0,
    avgText: '0.0',        // '4.6'
    avgStarsText: '☆☆☆☆☆',
    /* ---- 标签筛选 ---- */
    chips: [],              // [{tag, count}]，「全部」在首位
    activeTag: '全部',
    /* ---- 列表与分页 ---- */
    records: [],            // 当前展示（按 activeTag 过滤后）
    page: 1,
    hasMore: true,
    loadingMore: false
  },

  onLoad(options) {
    // 可选入口：从饮品详情带 drinkId 只看该饮品的评价
    // （request.js 的 buildQuery 对空值自动不拼参数，「全部入口」不传即可）
    this._drinkId = (options && options.drinkId) || '';
    this._all = [];         // 已加载全量评价卡（过滤前）
  },

  onShow() {
    // 登录守卫：未登录统一踢回登录页
    auth.ensureLogin((ok) => {
      if (!ok) {
        auth.gotoLogin();
        return;
      }
      // 每次进入重拉第一页（刚写完评价回来看得到自己的新评价）
      this.reload();
    });
  },

  /* ---------- 数据加载 ---------- */
  /** 重置到第一页：评价墙 + 标签两路请求并行 */
  reload() {
    this.setData({ loading: true, page: 1, hasMore: true });
    this._all = [];
    return Promise.all([this.fetchWall(1), this.fetchTags()])
      .then(() => {
        // 「全部」chip 的计数用墙接口的 total 补上（tags 接口不含「全部」）
        const chips = this.data.chips.map((c) => (
          c.tag === '全部' ? { tag: '全部', count: this.data.total } : c
        ));
        this.setData({ chips });
      })
      .finally(() => this.setData({ loading: false }));
  },

  /** 拉一页评价（page=1 重置，>1 追加 append） */
  fetchWall(page) {
    if (this.data.loadingMore) return Promise.resolve();
    if (page > 1) this.setData({ loadingMore: true });
    return request({
      url: '/user/review/wall',
      query: { page, pageSize: PAGE_SIZE, drinkId: this._drinkId }
    })
      .then((res) => {
        const fetched = (res && res.records) || [];
        const cards = fetched.map(mapCard);
        this._all = page === 1 ? cards : this._all.concat(cards);
        const total = Number(res && res.total) || 0;
        // 平均星级：客户端由已加载 records 计算，保留 1 位小数（'4.6 分'）
        const avg = this._all.length
          ? this._all.reduce((s, r) => s + r.rating, 0) / this._all.length
          : 0;
        this.setData({
          total,
          avgText: avg.toFixed(1),
          avgStarsText: starsText(Math.round(avg)),
          records: this.applyFilter(this._all, this.data.activeTag),
          page,
          hasMore: this._all.length < total,
          loadingMore: false
        });
      })
      .catch((err) => {
        this.setData({ loadingMore: false });
        wx.showToast({ title: err.message || '加载评价失败', icon: 'none' });
      });
  },

  /** 拉热门标签（按热度降序） */
  fetchTags() {
    return request({ url: '/user/review/tags' })
      .then((tags) => {
        const list = [{ tag: '全部', count: null }].concat(
          (tags || []).map((t) => ({ tag: t.tag, count: Number(t.count) || 0 }))
        );
        this.setData({ chips: list });
      })
      .catch((err) => wx.showToast({ title: err.message || '加载标签失败', icon: 'none' }));
  },

  /** 客户端标签过滤：只筛已加载的 records（tags 数组包含该标签才保留） */
  applyFilter(list, tag) {
    if (!tag || tag === '全部') return list;
    return list.filter((r) => (r.tags || []).indexOf(tag) > -1);
  },

  /* ---------- 交互 ---------- */
  /** 切标签：不发请求，纯客户端过滤已加载列表 */
  onChipTap(e) {
    const tag = e.currentTarget.dataset.tag;
    if (tag === this.data.activeTag) return;
    this.setData({
      activeTag: tag,
      records: this.applyFilter(this._all, tag)
    });
  },

  /* ---------- 分页触底 ---------- */
  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return;
    this.fetchWall(this.data.page + 1);
  }
});
