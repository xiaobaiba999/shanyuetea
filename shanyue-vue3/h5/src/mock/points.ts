import type { CheckinResultVO, ExchangeRuleVO, PointRecordVO, PointSummaryVO, Result } from '@/types'
import { dbGet, dbSet, dbRemove, delay, fail, ok } from './db'
import { fmtDateTime } from '@/utils/format'
import { getTemplatesSync, grantCouponByPoints } from './coupon'

/**
 * 积分体系 mock（localStorage 持久化，key = shanyue-h5-point-checkins / point-exchanges）
 * 三个积分来源 / 去向（对应后端 point_record 表的三种 type）：
 * - 消费积分：已完成订单实付金额 / 100 取整（1 元 = 1 分），按订单动态聚合，无需落表
 * - 签到积分：每日签到 +7（后端用 (user_id, date) 唯一键防重复签到）
 * - 兑换消耗：积分兑换优惠券，cost = max(20, 面额/10)（1 积分 ≈ 0.1 元面额）
 * 余额 = 消费积分 + 签到积分 - 兑换消耗；与「会员概要」的 points 同源（mock/order.ts 引用本模块）
 */
const CHECKIN_KEY = 'point-checkins'
const EXCHANGE_KEY = 'point-exchanges'
const BONUS_KEY = 'point-bonuses'

/** 每日签到奖励（面试点：真实场景签到奖励可配置化，如连续 7 天额外 +20） */
export const CHECKIN_REWARD = 7

/** 连签达成奖励：每连续签到 STREAK_TARGET 天额外 +STREAK_BONUS（7 的倍数天均可再触发） */
export const STREAK_TARGET = 7
export const STREAK_BONUS = 20

/** 签到记录：一天一条 */
interface CheckinItem {
  date: string
  time: string
}

/** 连签达成奖励记录（对应后端 point_record 的 type=4 专项行；演示并入 type=1 展示） */
interface BonusItem {
  date: string
  streak: number
  points: number
  time: string
}

/** 兑换记录 */
interface ExchangeItem {
  id: number
  templateId: number
  name: string
  cost: number
  time: string
}

/** 订单里积分计算需要的最小字段（直接读 db，避免与 order.ts 循环依赖） */
interface PointOrderRef {
  status: number
  orderAmount: number
  orderNumber: string
  orderTime: string
}

function readCheckins(): CheckinItem[] {
  return dbGet<CheckinItem[]>(CHECKIN_KEY, [])
}

function writeCheckins(list: CheckinItem[]): void {
  dbSet(CHECKIN_KEY, list)
}

function readExchanges(): ExchangeItem[] {
  return dbGet<ExchangeItem[]>(EXCHANGE_KEY, [])
}

function writeExchanges(list: ExchangeItem[]): void {
  dbSet(EXCHANGE_KEY, list)
}

function readBonuses(): BonusItem[] {
  return dbGet<BonusItem[]>(BONUS_KEY, [])
}

function writeBonuses(list: BonusItem[]): void {
  dbSet(BONUS_KEY, list)
}

/** 消费积分（已完成订单实付 / 100）+ 明细聚合行 */
function orderPointParts(): { earned: number; records: PointRecordVO[] } {
  const orders = dbGet<PointOrderRef[]>('orders', [])
  const finished = orders.filter((o) => o.status === 4)
  let earned = 0
  const records: PointRecordVO[] = finished.map((o) => {
    const p = Math.floor(o.orderAmount / 100)
    earned += p
    return {
      id: `order-${o.orderNumber}`,
      type: 2,
      title: '订单消费',
      sub: `${o.orderNumber} · 实付 ¥${(o.orderAmount / 100).toFixed(2)}`,
      points: p,
      createTime: o.orderTime,
    }
  })
  return { earned, records }
}

/** 本地时区 yyyy-MM-dd（与周历格子 / 明细 createTime 口径一致；勿用 todayStr——它是无横杠的 yyyyMMdd） */
function ymdOf(d: Date): string {
  return fmtDateTime(d).slice(0, 10)
}

/** 连续签到天数：已签今日则从今日往回数，否则从昨日（今天还能补签不影响展示） */
function calcStreak(checked: Set<string>): number {
  let streak = 0
  const cursor = new Date()
  if (!checked.has(ymdOf(cursor))) cursor.setDate(cursor.getDate() - 1)
  while (checked.has(ymdOf(cursor))) {
    streak += 1
    cursor.setDate(cursor.getDate() - 1)
  }
  return streak
}

/** 汇总积分账本（同步版本，供 mockGetMemberSummary 与兑换校验复用） */
export function getPointSummarySync(): PointSummaryVO {
  const checkins = readCheckins()
  const exchanges = readExchanges()
  const bonuses = readBonuses()
  const checkedSet = new Set(checkins.map((c) => c.date))
  const { earned: orderEarned } = orderPointParts()
  const signEarned = checkins.length * CHECKIN_REWARD
  const bonusEarned = bonuses.reduce((s, b) => s + b.points, 0)
  const totalEarned = orderEarned + signEarned + bonusEarned
  const totalRedeemed = exchanges.reduce((s, e) => s + e.cost, 0)
  const streak = calcStreak(checkedSet)
  return {
    balance: totalEarned - totalRedeemed,
    totalEarned,
    totalRedeemed,
    checkinDays: checkins.length,
    streak,
    checkedToday: checkedSet.has(ymdOf(new Date())),
    streakBonusTotal: bonusEarned,
    // 已达成当日（streak 为 7 的倍数）显示进度满格，下一次奖励重新计 7 天
    nextBonusIn: STREAK_TARGET - (streak % STREAK_TARGET),
  }
}

/** 内部别名（本模块各接口复用） */
function computeSummarySync(): PointSummaryVO {
  return getPointSummarySync()
}

/** 15.1 积分概要 */
export function mockGetPointSummary(): Promise<Result<PointSummaryVO>> {
  return delay(ok(computeSummarySync()))
}

/** 15.2 每日签到（+7；后端用唯一键防重复，这里读当日记录判断）
 * 连签奖励：签到后 streak 为 STREAK_TARGET 整数倍时额外 +STREAK_BONUS，
 * 同一日期幂等去重（后端可用 (user_id, date) 唯一键 + 事务保证）。 */
export function mockCheckin(): Promise<Result<CheckinResultVO>> {
  const checkins = readCheckins()
  const today = ymdOf(new Date())
  if (checkins.some((c) => c.date === today)) return delay(fail('今天已经签到过啦，明天再来'))
  checkins.push({ date: today, time: fmtDateTime(new Date()) })
  writeCheckins(checkins)

  // 先写签到再算 streak，判断是否触发连签奖励
  const streak = computeSummarySync().streak
  let justBonus = 0
  if (streak > 0 && streak % STREAK_TARGET === 0) {
    const bonuses = readBonuses()
    if (!bonuses.some((b) => b.date === today)) {
      bonuses.unshift({ date: today, streak, points: STREAK_BONUS, time: fmtDateTime(new Date()) })
      writeBonuses(bonuses)
      justBonus = STREAK_BONUS
    }
  }
  return delay(ok({ ...computeSummarySync(), justBonus }))
}

/** 15.3 积分明细（订单消费动态聚合行 + 签到 + 连签奖励 + 兑换，按时间倒序） */
export function mockGetPointRecords(): Promise<Result<PointRecordVO[]>> {
  const { records: orderRecords } = orderPointParts()
  const checkinRecords: PointRecordVO[] = readCheckins().map((c) => ({
    id: `checkin-${c.date}`,
    type: 1,
    title: '每日签到',
    sub: `每日签到奖励 +${CHECKIN_REWARD} 积分`,
    points: CHECKIN_REWARD,
    createTime: `${c.date} ${c.time.slice(11) || '08:00:00'}`,
  }))
  const bonusRecords: PointRecordVO[] = readBonuses().map((b) => ({
    id: `bonus-${b.date}`,
    type: 1,
    title: '🎉 连签达成奖励',
    sub: `连续签到 ${b.streak} 天 · 额外 +${b.points} 积分`,
    points: b.points,
    createTime: `${b.date} ${b.time.slice(11) || '08:00:00'}`,
  }))
  const exchangeRecords: PointRecordVO[] = readExchanges().map((e) => ({
    id: `exchange-${e.id}`,
    type: 3,
    title: '积分兑换',
    sub: `「${e.name}」已放入我的优惠券`,
    points: -e.cost,
    createTime: e.time,
  }))
  const all = [...orderRecords, ...checkinRecords, ...bonusRecords, ...exchangeRecords].sort((a, b) =>
    a.createTime < b.createTime ? 1 : -1,
  )
  return delay(ok(all))
}

/** 兑换定价：1 积分 ≈ 0.1 元面额，最低 20 积分起兑 */
export function calcCost(discount: number): number {
  return Math.max(20, Math.round(discount / 10))
}

/** 15.4 兑换货架（在售且有库存的券模板按积分定价） */
export function mockGetExchangeShelf(): Promise<Result<ExchangeRuleVO[]>> {
  const shelf = getTemplatesSync()
    .filter((t) => t.remainCount > 0)
    .map((t) => ({
      templateId: t.id,
      name: t.name,
      description: t.description,
      discount: t.discount,
      threshold: t.threshold,
      cost: calcCost(t.discount),
      remainCount: t.remainCount,
    }))
  return delay(ok(shelf))
}

/** 15.5 积分兑换优惠券（余额校验 → 扣积分 → 发券，三者应同一事务；兑换不占「限领 1 张」名额） */
export function mockExchangeCoupon(templateId: number): Promise<Result<PointSummaryVO>> {
  const tpl = getTemplatesSync().find((t) => t.id === templateId)
  if (!tpl) return delay(fail('兑换商品不存在'))
  if (tpl.remainCount <= 0) return delay(fail('该券库存不足，暂时无法兑换'))

  const s = computeSummarySync()
  const cost = calcCost(tpl.discount)
  if (s.balance < cost) return delay(fail(`积分不足，还差 ${cost - s.balance} 分`))

  const granted = grantCouponByPoints(templateId)
  if (!granted) return delay(fail('兑换失败，请稍后再试'))

  const exchanges = readExchanges()
  exchanges.unshift({
    id: Date.now(),
    templateId,
    name: tpl.name,
    cost,
    time: fmtDateTime(new Date()),
  })
  writeExchanges(exchanges)

  return delay(ok(computeSummarySync()))
}

/** 演示：注入 STREAK_TARGET-1 天连签历史（不含今天），下一次签到即触发连签奖励（仅 mock 演示用） */
export function mockSeedStreakDemo(): Promise<Result<PointSummaryVO>> {
  const days = STREAK_TARGET - 1
  const list: CheckinItem[] = []
  const cursor = new Date()
  // 从昨天往回推 days 天，跳过今天
  for (let i = 1; i <= days; i++) {
    const d = new Date(cursor)
    d.setDate(cursor.getDate() - i)
    list.push({ date: ymdOf(d), time: fmtDateTime(d) })
  }
  writeCheckins(list)
  writeBonuses([]) // 连签历史从零开始，奖励由下一次真实签到触发
  return delay(ok(computeSummarySync()))
}

/** 重置演示数据时连带清空积分（dbClearAll 已按前缀清库，这里兜底显式移除） */
export function mockResetPoints(): void {
  dbRemove(CHECKIN_KEY)
  dbRemove(EXCHANGE_KEY)
  dbRemove(BONUS_KEY)
}
