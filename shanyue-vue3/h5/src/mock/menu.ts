import type { CategoryVO, DrinkVO, Result } from '@/types'
import { delay, ok } from './db'
import { CATEGORIES, DRINKS } from './menu-data'

/** 8.2 查询分类列表（只返回在售分类，按 sort 升序） */
export function mockGetCategoryList(): Promise<Result<CategoryVO[]>> {
  const list = [...CATEGORIES].sort((a, b) => a.sort - b.sort)
  return delay(ok(list))
}

/** 8.3 查询在售饮品（含规格组，categoryId 可选，按 sold 降序） */
export function mockGetDrinkList(categoryId?: number): Promise<Result<DrinkVO[]>> {
  let list = DRINKS.filter((d) => d.status === 1)
  if (categoryId) list = list.filter((d) => d.categoryId === categoryId)
  list = [...list].sort((a, b) => b.sold - a.sold)
  return delay(ok(list.map((d) => structuredClone(d))))
}
