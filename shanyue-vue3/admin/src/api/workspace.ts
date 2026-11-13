/* 工作台接口（契约：/api/workspace/*，仪表盘四卡数据源） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import { mockBusinessData, mockOverviewDrinkes, mockOverviewOrders, mockOverviewSetmeals } from '@/mock/workspace'
import type { BusinessDataVO, GoodsOverviewVO, OrdersOverviewVO } from '@/types'

/** 经营数据 GET /api/workspace/businessData → {turnover, validOrderCount, orderCompletionRate, unitPrice, newUsers} */
export async function getBusinessData(): Promise<BusinessDataVO> {
  if (USE_MOCK) return unwrapMock(mockBusinessData())
  const res = await request.get<BusinessDataVO>('/workspace/businessData')
  return res.data
}

/** 订单概览 GET /api/workspace/overviewOrders → {waitingOrders, deliveredOrders, completedOrders, cancelledOrders, allOrders} */
export async function getOverviewOrders(): Promise<OrdersOverviewVO> {
  if (USE_MOCK) return unwrapMock(mockOverviewOrders())
  const res = await request.get<OrdersOverviewVO>('/workspace/overviewOrders')
  return res.data
}

/** 菜品总览 GET /api/workspace/overviewDrinkes → {sold, discontinued} */
export async function getOverviewDrinkes(): Promise<GoodsOverviewVO> {
  if (USE_MOCK) return unwrapMock(mockOverviewDrinkes())
  const res = await request.get<GoodsOverviewVO>('/workspace/overviewDrinkes')
  return res.data
}

/** 套餐总览 GET /api/workspace/overviewSetmeals → {sold, discontinued} */
export async function getOverviewSetmeals(): Promise<GoodsOverviewVO> {
  if (USE_MOCK) return unwrapMock(mockOverviewSetmeals())
  const res = await request.get<GoodsOverviewVO>('/workspace/overviewSetmeals')
  return res.data
}
