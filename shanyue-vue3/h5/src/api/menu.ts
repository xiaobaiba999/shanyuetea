import type { CategoryVO, DrinkVO, Result } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet } from '@/utils/request'
import { mockGetCategoryList, mockGetDrinkList } from '@/mock/menu'
import { unwrapData } from './unwrap'

/** 8.2 查询分类列表 */
export function getCategories(): Promise<CategoryVO[]> {
  if (USE_MOCK) return unwrapData(mockGetCategoryList())
  return httpGet<CategoryVO[]>('/user/category/list')
}

/** 8.3 查询在售饮品（含规格组） */
export function getDrinks(categoryId?: number): Promise<DrinkVO[]> {
  if (USE_MOCK) return unwrapData(mockGetDrinkList(categoryId))
  return httpGet<DrinkVO[]>('/user/drink/list', categoryId ? { categoryId } : undefined)
}
