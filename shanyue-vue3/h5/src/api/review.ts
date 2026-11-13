import type { ReviewCreateDTO, ReviewVO, ShopReviewStats } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet, httpPost } from '@/utils/request'
import { mockCreateReview, mockGetMyReviews, mockGetReviewByOrderId, mockGetShopReviewStats } from '@/mock/review'
import { unwrap } from './unwrap'

/* ---------------- 评价体系（模块 16【阶段五扩展】） ---------------- */

/** 16.1 提交评价（一单一评，仅已完成订单） */
export function createReview(dto: ReviewCreateDTO): Promise<ReviewVO> {
  if (USE_MOCK) return unwrap(mockCreateReview(dto))
  return httpPost<ReviewVO>('/user/review', dto)
}

/** 16.2 查询订单评价（未评价返回 null） */
export function getReviewByOrderId(orderId: number): Promise<ReviewVO | null> {
  if (USE_MOCK) return unwrap(mockGetReviewByOrderId(orderId))
  return httpGet<ReviewVO | null>(`/user/review/order/${orderId}`)
}

/** 16.3 门店评价统计（评分/好评率/分布/标签热度） */
export function getShopReviewStats(): Promise<ShopReviewStats> {
  if (USE_MOCK) return unwrap(mockGetShopReviewStats())
  return httpGet<ShopReviewStats>('/user/review/stats')
}

/** 16.4 我的评价列表（倒序） */
export function getMyReviews(): Promise<ReviewVO[]> {
  if (USE_MOCK) return unwrap(mockGetMyReviews())
  return httpGet<ReviewVO[]>('/user/review/mine')
}

/** 快捷标签池（16.1 约定的固定枚举，前端展示与校验共用） */
export { REVIEW_TAG_POOL } from '@/mock/review'
