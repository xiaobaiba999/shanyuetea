# 山月茶咖 · C端点单 H5（shanyue-h5）

> Vue3 + TypeScript + Vite 5 + Vant 4（按需引入）+ Pinia + Vue Router 4
> 顾客扫码点单（门店自取模式），配合「苍穹外卖 sky-take-out」改造项目「山月茶咖」的后端使用。
> 接口规范唯一事实来源：`/home/z/my-project/agent-ctx/api-spec.md`（C端 8.1~8.11）。

## 快速开始

```bash
# 依赖安装（二选一）
bun install
npm install --no-audit --no-fund

# 开发（默认 5173 端口；mock 模式，无需后端）
bun run dev

# 指定端口
bun run dev -- --port 5174

# 生产构建（vite build，不做类型检查）+ 本地预览
bun run build
bun run preview
```

## 环境变量与 Mock 开关

| 变量 | 文件 | 说明 |
| --- | --- | --- |
| `VITE_USE_MOCK` | `.env.development` / `.env.production` | `true`=纯前端演示（默认，开箱即用）；`false`=请求真实后端 |
| `VITE_API_BASE_URL` | 同上 | axios baseURL，默认 `/api`（dev 下由 vite proxy 转发到 `http://localhost:8080` 并去掉 `/api` 前缀） |

**切换真实后端步骤：**
1. 启动 Spring Boot（8080），实现 API-SPEC 第 2 节 C端 11 个接口；
2. `.env.development` 改 `VITE_USE_MOCK=false`；
3. 保持 `VITE_API_BASE_URL=/api`（vite proxy 已配好 `/api -> localhost:8080` rewrite）；
4. 重启 `bun run dev`。生产部署时同理改 `.env.production`，由 nginx 做同样的 `/api` 反代 + SPA `try_files` 回退。

## Mock 说明（默认模式）

- 开关判断在 `src/utils/env.ts`（`USE_MOCK`），每个 api 函数内部形如：
  `if (USE_MOCK) return unwrap(mockXxx())`，mock 与真实接口返回同构 `Result{code,msg,data}`。
- 持久化：`localStorage`，key 前缀 `shanyue-h5-`（`shop` / `cart` / `cart-seq` / `orders` / `order-meta`），
  另有 UI 缓存 `shanyue-h5-cart-cache` 与查询记录 `shanyue-h5-recent-codes`。刷新不丢。
- 菜单数据：`src/mock/menu-data.ts` 内联自 `export/menu-data.json`（5 分类 / 13 商品；规格组精简为每商品 0~4 组）。
  - 规格组/选项 id 为确定性派生（组 id = 商品id×100+sort，选项 id = 组id×10+sort），保证 `specKey` 跨会话稳定；
  - `黄油可颂` 无规格 → 演示商品卡「+」直接加购路径；`黑糖波波拿铁`/`茉莉奶绿` 带 required=0 的「加料」组 → 演示「无需」chip。
- 购物车 mock（`src/mock/cart.ts`）：`drinkId+specKey` 相同合并数量；`sub` 减到 0 删行；
  `specKey` = 选项 id 按 group.sort 排序逗号拼接（如 `1012,1021,1031,1041`）；`specText` = 选项名按组拼接（`大杯/冰/标准糖/标准`）；
  行内 `amount` = 单价（含 priceDelta 加价），行小计 = `amount × number`。
- 订单 mock（`src/mock/order.ts`）：
  - `orderNumber` = `yyyyMMdd` + 3 位当日序号；`pickUpCode` = `A01` 按天自增（跨天自动重置）；
  - 金额服务端重算 `Σ(amount×number)`；下单成功清空购物车；状态固定 `1 待接单`；
  - 内置 3 条当日演示订单：`A01` 待接单 / `A02` 制作中 / `A03` 待取餐（订单查询页可直接试）。
- 演示工具（订单查询页底部，仅 mock 模式显示）：**模拟打烊/恢复营业**、**重置演示数据**（清空全部 `shanyue-h5-*` 并重新播种）。

## 页面与流程

```
/ 点单主页
   │ 门店横幅(状态灯/公告跑马灯) · 招牌横滑 · 分类侧栏滚动联动 · 商品卡(+或选规格)
   │ 规格弹窗(实时算价) → 加入购物车 → 底部购物车栏/浮层
   ▼
/confirm 确认下单
   │ 清单/备注/手机号(选填) · 打烊校验 → POST /user/order/submit（提交按钮 loading）
   ▼
/success/:pickUpCode 下单成功
   │ 大号取餐号 A01 · 金额/预计等待
   ├──「查询订单进度」→ /query?code=A01
   └──「继续点单」→ /（清空购物车后跳转）

/query 订单查询
     输入取餐号(自动大写)/最近查询 chips → 状态步骤条(下单→接单→制作→待取餐→完成；取消=红色 tag)
     催单(状态1/2) · 取消订单(状态1) · 演示工具
```

## 目录结构

```
src/
├── api/          # 接口层：USE_MOCK 分流（shop/menu/cart/order + unwrap）
├── components/   # StoreHeader / ProductCard / SpecSheet / CartBar / ProductImage
├── mock/         # db(localStorage) / menu-data(内联种子) / menu / shop / cart / order
├── router/       # 4 条路由
├── stores/       # Pinia cart store（手动 watch 持久化 + getters totalCount/totalPrice）
├── styles/       # 全局样式/主题变量
├── types/        # 与 API-SPEC 对齐的 TS 类型
├── utils/        # request(axios) / env / format(分转元) / emoji兜底 / specKey反查
└── views/        # HomeView / ConfirmView / SuccessView / QueryView
```

## UI 规范落地（API-SPEC 第 5 节）

- 主色焦糖 `#C08552`（Vant ConfigProvider 主题变量）、深咖文字 `#3F2A1A`、奶油底 `#FAF6F1`；
- 卡片圆角 16px / 按钮圆角 10px / 阴影 `0 2px 12px rgba(63,42,26,.06)`；
- `max-width: 480px` 居中，桌面打开模拟手机外框；375px 宽无横向滚动；
- 底部安全区 `env(safe-area-inset-bottom)`；「+」按钮 CSS scale 弹性动画；跑马灯两端渐隐遮罩；
- 商品图加载失败 → emoji/渐变兜底（`src/utils/emoji.ts` 映射表，缺省 🧋/🍰）。

## 联调核对清单（对接你的 Java 后端时逐项确认）

- [ ] 统一响应 `Result{code:1,msg:'success',data}`；`code=0` 时前端会 Toast `msg` 并 reject
- [ ] 价格单位一律为**分**（number）；时间格式 `yyyy-MM-dd HH:mm:ss`
- [ ] C端接口前缀 `/user` 且**无需登录**（无 token；`/api` 前缀由反代去掉）
- [ ] `GET /user/shop/status` → `ShopVO{name,status,notice,openHours}`，status: 1 营业 / 0 打烊
- [ ] `GET /user/category/list` → 按 sort 升序，仅 status=1
- [ ] `GET /user/drink/list` → 按 sold 降序、仅 status=1，`specGroups` 内嵌 `specOptions`
- [ ] `POST /user/shoppingCart/add` → 服务端按 groupName+optionName 匹配 priceDelta **重算金额**，拼 specKey，同 `drinkId+specKey` 合并 +1
- [ ] `POST /user/shoppingCart/sub` → number-1，减到 0 删行；`DELETE /user/shoppingCart/clean` 清空
- [ ] `POST /user/order/submit` → `{remark?,phone?}`（从购物车结算），返回 `{id,orderNumber,pickUpCode,orderAmount,estimatedWaitMinutes}`；pickUpCode 按天自增 A01；成功后清购物车
- [ ] `GET /user/order/byPickUpCode/{code}` → `OrderVO`（含 orderItems）；`PUT /user/order/reminder/{id}` 仅状态 1/2；`PUT /user/order/cancel/{id}` 仅状态 1
- [ ] 若后端购物车 `amount` 语义为「行小计」而非「单价」，请同步调整 `stores/cart.ts` 的 `totalPrice` 与确认页小计展示

## 已知边界（演示定位）

- 未接微信支付/登录（自取匿名模式）；WebSocket 叫号、管理端为另外工程；
- 「购物车内 +1」通过 specKey 反查规格后复用 `addToCart` 实现（真实/模拟后端行为一致）；
- 「删除单行」等价于连续 `sub` 至删行（C端接口未提供删行端点，与苍穹外卖保持一致）；
- 构建未跑 `vue-tsc`（按任务要求 `build` 直接 `vite build`），但代码保持基本类型正确。
