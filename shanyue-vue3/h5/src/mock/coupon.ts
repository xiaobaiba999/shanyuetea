import type { CouponTemplateVO, UserCouponVO } from '@/types'
import { dbGet, dbSet, delay, fail, ok } from './db'
import { fmtDateTime } from '@/utils/format'

/**
 * 优惠券 mock（localStorage 持久化，key = shanyue-h5-coupon-templates / shanyue-h5-user-coupons）
 * - 模板（coupon）：管理端配置的券种，C 端「券广场」领取
 * - 用户券（user_coupon）：领取时生成，含 expireTime 与状态机 1 可用 / 2 已核销 / 3 已过期
 * - 每张模板限领 1 张（面试点：真实场景还要防超发/防刷，可用 Redis decr 原子扣减库存）
 * - 核销由「提交订单」服务端完成（见 mock/order.ts），下单与领券同事务语义
 */
const TPL_KEY = 'coupon-templates'
const MY_KEY = 'user-coupons'

const DEFAULT_TEMPLATES: CouponTemplateVO[] = [
  {
    id: 1,
    name: '山月新人礼',
    type: 2,
    threshold: 0,
    discount: 500,
    validDays: 7,
    totalCount: 999,
    remainCount: 99,
    description: '无门槛立减 5 元，新客见面礼',
  },
  {
    id: 2,
    name: '午后轻松享',
    type: 1,
    threshold: 2500,
    discount: 400,
    validDays: 7,
    totalCount: 200,
    remainCount: 50,
    description: '满 25 元减 4 元',
  },
  {
    id: 3,
    name: '闺蜜同行券',
    type: 1,
    threshold: 4000,
    discount: 800,
    validDays: 7,
    totalCount: 100,
    remainCount: 30,
    description: '满 40 元减 8 元',
  },
]

function readTemplates(): CouponTemplateVO[] {
  return dbGet<CouponTemplateVO[]>(TPL_KEY, DEFAULT_TEMPLATES)
}

function writeTemplates(list: CouponTemplateVO[]): void {
  dbSet(TPL_KEY, list)
}

function readMy(): UserCouponVO[] {
  return dbGet<UserCouponVO[]>(MY_KEY, [])
}

function writeMy(list: UserCouponVO[]): void {
  dbSet(MY_KEY, list)
}

/** 过期时间是否已到（惰性判断，读列表时顺带修正状态） */
function isExpired(c: UserCouponVO): boolean {
  return new Date(c.expireTime.replace(/-/g, '/')).getTime() <= Date.now()
}

/** 惰性过期：读取时把已过「有效期」但仍标可用的券修正为 3 */
function normalize(list: UserCouponVO[]): UserCouponVO[] {
  let changed = false
  for (const c of list) {
    if (c.status === 1 && isExpired(c)) {
      c.status = 3
      changed = true
    }
  }
  if (changed) writeMy(list)
  return list
}

/** 13.1 券广场：可领模板列表（附带「我是否已领过」由前端用我的券判断） */
export function mockGetCouponTemplates(): Promise<ReturnType<typeof ok<CouponTemplateVO[]>>> {
  return delay(ok(readTemplates().map((t) => ({ ...t }))))
}

/** 13.2 领取优惠券（每模板限领 1 张） */
export function mockReceiveCoupon(templateId: number): Promise<ReturnType<typeof fail> | ReturnType<typeof ok<UserCouponVO>>> {
  const templates = readTemplates()
  const tpl = templates.find((t) => t.id === templateId)
  if (!tpl) return delay(fail('优惠券不存在'))
  if (tpl.remainCount <= 0) return delay(fail('该券已被领完啦'))

  const my = readMy()
  if (my.some((c) => c.couponId === templateId)) return delay(fail('该优惠券已领取过，每张限领 1 张'))

  const now = new Date()
  const expire = new Date(now.getTime() + tpl.validDays * 24 * 3600 * 1000)
  const coupon: UserCouponVO = {
    id: Date.now(),
    couponId: tpl.id,
    name: tpl.name,
    type: tpl.type,
    threshold: tpl.threshold,
    discount: tpl.discount,
    receiveTime: fmtDateTime(now),
    expireTime: fmtDateTime(expire),
    status: 1,
    usedOrderNumber: '',
  }
  my.push(coupon)
  writeMy(my)

  // 库存 -1（真实后端建议 Redis DECR 原子扣减防超发）
  tpl.remainCount -= 1
  writeTemplates(templates)

  return delay(ok({ ...coupon }))
}

/** 13.3 我的优惠券（status 不传 = 全部；读取时惰性修正过期状态） */
export function mockGetMyCoupons(status?: 1 | 2 | 3): Promise<ReturnType<typeof ok<UserCouponVO[]>>> {
  const list = normalize(readMy()).map((c) => ({ ...c }))
  return delay(ok(status ? list.filter((c) => c.status === status) : list))
}

/** 13.4 下单可用券：可用 + 未过期 + 满足门槛，按抵扣金额降序 */
export function mockGetUsableCoupons(goodsAmount: number): Promise<ReturnType<typeof ok<UserCouponVO[]>>> {
  const list = normalize(readMy())
    .filter((c) => c.status === 1 && goodsAmount >= c.threshold)
    .sort((a, b) => b.discount - a.discount)
    .map((c) => ({ ...c }))
  return delay(ok(list))
}

/** 重置演示数据时连带清空券（由 QueryView 的重置工具调用） */
export function mockResetCoupons(): void {
  dbSet(TPL_KEY, DEFAULT_TEMPLATES)
  dbSet(MY_KEY, [])
}

/** 同步统计可用券张数（会员概要用；读取时惰性修正过期） */
export function countUsableCouponsSync(): number {
  return normalize(readMy()).filter((c) => c.status === 1).length
}

/** mock/order.ts 核销用户券用：找到并标记已核销 */
export function burnCoupon(userCouponId: number, orderNumber: string): UserCouponVO | null {
  const my = readMy()
  const c = my.find((x) => x.id === userCouponId)
  if (!c) return null
  if (c.status !== 1 || isExpired(c)) return null
  c.status = 2
  c.usedOrderNumber = orderNumber
  writeMy(my)
  return { ...c }
}

/* ---------------- 供积分兑换模块复用的底层能力 ---------------- */

/** 同步读取券模板（积分兑换货架定价用） */
export function getTemplatesSync(): CouponTemplateVO[] {
  return readTemplates().map((t) => ({ ...t }))
}

/**
 * 直接发放一张券并扣减库存（跳过「每模板限领 1 张」——积分兑换属于用户付出对价获得，
 * 面试点：免费领券限 1 张防刷，积分兑换走另一条不限次数的发放链路但同样要原子扣库存）
 */
export function grantCouponByPoints(templateId: number): UserCouponVO | null {
  const templates = readTemplates()
  const tpl = templates.find((t) => t.id === templateId)
  if (!tpl || tpl.remainCount <= 0) return null

  const now = new Date()
  const expire = new Date(now.getTime() + tpl.validDays * 24 * 3600 * 1000)
  const coupon: UserCouponVO = {
    id: Date.now(),
    couponId: tpl.id,
    name: tpl.name,
    type: tpl.type,
    threshold: tpl.threshold,
    discount: tpl.discount,
    receiveTime: fmtDateTime(now),
    expireTime: fmtDateTime(expire),
    status: 1,
    usedOrderNumber: '',
  }
  const my = readMy()
  my.push(coupon)
  writeMy(my)

  tpl.remainCount -= 1
  writeTemplates(templates)
  return { ...coupon }
}
