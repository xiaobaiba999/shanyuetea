/* 分类模块 mock：严格模拟 /api/category/* 行为（type：1 菜品 / 2 套餐；分类下有商品/套餐不可删） */
import type { Category, CategoryDTO, CategoryPageQuery, PageResult, Result } from '@/types'
import { clone, dbStore, fail, nextId, now, ok } from './db'

export function mockCategoryPage(query: CategoryPageQuery): Result<PageResult<Category>> {
  let list = dbStore.categories.load()
  const name = query.name?.trim()
  if (name) list = list.filter((c) => c.name.includes(name))
  if (query.type != null) list = list.filter((c) => c.type === query.type)
  list = [...list].sort((a, b) => a.type - b.type || a.sort - b.sort)
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = clone(list.slice(start, start + query.pageSize))
  return ok({ total, records })
}

export function mockCategoryList(type?: 1 | 2): Result<Category[]> {
  let list = [...dbStore.categories.load()]
  if (type != null) list = list.filter((c) => c.type === type)
  list.sort((a, b) => a.type - b.type || a.sort - b.sort)
  return ok(clone(list))
}

export function mockAddCategory(dto: CategoryDTO): Result<null> {
  const list = dbStore.categories.load()
  const name = dto.name.trim()
  if (!name) return fail('分类名称不能为空')
  if (list.some((c) => c.name === name)) return fail('分类名称已存在')
  const t = now()
  list.push({
    id: nextId('category'),
    type: dto.type,
    name,
    sort: dto.sort ?? 99,
    status: 1,
    createTime: t,
    updateTime: t,
  })
  dbStore.categories.save(list)
  return ok(null)
}

export function mockUpdateCategory(dto: CategoryDTO): Result<null> {
  const list = dbStore.categories.load()
  const cat = list.find((c) => c.id === dto.id)
  if (!cat) return fail('分类不存在')
  const name = dto.name.trim()
  if (!name) return fail('分类名称不能为空')
  if (list.some((c) => c.id !== dto.id && c.name === name)) return fail('分类名称已存在')
  cat.type = dto.type
  cat.name = name
  cat.sort = dto.sort
  cat.updateTime = now()
  dbStore.categories.save(list)
  return ok(null)
}

export function mockDeleteCategory(id: number): Result<null> {
  const list = dbStore.categories.load()
  const cat = list.find((c) => c.id === id)
  if (!cat) return fail('分类不存在')
  // 与真实后端一致的删除约束：菜品分类下有饮品、套餐分类下有套餐时不可删
  if (cat.type === 1 && dbStore.drinks.load().some((d) => d.categoryId === id)) {
    return fail('该分类下存在饮品，无法删除')
  }
  if (cat.type === 2 && dbStore.setmeals.load().some((s) => s.categoryId === id)) {
    return fail('该分类下存在套餐，无法删除')
  }
  dbStore.categories.save(list.filter((c) => c.id !== id))
  return ok(null)
}

export function mockCategoryStatus(status: 0 | 1, id: number): Result<null> {
  const list = dbStore.categories.load()
  const cat = list.find((c) => c.id === id)
  if (!cat) return fail('分类不存在')
  cat.status = status
  cat.updateTime = now()
  dbStore.categories.save(list)
  return ok(null)
}
