/* 饮品管理接口（契约：/api/drink/*；核心：规格组 specGroups.options 整体提交，先删旧后插新） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockAddDrink,
  mockDeleteDrinks,
  mockDrinkList,
  mockDrinkPage,
  mockDrinkStatus,
  mockGetDrink,
  mockUpdateDrink,
} from '@/mock/drink'
import type { Drink, DrinkDTO, DrinkPageQuery, PageResult } from '@/types'

/** 饮品分页查询 GET /api/drink/page?name&categoryId&status&page&pageSize */
export async function getDrinkPage(query: DrinkPageQuery): Promise<PageResult<Drink>> {
  if (USE_MOCK) return unwrapMock(mockDrinkPage(query))
  const res = await request.get<PageResult<Drink>>('/drink/page', { params: query })
  return res.data
}

/** 根据 ID 查询饮品（DrinkVO：含 categoryName + specGroups 树 + monthSales） GET /api/drink/{id} */
export async function getDrink(id: number): Promise<Drink> {
  if (USE_MOCK) return unwrapMock(mockGetDrink(id))
  const res = await request.get<Drink>(`/drink/${id}`)
  return res.data
}

/** 按分类查饮品列表 GET /api/drink/list?categoryId=（套餐选品用；不传 categoryId 返回全部起售饮品） */
export async function getDrinkList(categoryId?: number): Promise<Drink[]> {
  if (USE_MOCK) return unwrapMock(mockDrinkList(categoryId))
  const res = await request.get<Drink[]>('/drink/list', { params: categoryId != null ? { categoryId } : {} })
  return res.data
}

/** 新增饮品 POST /api/drink（DrinkDTO：specGroups:[{name,sort,options:[{name,priceDelta,sort}]}]） */
export async function addDrink(dto: DrinkDTO): Promise<number> {
  if (USE_MOCK) return unwrapMock(mockAddDrink(dto))
  const res = await request.post<number>('/drink', dto)
  return res.data
}

/** 修改饮品 PUT /api/drink（specGroups 整体提交，后端先删旧后插新） */
export async function updateDrink(dto: DrinkDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateDrink(dto))
  const res = await request.put<null>('/drink', dto)
  return res.data
}

/** 批量删除饮品 DELETE /api/drink?ids=1,2 */
export async function deleteDrinks(ids: number[]): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDeleteDrinks(ids))
  const res = await request.delete<null>('/drink', { params: { ids: ids.join(',') } })
  return res.data
}

/** 起售/停售 POST /api/drink/status/{status}?id= */
export async function updateDrinkStatus(status: 0 | 1, id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDrinkStatus(status, id))
  const res = await request.post<null>(`/drink/status/${status}`, null, { params: { id } })
  return res.data
}
