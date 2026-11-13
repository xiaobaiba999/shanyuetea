/* 套餐管理接口（契约：/api/setmeal/*，本轮新增页面；SetmealDTO.setmealDrinkes 整体提交） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockAddSetmeal,
  mockDeleteSetmeals,
  mockGetSetmeal,
  mockSetmealPage,
  mockSetmealStatus,
  mockUpdateSetmeal,
} from '@/mock/setmeal'
import type { PageResult, Setmeal, SetmealDTO, SetmealPageQuery } from '@/types'

/** 套餐分页查询 GET /api/setmeal/page?name&categoryId&status&page&pageSize */
export async function getSetmealPage(query: SetmealPageQuery): Promise<PageResult<Setmeal>> {
  if (USE_MOCK) return unwrapMock(mockSetmealPage(query))
  const res = await request.get<PageResult<Setmeal>>('/setmeal/page', { params: query })
  return res.data
}

/** 根据 ID 查询套餐（SetmealVO：含 categoryName + setmealDrinkes） GET /api/setmeal/{id} */
export async function getSetmeal(id: number): Promise<Setmeal> {
  if (USE_MOCK) return unwrapMock(mockGetSetmeal(id))
  const res = await request.get<Setmeal>(`/setmeal/${id}`)
  return res.data
}

/** 新增套餐 POST /api/setmeal（SetmealDTO：setmealDrinkes:[{drinkId,name,price,copies}]） */
export async function addSetmeal(dto: SetmealDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockAddSetmeal(dto))
  const res = await request.post<null>('/setmeal', dto)
  return res.data
}

/** 修改套餐 PUT /api/setmeal（setmealDrinkes 整体覆盖） */
export async function updateSetmeal(dto: SetmealDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateSetmeal(dto))
  const res = await request.put<null>('/setmeal', dto)
  return res.data
}

/** 批量删除套餐 DELETE /api/setmeal?ids=1,2 */
export async function deleteSetmeals(ids: number[]): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDeleteSetmeals(ids))
  const res = await request.delete<null>('/setmeal', { params: { ids: ids.join(',') } })
  return res.data
}

/** 起售/停售套餐 POST /api/setmeal/status/{status}?id=（起售校验：套餐内饮品须全部起售） */
export async function updateSetmealStatus(status: 0 | 1, id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockSetmealStatus(status, id))
  const res = await request.post<null>(`/setmeal/status/${status}`, null, { params: { id } })
  return res.data
}
