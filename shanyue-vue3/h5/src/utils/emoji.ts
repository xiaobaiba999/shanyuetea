/**
 * 商品图加载失败时的衬线字兜底映射表（写死，含 API-SPEC/task 要求的全部条目）
 * 缺省兜底：饮品「茶」/ 轻食甜点「食」（由调用方按分类决定）
 */
const EMOJI_MAP: Record<string, string> = {
  // —— 13 个在售商品的精确映射 ——
  生椰拿铁: '茶',
  桂花冰酿拿铁: '茶',
  焦糖海盐玛奇朵: '咖',
  美式咖啡: '咖',
  拿铁咖啡: '咖',
  卡布奇诺: '咖',
  澳白: '咖',
  茉莉青提冷萃: '茶',
  黑糖波波拿铁: '茶',
  茉莉奶绿: '茶',
  桂花乌龙: '茶',
  巴斯克芝士蛋糕: '食',
  黄油可颂: '食',
  // —— task 指定的通用映射（同名商品/别名兜底）——
  美式: '咖',
  馥芮白: '咖',
  焦糖玛奇朵: '咖',
  冷萃: '咖',
  抹茶拿铁: '茶',
  茉莉奶白: '茶',
  黑糖珍珠奶茶: '茶',
  拿铁: '咖',
  咖啡: '咖',
}

/** 按商品名取兜底字，找不到返回 undefined（由调用方给分类级兜底） */
export function emojiForName(name: string): string | undefined {
  return EMOJI_MAP[name]
}

export const DEFAULT_DRINK_EMOJI = '茶'
export const DEFAULT_FOOD_EMOJI = '食'
