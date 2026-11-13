/* =========================================================
   工作台 mock：/api/workspace/*（仪表盘四接口）
   口径对齐课程 WorkspaceServiceImpl：
   - businessData：营业额=今日已完成订单金额和；有效订单=今日 status≠6；
     完成率=有效订单/总订单；客单价=营业额/有效订单；新增用户=观感值
   - overviewOrders：waitingOrders=待接单(2)；deliveredOrders=制作中+派送中/待取餐(3/4)；
     completedOrders=已完成(5)；cancelledOrders=已取消(6)；allOrders=全部
   - overviewDrinkes / overviewSetmeals：起售 sold / 停售 discontinued
   ========================================================= */
import type { BusinessDataVO, GoodsOverviewVO, OrdersOverviewVO, Result } from '@/types'
import { dbStore, ok } from './db'
import { ymd } from '@/utils/format'

/** 观感今日新增用户（与 report 用户统计口径一致：确定性伪随机） */
function fakeNewUsersToday(): number {
  let h = 0x811c9dc5
  const key = `users-${ymd(new Date())}`
  for (let i = 0; i < key.length; i++) {
    h ^= key.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return 3 + (h >>> 0) % 12
}

export function mockBusinessData(): Result<BusinessDataVO> {
  const today = ymd(new Date())
  const list = dbStore.orders.load().filter((o) => o.orderTime.startsWith(today))
  const turnover = list.filter((o) => o.status === 5).reduce((s, o) => s + o.amount, 0)
  const validOrderCount = list.filter((o) => o.status !== 6).length
  const totalOrderCount = list.length
  const orderCompletionRate = totalOrderCount ? Math.round((validOrderCount / totalOrderCount) * 100) / 100 : 0
  // 客单价（元）：保留两位小数（v2.1 价格单位元）
  const unitPrice = validOrderCount ? Math.round((turnover / validOrderCount) * 100) / 100 : 0
  return ok({
    turnover,
    validOrderCount,
    orderCompletionRate,
    unitPrice,
    newUsers: fakeNewUsersToday(),
  })
}

export function mockOverviewOrders(): Result<OrdersOverviewVO> {
  const list = dbStore.orders.load()
  const count = (s: number) => list.filter((o) => o.status === s).length
  return ok({
    waitingOrders: count(2),
    deliveredOrders: count(3) + count(4),
    completedOrders: count(5),
    cancelledOrders: count(6),
    allOrders: list.length,
  })
}

export function mockOverviewDrinkes(): Result<GoodsOverviewVO> {
  const list = dbStore.drinks.load()
  return ok({
    sold: list.filter((d) => d.status === 1).length,
    discontinued: list.filter((d) => d.status === 0).length,
  })
}

export function mockOverviewSetmeals(): Result<GoodsOverviewVO> {
  const list = dbStore.setmeals.load()
  return ok({
    sold: list.filter((s) => s.status === 1).length,
    discontinued: list.filter((s) => s.status === 0).length,
  })
}
