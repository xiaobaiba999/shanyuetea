import type { ShopVO } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpGet } from '@/utils/request'
import { mockGetShop, mockSetShopStatus } from '@/mock/shop'
import { unwrap } from './unwrap'

/** 8.1 查询店铺信息（含营业状态） */
export function getShopStatus(): Promise<ShopVO> {
  if (USE_MOCK) return unwrap(mockGetShop())
  return httpGet<ShopVO>('/user/shop/status')
}

/** 演示工具：切换营业状态（仅 mock 生效；真实环境由商家在管理端操作 7.2 接口） */
export async function setShopStatusDemo(status: 0 | 1): Promise<void> {
  if (USE_MOCK) {
    const res = await mockSetShopStatus(status)
    if (res.code === 0) throw new Error(res.msg)
  }
}
