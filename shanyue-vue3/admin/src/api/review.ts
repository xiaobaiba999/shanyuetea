/* 评价管理接口：/api/review/*（/api 重写为 /admin 转发 8080）（模块 17【阶段五扩展】） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import { mockReplyReview, mockReviewPage, mockReviewStats, mockToggleReviewVisible } from '@/mock/review'
import type { PageResult, Review, ReviewPageQuery, ReviewStats } from '@/types'

/** 17.1 评价分页查询 GET /api/review/page */
export async function getReviewPage(query: ReviewPageQuery): Promise<PageResult<Review>> {
  if (USE_MOCK) return unwrapMock(mockReviewPage(query))
  const res = await request.get<PageResult<Review>>('/review/page', { params: query })
  return res.data
}

/** 17.2 评价统计 GET /api/review/stats */
export async function getReviewStats(): Promise<ReviewStats> {
  if (USE_MOCK) return unwrapMock(mockReviewStats())
  const res = await request.get<ReviewStats>('/review/stats')
  return res.data
}

/** 17.3 回复评价 PUT /api/review/reply */
export async function replyReview(id: number, reply: string): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockReplyReview(id, reply))
  const res = await request.put<null>('/review/reply', { id, reply })
  return res.data
}

/** 17.4 隐藏/恢复评价 PUT /api/review/visible/{visible}?id= */
export async function toggleReviewVisible(id: number, visible: 0 | 1): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockToggleReviewVisible(id))
  const res = await request.put<null>(`/review/visible/${visible}`, null, { params: { id } })
  return res.data
}
