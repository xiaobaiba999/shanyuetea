import type { CategoryVO, DrinkVO, SpecGroupVO, SpecOptionVO } from '@/types'

/**
 * 内联种子数据（源自 /home/z/my-project/export/menu-data.json）
 * 5 分类 / 13 商品；规格组精简为每商品 0-4 组
 * id 规则（确定性，保证 specKey 跨会话稳定）：
 *   规格组 id   = 商品id * 100 + 组内 sort
 *   规格选项 id = 规格组 id * 10 + 选项 sort
 */

export interface SeedGroup {
  id: number
  name: string
  required: 0 | 1
  sort: number
  options: { id: number; name: string; priceDelta: number; sort: number }[]
}

export const CATEGORIES: CategoryVO[] = [
  { id: 1, name: '招牌推荐', icon: '⭐', sort: 1 },
  { id: 2, name: '经典咖啡', icon: '☕', sort: 2 },
  { id: 3, name: '创意特调', icon: '🧋', sort: 3 },
  { id: 4, name: '茶饮', icon: '🍵', sort: 4 },
  { id: 5, name: '轻食甜点', icon: '🍰', sort: 5 },
]

/** 生成一个规格组（选项 id 自动派生） */
function g(pid: number, sort: number, name: string, required: 0 | 1, opts: [string, number][]): SeedGroup {
  const id = pid * 100 + sort
  return {
    id,
    name,
    required,
    sort,
    options: opts.map(([oName, delta], i) => ({ id: id * 10 + i + 1, name: oName, priceDelta: delta, sort: i + 1 })),
  }
}

// 复用的规格组模板（按商品 pid 生成对应 id）
const cup = (pid: number) => g(pid, 1, '杯型', 1, [['中杯', 0], ['大杯', 300], ['超大杯', 500]])
const temp = (pid: number) => g(pid, 2, '温度', 1, [['冰', 0], ['热', 0], ['温', 0]])
const sugar = (pid: number) => g(pid, 3, '糖度', 1, [['标准糖', 0], ['半糖', 0], ['少糖', 0], ['无糖', 0]])
const strength = (pid: number) => g(pid, 4, '浓度', 1, [['标准', 0], ['加浓', 200]])
const topping = (pid: number) => g(pid, 4, '加料', 0, [['珍珠', 200], ['布丁', 300], ['奶盖', 300]])

interface DrinkSeed {
  id: number
  name: string
  categoryId: number
  price: number
  originPrice: number | null
  image: string
  description: string
  featured: 0 | 1
  sold: number
  groups: SeedGroup[]
}

const DRINK_SEEDS: DrinkSeed[] = [
  {
    id: 1, name: '生椰拿铁', categoryId: 1, price: 1590, originPrice: 1890,
    image: '/menu/coconut-latte.png', description: '厚椰乳与浓缩咖啡的经典相遇，顺滑清甜',
    featured: 1, sold: 3281, groups: [cup(1), temp(1), sugar(1), strength(1)],
  },
  {
    id: 2, name: '桂花冰酿拿铁', categoryId: 1, price: 1790, originPrice: null,
    image: '/menu/osmanthus-latte.png', description: '金桂酒酿入奶，一口秋天的温柔',
    featured: 1, sold: 1865, groups: [cup(2), temp(2), sugar(2), strength(2)],
  },
  {
    id: 3, name: '焦糖海盐玛奇朵', categoryId: 1, price: 1890, originPrice: null,
    image: '/menu/caramel-macchiato.png', description: '咸甜交织的焦糖云顶，层次感拉满',
    featured: 1, sold: 1204, groups: [cup(3), temp(3), sugar(3), strength(3)],
  },
  {
    id: 4, name: '美式咖啡', categoryId: 2, price: 990, originPrice: null,
    image: '/menu/americano.png', description: '双份浓缩加冰，干净利落的美式风味',
    featured: 0, sold: 4520, groups: [cup(4), temp(4), sugar(4), strength(4)],
  },
  {
    id: 5, name: '拿铁咖啡', categoryId: 2, price: 1390, originPrice: null,
    image: '/menu/latte.png', description: '经典意式拿铁，奶咖比例刚刚好',
    featured: 0, sold: 3890, groups: [cup(5), temp(5), sugar(5), strength(5)],
  },
  {
    id: 6, name: '卡布奇诺', categoryId: 2, price: 1390, originPrice: null,
    image: '/menu/cappuccino.png', description: '绵密奶泡撒可可粉，经典意式配方',
    featured: 0, sold: 2100, groups: [cup(6), temp(6), sugar(6), strength(6)],
  },
  {
    id: 7, name: '澳白', categoryId: 2, price: 1590, originPrice: null,
    image: '/menu/flat-white.png', description: '双份浓缩配丝绒奶泡，咖啡感更强',
    featured: 0, sold: 1580, groups: [cup(7), temp(7), sugar(7), strength(7)],
  },
  {
    id: 8, name: '茉莉青提冷萃', categoryId: 3, price: 1990, originPrice: null,
    image: '/menu/grape-cold-brew.png', description: '茉莉花茶汤撞青提果肉，清爽果香',
    featured: 0, sold: 980, groups: [cup(8), temp(8), sugar(8)],
  },
  {
    id: 9, name: '黑糖波波拿铁', categoryId: 3, price: 1690, originPrice: null,
    image: '/menu/brown-sugar-boba.png', description: '虎纹挂壁配 Q 弹波波，嚼得到的快乐',
    featured: 0, sold: 2350, groups: [cup(9), temp(9), sugar(9), topping(9)],
  },
  {
    id: 10, name: '茉莉奶绿', categoryId: 4, price: 1290, originPrice: null,
    image: '/menu/jasmine-milk-tea.png', description: '茉莉绿茶打底，奶香轻盈不腻',
    featured: 0, sold: 1730, groups: [cup(10), temp(10), sugar(10), topping(10)],
  },
  {
    id: 11, name: '桂花乌龙', categoryId: 4, price: 1190, originPrice: null,
    image: '/menu/osmanthus-oolong.png', description: '乌龙茶底飘桂香，清雅回甘',
    featured: 0, sold: 1320, groups: [cup(11), temp(11), sugar(11)],
  },
  {
    id: 12, name: '巴斯克芝士蛋糕', categoryId: 5, price: 1990, originPrice: null,
    image: '/menu/basque-cheesecake.png', description: '焦香外皮配流心内芯，重芝士爱好者必点',
    featured: 0, sold: 860,
    groups: [g(12, 1, '规格', 1, [['标准装', 0], ['分享装', 600]]), g(12, 2, '温度', 1, [['冷藏', 0], ['微加热', 0]])],
  },
  {
    // 无规格商品：演示「+ 直接加购」路径
    id: 13, name: '黄油可颂', categoryId: 5, price: 990, originPrice: null,
    image: '/menu/butter-croissant.png', description: '72 层起酥，出炉即香',
    featured: 0, sold: 1420, groups: [],
  },
]

/** 组装为接口形态 DrinkVO（specGroups 内嵌 specOptions，按 group.sort 升序） */
export const DRINKS: DrinkVO[] = DRINK_SEEDS.map((s) => ({
  id: s.id,
  name: s.name,
  categoryId: s.categoryId,
  price: s.price,
  originPrice: s.originPrice,
  image: s.image,
  description: s.description,
  featured: s.featured,
  status: 1 as const,
  sold: s.sold,
  specGroups: [...s.groups]
    .sort((a, b) => a.sort - b.sort)
    .map<SpecGroupVO>((grp) => ({
      id: grp.id,
      name: grp.name,
      required: grp.required,
      sort: grp.sort,
      specOptions: grp.options.map<SpecOptionVO>((o) => ({ ...o })),
    })),
}))

export function findDrink(drinkId: number): DrinkVO | undefined {
  return DRINKS.find((d) => d.id === drinkId)
}
