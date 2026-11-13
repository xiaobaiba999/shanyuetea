/**
 * 山月茶咖 C端接口类型定义
 * 与《API-SPEC（内部统一规范 v1.0）》第 0/2 节保持一致
 * 价格单位：分（number），如 1590 = 15.90 元
 */

/** 统一响应结构：code=1 成功 / code=0 失败 */
export interface Result<T = unknown> {
  code: 0 | 1
  msg: string
  data: T
}

/** 分类 */
export interface CategoryVO {
  id: number
  name: string
  icon: string
  sort: number
}

/** 规格选项 */
export interface SpecOptionVO {
  id: number
  name: string
  /** 加价（分），0 表示不加价 */
  priceDelta: number
  sort: number
}

/** 规格组（杯型/温度/糖度…） */
export interface SpecGroupVO {
  id: number
  name: string
  /** 1 必选 / 0 可选（可选组出现「无需」chip） */
  required: 0 | 1
  sort: number
  specOptions: SpecOptionVO[]
}

/** 饮品/商品 */
export interface DrinkVO {
  id: number
  name: string
  categoryId: number
  price: number
  /** 原价（可空），仅展示用划线价 */
  originPrice: number | null
  image: string
  description: string
  featured: 0 | 1
  status: 0 | 1
  sold: number
  specGroups: SpecGroupVO[]
}

/** 店铺信息（C端 8.1） */
export interface ShopVO {
  name: string
  /** 1 营业中 / 0 已打烊 */
  status: 0 | 1
  notice: string
  openHours: string
}

/** 添加购物车请求体（8.4）：按 groupName+optionName 提交，服务端重算金额 */
export interface SpecPair {
  groupName: string
  optionName: string
}

export interface AddCartDTO {
  drinkId: number
  specs: SpecPair[]
}

/** 购物车减一个（8.6） */
export interface SubCartDTO {
  drinkId: number
  specKey: string
}

/** 购物车行（8.5）：amount = 单价（含规格加价，分）；行小计 = amount × number */
export interface ShoppingCartVO {
  id: number
  drinkId: number
  drinkName: string
  image: string
  /** 规格文案，如「大杯/冰/标准糖/标准」 */
  specText: string
  /** 选项 id 按 group.sort 排序逗号拼接，如 "1011,1021,1031,1041" */
  specKey: string
  amount: number
  number: number
  createTime: string
}

/** 提交订单请求体（8.8）：从购物车结算 */
export interface OrderSubmitDTO {
  remark?: string
  phone?: string
  /** 使用的用户优惠券 id（选填，服务端校验可用性并重算金额） */
  userCouponId?: number
}

/** 提交订单响应（8.8） */
export interface OrderSubmitVO {
  id: number
  orderNumber: string
  pickUpCode: string
  /** 实付金额（分）= 商品总额 - 券抵扣 */
  orderAmount: number
  /** 优惠券抵扣金额（分），未用券为 0 */
  discountAmount: number
  estimatedWaitMinutes: number
}

/** 订单明细行 */
export interface OrderItemVO {
  id: number
  drinkId: number
  drinkName: string
  image: string
  specText: string
  specKey: string
  price: number
  number: number
  amount: number
}

/** 订单状态：1 待接单 / 2 制作中 / 3 待取餐 / 4 已完成 / 5 已取消 */
export type OrderStatus = 1 | 2 | 3 | 4 | 5

/** 订单（8.9） */
export interface OrderVO {
  id: number
  orderNumber: string
  pickUpCode: string
  status: OrderStatus
  orderAmount: number
  /** 优惠券抵扣金额（分），未用券为 0 */
  discountAmount: number
  remark: string
  phone: string
  cancelReason: string
  orderTime: string
  checkoutTime: string
  estimatedWaitMinutes: number
  orderItems: OrderItemVO[]
}

/* ---------------- 优惠券与会员（模块 13【阶段四扩展】） ---------------- */

/** 优惠券模板（管理端配置，C 端券广场领取） */
export interface CouponTemplateVO {
  id: number
  name: string
  /** 1 满减券 / 2 无门槛券 */
  type: 1 | 2
  /** 使用门槛（分），0 = 无门槛 */
  threshold: number
  /** 抵扣金额（分） */
  discount: number
  /** 领取后有效天数 */
  validDays: number
  /** 发放总量（演示用展示） */
  totalCount: number
  /** 剩余可领数量 */
  remainCount: number
  /** 券说明，如「满 25 元减 4 元」 */
  description: string
}

/** 用户持有的优惠券（user_coupon） */
export interface UserCouponVO {
  id: number
  couponId: number
  name: string
  type: 1 | 2
  threshold: number
  discount: number
  /** 领取时间 yyyy-MM-dd HH:mm:ss */
  receiveTime: string
  /** 过期时间 yyyy-MM-dd HH:mm:ss */
  expireTime: string
  /** 1 可用 / 2 已核销 / 3 已过期 */
  status: 1 | 2 | 3
  /** 核销该券的订单号（已核销时回写） */
  usedOrderNumber: string
}

/** 会员概要（积分/累计消费，演示口径） */
export interface MemberSummaryVO {
  /** 会员号（演示固定） */
  memberNo: string
  /** 积分余额 = 消费积分 + 签到积分 - 兑换消耗（见 PointSummaryVO） */
  points: number
  /** 累计消费（分，仅已完成订单） */
  totalSpent: number
  /** 累计完成订单数 */
  finishedOrders: number
  /** 当前可用优惠券张数 */
  couponsAvailable: number
}

/* ---------------- 积分体系（模块 15【阶段四扩展】） ---------------- */

/** 积分概要 */
export interface PointSummaryVO {
  /** 积分余额 = 消费积分（已完成订单实付/100）+ 签到积分（7/天 + 连签奖励 20/7天）- 兑换消耗 */
  balance: number
  /** 累计获得（消费 + 签到 + 连签奖励） */
  totalEarned: number
  /** 累计兑换消耗 */
  totalRedeemed: number
  /** 累计签到天数 */
  checkinDays: number
  /** 连续签到天数（已签今日则含今日，否则截至昨日） */
  streak: number
  /** 今天是否已签到 */
  checkedToday: boolean
  /** 累计获得的连签达成奖励积分 */
  streakBonusTotal: number
  /** 距下一次连签奖励（每连签 7 天 +20）还差几天，1~7 */
  nextBonusIn: number
}

/** 签到接口（15.2）返回：概要 + 本次触发的连签奖励（未触发为 0） */
export type CheckinResultVO = PointSummaryVO & {
  /** 本次签到触发的连签达成奖励积分，0 = 未触发 */
  justBonus: number
}

/** 积分明细记录（type：1 签到 / 2 消费 / 3 兑换） */
export interface PointRecordVO {
  id: string
  type: 1 | 2 | 3
  /** 标题，如「每日签到」「订单消费」「积分兑换」 */
  title: string
  /** 补充说明，如订单号 / 券名称 */
  sub: string
  /** 变动积分：正为获得、负为消耗 */
  points: number
  /** 时间 yyyy-MM-dd HH:mm:ss */
  createTime: string
}

/** 积分兑换货架条目（由券模板按面额推导：1 积分 ≈ 0.1 元，最低 20 积分） */
export interface ExchangeRuleVO {
  templateId: number
  name: string
  description: string
  /** 券面额（分） */
  discount: number
  /** 使用门槛（分），0 = 无门槛 */
  threshold: number
  /** 兑换所需积分 */
  cost: number
  /** 剩余可兑张数（与券库存一致） */
  remainCount: number
}

/* ---------------- 评价体系（模块 16【阶段五扩展】） ---------------- */

/** 评价提交请求体（16.1）：一单仅可评价一次，仅已完成订单可评 */
export interface ReviewCreateDTO {
  orderId: number
  /** 星级 1~5 */
  score: number
  /** 快捷标签（多选），如 ['口味赞', '出餐快'] */
  tags: string[]
  /** 文字评价（选填，≤50 字） */
  content?: string
}

/** 评价（order_review） */
export interface ReviewVO {
  id: number
  orderId: number
  orderNumber: string
  pickUpCode: string
  /** 星级 1~5 */
  score: number
  /** 快捷标签数组 */
  tags: string[]
  /** 文字评价，可为空 */
  content: string
  /** 商家回复（可空） */
  reply: string
  /** 评价时间 yyyy-MM-dd HH:mm:ss */
  createTime: string
}

/** 门店评价统计（16.3）：首页头部门店评分与门店信息页评价墙共用 */
export interface ShopReviewStats {
  /** 平均分（保留 1 位小数，如 4.8；无评价时为 0） */
  avgScore: number
  /** 评价总数 */
  total: number
  /** 好评率 = 4/5 星占比百分数（如 96） */
  goodRate: number
  /** 各星级数量，下标 0 = 5 星 … 下标 4 = 1 星 */
  distribution: number[]
  /** 标签热度（按次数降序，取前 6） */
  tagStats: { tag: string; count: number }[]
}
