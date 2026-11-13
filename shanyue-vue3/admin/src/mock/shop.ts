/* 店铺模块 mock：/api/shop/*（苍穹口径：营业状态 get/put；notice 为山月扩展） */
import type { Result } from '@/types'
import { dbStore, ok } from './db'

/** GET /api/shop/status → data 直接是 1 | 0 */
export function mockGetShopStatus(): Result<number> {
  return ok(dbStore.shop.load().status)
}

/** GET /api/shop/notice（山月扩展：公告读取口） */
export function mockGetShopNotice(): Result<string> {
  return ok(dbStore.shop.load().notice)
}

/** PUT /api/shop/{status}（路径参数，无 body） */
export function mockSetShopStatus(status: 0 | 1): Result<null> {
  const shop = dbStore.shop.load()
  shop.status = status
  dbStore.shop.save(shop)
  return ok(null)
}

/** PUT /api/shop/notice  body: { notice }（山月扩展） */
export function mockUpdateNotice(notice: string): Result<null> {
  const shop = dbStore.shop.load()
  shop.notice = notice
  dbStore.shop.save(shop)
  return ok(null)
}
