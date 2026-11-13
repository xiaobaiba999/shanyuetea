import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { ShoppingCartVO, SpecPair } from '@/types'
import * as cartApi from '@/api/cart'
import { resolveSpecPairs } from '@/utils/specs'

/**
 * 购物车 store（无额外依赖，手动 watch 持久化到 localStorage）
 * - items 为接口形态 ShoppingCartVO[]（与 /user/shoppingCart/list 一致）
 * - 每次变更都走 api 层（mock/真实后端同一条路径），成功后 refresh
 * - localStorage 缓存用于刷新时先渲染、避免闪烁（api 拉取后覆盖）
 */
const CACHE_KEY = 'shanyue-h5-cart-cache'

function loadCache(): ShoppingCartVO[] {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? (JSON.parse(raw) as ShoppingCartVO[]) : []
  } catch {
    return []
  }
}

export const useCartStore = defineStore('cart', () => {
  // 同步恢复缓存（写在 store 初始化里，早于任何组件渲染）
  const items = ref<ShoppingCartVO[]>(loadCache())

  watch(
    items,
    (v) => {
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify(v))
      } catch {
        /* ignore */
      }
    },
    { deep: true },
  )

  /** 总件数 */
  const totalCount = computed(() => items.value.reduce((s, i) => s + i.number, 0))

  /** 合计金额（分）= Σ 单价 × 数量 */
  const totalPrice = computed(() => items.value.reduce((s, i) => s + i.amount * i.number, 0))

  /** 从接口拉取最新购物车 */
  async function refresh(): Promise<void> {
    items.value = await cartApi.getCartList()
  }

  /** 初始化（首屏调用一次） */
  async function init(): Promise<void> {
    await refresh()
  }

  /** 加购：无规格传 [] */
  async function add(drinkId: number, specs: SpecPair[]): Promise<void> {
    await cartApi.addToCart({ drinkId, specs })
    await refresh()
  }

  /** 购物车里 +1：按行内的 specKey 反查规格再复用 addToCart */
  async function addByKey(row: ShoppingCartVO): Promise<void> {
    const specs = await resolveSpecPairs(row.drinkId, row.specKey)
    await add(row.drinkId, specs)
  }

  /** 购物车里 -1（减到 0 服务端删行） */
  async function sub(row: ShoppingCartVO): Promise<void> {
    await cartApi.subCart({ drinkId: row.drinkId, specKey: row.specKey })
    await refresh()
  }

  /** 删除整行（C端接口无删行，等价于连减 number 次） */
  async function removeRow(row: ShoppingCartVO): Promise<void> {
    for (let i = 0; i < row.number; i++) {
      await cartApi.subCart({ drinkId: row.drinkId, specKey: row.specKey })
    }
    await refresh()
  }

  /** 清空 */
  async function clean(): Promise<void> {
    await cartApi.cleanCart()
    items.value = []
  }

  return { items, totalCount, totalPrice, init, refresh, add, addByKey, sub, removeRow, clean }
})
