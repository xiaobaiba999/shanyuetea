/* =========================================================
   山月茶咖 · Mock 共享持久层（localStorage）
   key 前缀：shanyue-admin-db-（重置演示数据 = 清空这些 key 后 reload，
   登录态 shanyue-admin-token / shanyue-admin-name 不在同一前缀下，不会被清除）
   ========================================================= */
import type { Category, CouponTemplate, Drink, Review, ShopSetting, Setmeal } from '@/types'
import {
  buildSeedOrders,
  buildSeedReviews,
  seedCategories,
  seedCoupons,
  seedDrinks,
  seedEmployees,
  seedSetmeals,
  seedShop,
  type SeedEmployee,
  type SeedOrder,
} from './seed'
import { fmtDateTime, ymd } from '@/utils/format'

const DB_PREFIX = 'shanyue-admin-db-'

export interface Seq {
  employee: number
  category: number
  drink: number
  groupId: number
  optionId: number
  order: number
  orderItem: number
  coupon: number
  setmeal: number
}

const defaultSeq: Seq = {
  employee: 100,
  category: 100,
  drink: 100,
  groupId: 1000,
  optionId: 5000,
  order: 100,
  orderItem: 1000,
  coupon: 100,
  setmeal: 100,
}

function save<T>(key: string, value: T): void {
  try {
    localStorage.setItem(DB_PREFIX + key, JSON.stringify(value))
  } catch (e) {
    console.warn('[shanyue-admin] localStorage 写入失败（容量不足？）', e)
  }
}

function load<T>(key: string, fallback: () => T): T {
  try {
    const raw = localStorage.getItem(DB_PREFIX + key)
    if (raw == null) {
      const v = fallback()
      save(key, v)
      return v
    }
    return JSON.parse(raw) as T
  } catch {
    return fallback()
  }
}

/** 把种子订单的日期收敛到“今天”，保证隔天打开演示数据统计卡依旧有数 */
function ensureOrdersToday(orders: SeedOrder[]): SeedOrder[] {
  const today = ymd(new Date())
  const needShift = orders.some((o) => o.seeded && !o.orderTime.startsWith(today))
  if (!needShift) return orders
  return orders.map((o) => {
    if (!o.seeded) return o
    const t = new Date(agoTimeOf(o.seedMinutesAgo ?? 120))
    const c = new Date(t.getTime() + 2 * 60_000)
    return { ...o, orderTime: fmtDateTime(t), checkoutTime: fmtDateTime(c) }
  })
}

function agoTime(minutesAgo: number): number {
  const now = new Date()
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const minutesSinceMidnight = Math.floor((now.getTime() - startOfDay) / 60000)
  const m = Math.min(minutesAgo, Math.max(2, minutesSinceMidnight - 2))
  return Math.max(startOfDay + 60_000, now.getTime() - m * 60_000)
}

export const dbStore = {
  employees: {
    load: (): SeedEmployee[] => load<SeedEmployee[]>('employees', () => seedEmployees),
    save: (v: SeedEmployee[]) => save('employees', v),
  },
  categories: {
    load: (): Category[] => load<Category[]>('categories', () => seedCategories),
    save: (v: Category[]) => save('categories', v),
  },
  drinks: {
    load: (): Drink[] => load<Drink[]>('drinks', () => seedDrinks),
    save: (v: Drink[]) => save('drinks', v),
  },
  setmeals: {
    load: (): Setmeal[] => load<Setmeal[]>('setmeals', () => seedSetmeals),
    save: (v: Setmeal[]) => save('setmeals', v),
  },
  orders: {
    load: (): SeedOrder[] => {
      const v = load<SeedOrder[]>('orders', () => buildSeedOrders())
      const fixed = ensureOrdersToday(v)
      if (fixed !== v) save('orders', fixed)
      return fixed
    },
    save: (v: SeedOrder[]) => save('orders', v),
  },
  shop: {
    load: (): ShopSetting => load<ShopSetting>('shop', () => ({ ...seedShop })),
    save: (v: ShopSetting) => save('shop', v),
  },
  coupons: {
    load: (): CouponTemplate[] => load<CouponTemplate[]>('coupons', () => seedCoupons.map((c) => ({ ...c }))),
    save: (v: CouponTemplate[]) => save('coupons', v),
  },
  reviews: {
    load: (): Review[] => load<Review[]>('reviews', () => buildSeedReviews()),
    save: (v: Review[]) => save('reviews', v),
  },
  seq: {
    /* 与旧版本存储合并，避免新增自增字段（如 setmeal）在旧 localStorage 里缺失导致 NaN */
    load: (): Seq => ({ ...defaultSeq, ...load<Partial<Seq>>('seq', () => ({ ...defaultSeq })) }),
    save: (v: Seq) => save('seq', v),
  },
}

/** 取自增 id */
export function nextId(field: keyof Seq): number {
  const s = dbStore.seq.load()
  const id = s[field]
  s[field] = id + 1
  dbStore.seq.save(s)
  return id
}

/** 当前时间字符串 */
export function now(): string {
  return fmtDateTime(new Date())
}

/** Result 快捷构造 */
export function ok<T>(data: T, msg = 'success'): { code: 1; msg: string; data: T } {
  return { code: 1, msg, data }
}

/** 业务失败（对应后端 code=0，前端会 ElMessage.error(msg)） */
export function fail(msg: string): { code: 0; msg: string; data: null } {
  return { code: 0, msg, data: null }
}

/** 深拷贝（防止调用方直接改动存储内对象） */
export function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

/** 重置演示数据：清空所有 shanyue-admin-db-* key（调用方自行 reload） */
export function resetMockData(): number {
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i)
    if (k && k.startsWith(DB_PREFIX)) keys.push(k)
  }
  keys.forEach((k) => localStorage.removeItem(k))
  return keys.length
}
