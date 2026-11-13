import type { Result, ShopVO } from '@/types'
import { dbGet, dbSet, delay, fail, ok } from './db'

const SHOP_KEY = 'shop'

export const SHOP_SEED: ShopVO = {
  name: '山月茶咖 · 科创园区店',
  status: 1,
  notice: '新店开业：全场茶咖第二杯半价，活动截至本周日',
  openHours: '09:00-22:00',
}

/** 8.1 查询店铺信息（含营业状态） */
export function mockGetShop(): Promise<Result<ShopVO>> {
  const shop = dbGet<ShopVO>(SHOP_KEY, SHOP_SEED)
  return delay(ok({ ...shop }))
}

/** 演示工具：切换营业状态（仅 mock 模式可用，真实环境走管理端 7.2 接口） */
export function mockSetShopStatus(status: 0 | 1): Promise<Result<null>> {
  const shop = dbGet<ShopVO>(SHOP_KEY, SHOP_SEED)
  dbSet<ShopVO>(SHOP_KEY, { ...shop, status })
  return delay(ok(null))
}
