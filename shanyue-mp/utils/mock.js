/**
 * utils/mock.js —— 内置演示数据与接口模拟（USE_MOCK = true 时由 request.js 调用）
 *
 * 覆盖范围（与真实后端接口路径一一对应，联调时把 config.js 的 USE_MOCK 改回 false 即可无缝切换）：
 *  - 登录        POST /user/user/login（免登录）        → UserLoginVO{ id, openid, token, nickName, avatarUrl }
 *  - 我的资料    GET  /user/user/profile                → { id, nickName, avatarUrl, phone, points }
 *  - 分类        GET  /user/category/list?type=         → Category[]
 *  - 饮品        GET  /user/drink/list?categoryId=       → DrinkVO[]（山月补全：monthSales + specGroups 结构化规格组）
 *  - 套餐        GET  /user/setmeal/list?categoryId=    → Setmeal[]
 *                GET  /user/setmeal/drink/{id}           → DrinkItemVO[]
 *  - 购物车      POST /user/shoppingCart/add | /sub；GET /list；DELETE /clean（山月补全：specSelected）
 *  - 下单        POST /user/order/submit               → OrderSubmitVO（山月补全：pickupNumber / deliveryFee）
 *  - 支付(两步)  PUT  /user/order/payment               → OrderPaymentVO（仅生成支付参数，不改状态）
 *                PUT  /user/order/paySuccess?orderNumber=（模拟支付回调：payStatus→1、状态 1→2）
 *  - 订单        GET  /user/order/historyOrders         → PageResult{ total, records }
 *                GET  /user/order/orderDetail/{id}      → OrderVO（含 orderDetailList）
 *                PUT  /user/order/cancel/{id}；POST /user/order/repetition/{id}；GET /user/order/reminder/{id}
 *  - 地址簿      POST|PUT /user/addressBook；DELETE /user/addressBook?id=（苍穹契约：query 传 id）
 *                GET /list、GET /default、GET /{id}、PUT /default（body 传 { id, isDefault }）
 *  - 店铺        GET  /user/shop/status                 → data: 1 营业 / 0 打烊
 *
 * 模拟口径与苍穹后端一致：
 *  - 购物车/订单明细的 amount = 单价，Orders.amount = 总金额（前端提交，苍穹信任前端金额）
 *  - 订单号 number = 时间戳字符串；自取单取餐号 A01 按天自增
 *  - 支付两步走；取消仅 1 待付款 / 2 待接单；催单仅 2 待接单 / 3 制作中
 *  - 金额运算统一走「转分取整」，避免浮点误差
 */
const config = require('./config');
const F = require('./format');

const DB_KEY = 'SHANYUE_MOCK_DB_V1';

/* ============================== 工具函数 ============================== */
function pad(n, len) {
  let s = String(n);
  while (s.length < len) s = '0' + s;
  return s;
}
/** 时间戳 -> 'yyyy-MM-dd HH:mm:ss'（苍穹后端返回格式） */
function fmtDateTime(ts) {
  const d = new Date(ts);
  return (
    d.getFullYear() + '-' + pad(d.getMonth() + 1, 2) + '-' + pad(d.getDate(), 2) +
    ' ' + pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2) + ':' + pad(d.getSeconds(), 2)
  );
}
function todayStr(ts) {
  const d = new Date(ts);
  return d.getFullYear() + '-' + pad(d.getMonth() + 1, 2) + '-' + pad(d.getDate(), 2);
}
function fenRound(n) {
  return Math.round(Number(n || 0) * 100) / 100;
}
/** 规格数组 -> specSelected JSON 字符串（山月契约格式） */
function toSpecSelected(pairs) {
  return JSON.stringify(pairs); // [{"groupName":"杯型","optionName":"大杯","priceDelta":3},...]
}

/* ============================== 种子数据 ============================== */
/** 规格快捷构造 */
function spec(groupName, options) {
  return {
    name: groupName,
    sort: 0,
    options: options.map((o, i) => ({ name: o[0], priceDelta: o[1] || 0, sort: i }))
  };
}
/** 自取单规格串快捷构造 */
const S = (pairs) => toSpecSelected(pairs);

const CUP = spec('杯型', [['中杯', 0], ['大杯', 3]]);
const TEMP_ICE = spec('温度', [['冰', 0], ['热', 0], ['去冰', 0]]);
const TEMP_HOT = spec('温度', [['热', 0], ['冰', 0]]);
const SUGAR = spec('糖度', [['标准糖', 0], ['半糖', 0], ['不另加糖', 0]]);

function seedDb() {
  const now = Date.now();
  const H = 3600 * 1000;
  const yesterday = fmtDateTime(now - 26 * H);

  // 分类 4 个（type: 1 饮品分类 | 2 套餐分类，与苍穹 Category.type 一致）
  const categories = [
    { id: 1, type: 1, name: '招牌推荐', sort: 1, status: 1 },
    { id: 2, type: 1, name: '经典咖啡', sort: 2, status: 1 },
    { id: 3, type: 1, name: '茶饮', sort: 3, status: 1 },
    { id: 4, type: 2, name: '超值套餐', sort: 4, status: 1 }
  ];

  // 饮品 8 款（DrinkVO + 山月补全 monthSales / specGroups；status: 1 起售 0 售罄）
  const drinkes = [
    {
      id: 101, name: '生椰拿铁', categoryId: 1, price: 15.9, image: '', status: 1, monthSales: 356,
      description: '厚椰乳遇上浓缩，椰香清冽',
      specGroups: [CUP, TEMP_ICE, SUGAR]
    },
    {
      id: 102, name: '桂花冰酿拿铁', categoryId: 1, price: 17.9, image: '', status: 1, monthSales: 289,
      description: '桂花米酿打底，秋日限定风味',
      specGroups: [CUP, spec('温度', [['冰', 0], ['去冰', 0]]), SUGAR]
    },
    {
      id: 103, name: '焦糖玛奇朵', categoryId: 1, price: 18.9, image: '', status: 1, monthSales: 173,
      description: '香草奶与焦糖网的经典层次',
      specGroups: [CUP, TEMP_HOT, SUGAR]
    },
    {
      id: 104, name: '美式咖啡', categoryId: 2, price: 9.9, image: '', status: 1, monthSales: 412,
      description: '双份浓缩，干净纯粹（本款无规格，点击直接加购）',
      specGroups: []
    },
    {
      id: 105, name: '澳白', categoryId: 2, price: 15.9, image: '', status: 1, monthSales: 198,
      description: '双份 ristretto 与绵密奶泡',
      specGroups: [CUP, TEMP_HOT]
    },
    {
      id: 106, name: '黑糖波波奶茶', categoryId: 3, price: 14.9, image: '', status: 1, monthSales: 331,
      description: '现熬黑糖挂壁，波波弹牙',
      specGroups: [CUP, TEMP_ICE, SUGAR, spec('小料', [['不加小料', 0], ['珍珠', 2], ['椰果', 2]])]
    },
    {
      id: 107, name: '茉莉奶绿', categoryId: 3, price: 12.9, image: '', status: 1, monthSales: 245,
      description: '茉莉花茶底与轻乳茶',
      specGroups: [CUP, TEMP_ICE, SUGAR]
    },
    {
      id: 108, name: '柚见鲜茶', categoryId: 3, price: 13.9, image: '', status: 0, monthSales: 88,
      description: '满杯柚子果肉，清爽解腻（售罄演示）',
      specGroups: [CUP, TEMP_ICE, SUGAR]
    }
  ];

  // 套餐 2 个（苍穹 Setmeal：status 1 启用）
  const setmeals = [
    {
      id: 201, categoryId: 4, name: '山月双人下午茶套餐', price: 39.9, status: 1, image: '',
      description: '生椰拿铁×2 + 黄油可颂×2 + 巴斯克芝士蛋糕×1'
    },
    {
      id: 202, categoryId: 4, name: '经典咖啡轻食套餐', price: 29.9, status: 1, image: '',
      description: '美式咖啡×1 + 燕麦曲奇×2'
    }
  ];

  // 套餐明细（GET /user/setmeal/drink/{id} → DrinkItemVO[]：name/copies/image/description）
  const setmealDrinkes = {
    201: [
      { name: '生椰拿铁', copies: 2, image: '', description: '中杯·冰·标准糖' },
      { name: '黄油可颂', copies: 2, image: '', description: '现烤酥皮' },
      { name: '巴斯克芝士蛋糕', copies: 1, image: '', description: '切件' }
    ],
    202: [
      { name: '美式咖啡', copies: 1, image: '', description: '双份浓缩' },
      { name: '燕麦曲奇', copies: 2, image: '', description: '海盐口味' }
    ]
  };

  // 地址簿 2 条（苍穹 AddressBook 字段：consignee/phone/sex/detail/label/isDefault…）
  const addresses = [
    {
      id: 301, userId: 1, consignee: '林小月', phone: '13800006688', sex: '1',
      provinceCode: '', provinceName: '广东省', cityCode: '', cityName: '广州市',
      districtCode: '', districtName: '天河区', detail: '科创园区 A 座 12 楼 1201 室',
      label: '公司', isDefault: 1
    },
    {
      id: 302, userId: 1, consignee: '林小月', phone: '13800006688', sex: '0',
      provinceCode: '', provinceName: '广东省', cityCode: '', cityName: '广州市',
      districtCode: '', districtName: '天河区', detail: '望月花园 3 栋 801',
      label: '家', isDefault: 0
    }
  ];

  // 订单 6 个：自取/外送混合，覆盖 1~6 全部状态（payStatus 0/1）
  const orders = [
    {
      id: 1, number: String(now - 40 * 60 * 1000), status: 1, payStatus: 0,
      deliveryMode: 1, pickupNumber: 'A05', deliveryFee: 0, addressBookId: null,
      orderTime: fmtDateTime(now - 40 * 60 * 1000), checkoutTime: null,
      payMethod: 1, amount: 18.9, remark: '少冰，谢谢',
      userName: '山月茶咖用户', phone: '', address: '', consignee: '',
      cancelReason: null,
      orderDetailList: [
        {
          id: 11, orderId: 1, name: '生椰拿铁', drinkId: 101, setmealId: null,
          drinkFlavor: S([{ groupName: '杯型', optionName: '大杯', priceDelta: 3 },
                         { groupName: '温度', optionName: '冰', priceDelta: 0 },
                         { groupName: '糖度', optionName: '标准糖', priceDelta: 0 }]),
          specSelected: S([{ groupName: '杯型', optionName: '大杯', priceDelta: 3 },
                           { groupName: '温度', optionName: '冰', priceDelta: 0 },
                           { groupName: '糖度', optionName: '标准糖', priceDelta: 0 }]),
          number: 1, amount: 18.9, image: ''
        }
      ]
    },
    {
      id: 2, number: String(now - 2 * H), status: 2, payStatus: 1,
      deliveryMode: 1, pickupNumber: 'A04', deliveryFee: 0, addressBookId: null,
      orderTime: fmtDateTime(now - 2 * H), checkoutTime: fmtDateTime(now - 2 * H + 3 * 60 * 1000),
      payMethod: 1, amount: 29.8, remark: '',
      userName: '山月茶咖用户', phone: '', address: '', consignee: '',
      cancelReason: null,
      orderDetailList: [
        {
          id: 21, orderId: 2, name: '黑糖波波奶茶', drinkId: 106, setmealId: null,
          drinkFlavor: S([{ groupName: '杯型', optionName: '大杯', priceDelta: 3 },
                         { groupName: '温度', optionName: '冰', priceDelta: 0 },
                         { groupName: '糖度', optionName: '半糖', priceDelta: 0 },
                         { groupName: '小料', optionName: '珍珠', priceDelta: 2 }]),
          specSelected: S([{ groupName: '杯型', optionName: '大杯', priceDelta: 3 },
                           { groupName: '温度', optionName: '冰', priceDelta: 0 },
                           { groupName: '糖度', optionName: '半糖', priceDelta: 0 },
                           { groupName: '小料', optionName: '珍珠', priceDelta: 2 }]),
          number: 1, amount: 19.9, image: ''
        },
        {
          id: 22, orderId: 2, name: '美式咖啡', drinkId: 104, setmealId: null,
          drinkFlavor: null, specSelected: null,
          number: 1, amount: 9.9, image: ''
        }
      ]
    },
    {
      id: 3, number: String(now - 3 * H), status: 3, payStatus: 1,
      deliveryMode: 2, pickupNumber: null, deliveryFee: config.DELIVERY_FEE, addressBookId: 301,
      orderTime: fmtDateTime(now - 3 * H), checkoutTime: fmtDateTime(now - 3 * H + 2 * 60 * 1000),
      payMethod: 1, amount: 40.8, remark: '放前台即可',
      userName: '山月茶咖用户', phone: '13800006688',
      address: '广东省广州市天河区科创园区 A 座 12 楼 1201 室', consignee: '林小月',
      cancelReason: null,
      orderDetailList: [
        {
          id: 31, orderId: 3, name: '桂花冰酿拿铁', drinkId: 102, setmealId: null,
          drinkFlavor: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                         { groupName: '温度', optionName: '冰', priceDelta: 0 },
                         { groupName: '糖度', optionName: '标准糖', priceDelta: 0 }]),
          specSelected: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                           { groupName: '温度', optionName: '冰', priceDelta: 0 },
                           { groupName: '糖度', optionName: '标准糖', priceDelta: 0 }]),
          number: 2, amount: 17.9, image: ''
        }
      ]
    },
    {
      id: 4, number: String(now - 5 * H), status: 4, payStatus: 1,
      deliveryMode: 2, pickupNumber: null, deliveryFee: config.DELIVERY_FEE, addressBookId: 302,
      orderTime: fmtDateTime(now - 5 * H), checkoutTime: fmtDateTime(now - 5 * H + 60 * 1000),
      payMethod: 1, amount: 44.9, remark: '',
      userName: '山月茶咖用户', phone: '13800006688',
      address: '广东省广州市天河区望月花园 3 栋 801', consignee: '林小月',
      cancelReason: null,
      orderDetailList: [
        { id: 41, orderId: 4, name: '山月双人下午茶套餐', drinkId: null, setmealId: 201,
          drinkFlavor: null, specSelected: null, number: 1, amount: 39.9, image: '' }
      ]
    },
    {
      id: 5, number: String(now - 26 * H), status: 5, payStatus: 1,
      deliveryMode: 1, pickupNumber: 'A01', deliveryFee: 0, addressBookId: null,
      orderTime: yesterday, checkoutTime: yesterday,
      payMethod: 1, amount: 25.8, remark: '',
      userName: '山月茶咖用户', phone: '', address: '', consignee: '',
      cancelReason: null,
      orderDetailList: [
        { id: 51, orderId: 5, name: '澳白', drinkId: 105, setmealId: null,
          drinkFlavor: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                         { groupName: '温度', optionName: '热', priceDelta: 0 }]),
          specSelected: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                           { groupName: '温度', optionName: '热', priceDelta: 0 }]),
          number: 1, amount: 15.9, image: '' },
        { id: 52, orderId: 5, name: '美式咖啡', drinkId: 104, setmealId: null,
          drinkFlavor: null, specSelected: null, number: 1, amount: 9.9, image: '' }
      ]
    },
    {
      id: 6, number: String(now - 30 * H), status: 6, payStatus: 0,
      deliveryMode: 2, pickupNumber: null, deliveryFee: config.DELIVERY_FEE, addressBookId: 301,
      orderTime: fmtDateTime(now - 30 * H), checkoutTime: null,
      payMethod: 1, amount: 17.9, remark: '',
      userName: '山月茶咖用户', phone: '13800006688',
      address: '广东省广州市天河区科创园区 A 座 12 楼 1201 室', consignee: '林小月',
      cancelReason: '用户取消订单',
      orderDetailList: [
        { id: 61, orderId: 6, name: '茉莉奶绿', drinkId: 107, setmealId: null,
          drinkFlavor: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                         { groupName: '温度', optionName: '冰', priceDelta: 0 },
                         { groupName: '糖度', optionName: '不另加糖', priceDelta: 0 }]),
          specSelected: S([{ groupName: '杯型', optionName: '中杯', priceDelta: 0 },
                           { groupName: '温度', optionName: '冰', priceDelta: 0 },
                           { groupName: '糖度', optionName: '不另加糖', priceDelta: 0 }]),
          number: 1, amount: 12.9, image: '' }
      ]
    }
  ];

  return {
    seededAt: now,
    // 会话（模拟后端签发的 JWT；request.js 的 401 模拟基于此判断）
    session: null,
    // C 端用户资料（profile 接口数据；nickName 为空 → 前端显示默认「山月茶咖用户」）
    user: { id: 1, nickName: '', avatarUrl: '', phone: '13800006688', points: 120 },
    shopStatus: 1,
    categories,
    drinkes,
    setmeals,
    setmealDrinkes,
    addresses,
    orders,
    cart: [],
    seq: { cart: 1000, address: 400, order: 10, orderDetail: 100 },
    // 取餐号按天自增（种子订单已用掉 A01/A04/A05，从 A06 继续）
    pickup: { date: todayStr(now), seq: 5 },
    reminders: []
  };
}

/* ============================== 存取 ============================== */
let cache = null;
function loadDb() {
  if (cache) return cache;
  try {
    cache = wx.getStorageSync(DB_KEY) || null;
  } catch (e) {
    cache = null;
  }
  if (!cache || !cache.categories) {
    cache = seedDb();
    saveDb();
  }
  return cache;
}
function saveDb() {
  try {
    wx.setStorageSync(DB_KEY, cache);
  } catch (e) { /* 忽略 */ }
}
/** 重置演示数据（me 页「重置演示数据」按钮调用） */
function resetDb() {
  try {
    wx.removeStorageSync(DB_KEY);
  } catch (e) { /* 忽略 */ }
  cache = null;
}

/** 模拟 401：除登录外都需要「已登录会话」 */
function requireAuth(db) {
  if (!db.session || !db.session.token) {
    const err = new Error('未登录或登录已过期');
    err.httpStatus = 401;
    throw err;
  }
  return db.session.userId;
}
function bizError(msg) {
  return new Error(msg || '业务异常');
}

/** 自取单取餐号：按天 A01 自增（山月契约格式） */
function nextPickupNumber(db, now) {
  const today = todayStr(now);
  if (db.pickup.date !== today) {
    db.pickup = { date: today, seq: 0 };
  }
  db.pickup.seq += 1;
  return 'A' + pad(db.pickup.seq, 2);
}

/** 购物车合并查找：同 drinkId + setmealId + specSelected 视为同一项（同苍穹 drinkFlavor 模式） */
function findCartItem(db, drinkId, setmealId, specSelected) {
  return db.cart.find((c) =>
    (c.drinkId || null) === (drinkId || null) &&
    (c.setmealId || null) === (setmealId || null) &&
    (c.specSelected || null) === (specSelected || null)
  );
}

/* ============================== 路由分发 ============================== */
function route(method, url, data, query) {
  const db = loadDb();
  const m = (method || 'GET').toUpperCase();
  const q = query || {};

  /* ---------- 登录（免登录态） ---------- */
  if (url === '/user/user/login' && m === 'POST') {
    db.session = { token: 'mock-token-' + Date.now(), userId: 1 };
    saveDb();
    // UserLoginVO：苍穹三字段 + 山月补全 nickName / avatarUrl（可为空 → 前端显示默认昵称）
    return {
      id: db.user.id,
      openid: 'mock-openid-shanyue-001',
      token: db.session.token,
      nickName: db.user.nickName,
      avatarUrl: db.user.avatarUrl
    };
  }

  /* ---------- 以下全部需要登录态（模拟 401） ---------- */

  // 我的资料（山月补全契约）
  if (url === '/user/user/profile' && m === 'GET') {
    requireAuth(db);
    const u = db.user;
    return { id: u.id, nickName: u.nickName, avatarUrl: u.avatarUrl, phone: u.phone, points: u.points };
  }

  // 分类
  if (url === '/user/category/list' && m === 'GET') {
    requireAuth(db);
    const type = q.type ? Number(q.type) : null;
    const list = db.categories
      .filter((c) => (type ? c.type === type : true))
      .sort((a, b) => a.sort - b.sort);
    return list;
  }

  // 饮品（含售罄，供前端置灰展示）
  if (url === '/user/drink/list' && m === 'GET') {
    requireAuth(db);
    const categoryId = Number(q.categoryId);
    return db.drinkes
      .filter((d) => d.categoryId === categoryId)
      .map((d) => JSON.parse(JSON.stringify(d)));
  }

  // 套餐列表
  if (url === '/user/setmeal/list' && m === 'GET') {
    requireAuth(db);
    const categoryId = Number(q.categoryId);
    return db.setmeals.filter((s) => s.categoryId === categoryId);
  }

  // 套餐明细
  if (url.indexOf('/user/setmeal/drink/') === 0 && m === 'GET') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    return db.setmealDrinkes[id] || [];
  }

  /* ---------- 购物车 ---------- */
  if (url === '/user/shoppingCart/add' && m === 'POST') {
    requireAuth(db);
    const drinkId = data.drinkId || null;
    const setmealId = data.setmealId || null;
    // 山月补全：specSelected（String）；与 drinkFlavor 同值，兼容苍穹按 drinkFlavor 合并
    const specSelected = data.specSelected || data.drinkFlavor || null;
    if (drinkId) {
      const drink = db.drinkes.find((d) => d.id === Number(drinkId));
      if (!drink) throw bizError('菜品不存在');
      if (drink.status === 0) throw bizError('「' + drink.name + '」已售罄，看看其他饮品吧');
      const hit = findCartItem(db, drink.id, null, specSelected);
      if (hit) {
        hit.number += 1;
      } else {
        db.cart.push({
          id: db.seq.cart++, name: drink.name, userId: 1, drinkId: drink.id, setmealId: null,
          drinkFlavor: specSelected, specSelected, number: 1, amount: drink.price,
          image: drink.image, createTime: fmtDateTime(Date.now())
        });
      }
    } else if (setmealId) {
      const setmeal = db.setmeals.find((s) => s.id === Number(setmealId));
      if (!setmeal) throw bizError('套餐不存在');
      if (setmeal.status === 0) throw bizError('「' + setmeal.name + '」已下架');
      const hit = findCartItem(db, null, setmeal.id, specSelected);
      if (hit) {
        hit.number += 1;
      } else {
        db.cart.push({
          id: db.seq.cart++, name: setmeal.name, userId: 1, drinkId: null, setmealId: setmeal.id,
          drinkFlavor: specSelected, specSelected, number: 1, amount: setmeal.price,
          image: setmeal.image, createTime: fmtDateTime(Date.now())
        });
      }
    } else {
      throw bizError('购物车参数错误：drinkId / setmealId 至少传一个');
    }
    saveDb();
    return null;
  }

  if (url === '/user/shoppingCart/sub' && m === 'POST') {
    requireAuth(db);
    const drinkId = data.drinkId || null;
    const setmealId = data.setmealId || null;
    const specSelected = data.specSelected || data.drinkFlavor || null;
    const idx = db.cart.findIndex((c) =>
      (c.drinkId || null) === (drinkId || null) &&
      (c.setmealId || null) === (setmealId || null) &&
      (c.specSelected || null) === (specSelected || null)
    );
    if (idx > -1) {
      if (db.cart[idx].number > 1) {
        db.cart[idx].number -= 1;
      } else {
        db.cart.splice(idx, 1);
      }
    }
    saveDb();
    return null;
  }

  if (url === '/user/shoppingCart/list' && m === 'GET') {
    requireAuth(db);
    return db.cart.map((c) => JSON.parse(JSON.stringify(c)));
  }

  if (url === '/user/shoppingCart/clean' && m === 'DELETE') {
    requireAuth(db);
    db.cart = [];
    saveDb();
    return null;
  }

  /* ---------- 订单 ---------- */
  if (url === '/user/order/submit' && m === 'POST') {
    requireAuth(db);
    if (!db.cart.length) throw bizError('购物车为空，不能下单');
    const deliveryMode = Number(data.deliveryMode || config.MODE_PICKUP);
    let addressBook = null;
    if (deliveryMode === config.MODE_DELIVERY) {
      if (!data.addressBookId) throw bizError('外送订单请先选择收货地址');
      addressBook = db.addresses.find((a) => a.id === Number(data.addressBookId));
      if (!addressBook) throw bizError('地址信息有误，请重新选择');
    }
    const now = Date.now();
    const isDelivery = deliveryMode === config.MODE_DELIVERY;
    const deliveryFee = isDelivery ? fenRound(data.deliveryFee !== undefined ? data.deliveryFee : config.DELIVERY_FEE) : 0;
    const order = {
      id: db.seq.order++,
      number: String(now), // 苍穹口径：订单号 = 时间戳字符串
      status: 1, // PENDING_PAYMENT 待付款
      payStatus: 0, // UN_PAID 未支付
      deliveryMode,
      pickupNumber: isDelivery ? null : nextPickupNumber(db, now), // 山月补全：自取才有
      deliveryFee, // 山月补全：外送才有
      addressBookId: isDelivery ? addressBook.id : null,
      orderTime: fmtDateTime(now),
      checkoutTime: null,
      payMethod: data.payMethod || 1,
      // 苍穹信任前端提交金额（OrdersSubmitDTO.amount）；本页已按 分 计算防浮点误差
      amount: fenRound(data.amount),
      remark: data.remark || '',
      userName: db.user.nickName || '山月茶咖用户',
      phone: isDelivery ? addressBook.phone : '',
      address: isDelivery ? (addressBook.provinceName + addressBook.cityName + addressBook.districtName + addressBook.detail) : '',
      consignee: isDelivery ? addressBook.consignee : '',
      cancelReason: null,
      orderDetailList: db.cart.map((c) => ({
        id: db.seq.orderDetail++,
        name: c.name,
        drinkId: c.drinkId,
        setmealId: c.setmealId,
        drinkFlavor: c.drinkFlavor,
        specSelected: c.specSelected,
        number: c.number,
        amount: c.amount, // 苍穹口径：明细 amount = 单价
        image: c.image
      }))
    };
    order.orderDetailList.forEach((d) => { d.orderId = order.id; });
    db.orders.unshift(order);
    // 下单成功清空购物车（苍穹行为）
    db.cart = [];
    // 消费积分演示（可选扩展点：每 1 元 1 分，向上取整）
    db.user.points += Math.ceil(order.amount);
    saveDb();
    // OrderSubmitVO：苍穹四字段 + 山月补全 pickupNumber / deliveryFee
    const vo = {
      id: order.id,
      orderNumber: order.number,
      orderAmount: order.amount,
      orderTime: order.orderTime
    };
    if (order.pickupNumber) vo.pickupNumber = order.pickupNumber;
    if (order.deliveryFee > 0) vo.deliveryFee = order.deliveryFee;
    return vo;
  }

  // 支付第一步：只生成支付参数，不改状态（苍穹两步支付）
  if (url === '/user/order/payment' && m === 'PUT') {
    requireAuth(db);
    const order = db.orders.find((o) => o.number === data.orderNumber);
    if (!order) throw bizError('订单不存在');
    if (order.payStatus === 1) throw bizError('该订单已支付');
    return {
      nonceStr: 'mock' + Math.floor(Math.random() * 1000000),
      paySign: 'MOCK-PAY-SIGN',
      timeStamp: String(Math.floor(Date.now() / 1000)),
      signType: 'RSA',
      packageStr: 'prepay_id=mock_' + order.number
    };
  }

  // 支付第二步：模拟支付成功回调（山月补全契约：payStatus→1，苍穹 paySuccess：状态 1→2）
  if (url === '/user/order/paySuccess' && m === 'PUT') {
    requireAuth(db);
    const orderNumber = q.orderNumber || data.orderNumber;
    const order = db.orders.find((o) => o.number === orderNumber);
    if (!order) throw bizError('订单不存在');
    order.payStatus = 1;
    if (order.status === 1) order.status = 2; // 待付款 → 待接单
    order.checkoutTime = fmtDateTime(Date.now());
    saveDb();
    return null;
  }

  // 历史订单分页（苍穹 PageResult{total, records}，records 为 OrderVO 含 orderDetailList）
  if (url === '/user/order/historyOrders' && m === 'GET') {
    requireAuth(db);
    const page = Math.max(1, Number(q.page || 1));
    const pageSize = Math.max(1, Number(q.pageSize || 10));
    let list = db.orders.slice().sort((a, b) => Number(b.orderTime.replace(/[-: ]/g, '')) - Number(a.orderTime.replace(/[-: ]/g, '')));
    if (q.status !== undefined && q.status !== null && q.status !== '') {
      list = list.filter((o) => o.status === Number(q.status));
    }
    const total = list.length;
    const records = list
      .slice((page - 1) * pageSize, page * pageSize)
      .map((o) => JSON.parse(JSON.stringify(o)));
    return { total, records };
  }

  // 订单详情
  if (url.indexOf('/user/order/orderDetail/') === 0 && m === 'GET') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const order = db.orders.find((o) => o.id === id);
    if (!order) throw bizError('订单不存在');
    return JSON.parse(JSON.stringify(order));
  }

  // 取消订单（仅 1 待付款 / 2 待接单 可取消）
  if (url.indexOf('/user/order/cancel/') === 0 && m === 'PUT') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const order = db.orders.find((o) => o.id === id);
    if (!order) throw bizError('订单不存在');
    if (order.status !== 1 && order.status !== 2) throw bizError('当前订单状态不支持取消');
    order.status = 6;
    order.cancelReason = '用户取消订单';
    order.cancelTime = fmtDateTime(Date.now());
    saveDb();
    return null;
  }

  // 再来一单（苍穹口径：把明细原样插回购物车，不合并）
  if (url.indexOf('/user/order/repetition/') === 0 && m === 'POST') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const order = db.orders.find((o) => o.id === id);
    if (!order) throw bizError('订单不存在');
    (order.orderDetailList || []).forEach((d) => {
      const specSelected = d.specSelected || d.drinkFlavor || null;
      const hit = findCartItem(db, d.drinkId, d.setmealId, specSelected);
      if (hit) {
        hit.number += d.number;
      } else {
        db.cart.push({
          id: db.seq.cart++, name: d.name, userId: 1, drinkId: d.drinkId, setmealId: d.setmealId,
          drinkFlavor: specSelected, specSelected, number: d.number, amount: d.amount,
          image: d.image, createTime: fmtDateTime(Date.now())
        });
      }
    });
    saveDb();
    return null;
  }

  // 催单（仅 2 待接单 / 3 制作中）
  if (url.indexOf('/user/order/reminder/') === 0 && m === 'GET') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const order = db.orders.find((o) => o.id === id);
    if (!order) throw bizError('订单不存在');
    if (order.status !== 2 && order.status !== 3) throw bizError('当前订单状态不支持催单');
    db.reminders.push({ orderId: id, time: fmtDateTime(Date.now()) });
    saveDb();
    return null;
  }

  /* ---------- 地址簿（苍穹契约：DELETE 用 query id；PUT /default 用 body） ---------- */
  if (url === '/user/addressBook' && m === 'POST') {
    requireAuth(db);
    const body = data || {};
    const item = {
      id: db.seq.address++,
      userId: 1,
      consignee: body.consignee || '',
      phone: body.phone || '',
      sex: body.sex || '0',
      provinceCode: body.provinceCode || '',
      provinceName: body.provinceName || '',
      cityCode: body.cityCode || '',
      cityName: body.cityName || '',
      districtCode: body.districtCode || '',
      districtName: body.districtName || '',
      detail: body.detail || '',
      label: body.label || '',
      isDefault: body.isDefault === 1 ? 1 : 0
    };
    if (item.isDefault === 1) {
      db.addresses.forEach((a) => { a.isDefault = 0; });
    } else if (db.addresses.length === 0) {
      item.isDefault = 1; // 苍穹行为：第一条地址自动设为默认
    }
    db.addresses.push(item);
    saveDb();
    return null; // 苍穹 save 不回传 id
  }

  if (url === '/user/addressBook' && m === 'PUT') {
    requireAuth(db);
    const body = data || {};
    const item = db.addresses.find((a) => a.id === Number(body.id));
    if (!item) throw bizError('地址不存在');
    Object.assign(item, {
      consignee: body.consignee,
      phone: body.phone,
      sex: body.sex || item.sex,
      provinceCode: body.provinceCode || '',
      provinceName: body.provinceName,
      cityCode: body.cityCode || '',
      cityName: body.cityName,
      districtCode: body.districtCode || '',
      districtName: body.districtName,
      detail: body.detail,
      label: body.label
    });
    if (body.isDefault === 1) {
      db.addresses.forEach((a) => { a.isDefault = 0; });
      item.isDefault = 1;
    }
    saveDb();
    return null;
  }

  if (url === '/user/addressBook' && m === 'DELETE') {
    requireAuth(db);
    const id = Number(q.id);
    db.addresses = db.addresses.filter((a) => a.id !== id);
    saveDb();
    return null;
  }

  if (url === '/user/addressBook/list' && m === 'GET') {
    requireAuth(db);
    return db.addresses.map((a) => JSON.parse(JSON.stringify(a)));
  }

  if (url === '/user/addressBook/default' && m === 'GET') {
    requireAuth(db);
    const hit = db.addresses.find((a) => a.isDefault === 1);
    return hit ? JSON.parse(JSON.stringify(hit)) : null; // 无默认地址时 data 为 null
  }

  if (url === '/user/addressBook/default' && m === 'PUT') {
    requireAuth(db);
    const id = Number((data || {}).id);
    const item = db.addresses.find((a) => a.id === id);
    if (!item) throw bizError('地址不存在');
    db.addresses.forEach((a) => { a.isDefault = 0; });
    item.isDefault = 1;
    saveDb();
    return null;
  }

  if (url.indexOf('/user/addressBook/') === 0 && m === 'GET') {
    requireAuth(db);
    const id = Number(url.split('/').pop());
    const item = db.addresses.find((a) => a.id === id);
    if (!item) throw bizError('地址不存在');
    return JSON.parse(JSON.stringify(item));
  }

  /* ---------- 店铺 ---------- */
  if (url === '/user/shop/status' && m === 'GET') {
    return db.shopStatus; // data 为 1 营业 / 0 打烊
  }

  throw bizError('Mock 未实现该接口：' + m + ' ' + url);
}

/**
 * mock 请求入口（request.js 在 USE_MOCK=true 时调用）
 * 加 120ms 延迟模拟网络，页面 loading 观感与真实请求一致
 */
function mockRequest(options) {
  const { url, method, data, query } = options || {};
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      try {
        resolve(route(method, String(url || '').split('?')[0], data, query));
      } catch (e) {
        reject(e);
      }
    }, 120);
  });
}

module.exports = {
  mockRequest,
  resetDb
};
