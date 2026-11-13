/* 店铺管理接口（契约：/api/shop/*；notice 编辑为山月扩展，其余对齐苍穹） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import { mockGetShopNotice, mockGetShopStatus, mockSetShopStatus, mockUpdateNotice } from '@/mock/shop'

/** 获取营业状态 GET /api/shop/status → data 直接是 1 | 0 */
export async function getShopStatus(): Promise<number> {
  if (USE_MOCK) return unwrapMock(mockGetShopStatus())
  const res = await request.get<number>('/shop/status')
  return res.data
}

/** 查询店铺公告 GET /api/shop/notice（山月扩展模块的读取口，与 PUT /shop/notice 配对） */
export async function getShopNotice(): Promise<string> {
  if (USE_MOCK) return unwrapMock(mockGetShopNotice())
  const res = await request.get<string>('/shop/notice')
  return res.data
}

/** 修改营业状态 PUT /api/shop/{status}（路径参数，无 body） */
export async function setShopStatus(status: 0 | 1): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockSetShopStatus(status))
  const res = await request.put<null>(`/shop/${status}`)
  return res.data
}

/** 修改公告 PUT /api/shop/notice  body: { notice }（山月扩展接口，配套 api-docs 模块 8） */
export async function updateShopNotice(notice: string): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateNotice(notice))
  const res = await request.put<null>('/shop/notice', { notice })
  return res.data
}
