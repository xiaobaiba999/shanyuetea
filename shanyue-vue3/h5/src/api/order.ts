import type { OrderSubmitDTO, OrderSubmitVO, OrderVO } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet, httpPost, httpPut } from '@/utils/request'
import { mockAdvanceOrder, mockCancelOrder, mockGetOrderByPickUpCode, mockReminderOrder, mockSubmitOrder } from '@/mock/order'
import { unwrap, unwrapNull } from './unwrap'

/** 8.8 提交订单（从购物车结算） */
export function submitOrder(dto: OrderSubmitDTO): Promise<OrderSubmitVO> {
  if (USE_MOCK) return unwrap(mockSubmitOrder(dto))
  return httpPost<OrderSubmitVO>('/user/order/submit', dto)
}

/** 8.9 按取餐号查询订单 */
export function getOrderByPickUpCode(code: string): Promise<OrderVO> {
  if (USE_MOCK) return unwrap(mockGetOrderByPickUpCode(code))
  return httpGet<OrderVO>(`/user/order/byPickUpCode/${encodeURIComponent(code)}`)
}

/** 8.10 催单（仅 1/2 状态） */
export function reminderOrder(id: number): Promise<void> {
  if (USE_MOCK) return unwrapNull(mockReminderOrder(id))
  return httpPut<void>(`/user/order/reminder/${id}`)
}

/** 8.11 用户取消订单（仅状态 1） */
export function cancelOrder(id: number): Promise<void> {
  if (USE_MOCK) return unwrapNull(mockCancelOrder(id))
  return httpPut<void>(`/user/order/cancel/${id}`)
}

/**
 * 演示推进（仅 mock 模式调用）：模拟商家侧把订单推进一步（1→2→3→4）
 * 真实模式下不存在此接口，按钮也不会渲染
 */
export function advanceOrderDemo(id: number): Promise<OrderVO> {
  return unwrap(mockAdvanceOrder(id))
}
