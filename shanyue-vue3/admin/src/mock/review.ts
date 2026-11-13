/* 评价模块 mock：严格模拟 /admin/review/* 行为（模块 17【阶段五扩展】）
 * 评价数据独立存储（shanyue-admin-db-reviews），种子挂在已完成种子订单上；
 * 管理端职责：查看/回复/隐藏-恢复/统计，不代顾客写评价。
 * 与来单模拟的联动：新完成的订单尚无评价 → 列表「待回复/新评价」会随经营自然增长（此处演示不做自动生成）。
 */
import type { PageResult, Result, Review, ReviewPageQuery, ReviewStats } from '@/types'
import { clone, dbStore, fail, ok } from './db'

/** 低分阈值：≤3 星未回复视为「待回复」（仪表盘待办同口径） */
export const PENDING_SCORE_MAX = 3

function isPending(r: Review): boolean {
  return r.visible === 1 && r.score <= PENDING_SCORE_MAX && !r.reply.trim()
}

/** 17.1 评价分页（星级/关键词/隐藏态筛选，时间倒序） */
export function mockReviewPage(query: ReviewPageQuery): Result<PageResult<Review>> {
  let list = dbStore.reviews.load()
  const keyword = query.keyword?.trim().toLowerCase()
  if (keyword) {
    list = list.filter(
      (r) =>
        r.content.toLowerCase().includes(keyword) ||
        r.orderNumber.includes(keyword) ||
        r.pickUpCode.toLowerCase().includes(keyword),
    )
  }
  if (query.score != null && query.score > 0) list = list.filter((r) => r.score === query.score)
  if (query.hidden != null) list = list.filter((r) => r.visible === (query.hidden === 1 ? 0 : 1))

  list = [...list].sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  const total = list.length
  const start = (query.page - 1) * query.pageSize
  const records = clone(list.slice(start, start + query.pageSize))
  return ok({ total, records })
}

/** 17.2 评价统计（平均分/好评率/待回复/星级分布/标签热度；已隐藏不参与统计） */
export function mockReviewStats(): Result<ReviewStats> {
  const list = dbStore.reviews.load().filter((r) => r.visible === 1)
  const total = list.length
  const distribution = [5, 4, 3, 2, 1].map((s) => list.filter((r) => r.score === s).length)
  const avgScore = total ? Math.round((list.reduce((s, r) => s + r.score, 0) / total) * 10) / 10 : 0
  const goodRate = total ? Math.round(((distribution[0] + distribution[1]) / total) * 100) : 0
  const pendingReply = list.filter(isPending).length

  const counter = new Map<string, number>()
  for (const r of list) for (const t of r.tags) counter.set(t, (counter.get(t) ?? 0) + 1)
  const tagStats = [...counter.entries()]
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count)

  return ok({ avgScore, total, goodRate, pendingReply, distribution, tagStats })
}

/** 17.3 回复评价（一评一回复，可修改已回复内容；已隐藏评价也可先回复再展示） */
export function mockReplyReview(id: number, reply: string): Result<null> {
  const list = dbStore.reviews.load()
  const review = list.find((r) => r.id === id)
  if (!review) return fail('评价不存在')
  const text = reply.trim()
  if (!text) return fail('回复内容不能为空')
  if (text.length > 50) return fail('回复内容最多 50 字')
  review.reply = text
  dbStore.reviews.save(list)
  return ok(null)
}

/** 17.4 隐藏/恢复评价（隐藏 = 不在 C 端评价墙展示且不参与统计；面试点：生产建议「先审后显」或软删除留痕） */
export function mockToggleReviewVisible(id: number): Result<null> {
  const list = dbStore.reviews.load()
  const review = list.find((r) => r.id === id)
  if (!review) return fail('评价不存在')
  review.visible = review.visible === 1 ? 0 : 1
  dbStore.reviews.save(list)
  return ok(null)
}
