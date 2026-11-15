/**
 * pages/level/level.js —— F2 会员等级（简化版）
 *
 * 本页职责：
 *   GET /user/member/level 拉取 MemberLevelVO；
 *   顶部等级英雄卡（pine 深色渐变底：左侧大衬线等级名 + Lv 徽标 + title，
 *   右侧成长值衬线大数字，卡内 gold 进度条 + 「距下一级还差 N 成长值」文案）；
 *   当前等级权益卡（前置小金点逐条）；
 *   成长值规则卡（下单 / 签到两行）。（原五级时间轴已按需求简化移除）
 *
 * 涉及接口：
 *   GET /user/member/level → MemberLevelVO
 *     { level, levelName, levelTitle, growth,
 *       nextLevel: {level, name, minGrowth} | null, remainGrowth, progress(0-100),
 *       privileges: ['签到积分翻倍（+10/天）', ...],
 *       levels: [ {level, name, title, minGrowth, privileges[]} ... 共 5 级 ],
 *       growthRules: [ {text, icon} ... ] }
 *
 * 新手要点（面向 Java 学习者）：
 *   1. 会员等级在服务端有两种落库方案：
 *      ① 聚合计算——每次查询实时 SUM（已支付订单金额×2 + 签到天数×5）
 *        再套等级阈值表，数据永远新鲜但每次都要算（本项目 mock 即此方案 memberLevelView）；
 *      ② 冗余字段——user 表直接存 growth 列，各行为落库时同步累加（读快，
 *        但要注意并发加锁 / 乐观锁防漏加）。两种方案接口返回结构一致，前端无感知
 *   2. 等级判定是「区间」问题：growth >= minGrowth 从 Lv5 往回扫第一个命中即当前级；
 *      remainGrowth = 下一级阈值 - 当前成长值；progress 已由后端算好 0-100 整数
 *   3. 本页纯只读：没有任何 POST。成长值变化来自下单 / 签到行为，
 *      回到本页 onShow 重新拉取即是最新值（不用本地缓存）
 */
const auth = require('../../utils/auth');
const aiBall = require('../../utils/ai-ball');
const { request } = require('../../utils/request');

/** growthRules.icon → 圆徽记内单字（不用 emoji，衬线单字更有印章味） */
const ICON_CHARS = { order: '单', checkin: '签' };

Page({
  data: {
    loading: true,
    /* ---- 英雄卡展示模型 ---- */
    levelBadge: '',        // 'Lv2'
    levelName: '',        // '青绿'
    levelTitle: '',       // '抽枝'
    growth: 0,            // 成长值大数字
    progress: 0,          // 进度条宽度（0-100，绑 style width）
    remainText: '',       // 距「松风」还差 292 成长值 / 已是最高等级 · 入盏知味
    /* ---- 列表展示模型 ---- */
    privileges: [],       // 当前等级权益（字符串数组）
    growthRules: []       // 成长值规则（含 iconChar 展示字）
  },

  onShow() {
    // 登录守卫：未登录统一踢回登录页（auth.ensureLogin 不做静默登录）
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
    request({ url: '/user/member/level' })
      .then((vo) => {
        this.setData({ loading: false });
        this.render(vo);
      })
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message || '加载等级失败', icon: 'none' });
      });
  },

  /** MemberLevelVO → 页面展示模型（所有展示串 / 状态在 JS 预计算） */
  render(vo) {
    // Lv5 满级：nextLevel 为 null，文案切换为「已是最高等级 · 入盏知味」
    const maxed = !vo.nextLevel;
    const remainText = maxed
      ? '已是最高等级 · ' + vo.levelName + vo.levelTitle
      : '距「' + vo.nextLevel.name + '」还差 ' + vo.remainGrowth + ' 成长值';

    const growthRules = (vo.growthRules || []).map((r) => ({
      text: r.text,
      iconChar: ICON_CHARS[r.icon] || '·'
    }));

    this.setData({
      levelBadge: 'Lv' + vo.level,
      levelName: vo.levelName,
      levelTitle: vo.levelTitle,
      growth: vo.growth,
      remainText,
      progress: Math.max(0, Math.min(100, Number(vo.progress) || 0)),
      privileges: vo.privileges || [],
      growthRules
    });
  },

  /* ---------- AI 悬浮球：滑动隐藏 · 静置 2 秒再现 ---------- */
  onPageScroll() {
    aiBall.onPageScroll(this);
  }
});
