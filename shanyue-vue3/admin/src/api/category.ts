/* 分类管理接口（契约：/api/category/*；type：1 菜品分类 / 2 套餐分类） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockAddCategory,
  mockCategoryList,
  mockCategoryPage,
  mockCategoryStatus,
  mockDeleteCategory,
  mockUpdateCategory,
} from '@/mock/category'
import type { Category, CategoryDTO, CategoryPageQuery, PageResult } from '@/types'

/** 分类分页查询 GET /api/category/page?name&type&page&pageSize */
export async function getCategoryPage(query: CategoryPageQuery): Promise<PageResult<Category>> {
  if (USE_MOCK) return unwrapMock(mockCategoryPage(query))
  const res = await request.get<PageResult<Category>>('/category/page', { params: query })
  return res.data
}

/** 分类列表 GET /api/category/list?type=（type 不传返回全部；下拉/选品用） */
export async function getCategoryList(type?: 1 | 2): Promise<Category[]> {
  if (USE_MOCK) return unwrapMock(mockCategoryList(type))
  const res = await request.get<Category[]>('/category/list', { params: type != null ? { type } : {} })
  return res.data
}

/** 新增分类 POST /api/category {type,name,sort} */
export async function addCategory(dto: CategoryDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockAddCategory(dto))
  const res = await request.post<null>('/category', dto)
  return res.data
}

/** 修改分类 PUT /api/category */
export async function updateCategory(dto: CategoryDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateCategory(dto))
  const res = await request.put<null>('/category', dto)
  return res.data
}

/** 删除分类 DELETE /api/category?id=（分类下有商品/套餐时后端 code=0） */
export async function deleteCategory(id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDeleteCategory(id))
  const res = await request.delete<null>('/category', { params: { id } })
  return res.data
}

/** 启用/禁用分类 POST /api/category/status/{status}?id= */
export async function updateCategoryStatus(status: 0 | 1, id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockCategoryStatus(status, id))
  const res = await request.post<null>(`/category/status/${status}`, null, { params: { id } })
  return res.data
}
