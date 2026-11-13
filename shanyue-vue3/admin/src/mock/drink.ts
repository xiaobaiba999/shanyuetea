/* 饮品模块 mock：严格模拟 /api/drink/* 行为（specGroups.options 整体提交、先删旧后插新、monthSales） */
import type { Drink, DrinkDTO, DrinkPageQuery, PageResult, Result, SpecGroup, SpecOption } from '@/types'
import { clone, dbStore, fail, nextId, now, ok } from './db'
import { seedCategories } from './seed'

export function mockDrinkPage(query: DrinkPageQuery): Result<PageResult<Drink>> {
  let list = dbStore.drinks.load()
  const name = query.name?.trim()
  if (name) list = list.filter((d) => d.name.includes(name))
  if (query.categoryId != null) list = list.filter((d) => d.categoryId === query.categoryId)
  if (query.status != null) list = list.filter((d) => d.status === query.status)
  // 新增的排前面（近似按更新时间倒序）
  list = [...list].sort((a, b) => b.id - a.id)
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  // 分页行 = 全量 DrinkVO：补 categoryName（对齐 API 文档 3.1，后端由联表带出）
  const records = clone(list.slice(start, start + query.pageSize)).map(withCategoryName)
  return ok({ total, records })
}

export function mockGetDrink(id: number): Result<Drink> {
  const drink = dbStore.drinks.load().find((d) => d.id === id)
  if (!drink) return fail('饮品不存在')
  return ok(clone(drink))
}

/** GET /drink/list?categoryId=（套餐选品用；不传 categoryId 返回全部） */
export function mockDrinkList(categoryId?: number): Result<Drink[]> {
  let list = [...dbStore.drinks.load()]
  if (categoryId != null) list = list.filter((d) => d.categoryId === categoryId)
  list.sort((a, b) => a.categoryId - b.categoryId || a.id - b.id)
  return ok(clone(list))
}

/** 为提交上来的规格组重新分配 id（先删旧后插新策略下，id 不复用） */
function rebuildSpecGroups(groups: SpecGroup[]): SpecGroup[] {
  return groups.map((g, gi) => {
    const options: SpecOption[] = (g.options ?? []).map((o, oi) => ({
      id: nextId('optionId'),
      name: o.name,
      priceDelta: o.priceDelta ?? 0,
      sort: oi + 1,
    }))
    const group: SpecGroup = {
      id: nextId('groupId'),
      name: g.name,
      required: g.required ?? 1, // 山月 UI 扩展字段（苍穹 DTO 无，提交时后端忽略）
      sort: gi + 1,
      options,
    }
    return group
  })
}

/** 填充分类名称（DrinkVO.categoryName 冗余字段，对齐苍穹联调行为） */
function withCategoryName(d: Drink): Drink {
  return { ...d, categoryName: seedCategories.find((c) => c.id === d.categoryId)?.name }
}

export function mockAddDrink(dto: DrinkDTO): Result<number> {
  const list = dbStore.drinks.load()
  const t = now()
  const drink: Drink = {
    id: nextId('drink'),
    name: dto.name.trim(),
    categoryId: dto.categoryId,
    price: dto.price,
    image: dto.image,
    description: dto.description ?? '',
    featured: dto.featured ?? 0,
    status: dto.status ?? 1,
    monthSales: 0,
    categoryName: seedCategories.find((c) => c.id === dto.categoryId)?.name,
    updateTime: t,
    specGroups: rebuildSpecGroups(dto.specGroups ?? []),
  }
  list.push(drink)
  dbStore.drinks.save(list)
  return ok(drink.id)
}

export function mockUpdateDrink(dto: DrinkDTO): Result<null> {
  const list = dbStore.drinks.load()
  const drink = list.find((d) => d.id === dto.id)
  if (!drink) return fail('饮品不存在')
  drink.name = dto.name.trim()
  drink.categoryId = dto.categoryId
  drink.price = dto.price
  drink.image = dto.image
  drink.description = dto.description ?? ''
  drink.featured = dto.featured ?? 0
  drink.categoryName = seedCategories.find((c) => c.id === dto.categoryId)?.name
  drink.updateTime = now()
  // 规格组整体覆盖（与契约约定的“先删旧后插新”一致）
  drink.specGroups = rebuildSpecGroups(dto.specGroups ?? [])
  dbStore.drinks.save(list)
  return ok(null)
}

export function mockDeleteDrinks(ids: number[]): Result<null> {
  const list = dbStore.drinks.load()
  const next = list.filter((d) => !ids.includes(d.id))
  if (next.length === list.length) return fail('饮品不存在')
  // 对齐课程删除约束：饮品被套餐关联时不可删（先在套餐管理里移除）
  const setmeals = dbStore.setmeals.load()
  const ref = setmeals.filter((s) => s.setmealDrinkes.some((sd) => ids.includes(sd.drinkId)))
  if (ref.length) return fail(`该饮品已被套餐「${ref[0].name}」关联，无法删除`)
  dbStore.drinks.save(next)
  return ok(null)
}

export function mockDrinkStatus(status: 0 | 1, id: number): Result<null> {
  const list = dbStore.drinks.load()
  const drink = list.find((d) => d.id === id)
  if (!drink) return fail('饮品不存在')
  drink.status = status
  drink.updateTime = now()
  dbStore.drinks.save(list)
  return ok(null)
}
