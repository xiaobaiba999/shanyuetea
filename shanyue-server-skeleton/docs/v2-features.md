# 山月茶咖 · v2 功能升级包落地手册（docs/v2-features.md）

> 面向对象：拿着这份手册把 6 个 Java 接口桩（`shanyue-server` 的 `controller/user/` 下）
> 逐步换成真实现的同学。每个桩的返回值与前端 `utils/mock-v2.js` 完全同构，
> 所以"每替换一个接口，前端对应页面立即可真实联调"，随时可验证、随时可回退。
> 配套文件：`sql/v2_features.sql`（建表 + 种子）。

---

## 0. 这本手册怎么用

1. 先看第 1 节总览表，知道每个功能包"前端页面 → 接口 → 后端落点 → 课程章节 → 工时"的全貌；
2. 再看第 7 节联调三步（改 config → 跑 SQL → 起后端），此时全部页面走的都是"桩数据"；
3. 最后按第 8 节建议顺序逐个消灭 TODO —— 每个控制器方法头上都写了大段替换步骤，
   手册不再重复，只补全局性的口径与坑。

---

## 1. 功能包总览（8 个包）

> **F3 积分商城与茶小月周边已按需求方要求移除**：积分只保留数值展示与
> 签到（+5）/评价（+10）入账，无兑换消费出口，不建积分商城表与页面。
> 因此功能包编号从 F1、F2 直接跳到 F4。

| 包号 | 功能 | 前端页面路径 | 接口清单 | 后端落点（桩 → 真实现） | 课程章节映射 | 预计工时 |
| --- | --- | --- | --- | --- | --- | --- |
| F1 | 轮播横幅 | `pages/menu/menu`（顶部轮播位；`linkUrl` 跳 menu/checkin/coupons） | `GET /user/banner/list` | `BannerController` → banner 表 + BannerMapper（status=1 按 sort 排序）；管理端可套 admin/DishController 的 CRUD | 苍穹：分类/菜品管理 CRUD 套路 | 0.5 天 |
| F2 | 会员等级 | `pages/level/level` | `GET /user/member/level` | `MemberController` → user.growth 冗余字段（方案 A）+ 等级常量类 | 无直接章节；「冗余字段 vs 聚合查询」的取舍（答辩讲点） | 1 天 |
| F4 | 券包 | `pages/coupons/coupons`、`pages/submit/submit`（下单选券） | `GET /user/coupon/list`<br>`POST /user/coupon/receive/{id}`<br>`GET /user/coupon/usable?amount=` | `CouponController` → coupon/user_coupon 两表 + Redis SETNX 一人一单 + Lua 原子扣库存；用券挂进 `OrderServiceImpl.submit` 事务 | **黑马 Redis 实战：优惠券秒杀（超卖/一人一单/Lua 脚本）** | 2.5~3 天 |
| F5 | 每日签到 | `pages/checkin/checkin` | `GET /user/checkin/info`<br>`POST /user/checkin/sign` | `CheckinController` → Redis BITMAP（`checkin:{userId}:{yyyyMM}`）+ user.points 入账 | **黑马 Redis 实战：BITMAP 签到（SETBIT/BITFIELD/BITCOUNT）** | 1.5~2 天 |
| F6 | 评价墙 | `pages/reviews/reviews`、`pages/review-write/review-write` | `GET /user/review/wall`<br>`GET /user/review/tags`<br>`POST /user/review/submit` | `ReviewController` → review 表（tags 存 JSON）+ PageHelper 分页 + uk_order_id 唯一索引一单一评 | 苍穹：历史订单分页（PageHelper）、MyBatis collection 一对多（方案 2 练习） | 1.5 天 |
| F7 | 茶小月 AI 点单（毕设主菜） | `pages/ai-order/ai-order` | `GET /user/ai/suggests`<br>`POST /user/ai/order` | `AiOrderController` → AiOrderService 直调 OpenAI 兼容接口（三级方案见桩内 TODO） | 苍穹：HttpClient 调第三方接口模式（RestTemplate 已配好） | 2~3 天 |
| F8 | 取餐进度推送 | `pages/order-detail/order-detail`（`utils/pickup-ws.js`） | 不走 REST，WebSocket `/ws/user_{userId}` | 复用既有 `WebSocketServer.sendToAllClient` 推 type=3，落点在管理端订单状态流转处（见第 5 节） | 苍穹：WebSocket 来单提醒/催单 | 0.5~1 天 |
| F9 | 极简启动 | 登录 → 菜单首屏 | 无新增接口 | **无后端工作**（纯前端骨架屏，见第 6 节） | 无 | 0 天 |

---

## 2. 接口契约总表（抄自 `utils/mock-v2.js` 文件头，Java 实现照此对齐）

| 功能 | 接口 | 返回 |
| --- | --- | --- |
| F1 轮播横幅 | `GET /user/banner/list` | `BannerVO[]` |
| F2 会员等级 | `GET /user/member/level` | `MemberLevelVO` |
| F4 券包 | `GET /user/coupon/list` | `CouponVO[]`（含领取/使用状态合并视图） |
| | `POST /user/coupon/receive/{id}` | `null`（一人一单：重复领取报错） |
| | `GET /user/coupon/usable?amount=` | `UsableCouponVO[]`（下单页选券用） |
| | （用券在下单事务里做：submit 带 `userCouponId`，见 mock.js 的 applyCoupon 挂钩） | |
| F5 每日签到 | `GET /user/checkin/info` | `CheckinVO` |
| | `POST /user/checkin/sign` | `CheckinResultVO` |
| F6 评价墙 | `GET /user/review/wall?page&pageSize&drinkId` | `PageResult{total, records}` |
| | `GET /user/review/tags` | `[{tag, count}]` |
| | `POST /user/review/submit` | `{pointsGained, points}` |
| F7 AI 点单 | `GET /user/ai/suggests` | `string[]`（快捷提问 chips） |
| | `POST /user/ai/order` | `AiOrderVO{reply, recommend}` |
| F8 取餐推送 | 不走 REST —— `utils/pickup-ws.js`（WebSocket，type=3 取餐进度） | |

### 联调口径（写 Java 时照此实现，前端 `config.js` 的 `USE_MOCK` 改 `false` 即无缝切换）

- **签到**：连续签到 streak 从今天（已签）或昨天往前数连续日期；积分 +5/天（写 user.points）
- **用券**：下单接口事务内校验门槛 → 抵扣 → 置已使用（一人一单 + 防超卖是 Java 练习重点）
- **评价**：仅「已完成 status=5」订单可评，一单一评；评价奖励 +10 积分
- **成长值**：下单每 1 元 +2、签到 +5、评价 +10；等级阈值 0/200/600/1200/2000（Lv1~Lv5）
- **AI**：`POST /user/ai/order` 请求体 `{ message, history, lastRecommendId }`；
  history 是最近几轮对话 `[{role:'user'|'assistant', content}]`，真实后端拼进大模型上下文

---

## 3. 数据库：先跑 `sql/v2_features.sql`

在 `schema.sql`、`data.sql` 之后执行（`USE shanyue; source v2_features.sql;`）：

| 表 | 功能包 | 说明 |
| --- | --- | --- |
| banner | F1 | 横幅 3 条种子 |
| coupon | F4 | 券模板 4 张种子（秒杀券 id=2 带 stock 100 / remain 37） |
| user_coupon | F4 | 用户券 2 张种子（id 91 可用 / id 92 已使用）；`uk_user_coupon(user_id, coupon_id)` 一人一单兜底 |
| review | F6 | 他人评价 8 条种子；`uk_review_order_id` 一单一评兜底；tags 存 JSON 字符串（方案 1） |
| checkin_log | F5 | 签到流水 5 天种子；**生产用 Redis BITMAP 替代表存储，本表作兜底对账** |
| user（ALTER） | F2/F5/F6 | 加 growth / points 两列（方案 A 冗余字段；方案 B 视图写法在 SQL 注释里） |

**drink id 号段提醒**：mock 演示菜单用 101~108 号段，data.sql 的 drink 表是 1~8 号段；
review 种子的 drink_id 按 mock 原样写 101~107，评价墙"按饮品筛选"（`?drinkId=`）传真实
drink 表 id 时不会命中这些氛围种子 —— 需要命中就把种子 drink_id 手工改掉，不改不影响
默认评价墙（drinkName 是冗余字段）。

---

## 4. 六个桩控制器速览（都在 `controller/user/` 下）

每个类的知识点与逐方法 TODO 都写在代码注释里，这里只列"练什么"：

| 桩文件 | 有状态演示 | 核心练习点 |
| --- | --- | --- |
| BannerController | 无 | 建表 → Mapper 查询 → 管理端 CRUD（热身） |
| MemberController | 无 | growth 冗余字段方案 A/B 取舍；计算点挂 paySuccess / checkin / review 三处 |
| CouponController | 有（可领券、秒杀库存递减、重复领取报错） | SETNX 一人一单 + Lua 三步合一防超卖 + submit 事务内用券 |
| CheckinController | 有（可签到、重复签到报错、积分累加） | BITMAP SETBIT/BITFIELD/BITCOUNT 三连 + 连续签到位运算 |
| ReviewController | 有（一单一评重复提交报错；评价墙恒返 8 条种子） | PageHelper 分页 + 动态 SQL + 唯一索引 + tags 两种存法 |
| AiOrderController | 无（关键词匹配版即降级逻辑） | 大模型三级接入 + 结构化输出解析 + 降级兜底 |

> 有状态桩说明：为让页面"点得动"，签到/领券/评价在桩内用了内存变量记状态
> （重启即重置）；换成真实现时这些内存状态整体删除。评价墙桩不落库，
> 所以 submit 成功后新评价不会立刻出现在墙首 —— 真实实现后才会（这是预期行为）。

---

## 5. F8 取餐进度推送（WebSocket，type=3）

**消息契约**（复用苍穹 `/ws/{sid}` 端点，扩展 type=3 给 C 端）：

```json
{"type": 3, "orderId": 5, "status": 4, "content": "您的茶好了，请取餐"}
```

- type=1 来单提醒（管理端监工屏，`OrderServiceImpl.paySuccess` 已推）
- type=2 催单提醒（管理端，`OrderServiceImpl.reminder` 已推）
- type=3 取餐进度推送（**C 端小程序，本次新增**）

**Java 落点**：管理端 OrderController 的状态流转接口对应的 Service 方法里，调用
`WebSocketServer.sendToAllClient(JSON.toJSONString(msg))`（该 Bean 在 OrderServiceImpl
里已注入，照抄 paySuccess 的推送写法即可）：

```java
// OrderServiceImpl.delivery(Long id)：status 3→4（外送=开始配送，自取=出餐待取餐）
// 这是"您的茶好了"的主推点位；confirm(2→3) 可选推「已接单，小月开始制茶啦」，
// complete(4→5) 可选推「感谢惠顾，期待再见」—— 同一段代码换 content/status 即可
Map<String, Object> msg = new HashMap<>();
msg.put("type", 3);
msg.put("orderId", order.getId());
msg.put("status", 4);
msg.put("content", "您的茶好了，请取餐");
webSocketServer.sendToAllClient(JSON.toJSONString(msg));
```

**为什么可以群发**：`sendToAllClient` 广播给所有在线连接（管理端监工屏 + C 端都在），
小程序端 `utils/pickup-ws.js` 已实现按 `orderId` 过滤只处理自己的订单 —— 服务端不用
做定向推送，前端自己认领（省掉服务端 sid 路由，答辩可以讲这个简化取舍）。

**小程序端已就绪**：`utils/pickup-ws.js` 在 `USE_MOCK=false` 时 `wx.connectSocket` 连
`ws://<BASE_URL 的 http 换 ws>/ws/user_{userId}`，断线自动重连（最多 3 次）；
`USE_MOCK=true` 时本地 12 秒后模拟一次「制作完成 → 待取餐」推送（对应 status 3→4）。
页面在 `onHide`/`onUnload` 必须调 `pickupWs.close()`。

---

## 6. F9 极简启动（纯前端，无后端工作）

登录 → 菜单首屏的骨架屏体验（骨架占位 + 渐进加载），完全由前端页面实现，
后端零改动 —— 联调时只需确认既有 `GET /user/shop/status`、`/user/category/list`、
`/user/drink/list` 正常返回即可。放进论文的「首屏加载体验优化」小节。

---

## 7. 联调步骤（三步走）

1. **前端切环境**：`utils/config.js` 的 `USE_MOCK` 改为 `false`（BASE_URL 指向本机 8080）；
2. **建库**：按 README 顺序执行 `schema.sql` → `data.sql` → `sql/v2_features.sql`；
3. **起后端逐个替换 TODO**：启动 `ShanyueApplication`，打开 Swagger
   （http://localhost:8080/swagger-ui/index.html，springdoc 1.7.0）逐个点
   6 个桩接口，返回与 mock 种子一致即桩阶段打通；然后按第 8 节顺序换真实现 ——
   **每换一个接口，前端对应页面即可真实联调**（例如换完 CheckinController，
   签到页的日历/连签/积分就来自 Redis BITMAP 了）。

替换通用套路（每个控制器头注释里都有具体版）：
entity（shanyue-pojo）→ Mapper 接口 + XML（shanyue-server）→ Service/ServiceImpl →
Controller 注入 Service、方法体换成一行调用 → 删除桩数据工具方法。

---

## 8. TODO 练习清单（建议顺序：先难后易，前两个是简历亮点）

1. **CheckinController（F5，BITMAP）** —— Redis 数据结构选型 + 位运算，1.5~2 天
   SETBIT 写 / BITFIELD 读 / BITCOUNT 数 / 连续签到从右往左数连续 1。
2. **CouponController（F4，SETNX + Lua）** —— 黑马秒杀全家桶，2.5~3 天
   一人一单 SETNX 占坑（失败记得 DEL 释放）→ Lua 三步合一防超卖 →
   user_coupon 唯一索引兜底 → submit 事务内用券（门槛校验/抵扣/置已使用）。
3. **AiOrderController（F7，直调 LLM）** —— 毕设主菜，1 天（一级方案）
   系统提示词约束 JSON 输出 → RestTemplate 调 /chat/completions → Jackson 解析
   choices[0].message.content → 解析失败降级关键词匹配版（桩逻辑搬进 Service）。
   二级（RAG）/三级（FastAPI+LangChain）选做，各 +1~2 天。
4. **ReviewController（F6，分页 + 唯一索引）** —— 1.5 天
   PageHelper 三件套 + 动态 SQL 过滤 drinkId + 一单一评双保险 + tags 存 JSON。
5. **MemberController（F2，冗余字段）** —— 1 天
   user.growth 方案 A 的三个计算点（paySuccess / checkin / review）+ 等级常量类。
6. **BannerController（F1，最简单热身）** —— 0.5 天
   建表 → Mapper → 管理端 CRUD 套路（也可拿来当第一个练手，顺序随意）。

---

## 9. 已知差异与人工复核点

- **饮品 id 号段**：见第 3 节末尾的 101 系 vs 1 系说明（评价墙按饮品筛选时留意）；
- **有状态桩**：签到/领券/评价的防重演示基于内存，重启重置，多实例部署不可用（本来就是
  演示用途，真实现走 Redis/DB）；
- **F8 的推送点位**：契约示例是 status=4（待取餐）时推「您的茶好了」；confirm/complete
  的附加推送文案需与前端 `pickup-ws.js` 的 `onPush` 处理逻辑对一遍再上线；
- **积分口径**：`points` 下单 1 元得 1 积分仅展示口径（mock 演示），真实入账只做签到/评价
  两处，别在 paySuccess 里重复加（论文里写清楚口径即可）。
