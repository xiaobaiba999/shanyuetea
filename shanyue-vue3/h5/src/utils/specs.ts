import { getDrinks } from '@/api/menu'
import type { DrinkVO, SpecPair } from '@/types'

/**
 * specKey（选项 id 按 group.sort 排序逗号拼接，如 "1011,1021,1031,1041"）
 * -> SpecPair[]（groupName + optionName），用于「购物车里再 +1」时复用 addToCart 接口。
 * 饮品列表带 specGroups，模块级缓存一次即可（mock 模式瞬时返回）。
 */
let drinkCache: DrinkVO[] | null = null

export async function resolveSpecPairs(drinkId: number, specKey: string): Promise<SpecPair[]> {
  if (!drinkCache) {
    drinkCache = await getDrinks()
  }
  const drink = drinkCache.find((d) => d.id === drinkId)
  if (!drink) return []
  const ids = specKey ? specKey.split(',').filter(Boolean).map(Number) : []
  const pairs: SpecPair[] = []
  ;[...drink.specGroups]
    .sort((a, b) => a.sort - b.sort)
    .forEach((g) => {
      ;[...g.specOptions]
        .sort((a, b) => a.sort - b.sort)
        .forEach((o) => {
          if (ids.includes(o.id)) {
            pairs.push({ groupName: g.name, optionName: o.name })
          }
        })
    })
  return pairs
}
