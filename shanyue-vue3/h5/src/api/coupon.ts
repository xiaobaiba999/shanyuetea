import type { CouponTemplateVO, MemberSummaryVO, UserCouponVO } from '@/types'
import type { Result } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet, httpPost } from '@/utils/request'
import {
  mockGetCouponTemplates,
  mockGetMyCoupons,
  mockGetUsableCoupons,
  mockReceiveCoupon,
} from '@/mock/coupon'
import { mockGetMemberSummary } from '@/mock/order'
import { unwrap } from './unwrap'

/** 13.1 券广场：可领模板列表 */
export function getCouponTemplates(): Promise<CouponTemplateVO[]> {
  if (USE_MOCK) return unwrap(mockGetCouponTemplates())
  return httpGet<CouponTemplateVO[]>('/user/coupon/templates')
}

/** 13.2 领取优惠券（每模板限领 1 张） */
export function receiveCoupon(templateId: number): Promise<void> {
  if (USE_MOCK) return unwrap(mockReceiveCoupon(templateId) as Promise<Result<UserCouponVO>>).then(() => undefined)
  return httpPost<void>(`/user/coupon/receive/${templateId}`, {})
}

/** 13.3 我的优惠券（status: 1 可用 / 2 已核销 / 3 已过期，不传 = 全部） */
export function getMyCoupons(status?: 1 | 2 | 3): Promise<UserCouponVO[]> {
  if (USE_MOCK) return unwrap(mockGetMyCoupons(status))
  return httpGet<UserCouponVO[]>(`/user/coupon/list${status ? `?status=${status}` : ''}`)
}

/** 13.4 下单可用券（服务端按门槛 + 有效期过滤，抵扣金额降序） */
export function getUsableCoupons(goodsAmount: number): Promise<UserCouponVO[]> {
  if (USE_MOCK) return unwrap(mockGetUsableCoupons(goodsAmount))
  return httpGet<UserCouponVO[]>(`/user/coupon/usable?amount=${goodsAmount}`)
}

/** 13.5 会员概要（积分/累计消费，演示口径） */
export function getMemberSummary(): Promise<MemberSummaryVO> {
  if (USE_MOCK) return unwrap(mockGetMemberSummary())
  return httpGet<MemberSummaryVO>('/user/member/summary')
}
