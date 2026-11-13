import type { AddCartDTO, Result, ShoppingCartVO, SubCartDTO } from '@/types'
import { USE_MOCK } from '@/utils/env'
import { httpDelete, httpGet, httpPost } from '@/utils/request'
import { mockAddCart, mockCleanCart, mockGetCartList, mockSubCart } from '@/mock/cart'
import { unwrapData, unwrapNull } from './unwrap'

/** 8.4 添加购物车 */
export function addToCart(dto: AddCartDTO): Promise<void> {
  if (USE_MOCK) return unwrapNull(mockAddCart(dto))
  return httpPost<void>('/user/shoppingCart/add', dto)
}

/** 8.5 查看购物车 */
export function getCartList(): Promise<ShoppingCartVO[]> {
  if (USE_MOCK) return unwrapData(mockGetCartList())
  return httpGet<ShoppingCartVO[]>('/user/shoppingCart/list')
}

/** 8.6 购物车减一个 */
export function subCart(dto: SubCartDTO): Promise<void> {
  if (USE_MOCK) return unwrapNull(mockSubCart(dto))
  return httpPost<void>('/user/shoppingCart/sub', dto)
}

/** 8.7 清空购物车 */
export function cleanCart(): Promise<void> {
  if (USE_MOCK) return unwrapNull(mockCleanCart())
  return httpDelete<void>('/user/shoppingCart/clean')
}
