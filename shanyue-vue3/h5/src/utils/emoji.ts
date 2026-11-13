/**
 * 商品图加载失败时的 emoji 兜底映射表（写死，含 API-SPEC/task 要求的全部条目）
 * 缺省兜底：饮品 🧋 / 轻食甜点 🍰（由调用方按分类决定）
 */
const EMOJI_MAP: Record<string, string> = {
  // —— 13 个在售商品的精确映射 ——
  生椰拿铁: '🥥',
  桂花冰酿拿铁: '🌸',
  焦糖海盐玛奇朵: '🍮',
  美式咖啡: '☕',
  拿铁咖啡: '☕',
  卡布奇诺: '☕',
  澳白: '☕',
  茉莉青提冷萃: '🍇',
  黑糖波波拿铁: '🧋',
  茉莉奶绿: '🍵',
  桂花乌龙: '🌼',
  巴斯克芝士蛋糕: '🍰',
  黄油可颂: '🥐',
  // —— task 指定的通用映射（同名商品/别名兜底）——
  美式: '☕',
  馥芮白: '🥛',
  焦糖玛奇朵: '🍮',
  冷萃: '☕',
  抹茶拿铁: '🍵',
  茉莉奶白: '🍵',
  黑糖珍珠奶茶: '🧋',
  拿铁: '☕',
  咖啡: '☕',
}

/** 按商品名取 emoji，找不到返回 undefined（由调用方给分类级兜底） */
export function emojiForName(name: string): string | undefined {
  return EMOJI_MAP[name]
}

export const DEFAULT_DRINK_EMOJI = '🧋'
export const DEFAULT_FOOD_EMOJI = '🍰'
