import type { AddCartDTO, Result, ShoppingCartVO, SubCartDTO } from '@/types'
import { dbGet, dbSet, delay, fail, ok } from './db'
import { findDrink } from './menu-data'

/**
 * 购物车 mock（localStorage 持久化，key = shanyue-h5-cart）
 * - 相同 drinkId + specKey 合并数量（number+1）
 * - sub 减到 0 删行；clean 清空
 * - specKey 规则：选项 id 按 group.sort 排序逗号拼接（如 "1012,1021,1031,1041"）
 * - specText 规则：选项名按组拼接，如「大杯/冰/标准糖/标准」
 * - amount = 单价（含规格 priceDelta 加价，分）；行小计 = amount × number
 */
const CART_KEY = 'cart'
const CART_SEQ_KEY = 'cart-seq'

function readCart(): ShoppingCartVO[] {
  return dbGet<ShoppingCartVO[]>(CART_KEY, [])
}

function writeCart(rows: ShoppingCartVO[]): void {
  dbSet(CART_KEY, rows)
}

function nextRowId(): number {
  const seq = dbGet<number>(CART_SEQ_KEY, 0) + 1
  dbSet(CART_SEQ_KEY, seq)
  return seq
}

function now(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 8.4 添加购物车（按 groupName+optionName 匹配，服务端重算金额） */
export function mockAddCart(dto: AddCartDTO): Promise<Result<null>> {
  const drink = findDrink(dto.drinkId)
  if (!drink || drink.status !== 1) return delay(fail('商品不存在或已停售'))

  // 匹配提交的规格（ groupName+optionName -> option ）
  const chosen: { groupId: number; groupSort: number; optionId: number; optionName: string; priceDelta: number }[] = []
  for (const pair of dto.specs) {
    const group = drink.specGroups.find((gr) => gr.name === pair.groupName)
    if (!group) continue
    const opt = group.specOptions.find((o) => o.name === pair.optionName)
    if (!opt) continue
    chosen.push({ groupId: group.id, groupSort: group.sort, optionId: opt.id, optionName: opt.name, priceDelta: opt.priceDelta })
  }
  // 兜底：必选组未覆盖时自动取第一项（服务端健壮性）
  for (const group of drink.specGroups.filter((gr) => gr.required === 1)) {
    if (!chosen.some((c) => c.groupId === group.id) && group.specOptions[0]) {
      const opt = group.specOptions[0]
      chosen.push({ groupId: group.id, groupSort: group.sort, optionId: opt.id, optionName: opt.name, priceDelta: opt.priceDelta })
    }
  }
  chosen.sort((a, b) => a.groupSort - b.groupSort)

  const specKey = chosen.map((c) => String(c.optionId)).join(',')
  const specText = chosen.map((c) => c.optionName).join('/')
  const amount = drink.price + chosen.reduce((s, c) => s + c.priceDelta, 0)

  const rows = readCart()
  const exist = rows.find((r) => r.drinkId === dto.drinkId && r.specKey === specKey)
  if (exist) {
    exist.number += 1
  } else {
    rows.push({
      id: nextRowId(),
      drinkId: drink.id,
      drinkName: drink.name,
      image: drink.image,
      specText,
      specKey,
      amount,
      number: 1,
      createTime: now(),
    })
  }
  writeCart(rows)
  return delay(ok(null))
}

/** 8.5 查看购物车 */
export function mockGetCartList(): Promise<Result<ShoppingCartVO[]>> {
  return delay(ok(readCart().map((r) => ({ ...r }))))
}

/** 8.6 购物车减一个（减到 0 删行） */
export function mockSubCart(dto: SubCartDTO): Promise<Result<null>> {
  const rows = readCart()
  const idx = rows.findIndex((r) => r.drinkId === dto.drinkId && r.specKey === dto.specKey)
  if (idx === -1) return delay(fail('购物车中不存在该商品'))
  rows[idx].number -= 1
  if (rows[idx].number <= 0) rows.splice(idx, 1)
  writeCart(rows)
  return delay(ok(null))
}

/** 8.7 清空购物车 */
export function mockCleanCart(): Promise<Result<null>> {
  writeCart([])
  return delay(ok(null))
}
