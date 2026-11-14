/**
 * utils/mock-v2.js —— 功能升级包 v2 演示数据与接口模拟（F1/F2/F4/F5/F6/F7）
 *
 * 由 utils/mock.js 的 route() 在头部委托调用（命中 v2 路径即处理，未命中落回原有路由）。
 * 所有状态挂在 db.v2 命名空间下，老演示库懒迁移自动补齐（ensureV2）。
 *
 * ┌────────────────────────── v2 API 契约（与 Java 接口桩一一对应） ─────────────────────────┐
 * │ F1 轮播横幅   GET  /user/banner/list                → BannerVO[]                        │
 * │ F2 会员等级   GET  /user/member/level               → MemberLevelVO                    │
 * │ F4 券包       GET  /user/coupon/list                → CouponVO[]（含领取/使用状态）      │
 * │               POST /user/coupon/receive/{id}        → null（一人一单：重复领取报错）      │
 * │               GET  /user/coupon/usable?amount=      → UsableCouponVO[]（下单页选券用）   │
 * │               （用券在下单事务里做：submit 带 userCouponId，见 mock.js 的 applyCoupon 挂钩）│
 * │ F5 每日签到   GET  /user/checkin/info               → CheckinVO                        │
 * │               POST /user/checkin/sign               → CheckinResultVO                  │
 * │ F6 评价墙     GET  /user/review/wall?page&pageSize&drinkId → PageResult{total,records} │
 * │               GET  /user/review/tags                → [{tag, count}]                   │
 * │               POST /user/review/submit              → {pointsGained, points}           │
 * │ F7 AI 点单    GET  /user/ai/suggests                → string[]（快捷提问 chips）         │
 * │               POST /user/ai/order                   → AiOrderVO{reply, recommend}      │
 * │ F8 取餐推送   不走 REST —— utils/pickup-ws.js（WebSocket，type=3 取餐进度）            │
 * └──────────────────────────────────────────────────────────────────────────────────────────┘
 *
 * 联调口径（写 Java 时照此实现，前端 config.js USE_MOCK 改 false 即无缝切换）：
 *  - 签到：连续签到 streak 从今天（已签）或昨天往前数连续日期；积分 +5/天（写 db.user.points）
 *  - 用券：下单接口事务内校验门槛 → 抵扣 → 置已使用（一人一单 + 防超卖是 Java 练习重点）
 *  - 评价：仅「已完成 status=5」订单可评，一单一评；评价奖励 +10 积分
 *  - 成长值：下单每 1 元 +2、签到 +5、评价 +10；等级阈值 0/200/600/1200/2000（Lv1~Lv5）
 *  - AI：POST /user/ai/order 请求体 { message, history, lastRecommendId }；
 *        history 是最近几轮对话 [{role:'user'|'assistant', content}]，真实后端拼进大模型上下文
 */

/* ============================== 种子数据 ============================== */

/** Lv1~Lv5 等级定义（名称沿用品牌语言：月白→青绿→松风→山岚→入盏；权益均为虚拟口径，无实物周边） */
const LEVELS = [
  { level: 1, name: '月白', title: '新芽', minGrowth: 0, privileges: ['注册即享每日签到赚积分', '生日月饮品券一张'] },
  { level: 2, name: '青绿', title: '抽枝', minGrowth: 200, privileges: ['签到积分翻倍（+10/天）', '会员券包抢先领'] },
  { level: 3, name: '松风', title: '亭亭', minGrowth: 600, privileges: ['高峰期优先出餐通道', '每月专属折扣券一张'] },
  { level: 4, name: '山岚', title: '成林', minGrowth: 1200, privileges: ['新品优先试饮资格', '免配送费特权'] },
  { level: 5, name: '入盏', title: '知味', minGrowth: 2000, privileges: ['专属茶席活动邀请', '专属客服通道'] }
];

/** 演示轮播横幅（image 为空 → 前端按 theme 渲染新中式渐变卡；后端接管理端后可回传图片 URL） */
const SEED_BANNERS = [
  {
    id: 1, title: '桂花冰酿 · 秋日上新', subtitle: '第二杯半价 · 限时一周', theme: 'pine',
    image: '', linkType: 'page', linkUrl: '/pages/menu/menu', sort: 1, status: 1
  },
  {
    id: 2, title: '每日签到 · 茶香不断', subtitle: '连签 7 天额外得 50 积分', theme: 'gold',
    image: '', linkType: 'page', linkUrl: '/pages/checkin/checkin', sort: 2, status: 1
  },
  {
    id: 3, title: '新人见面礼', subtitle: '满 30 减 8 券包待领取', theme: 'terracotta',
    image: '', linkType: 'page', linkUrl: '/pages/coupons/coupons', sort: 3, status: 1
  }
];

/** 券模板（type: 1 满减 | 2 折扣 | 3 限时秒杀满减；秒杀卷带库存演示一人一单+防超卖） */
const SEED_COUPONS = [
  { id: 1, name: '新人礼·满30减8', type: 1, thresholdAmount: 30, discountAmount: 8, discountRate: 100, totalDays: 30, seckill: false, stock: 0, remain: 0 },
  { id: 2, name: '周五秒杀·满20减10', type: 1, thresholdAmount: 20, discountAmount: 10, discountRate: 100, totalDays: 7, seckill: true, stock: 100, remain: 37 },
  { id: 3, name: '会员日·全场88折', type: 2, thresholdAmount: 0, discountAmount: 0, discountRate: 88, totalDays: 15, seckill: false, stock: 0, remain: 0 },
  { id: 4, name: '老友回归·满50减15', type: 1, thresholdAmount: 50, discountAmount: 15, discountRate: 100, totalDays: 30, seckill: false, stock: 0, remain: 0 }
];

/** 他人评价种子（评价墙初始氛围；本人评价由 submit 接口追加在最前） */
const SEED_REVIEWS = [
  { id: 101, userId: 21, nickName: '月下独酌', avatarUrl: '', orderId: null, drinkId: 101, drinkName: '生椰拿铁', rating: 5, content: '椰香很正，不齁甜，下午的续命全靠它。', tags: ['椰香浓', '出餐快'], images: [], createTime: -1, likeCount: 23 },
  { id: 102, userId: 22, nickName: '山间客', avatarUrl: '', orderId: null, drinkId: 106, drinkName: '黑糖波波奶茶', rating: 5, content: '波波弹牙，黑糖挂壁看着就治愈，大杯满足。', tags: ['分量足', '会回购'], images: [], createTime: -2, likeCount: 18 },
  { id: 103, userId: 23, nickName: '白鹭未眠', avatarUrl: '', orderId: null, drinkId: 102, drinkName: '桂花冰酿拿铁', rating: 4, content: '桂花香是真实桂花的那种香，就是杯子小了点。', tags: ['香气足'], images: [], createTime: -3, likeCount: 9 },
  { id: 104, userId: 24, nickName: '南山南', avatarUrl: '', orderId: null, drinkId: 104, drinkName: '美式咖啡', rating: 5, content: '双份浓缩够劲，加班救星，性价比无敌。', tags: ['提神醒脑', '性价比'], images: [], createTime: -4, likeCount: 31 },
  { id: 105, userId: 25, nickName: '青梅煮酒', avatarUrl: '', orderId: null, drinkId: 107, drinkName: '茉莉奶绿', rating: 4, content: '茶底清清爽爽，饭后喝刚好，回甘明显。', tags: ['回甘明显', '清爽解腻'], images: [], createTime: -6, likeCount: 12 },
  { id: 106, userId: 26, nickName: '拾月', avatarUrl: '', orderId: null, drinkId: 103, drinkName: '焦糖玛奇朵', rating: 5, content: '层次做得很好，甜度选半糖刚刚好，颜值也在线。', tags: ['颜值高', '甜度刚好'], images: [], createTime: -8, likeCount: 15 },
  { id: 107, userId: 27, nickName: '迟迟', avatarUrl: '', orderId: null, drinkId: 105, drinkName: '澳白', rating: 5, content: '奶泡绵密入口很顺，热饮出品稳定。', tags: ['口感顺滑'], images: [], createTime: -10, likeCount: 7 },
  { id: 108, userId: 28, nickName: '雾岛', avatarUrl: '', orderId: null, drinkId: 101, drinkName: '生椰拿铁', rating: 4, content: '冰的更好喝，建议选去冰再少一档糖。', tags: ['椰香浓'], images: [], createTime: -12, likeCount: 5 }
];

/** AI 点单快捷提问（chips） */
const AI_SUGGESTS = [
  '睡不着，想喝低因的',
  '来杯今日招牌',
  '刚吃完火锅，解腻一点的',
  '预算 15 元以内',
  '天冷了，想喝热的'
];

/* ============================== 工具 ============================== */

function pad(n, len) {
  let s = String(n);
  while (s.length < len) s = '0' + s;
  return s;
}
function dateStr(ts) {
  const d = new Date(ts);
  return d.getFullYear() + '-' + pad(d.getMonth() + 1, 2) + '-' + pad(d.getDate(), 2);
}
function dateTimeStr(ts) {
  const d = new Date(ts);
  return dateStr(ts) + ' ' + pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2);
}
function fenRound(n) {
  return Math.round(Number(n || 0) * 100) / 100;
}
const DAY = 24 * 3600 * 1000;

/** 连续签到天数：从今天（已签）或昨天起往前数连续命中的天数 */
function calcStreak(signedDates, today) {
  const set = {};
  signedDates.forEach((d) => { set[d] = 1; });
  let streak = 0;
  let cur = set[today] ? new Date() : new Date(Date.now() - DAY);
  while (set[dateStr(cur.getTime())]) {
    streak += 1;
    cur = new Date(cur.getTime() - DAY);
  }
  return streak;
}

/* ============================== v2 命名空间 ============================== */

/**
 * 老库懒迁移：没有 db.v2 就补种子（不覆盖已有 v1 数据）。
 * 调用时机：mock.js loadDb() 之后、route() 委托 handle() 之前。
 */
function ensureV2(db) {
  if (db.v2) return db.v2;
  const now = Date.now();
  db.v2 = {
    banners: JSON.parse(JSON.stringify(SEED_BANNERS)),
    // 券模板（remain 为秒杀券实时库存）
    coupons: JSON.parse(JSON.stringify(SEED_COUPONS)),
    // 用户已领的券（status: 1 可用 | 2 已使用 | 3 已过期）
    userCoupons: [
      { id: 91, couponId: 1, status: 1, receiveTime: dateTimeStr(now - 3 * DAY), expireDate: dateStr(now + 27 * DAY) },
      { id: 92, couponId: 4, status: 2, receiveTime: dateTimeStr(now - 12 * DAY), usedTime: dateTimeStr(now - 9 * DAY), expireDate: dateStr(now + 18 * DAY) }
    ],
    couponSeq: 100,
    // 签到记录（yyyy-MM-dd 数组；种子：昨天起连签 3 天 + 更早零散 2 天 → 签到后 streak=4）
    checkin: { signedDates: [dateStr(now - DAY), dateStr(now - 2 * DAY), dateStr(now - 3 * DAY), dateStr(now - 6 * DAY), dateStr(now - 9 * DAY)] },
    // 评价墙 + 本人已评订单号（一单一评）
    reviews: SEED_REVIEWS.map((r) => ({ ...r, createTime: dateTimeStr(now + r.createTime * DAY) })),
    reviewedOrderIds: [],
    reviewSeq: 200,
    // 预留：额外成长值（演示用，默认 0；growth 主体按规则实时计算）
    growthExtra: 0
  };
  return db.v2;
}

/* ============================== 业务计算 ============================== */

/** 成长值 = 已支付订单金额×2 + 累计签到天数×5 + 本人评价数×10 + growthExtra */
function calcGrowth(db) {
  const v2 = ensureV2(db);
  const paidAmount = db.orders
    .filter((o) => Number(o.payStatus) === 1 && Number(o.status) !== 6)
    .reduce((sum, o) => sum + Number(o.amount || 0), 0);
  const checkinDays = v2.checkin.signedDates.length;
  const myReviews = v2.reviews.filter((r) => r.userId === 1).length;
  return Math.round(paidAmount * 2) + checkinDays * 5 + myReviews * 10 + Number(v2.growthExtra || 0);
}

/** 按 LEVELS 阈值算当前等级视图 */
function memberLevelView(db) {
  const growth = calcGrowth(db);
  let cur = LEVELS[0];
  for (let i = LEVELS.length - 1; i >= 0; i--) {
    if (growth >= LEVELS[i].minGrowth) { cur = LEVELS[i]; break; }
  }
  const idx = cur.level - 1;
  const next = idx + 1 < LEVELS.length ? LEVELS[idx + 1] : null;
  const remainGrowth = next ? Math.max(0, next.minGrowth - growth) : 0;
  const span = next ? next.minGrowth - cur.minGrowth : 1;
  const progress = next ? Math.min(100, Math.round(((growth - cur.minGrowth) / span) * 100)) : 100;
  return {
    level: cur.level,
    levelName: cur.name,
    levelTitle: cur.title,
    growth,
    nextLevel: next ? { level: next.level, name: next.name, minGrowth: next.minGrowth } : null,
    remainGrowth,
    progress,
    privileges: cur.privileges,
    levels: LEVELS,
    growthRules: [
      { text: '下单消费 1 元 = 2 成长值', icon: 'order' },
      { text: '每日签到 = 5 成长值', icon: 'checkin' },
      { text: '完成订单评价 = 10 成长值', icon: 'review' }
    ]
  };
}

/** 折扣金额计算（type1 满减固定额 / type2 折扣率，基准为商品金额） */
function calcDiscount(coupon, baseAmount) {
  if (coupon.type === 2) {
    return fenRound(Number(baseAmount) * (100 - coupon.discountRate) / 100);
  }
  return fenRound(coupon.discountAmount);
}

/**
 * 下单用券（mock.js 的 /user/order/submit 内部挂钩调用，与真实后端「下单事务内用券」同口径）：
 * 校验 → 算抵扣 → 置已使用，返回 { discountAmount, name }
 */
function applyCoupon(db, userCouponId, baseAmount) {
  const v2 = ensureV2(db);
  const uc = v2.userCoupons.find((u) => u.id === Number(userCouponId));
  if (!uc) throw new Error('优惠券不存在');
  if (uc.status === 2) throw new Error('该券已使用过啦');
  if (uc.status === 3) throw new Error('该券已过期');
  const coupon = v2.coupons.find((c) => c.id === uc.couponId);
  if (!coupon) throw new Error('券模板不存在');
  if (Number(baseAmount) < Number(coupon.thresholdAmount)) {
    throw new Error('未满足用券门槛（满 ' + coupon.thresholdAmount + ' 元可用）');
  }
  const discount = calcDiscount(coupon, baseAmount);
  uc.status = 2;
  uc.usedTime = dateTimeStr(Date.now());
  return { discountAmount: discount, name: coupon.name };
}

/* ============================== AI 点单模拟 ============================== */

/** 茶小月人设应答：关键词 → 推荐饮品/套餐 + 口吻化回复（真实后端换成大模型生成，见 AiOrderController 桩） */
function aiReply(db, message, lastRecommendId) {
  const msg = String(message || '').trim();
  const drinks = db.drinkes.filter((d) => d.status === 1);
  const pick = (id) => drinks.find((d) => d.id === id);
  const setmeal = db.setmeals.find((s) => s.id === 201);

  const mk = (drink, reason, specSuggestion, reply) => ({
    reply,
    recommend: drink ? {
      kind: 'drink', drinkId: drink.id, setmealId: null, name: drink.name, price: drink.price,
      description: drink.description, monthSales: drink.monthSales, reason, specSuggestion, image: drink.image || ''
    } : null
  });

  // 问候 / 感谢 / 换一个（无推荐或换推荐）
  if (/^(你好|您好|嗨|hi|hello|在吗|早|晚上好)/i.test(msg)) {
    return { reply: '小月参见～今日想喝点什么？说说口味、心情或预算，小月替您参谋。', recommend: null };
  }
  if (/(谢谢|感谢|多谢|辛苦)/.test(msg)) {
    return { reply: '客官慢走～杯壁余温尚在，欢迎常来山月坐坐。', recommend: null };
  }
  if (/(换一个|换换|其他|别的|不想喝这个)/.test(msg)) {
    const alt = drinks
      .filter((d) => d.id !== Number(lastRecommendId))
      .sort((a, b) => b.monthSales - a.monthSales)[0] || drinks[0];
    return mk(alt, '换换口味好主意，这一杯也是店里的常胜将军', specOf(alt.name),
      '好嘞，那小月换一杯为您斟上——' + alt.name + '，月售 ' + alt.monthSales + ' 杯，同样不踩雷。');
  }
  // 预算（「15元以内」「20块」）
  const budget = msg.match(/(\d+)\s*(元|块|rmb|RMB)/);
  if (budget) {
    const limit = Number(budget[1]);
    const fit = drinks.filter((d) => d.price <= limit).sort((a, b) => b.monthSales - a.monthSales);
    if (!fit.length) {
      const cheapest = drinks.slice().sort((a, b) => a.price - b.price)[0];
      return mk(cheapest, '小月翻遍价目表，这是最亲民的一杯', specOf(cheapest.name),
        limit + ' 元以内暂时没有合适的，最实惠的 ' + cheapest.name + ' 只要 ' + cheapest.price + ' 元，小月先替您留意着～');
    }
    const best = fit[0];
    return mk(best, limit + ' 元档里的人气之选', specOf(best.name),
      '明白，小月替您精打细算～' + best.name + ' 只要 ' + best.price + ' 元，月售 ' + best.monthSales + ' 杯，是 ' + limit + ' 元以内的人气之选。');
  }
  // 口味关键词
  if (/(睡不着|睡眠|低因|失眠|晚上|夜里|熬夜)/.test(msg)) {
    const d = pick(107);
    return mk(d, '茶底轻盈、咖啡因含量低，晚间饮用也安心', '中杯 · 冰 · 不另加糖',
      '夜里馋茶又怕睡不好？小月给您备了茉莉奶绿——茶底轻盈，晚上喝也踏实。');
  }
  if (/(解腻|清爽|腻|火锅|油腻|大餐)/.test(msg)) {
    const d = pick(107);
    return mk(d, '茉莉花茶底配轻乳，清爽解腻', '中杯 · 冰 · 标准糖',
      '刚吃完火锅吧～茉莉花茶底配轻乳，一口漱去油腻，正是解腻的好帮手。');
  }
  if (/(招牌|推荐|热销|人气|必点|什么好)/.test(msg)) {
    const d = pick(101);
    return mk(d, '本店人气第一，厚椰乳清冽不腻', '中杯 · 冰 · 半糖',
      '遵命～小月为您斟上本店人气第一的生椰拿铁，厚椰乳清冽不腻，喝过的客官十有八九会回头。');
  }
  if (/(甜|奶茶|珍珠|波波|黑糖|快乐水)/.test(msg)) {
    const d = pick(106);
    return mk(d, '现熬黑糖挂壁，波波弹牙', '大杯 · 冰 · 半糖 + 珍珠',
      '想讨点甜头呀～现熬黑糖挂壁、波波弹牙，这一杯的快乐很扎实。');
  }
  if (/(咖啡|提神|困|加班|醒|美式)/.test(msg)) {
    const d = pick(104);
    return mk(d, '双份浓缩干净纯粹，提神直接', '无规格，可直接加购',
      '困意来袭？双份浓缩的美式最直接，干净纯粹，一口回魂。');
  }
  if (/(拿铁|奶|生椰)/.test(msg)) {
    const d = pick(101);
    return mk(d, '椰香与浓缩的经典相遇', '中杯 · 冰 · 半糖',
      '想喝奶咖呀～小月推荐生椰拿铁，椰香清冽、奶感顺滑，是店里走得最快的一杯。');
  }
  if (/(热|冷|冬天|暖|凉)/.test(msg)) {
    const d = pick(103);
    return mk(d, '热饮最熨帖，香草奶裹焦糖网', '大杯 · 热 · 半糖',
      '天凉了，小月把炉子烧上了～焦糖玛奇朵做热饮最熨帖，暖手又暖心。');
  }
  if (/(套餐|两人|双人|下午茶|朋友|闺蜜|同事)/.test(msg)) {
    return {
      reply: '两位客官呀～山月双人下午茶套餐最合适：两杯生椰拿铁，配黄油可颂与巴斯克芝士蛋糕，39.9 元比单点省 12 元。',
      recommend: {
        kind: 'setmeal', drinkId: null, setmealId: setmeal.id, name: setmeal.name, price: setmeal.price,
        description: setmeal.description, monthSales: 128, reason: '双人出行性价比之选',
        specSuggestion: '套餐无需选规格', image: setmeal.image || ''
      }
    };
  }
  // 兜底：招牌
  const d = pick(101);
  return mk(d, '本店招牌，椰香清冽', '中杯 · 冰 · 半糖',
    '小月猜您今日想试试本店招牌——生椰拿铁，椰香清冽、月售 356 杯，十有八九不踩雷。不合心意的话，再与小月说说口味？');
}

/** 与 seed 规格对应的默认建议（展示用） */
function specOf(name) {
  const map = {
    '生椰拿铁': '中杯 · 冰 · 半糖',
    '桂花冰酿拿铁': '中杯 · 冰 · 半糖',
    '焦糖玛奇朵': '大杯 · 热 · 半糖',
    '美式咖啡': '无规格，可直接加购',
    '澳白': '中杯 · 热',
    '黑糖波波奶茶': '大杯 · 冰 · 半糖 + 珍珠',
    '茉莉奶绿': '中杯 · 冰 · 不另加糖'
  };
  return map[name] || '按口味任选';
}

/* ============================== 路由处理 ============================== */

/**
 * v2 路由分发（由 mock.js route() 头部委托）。
 * @param {string} m    大写 HTTP method
 * @param {string} url  干净路径（已去掉 query）
 * @param {object} data 请求体
 * @param {object} q    query 对象
 * @param {object} h    宿主注入 { db, bizError, requireAuth, fmtDateTime, todayStr, saveDb }
 * @returns {{handled:boolean, value?:any}}  handled=false 时回落到 mock.js 原有路由
 */
function handle(m, url, data, q, h) {
  const { db, bizError, requireAuth, saveDb } = h;
  const v2 = ensureV2(db);
  const ok = (value) => ({ handled: true, value });

  /* ---------- F1 轮播横幅 ---------- */
  if (url === '/user/banner/list' && m === 'GET') {
    requireAuth(db);
    return ok(v2.banners.filter((b) => b.status === 1).sort((a, b) => a.sort - b.sort));
  }

  /* ---------- F2 会员等级 ---------- */
  if (url === '/user/member/level' && m === 'GET') {
    requireAuth(db);
    return ok(memberLevelView(db));
  }

  /* ---------- F4 券包 ---------- */
  if (url === '/user/coupon/list' && m === 'GET') {
    requireAuth(db);
    const list = v2.coupons.map((c) => {
      const owned = v2.userCoupons.find((u) => u.couponId === c.id);
      return {
        id: c.id,
        name: c.name,
        type: c.type,                       // 1 满减 | 2 折扣 | 3 秒杀满减
        thresholdAmount: c.thresholdAmount,
        discountAmount: c.discountAmount,
        discountRate: c.discountRate,
        validDays: c.totalDays,
        seckill: !!c.seckill,
        stock: c.seckill ? c.stock : null,
        remain: c.seckill ? c.remain : null,
        received: !!owned,                  // 模板维度：是否已领过（一人一单）
        userCouponId: owned ? owned.id : null,
        status: owned ? owned.status : 0,   // 0 未领取 | 1 可用 | 2 已使用 | 3 已过期
        expireDate: owned ? owned.expireDate : null
      };
    });
    return ok(list);
  }

  if (url.indexOf('/user/coupon/receive/') === 0 && m === 'POST') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const coupon = v2.coupons.find((c) => c.id === id);
    if (!coupon) throw bizError('券不存在或已下架');
    if (v2.userCoupons.some((u) => u.couponId === id)) {
      throw bizError('您已领取过该券，一人限领一张'); // 对应 Java SETNX 防重
    }
    if (coupon.seckill && coupon.remain <= 0) {
      throw bizError('手慢了，本场秒杀券已抢光');      // 对应 Java 库存校验
    }
    if (coupon.seckill) coupon.remain -= 1;
    const now = Date.now();
    v2.userCoupons.push({
      id: v2.couponSeq++,
      couponId: id,
      status: 1,
      receiveTime: h.fmtDateTime(now),
      expireDate: dateStr(now + coupon.totalDays * DAY)
    });
    saveDb();
    return ok(null);
  }

  if (url === '/user/coupon/usable' && m === 'GET') {
    requireAuth(db);
    const amount = Number(q.amount || 0);
    const list = v2.userCoupons
      .filter((u) => u.status === 1)
      .map((u) => {
        const c = v2.coupons.find((x) => x.id === u.couponId);
        if (!c) return null;
        if (amount < c.thresholdAmount) return null; // 门槛不满足不返回
        const discount = calcDiscount(c, amount);
        return {
          userCouponId: u.id,
          couponId: c.id,
          name: c.name,
          type: c.type,
          thresholdAmount: c.thresholdAmount,
          discountAmount: discount,
          discountText: c.type === 2 ? ('满' + c.thresholdAmount + '打' + (c.discountRate / 10) + '折') : ('满' + c.thresholdAmount + '减' + c.discountAmount),
          expireDate: u.expireDate
        };
      })
      .filter(Boolean)
      .sort((a, b) => b.discountAmount - a.discountAmount);
    return ok(list);
  }

  /* ---------- F5 每日签到 ---------- */
  if (url === '/user/checkin/info' && m === 'GET') {
    requireAuth(db);
    const today = dateStr(Date.now());
    const signedDates = v2.checkin.signedDates.slice().sort();
    return ok({
      signedDates,
      streak: calcStreak(signedDates, today),
      totalDays: signedDates.length,
      todaySigned: signedDates.indexOf(today) > -1,
      points: db.user.points
    });
  }

  if (url === '/user/checkin/sign' && m === 'POST') {
    requireAuth(db);
    const today = dateStr(Date.now());
    if (v2.checkin.signedDates.indexOf(today) > -1) {
      throw bizError('今日已签到，明天再来哦');
    }
    v2.checkin.signedDates.push(today);
    db.user.points += 5; // 签到积分（演示口径；对应 Java 里 user 表积分 + Redis BITMAP）
    saveDb();
    const signedDates = v2.checkin.signedDates.slice().sort();
    return ok({
      signedDates,
      streak: calcStreak(signedDates, today),
      totalDays: signedDates.length,
      todaySigned: true,
      gained: 5,
      points: db.user.points
    });
  }

  /* ---------- F6 评价墙 ---------- */
  if (url === '/user/review/wall' && m === 'GET') {
    requireAuth(db);
    const page = Math.max(1, Number(q.page || 1));
    const pageSize = Math.max(1, Number(q.pageSize || 10));
    let list = v2.reviews.slice().sort((a, b) => (b.createTime > a.createTime ? 1 : -1));
    if (q.drinkId !== undefined && q.drinkId !== null && q.drinkId !== '') {
      list = list.filter((r) => r.drinkId === Number(q.drinkId));
    }
    const total = list.length;
    const records = list.slice((page - 1) * pageSize, page * pageSize);
    return ok({ total, records });
  }

  if (url === '/user/review/tags' && m === 'GET') {
    requireAuth(db);
    const counter = {};
    v2.reviews.forEach((r) => (r.tags || []).forEach((t) => { counter[t] = (counter[t] || 0) + 1; }));
    const tags = Object.keys(counter)
      .map((tag) => ({ tag, count: counter[tag] }))
      .sort((a, b) => b.count - a.count);
    return ok(tags);
  }

  if (url === '/user/review/submit' && m === 'POST') {
    requireAuth(db);
    const body = data || {};
    const orderId = Number(body.orderId);
    const order = db.orders.find((o) => o.id === orderId);
    if (!order) throw bizError('订单不存在');
    if (Number(order.status) !== 5) throw bizError('订单完成后才能评价');
    if (v2.reviewedOrderIds.indexOf(orderId) > -1) throw bizError('该订单已评价过啦，一单一评');
    const first = (order.orderDetailList || [])[0] || {};
    const rating = Math.min(5, Math.max(1, Number(body.rating || 5)));
    v2.reviews.unshift({
      id: v2.reviewSeq++,
      userId: 1,
      nickName: db.user.nickName || '山月茶咖用户',
      avatarUrl: db.user.avatarUrl || '',
      orderId,
      drinkId: first.drinkId || null,
      drinkName: first.name || '',
      rating,
      content: String(body.content || '').slice(0, 120),
      tags: (body.tags || []).slice(0, 6),
      images: [],
      createTime: h.fmtDateTime(Date.now()),
      likeCount: 0
    });
    v2.reviewedOrderIds.push(orderId);
    db.user.points += 10; // 评价奖励
    saveDb();
    return ok({ pointsGained: 10, points: db.user.points });
  }

  /* ---------- F7 AI 点单 ---------- */
  if (url === '/user/ai/suggests' && m === 'GET') {
    requireAuth(db);
    return ok(AI_SUGGESTS.slice());
  }

  if (url === '/user/ai/order' && m === 'POST') {
    requireAuth(db);
    const body = data || {};
    // history 契约字段：真实后端拼进大模型 messages；mock 只用当前句 + lastRecommendId
    return ok(aiReply(db, body.message, body.lastRecommendId));
  }

  // 未命中 v2 路径
  return { handled: false };
}

module.exports = {
  ensureV2,
  handle,
  applyCoupon,
  memberLevelView,
  LEVELS
};
