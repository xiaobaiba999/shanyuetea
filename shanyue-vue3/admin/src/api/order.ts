/* 订单管理接口（契约：/api/order/*，苍穹 6 状态 + 双配送模式）
 * 状态机：1 待付款 / 2 待接单 / 3 已接单(制作中) / 4 派送中(外送)·待取餐(自取) / 5 已完成 / 6 已取消
 * 动作：confirm 2→3；rejection 2→6；cancel 1/2→6；delivery 3→4（自取出餐/外送开始派送）；complete 4→5
 */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import {
  mockCancelOrder,
  mockCompleteOrder,
  mockConfirmOrder,
  mockDeliveryOrder,
  mockOrderDetails,
  mockOrderSearch,
  mockOrderStatistics,
  mockRejectOrder,
} from '@/mock/order'
import type { OrderSearchQuery, OrderStatistics, OrderVO, PageResult } from '@/types'

/** 订单分页搜索 GET /api/order/conditionSearch?page&pageSize&number&phone&status&beginTime&endTime&deliveryMode
 *  UI 内部用 -1 表示「全部」，发请求前统一剥掉（真实后端 status 只认 1~6） */
export async function searchOrders(query: OrderSearchQuery): Promise<PageResult<OrderVO>> {
  const params = {
    ...query,
    status: query.status != null && query.status !== -1 ? query.status : undefined,
    deliveryMode: query.deliveryMode != null && query.deliveryMode !== 0 ? query.deliveryMode : undefined,
  }
  if (USE_MOCK) return unwrapMock(mockOrderSearch(params))
  const res = await request.get<PageResult<OrderVO>>('/order/conditionSearch', { params })
  return res.data
}

/** 各状态订单数量统计 GET /api/order/statistics → {toBeConfirmed, confirmed, deliveryInProgress} */
export async function getOrderStatistics(): Promise<OrderStatistics> {
  if (USE_MOCK) return unwrapMock(mockOrderStatistics())
  const res = await request.get<OrderStatistics>('/order/statistics')
  return res.data
}

/** 订单详情 GET /api/order/details/{id} → OrderVO（含 orderDetailList） */
export async function getOrderDetails(id: number): Promise<OrderVO> {
  if (USE_MOCK) return unwrapMock(mockOrderDetails(id))
  const res = await request.get<OrderVO>(`/order/details/${id}`)
  return res.data
}

/** 接单 PUT /api/order/confirm  body {id}  2→3 */
export async function confirmOrder(id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockConfirmOrder(id))
  const res = await request.put<null>('/order/confirm', { id })
  return res.data
}

/** 拒单 PUT /api/order/rejection  body {id, rejectionReason}  2→6 */
export async function rejectOrder(id: number, rejectionReason: string): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockRejectOrder(id, rejectionReason))
  const res = await request.put<null>('/order/rejection', { id, rejectionReason })
  return res.data
}

/** 取消订单 PUT /api/order/cancel  body {id, cancelReason}  1/2→6 */
export async function cancelOrder(id: number, cancelReason: string): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockCancelOrder(id, cancelReason))
  const res = await request.put<null>('/order/cancel', { id, cancelReason })
  return res.data
}

/** 出餐/派送 PUT /api/order/delivery/{id}  3→4（自取：出餐转「待取餐」；外送：开始派送转「派送中」） */
export async function deliveryOrder(id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockDeliveryOrder(id))
  const res = await request.put<null>(`/order/delivery/${id}`)
  return res.data
}

/** 完成订单 PUT /api/order/complete/{id}  4→5（自取：确认取餐；外送：确认送达） */
export async function completeOrder(id: number): Promise<null> {
  if (USE_MOCK) return unwrapMock(mockCompleteOrder(id))
  const res = await request.put<null>(`/order/complete/${id}`)
  return res.data
}
