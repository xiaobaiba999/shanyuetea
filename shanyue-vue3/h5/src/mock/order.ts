import type { MemberSummaryVO, OrderItemVO, OrderStatus, OrderSubmitDTO, OrderSubmitVO, OrderVO, Result, ShoppingCartVO, UserCouponVO } from '@/types'
import { dbGet, dbRemove, dbSet, delay, fail, ok } from './db'
import { fmtDateTime, fmtPrice, todayStr } from '@/utils/format'
import { mockCleanCart, mockGetCartList } from './cart'
import { burnCoupon, countUsableCouponsSync, mockGetMyCoupons } from './coupon'
import { getPointSummarySync } from './points'

/**
 * 订单 mock（localStorage 持久化，key = shanyue-h5-orders / shanyue-h5-order-meta）
 * - orderNumber：yyyyMMdd + 3 位当日序号
 * - pickUpCode：按天重置自增，A01/A02…
 * - 金额服务端重算：Σ(单价 amount × 数量)
 * - 内置 3 条当日演示订单（A01 待接单 / A02 制作中 / A03 待取餐）
 */
const ORDERS_KEY = 'orders'
const META_KEY = 'order-meta'

interface OrderMeta {
  date: string
  orderSeq: number
  pickupSeq: number
}

type StoredOrder = OrderVO & { remindedAt?: string; userCouponId?: number }

function readOrders(): StoredOrder[] {
  return dbGet<StoredOrder[]>(ORDERS_KEY, [])
}

function writeOrders(list: StoredOrder[]): void {
  dbSet(ORDERS_KEY, list)
}

/** 组装一条演示订单 */
function buildDemoOrder(
  id: number,
  seq: number,
  status: OrderStatus,
  orderTime: string,
  items: { drinkId: number; drinkName: string; image: string; specText: string; specKey: string; price: number; number: number }[],
  remark = '',
): StoredOrder {
  return {
    id,
    orderNumber: `${todayStr()}${String(seq).padStart(3, '0')}`,
    pickUpCode: `A${String(seq).padStart(2, '0')}`,
    status,
    orderAmount: items.reduce((s, it) => s + it.price * it.number, 0),
    discountAmount: 0,
    remark,
    phone: '',
    cancelReason: '',
    orderTime,
    checkoutTime: orderTime,
    estimatedWaitMinutes: 10,
    orderItems: items.map((it, i) => ({ id: id * 10 + i, ...it, amount: it.price * it.number })),
  }
}

/** 播种/跨天重置：保证演示订单与取餐号计数按天一致 */
function ensureSeed(): void {
  const today = todayStr()
  const meta = dbGet<OrderMeta>(META_KEY, { date: '', orderSeq: 0, pickupSeq: 0 })
  if (meta.date === today && dbGet<StoredOrder[]>(ORDERS_KEY, []).length > 0) return

  const orders: StoredOrder[] = [
    buildDemoOrder(1, 1, 1, `${today.slice(0, 4)}-${today.slice(4, 6)}-${today.slice(6, 8)} 09:12:00`, [
      { drinkId: 1, drinkName: '生椰拿铁', image: '/menu/coconut-latte.png', specText: '大杯/冰/标准糖/标准', specKey: '1012,1021,1031,1041', price: 1890, number: 1 },
    ], '少冰'),
    buildDemoOrder(2, 2, 2, `${today.slice(0, 4)}-${today.slice(4, 6)}-${today.slice(6, 8)} 09:25:00`, [
      { drinkId: 4, drinkName: '美式咖啡', image: '/menu/americano.png', specText: '中杯/冰/无糖/加浓', specKey: '4011,4021,4034,4042', price: 1190, number: 2 },
    ]),
    buildDemoOrder(3, 3, 3, `${today.slice(0, 4)}-${today.slice(4, 6)}-${today.slice(6, 8)} 09:31:00`, [
      { drinkId: 12, drinkName: '巴斯克芝士蛋糕', image: '/menu/basque-cheesecake.png', specText: '标准装/冷藏', specKey: '12011,12021', price: 1990, number: 1 },
      { drinkId: 5, drinkName: '拿铁咖啡', image: '/menu/latte.png', specText: '中杯/热/半糖/标准', specKey: '5011,5022,5032,5041', price: 1390, number: 1 },
    ]),
  ]
  writeOrders(orders)
  dbSet<OrderMeta>(META_KEY, { date: today, orderSeq: 3, pickupSeq: 3 })
}

/** 8.8 提交订单（从购物车结算；下单成功后清空购物车；支持优惠券核销） */
export async function mockSubmitOrder(dto: OrderSubmitDTO): Promise<Result<OrderSubmitVO>> {
  ensureSeed()
  const cartRes = await mockGetCartList()
  const items = cartRes.data as ShoppingCartVO[]
  if (!items.length) return fail('购物车为空，先去挑一杯吧')

  // 商品总额（分）
  const goodsAmount = items.reduce((s, it) => s + it.amount * it.number, 0)

  // 优惠券核销（服务端职责：校验可用性 + 重算金额，客户端传值不可信）
  let discountAmount = 0
  let usedCoupon: ReturnType<typeof burnCoupon> = null
  if (dto.userCouponId) {
    const myRes = await mockGetMyCoupons()
    const mine = (myRes.data as UserCouponVO[]).find((c) => c.id === dto.userCouponId)
    if (!mine) return fail('优惠券不存在')
    if (mine.status !== 1) return fail('优惠券不可用（已核销或已过期）')
    if (goodsAmount < mine.threshold) return fail(`该券满 ${fmtPrice(mine.threshold)} 元可用，还差一点`)
    usedCoupon = { ...mine }
    discountAmount = mine.discount
    if (discountAmount > goodsAmount) discountAmount = goodsAmount
  }

  const orderAmount = Math.max(0, goodsAmount - discountAmount)
  const today = todayStr()
  const meta = dbGet<OrderMeta>(META_KEY, { date: '', orderSeq: 0, pickupSeq: 0 })
  const orderSeq = meta.date === today ? meta.orderSeq + 1 : 1
  const pickupSeq = meta.date === today ? meta.pickupSeq + 1 : 1
  dbSet<OrderMeta>(META_KEY, { date: today, orderSeq, pickupSeq })

  const nowDate = new Date()
  const order: StoredOrder = {
    id: 100 + orderSeq,
    orderNumber: `${today}${String(orderSeq).padStart(3, '0')}`,
    pickUpCode: `A${String(pickupSeq).padStart(2, '0')}`,
    status: 1,
    orderAmount,
    discountAmount,
    remark: dto.remark?.trim() || '',
    phone: dto.phone?.trim() || '',
    cancelReason: '',
    orderTime: fmtDateTime(nowDate),
    checkoutTime: fmtDateTime(nowDate),
    estimatedWaitMinutes: Math.min(15, 8 + items.reduce((s, it) => s + it.number, 0)),
    orderItems: items.map((row, i) => ({
      id: (100 + orderSeq) * 10 + i,
      drinkId: row.drinkId,
      drinkName: row.drinkName,
      image: row.image,
      specText: row.specText,
      specKey: row.specKey,
      price: row.amount,
      number: row.number,
      amount: row.amount * row.number,
    })),
  }
  if (dto.userCouponId) order.userCouponId = dto.userCouponId
  const orders = readOrders()
  orders.unshift(order)
  writeOrders(orders)

  // 金额落库成功后再核销优惠券（演示简化；真实后端应与下单同一事务）
  if (dto.userCouponId && usedCoupon) {
    burnCoupon(dto.userCouponId, order.orderNumber)
  }

  // 对应苍穹外卖逻辑：下单成功清空购物车
  await mockCleanCart()

  const vo: OrderSubmitVO = {
    id: order.id,
    orderNumber: order.orderNumber,
    pickUpCode: order.pickUpCode,
    orderAmount: order.orderAmount,
    discountAmount: order.discountAmount,
    estimatedWaitMinutes: order.estimatedWaitMinutes,
  }
  return ok(vo)
}

/** 8.9 按取餐号查询订单 */
export function mockGetOrderByPickUpCode(code: string): Promise<Result<OrderVO>> {
  ensureSeed()
  const key = code.trim().toUpperCase()
  if (!key) return delay(fail('请输入取餐号'))
  const order = readOrders().find((o) => o.pickUpCode === key)
  if (!order) return delay(fail('未查询到订单，请核对取餐号'))
  return delay(ok({ ...order }))
}

/** 8.10 催单（仅 1/2 状态可催单） */
export function mockReminderOrder(id: number): Promise<Result<null>> {
  const orders = readOrders()
  const order = orders.find((o) => o.id === id)
  if (!order) return delay(fail('订单不存在'))
  if (order.status !== 1 && order.status !== 2) return delay(fail('当前状态不可催单'))
  order.remindedAt = fmtDateTime(new Date())
  writeOrders(orders)
  return delay(ok(null))
}

/** 8.11 用户取消订单（仅状态 1 待接单可取消） */
export function mockCancelOrder(id: number): Promise<Result<null>> {
  const orders = readOrders()
  const order = orders.find((o) => o.id === id)
  if (!order) return delay(fail('订单不存在'))
  if (order.status !== 1) return delay(fail('当前状态不可取消'))
  order.status = 5
  order.cancelReason = '用户主动取消'
  writeOrders(orders)
  return delay(ok(null))
}

/**
 * 演示推进（仅 mock）：把订单状态沿真实状态机推进一步
 * 1 待接单 → 2 制作中 → 3 待取餐 → 4 已完成
 * 用于单人演示时模拟「商家侧操作」（对应管理端接单/出餐叫号/完成）
 */
export function mockAdvanceOrder(id: number): Promise<Result<OrderVO>> {
  const orders = readOrders()
  const order = orders.find((o) => o.id === id)
  if (!order) return delay(fail('订单不存在'))
  if (order.status === 5) return delay(fail('订单已取消，无法推进'))
  if (order.status === 4) return delay(fail('订单已完成'))
  const next = (order.status + 1) as OrderStatus
  order.status = next
  if (next === 4) order.estimatedWaitMinutes = 0
  writeOrders(orders)
  return delay(ok({ ...order }))
}

/**
 * 13.5 会员概要（演示口径）：
 * 积分 = 积分账本余额（消费积分 + 签到积分 - 兑换消耗，见 mock/points.ts）；
 * 累计消费仅统计已完成订单
 */
export function mockGetMemberSummary(): Promise<Result<MemberSummaryVO>> {
  ensureSeed()
  const finished = readOrders().filter((o) => o.status === 4)
  const totalSpent = finished.reduce((s, o) => s + o.orderAmount, 0)
  const couponsAvailable = countUsableCouponsSync()
  const points = getPointSummarySync().balance
  return delay(
    ok({
      memberNo: 'SHY-2026-0001',
      points,
      totalSpent,
      finishedOrders: finished.length,
      couponsAvailable,
    }),
  )
}

/** 重置演示数据时连带清空订单与计数 */
export function mockResetOrders(): void {
  dbRemove(ORDERS_KEY)
  dbRemove(META_KEY)
  ensureSeed()
}
