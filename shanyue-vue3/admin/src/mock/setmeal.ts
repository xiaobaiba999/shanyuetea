/* 套餐模块 mock（v2.0 新增）：严格模拟 /api/setmeal/* 行为
 * - setmealDrinkes 整体提交、先删旧后插新
 * - 新增默认停售（对齐课程 StatusConstant.DISABLE，需手动起售）
 * - 起售校验：套餐内所有饮品必须起售（对齐课程 SetmealEnableFailedException）
 */
import type { PageResult, Result, Setmeal, SetmealDTO, SetmealPageQuery, SetmealDrink } from '@/types'
import { clone, dbStore, fail, nextId, now, ok } from './db'
import { seedCategories } from './seed'

function categoryNameOf(categoryId: number): string | undefined {
  return seedCategories.find((c) => c.id === categoryId)?.name
}

/** 回填 setmealDrinkes 冗余字段（name/price 取饮品当前值，对齐后端行为） */
function hydrateDrinkes(dto: SetmealDTO): SetmealDrink[] {
  const drinks = dbStore.drinks.load()
  return dto.setmealDrinkes.map((sd) => {
    const d = drinks.find((x) => x.id === sd.drinkId)
    return {
      id: nextId('orderItem'), // 关联行 id 与订单明细共用一段自增（mock 简化）
      drinkId: sd.drinkId,
      name: d?.name ?? sd.name,
      price: d?.price ?? sd.price,
      copies: sd.copies,
    }
  })
}

export function mockSetmealPage(query: SetmealPageQuery): Result<PageResult<Setmeal>> {
  let list = dbStore.setmeals.load()
  const name = query.name?.trim()
  if (name) list = list.filter((s) => s.name.includes(name))
  if (query.categoryId != null) list = list.filter((s) => s.categoryId === query.categoryId)
  if (query.status != null) list = list.filter((s) => s.status === query.status)
  list = [...list].sort((a, b) => b.id - a.id)
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = clone(list.slice(start, start + query.pageSize))
  return ok({ total, records })
}

export function mockGetSetmeal(id: number): Result<Setmeal> {
  const setmeal = dbStore.setmeals.load().find((s) => s.id === id)
  if (!setmeal) return fail('套餐不存在')
  return ok(clone(setmeal))
}

export function mockAddSetmeal(dto: SetmealDTO): Result<null> {
  const list = dbStore.setmeals.load()
  if (!dto.setmealDrinkes.length) return fail('套餐内至少包含一款饮品')
  const t = now()
  const setmeal: Setmeal = {
    id: nextId('setmeal'),
    categoryId: dto.categoryId,
    name: dto.name.trim(),
    price: dto.price,
    image: dto.image ?? '',
    description: dto.description ?? '',
    // 对齐课程：新增套餐默认停售，由商家确认内容后手动起售
    status: dto.status ?? 0,
    updateTime: t,
    categoryName: categoryNameOf(dto.categoryId),
    setmealDrinkes: hydrateDrinkes(dto),
  }
  list.push(setmeal)
  dbStore.setmeals.save(list)
  return ok(null)
}

export function mockUpdateSetmeal(dto: SetmealDTO): Result<null> {
  const list = dbStore.setmeals.load()
  const setmeal = list.find((s) => s.id === dto.id)
  if (!setmeal) return fail('套餐不存在')
  if (!dto.setmealDrinkes.length) return fail('套餐内至少包含一款饮品')
  setmeal.categoryId = dto.categoryId
  setmeal.name = dto.name.trim()
  setmeal.price = dto.price
  setmeal.image = dto.image ?? ''
  setmeal.description = dto.description ?? ''
  setmeal.categoryName = categoryNameOf(dto.categoryId)
  setmeal.updateTime = now()
  // 关联关系整体覆盖（先删旧后插新）
  setmeal.setmealDrinkes = hydrateDrinkes(dto)
  dbStore.setmeals.save(list)
  return ok(null)
}

export function mockDeleteSetmeals(ids: number[]): Result<null> {
  const list = dbStore.setmeals.load()
  const next = list.filter((s) => !ids.includes(s.id))
  if (next.length === list.length) return fail('套餐不存在')
  dbStore.setmeals.save(next)
  return ok(null)
}

export function mockSetmealStatus(status: 0 | 1, id: number): Result<null> {
  const list = dbStore.setmeals.load()
  const setmeal = list.find((s) => s.id === id)
  if (!setmeal) return fail('套餐不存在')
  if (status === 1) {
    // 起售校验（对齐课程 SetmealEnableFailedException：套餐内包含无法售卖的商品）
    const drinks = dbStore.drinks.load()
    const offSale = setmeal.setmealDrinkes
      .map((sd) => drinks.find((d) => d.id === sd.drinkId))
      .filter((d) => d && d.status === 0)
      .map((d) => d!.name)
    if (offSale.length) {
      return fail(`套餐内包含停售饮品（${offSale.join('、')}），无法起售`)
    }
  }
  setmeal.status = status
  setmeal.updateTime = now()
  dbStore.setmeals.save(list)
  return ok(null)
}
