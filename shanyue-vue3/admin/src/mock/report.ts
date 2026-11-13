/* =========================================================
   数据统计 mock：/api/report/*（v2.0 契约：列表字段一律为逗号分隔字符串）
   - turnoverStatistics  营业额统计  → { dateList, turnoverList }（元）
   - userStatistics      用户统计    → { dateList, totalUserList, newUserList }
   - ordersStatistics    订单统计    → { dateList, orderCountList, validOrderCountList,
                                        totalOrderCount, validOrderCount, orderCompletionRate }
   - top10               销量 TOP10  → { nameList, numberList }
   说明：历史日期没有真实 mock 订单，用「日期做种子的确定性伪随机」生成观感数据 ——
   同一天刷新页面数值不变，今日柱始终取真实统计，保证「趋势图与统计卡互相印证」
   （对齐真实后端 GROUP BY 行为）。有效订单口径：status ≠ 6（已取消）。
   ========================================================= */
import type { OrdersReportVO, Result, SalesTop10ReportVO, TurnoverReportVO, UserReportVO } from '@/types'
import { dbStore, ok } from './db'
import { ymd } from '@/utils/format'

/* ---------------- 内部工具 ---------------- */

/** 字符串 → 32 位种子（简单 FNV-1a） */
function hashSeed(str: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

/** mulberry32 伪随机：同一 seed 输出序列稳定 */
function mulberry32(seed: number): () => number {
  let a = seed
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** [min, max) 区间取整 */
function randInt(rand: () => number, min: number, max: number): number {
  return min + Math.floor(rand() * (max - min))
}

/** begin/end（yyyy-MM-dd，闭区间）→ 日期数组 */
function eachDay(begin: string, end: string): Date[] {
  const days: Date[] = []
  const start = new Date(`${begin}T00:00:00`)
  const stop = new Date(`${end}T00:00:00`)
  for (let d = start; d <= stop; d = new Date(d.getTime() + 24 * 3600 * 1000)) days.push(d)
  return days
}

/** 修正日期：begin > end 时自动交换 */
function normalizeRange(begin: string, end: string): [string, string] {
  return begin <= end ? [begin, end] : [end, begin]
}

/** 今日真实统计（营业额/订单数，口径与 workspace 一致） */
function realToday(): { turnover: number; validOrderCount: number; totalOrderCount: number } {
  const today = ymd(new Date())
  const list = dbStore.orders.load().filter((o) => o.orderTime.startsWith(today))
  return {
    // 营业额 = 今日已完成订单金额之和
    turnover: list.filter((o) => o.status === 5).reduce((s, o) => s + o.amount, 0),
    validOrderCount: list.filter((o) => o.status !== 6).length,
    totalOrderCount: list.length,
  }
}

/** 某日观感营业额（元）：工作日 180~360 元，周末上浮约 35% */
function fakeTurnover(date: Date): number {
  const rand = mulberry32(hashSeed(`turnover-${ymd(date)}`))
  const weekend = date.getDay() === 0 || date.getDay() === 6
  const base = randInt(rand, 18000, 36000) / 100
  return Math.round(base * (weekend ? 1.35 : 1) * 100) / 100
}

/** 某日观感有效订单数：营业额 / 客单价 26~32 元 反推，保证两图口径自洽 */
function fakeOrderCount(date: Date, turnover: number): { total: number; valid: number } {
  const rand = mulberry32(hashSeed(`orders-${ymd(date)}`))
  const avgPrice = randInt(rand, 2600, 3200) / 100 // 元（26~32）
  const valid = Math.max(1, Math.round(turnover / avgPrice))
  // 总订单 = 有效订单 + 少量取消单（约 4%）
  const total = Math.round(valid / (0.94 + rand() * 0.04))
  return { total, valid }
}

/** 某日观感新增用户数 */
function fakeNewUsers(date: Date): number {
  const rand = mulberry32(hashSeed(`users-${ymd(date)}`))
  return randInt(rand, 3, 14)
}

/* ---------------- 营业额统计 ---------------- */
export function mockTurnoverStatistics(begin: string, end: string): Result<TurnoverReportVO> {
  const [b, e] = normalizeRange(begin, end)
  const today = ymd(new Date())
  const real = realToday()
  const dateList: string[] = []
  const turnoverList: number[] = []
  for (const d of eachDay(b, e)) {
    const key = ymd(d)
    dateList.push(key)
    // 今日取真实值，历史日取观感值（同一天恒定）
    turnoverList.push(key === today ? real.turnover : fakeTurnover(d))
  }
  return ok({ dateList: dateList.join(','), turnoverList: turnoverList.join(',') })
}

/* ---------------- 用户统计 ---------------- */
export function mockUserStatistics(begin: string, end: string): Result<UserReportVO> {
  const [b, e] = normalizeRange(begin, end)
  const today = ymd(new Date())
  const dateList: string[] = []
  const newUserList: number[] = []
  for (const d of eachDay(b, e)) {
    const key = ymd(d)
    dateList.push(key)
    const add = key === today ? Math.max(0, realToday().validOrderCount - 4) : fakeNewUsers(d)
    newUserList.push(add)
  }
  return ok({
    dateList: dateList.join(','),
    // 累计用户 = 存量基数 120 + 每日新增累加（与 newUserList 逐日对齐）
    totalUserList: toCumulative(newUserList, 120),
    newUserList: newUserList.join(','),
  })
}

/** 累计用户 = 存量基数 + 每日新增累加（与 newUserList 对齐） */
function toCumulative(daily: number[], base: number): string {
  let acc = base
  return daily.map((v) => (acc += v)).join(',')
}

/* ---------------- 订单统计 ---------------- */
export function mockOrdersStatistics(begin: string, end: string): Result<OrdersReportVO> {
  const [b, e] = normalizeRange(begin, end)
  const today = ymd(new Date())
  const real = realToday()
  const dateList: string[] = []
  const orderCountList: number[] = []
  const validOrderCountList: number[] = []
  for (const d of eachDay(b, e)) {
    const key = ymd(d)
    dateList.push(key)
    if (key === today) {
      orderCountList.push(real.totalOrderCount)
      validOrderCountList.push(real.validOrderCount)
    } else {
      const fake = fakeOrderCount(d, fakeTurnover(d))
      orderCountList.push(fake.total)
      validOrderCountList.push(fake.valid)
    }
  }
  const totalOrderCount = orderCountList.reduce((s, v) => s + v, 0)
  const validOrderCount = validOrderCountList.reduce((s, v) => s + v, 0)
  const orderCompletionRate = totalOrderCount ? Math.round((validOrderCount / totalOrderCount) * 100) / 100 : 0
  return ok({
    dateList: dateList.join(','),
    orderCountList: orderCountList.join(','),
    validOrderCountList: validOrderCountList.join(','),
    totalOrderCount,
    validOrderCount,
    orderCompletionRate,
  })
}

/* ---------------- 销量 TOP10 ---------------- */
/** 观感基准销量（模拟历史存量订单，演示时 TOP10 图不空） */
const BASE_SALES: Record<string, number> = {
  生椰拿铁: 96,
  美式: 82,
  桂花冰酿拿铁: 74,
  黑糖波波奶茶: 66,
  卡布奇诺: 52,
  茉莉奶绿: 47,
  黄油可颂: 43,
  焦糖玛奇朵: 38,
  澳白: 31,
  抹茶拿铁: 26,
  葡萄冷萃: 21,
  茉莉青提冷萃: 18,
  桂花乌龙: 15,
  巴斯克芝士蛋糕: 12,
}

export function mockTop10Statistics(begin: string, end: string): Result<SalesTop10ReportVO> {
  const [b, e] = normalizeRange(begin, end)
  // 1) 真实聚合：区间内已完成订单的明细 number 汇总（与后端 SQL 口径一致）
  const real = new Map<string, number>()
  for (const o of dbStore.orders.load()) {
    if (o.status !== 5) continue
    const day = o.orderTime.slice(0, 10)
    if (day < b || day > e) continue
    for (const item of o.orderDetailList) {
      real.set(item.name, (real.get(item.name) ?? 0) + item.number)
    }
  }
  // 2) 合并观感基准：演示数据饱满；真实新订单（来单模拟/接单完成）会让对应商品实时上涨
  const merged = new Map<string, number>()
  for (const [name, base] of Object.entries(BASE_SALES)) merged.set(name, base)
  for (const [name, n] of real) merged.set(name, (merged.get(name) ?? 0) + n)
  // 3) 降序取前 10 → 逗号分隔字符串
  const sorted = [...merged.entries()].sort((a, c) => c[1] - a[1]).slice(0, 10)
  return ok({ nameList: sorted.map(([k]) => k).join(','), numberList: sorted.map(([, v]) => v).join(',') })
}
