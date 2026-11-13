# 商品图片说明

本目录存放 C端 H5 引用的商品图与门店 banner（路径与 `menu-data.json` 的 `image` 字段一致，如 `/menu/coconut-latte.png`）。

**本仓库已随附一份从 Next.js 视觉稿原型拷贝的图片**（13 张商品图 + 1 张 banner.png），可直接运行。
如需替换为真实拍摄图/新图，保持同名覆盖即可，或重新生成：

- 原型仓库：`/home/z/my-project/public/menu/`（由 `scripts/generate-images.mjs` 用 z-ai SDK 生成）
- 命名对照：
  | 商品 | 文件 |
  | --- | --- |
  | 生椰拿铁 | coconut-latte.png |
  | 桂花冰酿拿铁 | osmanthus-latte.png |
  | 焦糖海盐玛奇朵 | caramel-macchiato.png |
  | 美式咖啡 | americano.png |
  | 拿铁咖啡 | latte.png |
  | 卡布奇诺 | cappuccino.png |
  | 澳白 | flat-white.png |
  | 茉莉青提冷萃 | grape-cold-brew.png |
  | 黑糖波波拿铁 | brown-sugar-boba.png |
  | 茉莉奶绿 | jasmine-milk-tea.png |
  | 桂花乌龙 | osmanthus-oolong.png |
  | 巴斯克芝士蛋糕 | basque-cheesecake.png |
  | 黄油可颂 | butter-croissant.png |
  | 门店 banner | banner.png |

**兜底机制**：图片缺失/加载失败时，`ProductImage` 组件会自动降级为「咖啡色渐变底 + emoji」（映射表见 `src/utils/emoji.ts`），页面不会出现裂图。
