/**
 * pages/checkin/checkin.js —— F5 每日签到（简化版）
 *
 * 本页职责：
 *   统计条三格卡片（连续签到 / 累计签到 / 当前积分，衬线大数字）；
 *   大按钮签到：POST sign → toast「签到成功 +5 积分」→ 刷新 info；
 *   规则说明卡。（原当月签到日历已按需求简化移除）
 *
 * 涉及接口：
 *   GET  /user/checkin/info  → CheckinVO{ signedDates, streak, totalDays, todaySigned, points }
 *   POST /user/checkin/sign  → CheckinResultVO{ ..., todaySigned:true, gained:5, points }
 *                              （重复签到 reject「今日已签到，明天再来哦」）
 *
 * 新手要点（写 Java 时对照）：
 *   1. 签到状态在真实后端用 Redis BITMAP 存：key = checkin:{userId}:{yyyy}，
 *      一年 366 天 = 366 个 bit ≈ 46 字节，SETBIT/GETBIT 的 offset 用「年内第几天」，
 *      比建 366 行签到记录表省内存、查询也快——mock-v2.js 用 yyyy-MM-dd 数组模拟同口径
 *   2. 重复签到判重：后端先 GETBIT（或看 SETBIT 的返回旧值）判断当天已签则抛业务异常；
 *      前端 _signing 防重复点击标志是第二道保险，两台设备同时点时以 Redis 为准
 *   3. 连签 streak 算法：从今天（已签）或昨天起往前数连续已签的天数，
 *      JS 版见 mock-v2.js 的 calcStreak，Java 里可循环右移 BITFIELD 位段实现
 */
const auth = require('../../utils/auth');
const aiBall = require('../../utils/ai-ball');
const { request } = require('../../utils/request');

Page({
  data: {
    loading: true,
    /* 统计条 */
    streak: 0,
    totalDays: 0,
    points: 0,
    /* 签到按钮 */
    todaySigned: false,
    signBtnText: ''
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
    request({ url: '/user/checkin/info' })
      .then((info) => {
        this.setData({ loading: false });
        this.render(info || {});
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载签到信息失败', icon: 'none' });
      });
  },

  /** CheckinVO → 页面展示模型（统计条一次性算好） */
  render(info) {
    const todaySigned = !!info.todaySigned;
    this.setData({
      streak: Number(info.streak) || 0,
      totalDays: Number(info.totalDays) || 0,
      points: Number(info.points) || 0,
      todaySigned,
      signBtnText: todaySigned ? '今日已签到 · 明日再来' : '签到 · 得 5 积分'
    });
  },

  /* ---------- 交互 ---------- */
  /** 签到：POST /user/checkin/sign → toast +5 积分 → 刷新（_signing 防重复点击） */
  onSign() {
    if (this._signing) return;
    if (this.data.todaySigned) {
      wx.showToast({ title: '今日已签到，明天再来哦', icon: 'none' });
      return;
    }
    this._signing = true;
    request({ url: '/user/checkin/sign', method: 'POST' })
      .then(() => {
        wx.showToast({ title: '签到成功 +5 积分', icon: 'success' });
        this.load();
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '签到失败，请稍后再试', icon: 'none' });
      })
      .finally(() => {
        this._signing = false;
      });
  },

  /* ---------- AI 悬浮球：滑动隐藏 · 静置 2 秒再现 ---------- */
  onPageScroll() {
    aiBall.onPageScroll(this);
  }
});
