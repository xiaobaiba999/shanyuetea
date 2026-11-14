/**
 * pages/checkin/checkin.js —— F5 每日签到（签到日历）
 *
 * 本页职责：
 *   统计条三格卡片（连续签到 / 累计签到 / 当前积分，衬线大数字）；
 *   当月签到日历（周头「日一二三四五六」、前置空格补齐、已签小金印、
 *   今天墨绿描边加粗 +「今」角标、未来日置灰不可点）；
 *   大按钮签到：POST sign → toast「签到成功 +5 积分」→ 刷新 info；
 *   点击「今天」格子等同点签到按钮；规则说明卡。
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
 *   4. 日历格子（含补位空格、signed/isToday/future、点击分支标记）全部在本文件预计算，
 *      WXML 只做展示、不调任何函数——这是本项目 WXML 的统一写法
 */
const auth = require('../../utils/auth');
const { request } = require('../../utils/request');

/** 周表头（getDay() 0=周日，正好与本数组下标对齐） */
const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六'];

function pad(n) {
  return n < 10 ? '0' + n : String(n);
}

/** Date → 'yyyy-MM-dd'（与后端 CheckinVO.signedDates 的日期口径一致） */
function fmtDate(d) {
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

Page({
  data: {
    loading: true,
    /* 统计条 */
    streak: 0,
    totalDays: 0,
    points: 0,
    /* 日历 */
    monthText: '',
    monthSignedCount: 0,
    weekLabels: WEEK_LABELS,
    cells: [],
    /* 签到按钮（展示串预计算） */
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

  /** CheckinVO → 页面展示模型（统计 + 日历格子一次性算好） */
  render(info) {
    const signedSet = {};
    (info.signedDates || []).forEach((d) => { signedSet[d] = true; });

    const now = new Date();
    const cells = this.buildCells(now, signedSet);
    let monthSignedCount = 0;
    cells.forEach((c) => { if (c.signed) monthSignedCount += 1; });

    const todaySigned = !!info.todaySigned;
    this.setData({
      streak: Number(info.streak) || 0,
      totalDays: Number(info.totalDays) || 0,
      points: Number(info.points) || 0,
      todaySigned,
      signBtnText: todaySigned ? '今日已签到 · 明日再来' : '签到 · 得 5 积分',
      monthText: now.getFullYear() + '年' + (now.getMonth() + 1) + '月',
      monthSignedCount,
      cells
    });
  },

  /**
   * 生成当月日历格子：
   *   前置空格补齐 1 号前的位置；每格携带 { day, dateStr, signed, isToday, future }；
   *   tapFlag 预置点击分支（today 才可签到 / future 置灰 / past 静默忽略）
   */
  buildCells(now, signedSet) {
    const today = fmtDate(now);
    const year = now.getFullYear();
    const month = now.getMonth(); // 0-based
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const firstWeekday = new Date(year, month, 1).getDay();

    const cells = [];
    for (let i = 0; i < firstWeekday; i++) {
      cells.push({ key: 'b' + i, blank: true, day: '', dateStr: '', signed: false, isToday: false, future: false, tapFlag: '' });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const str = year + '-' + pad(month + 1) + '-' + pad(d);
      const isToday = str === today;
      const isFuture = str > today; // yyyy-MM-dd 定长字符串，字典序即时间先后
      cells.push({
        key: str,
        blank: false,
        day: d,
        dateStr: str,
        signed: !!signedSet[str],
        isToday,
        future: isFuture,
        tapFlag: isToday ? 'today' : (isFuture ? 'future' : 'past')
      });
    }
    return cells;
  },

  /* ---------- 交互 ---------- */
  /** 点格子：仅「今天」触发签到；未来日置灰不可点；其余静默忽略 */
  onCellTap(e) {
    const flag = e.currentTarget.dataset.flag;
    if (flag !== 'today') return;
    this.onSign();
  },

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
  }
});
