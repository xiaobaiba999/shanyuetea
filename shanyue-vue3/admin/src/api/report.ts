/* 数据统计接口（契约：/api/report/*，列表字段为逗号分隔字符串，页面负责 split(',') 渲染） */
import { request } from '@/utils/request'
import { USE_MOCK } from '@/utils/env'
import { unwrapMock } from './_base'
import { mockOrdersStatistics, mockTop10Statistics, mockTurnoverStatistics, mockUserStatistics } from '@/mock/report'
import type { OrdersReportVO, SalesTop10ReportVO, TurnoverReportVO, UserReportVO } from '@/types'

/**
 * 营业额统计 GET /api/report/turnoverStatistics?begin&end
 * → { dateList: '2026-9.1,2026-9.2', turnoverList: '...,...' }（价格单位：分）
 */
export async function getTurnoverStatistics(begin: string, end: string): Promise<TurnoverReportVO> {
  if (USE_MOCK) return unwrapMock(mockTurnoverStatistics(begin, end))
  const res = await request.get<TurnoverReportVO>('/report/turnoverStatistics', { params: { begin, end } })
  return res.data
}

/** 用户统计 GET /api/report/userStatistics?begin&end → {dateList, totalUserList, newUserList} */
export async function getUserStatistics(begin: string, end: string): Promise<UserReportVO> {
  if (USE_MOCK) return unwrapMock(mockUserStatistics(begin, end))
  const res = await request.get<UserReportVO>('/report/userStatistics', { params: { begin, end } })
  return res.data
}

/**
 * 订单统计 GET /api/report/ordersStatistics?begin&end
 * 有效订单口径：status ≠ 6（已取消）；orderCountList=每日总订单，validOrderCountList=每日有效订单
 */
export async function getOrdersStatistics(begin: string, end: string): Promise<OrdersReportVO> {
  if (USE_MOCK) return unwrapMock(mockOrdersStatistics(begin, end))
  const res = await request.get<OrdersReportVO>('/report/ordersStatistics', { params: { begin, end } })
  return res.data
}

/** 销量 TOP10 GET /api/report/top10?begin&end → {nameList: '生椰拿铁,美式', numberList: '96,82'} */
export async function getTop10Statistics(begin: string, end: string): Promise<SalesTop10ReportVO> {
  if (USE_MOCK) return unwrapMock(mockTop10Statistics(begin, end))
  const res = await request.get<SalesTop10ReportVO>('/report/top10', { params: { begin, end } })
  return res.data
}
