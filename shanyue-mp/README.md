# 山月茶咖 · 微信小程序端（原生）

「山月茶咖 SHANYUE TEA & COFFEE」原生微信小程序，接口完全按苍穹外卖模式（JWT 放 `token` 请求头、统一响应 `Result{code,msg,data}`、code===1 成功）+ 山月 v2.0 补全契约（规格 `specGroups` / `specSelected` 快照、取餐号 `pickupNumber`、外送配送费 `deliveryFee`、`deliveryMode` 1 自取 2 外送）。

**内置 USE_MOCK 演示模式**：不需要后端、不需要 AppID 也能先跑通完整点单流程；联调时改一个开关即可切到真实 Java 后端。

> **【v2 功能包已落地】**（F3 积分商城与周边已按需求移除）：首页轮播横幅（F1）/ 会员等级（F2）/ 券包+下单选券（F4，对应 Redis SETNX+Lua 练习）/ 每日签到（F5，对应 BITMAP）/ 评价墙与写评价（F6）/ 茶小月 AI 点单聊天（F7，对应大模型接入）/ 取餐 WebSocket 推送（F8）/ 首页骨架屏（F9）。
> 前后端契约与 Java 练习步骤见 **`shanyue-server-skeleton/docs/v2-features.md`**；mock 数据源在 `utils/mock-v2.js`（接口路径与 Java 接口桩一一对应）。

---

## 0. 项目里有什么

```
shanyue-mp/
├── app.js / app.json / app.wxss      # 入口、页面注册（tab：点单/订单/我的）、全局品牌样式
├── project.config.json               # 工程配置（appid 已用测试号 touristappid）
├── images/                           # tabBar 图标 + brand/ 茶小月品牌图
├── utils/
│   ├── config.js                     # ★ 全局开关：USE_MOCK / BASE_URL（联调只改这一个文件）
│   ├── request.js                    # 统一请求封装（自动带 token、解包 Result、401 跳登录）
│   ├── mock.js                       # 内置演示数据（USE_MOCK=true 时替代后端；含下单用券事务钩子）
│   ├── mock-v2.js                    # 【v2】功能包演示数据与接口路由（契约源）
│   ├── pickup-ws.js                  # 【v2/F8】取餐进度推送（mock 模拟/真实 WebSocket 双模式）
│   ├── auth.js                       # 登录态管理（wx.login → code 换 token）
│   └── format.js                     # 金额/时间/状态文案格式化
└── pages/
    ├── login/          # 微信一键登录
    ├── menu/           # 点单主页（【v2】轮播横幅/功能入口/骨架屏 + 分类/规格弹层/购物车）
    ├── submit/         # 确认订单（自取/外送 + 【v2/F4】选券）
    ├── success/        # 下单成功（取餐号/模拟支付）
    ├── address-list/   # 收货地址列表
    ├── address-edit/   # 新增/编辑地址
    ├── order-list/     # 我的订单（状态筛选/分页/催单/取消/去支付）
    ├── order-detail/   # 订单详情（步骤条/取餐号/【v2/F8】推送徽标/【v2/F6】评价入口）
    ├── me/             # 我的（用户卡/【v2/F2】会员卡/积分/关于门店/退出登录）
    ├── ai-order/       # 【v2/F7】茶小月 AI 点单聊天（推荐卡一键加购）
    ├── checkin/        # 【v2/F5】每日签到日历
    ├── coupons/        # 【v2/F4】券包（待领/可用/已用；秒杀券库存进度）
    ├── level/          # 【v2/F2】会员等级（成长值进度/等级时间轴/权益）
    ├── reviews/        # 【v2/F6】评价墙（标签筛选/分页加载）
    └── review-write/   # 【v2/F6】写评价（星级/印象标签/得积分）
```

---

## 1. 下载安装「微信开发者工具」

1. 打开官方下载页：https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html
2. 选择 **稳定版 Stable Build**，按操作系统下载：
   - Windows 64 位 → 下载 `.exe` 安装包，双击下一步装完即可；
   - macOS → 下载 `.dmg`，把图标拖进「应用程序」。
3. 首次打开会要求**扫码登录**：用你自己的微信扫码即可（体验项目不需要注册小程序账号）。

> 如果连扫码都嫌麻烦：安装后也可以用「游客模式」进入部分功能，但建议直接扫码，最省事。

---

## 2. 导入本项目

1. 先把本项目文件夹 `shanyue-mp/` 下载/解压到电脑任意目录（**路径里最好不要有中文和空格**）。
2. 打开微信开发者工具 → 首页点 **「导入项目」**（不是「新建」）：
   - **目录**：选择 `shanyue-mp` 这个文件夹（里面能直接看到 `app.json`、`project.config.json`）；
   - **AppID**：点「测试号」链接使用自动分配的测试号（本项目工程文件里已预置 `touristappid`，正常导入即可）；
   - **名称**：随便填，比如「山月茶咖」。
3. 点「确定」进入开发者界面。
4. **必须做一步**：点工具右上角 **「详情」→「本地设置」→ 勾选 ☑「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**。
   > 不勾的话，所有 `http://localhost:8080` 请求都会被工具拦截报「不在以下 request 合法域名列表中」。

---

## 3. 第一次跑：USE_MOCK = true 看完整演示流程

打开 `utils/config.js`，把开关改为 `true` 并保存（工具会自动重新编译）：

```js
const config = {
  BASE_URL: 'http://localhost:8080',
  USE_MOCK: true,   // ← 改成 true：使用内置演示数据，不需要启动后端
  ...
};
```

然后在模拟器里跟着走一遍（建议按顺序点）：

| 步骤 | 操作 | 你会看到 |
|---|---|---|
| ① 登录 | 自动进入登录页 → 点「微信一键登录」 | mock 签发 token，进入点单页 |
| ② 菜单 | 左侧切分类、顶部切「饮品/套餐」 | 8 款饮品 + 2 款套餐，含 1 款售罄演示 |
| ③ 规格 | 点「生椰拿铁」 | 底部弹层选杯型/温度/糖度，价格实时变化（+3 大杯） |
| ④ 购物车 | 底部购物车条 → 点开 | 加减购、同规格合并、清空 |
| ⑤ 下单 | 「去结算」→ 切「外送」选地址 → 提交订单 | 外送自动加 5 元配送费；自取出取餐号大卡 |
| ⑥ 支付 | 成功页点「模拟支付」 | 两步支付（生成参数→回调），状态变「待接单」 |
| ⑦ 订单 | 底部 tab「订单」 | 6 个种子订单覆盖全部状态；切状态筛选、下拉刷新、触底翻页 |
| ⑧ 详情/操作 | 点任意订单卡 | 步骤条、单号复制、催单/取消/再来一单/去支付 |
| ⑨ 我的 | 底部 tab「我的」 | 用户卡、积分（下单会涨）、地址管理、关于门店、退出登录 |

> 想把演示数据恢复出厂？「我的」页点「重置演示数据」（仅 USE_MOCK=true 时显示）。

---

## 4. 联调真实后端

前置：你已按课程/工程把 Java 后端（苍穹模式）跑起来，监听 `8080` 端口。

### 4.1 电脑模拟器联调（最常用）

打开 `utils/config.js`，只改两行：

```js
const config = {
  BASE_URL: 'http://localhost:8080',  // 后端地址：本机 8080
  USE_MOCK: false,                    // ← 关掉演示数据，走真实接口
  ...
};
```

然后确认三件事：
1. 后端已启动（浏览器访问一下接口能通，比如返回 401/JSON 都算通）；
2. 开发者工具「详情 → 本地设置」已勾选「不校验合法域名」（见第 2 步）；
3. 模拟器里**先退出登录再重新登录**（换一枚真实 JWT），然后全流程点一遍即可。

### 4.2 真机预览联调（手机上看效果）

真机访问不到你电脑里的 `localhost`，必须改成**电脑在 WiFi 里的局域网 IP**：

1. 查电脑 IP：
   - Windows：`Win + R` → `cmd` → 输入 `ipconfig`，找「无线局域网适配器 WLAN」下的 **IPv4 地址**（如 `192.168.1.100`）；
   - macOS：系统设置 → Wi-Fi → 详细信息 → 看「IP 地址」。
2. 改配置：

```js
BASE_URL: 'http://192.168.1.100:8080',  // 换成你查到的局域网 IP
USE_MOCK: false,
```

3. **手机和电脑必须连同一个 WiFi**（公司/校园网常有 AP 隔离会不通，换手机热点最稳）。
4. 工具栏点「预览」生成二维码，手机微信扫码打开。
5. 还是不通？在手机浏览器里访问 `http://192.168.1.100:8080` 先验证网络层是否可达，再排查防火墙（见 FAQ③）。

> 后端注意：C 端接口都在 `/user/**` 路径下且 JWT 放在名为 `token` 的请求头里（苍穹模式），与员工端 `Authorization` 不同。

---

## 5. 后端接口清单简表（小程序用到的全部接口）

统一响应 `Result{ code:1成功/0失败, msg, data }`；除登录外均需请求头 `token: <JWT>`；HTTP 401 时小程序自动清登录态并跳登录页。

| 模块 | 方法 & 路径 | 说明 | 关键参数 / 返回 |
|---|---|---|---|
| 登录 | POST `/user/user/login` | 免登录，wx.login 的 code 换 JWT | 入参 `{code}`；出参 `{id, openid, token, nickName, avatarUrl}` |
| 资料 | GET `/user/user/profile` | 我的资料（积分） | 出参 `{id, nickName, avatarUrl, phone, points}` |
| 分类 | GET `/user/category/list?type=` | 1 饮品分类 / 2 套餐分类 | 出参 `Category[]` |
| 饮品 | GET `/user/drink/list?categoryId=` | 含售罄（status 0 前端置灰） | 出参 `DrinkVO[]`，山月补全 `monthSales`、`specGroups:[{name,options:[{name,priceDelta}]}]` |
| 套餐 | GET `/user/setmeal/list?categoryId=` | 套餐列表 | 出参 `Setmeal[]` |
| 套餐明细 | GET `/user/setmeal/drink/{id}` | 套餐包含哪些菜 | 出参 `DrinkItemVO[]` |
| 购物车 | POST `/user/shoppingCart/add` | 加购 | 入参 `{drinkId?, setmealId?, drinkFlavor}`，山月用 `specSelected`（String JSON 快照）+`amount`(单价)+`number` |
| 购物车 | POST `/user/shoppingCart/sub` | 减购（同上参数，数量-1，减到 0 删除） | — |
| 购物车 | GET `/user/shoppingCart/list` | 购物车列表 | 出参 `ShoppingCartDTO[]` |
| 购物车 | DELETE `/user/shoppingCart/clean` | 清空 | — |
| 下单 | POST `/user/order/submit` | 提交订单 | 入参 `{deliveryMode:1自取\|2外送, addressBookId(外送必填), remark, payMethod, amount}`；出参 `{id, orderNumber, orderAmount, orderTime, pickupNumber(自取), deliveryFee(外送5元)}` |
| 支付① | PUT `/user/order/payment` | 生成支付参数（不改状态） | 入参 `{orderNumber, payMethod}`；出参微信支付参数 |
| 支付② | PUT `/user/order/paySuccess?orderNumber=` | 模拟支付回调（payStatus→1，状态 1→2） | query 传订单号 |
| 订单 | GET `/user/order/historyOrders?page&pageSize&status` | 历史订单分页，status 不传=全部 | 出参 `PageResult{total, records:OrderVO[]}`（含 orderDetailList） |
| 订单 | GET `/user/order/orderDetail/{id}` | 订单详情 | 出参 `OrderVO` |
| 订单 | PUT `/user/order/cancel/{id}` | 取消（仅 1 待付款 / 2 待接单） | — |
| 订单 | POST `/user/order/repetition/{id}` | 再来一单（明细插回购物车） | — |
| 订单 | GET `/user/order/reminder/{id}` | 催单（仅 2 待接单 / 3 制作中） | — |
| 地址簿 | POST `/user/addressBook` | 新增（苍穹不回传 id） | 入参 AddressBook：`consignee/phone/sex('1'男'0'女)/detail/label/isDefault…` |
| 地址簿 | PUT `/user/addressBook` | 编辑（body 必须含 `id`） | 同上 |
| 地址簿 | DELETE `/user/addressBook?id=` | 删除（★ query 传 id，不是路径参数） | — |
| 地址簿 | GET `/user/addressBook/list` | 列表 | `AddressBook[]` |
| 地址簿 | GET `/user/addressBook/{id}` | 单条（编辑回显） | `AddressBook` |
| 地址簿 | GET `/user/addressBook/default` | 默认地址（无则 data=null） | `AddressBook` |
| 地址簿 | PUT `/user/addressBook/default` | 设为默认（★ body 传 `{id, isDefault:1}`） | — |
| 店铺 | GET `/user/shop/status` | 1 营业 / 0 打烊 | data 直接是数字 |

**订单状态机**（6 状态，与课程一致）：`1 待付款 → 2 待接单 → 3 已接单·制作中 → 4 待取餐(自取)/派送中(外送) → 5 已完成`；任意 1/2 状态可 `6 已取消`。状态文案与颜色统一在 `utils/format.js` 的 `STATUS_MAP`。

---

## 6. 常见问题（FAQ）

**① 一直被踢回登录页 / 401 循环跳登录？**
登录接口返回的 `token` 没有被后端认出来。依次排查：后端是否对 `/user/**` 放行了登录接口并校验名为 `token` 的请求头（苍穹拦截器写法）；登录响应里是否真的有 `token` 字段（`storage` 里 `shanyue_user` 可看）；JWT 是否过期（后端配置的过期时间）；系统时间偏差过大也会导致 JWT 校验失败。

**② 报「不在以下 request 合法域名列表中」/ 域名不合法？**
两种解法任选：开发阶段 → 「详情 → 本地设置 → 勾选不校验合法域名」（第 2 步）；要上线 → 在微信公众平台「开发管理 → 开发设置 → 服务器域名」里把后端域名配进 `request` 合法域名（必须是 HTTPS + 备案域名，`http://localhost` 只能在开发工具里用）。

**③ 请求 fail「网络异常」/ 端口不通？**
按顺序检查：后端是否真的起来了（命令行 `curl http://localhost:8080` 有响应才算）；`config.js` 的 `BASE_URL` 是否写对（注意别漏 `http://`、端口别写成前端端口）；真机场景 IP 是否是局域网 IP 而不是 `localhost`、手机和电脑是否同一 WiFi、电脑防火墙是否放行了 8080（Windows 常见拦截，临时可关防火墙验证）。

**④ 下单时提示「购物车为空」？**
下单接口依赖购物车里有数据（苍穹口径：先加购再下单，后端从购物车生成订单明细）。若你绕过菜单页直接进确认订单页、或后端把购物车表清了，就会出现该提示；另外**先登录后下单**——未登录时 token 失效，购物车接口 401 也会表现为空。从菜单页正常加购后再下单即可。

**⑤ 接口返回 code: 0（或 msg 报错）怎么排查？**
`code:0` 是后端**业务校验没过**，不是网络问题。看 `msg` 提示：常见有「订单状态不支持取消/催单」（只有 1/2 可取消、2/3 可催单）、「地址信息有误」（外送必须传存在的 `addressBookId`）、「该订单已支付」（重复点支付）、金额校验失败（前端按「分」计算提交 `amount`，后端若有二次校验注意精度）。照着 msg 修参数或换一条符合状态的订单重试。

**⑥ 真机预览白屏 / 部分页面空白？**
先看工具「调试器 → Console」与真机 vConsole 的报错：多半是 BASE_URL 不可达导致页面数据接口全挂（解决见 FAQ③）；其次是基础库过旧（工具「详情 → 调试基础库」选 2.30+）；再检查手机微信版本。若只是图片不显示、页面能出来，是图片域名没配 `downloadFile` 合法域名，本项目默认不依赖网络图片，不受影响。

---

## 7. 二次开发提示

- **联调只改 `utils/config.js`**，页面代码不用动（mock 与真实接口的路径/出入参完全一致）。
- 金额一律走 `utils/format.js` 的 `fenRound / fmtMoney / sumMoney`（转分取整，防 0.1+0.2 浮点误差）。
- 订单状态文案/颜色统一改 `format.js` 的 `STATUS_MAP`，别在页面里硬编码。
- 新增页面记得在 `app.json` 的 `pages` 里注册；tab 页跳转只能 `switchTab`。
- 「重置演示数据」只清 mock 库；真实后端联调时该按钮自动隐藏。

祝联调顺利，山月入杯，茶咖相宜 🍵☕
