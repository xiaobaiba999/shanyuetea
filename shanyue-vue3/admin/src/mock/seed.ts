/* =========================================================
   山月茶咖 · Mock 种子数据（v2.0 契约版）
   数据源：/home/z/my-project/export/menu-data.json（5 商品分类 / 13 商品 / 规格组 / 选项）
   已内联为 TS 常量（拷贝进来，不运行时 fetch），id 由 cuid 映射为数字。
   价格单位：元（v2.1 契约，与后端 BigDecimal 直出一致；v1.6 旧版为分，所有数值已同步 ÷100）。规格组按源数据精简为每商品 2~4 组。
   订单种子：苍穹 6 状态全覆盖 + 自取/外送双模式混合（约 2:1），
   外送单带收货快照（consignee/phone/deliveryAddress）+ deliveryFee=5，待接单含未支付演示单。
   ========================================================= */
import type {
  Category,
  CouponTemplate,
  DeliveryMode,
  Drink,
  EmployeeVO,
  OrderDetail,
  OrderVO,
  Review,
  Setmeal,
  SetmealDrink,
  ShopSetting,
  SpecGroup,
  SpecOption,
} from '@/types'
import { fmtDateTime, ymd } from '@/utils/format'

/* ---------------- 分类（含套餐分类；type：1 菜品 / 2 套餐） ---------------- */
const SEED_TIME = '2025-09-01 09:00:00'

export const seedCategories: Category[] = [
  { id: 1, type: 1, name: '招牌推荐', sort: 1, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 2, type: 1, name: '经典咖啡', sort: 2, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 3, type: 1, name: '创意特调', sort: 3, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 4, type: 1, name: '茶饮', sort: 4, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 5, type: 1, name: '轻食甜点', sort: 5, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 6, type: 2, name: '超值单人套餐', sort: 1, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
  { id: 7, type: 2, name: '双人分享套餐', sort: 2, status: 1, createTime: SEED_TIME, updateTime: SEED_TIME },
]

/* ---------------- 规格模板（menu-data.json specGroups/options） ---------------- */
interface OptionTpl {
  name: string
  priceDelta: number
}
interface GroupTpl {
  name: string
  required: number
  options: OptionTpl[]
}

const TPL = {
  /** 咖啡类：杯型（中/大/超大） */
  cupFull: {
    name: '杯型',
    required: 1,
    options: [
      { name: '中杯', priceDelta: 0 },
      { name: '大杯', priceDelta: 3 },
      { name: '超大杯', priceDelta: 5 },
    ],
  },
  /** 果茶/特调类：杯型（中/大） */
  cupStd: {
    name: '杯型',
    required: 1,
    options: [
      { name: '中杯', priceDelta: 0 },
      { name: '大杯', priceDelta: 3 },
    ],
  },
  /** 饮品温度 */
  temp: {
    name: '温度',
    required: 1,
    options: [
      { name: '冰', priceDelta: 0 },
      { name: '热', priceDelta: 0 },
      { name: '温', priceDelta: 0 },
    ],
  },
  /** 糖度 */
  sweet: {
    name: '糖度',
    required: 1,
    options: [
      { name: '标准糖', priceDelta: 0 },
      { name: '半糖', priceDelta: 0 },
      { name: '少糖', priceDelta: 0 },
      { name: '无糖', priceDelta: 0 },
    ],
  },
  /** 浓度 */
  strength: {
    name: '浓度',
    required: 1,
    options: [
      { name: '标准', priceDelta: 0 },
      { name: '加浓', priceDelta: 2 },
    ],
  },
  /** 甜点规格 */
  dessertSpec: {
    name: '规格',
    required: 1,
    options: [
      { name: '标准装', priceDelta: 0 },
      { name: '分享装', priceDelta: 6 },
    ],
  },
  /** 甜点温度 */
  dessertTemp: {
    name: '温度',
    required: 1,
    options: [
      { name: '常温', priceDelta: 0 },
      { name: '加热', priceDelta: 1 },
    ],
  },
} satisfies Record<string, GroupTpl>

/* ---------------- 商品（menu-data.json products） ---------------- */
interface ProductSeed {
  id: number
  categoryId: number
  name: string
  description: string
  price: number
  image: string
  monthSales: number
  featured: number
  status: number
  groups: GroupTpl[]
}

const productSeeds: ProductSeed[] = [
  {
    id: 1, categoryId: 1, name: '生椰拿铁', description: '厚椰乳与浓缩咖啡的经典相遇，顺滑清甜',
    price: 15.9, image: '/menu/coconut-latte.png', monthSales: 3281, featured: 1, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 2, categoryId: 1, name: '桂花冰酿拿铁', description: '金桂酒酿入奶，一口秋天的温柔',
    price: 17.9, image: '/menu/osmanthus-latte.png', monthSales: 1865, featured: 1, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 3, categoryId: 1, name: '焦糖海盐玛奇朵', description: '咸甜交织的焦糖云顶，层次感拉满',
    price: 18.9, image: '/menu/caramel-macchiato.png', monthSales: 1204, featured: 1, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 4, categoryId: 2, name: '美式咖啡', description: '双份浓缩加冰，干净利落的美式风味',
    price: 9.9, image: '/menu/americano.png', monthSales: 4520, featured: 0, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 5, categoryId: 2, name: '拿铁咖啡', description: '经典意式拿铁，奶咖比例刚刚好',
    price: 13.9, image: '/menu/latte.png', monthSales: 3890, featured: 0, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 6, categoryId: 2, name: '卡布奇诺', description: '绵密奶泡撒可可粉，经典意式配方',
    price: 13.9, image: '/menu/cappuccino.png', monthSales: 2100, featured: 0, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 7, categoryId: 2, name: '澳白', description: '双份浓缩配丝绒奶泡，咖啡感更强',
    price: 15.9, image: '/menu/flat-white.png', monthSales: 1580, featured: 0, status: 1,
    groups: [TPL.cupFull, TPL.temp, TPL.sweet, TPL.strength],
  },
  {
    id: 8, categoryId: 3, name: '茉莉青提冷萃', description: '茉莉花茶汤撞青提果肉，清爽果香',
    price: 19.9, image: '/menu/grape-cold-brew.png', monthSales: 980, featured: 0, status: 1,
    groups: [TPL.cupStd, TPL.temp, TPL.sweet],
  },
  {
    id: 9, categoryId: 3, name: '黑糖波波拿铁', description: '虎纹挂壁配 Q 弹波波，嚼得到的快乐',
    price: 16.9, image: '/menu/brown-sugar-boba.png', monthSales: 2350, featured: 0, status: 1,
    groups: [TPL.cupStd, TPL.temp, TPL.sweet],
  },
  {
    id: 10, categoryId: 4, name: '茉莉奶绿', description: '茉莉绿茶打底，奶香轻盈不腻',
    price: 12.9, image: '/menu/jasmine-milk-tea.png', monthSales: 1730, featured: 0, status: 1,
    groups: [TPL.cupStd, TPL.temp, TPL.sweet],
  },
  {
    id: 11, categoryId: 4, name: '桂花乌龙', description: '乌龙茶底飘桂香，清雅回甘',
    price: 11.9, image: '/menu/osmanthus-oolong.png', monthSales: 1320, featured: 0, status: 1,
    groups: [TPL.cupStd, TPL.temp, TPL.sweet],
  },
  {
    id: 12, categoryId: 5, name: '巴斯克芝士蛋糕', description: '焦香外皮配流心内芯，重芝士爱好者必点',
    price: 19.9, image: '/menu/basque-cheesecake.png', monthSales: 860, featured: 0, status: 1,
    groups: [TPL.dessertSpec, TPL.dessertTemp],
  },
  {
    id: 13, categoryId: 5, name: '黄油可颂', description: '72 层起酥，出炉即香',
    price: 9.9, image: '/menu/butter-croissant.png', monthSales: 1420, featured: 0, status: 1,
    groups: [TPL.dessertTemp],
  },
]

/* 用固定计数器为种子规格组/选项分配稳定 id */
let gid = 1
let oid = 1

function buildGroup(tpl: GroupTpl): SpecGroup {
  const id = gid++
  const options: SpecOption[] = tpl.options.map((o, i) => ({
    id: oid++,
    name: o.name,
    priceDelta: o.priceDelta,
    sort: i + 1,
  }))
  return { id, name: tpl.name, required: tpl.required, sort: 0, options }
}

const seedTime = (i: number) => {
  const d = new Date(2025, 8, 1, 9, 0, 0)
  d.setMinutes(d.getMinutes() + i * 7)
  return fmtDateTime(d)
}

export const seedDrinks: Drink[] = productSeeds.map((p, i) => ({
  id: p.id,
  name: p.name,
  categoryId: p.categoryId,
  price: p.price,
  image: p.image,
  description: p.description,
  featured: p.featured,
  status: p.status,
  monthSales: p.monthSales,
  categoryName: seedCategories.find((c) => c.id === p.categoryId)?.name,
  updateTime: seedTime(i),
  specGroups: p.groups.map((g, gi) => ({ ...buildGroup(g), sort: gi + 1 })),
}))

/* ---------------- 套餐（v2.0 新增；setmealDrinkes 引用上面饮品的原价） ---------------- */
function setmealDrink(drinkId: number, copies: number): SetmealDrink {
  const d = seedDrinks.find((x) => x.id === drinkId) as Drink
  return { drinkId, name: d.name, price: d.price, copies }
}

export const seedSetmeals: Setmeal[] = [
  {
    id: 1, categoryId: 6, name: '晨光咖啡轻食套餐', price: 22.8, status: 1,
    description: '生椰拿铁 + 黄油可颂，开启元气早晨，立省 3 元',
    image: '/menu/coconut-latte.png',
    updateTime: seedTime(20),
    categoryName: '超值单人套餐',
    setmealDrinkes: [setmealDrink(1, 1), setmealDrink(13, 1)],
  },
  {
    id: 2, categoryId: 6, name: '工作日提神套餐', price: 16.8, status: 1,
    description: '双份美式 ×2，会议码农救星',
    image: '/menu/americano.png',
    updateTime: seedTime(21),
    categoryName: '超值单人套餐',
    setmealDrinkes: [setmealDrink(4, 2)],
  },
  {
    id: 3, categoryId: 7, name: '桂月双人下午茶', price: 32.8, status: 1,
    description: '桂花冰酿拿铁 + 巴斯克芝士蛋糕，两个人刚刚好',
    image: '/menu/osmanthus-latte.png',
    updateTime: seedTime(22),
    categoryName: '双人分享套餐',
    setmealDrinkes: [setmealDrink(2, 1), setmealDrink(12, 1)],
  },
  {
    id: 4, categoryId: 7, name: '奶茶轻食欢享组合', price: 18.8, status: 0,
    description: '茉莉奶绿 + 黄油可颂（原料调整，暂停售卖演示）',
    image: '/menu/jasmine-milk-tea.png',
    updateTime: seedTime(23),
    categoryName: '双人分享套餐',
    setmealDrinkes: [setmealDrink(10, 1), setmealDrink(13, 1)],
  },
]

/* ---------------- 员工（admin / 123456 为教师演示账号） ---------------- */
export type SeedEmployee = EmployeeVO & { password?: string }

export const seedEmployees: SeedEmployee[] = [
  {
    id: 1, name: '沈山月', username: 'admin', phone: '13800000001', sex: '0', status: 1,
    idNumber: '330106199001011234', createTime: SEED_TIME, updateTime: SEED_TIME, password: '123456',
  },
  {
    id: 2, name: '顾时安', username: 'shanyue-gu', phone: '13800000002', sex: '1', status: 1,
    createTime: '2025-09-03 10:20:00', updateTime: '2025-09-03 10:20:00',
  },
  {
    id: 3, name: '陆则言', username: 'shanyue-lu', phone: '13800000003', sex: '1', status: 1,
    createTime: '2025-09-05 14:05:00', updateTime: '2025-09-05 14:05:00',
  },
  {
    id: 4, name: '苏晚棠', username: 'shanyue-su', phone: '13900000004', sex: '0', status: 1,
    createTime: '2025-09-10 11:30:00', updateTime: '2025-09-10 11:30:00',
  },
  {
    id: 5, name: '程一诺', username: 'shanyue-cheng', phone: '13700000005', sex: '1', status: 1,
    createTime: '2025-09-15 16:45:00', updateTime: '2025-09-15 16:45:00',
  },
  {
    id: 6, name: '白芷', username: 'shanyue-bai', phone: '13600000006', sex: '0', status: 0,
    createTime: '2025-09-20 09:10:00', updateTime: '2025-09-22 18:00:00',
  },
]

/* ---------------- 店铺设置（苍穹口径只有营业状态；notice 为山月扩展） ---------------- */
export const seedShop: ShopSetting = {
  status: 1,
  notice: '新店开业：全场茶咖第二杯半价，活动截至本周日',
}

/* ---------------- 优惠券模板（管理端模块 14；与 C 端券广场默认数据口径一致） ----------------
 * C 端 h5 的券模板存储在自己 origin 的 localStorage（两端 mock 不互通，接入真实后端后自然同源）。
 * 这里额外包含 1 个「已下架」模板，用于演示上下架状态。
 */
export const seedCoupons: CouponTemplate[] = [
  {
    id: 1, name: '山月新人礼', type: 2, threshold: 0, discount: 5, validDays: 7,
    totalCount: 999, remainCount: 999, status: 1,
    description: '无门槛立减 5 元，新客见面礼',
    createTime: SEED_TIME, updateTime: SEED_TIME,
  },
  {
    id: 2, name: '午后轻松享', type: 1, threshold: 25, discount: 4, validDays: 7,
    // 剩余 34/200 = 17% < 20%：作为「券库存告急」演示样例，仪表盘待办会提示
    totalCount: 200, remainCount: 34, status: 1,
    description: '满 25 元减 4 元',
    createTime: SEED_TIME, updateTime: SEED_TIME,
  },
  {
    id: 3, name: '闺蜜同行券', type: 1, threshold: 40, discount: 8, validDays: 7,
    totalCount: 100, remainCount: 100, status: 1,
    description: '满 40 元减 8 元',
    createTime: SEED_TIME, updateTime: SEED_TIME,
  },
  {
    id: 4, name: '开业大酬宾', type: 1, threshold: 20, discount: 6, validDays: 3,
    totalCount: 300, remainCount: 274, status: 0,
    description: '满 20 元减 6 元',
    createTime: SEED_TIME, updateTime: SEED_TIME,
  },
]

/* ---------------- 今日订单（6 状态全覆盖 + 自取/外送约 2:1 双模式） ----------------
 * 金额口径（苍穹）：OrderDetail.amount = 单价，行小计 = amount × number；
 * Orders.amount = Σ 行小计 +（外送 ? deliveryFee : 0）。
 * 自取单带 pickupNumber（A01 起）；外送单带收货快照 + deliveryFee=5 元。
 * 待接单中含 1 笔「外送货到付款」未支付单（payStatus=0），用于演示支付状态徽标。
 */
type Pick = [groupIndex: number, optionIndex: number]

interface ItemPlan {
  drinkId: number
  picks: Pick[]
  number?: number
}

type OrderStatusSeed = 1 | 2 | 3 | 4 | 5 | 6

interface OrderPlan {
  minutesAgo: number
  status: OrderStatusSeed
  /** 1 自取 / 2 外送（缺省自取，外送单自动补收货快照与配送费） */
  mode?: DeliveryMode
  items: ItemPlan[]
  remark?: string
  phone?: string
  /** 覆盖默认支付状态（1 待付款/拒单单演示 payStatus=0） */
  payStatus?: 0 | 1
  cancelReason?: string
  rejectionReason?: string
}

/** 下单时间：seedMinutesAgo 分钟前，并收敛到“今天”以内，保证统计卡始终有数据 */
function agoTime(minutesAgo: number): number {
  const now = new Date()
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const minutesSinceMidnight = Math.floor((now.getTime() - startOfDay) / 60000)
  const m = Math.min(minutesAgo, Math.max(2, minutesSinceMidnight - 2))
  return Math.max(startOfDay + 60_000, now.getTime() - m * 60_000)
}

const DELIVERY_FEE = 5

/** 外送单收货快照池（consignee + phone + deliveryAddress 成组） */
const ADDRESS_POOL: Array<{ consignee: string; phone: string; deliveryAddress: string }> = [
  { consignee: '沈晚晴', phone: '13811112222', deliveryAddress: '西湖区文三路 128 号 2 幢 601 室' },
  { consignee: '顾北辰', phone: '13922223333', deliveryAddress: '科创园区 3 号楼 B 座 12 层前台' },
  { consignee: '林知夏', phone: '13733334444', deliveryAddress: '西斗门路 9 号人才公寓 A 座 803' },
]

const orderPlans: OrderPlan[] = [
  // ---- 已完成 8 单（自取 5 + 外送 3，营业额 323.10 + 配送费）----
  {
    minutesAgo: 330, status: 5, mode: 2,
    items: [
      { drinkId: 1, picks: [[0, 1], [1, 0], [2, 1], [3, 0]] }, // 生椰拿铁 大杯/冰/半糖/标准 18.90
      { drinkId: 13, picks: [[0, 1]] }, // 黄油可颂 加热 10.90
    ],
  }, // 商品 29.80 + 配送 5
  {
    minutesAgo: 300, status: 5, remark: '蛋糕麻烦切件，谢谢',
    items: [
      { drinkId: 5, picks: [[0, 0], [1, 1], [2, 0], [3, 1]] }, // 拿铁咖啡 中杯/热/标准糖/加浓 15.90
      { drinkId: 12, picks: [[0, 1], [1, 0]] }, // 巴斯克 分享装/常温 25.90
    ],
  }, // 41.80 自取
  {
    minutesAgo: 270, status: 5, mode: 2,
    items: [
      { drinkId: 1, picks: [[0, 0], [1, 0], [2, 0], [3, 0]] }, // 15.90
      { drinkId: 2, picks: [[0, 1], [1, 0], [2, 0], [3, 0]] }, // 桂花冰酿 大杯 20.90
      { drinkId: 6, picks: [[0, 0], [1, 1], [2, 0], [3, 0]] }, // 13.90
    ],
  }, // 商品 50.70 + 配送 5
  {
    minutesAgo: 240, status: 5, remark: '会议用，多放杯垫',
    items: [{ drinkId: 4, picks: [[0, 0], [1, 0], [2, 3], [3, 1]], number: 2 }], // 美式加浓无糖 x2 11.90×2
  }, // 23.80 自取
  {
    minutesAgo: 210, status: 5,
    items: [
      { drinkId: 9, picks: [[0, 1], [1, 0], [2, 2]] }, // 黑糖波波 大杯/冰/少糖 19.90
      { drinkId: 10, picks: [[0, 0], [1, 2], [2, 1]] }, // 茉莉奶绿 中杯/温/半糖 12.90
      { drinkId: 3, picks: [[0, 1], [1, 0], [2, 0], [3, 0]] }, // 焦糖海盐 大杯 21.90
    ],
  }, // 54.70 自取
  {
    minutesAgo: 180, status: 5, mode: 2, remark: '放前台就行，谢谢',
    items: [
      { drinkId: 7, picks: [[0, 0], [1, 1], [2, 0], [3, 0]] }, // 澳白 15.90
      { drinkId: 12, picks: [[0, 0], [1, 0]] }, // 巴斯克 标准装 19.90
    ],
  }, // 商品 35.80 + 配送 5
  {
    minutesAgo: 150, status: 5, remark: '无糖少冰',
    items: [
      { drinkId: 1, picks: [[0, 2], [1, 0], [2, 2], [3, 1]] }, // 生椰 超大杯/冰/少糖/加浓 22.90
      { drinkId: 7, picks: [[0, 1], [1, 1], [2, 0], [3, 0]] }, // 澳白 大杯/热 18.90
      { drinkId: 13, picks: [[0, 0]] }, // 可颂 常温 9.90
    ],
  }, // 51.70 自取
  {
    minutesAgo: 120, status: 5,
    items: [
      { drinkId: 8, picks: [[0, 1], [1, 0], [2, 0]] }, // 茉莉青提 大杯 22.90
      { drinkId: 11, picks: [[0, 0], [1, 0], [2, 2]] }, // 桂花乌龙 11.90
    ],
  }, // 34.80 自取
  // ---- 已接单（制作中）2 单：自取 1 + 外送 1 ----
  {
    minutesAgo: 8, status: 3,
    items: [
      { drinkId: 2, picks: [[0, 1], [1, 0], [2, 0], [3, 0]] }, // 20.90
      { drinkId: 10, picks: [[0, 0], [1, 2], [2, 0]] }, // 12.90
    ],
  }, // 33.80 自取·制作中
  {
    minutesAgo: 14, status: 3, mode: 2,
    items: [
      { drinkId: 9, picks: [[0, 1], [1, 0], [2, 2]] }, // 19.90
      { drinkId: 4, picks: [[0, 0], [1, 0], [2, 3], [3, 1]] }, // 11.90
    ],
  }, // 商品 31.80 + 配送 5 · 外送·制作中
  // ---- 派送中 / 待取餐 2 单：状态 4 的双模式文案演示 ----
  {
    minutesAgo: 26, status: 4,
    items: [{ drinkId: 3, picks: [[0, 0], [1, 0], [2, 0], [3, 0]] }], // 18.90 待取餐
  },
  {
    minutesAgo: 34, status: 4, mode: 2, remark: '麻烦敲门',
    items: [{ drinkId: 6, picks: [[0, 1], [1, 0], [2, 1], [3, 0]] }], // 16.90 派送中
  }, // 商品 16.90 + 配送 5
  // ---- 待接单 3 单：自取 2（已支付） + 外送 1（货到付款 payStatus=0） ----
  {
    minutesAgo: 4, status: 2, remark: '少冰，谢谢', phone: '13800001234',
    items: [
      { drinkId: 2, picks: [[0, 1], [1, 0], [2, 0], [3, 0]] }, // 20.90
      { drinkId: 10, picks: [[0, 0], [1, 2], [2, 0]] }, // 12.90
    ],
  }, // 33.80 自取·待接单
  {
    minutesAgo: 6, status: 2,
    items: [{ drinkId: 1, picks: [[0, 0], [1, 1], [2, 3], [3, 0]] }], // 15.90 自取·待接单
  },
  {
    minutesAgo: 9, status: 2, mode: 2, payStatus: 0, remark: '货到付款',
    items: [{ drinkId: 5, picks: [[0, 1], [1, 0], [2, 0], [3, 0]] }], // 15.90
  }, // 商品 15.90 + 配送 5 · 外送·待接单·未支付
  // ---- 待付款 1 单（payStatus=0，C 端刚提交未支付） ----
  {
    minutesAgo: 2, status: 1,
    items: [{ drinkId: 12, picks: [[0, 0], [1, 0]] }], // 19.90
  },
  // ---- 已取消 2 单：顾客取消 + 商家拒单（rejectionReason 演示） ----
  {
    minutesAgo: 100, status: 6, cancelReason: '顾客临时有事，主动取消',
    items: [{ drinkId: 1, picks: [[0, 0], [1, 1], [2, 0], [3, 0]] }], // 15.90
  },
  {
    minutesAgo: 70, status: 6, mode: 2, payStatus: 0, rejectionReason: '可颂原料售罄，商家拒单',
    items: [{ drinkId: 13, picks: [[0, 0]] }],
  },
]

export type SeedOrder = OrderVO & { seeded?: boolean; seedMinutesAgo?: number }

/** 规格选择 → specSelected 快照 JSON（与 C 端 h5/小程序同款契约格式） */
function toSpecSnapshot(
  drink: Drink,
  picks: Pick[],
): { specSelected: string; unitPrice: number } {
  let delta = 0
  const snapshot: Array<{ groupName: string; optionName: string; priceDelta: number }> = []
  for (const [gi, oi] of picks) {
    const group = drink.specGroups[gi]
    const opt = group.options[oi]
    delta += opt.priceDelta
    snapshot.push({ groupName: group.name, optionName: opt.name, priceDelta: opt.priceDelta })
  }
  return { specSelected: JSON.stringify(snapshot), unitPrice: drink.price + delta }
}

function buildSeedItem(plan: ItemPlan, itemId: number): OrderDetail {
  const drink = seedDrinks.find((d) => d.id === plan.drinkId) as Drink
  const { specSelected, unitPrice } = toSpecSnapshot(drink, plan.picks)
  const number = plan.number ?? 1
  return {
    id: itemId,
    name: drink.name,
    drinkId: drink.id,
    specSelected,
    number,
    amount: unitPrice, // 苍穹口径：明细 amount 为单价
    image: drink.image,
  }
}

export function buildSeedOrders(): SeedOrder[] {
  let itemId = 1
  let pickupSeq = 0 // 取餐号只发给自取单
  const rows = orderPlans.map((plan, i) => {
    const mode: DeliveryMode = plan.mode ?? 1
    const orderTime = agoTime(plan.minutesAgo)
    const paid = plan.status !== 1
    const checkoutTime = paid ? orderTime + 2 * 60_000 : 0
    const orderDetailList = plan.items.map((it) => buildSeedItem(it, itemId++))
    const goodsTotal = orderDetailList.reduce((s, it) => s + it.amount * it.number, 0)
    const deliveryFee = mode === 2 ? DELIVERY_FEE : 0
    const payStatus: 0 | 1 = plan.payStatus ?? (paid ? 1 : 0)
    const pickupNumber = mode === 1 ? `A${String(++pickupSeq).padStart(2, '0')}` : ''
    const addr = mode === 2 ? ADDRESS_POOL[i % ADDRESS_POOL.length] : null
    return {
      id: i + 1,
      // 订单号口径与真实后端一致：yyyyMMdd + 3 位当日序号（无分隔符）
      number: `${ymd(new Date()).replace(/-/g, '')}${String(i + 1).padStart(3, '0')}`,
      status: plan.status,
      payStatus,
      deliveryMode: mode,
      pickupNumber,
      deliveryFee: deliveryFee || undefined,
      consignee: addr?.consignee,
      phone: plan.phone ?? addr?.phone ?? '',
      deliveryAddress: addr?.deliveryAddress,
      amount: goodsTotal + deliveryFee,
      remark: plan.remark ?? '',
      cancelReason: plan.cancelReason ?? '',
      rejectionReason: plan.rejectionReason ?? '',
      orderTime: fmtDateTime(new Date(orderTime)),
      checkoutTime: checkoutTime ? fmtDateTime(new Date(checkoutTime)) : '',
      estimatedDeliveryTime:
        mode === 2 ? fmtDateTime(new Date(orderTime + 40 * 60_000)) : undefined,
      orderDetailList,
      seeded: true,
      seedMinutesAgo: plan.minutesAgo,
    } satisfies SeedOrder
  })
  return rows
}

/* ---------------- 评价种子（模块 17：挂在已完成种子订单上） ----------------
 * 8 条已完成订单逐单生成 8 条评价；
 * 含 1 条 3 星未回复（仪表盘「待回复」演示）+ 1 条 2 星已隐藏（不当言论管理演示）。
 * 可见评价 7 条：平均分 4.4 / 好评率 86%，开箱即有完整统计观感。
 */
interface ReviewSeed {
  orderSeq: number // 对应 buildSeedOrders 的第 N 条（1 起）
  score: number
  tags: string[]
  content: string
  reply?: string
  visible?: number // 0 = 已隐藏
  minutesAfter: number // 订单完成后多少分钟评价
}

const reviewSeeds: ReviewSeed[] = [
  { orderSeq: 1, score: 5, tags: ['口味赞', '出餐快'], content: '生椰拿铁和可颂都很好吃，从下单到取餐也就几分钟！', reply: '谢谢喜爱～欢迎常来 🌼', minutesAfter: 9 },
  { orderSeq: 2, score: 5, tags: ['包装精美'], content: '蛋糕切件包装得很仔细，带去开会一点没洒。', minutesAfter: 16 },
  { orderSeq: 3, score: 4, tags: ['环境好'], content: '店里座位舒服，桂花拿铁好喝，就是下午人多要等一会。', minutesAfter: 22 },
  { orderSeq: 4, score: 4, tags: ['性价比高', '出餐快'], content: '美式加浓很提神，两杯才二十出头，会议救星。', minutesAfter: 35 },
  { orderSeq: 5, score: 5, tags: ['口味赞'], content: '黑糖波波很 Q 弹，甜度选少糖刚刚好。', minutesAfter: 12 },
  { orderSeq: 6, score: 3, tags: ['服务贴心'], content: '澳白奶泡打得有点粗，巴斯克倒是不错，希望稳定一点。', minutesAfter: 28 },
  { orderSeq: 7, score: 2, tags: ['包装精美'], content: '餐车脏了都没擦……失望。', visible: 0, minutesAfter: 6 },
  { orderSeq: 8, score: 5, tags: ['口味赞', '包装精美'], content: '茉莉青提颜值超高，拍照发圈被问链接。', reply: '哈哈欢迎常来打卡 ✨', minutesAfter: 11 },
]

export function buildSeedReviews(): Review[] {
  const orders = buildSeedOrders()
  return reviewSeeds.map((rs, i) => {
    const order = orders[rs.orderSeq - 1]
    const base = new Date(order.orderTime.replace(/-/g, '/')).getTime()
    const created = new Date(base + rs.minutesAfter * 60_000)
    return {
      id: i + 1,
      orderId: order.id,
      orderNumber: order.number,
      pickUpCode: order.pickupNumber ?? '',
      score: rs.score,
      tags: rs.tags,
      content: rs.content,
      reply: rs.reply ?? '',
      visible: rs.visible ?? 1,
      createTime: fmtDateTime(created),
    } satisfies Review
  })
}
