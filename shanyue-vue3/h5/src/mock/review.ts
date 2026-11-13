import type { Result, ReviewCreateDTO, ReviewVO, ShopReviewStats } from '@/types'
import { dbGet, dbRemove, dbSet, delay, fail, ok } from './db'
import { fmtDateTime } from '@/utils/format'

/**
 * 评价 mock（localStorage 持久化，key = shanyue-h5-reviews，见模块 16）
 * - 一单仅可评价一次（orderId 唯一，对应后端 order_id 唯一键）
 * - 仅已完成（status=4）订单可评价，服务端校验
 * - 种子数据为「历史订单」评价（不在当日订单列表中），让门店评分/评价墙开箱有内容
 * - 标签池与真实 App 对齐：口味赞/出餐快/服务贴心/性价比高/包装精美/环境好
 */
const REVIEWS_KEY = 'reviews'

/** 快捷标签池（16.1 约定） */
export const REVIEW_TAG_POOL = ['口味赞', '出餐快', '服务贴心', '性价比高', '包装精美', '环境好'] as const

interface StoredReview extends ReviewVO {
  /** 商家是否隐藏（管理端操作，C 端评价墙/统计不再展示） */
  hidden?: boolean
}

/** 订单最小读取结构（与 mock/order.ts 的 ORDERS_KEY='orders' 同源，避免循环依赖） */
interface OrderBrief {
  id: number
  orderNumber: string
  pickUpCode: string
  status: number
}

function readReviews(): StoredReview[] {
  return dbGet<StoredReview[]>(REVIEWS_KEY, [])
}

function writeReviews(list: StoredReview[]): void {
  dbSet(REVIEWS_KEY, list)
}

/** 昨天 yyyy-MM-dd（种子评价展示用） */
function yesterdayYmd(): string {
  const d = new Date(Date.now() - 24 * 3600 * 1000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 组装种子评价（挂历史订单号，当日订单列表里查不到，符合「历史订单」语义） */
function buildSeedReviews(): StoredReview[] {
  const yd = yesterdayYmd()
  const seqOf = (orderNumber: string) => orderNumber.slice(-3)
  const mk = (
    id: number,
    orderNumber: string,
    score: number,
    tags: string[],
    content: string,
    createTime: string,
    reply = '',
  ): StoredReview => ({
    id,
    orderId: 9000 + id,
    orderNumber,
    pickUpCode: `A${seqOf(orderNumber)}`,
    score,
    tags,
    content,
    reply,
    createTime,
  })
  return [
    mk(
      1,
      `${yd.replace(/-/g, '')}008`,
      5,
      ['口味赞', '出餐快'],
      '桂花冰酿拿铁太惊喜了，桂花香特别正，出餐也就三分钟！',
      `${yd} 12:41:05`,
      '谢谢喜爱～桂花的春天风味即将回归，敬请期待 🌼',
    ),
    mk(
      2,
      `${yd.replace(/-/g, '')}011`,
      4,
      ['性价比高', '环境好'],
      '午后来一杯美式配可颂很舒服，店里座位也干净。',
      `${yd} 15:22:33`,
      '',
    ),
    mk(
      3,
      `${yd.replace(/-/g, '')}014`,
      5,
      ['包装精美', '服务贴心'],
      '杯垫都给垫好了，店员还提醒小心烫，细节满分。',
      `${yd} 18:03:10`,
      '',
    ),
    mk(
      4,
      `${yd.replace(/-/g, '')}017`,
      5,
      ['口味赞'],
      '巴斯克芝士蛋糕流心绝了，微甜不腻，配拿铁刚好。',
      `${yd} 19:47:52`,
      '',
    ),
  ]
}

function ensureSeed(): void {
  if (dbGet<StoredReview[]>(REVIEWS_KEY, []).length > 0) return
  writeReviews(buildSeedReviews())
}

/** 16.1 提交评价（一单一评；仅已完成订单可评；服务端校验星级与内容长度） */
export function mockCreateReview(dto: ReviewCreateDTO): Promise<Result<ReviewVO>> {
  const score = Math.round(dto.score)
  if (!score || score < 1 || score > 5) return delay(fail('请选择 1~5 星评分'))
  const content = (dto.content ?? '').trim()
  if (content.length > 50) return delay(fail('评价内容最多 50 字'))
  if (dto.tags.some((t) => !(REVIEW_TAG_POOL as readonly string[]).includes(t))) {
    return delay(fail('存在无效的评价标签'))
  }

  const orders = dbGet<OrderBrief[]>('orders', [])
  const order = orders.find((o) => o.id === dto.orderId)
  if (!order) return delay(fail('订单不存在'))
  if (order.status !== 4) return delay(fail('订单完成后才能评价哦'))

  const reviews = readReviews()
  if (reviews.some((r) => r.orderId === dto.orderId)) return delay(fail('该订单已评价过啦'))

  const review: StoredReview = {
    id: Date.now(),
    orderId: dto.orderId,
    orderNumber: order.orderNumber,
    pickUpCode: order.pickUpCode,
    score,
    tags: [...dto.tags],
    content,
    reply: '',
    createTime: fmtDateTime(new Date()),
  }
  reviews.unshift(review)
  writeReviews(reviews)
  const { hidden: _h, ...vo } = review
  return delay(ok(vo as ReviewVO), 260)
}

/** 16.2 查询订单是否已评价（未评价返回 null，由 C 端渲染评价按钮） */
export function mockGetReviewByOrderId(orderId: number): Promise<Result<ReviewVO | null>> {
  const review = readReviews().find((r) => r.orderId === orderId)
  if (!review) return delay(ok(null))
  const { hidden: _h, ...vo } = review
  return delay(ok(vo as ReviewVO))
}

/** 16.3 门店评价统计（隐藏评价不参与统计；无评价时 avgScore=0） */
export function mockGetShopReviewStats(): Promise<Result<ShopReviewStats>> {
  ensureSeed()
  const list = readReviews().filter((r) => !r.hidden)
  const total = list.length
  const distribution = [5, 4, 3, 2, 1].map((s) => list.filter((r) => r.score === s).length)
  const avgScore = total ? Math.round((list.reduce((acc, r) => acc + r.score, 0) / total) * 10) / 10 : 0
  const good = distribution[0] + distribution[1]
  const goodRate = total ? Math.round((good / total) * 100) : 0

  const counter = new Map<string, number>()
  for (const r of list) for (const t of r.tags) counter.set(t, (counter.get(t) ?? 0) + 1)
  const tagStats = [...counter.entries()]
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6)

  return delay(ok({ avgScore, total, goodRate, distribution, tagStats }))
}

/** 16.4 我的评价（倒序，含商家回复；演示单用户持有全部评价） */
export function mockGetMyReviews(): Promise<Result<ReviewVO[]>> {
  ensureSeed()
  const list = [...readReviews()]
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map(({ hidden: _h, ...vo }) => vo as ReviewVO)
  return delay(ok(list))
}

/** 重置演示数据时连带清空评价 */
export function mockResetReviews(): void {
  dbRemove(REVIEWS_KEY)
  ensureSeed()
}

/* ---------------- 商家回复注入（供 admin 演示同步观感，见 QueryView 评价卡） ---------------- */
/** 供测试钩子注入：给最新一条评价写回复（不暴露为正式接口） */
export function mockInjectReply(content: string): void {
  const reviews = readReviews()
  if (!reviews.length) return
  reviews[0].reply = content
  writeReviews(reviews)
}
