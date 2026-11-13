/* 优惠券模板接口：/api/coupon/*（/api 重写为 /admin 转发 8080）（模块 14【阶段四扩展】） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockAddCoupon,
  mockCouponPage,
  mockCouponStatus,
  mockDeleteCoupon,
  mockUpdateCoupon,
} from '@/mock/coupon'
import type { CouponDTO, CouponPageQuery, CouponTemplate, PageResult } from '@/types'

/** 14.1 优惠券分页查询 GET /api/coupon/page */
export async function getCouponPage(query: CouponPageQuery): Promise<PageResult<CouponTemplate>> {
  if (USE_MOCK) return unwrapMock(mockCouponPage(query))
  const res = await request.get<PageResult<CouponTemplate>>('/coupon/page', { params: query })
  return res.data
}

/** 14.2 新增优惠券 POST /api/coupon */
export async function addCoupon(dto: CouponDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockAddCoupon(dto))
  const res = await request.post<null>('/coupon', dto)
  return res.data
}

/** 14.3 修改优惠券 PUT /api/coupon */
export async function updateCoupon(dto: CouponDTO): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockUpdateCoupon(dto))
  const res = await request.put<null>('/coupon', dto)
  return res.data
}

/** 14.4 上架/下架 POST /api/coupon/status/{status}?id= */
export async function updateCouponStatus(status: 0 | 1, id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockCouponStatus(status, id))
  const res = await request.post<null>(`/coupon/status/${status}`, null, { params: { id } })
  return res.data
}

/** 14.5 删除优惠券 DELETE /api/coupon?id=（已有用户领取时后端 code=0） */
export async function deleteCoupon(id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDeleteCoupon(id))
  const res = await request.delete<null>('/coupon', { params: { id } })
  return res.data
}
