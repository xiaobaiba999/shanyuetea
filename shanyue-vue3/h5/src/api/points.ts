import type { CheckinResultVO, ExchangeRuleVO, PointRecordVO, PointSummaryVO, Result } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet, httpPost } from '@/utils/request'
import {
  mockCheckin,
  mockExchangeCoupon,
  mockGetExchangeShelf,
  mockGetPointRecords,
  mockGetPointSummary,
  mockSeedStreakDemo,
} from '@/mock/points'
import { unwrap } from './unwrap'

/** 15.1 积分概要（余额/累计获得/累计消耗/签到天数/连续签到/今日是否已签） */
export function getPointSummary(): Promise<PointSummaryVO> {
  if (USE_MOCK) return unwrap(mockGetPointSummary())
  return httpGet<PointSummaryVO>('/user/point/summary')
}

/** 15.2 每日签到（+7 积分，每日一次；返回签到后的最新概要 + 本次触发的连签奖励） */
export function checkinToday(): Promise<CheckinResultVO> {
  if (USE_MOCK) return unwrap(mockCheckin())
  return httpPost<CheckinResultVO>('/user/point/checkin', {})
}

/** 演示：注入 6 天连签历史（仅 mock），下一次签到即达成连签 7 天奖励 */
export function seedStreakDemo(): Promise<PointSummaryVO> {
  return unwrap(mockSeedStreakDemo())
}

/** 15.3 积分明细（消费/签到/兑换三类，按时间倒序） */
export function getPointRecords(): Promise<PointRecordVO[]> {
  if (USE_MOCK) return unwrap(mockGetPointRecords())
  return httpGet<PointRecordVO[]>('/user/point/records')
}

/** 15.4 积分兑换货架（在售券模板按面额定价） */
export function getExchangeShelf(): Promise<ExchangeRuleVO[]> {
  if (USE_MOCK) return unwrap(mockGetExchangeShelf())
  return httpGet<ExchangeRuleVO[]>('/user/point/exchanges')
}

/** 15.5 积分兑换优惠券（服务端同事务：校验余额 → 扣积分 → 发券） */
export function exchangeCoupon(templateId: number): Promise<PointSummaryVO> {
  if (USE_MOCK) return unwrap(mockExchangeCoupon(templateId) as Promise<Result<PointSummaryVO>>)
  return httpPost<PointSummaryVO>(`/user/point/exchange/${templateId}`, {})
}
