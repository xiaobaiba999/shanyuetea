/* 订单模块 mock：严格模拟 /api/order/* 行为（苍穹 6 状态机 + 双配送模式）
 * 状态机：1 待付款 / 2 待接单 / 3 已接单(制作中) / 4 派送中·待取餐 / 5 已完成 / 6 已取消
 * 动作（对齐课程守卫，非法流转返回 code=0）：
 *   confirm    2→3   接单
 *   rejection  2→6   拒单（rejectionReason，同时写 cancelReason）
 *   cancel     1/2→6 取消（cancelReason）
 *   delivery   3→4   自取=出餐转「待取餐」；外送=开始派送转「派送中」
 *   complete   4→5   自取=确认取餐；外送=确认送达
 */
import type { DeliveryMode, OrderSearchQuery, OrderStatistics, OrderVO, PageResult, Result } from '@/types'
import { clone, dbStore, fail, nextId, now, ok } from './db'
import type { SeedOrder } from './seed'
import { seedDrinks } from './seed'
import { fmtDateTime } from '@/utils/format'

const DELIVERY_FEE = 5

export function mockOrderSearch(query: OrderSearchQuery): Result<PageResult<OrderVO>> {
  let list: SeedOrder[] = dbStore.orders.load()
  const number = query.number?.trim()
  if (number) list = list.filter((o) => o.number.includes(number))
  const phone = query.phone?.trim()
  if (phone) list = list.filter((o) => (o.phone || '').includes(phone))
  // status=-1 视为全部（api 层已剥掉，这里兜底）
  if (query.status != null && query.status !== -1) {
    list = list.filter((o) => o.status === query.status)
  }
  if (query.deliveryMode != null && query.deliveryMode !== 0) {
    list = list.filter((o) => o.deliveryMode === query.deliveryMode)
  }
  if (query.beginTime) list = list.filter((o) => o.orderTime >= query.beginTime!)
  if (query.endTime) list = list.filter((o) => o.orderTime <= query.endTime!)
  list = [...list].sort((a, b) => (a.orderTime < b.orderTime ? 1 : -1))
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = clone(list.slice(start, start + query.pageSize)) as OrderVO[]
  return ok({ total, records })
}

/** 统计三卡口径（苍穹 OrderStatisticsVO）：待接单(2) / 已接单制作中(3) / 派送中·待取餐(4) */
export function mockOrderStatistics(): Result<OrderStatistics> {
  const list = dbStore.orders.load()
  const count = (s: number) => list.filter((o) => o.status === s).length
  return ok({
    toBeConfirmed: count(2),
    confirmed: count(3),
    deliveryInProgress: count(4),
  })
}

export function mockOrderDetails(id: number): Result<OrderVO> {
  const order = dbStore.orders.load().find((o) => o.id === id)
  if (!order) return fail('订单不存在')
  return ok(clone(order) as OrderVO)
}

/* ---- 状态流转（非法流转 code=0，与后端守卫一致） ---- */
export function mockConfirmOrder(id: number): Result<null> {
  const list = dbStore.orders.load()
  const order = list.find((o) => o.id === id) as SeedOrder | undefined
  if (!order) return fail('订单不存在')
  if (order.status !== 2) return fail('当前状态不可接单')
  order.status = 3
  dbStore.orders.save(list)
  return ok(null)
}

export function mockRejectOrder(id: number, rejectionReason: string): Result<null> {
  const list = dbStore.orders.load()
  const order = list.find((o) => o.id === id) as SeedOrder | undefined
  if (!order) return fail('订单不存在')
  if (order.status !== 2) return fail('仅待接单订单可以拒单')
  const reason = rejectionReason?.trim()
  if (!reason) return fail('请填写拒单原因')
  order.status = 6
  order.rejectionReason = reason
  order.cancelReason = reason // 与课程一致：拒单原因同步写入取消原因，C 端可见
  dbStore.orders.save(list)
  return ok(null)
}

export function mockCancelOrder(id: number, cancelReason: string): Result<null> {
  const list = dbStore.orders.load()
  const order = list.find((o) => o.id === id) as SeedOrder | undefined
  if (!order) return fail('订单不存在')
  if (order.status !== 1 && order.status !== 2) return fail('当前状态不可取消')
  order.status = 6
  order.cancelReason = cancelReason?.trim() || '商家取消'
  dbStore.orders.save(list)
  return ok(null)
}

export function mockDeliveryOrder(id: number): Result<null> {
  const list = dbStore.orders.load()
  const order = list.find((o) => o.id === id) as SeedOrder | undefined
  if (!order) return fail('订单不存在')
  if (order.status !== 3) return fail('当前状态不可出餐/派送，请先接单')
  order.status = 4
  if (order.deliveryMode === 2) {
    order.estimatedDeliveryTime = fmtAfter(40)
  }
  dbStore.orders.save(list)
  return ok(null)
}

export function mockCompleteOrder(id: number): Promise<Result<null>> | Result<null> {
  const list = dbStore.orders.load()
  const order = list.find((o) => o.id === id) as SeedOrder | undefined
  if (!order) return fail('订单不存在')
  if (order.status !== 4) {
    return fail(order.deliveryMode === 2 ? '请先开始派送，再确认送达' : '请先出餐，再确认顾客取餐')
  }
  order.status = 5
  dbStore.orders.save(list)
  return ok(null)
}

function fmtAfter(minutes: number): string {
  return fmtDateTime(new Date(Date.now() + minutes * 60_000))
}

/* ---------------- 来单模拟（mock 专属，供 order-simulator 调用） ----------------
 * 模拟 C 端新订单：支付完成进入待接单（status=2, payStatus=1）；
 * 外送货到付款单 payStatus=0（送达后收款）。双模式随机：约 2/3 自取、1/3 外送。 */
const REMARK_POOL = ['少冰，谢谢', '不要糖', '加浓一点', '麻烦快点，赶时间', '多加冰', '', '', '']
const PHONE_POOL = ['13800001234', '13911115678', '13722228901']
const NEW_ADDRESS_POOL = [
  { consignee: '周砚秋', phone: '13666607777', deliveryAddress: '文一西路 398 号 5 幢 1201' },
  { consignee: '叶未眠', phone: '13555502222', deliveryAddress: '科创园区 3 号楼 A 座 8 层' },
  { consignee: '江疏影', phone: '13444403333', deliveryAddress: '古墩路 701 号紫金广场 21 楼' },
]

export function mockCreateOrder(_input?: { remark?: string; phone?: string }): Result<OrderVO> {
  const drinks = seedDrinks.filter((d) => d.status === 1)
  if (!drinks.length) return fail('暂无可售饮品')
  const list = dbStore.orders.load()
  const today = now().slice(0, 10)
  const todaySeq = list.filter((o) => o.orderTime.startsWith(today)).length + 1

  // 随机 1~2 款饮品，每组随机选一个规格项
  const picked = [...drinks].sort(() => Math.random() - 0.5).slice(0, Math.random() < 0.65 ? 1 : 2)
  const orderDetailList = picked.map((drink, idx) => {
    const snapshot: Array<{ groupName: string; optionName: string; priceDelta: number }> = []
    let unit = drink.price
    for (const g of [...drink.specGroups].sort((a, b) => a.sort - b.sort)) {
      const opts = [...g.options].sort((a, b) => a.sort - b.sort)
      const opt = opts[Math.floor(Math.random() * opts.length)]
      unit += opt.priceDelta
      snapshot.push({ groupName: g.name, optionName: opt.name, priceDelta: opt.priceDelta })
    }
    return {
      id: nextId('orderItem') + idx,
      name: drink.name,
      drinkId: drink.id,
      specSelected: JSON.stringify(snapshot),
      number: Math.random() < 0.75 ? 1 : 2,
      amount: unit,
      image: drink.image,
    }
  })

  const mode: DeliveryMode = Math.random() < 2 / 3 ? 1 : 2
  const cod = mode === 2 && Math.random() < 0.35 // 外送货到付款
  const addr = mode === 2 ? NEW_ADDRESS_POOL[Math.floor(Math.random() * NEW_ADDRESS_POOL.length)] : null
  const pickupSeq = list.filter((o) => o.deliveryMode === 1).length + 1

  const order: SeedOrder = {
    id: nextId('order'),
    number: `${today.replace(/\D/g, '')}${String(todaySeq).padStart(3, '0')}`,
    status: 2, // 已支付，进入待接单
    payStatus: cod ? 0 : 1,
    deliveryMode: mode,
    pickupNumber: mode === 1 ? `A${String(pickupSeq).padStart(2, '0')}` : '',
    deliveryFee: mode === 2 ? DELIVERY_FEE : undefined,
    consignee: addr?.consignee,
    phone: _input?.phone ?? addr?.phone ?? PHONE_POOL[Math.floor(Math.random() * PHONE_POOL.length)],
    deliveryAddress: addr?.deliveryAddress,
    amount:
      orderDetailList.reduce((s, it) => s + it.amount * it.number, 0) + (mode === 2 ? DELIVERY_FEE : 0),
    remark: _input?.remark ?? REMARK_POOL[Math.floor(Math.random() * REMARK_POOL.length)],
    cancelReason: '',
    rejectionReason: '',
    orderTime: now(),
    checkoutTime: cod ? '' : now(),
    estimatedDeliveryTime: mode === 2 ? fmtAfter(40) : undefined,
    orderDetailList,
    seedMinutesAgo: 0,
    seeded: false,
  }
  list.push(order)
  dbStore.orders.save(list)
  const { seeded: _s, seedMinutesAgo: _m, ...vo } = order
  return ok(vo as OrderVO)
}
