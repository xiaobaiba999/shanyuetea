# 山月茶咖 接口文档 v2.0

> **山月茶咖 SHANYUE TEA & COFFEE** —— 门店扫码点单 + 自取/外送双模式（茶饮 & 咖啡）系统 · slogan「山月入杯，茶咖相宜」
> 本文档是管理端（Vue3 + Element Plus）与 C 端微信小程序三方（前端开发、Mock 数据、后端实现）的**唯一接口事实来源**，v2.0 起按课程工程 sky-take-out 的模块划分与代码风格重构：后端技术栈 Spring Boot + MyBatis + MySQL + Redis + WebSocket，接口路径、DTO/VO 字段名与骨架工程 `shanyue-server-skeleton`（v2.0 增量）逐一对应。
> v2.0 定位：**主链路（模块 1~16）为联调契约**，扩展模块（17~21）保留概览、主链路联调完成后再做（详表见 dev-guide 扩展任务卡）。

---

## 全局约定

### 基本信息

| 项目 | 说明 |
| --- | --- |
| Base URL | `http://localhost:8080`（小程序直连；管理端浏览器经 nginx 80 端口转发，见下） |
| 管理端接口前缀 | `/admin`（除登录外均需携带 JWT） |
| C 端接口前缀 | `/user`（微信登录后携带 JWT，仅登录与店铺状态两个接口免登录） |
| 数据格式 | 请求/响应均为 `application/json`（图片上传除外，使用 `multipart/form-data`） |
| 价格单位 | **元**（number / BigDecimal），如 `15.9` 表示 15.90 元；v2.0 起由 v1.6 的「分」统一改为「元」，与后端 `BigDecimal` 字段直出一致 |
| 时间格式 | `"yyyy-MM-dd HH:mm:ss"` 字符串，如 `"2026-09-26 12:30:00"`；统计类报表的 `begin`/`end` 参数格式为 `yyyy-MM-dd` |

### 统一响应结构 Result

所有接口返回统一结构，`code=1` 成功，`code=0` 失败：

```json
{
  "code": 1,
  "msg": "success",
  "data": { }
}
```

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码：`1` 成功，`0` 失败 |
| msg | string | 是 | 响应信息：成功为 `success`，失败为具体错误描述 |
| data | object | 否 | 业务数据，无数据时为 `null` |

### 分页响应结构

分页查询类接口的 data 统一为 `{ total, records }`：

```json
{
  "total": 13,
  "records": [ ]
}
```

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页数据列表 |

### 认证机制

**管理端 `/admin/**`（浏览器经 nginx 访问）**

- 浏览器端所有请求先发往 nginx（80 端口），前端统一以 `/api/**` 为前缀；nginx 用**前缀替换**转发到后端：

```nginx
location /api/ {
    proxy_pass http://localhost:8080/admin/;
}
```

- 即浏览器请求 `GET /api/employee/page` → nginx 去掉 `/api/` 换上 `/admin/` → 后端实际收到 `GET /admin/employee/page`。**本文档所有管理端接口均直接写服务端真实路径（/admin/**）**，前端联调时把 `/admin` 换成 `/api` 即可。
- 除「1.1 员工登录」外，**所有 `/admin` 接口**必须在请求头携带登录时颁发的 JWT：

```
token: <登录接口返回的 token 字符串>
```

- 未携带 token 或 token 过期/非法时，接口返回 HTTP 状态码 `401`，前端应统一拦截并跳转登录页。

**C 端 `/user/**`（小程序直连）**

- 微信小程序**直连** `http://localhost:8080`（不经 nginx），路径即真实路径 `/user/**`。
- **v2.0 起 C 端从匿名改为微信登录（先登录后下单，与课程模式一致）**：除以下两个接口外，所有 `/user/**` 接口必须在请求头携带 C 端 JWT（请求头名同为 `token`）：
  - `POST /user/user/login` —— 登录本身（登录前没有 token）
  - `GET /user/shop/status` —— 店铺营业状态（未登录也要能判断能不能下单）
- 未登录访问受保护接口时返回 HTTP `401`（服务端拦截器 + `@ResponseStatus(UNAUTHORIZED)`），小程序端统一拦截并 `reLaunch` 到登录页。

### v2.0 变更记录

| 序号 | 变更 | 说明 |
| --- | --- | --- |
| ① | C 端从匿名改为微信登录 | `POST /user/user/login` 以 `wx.login` 的 code 换 openid，首登自动注册；先登录后下单，与课程模式一致（v1.6 的匿名自取模式废弃） |
| ② | 订单支持门店自取/外送双模式 | 新增 `deliveryMode`（`1` 门店自取 / `2` 外送）；外送固定配送费 5 元（`deliveryFee`），收货人/电话/地址以**快照**写入订单表 |
| ③ | 规格组 `specGroups` 结构化 | 替代 v1.6 的 `flavors` 口味字符串：规格组→选项两级结构，选项带 `priceDelta` 加价，前端规格弹窗与计价全部结构化 |
| ④ | 月售 `monthSales` 动态统计 | 商品列表/详情返回近 30 天销量；不建数据库字段，由服务端实时 SQL 统计（面试讲点，见 3.1） |
| ⑤ | 取餐号 `pickupNumber` | 自取单专属：Redis 按天自增（A01/A02…），下单成功页与取餐大字展示（外送单为 `null`） |

### 状态机与状态枚举

**订单状态机（6 状态）**：

| status | 含义 | 说明 |
| --- | --- | --- |
| 1 | 待付款 | 已提交未支付（骨架模拟支付秒付，提交后短暂停留或直接由支付回调推进；取消接口对 1/2 都放行） |
| 2 | 待接单 | 支付成功，等待商家接单 |
| 3 | 已接单（制作中） | 商家已接单，正在制作 |
| 4 | 派送中（外送）· 待取餐（自取） | 同一状态值按 `deliveryMode` 区分语义：外送单=派送中，自取单=待取餐（取餐号叫号） |
| 5 | 已完成 | 外送单送达 / 自取单取餐完成 |
| 6 | 已取消 | 任意环节可取消（用户取消 / 商家拒单 / 商家取消），取消原因见 `cancelReason` / `rejectionReason` |

**支付状态 payStatus**：`0` 未支付 / `1` 已支付 / `2` 退款。

**其他枚举**：

| 枚举 | 取值 |
| --- | --- |
| 店铺营业状态 | `1` 营业中 / `0` 已打烊（Redis 存储，key：`shanyue:shop:status`） |
| 商品/套餐状态 | `1` 起售 / `0` 停售 |
| 员工账号状态 | `1` 启用 / `0` 禁用 |
| 分类状态 | `1` 启用 / `0` 禁用 |
| 分类类型 type | `1` 商品分类 / `2` 套餐分类 |
| 下单模式 deliveryMode | `1` 门店自取 / `2` 外送 |
| 支付方式 payMethod | `1` 微信 / `2` 支付宝 |

### 模块目录

| 模块 | 内容 | 接口数 |
| --- | --- | --- |
| 1. 员工管理 | 登录、退出、员工 CRUD、启用禁用 | 7 |
| 2. 分类管理 | 分类 CRUD、按类型查询、启用禁用 | 6 |
| 3. 饮品管理 | 饮品 CRUD、结构化规格组、起售停售 | 7 |
| 4. 套餐管理 | 套餐 CRUD、套餐明细、起售停售 | 6 |
| 5. 公共 | 图片上传（本地磁盘） | 1 |
| 6. 订单管理 | 订单搜索、状态流转（接单/拒单/派送/完成/取消） | 8 |
| 7. 数据统计 | 营业额/用户/订单统计、销量 TOP10、Excel 导出 | 5 |
| 8. 工作台 | 今日运营数据、订单/商品/套餐总览 | 4 |
| 9. 店铺管理 | 营业状态开关（Redis） | 2 |
| 10. C端-微信登录【v2.0 新增】 | 微信登录、用户资料 | 2 |
| 11. C端-店铺 | 营业状态查询（免登录） | 1 |
| 12. C端-分类与商品 | 分类列表、在售饮品（含规格组/月售）、套餐 | 4 |
| 13. C端-购物车 | 加购/减购/列表/清空（规格快照合并） | 4 |
| 14. C端-地址簿【v2.0 新增】 | 地址 CRUD、默认地址（外送模式配套） | 7 |
| 15. C端-订单 | 下单、两步支付、历史订单、取消/再来一单/催单 | 8 |
| 16. WebSocket 推送 | 来单提醒（type=1）/ 催单提醒（type=2） | 1 |
| 17. C端-优惠券与会员【扩展阶段】 | 券广场、领券、我的券、下单可用券、会员概要 | 5 |
| 18. 管理端-优惠券模板【扩展阶段】 | 券模板分页查询、新增、修改、上下架、删除 | 5 |
| 19. C端-积分【扩展阶段】 | 积分概要、每日签到、积分明细、兑换货架、积分兑换 | 5 |
| 20. C端-评价【扩展阶段】 | 提交评价、查询订单评价、门店评价统计、我的评价 | 4 |
| 21. 管理端-评价【扩展阶段】 | 评价分页、评价统计、回复评价、隐藏/恢复 | 4 |

> 模块 1~9 为管理端主链路（46 个接口）；模块 10~15 为 C 端主链路（26 个接口）；模块 16 为 WebSocket 推送通道；模块 17~21 为扩展阶段概览（9 + 14 = 23 个接口，主链路联调完成后再做，详表见 dev-guide 扩展任务卡 T9/T10/T11）。合计 **96 个接口**（管理端 55 + C 端 40 + WebSocket 1），明细见文末「接口计数表」。

---

# 1. 员工管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。

## 1.1 员工登录

### 1.1.1 基本信息

- 请求路径：`/admin/employee/login`
- 请求方式：POST
- 接口描述：管理端登录。校验用户名与密码，成功后返回员工信息与 JWT 令牌，前端保存 token 并在后续所有管理端请求头中携带（`token: <jwt>`）。本接口是唯一**不需要** token 的管理端接口。默认演示账号：`admin / 123456`。用户名或密码错误、账号被禁用时返回 `code=0`。
- 业务规则：JWT claims 中只放 `empId`，服务端用拦截器统一校验（本接口在拦截器白名单）；密码错误/账号禁用返回 `code=0`，msg 区分「用户名或密码错误」与「账号被锁定」。★ 登录返回的 `userName` 字段大写 N（`EmployeeLoginVO.userName`，与课程工程拼写完全一致，前端取值不要写成 `username`）。

### 1.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| username | string | 是 | 登录账号 |
| password | string | 是 | 登录密码 |

请求参数样例：

```json
{
  "username": "admin",
  "password": "123456"
}
```

### 1.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 登录员工信息与令牌（EmployeeLoginVO） |
| \|- id | number | 是 | 员工ID |
| \|- userName | string | 是 | 登录账号（注意大写 N） |
| \|- name | string | 是 | 员工姓名 |
| \|- token | string | 是 | JWT 令牌，后续管理端请求放入请求头 `token` |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "userName": "admin",
    "name": "店长",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJlbXBsb3llZUlkIjoxfQ.kLi9KJAeSCnMCOxVjBCBPu2lKoHjcCztA57puE9Ov5Y"
  }
}
```

失败响应样例：

```json
{
  "code": 0,
  "msg": "用户名或密码错误",
  "data": null
}
```

## 1.2 退出登录

### 1.2.1 基本信息

- 请求路径：`/admin/employee/logout`
- 请求方式：POST
- 接口描述：管理端退出登录。JWT 为无状态令牌，服务端不做失效处理，前端负责清除本地存储的 token 并跳转登录页。权限：需管理端登录。

### 1.2.2 请求参数

无请求参数。

### 1.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.3 新增员工

### 1.3.1 基本信息

- 请求路径：`/admin/employee`
- 请求方式：POST
- 接口描述：新增门店员工。新增成功后默认状态为启用（status=1），默认密码为 `123456`。登录账号 username 全局唯一，重复时返回 `code=0`、msg「用户名已存在」。权限：需管理端登录。
- 业务规则：创建人/修改人（createUser/updateUser）由服务端从 ThreadLocal 取当前登录员工 id 自动填充，前端不传。★ 公共字段自动填充用自定义注解 + AOP 统一实现（`@AutoFill(OperationType.INSERT)`），业务代码里不散落 set 语句（面试讲点）。

### 1.3.2 请求参数

参数格式：application/json（EmployeeDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 是 | 员工姓名 |
| username | string | 是 | 登录账号，全局唯一 |
| phone | string | 否 | 手机号 |
| sex | string | 否 | 性别：`"1"` 男 / `"0"` 女 |
| idNumber | string | 否 | 身份证号 |

请求参数样例：

```json
{
  "name": "店员小月",
  "username": "xiaoyue",
  "phone": "13800000003",
  "sex": "0",
  "idNumber": "330106199803034567"
}
```

### 1.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.4 员工分页查询

### 1.4.1 基本信息

- 请求路径：`/admin/employee/page`
- 请求方式：GET
- 接口描述：分页查询员工列表，支持按姓名模糊搜索。权限：需管理端登录。
- 业务规则：分页由 PageHelper 插件实现（`PageHelper.startPage(page, pageSize)` 后紧跟首条查询），返回结构统一为 `{ total, records }`。

### 1.4.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 否 | 员工姓名，模糊匹配 |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```
GET /admin/employee/page?name=店&page=1&pageSize=10
```

### 1.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页员工列表（Employee） |
| \|- \|- id | number | 是 | 员工 id |
| \|- \|- username | string | 是 | 登录账号 |
| \|- \|- name | string | 是 | 员工姓名 |
| \|- \|- phone | string | 是 | 手机号 |
| \|- \|- sex | string | 是 | 性别：`"1"` 男 / `"0"` 女 |
| \|- \|- idNumber | string | 是 | 身份证号 |
| \|- \|- status | number | 是 | 账号状态：1 启用 / 0 禁用 |
| \|- \|- createTime | string | 是 | 创建时间 |
| \|- \|- updateTime | string | 是 | 更新时间 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 3,
    "records": [
      {
        "id": 1,
        "username": "admin",
        "name": "店长",
        "phone": "13800000001",
        "sex": "1",
        "idNumber": "330110199001011234",
        "status": 1,
        "createTime": "2026-09-20 09:00:00",
        "updateTime": "2026-09-25 18:20:31"
      },
      {
        "id": 3,
        "username": "xiaoyue",
        "name": "店员小月",
        "phone": "13800000003",
        "sex": "0",
        "idNumber": "330106199803034567",
        "status": 1,
        "createTime": "2026-09-24 10:12:40",
        "updateTime": "2026-09-24 10:12:40"
      }
    ]
  }
}
```

## 1.5 根据ID查询员工

### 1.5.1 基本信息

- 请求路径：`/admin/employee/{id}`
- 请求方式：GET
- 接口描述：按 id 查询员工详情，用于「编辑员工」弹窗回显。权限：需管理端登录。
- 业务规则：★ 回显时服务端把密码字段重置为固定值（`123456`，常量 PasswordConstant），既保证前端表单能提交编辑，又不泄露真实密码（课程同款做法，面试讲点：为什么不是返回 null——编辑页没有「密码」输入项，回显值不会被提交更新）。

### 1.5.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，员工 id |

### 1.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 员工详情（Employee，字段同 1.4.3 的 records 元素） |
| \|- id | number | 是 | 员工 id |
| \|- username | string | 是 | 登录账号 |
| \|- name | string | 是 | 员工姓名 |
| \|- phone | string | 是 | 手机号 |
| \|- sex | string | 是 | 性别 |
| \|- idNumber | string | 是 | 身份证号 |
| \|- status | number | 是 | 账号状态 |
| \|- createTime | string | 是 | 创建时间 |
| \|- updateTime | string | 是 | 更新时间 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 3,
    "username": "xiaoyue",
    "name": "店员小月",
    "phone": "13800000003",
    "sex": "0",
    "idNumber": "330106199803034567",
    "status": 1,
    "createTime": "2026-09-24 10:12:40",
    "updateTime": "2026-09-24 10:12:40"
  }
}
```

## 1.6 编辑员工

### 1.6.1 基本信息

- 请求路径：`/admin/employee`
- 请求方式：PUT
- 接口描述：修改员工信息（姓名、手机号等）。权限：需管理端登录。
- 业务规则：请求体与新增共用 EmployeeDTO，**编辑时必须带 `id`**。username 全局唯一，改成已有账号时数据库唯一索引兜底，返回 `code=0`、msg「用户名已存在」。

### 1.6.2 请求参数

参数格式：application/json（EmployeeDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 员工 id（编辑必传） |
| name | string | 是 | 员工姓名 |
| username | string | 是 | 登录账号，全局唯一 |
| phone | string | 否 | 手机号 |
| sex | string | 否 | 性别：`"1"` 男 / `"0"` 女 |
| idNumber | string | 否 | 身份证号 |

请求参数样例：

```json
{
  "id": 3,
  "name": "店员小月",
  "username": "xiaoyue",
  "phone": "13800000004",
  "sex": "0",
  "idNumber": "330106199803034567"
}
```

### 1.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.7 启用/禁用员工

### 1.7.1 基本信息

- 请求路径：`/admin/employee/status/{status}`
- 请求方式：POST
- 接口描述：启用或禁用员工账号。被禁用的账号无法登录（登录时校验 status）。权限：需管理端登录。
- 业务规则：`status` 在路径上，`id` 用 query 传参（`POST /admin/employee/status/0?id=3`）。★ 禁用/启用与 1.3/1.6 一样走动态 SQL 更新，禁止把 status 写死在前端代码里拼 SQL 字符串（面试讲点：为什么 status 用路径参数而不是 body——操作语义明确、URL 可读、与课程工程保持一致）。

### 1.7.2 请求参数

参数格式：路径参数 + query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | number | 是 | 路径参数，目标状态：`1` 启用 / `0` 禁用 |
| id | number | 是 | query 参数，员工 id |

### 1.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 2. 分类管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。

## 2.1 新增分类

### 2.1.1 基本信息

- 请求路径：`/admin/category`
- 请求方式：POST
- 接口描述：新增商品分类或套餐分类。权限：需管理端登录。
- 业务规则：`type`：`1` 商品分类 / `2` 套餐分类；新增后默认启用（status=1）；分类名全局唯一，重复时返回 `code=0`、msg「分类名称已存在」。

### 2.1.2 请求参数

参数格式：application/json（Category）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| type | number | 是 | 分类类型：`1` 商品分类 / `2` 套餐分类 |
| name | string | 是 | 分类名称，唯一 |
| sort | number | 是 | 排序值，越小越靠前 |

请求参数样例：

```json
{
  "type": 1,
  "name": "山月茶饮",
  "sort": 2
}
```

### 2.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 2.2 分类分页查询

### 2.2.1 基本信息

- 请求路径：`/admin/category/page`
- 请求方式：GET
- 接口描述：分页查询分类，支持按名称模糊搜索与类型筛选。权限：需管理端登录。

### 2.2.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 否 | 分类名称，模糊匹配 |
| type | number | 否 | 分类类型：1 商品分类 / 2 套餐分类，不传查全部 |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```
GET /admin/category/page?page=1&pageSize=10&type=1
```

### 2.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页分类列表（Category） |
| \|- \|- id | number | 是 | 分类 id |
| \|- \|- type | number | 是 | 分类类型：1 商品分类 / 2 套餐分类 |
| \|- \|- name | string | 是 | 分类名称 |
| \|- \|- sort | number | 是 | 排序值 |
| \|- \|- status | number | 是 | 分类状态：1 启用 / 0 禁用 |
| \|- \|- createTime | string | 是 | 创建时间 |
| \|- \|- updateTime | string | 是 | 更新时间 |
| \|- \|- createUser | number | 是 | 创建人 id |
| \|- \|- updateUser | number | 是 | 修改人 id |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 4,
    "records": [
      {
        "id": 1,
        "type": 1,
        "name": "经典咖啡",
        "sort": 1,
        "status": 1,
        "createTime": "2026-09-20 09:00:00",
        "updateTime": "2026-09-20 09:00:00",
        "createUser": 1,
        "updateUser": 1
      },
      {
        "id": 4,
        "type": 2,
        "name": "双人轻食套餐",
        "sort": 1,
        "status": 1,
        "createTime": "2026-09-22 14:30:00",
        "updateTime": "2026-09-22 14:30:00",
        "createUser": 1,
        "updateUser": 1
      }
    ]
  }
}
```

## 2.3 按类型查询分类列表

### 2.3.1 基本信息

- 请求路径：`/admin/category/list`
- 请求方式：GET
- 接口描述：按类型查询全量分类（不分页），供饮品/套餐新增编辑弹窗的「分类下拉框」使用；不传 `type` 查全部类型。
- 业务规则：★ v2.0 对齐课程工程新增的接口（v1.6 无）：管理端下拉框需要全量启用分类，分页接口满足不了，必须提供 `list` 版本；C 端同名接口见 12.1（C 端只会返回启用状态的分类）。

### 2.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| type | number | 否 | 分类类型：1 商品分类 / 2 套餐分类，不传查全部 |

请求参数样例：

```
GET /admin/category/list?type=1
```

### 2.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 分类列表（Category，字段同 2.2.3 的 records 元素） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "type": 1,
      "name": "经典咖啡",
      "sort": 1,
      "status": 1,
      "createTime": "2026-09-20 09:00:00",
      "updateTime": "2026-09-20 09:00:00",
      "createUser": 1,
      "updateUser": 1
    },
    {
      "id": 2,
      "type": 1,
      "name": "山月茶饮",
      "sort": 2,
      "status": 1,
      "createTime": "2026-09-20 09:00:00",
      "updateTime": "2026-09-20 09:00:00",
      "createUser": 1,
      "updateUser": 1
    }
  ]
}
```

## 2.4 修改分类

### 2.4.1 基本信息

- 请求路径：`/admin/category`
- 请求方式：PUT
- 接口描述：修改分类信息（名称、排序、类型）。权限：需管理端登录。
- 业务规则：编辑时必须带 `id`；更新走动态 SQL（只更新非空字段）。

### 2.4.2 请求参数

参数格式：application/json（Category）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 分类 id |
| type | number | 否 | 分类类型：1 商品分类 / 2 套餐分类 |
| name | string | 否 | 分类名称 |
| sort | number | 否 | 排序值 |

请求参数样例：

```json
{
  "id": 2,
  "name": "山月鲜奶茶饮",
  "sort": 2
}
```

### 2.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 2.5 启用/禁用分类

### 2.5.1 基本信息

- 请求路径：`/admin/category/status/{status}`
- 请求方式：POST
- 接口描述：启用或禁用分类。禁用后 C 端分类列表不再展示该分类。权限：需管理端登录。

### 2.5.2 请求参数

参数格式：路径参数 + query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | number | 是 | 路径参数，目标状态：`1` 启用 / `0` 禁用 |
| id | number | 是 | query 参数，分类 id |

### 2.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 2.6 删除分类

### 2.6.1 基本信息

- 请求路径：`/admin/category`
- 请求方式：DELETE
- 接口描述：按 id 删除分类。权限：需管理端登录。
- 业务规则：★ 分类下存在饮品/套餐（或关联了套餐明细）时不允许删除，返回 `code=0`、msg「当前分类下有商品，不能删除」——这是「先查后插/先查后删」一致性校验的典型场景，同时建议数据库对关联字段建外键/唯一索引兜底（面试讲点）。

### 2.6.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | query 参数，分类 id |

请求参数样例：

```
DELETE /admin/category?id=4
```

### 2.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 3. 饮品管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> 本模块是 v2.0 变更最集中的模块：**规格组 `specGroups` 结构化**（替代 v1.6 的 flavors 口味字符串）+ **月售 `monthSales` 动态统计**。

## 3.1 饮品分页查询

### 3.1.1 基本信息

- 请求路径：`/admin/drink/page`
- 请求方式：GET
- 接口描述：分页查询饮品列表，支持名称模糊搜索、分类与状态筛选。返回 DrinkVO 列表（含分类名称、规格组摘要与月售）。权限：需管理端登录。
- 业务规则：★ `monthSales` 为近 30 天月售，**不建数据库字段**，由 Service 实时 SQL 统计（`order_detail` 关联 `orders(status=5)` 近 30 天 `SUM(number) GROUP BY drink_id`）：好处是永远不会统计错、不需要维护冗余字段；代价是每次查询多一次聚合 SQL，高并发场景可改为「订单完成时 +1 的冗余计数 / 定时任务」两种方案（面试讲点：两种方案的取舍）。

### 3.1.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |
| name | string | 否 | 饮品名称，模糊匹配 |
| categoryId | number | 否 | 分类 id |
| status | number | 否 | 状态：`1` 起售 / `0` 停售，不传查全部 |

请求参数样例：

```
GET /admin/drink/page?page=1&pageSize=10&name=拿铁
```

### 3.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页饮品列表（DrinkVO） |
| \|- \|- id | number | 是 | 饮品 id |
| \|- \|- name | string | 是 | 饮品名称 |
| \|- \|- categoryId | number | 是 | 分类 id |
| \|- \|- price | number | 是 | 基础价格（元） |
| \|- \|- image | string | 是 | 图片 URL |
| \|- \|- description | string | 是 | 描述信息 |
| \|- \|- status | number | 是 | 状态：1 起售 / 0 停售 |
| \|- \|- updateTime | string | 是 | 更新时间 |
| \|- \|- categoryName | string | 是 | 分类名称（联表带出） |
| \|- \|- flavors | object[] | 是 | 口味（课程同款字段保留，恒为 `[]`，仅供对照） |
| \|- \|- specGroups | object[] | 是 | ★ 规格组列表（山月 v2.0，结构见下） |
| \|- \|- \|- name | string | 是 | 规格组名称，如「杯型」「温度」「糖度」 |
| \|- \|- \|- sort | number | 是 | 组内排序 |
| \|- \|- \|- options | object[] | 是 | 该组下选项列表 |
| \|- \|- \|- \|- name | string | 是 | 选项名称，如「大杯」「少糖」 |
| \|- \|- \|- \|- priceDelta | number | 是 | 加价金额（元），0 表示不加价 |
| \|- \|- \|- \|- sort | number | 是 | 选项排序 |
| \|- \|- monthSales | number | 是 | ★ 近 30 天月售（实时统计，非数据库字段） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 6,
    "records": [
      {
        "id": 5,
        "name": "山月拿铁",
        "categoryId": 1,
        "price": 15.9,
        "image": "http://localhost:8080/upload/latte-5.png",
        "description": "浓缩与鲜奶的经典配比，山月招牌",
        "status": 1,
        "updateTime": "2026-09-25 10:00:00",
        "categoryName": "经典咖啡",
        "flavors": [],
        "specGroups": [
          {
            "name": "杯型",
            "sort": 1,
            "options": [
              { "name": "中杯", "priceDelta": 0, "sort": 1 },
              { "name": "大杯", "priceDelta": 3, "sort": 2 }
            ]
          },
          {
            "name": "温度",
            "sort": 2,
            "options": [
              { "name": "热", "priceDelta": 0, "sort": 1 },
              { "name": "冰", "priceDelta": 0, "sort": 2 }
            ]
          }
        ],
        "monthSales": 356
      },
      {
        "id": 8,
        "name": "桂花冰酿",
        "categoryId": 2,
        "price": 18,
        "image": "http://localhost:8080/upload/osmanthus-oolong.png",
        "description": "桂花乌龙底，冷萃茶香与酒酿的微甜",
        "status": 1,
        "updateTime": "2026-09-24 16:42:10",
        "categoryName": "山月茶饮",
        "flavors": [],
        "specGroups": [
          {
            "name": "杯型",
            "sort": 1,
            "options": [
              { "name": "标准杯", "priceDelta": 0, "sort": 1 },
              { "name": "大杯", "priceDelta": 3, "sort": 2 }
            ]
          }
        ],
        "monthSales": 208
      }
    ]
  }
}
```

## 3.2 新增饮品

### 3.2.1 基本信息

- 请求路径：`/admin/drink`
- 请求方式：POST
- 接口描述：新增饮品，含基础信息与结构化规格组（specGroups）。新增成功后即可在 C 端菜单展示（status=1 起售）。权限：需管理端登录。
- 业务规则：①服务端校验：分类必须存在、价格必须大于 0、每个规格组至少 1 个选项（校验不过返回 `code=0`）；②保存顺序：先插 `drink` 主表，再按 `drinkId` 插入 `spec_group` / `spec_option` 两张子表，整体在 `@Transactional` 中；③★ 与课程差异：课程口味是 `drink_flavor` 单表字符串，山月是「组→选项」两级结构化（`spec_group` + `spec_option`），`priceDelta` 参与计价（最终售价 = `drink.price` + Σ 所选项加价，金额一律 BigDecimal，面试讲点）。

### 3.2.2 请求参数

参数格式：application/json（DrinkDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 是 | 饮品名称 |
| categoryId | number | 是 | 分类 id |
| price | number | 是 | 基础价格（元） |
| image | string | 否 | 图片 URL（由 5.1 上传接口返回） |
| description | string | 否 | 描述信息 |
| status | number | 否 | 状态：1 起售 / 0 停售，默认 1 |
| specGroups | object[] | 否 | 规格组列表（可空 = 无规格商品） |
| \|- name | string | 是 | 规格组名称 |
| \|- sort | number | 是 | 排序 |
| \|- options | object[] | 是 | 选项列表 |
| \|- \|- name | string | 是 | 选项名称 |
| \|- \|- priceDelta | number | 是 | 加价金额（元），0 表示不加价 |
| \|- \|- sort | number | 是 | 排序 |
| flavors | object[] | 否 | 课程同款字段保留，山月业务不使用，恒传 `[]` |

请求参数样例：

```json
{
  "name": "厚椰生椰拿铁",
  "categoryId": 1,
  "price": 16.9,
  "image": "http://localhost:8080/upload/coconut-latte-9.png",
  "description": "厚椰乳与浓缩的双重浓郁",
  "status": 1,
  "specGroups": [
    {
      "name": "杯型",
      "sort": 1,
      "options": [
        { "name": "中杯", "priceDelta": 0, "sort": 1 },
        { "name": "大杯", "priceDelta": 3, "sort": 2 }
      ]
    },
    {
      "name": "糖度",
      "sort": 2,
      "options": [
        { "name": "标准糖", "priceDelta": 0, "sort": 1 },
        { "name": "少糖", "priceDelta": 0, "sort": 2 },
        { "name": "无糖", "priceDelta": 0, "sort": 3 }
      ]
    }
  ],
  "flavors": []
}
```

### 3.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.3 根据ID查询饮品（含规格组）

### 3.3.1 基本信息

- 请求路径：`/admin/drink/{id}`
- 请求方式：GET
- 接口描述：按 id 查询饮品完整详情（基础信息 + 规格组 + 月售），用于「修改饮品」弹窗回显。权限：需管理端登录。

### 3.3.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，饮品 id |

### 3.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 饮品详情（DrinkVO，字段同 3.1.3 的 records 元素） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 5,
    "name": "山月拿铁",
    "categoryId": 1,
    "price": 15.9,
    "image": "http://localhost:8080/upload/latte-5.png",
    "description": "浓缩与鲜奶的经典配比，山月招牌",
    "status": 1,
    "updateTime": "2026-09-25 10:00:00",
    "categoryName": "经典咖啡",
    "flavors": [],
    "specGroups": [
      {
        "id": 1,
        "drinkId": 5,
        "name": "杯型",
        "sort": 1,
        "options": [
          { "id": 1, "name": "中杯", "priceDelta": 0, "sort": 1 },
          { "id": 2, "name": "大杯", "priceDelta": 3, "sort": 2 }
        ]
      },
      {
        "id": 2,
        "drinkId": 5,
        "name": "温度",
        "sort": 2,
        "options": [
          { "id": 3, "name": "热", "priceDelta": 0, "sort": 1 },
          { "id": 4, "name": "冰", "priceDelta": 0, "sort": 2 }
        ]
      }
    ],
    "monthSales": 356
  }
}
```

## 3.4 修改饮品

### 3.4.1 基本信息

- 请求路径：`/admin/drink`
- 请求方式：PUT
- 接口描述：修改饮品（含整体替换规格组）。权限：需管理端登录。
- 业务规则：①编辑时必须带 `id`；②★ 规格组保存策略为「**先删后插、整体覆盖**」：先删该饮品的全部规格组/选项，再把提交内容整体插入——前端无需做复杂的 diff 比较，逻辑最简单、不会出现孤儿数据（面试讲点：为什么不用逐条 diff 更新）；③★ 起售中的饮品禁止修改规格组（需先停售，避免 C 端已加购规格失效），返回 `code=0`、msg「请先停售再修改规格」；④修改成功后清除该分类的 C 端商品缓存（Redis `drink_{categoryId}`）。

### 3.4.2 请求参数

参数格式：application/json（DrinkDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 饮品 id（编辑必传） |
| name | string | 是 | 饮品名称 |
| categoryId | number | 是 | 分类 id |
| price | number | 是 | 基础价格（元） |
| image | string | 否 | 图片 URL |
| description | string | 否 | 描述信息 |
| status | number | 否 | 状态：1 起售 / 0 停售 |
| specGroups | object[] | 否 | 规格组列表（结构同 3.2.2，整体覆盖） |
| flavors | object[] | 否 | 课程同款字段保留，恒传 `[]` |

请求参数样例：

```json
{
  "id": 5,
  "name": "山月拿铁",
  "categoryId": 1,
  "price": 15.9,
  "image": "http://localhost:8080/upload/latte-5.png",
  "description": "浓缩与鲜奶的经典配比，山月招牌（升级燕麦奶可选）",
  "status": 1,
  "specGroups": [
    {
      "name": "杯型",
      "sort": 1,
      "options": [
        { "name": "中杯", "priceDelta": 0, "sort": 1 },
        { "name": "大杯", "priceDelta": 3, "sort": 2 }
      ]
    },
    {
      "name": "温度",
      "sort": 2,
      "options": [
        { "name": "热", "priceDelta": 0, "sort": 1 },
        { "name": "冰", "priceDelta": 0, "sort": 2 }
      ]
    },
    {
      "name": "奶底",
      "sort": 3,
      "options": [
        { "name": "鲜奶", "priceDelta": 0, "sort": 1 },
        { "name": "燕麦奶", "priceDelta": 2, "sort": 2 }
      ]
    }
  ],
  "flavors": []
}
```

### 3.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.5 批量删除饮品

### 3.5.1 基本信息

- 请求路径：`/admin/drink`
- 请求方式：DELETE
- 接口描述：按 id 批量删除饮品（复选框多选删除）。权限：需管理端登录。
- 业务规则：★ ids 用 query 数组传参（`?ids=1,2,3` 或 `?ids=1&ids=2`，`@RequestParam List<Long>`）；起售中的饮品不能删除，返回 `code=0`、msg「请先停售再删除」；删除时同步删除规格组/选项子表数据，并清理 C 端商品缓存（`drink_*`）。

### 3.5.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| ids | number[] | 是 | query 参数，饮品 id 列表，逗号分隔 |

请求参数样例：

```
DELETE /admin/drink?ids=9,10
```

### 3.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.6 按分类查询饮品列表

### 3.6.1 基本信息

- 请求路径：`/admin/drink/list`
- 请求方式：GET
- 接口描述：按分类 id 查询该分类下饮品简表（不分页，返回 Drink 实体列表），供套餐管理「添加菜品」弹窗选择。权限：需管理端登录。
- 业务规则：★ v2.0 对齐课程工程新增的接口：套餐由多个饮品组成，配置套餐时需要按分类拉全量饮品；与 3.1 分页接口的区别是返回实体简表（无规格组/月售），性能更好。

### 3.6.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| categoryId | number | 是 | 分类 id |

请求参数样例：

```
GET /admin/drink/list?categoryId=1
```

### 3.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 饮品列表（Drink 实体：id/name/categoryId/price/image/description/status/createTime/updateTime 等） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 5,
      "name": "山月拿铁",
      "categoryId": 1,
      "price": 15.9,
      "image": "http://localhost:8080/upload/latte-5.png",
      "description": "浓缩与鲜奶的经典配比，山月招牌",
      "status": 1,
      "createTime": "2026-09-20 09:00:00",
      "updateTime": "2026-09-25 10:00:00"
    },
    {
      "id": 6,
      "name": "椰香澳白",
      "categoryId": 1,
      "price": 16,
      "image": "http://localhost:8080/upload/flat-white.png",
      "description": "双份浓缩与绵密奶泡",
      "status": 1,
      "createTime": "2026-09-20 09:00:00",
      "updateTime": "2026-09-20 09:00:00"
    }
  ]
}
```

## 3.7 起售/停售饮品

### 3.7.1 基本信息

- 请求路径：`/admin/drink/status/{status}`
- 请求方式：POST
- 接口描述：起售或停售饮品。停售后 C 端菜单不再展示。权限：需管理端登录。

### 3.7.2 请求参数

参数格式：路径参数 + query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | number | 是 | 路径参数，目标状态：`1` 起售 / `0` 停售 |
| id | number | 是 | query 参数，饮品 id |

### 3.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 4. 套餐管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> 套餐（Setmeal）= 多个饮品的组合销售单元；v2.0 新增模块，表结构与接口风格与课程工程一致（`setmeal` + `setmeal_drink` 两表）。

## 4.1 新增套餐

### 4.1.1 基本信息

- 请求路径：`/admin/setmeal`
- 请求方式：POST
- 接口描述：新增套餐，含基础信息与套餐内含饮品明细（setmealDrinkes）。权限：需管理端登录。
- 业务规则：①保存顺序：先插 `setmeal` 主表，再按 `setmealId` 批量插 `setmeal_drink` 明细（明细冗余菜品名称/原价，下单展示免联表）；②`categoryId` 必须为套餐分类（type=2）；③套餐价格建议低于明细原价合计（营销属性，服务端不强校验）。

### 4.1.2 请求参数

参数格式：application/json（SetmealVO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 是 | 套餐名称 |
| categoryId | number | 是 | 套餐分类 id（type=2） |
| price | number | 是 | 套餐价格（元） |
| image | string | 否 | 图片 URL |
| description | string | 否 | 描述信息 |
| status | number | 否 | 状态：1 起售 / 0 停售，默认 0（新建建议先停售） |
| setmealDrinkes | object[] | 是 | 套餐内含饮品明细 |
| \|- drinkId | number | 是 | 饮品 id |
| \|- name | string | 是 | 饮品名称（冗余） |
| \|- price | number | 是 | 饮品原价（冗余快照） |
| \|- copies | number | 是 | 份数 |

请求参数样例：

```json
{
  "name": "午后双人轻食套餐",
  "categoryId": 4,
  "price": 39.9,
  "image": "http://localhost:8080/upload/setmeal-duo.png",
  "description": "拿铁 + 可颂 + 拿铁 + 巴斯克，两人分享刚好",
  "status": 0,
  "setmealDrinkes": [
    { "drinkId": 5, "name": "山月拿铁", "price": 15.9, "copies": 2 },
    { "drinkId": 11, "name": "黄油可颂", "price": 12, "copies": 1 },
    { "drinkId": 12, "name": "巴斯克芝士蛋糕", "price": 22, "copies": 1 }
  ]
}
```

### 4.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 4.2 套餐分页查询

### 4.2.1 基本信息

- 请求路径：`/admin/setmeal/page`
- 请求方式：GET
- 接口描述：分页查询套餐列表，支持名称模糊搜索、分类与状态筛选。权限：需管理端登录。

### 4.2.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |
| name | string | 否 | 套餐名称，模糊匹配 |
| categoryId | number | 否 | 套餐分类 id |
| status | number | 否 | 状态：1 起售 / 0 停售 |

请求参数样例：

```
GET /admin/setmeal/page?page=1&pageSize=10
```

### 4.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页套餐列表（SetmealVO） |
| \|- \|- id | number | 是 | 套餐 id |
| \|- \|- name | string | 是 | 套餐名称 |
| \|- \|- categoryId | number | 是 | 套餐分类 id |
| \|- \|- price | number | 是 | 套餐价格（元） |
| \|- \|- status | number | 是 | 状态：1 起售 / 0 停售 |
| \|- \|- description | string | 是 | 描述信息 |
| \|- \|- image | string | 是 | 图片 URL |
| \|- \|- updateTime | string | 是 | 更新时间 |
| \|- \|- categoryName | string | 是 | 分类名称（联表带出） |
| \|- \|- setmealDrinkes | object[] | 是 | 套餐明细（列表页可为空数组，详情接口返回完整数据） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 2,
    "records": [
      {
        "id": 2,
        "name": "午后双人轻食套餐",
        "categoryId": 4,
        "price": 39.9,
        "status": 1,
        "description": "拿铁 + 可颂 + 拿铁 + 巴斯克，两人分享刚好",
        "image": "http://localhost:8080/upload/setmeal-duo.png",
        "updateTime": "2026-09-25 11:00:00",
        "categoryName": "双人轻食套餐",
        "setmealDrinkes": []
      }
    ]
  }
}
```

## 4.3 批量删除套餐

### 4.3.1 基本信息

- 请求路径：`/admin/setmeal`
- 请求方式：DELETE
- 接口描述：按 id 批量删除套餐。权限：需管理端登录。
- 业务规则：ids 用 query 数组传参；起售中的套餐不能删除（先停售），删除时同步删除 `setmeal_drink` 明细。

### 4.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| ids | number[] | 是 | query 参数，套餐 id 列表，逗号分隔 |

请求参数样例：

```
DELETE /admin/setmeal?ids=3
```

### 4.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 4.4 根据ID查询套餐

### 4.4.1 基本信息

- 请求路径：`/admin/setmeal/{id}`
- 请求方式：GET
- 接口描述：按 id 查询套餐完整详情（含套餐明细 setmealDrinkes），用于「修改套餐」弹窗回显。权限：需管理端登录。

### 4.4.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，套餐 id |

### 4.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 套餐详情（SetmealVO） |
| \|- id | number | 是 | 套餐 id |
| \|- name | string | 是 | 套餐名称 |
| \|- categoryId | number | 是 | 套餐分类 id |
| \|- price | number | 是 | 套餐价格（元） |
| \|- status | number | 是 | 状态：1 起售 / 0 停售 |
| \|- description | string | 是 | 描述信息 |
| \|- image | string | 是 | 图片 URL |
| \|- updateTime | string | 是 | 更新时间 |
| \|- categoryName | string | 是 | 分类名称 |
| \|- setmealDrinkes | object[] | 是 | 套餐明细 |
| \|- \|- drinkId | number | 是 | 饮品 id |
| \|- \|- name | string | 是 | 饮品名称（冗余） |
| \|- \|- price | number | 是 | 饮品原价（冗余快照） |
| \|- \|- copies | number | 是 | 份数 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 2,
    "name": "午后双人轻食套餐",
    "categoryId": 4,
    "price": 39.9,
    "status": 1,
    "description": "拿铁 + 可颂 + 拿铁 + 巴斯克，两人分享刚好",
    "image": "http://localhost:8080/upload/setmeal-duo.png",
    "updateTime": "2026-09-25 11:00:00",
    "categoryName": "双人轻食套餐",
    "setmealDrinkes": [
      { "drinkId": 5, "name": "山月拿铁", "price": 15.9, "copies": 2 },
      { "drinkId": 11, "name": "黄油可颂", "price": 12, "copies": 1 },
      { "drinkId": 12, "name": "巴斯克芝士蛋糕", "price": 22, "copies": 1 }
    ]
  }
}
```

## 4.5 修改套餐

### 4.5.1 基本信息

- 请求路径：`/admin/setmeal`
- 请求方式：PUT
- 接口描述：修改套餐（含整体替换套餐明细）。权限：需管理端登录。
- 业务规则：编辑时必须带 `id`；套餐明细同样「先删后插、整体覆盖」（先删 `setmeal_drink` 再整体插入，与饮品规格组同策略）。

### 4.5.2 请求参数

参数格式：application/json（SetmealDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 套餐 id（编辑必传） |
| name | string | 是 | 套餐名称 |
| categoryId | number | 是 | 套餐分类 id |
| price | number | 是 | 套餐价格（元） |
| image | string | 否 | 图片 URL |
| description | string | 否 | 描述信息 |
| status | number | 否 | 状态：1 起售 / 0 停售 |
| setmealDrinkes | object[] | 是 | 套餐明细（结构同 4.1.2，整体覆盖） |

请求参数样例：

```json
{
  "id": 2,
  "name": "午后双人轻食套餐",
  "categoryId": 4,
  "price": 39.9,
  "image": "http://localhost:8080/upload/setmeal-duo.png",
  "description": "拿铁 + 可颂 + 巴斯克，两人分享刚好",
  "status": 1,
  "setmealDrinkes": [
    { "drinkId": 5, "name": "山月拿铁", "price": 15.9, "copies": 2 },
    { "drinkId": 11, "name": "黄油可颂", "price": 12, "copies": 1 },
    { "drinkId": 12, "name": "巴斯克芝士蛋糕", "price": 22, "copies": 1 }
  ]
}
```

### 4.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 4.6 起售/停售套餐

### 4.6.1 基本信息

- 请求路径：`/admin/setmeal/status/{status}`
- 请求方式：POST
- 接口描述：起售或停售套餐。权限：需管理端登录。
- 业务规则：★ 起售时服务端校验套餐内含饮品必须全部为起售状态，存在停售饮品则起售失败，返回 `code=0`、msg「套餐内包含停售饮品，无法起售」（课程同款一致性校验；停售饮品时也应级联停售关联套餐，双向保证一致）。

### 4.6.2 请求参数

参数格式：路径参数 + query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | number | 是 | 路径参数，目标状态：`1` 起售 / `0` 停售 |
| id | number | 是 | query 参数，套餐 id |

### 4.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 5. 公共

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。

## 5.1 图片上传

### 5.1.1 基本信息

- 请求路径：`/admin/common/upload`
- 请求方式：POST
- 接口描述：上传图片（饮品图、套餐图等），返回可访问的图片 URL 字符串。请求使用 `multipart/form-data`，文件字段名 `file`。权限：需管理端登录。
- 业务规则：★ 山月用**本地磁盘存储**替代课程工程的阿里云 OSS（教学环境零成本跑通）：①文件重命名为 `UUID + 原始后缀` 防覆盖与路径穿越；②保存到 `shanyue.localStorage.path` 配置目录；③通过 `WebMvcConfiguration` 的 `addResourceHandler("/upload/**")` 把 `/upload/**` 静态映射到磁盘目录，返回 `http://localhost:8080/upload/xxx.png`；④浏览器访问图片时由 nginx 的 `location /upload/` 转发到后端（面试讲点：为什么文件名用 UUID、为什么要做静态资源映射）。文件类型/大小校验：仅允许图片格式，单文件 ≤ 10MB（Spring multipart 配置）。

### 5.1.2 请求参数

参数格式：multipart/form-data

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| file | file | 是 | 图片文件（jpg/png/webp，≤ 10MB） |

### 5.1.3 响应数据

参数格式：application/json（data 为图片 URL 字符串）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败（上传 IO 失败返回 0） |
| msg | string | 是 | 响应信息 |
| data | string | 是 | 图片访问 URL，如 `http://localhost:8080/upload/uuid.png` |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": "http://localhost:8080/upload/8f2c1a9e4b7d4f0a9c3e5d6b1a2f4e5c.png"
}
```

---

# 6. 订单管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> 订单状态机 6 态：`1` 待付款 → `2` 待接单 → `3` 已接单（制作中）→ `4` 派送中（外送）·待取餐（自取）→ `5` 已完成；任意环节可 → `6` 已取消。★ v2.0 管理端订单接口同时服务双模式（`deliveryMode` 1 自取 / 2 外送）：接单/拒单/完成通用，**派送仅对外送单有意义**，自取单由「完成」直接推进（取餐号叫号）。

## 6.1 订单条件搜索（分页）

### 6.1.1 基本信息

- 请求路径：`/admin/order/conditionSearch`
- 请求方式：GET
- 接口描述：管理端订单分页搜索，支持订单号、手机号、状态、下单时间段与下单模式组合筛选。返回 OrderVO 分页列表。权限：需管理端登录。
- 业务规则：★ `deliveryMode` 筛选为山月 v2.0 新增（课程单模式无需此参数）：不传查全部，`1` 只看自取单，`2` 只看外送单——商家午高峰常用「待接单 + 自取」组合快速处理门店单。时间参数格式 `yyyy-MM-dd HH:mm:ss`（`beginTime`/`endTime`）。

### 6.1.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |
| number | string | 否 | 订单号，精确匹配 |
| phone | string | 否 | 收货/联系手机号 |
| status | number | 否 | 订单状态：1 待付款 / 2 待接单 / 3 已接单 / 4 派送中·待取餐 / 5 已完成 / 6 已取消 |
| beginTime | string | 否 | 下单时间起，格式 `yyyy-MM-dd HH:mm:ss` |
| endTime | string | 否 | 下单时间止，格式 `yyyy-MM-dd HH:mm:ss` |
| deliveryMode | number | 否 | ★ 下单模式：`1` 门店自取 / `2` 外送，不传查全部（v2.0 新增） |

请求参数样例：

```
GET /admin/order/conditionSearch?page=1&pageSize=10&status=2&deliveryMode=2&beginTime=2026-09-26 00:00:00&endTime=2026-09-26 23:59:59
```

### 6.1.3 响应数据

参数格式：application/json

OrderVO = Orders 全字段 + `orderDrinkes` + `orderDetailList`，字段总表如下（后续订单类接口复用，不再重复列表）：

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 当前页订单列表（OrderVO = Orders 全字段 + 明细） |
| \|- \|- id | number | 是 | 订单 id |
| \|- \|- number | string | 是 | 订单号 |
| \|- \|- status | number | 是 | 订单状态：1 待付款 / 2 待接单 / 3 已接单 / 4 派送中·待取餐 / 5 已完成 / 6 已取消 |
| \|- \|- userId | number | 是 | 下单用户 id |
| \|- \|- addressBookId | number | 是 | 地址簿 id（自取单可为空） |
| \|- \|- orderTime | string | 是 | 下单时间 |
| \|- \|- checkoutTime | string | 是 | 结账时间（支付成功时写入） |
| \|- \|- payMethod | number | 是 | 支付方式：1 微信 / 2 支付宝 |
| \|- \|- payStatus | number | 是 | 支付状态：0 未支付 / 1 已支付 / 2 退款 |
| \|- \|- amount | number | 是 | 实收金额（元，含外送费） |
| \|- \|- remark | string | 是 | 备注 |
| \|- \|- userName | string | 是 | 下单用户名 |
| \|- \|- phone | string | 是 | 手机号（收货/联系手机快照） |
| \|- \|- address | string | 是 | 收货地址快照（自取单为空或门店地址） |
| \|- \|- consignee | string | 是 | 收货人快照 |
| \|- \|- cancelReason | string | 是 | 订单取消原因 |
| \|- \|- rejectionReason | string | 是 | 订单拒绝原因（管理端拒单） |
| \|- \|- cancelTime | string | 是 | 取消时间 |
| \|- \|- estimatedDeliveryTime | string | 是 | 预计送达时间（外送场景） |
| \|- \|- deliveryStatus | number | 是 | 配送状态：1 立即送出 / 0 选择具体时间 |
| \|- \|- deliveryTime | string | 是 | 送达时间 |
| \|- \|- packAmount | number | 是 | 打包费（元） |
| \|- \|- tablewareNumber | number | 是 | 餐具数量 |
| \|- \|- tablewareStatus | number | 是 | 餐具状态：1 按餐量提供 / 0 选择具体数量 |
| \|- \|- deliveryMode | number | 是 | ★ 下单模式：1 门店自取 / 2 外送（v2.0 新增） |
| \|- \|- pickupNumber | string | 是 | ★ 取餐号（自取单，如 `A01`；外送单为 null，v2.0 新增） |
| \|- \|- deliveryFee | number | 是 | ★ 外送费（元，外送固定 5，自取为 0 或 null，v2.0 新增） |
| \|- \|- orderDrinkes | string | 是 | 订单菜品摘要字符串（`名称*数量` 用 `;` 连接） |
| \|- \|- orderDetailList | object[] | 是 | 订单明细列表（OrderDetail） |
| \|- \|- \|- id | number | 是 | 明细 id |
| \|- \|- \|- name | string | 是 | 商品名称（快照，含所选规格摘要） |
| \|- \|- \|- orderId | number | 是 | 订单 id |
| \|- \|- \|- drinkId | number | 是 | 饮品 id（套餐明细为 null） |
| \|- \|- \|- setmealId | number | 是 | 套餐 id（饮品明细为 null） |
| \|- \|- \|- drinkFlavor | string | 是 | 口味（课程同款字段保留，山月为 null） |
| \|- \|- \|- specSelected | string | 是 | ★ 规格快照 JSON 数组字符串（v2.0 新增，与购物车 specSelected 同结构） |
| \|- \|- \|- number | number | 是 | 数量 |
| \|- \|- \|- amount | number | 是 | 单价（元，含规格加价） |
| \|- \|- \|- image | string | 是 | 商品图片 |

响应数据样例（外送单）：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 12,
    "records": [
      {
        "id": 103,
        "number": "202609260001",
        "status": 2,
        "userId": 1,
        "addressBookId": 3,
        "orderTime": "2026-09-26 09:30:12",
        "checkoutTime": "2026-09-26 09:30:40",
        "payMethod": 1,
        "payStatus": 1,
        "amount": 42.8,
        "remark": "少冰，谢谢",
        "userName": "山月客人",
        "phone": "13900001111",
        "address": "杭州市西湖区文三路 100 号山月大厦 3 层",
        "consignee": "小月",
        "cancelReason": null,
        "rejectionReason": null,
        "cancelTime": null,
        "estimatedDeliveryTime": null,
        "deliveryStatus": 1,
        "deliveryTime": null,
        "packAmount": 0,
        "tablewareNumber": 0,
        "tablewareStatus": 1,
        "deliveryMode": 2,
        "pickupNumber": null,
        "deliveryFee": 5,
        "orderDrinkes": "山月拿铁*1;午后双人轻食套餐*1",
        "orderDetailList": [
          {
            "id": 1,
            "name": "山月拿铁（大杯/冰）",
            "orderId": 103,
            "drinkId": 5,
            "setmealId": null,
            "drinkFlavor": null,
            "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3}]",
            "number": 1,
            "amount": 18.9,
            "image": "http://localhost:8080/upload/latte-5.png"
          },
          {
            "id": 2,
            "name": "午后双人轻食套餐",
            "orderId": 103,
            "drinkId": null,
            "setmealId": 2,
            "drinkFlavor": null,
            "specSelected": null,
            "number": 1,
            "amount": 18.9,
            "image": "http://localhost:8080/upload/setmeal-duo.png"
          }
        ]
      }
    ]
  }
}
```

## 6.2 各状态订单数量统计

### 6.2.1 基本信息

- 请求路径：`/admin/order/statistics`
- 请求方式：GET
- 接口描述：查询各环节待处理订单数量，用于订单管理页顶部的三个数字角标。权限：需管理端登录。
- 业务规则：返回三段计数：待接单（status=2）、已接单（status=3）、派送中/待取餐（status=4）。

### 6.2.2 请求参数

无请求参数。

### 6.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 订单统计（OrderStatisticsVO） |
| \|- toBeConfirmed | number | 是 | 待接单数量（status=2） |
| \|- confirmed | number | 是 | 已接单数量（status=3） |
| \|- deliveryInProgress | number | 是 | 派送中（外送）/ 待取餐（自取）数量（status=4） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "toBeConfirmed": 4,
    "confirmed": 2,
    "deliveryInProgress": 3
  }
}
```

## 6.3 查询订单详情

### 6.3.1 基本信息

- 请求路径：`/admin/order/details/{id}`
- 请求方式：GET
- 接口描述：按订单 id 查询订单详情（管理端订单详情抽屉/弹窗），返回结构与 6.1.3 的 records 元素完全一致（OrderVO）。权限：需管理端登录。

### 6.3.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 6.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 订单详情（OrderVO，字段同 6.1.3） |

响应数据样例（自取单，含取餐号）：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 104,
    "number": "202609260002",
    "status": 3,
    "userId": 2,
    "addressBookId": null,
    "orderTime": "2026-09-26 10:02:21",
    "checkoutTime": "2026-09-26 10:02:35",
    "payMethod": 1,
    "payStatus": 1,
    "amount": 18.9,
    "remark": "",
    "userName": "山月客人",
    "phone": "13700002222",
    "address": "",
    "consignee": "",
    "cancelReason": null,
    "rejectionReason": null,
    "cancelTime": null,
    "estimatedDeliveryTime": null,
    "deliveryStatus": null,
    "deliveryTime": null,
    "packAmount": 0,
    "tablewareNumber": 0,
    "tablewareStatus": null,
    "deliveryMode": 1,
    "pickupNumber": "A01",
    "deliveryFee": 0,
    "orderDrinkes": "山月拿铁（大杯/冰）*1",
    "orderDetailList": [
      {
        "id": 3,
        "name": "山月拿铁（大杯/冰）",
        "orderId": 104,
        "drinkId": 5,
        "setmealId": null,
        "drinkFlavor": null,
        "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3}]",
        "number": 1,
        "amount": 18.9,
        "image": "http://localhost:8080/upload/latte-5.png"
      }
    ]
  }
}
```

## 6.4 接单

### 6.4.1 基本信息

- 请求路径：`/admin/order/confirm`
- 请求方式：PUT
- 接口描述：商家接单，订单状态 `2 待接单 → 3 已接单（制作中）`。权限：需管理端登录。
- 业务规则：请求体只需 `id`（服务端按状态机把状态置为 3，不信任前端传的状态值）。

### 6.4.2 请求参数

参数格式：application/json（OrdersConfirmDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 订单 id |

请求参数样例：

```json
{
  "id": 103
}
```

### 6.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 6.5 拒单

### 6.5.1 基本信息

- 请求路径：`/admin/order/rejection`
- 请求方式：PUT
- 接口描述：商家拒绝订单，订单状态 `2 待接单 → 6 已取消`，记录拒单原因。权限：需管理端登录。
- 业务规则：★ 拒单原因必填（前端弹窗强制）；已支付的订单（payStatus=1）模拟原路退款（payStatus 置 2），与课程工程一致；拒单原因会写入 `rejectionReason` 并在 C 端订单详情展示。

### 6.5.2 请求参数

参数格式：application/json（OrdersRejectionDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 订单 id |
| rejectionReason | string | 是 | 拒单原因，如「门店高峰期爆单，建议稍后再试」 |

请求参数样例：

```json
{
  "id": 103,
  "rejectionReason": "门店高峰期爆单，建议稍后再试"
}
```

### 6.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 6.6 取消订单

### 6.6.1 基本信息

- 请求路径：`/admin/order/cancel`
- 请求方式：PUT
- 接口描述：商家取消订单（非拒单场景的主动取消，如商品临时售罄），订单状态 → `6 已取消`，记录取消原因。权限：需管理端登录。
- 业务规则：已支付的订单模拟原路退款（payStatus 置 2）；取消原因写入 `cancelReason`。

### 6.6.2 请求参数

参数格式：application/json（OrdersCancelDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 订单 id |
| cancelReason | string | 是 | 取消原因，如「桂花冰酿今日原料售罄」 |

请求参数样例：

```json
{
  "id": 105,
  "cancelReason": "桂花冰酿今日原料售罄"
}
```

### 6.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 6.7 派送订单

### 6.7.1 基本信息

- 请求路径：`/admin/order/delivery/{id}`
- 请求方式：PUT
- 接口描述：外送订单出餐发车，订单状态 `3 已接单 → 4 派送中`。权限：需管理端登录。
- 业务规则：★ 仅对 `deliveryMode=2` 的外送单有意义：服务端校验订单模式与当前状态（必须是 status=3 的外送单），自取单调用本接口返回 `code=0`——自取单在制作完成后直接「完成」（取餐号叫号顾客自取），这是双模式在管理端操作流上的唯一分叉点（面试讲点）。

### 6.7.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 6.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 6.8 完成订单

### 6.8.1 基本信息

- 请求路径：`/admin/order/complete/{id}`
- 请求方式：PUT
- 接口描述：订单完成：外送单 `4 派送中 → 5 已完成`（确认送达）；自取单 `3 已接单 → 5 已完成`（出餐叫号，顾客凭取餐号取走）。权限：需管理端登录。
- 业务规则：完成后订单计入营业额报表与月售统计（monthSales 统计的就是 status=5 的明细）；写入 `deliveryTime`（送达时间）。

### 6.8.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 6.8.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 7. 数据统计

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> ★ 统计类接口的 `dateList`/`turnoverList` 等**列表字段是逗号分隔的字符串**（如 `"2026-09-24,2026-09-25"`），这是课程工程的原版设计（ECharts 折线图可直接 `split(',')` 使用），山月沿用该契约，前端拿到后 `split(',')` 再渲染。

## 7.1 营业额统计

### 7.1.1 基本信息

- 请求路径：`/admin/report/turnoverStatistics`
- 请求方式：GET
- 接口描述：统计指定日期区间内每日营业额（只统计已完成订单 status=5 的金额合计），供「数据统计 → 营业额趋势」折线图使用。权限：需管理端登录。
- 业务规则：`begin`/`end` 参数格式 `yyyy-MM-dd`（服务端 `@DateTimeFormat` 解析为 LocalDate）；营业额单位元；无数据日期补 0，保证日期轴连续。

### 7.1.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 是 | 区间开始日期，格式 `yyyy-MM-dd` |
| end | string | 是 | 区间结束日期，格式 `yyyy-MM-dd` |

请求参数样例：

```
GET /admin/report/turnoverStatistics?begin=2026-09-20&end=2026-09-26
```

### 7.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 营业额统计（TurnoverReportVO） |
| \|- dateList | string | 是 | 日期列表，**逗号分隔字符串**，如 `2026-09-24,2026-09-25` |
| \|- turnoverList | string | 是 | 每日营业额列表，**逗号分隔字符串**，与 dateList 一一对应，如 `406.0,1520.0` |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "dateList": "2026-09-24,2026-09-25,2026-09-26",
    "turnoverList": "406.0,1520.0,752.6"
  }
}
```

## 7.2 用户统计

### 7.2.1 基本信息

- 请求路径：`/admin/report/userStatistics`
- 请求方式：GET
- 接口描述：统计指定日期区间内每日新增用户数与用户总量（按 `user` 表注册时间），供「用户统计」折线图使用。权限：需管理端登录。
- 业务规则：★ v2.0 起 C 端微信登录自动注册，用户量随真实下单增长——新增用户数 = 当日 `user.create_time` 落在该天的记录数；总量为累计值（累计曲线）。

### 7.2.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 是 | 区间开始日期，格式 `yyyy-MM-dd` |
| end | string | 是 | 区间结束日期，格式 `yyyy-MM-dd` |

请求参数样例：

```
GET /admin/report/userStatistics?begin=2026-09-20&end=2026-09-26
```

### 7.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 用户统计（UserReportVO） |
| \|- dateList | string | 是 | 日期列表，逗号分隔字符串 |
| \|- totalUserList | string | 是 | 每日用户总量，逗号分隔字符串 |
| \|- newUserList | string | 是 | 每日新增用户，逗号分隔字符串 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "dateList": "2026-09-24,2026-09-25,2026-09-26",
    "totalUserList": "120,128,136",
    "newUserList": "8,8,8"
  }
}
```

## 7.3 订单统计

### 7.3.1 基本信息

- 请求路径：`/admin/report/ordersStatistics`
- 请求方式：GET
- 接口描述：统计指定日期区间内每日订单数、有效订单数（已完成）与订单完成率，供「订单统计」折线图与指标卡使用。权限：需管理端登录。

### 7.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 是 | 区间开始日期，格式 `yyyy-MM-dd` |
| end | string | 是 | 区间结束日期，格式 `yyyy-MM-dd` |

请求参数样例：

```
GET /admin/report/ordersStatistics?begin=2026-09-20&end=2026-09-26
```

### 7.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 订单统计（OrderReportVO） |
| \|- dateList | string | 是 | 日期列表，逗号分隔字符串 |
| \|- orderCountList | string | 是 | 每日订单数，逗号分隔字符串 |
| \|- validOrderCountList | string | 是 | 每日有效订单数，逗号分隔字符串 |
| \|- totalOrderCount | number | 是 | 区间订单总数 |
| \|- validOrderCount | number | 是 | 区间有效订单总数 |
| \|- orderCompletionRate | number | 是 | 订单完成率（小数，如 0.85 表示 85%） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "dateList": "2026-09-24,2026-09-25,2026-09-26",
    "orderCountList": "26,30,22",
    "validOrderCountList": "20,21,10",
    "totalOrderCount": 78,
    "validOrderCount": 51,
    "orderCompletionRate": 0.65
  }
}
```

## 7.4 查询销量TOP10

### 7.4.1 基本信息

- 请求路径：`/admin/report/top10`
- 请求方式：GET
- 接口描述：统计指定日期区间内销量前十的商品（饮品与套餐合计，按销量降序），供「销量 TOP10」柱状图使用。权限：需管理端登录。
- 业务规则：销量来自 `order_detail` 聚合（`SUM(number)`），关联 `orders(status=5)` 只统计已完成订单。

### 7.4.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 是 | 区间开始日期，格式 `yyyy-MM-dd` |
| end | string | 是 | 区间结束日期，格式 `yyyy-MM-dd` |

请求参数样例：

```
GET /admin/report/top10?begin=2026-09-01&end=2026-09-26
```

### 7.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 销量统计（SalesTop10ReportVO） |
| \|- nameList | string | 是 | 商品名称列表，**逗号分隔字符串**，按销量降序 |
| \|- numberList | string | 是 | 销量列表，逗号分隔字符串，与 nameList 一一对应 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "nameList": "山月拿铁,桂花冰酿,椰香澳白,黄油可颂",
    "numberList": "356,208,150,98"
  }
}
```

## 7.5 导出Excel报表

### 7.5.1 基本信息

- 请求路径：`/admin/report/export`
- 请求方式：GET
- 接口描述：导出最近 30 天运营数据 Excel 报表（每日营业额/订单数/有效订单/完成率）。权限：需管理端登录。
- 业务规则：★ **响应体是 Excel 文件流（`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`），不是 Result JSON**——前端必须用 `responseType: 'blob'` 接收并用 `URL.createObjectURL` 触发下载；导出统计口径与 7.3 一致（近 30 天，按天分组）。

### 7.5.2 请求参数

无请求参数。

### 7.5.3 响应数据

参数格式：Excel 文件流（非 JSON）

| 项目 | 说明 |
| --- | --- |
| Content-Type | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| Content-Disposition | `attachment;filename=运营数据统计.xlsx`（文件名按实现可能为 URL 编码） |
| 响应体 | xlsx 二进制流，列：日期 / 营业额 / 订单数 / 有效订单数 / 订单完成率 |

---

# 8. 工作台

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> 工作台（Workspace）= 管理端首页仪表盘的数据聚合模块：4 个只读接口分别驱动「今日数据」「订单管理」「商品管理」「套餐管理」四张卡片。

## 8.1 查询今日运营数据

### 8.1.1 基本信息

- 请求路径：`/admin/workspace/businessData`
- 请求方式：GET
- 接口描述：查询当日运营核心指标（工作台首页「今日数据」卡片）：营业额、有效订单数、订单完成率、平均客单价、新增用户。权限：需管理端登录。
- 业务规则：统计区间为当天 00:00:00 ~ 23:59:59；营业额只算已完成订单；平均客单价 = 营业额 / 有效订单数（除数为 0 时按 0 处理）。

### 8.1.2 请求参数

无请求参数。

### 8.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 今日数据（BusinessDataVO） |
| \|- turnover | number | 是 | 今日营业额（元，已完成订单合计） |
| \|- validOrderCount | number | 是 | 今日有效订单数（已完成） |
| \|- orderCompletionRate | number | 是 | 今日订单完成率（小数，如 0.86） |
| \|- unitPrice | number | 是 | 平均客单价（元） |
| \|- newUsers | number | 是 | 今日新增用户数 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "turnover": 752.6,
    "validOrderCount": 10,
    "orderCompletionRate": 0.86,
    "unitPrice": 75.26,
    "newUsers": 8
  }
}
```

## 8.2 查询订单管理数据

### 8.2.1 基本信息

- 请求路径：`/admin/workspace/overviewOrders`
- 请求方式：GET
- 接口描述：查询订单总览计数（工作台「订单管理」卡片）：待接单、待派送/待取餐、已完成、已取消、全部。权限：需管理端登录。
- 业务规则：`deliveredOrders` 在山月双模式下语义为「派送中（外送）/ 待取餐（自取）」（status=4），字段名沿用课程工程不改动，前端文案按两种模式渲染。

### 8.2.2 请求参数

无请求参数。

### 8.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 订单总览（OrderOverViewVO） |
| \|- waitingOrders | number | 是 | 待接单数量（status=2） |
| \|- deliveredOrders | number | 是 | 待派送/待取餐数量（status=4） |
| \|- completedOrders | number | 是 | 已完成数量（status=5） |
| \|- cancelledOrders | number | 是 | 已取消数量（status=6） |
| \|- allOrders | number | 是 | 全部订单数量 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "waitingOrders": 4,
    "deliveredOrders": 3,
    "completedOrders": 10,
    "cancelledOrders": 2,
    "allOrders": 19
  }
}
```

## 8.3 查询商品总览

### 8.3.1 基本信息

- 请求路径：`/admin/workspace/overviewDrinkes`
- 请求方式：GET
- 接口描述：查询饮品总览计数（工作台「商品管理」卡片）：起售数与停售数。权限：需管理端登录。

### 8.3.2 请求参数

无请求参数。

### 8.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 商品总览（DrinkOverViewVO） |
| \|- sold | number | 是 | 已起售数量（status=1） |
| \|- discontinued | number | 是 | 已停售数量（status=0） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "sold": 9,
    "discontinued": 2
  }
}
```

## 8.4 查询套餐总览

### 8.4.1 基本信息

- 请求路径：`/admin/workspace/overviewSetmeals`
- 请求方式：GET
- 接口描述：查询套餐总览计数（工作台「套餐管理」卡片）：起售数与停售数。权限：需管理端登录。

### 8.4.2 请求参数

无请求参数。

### 8.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 套餐总览（SetmealOverViewVO） |
| \|- sold | number | 是 | 已起售数量（status=1） |
| \|- discontinued | number | 是 | 已停售数量（status=0） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "sold": 2,
    "discontinued": 1
  }
}
```

---

# 9. 店铺管理

> 管理端模块：以下路径均为服务端真实路径（`/admin/**`，需 JWT）；浏览器端经 nginx 将 `/api/**` 前缀替换转发为 `/admin/**`（见全局约定·认证机制）。
> 店铺营业状态存 **Redis**（key：`shanyue:shop:status`）：高频读（C 端每次进首页都读）、极少写（商家一天开关几次）、丢了无所谓（未设置时默认营业中）——是「开关型数据放缓存」的标准案例。v2.1 起「门店公告」回归服务端（同款 Redis 方案，key：`shanyue:shop:notice`），本模块共 4 个接口：营业状态双接口 + 公告读写双接口。

## 9.1 修改营业状态

### 9.1.1 基本信息

- 请求路径：`/admin/shop/{status}`
- 请求方式：PUT
- 接口描述：设置店铺营业状态（管理端工作台/店铺页的开关按钮）。打烊后 C 端无法加购与下单。权限：需管理端登录。
- 业务规则：★ Redis `SET shanyue:shop:status 1/0`，写操作直接覆盖，无事务/无并发冲突；管理端与 C 端读写共用同一个 key（key 只在一个 Service 出现，改 key 不用到处找——层级命名 `模块:对象:属性` 的治理规范，面试讲点）。

### 9.1.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | number | 是 | 路径参数，目标状态：`1` 营业中 / `0` 已打烊 |

### 9.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 9.2 查询营业状态

### 9.2.1 基本信息

- 请求路径：`/admin/shop/status`
- 请求方式：GET
- 接口描述：查询店铺当前营业状态（管理端开关按钮回显）。权限：需管理端登录。
- 业务规则：Redis 无值时默认返回 `1`（营业中）——避免「没设置过状态就全员打烊」的尴尬（课程原版返回 null 由前端判空，山月服务端兜底，更稳）。

### 9.2.2 请求参数

无请求参数。

### 9.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | number | 是 | 营业状态：`1` 营业中 / `0` 已打烊 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": 1
}
```

---

## 9.3 查询店铺公告【v2.1】

### 9.3.1 基本信息

- 请求路径：`/admin/shop/notice`
- 请求方式：GET
- 接口描述：查询店铺公告（管理端「店铺设置」页公告卡回显；空串 = 暂无公告）。权限：需管理端登录。
- 业务规则：Redis key `shanyue:shop:notice`；无值时服务端兜底返回空串（前端直接展示「暂无公告」，判空逻辑与营业状态默认 1 同款思路）。

### 9.3.2 请求参数

无请求参数。

### 9.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | string | 是 | 公告文本；从未设置过时为 `""` |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": "今日桂花限定买一送一，欢迎品尝"
}
```

## 9.4 修改店铺公告【v2.1】

### 9.4.1 基本信息

- 请求路径：`/admin/shop/notice`
- 请求方式：PUT
- 接口描述：修改店铺公告（管理端「店铺设置」页「保存公告」按钮）。权限：需管理端登录。
- 业务规则：★ 与 `PUT /admin/shop/{status}` 共存于一个 Controller —— `PUT /admin/shop/notice` 按「精确字面量优先于路径变量」的路由规则命中本接口，不会被 `{status}` 吃掉（URI 模板匹配的固定规则，答辩讲点）。★ 公告与状态同属「高频读、极少写、丢了不伤交易链路」的轻数据，同款 Redis 方案。

### 9.4.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| notice | string | 是 | 公告文本，去除首尾空白后非空，≤ 100 字（与前端 maxlength 同口径） |

请求参数样例：

```json
{
  "notice": "今日桂花限定买一送一，欢迎品尝"
}
```

### 9.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

---

# 10. C端-微信登录【v2.0 新增】

> C 端模块：小程序**直连** `http://localhost:8080`，路径即真实路径 `/user/**`；除 10.1 登录与 11.1 店铺状态外，所有接口需在请求头携带 C 端 JWT（`token: <jwt>`），未登录返回 HTTP `401`。
> ★ v2.0 核心变更：C 端从匿名改为**微信登录、先登录后下单**，与课程模式一致。

## 10.1 微信登录

### 10.1.1 基本信息

- 请求路径：`/user/user/login`
- 请求方式：POST
- 接口描述：C 端微信登录。小程序端先调 `wx.login()` 拿到临时授权码 `code`，POST 到本接口；服务端用 code 调微信 `jscode2session` 接口换取 `openid`，按 openid 查用户：已注册直接登录，未注册**自动注册**（首登创建 user 记录）；成功后返回用户信息与 C 端 JWT。本接口与店铺状态接口是仅有的两个免登录接口。
- 业务规则：★ 微信登录链路（面试讲点）：`wx.login()` → code（一次性，5 分钟有效）→ 服务端携带 appid/secret 请求微信 `jscode2session` → openid + session_key → 按 openid 查/注册 → 生成 JWT（claims 放 `userId`）→ 返回 token。★ 骨架工程内置**本地模拟**：`shanyue.wechat` 配置 mock 开关，code 固定映射 `mock_openid_demo` 等种子用户，无企业主体小程序也能跑通全流程；真实环境只需替换 HttpClient 调用点。★ `UserLoginVO` 相比课程新增 `nickName`/`avatarUrl`（可空），供小程序「我的」页展示；本地模拟登录时昵称取种子数据。

### 10.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | string | 是 | 微信授权码，由小程序端 `wx.login()` 获取，一次性有效 |

请求参数样例：

```json
{
  "code": "0a3Xk9000Qw4rT1bXp300bZv1d2Xk90S"
}
```

### 10.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 登录结果（UserLoginVO） |
| \|- id | number | 是 | 用户 id |
| \|- openid | string | 是 | 微信唯一标识（本地模拟为 `mock_openid_xxx`） |
| \|- token | string | 是 | C 端 JWT，后续请求放入请求头 `token` |
| \|- nickName | string | 否 | ★ 昵称（可空，v2.0 新增） |
| \|- avatarUrl | string | 否 | ★ 头像 URL（可空，v2.0 新增） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "openid": "mock_openid_demo",
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.7Q8wZx1yF2nG3hJ4kL5mN6oP7qR8sT9uV0wX1yZ2a3b",
    "nickName": "山月客人",
    "avatarUrl": "http://localhost:8080/upload/avatar-demo.png"
  }
}
```

## 10.2 查询用户资料

### 10.2.1 基本信息

- 请求路径：`/user/user/profile`
- 请求方式：GET
- 接口描述：查询当前登录用户资料（「我的」页面：昵称/头像/手机号/积分展示）。权限：需 C 端登录。
- 业务规则：★ `id` 不从前端传——拦截器解析 token 后已把 `userId` 放进 ThreadLocal（BaseContext），Service 直接取，天然防「看别人的资料」（防越权三板斧之一，面试讲点）。★ `points` 为山月积分余额（扩展模块 19 的数据源），主链路联调期可返回 0。

### 10.2.2 请求参数

无请求参数（userId 由服务端从 token 解析）。

### 10.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 用户资料 |
| \|- id | number | 是 | 用户 id |
| \|- nickName | string | 是 | 昵称（未设置时服务端可回退默认「山月茶咖用户」） |
| \|- avatarUrl | string | 是 | 头像 URL（未设置可为空串） |
| \|- phone | string | 是 | 手机号（未绑定可为空串） |
| \|- points | number | 是 | ★ 积分余额（扩展模块 19） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "nickName": "山月客人",
    "avatarUrl": "http://localhost:8080/upload/avatar-demo.png",
    "phone": "13900001111",
    "points": 85
  }
}
```

---

# 11. C端-店铺

> C 端模块：小程序直连 `http://localhost:8080`；本模块唯一接口免登录（未登录也要能判断能不能下单）。

## 11.1 查询店铺营业状态

### 11.1.1 基本信息

- 请求路径：`/user/shop/status`
- 请求方式：GET
- 接口描述：查询店铺营业状态，C 端首页/菜单页据此展示打烊遮罩并禁用下单按钮。免登录。
- 业务规则：与管理端 9.1/9.2 读写同一个 Redis key（`shanyue:shop:status`）；打烊时前端禁用加购与下单入口，服务端在下单/加购链路还会二次校验（防绕过前端直发请求，msg「门店已打烊，暂时无法下单」）。

### 11.1.2 请求参数

无请求参数。

### 11.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | number | 是 | 营业状态：`1` 营业中 / `0` 已打烊 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": 1
}
```

---

# 12. C端-分类与商品

> C 端模块：小程序直连 `http://localhost:8080`；除本页接口外需 token（本页接口也在拦截器保护范围内，携带 token 即可）。
> C 端只返回**起售（status=1）且分类启用**的商品——这是服务端过滤，前端不做状态判断。

## 12.1 查询分类列表

### 12.1.1 基本信息

- 请求路径：`/user/category/list`
- 请求方式：GET
- 接口描述：查询 C 端菜单页分类锚点列表。权限：需 C 端登录。
- 业务规则：服务端固定追加启用状态过滤（只返回 status=1 的分类）；`type` 可选，菜单页一般传 `1` 与 `2` 各拉一次（商品区与套餐区）。

### 12.1.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| type | number | 否 | 分类类型：1 商品分类 / 2 套餐分类，不传查全部 |

请求参数样例：

```
GET /user/category/list?type=1
```

### 12.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 分类列表（Category：id/type/name/sort/status） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    { "id": 1, "type": 1, "name": "经典咖啡", "sort": 1, "status": 1 },
    { "id": 2, "type": 1, "name": "山月茶饮", "sort": 2, "status": 1 }
  ]
}
```

## 12.2 查询在售饮品（含规格组）

### 12.2.1 基本信息

- 请求路径：`/user/drink/list`
- 请求方式：GET
- 接口描述：按分类查询在售饮品列表（C 端菜单页商品区数据源），返回 DrinkVO 列表，含规格组（规格弹窗数据源）与月售徽标数据。权限：需 C 端登录。
- 业务规则：①只返回起售饮品（服务端过滤 status=1）；②★ 规格组 `specGroups` 结构化返回（替代 v1.6 的 flavors 字符串）：前端规格弹窗直接渲染「组→选项」，选中后把 `groupName/optionName/priceDelta` 组装成 JSON 快照传给购物车（见 13.1）；③★ `monthSales` 月售动态统计（近 30 天已完成订单明细 SUM，非数据库字段）；④★ Redis 缓存：以 `drink_{categoryId}` 为 key 缓存整份列表，管理端增删改/起停售时清除对应缓存（缓存一致性的最小实现，面试讲点）。

### 12.2.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| categoryId | number | 是 | 分类 id |

请求参数样例：

```
GET /user/drink/list?categoryId=1
```

### 12.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 饮品列表（DrinkVO） |
| \|- id | number | 是 | 饮品 id |
| \|- name | string | 是 | 饮品名称 |
| \|- categoryId | number | 是 | 分类 id |
| \|- price | number | 是 | 基础价格（元） |
| \|- image | string | 是 | 图片 URL |
| \|- description | string | 是 | 描述信息 |
| \|- status | number | 是 | 状态（C 端恒为 1 起售） |
| \|- monthSales | number | 是 | ★ 近 30 天月售（实时统计） |
| \|- specGroups | object[] | 是 | ★ 规格组列表 |
| \|- \|- name | string | 是 | 规格组名称 |
| \|- \|- sort | number | 是 | 排序 |
| \|- \|- options | object[] | 是 | 选项列表 |
| \|- \|- \|- name | string | 是 | 选项名称 |
| \|- \|- \|- priceDelta | number | 是 | 加价金额（元） |
| \|- \|- \|- sort | number | 是 | 排序 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 5,
      "name": "山月拿铁",
      "categoryId": 1,
      "price": 15.9,
      "image": "http://localhost:8080/upload/latte-5.png",
      "description": "浓缩与鲜奶的经典配比，山月招牌",
      "status": 1,
      "monthSales": 356,
      "specGroups": [
        {
          "id": 1,
          "drinkId": 5,
          "name": "杯型",
          "sort": 1,
          "options": [
            { "id": 1, "name": "中杯", "priceDelta": 0, "sort": 1 },
            { "id": 2, "name": "大杯", "priceDelta": 3, "sort": 2 }
          ]
        },
        {
          "id": 2,
          "drinkId": 5,
          "name": "温度",
          "sort": 2,
          "options": [
            { "id": 3, "name": "热", "priceDelta": 0, "sort": 1 },
            { "id": 4, "name": "冰", "priceDelta": 0, "sort": 2 }
          ]
        }
      ]
    }
  ]
}
```

## 12.3 查询套餐列表

### 12.3.1 基本信息

- 请求路径：`/user/setmeal/list`
- 请求方式：GET
- 接口描述：按套餐分类查询在售套餐列表。权限：需 C 端登录。
- 业务规则：只返回起售套餐（服务端过滤 status=1）；套餐点击进入套餐详情弹窗（12.4）查看内含饮品。

### 12.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| categoryId | number | 是 | 套餐分类 id |

请求参数样例：

```
GET /user/setmeal/list?categoryId=4
```

### 12.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 套餐列表（Setmeal） |
| \|- id | number | 是 | 套餐 id |
| \|- name | string | 是 | 套餐名称 |
| \|- categoryId | number | 是 | 套餐分类 id |
| \|- price | number | 是 | 套餐价格（元） |
| \|- image | string | 是 | 图片 URL |
| \|- description | string | 是 | 描述信息 |
| \|- status | number | 是 | 状态（C 端恒为 1 起售） |
| \|- updateTime | string | 是 | 更新时间 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 2,
      "name": "午后双人轻食套餐",
      "categoryId": 4,
      "price": 39.9,
      "image": "http://localhost:8080/upload/setmeal-duo.png",
      "description": "拿铁 + 可颂 + 拿铁 + 巴斯克，两人分享刚好",
      "status": 1,
      "updateTime": "2026-09-25 11:00:00"
    }
  ]
}
```

## 12.4 根据套餐ID查询套餐内饮品

### 12.4.1 基本信息

- 请求路径：`/user/setmeal/drink/{id}`
- 请求方式：GET
- 接口描述：按套餐 id 查询套餐内含饮品列表（C 端套餐详情弹窗）。权限：需 C 端登录。

### 12.4.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，套餐 id |

### 12.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 套餐内含饮品（DrinkItemVO） |
| \|- name | string | 是 | 饮品名称 |
| \|- copies | number | 是 | 份数 |
| \|- image | string | 是 | 饮品图片 |
| \|- description | string | 是 | 饮品描述 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    { "name": "山月拿铁", "copies": 2, "image": "http://localhost:8080/upload/latte-5.png", "description": "浓缩与鲜奶的经典配比，山月招牌" },
    { "name": "黄油可颂", "copies": 1, "image": "http://localhost:8080/upload/butter-croissant.png", "description": "现烤酥皮，层次分明" },
    { "name": "巴斯克芝士蛋糕", "copies": 1, "image": "http://localhost:8080/upload/basque-cheesecake.png", "description": "焦香表面，芝士浓郁" }
  ]
}
```

---

# 13. C端-购物车

> C 端模块：小程序直连 `http://localhost:8080`，所有接口需 token。
> ★ 购物车按「**商品 + 规格快照**」分行：大杯拿铁与中杯拿铁是两行；合并/拆行全部依赖 `specSelected` 字符串。★ `userId` 一律取 ThreadLocal（token 解析），**接口参数里没有 userId**——防越权。★ 金额铁律：`amount`（行单价）由服务端按「基础价 + Σ规格加价」重算，永不信任前端金额。

## 13.1 添加商品到购物车

### 13.1.1 基本信息

- 请求路径：`/user/shoppingCart/add`
- 请求方式：POST
- 接口描述：加入购物车。若「当前用户 + 同商品 + 相同规格快照」的行已存在则数量 +1，否则新增一行。权限：需 C 端登录。
- 业务规则：①★ `specSelected` 为所选规格的 JSON 数组快照字符串（结构见下），完整形态为 `[{"groupId":1,"groupName":"杯型","optionId":2,"optionName":"大杯","priceDelta":3}]`，仅含 `groupName/optionName/priceDelta` 的简化形态亦可——服务端只按**整串字符串完全相同**判定同规格（`equals` 比对，前后端约定好一种形态即可）；②★ 同品同规格 number+1、不同规格另起一行（哪怕只差一个选项）；③★ `amount` 与 `number` 无需前端传：`amount` 由服务端按 `drink.price + Σ priceDelta` 重算，`number` 首次加购为 1、命中同行则 +1；④加购前服务端校验商品起售状态与门店营业状态（打烊时 msg「门店已打烊，暂时无法下单」）。

### 13.1.2 请求参数

参数格式：application/json（ShoppingCartDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| drinkId | number | 二选一 | 饮品 id（与 setmealId 二选一） |
| setmealId | number | 二选一 | 套餐 id（与 drinkId 二选一，套餐无规格，specSelected 不传） |
| specSelected | string | 否 | ★ 规格快照 JSON 数组字符串（无规格商品/套餐不传或传空串） |

请求参数样例（饮品，选中「大杯 + 冰」）：

```json
{
  "drinkId": 5,
  "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3},{\"groupId\":2,\"groupName\":\"温度\",\"optionId\":4,\"optionName\":\"冰\",\"priceDelta\":0}]"
}
```

请求参数样例（套餐）：

```json
{
  "setmealId": 2
}
```

### 13.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null（前端加购后调 13.2 刷新列表） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 13.2 查询购物车列表

### 13.2.1 基本信息

- 请求路径：`/user/shoppingCart/list`
- 请求方式：GET
- 接口描述：查询当前用户购物车全部行（角标数字 = Σnumber，结算金额 = Σamount×number）。权限：需 C 端登录。

### 13.2.2 请求参数

无请求参数（userId 由服务端从 token 解析）。

### 13.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 购物车列表（ShoppingCart） |
| \|- id | number | 是 | 购物车行 id |
| \|- name | string | 是 | 商品名称（冗余，下单时直接复制进订单明细） |
| \|- drinkId | number | 是 | 饮品 id（套餐行为 null） |
| \|- setmealId | number | 是 | 套餐 id（饮品行为 null） |
| \|- drinkFlavor | string | 是 | 口味（课程同款字段保留，山月为 null） |
| \|- specSelected | string | 是 | ★ 规格快照 JSON 数组字符串（减购/下单时原样回传） |
| \|- number | number | 是 | 数量 |
| \|- amount | number | 是 | ★ 行单价（元，服务端重算：基础价 + Σ规格加价） |
| \|- image | string | 是 | 商品图片 |
| \|- createTime | string | 是 | 加购时间 |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "山月拿铁",
      "userId": 1,
      "drinkId": 5,
      "setmealId": null,
      "drinkFlavor": null,
      "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3},{\"groupId\":2,\"groupName\":\"温度\",\"optionId\":4,\"optionName\":\"冰\",\"priceDelta\":0}]",
      "number": 2,
      "amount": 18.9,
      "image": "http://localhost:8080/upload/latte-5.png",
      "createTime": "2026-09-26 09:20:11"
    },
    {
      "id": 2,
      "name": "午后双人轻食套餐",
      "userId": 1,
      "drinkId": null,
      "setmealId": 2,
      "drinkFlavor": null,
      "specSelected": null,
      "number": 1,
      "amount": 39.9,
      "image": "http://localhost:8080/upload/setmeal-duo.png",
      "createTime": "2026-09-26 09:21:03"
    }
  ]
}
```

## 13.3 购物车减一个

### 13.3.1 基本信息

- 请求路径：`/user/shoppingCart/sub`
- 请求方式：POST
- 接口描述：将购物车中某行数量 -1；减到 0 删除该行。权限：需 C 端登录。
- 业务规则：★ **body 必须带上加购时的 `specSelected` 快照原样回传**：购物车按「商品+规格」分行，只传 drinkId 服务端不知道该减哪一行（大杯与中杯会混淆）；★ add 与 sub 都是 POST + JSON body（课程同款契约），不要想当然用 DELETE。

### 13.3.2 请求参数

参数格式：application/json（ShoppingCartDTO，与 13.1.2 同结构）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| drinkId | number | 二选一 | 饮品 id（与 setmealId 二选一） |
| setmealId | number | 二选一 | 套餐 id |
| specSelected | string | 否 | ★ 规格快照（须与加购时完全一致才能命中同一行） |

请求参数样例：

```json
{
  "drinkId": 5,
  "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3},{\"groupId\":2,\"groupName\":\"温度\",\"optionId\":4,\"optionName\":\"冰\",\"priceDelta\":0}]"
}
```

### 13.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 13.4 清空购物车

### 13.4.1 基本信息

- 请求路径：`/user/shoppingCart/clean`
- 请求方式：DELETE
- 接口描述：清空当前用户购物车全部行。权限：需 C 端登录。

### 13.4.2 请求参数

无请求参数（userId 由服务端从 token 解析）。

### 13.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 14. C端-地址簿【v2.0 新增】

> C 端模块：小程序直连 `http://localhost:8080`，所有接口需 token。
> ★ 地址簿是 v2.0 外送模式的配套模块（v1.6 匿名自取无需地址）：字段结构与课程工程 `AddressBook` 完全一致；**外送下单时会把 consignee/phone/详细地址快照进订单表**——地址以后被改/删也不影响历史订单（快照思想，面试讲点）。

## 14.1 新增地址

### 14.1.1 基本信息

- 请求路径：`/user/addressBook`
- 请求方式：POST
- 接口描述：新增收货地址。权限：需 C 端登录。
- 业务规则：①`userId` 由服务端从 token 取，前端不传；②★ 若新增的是第一条地址，服务端自动置为默认地址（isDefault=1），提升下单体验；③设为默认时先清掉旧默认再写入（同一用户至多一条默认地址）；④省市区三组「编码/名称」字段由小程序地址选择器带出（选填），`detail` 为详细地址必填。

### 14.1.2 请求参数

参数格式：application/json（AddressBook）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| consignee | string | 是 | 收货人姓名 |
| phone | string | 是 | 手机号（11 位） |
| sex | string | 否 | 性别：`"1"` 男 / `"0"` 女 |
| provinceCode | string | 否 | 省级行政区划编码 |
| provinceName | string | 否 | 省级名称 |
| cityCode | string | 否 | 市级行政区划编码 |
| cityName | string | 否 | 市级名称 |
| districtCode | string | 否 | 区级行政区划编码 |
| districtName | string | 否 | 区级名称 |
| detail | string | 是 | 详细地址（楼栋门牌等） |
| label | string | 否 | 标签：家 / 公司 / 学校 |
| isDefault | number | 否 | 是否默认：`1` 是 / `0` 否（不传按 0 处理，首条地址自动置 1） |

请求参数样例：

```json
{
  "consignee": "小月",
  "phone": "13900001111",
  "sex": "0",
  "provinceCode": "330000",
  "provinceName": "浙江省",
  "cityCode": "330100",
  "cityName": "杭州市",
  "districtCode": "330106",
  "districtName": "西湖区",
  "detail": "文三路 100 号山月大厦 3 层",
  "label": "公司",
  "isDefault": 1
}
```

### 14.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 14.2 修改地址

### 14.2.1 基本信息

- 请求路径：`/user/addressBook`
- 请求方式：PUT
- 接口描述：修改收货地址（编辑页回显后保存）。权限：需 C 端登录。
- 业务规则：编辑时必须带 `id`；★ 服务端校验地址归属（只能改自己的地址，越权按不存在处理）；置为默认的处理规则同 14.1。

### 14.2.2 请求参数

参数格式：application/json（AddressBook，字段同 14.1.2，另加）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 地址 id（编辑必传） |

请求参数样例：

```json
{
  "id": 3,
  "consignee": "小月",
  "phone": "13900001111",
  "sex": "0",
  "provinceCode": "330000",
  "provinceName": "浙江省",
  "cityCode": "330100",
  "cityName": "杭州市",
  "districtCode": "330106",
  "districtName": "西湖区",
  "detail": "文二路 391 号西湖国际科技大厦 B 座 12 层",
  "label": "公司",
  "isDefault": 1
}
```

### 14.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 14.3 删除地址

### 14.3.1 基本信息

- 请求路径：`/user/addressBook`
- 请求方式：DELETE
- 接口描述：按 id 删除收货地址。权限：需 C 端登录。
- 业务规则：★ `id` 用 **query** 传参（`DELETE /user/addressBook?id=3`，课程契约如此，不是路径参数也不是 body）——这是前端联调高频出错点；删除默认地址后不自动顺延新默认（下次下单手动选）。

### 14.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | query 参数，地址 id |

请求参数样例：

```
DELETE /user/addressBook?id=3
```

### 14.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 14.4 根据ID查询地址

### 14.4.1 基本信息

- 请求路径：`/user/addressBook/{id}`
- 请求方式：GET
- 接口描述：按 id 查询地址详情（编辑页回显）。权限：需 C 端登录。

### 14.4.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，地址 id |

### 14.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 地址详情（AddressBook，字段同 14.1.2，另含 id/userId/createTime 等） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 3,
    "userId": 1,
    "consignee": "小月",
    "phone": "13900001111",
    "sex": "0",
    "provinceCode": "330000",
    "provinceName": "浙江省",
    "cityCode": "330100",
    "cityName": "杭州市",
    "districtCode": "330106",
    "districtName": "西湖区",
    "detail": "文三路 100 号山月大厦 3 层",
    "label": "公司",
    "isDefault": 1
  }
}
```

## 14.5 查询地址列表

### 14.5.1 基本信息

- 请求路径：`/user/addressBook/list`
- 请求方式：GET
- 接口描述：查询当前用户全部收货地址（下单页地址选择弹层数据源）。权限：需 C 端登录。

### 14.5.2 请求参数

无请求参数（userId 由服务端从 token 解析）。

### 14.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object[] | 是 | 地址列表（AddressBook，元素字段同 14.4.3） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 3,
      "userId": 1,
      "consignee": "小月",
      "phone": "13900001111",
      "sex": "0",
      "provinceCode": "330000",
      "provinceName": "浙江省",
      "cityCode": "330100",
      "cityName": "杭州市",
      "districtCode": "330106",
      "districtName": "西湖区",
      "detail": "文三路 100 号山月大厦 3 层",
      "label": "公司",
      "isDefault": 1
    },
    {
      "id": 4,
      "userId": 1,
      "consignee": "小月",
      "phone": "13900001111",
      "sex": "0",
      "provinceCode": "330000",
      "provinceName": "浙江省",
      "cityCode": "330100",
      "cityName": "杭州市",
      "districtCode": "330108",
      "districtName": "滨江区",
      "detail": "江南大道 588 号中赢国际 2 幢 801",
      "label": "家",
      "isDefault": 0
    }
  ]
}
```

## 14.6 设置默认地址

### 14.6.1 基本信息

- 请求路径：`/user/addressBook/default`
- 请求方式：PUT
- 接口描述：将某条地址设为默认地址（列表页长按/开关操作）。权限：需 C 端登录。
- 业务规则：★ 请求体是**含 id 的完整地址 JSON**（PUT body，不是 query）——与 14.3 的 DELETE query 形成对照，联调注意区分；服务端先清旧默认再置新默认（同一用户至多一条默认）。

### 14.6.2 请求参数

参数格式：application/json（AddressBook，至少含 id 与 isDefault）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 地址 id |
| isDefault | number | 是 | 固定传 `1`（设为默认） |
| 其余字段 | - | 否 | 可原样回传 14.4 的完整地址（服务端只处理默认标记） |

请求参数样例：

```json
{
  "id": 4,
  "isDefault": 1
}
```

### 14.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 14.7 查询默认地址

### 14.7.1 基本信息

- 请求路径：`/user/addressBook/default`
- 请求方式：GET
- 接口描述：查询当前用户的默认地址（外送下单页自动带出）。权限：需 C 端登录。
- 业务规则：★ 无默认地址时 `data` 返回 `null`（不是空数组、也不是 code=0）——前端需要判空后引导用户去新增地址。

### 14.7.2 请求参数

无请求参数（userId 由服务端从 token 解析）。

### 14.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 默认地址（AddressBook，字段同 14.4.3）；无默认地址时为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 3,
    "userId": 1,
    "consignee": "小月",
    "phone": "13900001111",
    "sex": "0",
    "provinceCode": "330000",
    "provinceName": "浙江省",
    "cityCode": "330100",
    "cityName": "杭州市",
    "districtCode": "330106",
    "districtName": "西湖区",
    "detail": "文三路 100 号山月大厦 3 层",
    "label": "公司",
    "isDefault": 1
  }
}
```

---

# 15. C端-订单

> C 端模块：小程序直连 `http://localhost:8080`，所有接口需 token。
> ★ 订单金额/取餐号/收货信息全部由服务端产出（前端传的金额仅作展示参考）；★ 越权防护：查单先校验归属，不是你的订单一律按「订单不存在」处理。

## 15.1 提交订单

### 15.1.1 基本信息

- 请求路径：`/user/order/submit`
- 请求方式：POST
- 接口描述：根据当前用户购物车创建订单（外送/自取双模式），返回服务端重算的金额、取餐号（自取）或外送费（外送）。权限：需 C 端登录。
- 业务规则（**下单六步法**，顺序即业务，整体在 `@Transactional` 中，要么全成要么全不成）：
  ①**购物车非空**：空车不能下单，返回 `code=0`（msg「购物车数据为空」）；
  ②**外送地址必填**：`deliveryMode=2` 时必须传有效 `addressBookId`（查不到返回 `code=0`，msg「地址为空，不能下单」）；自取可不传，传了也会快照联系人手机号；
  ③**金额服务端重算**：订单金额 = Σ(购物车行 `amount × number`) + 外送费（仅外送单 +5 元，自取 0）——★ 前端 `amount` 字段仅展示参考，服务端一律覆盖重算（防篡改，面试讲点：为什么不能信前端金额）；
  ④**取餐号生成**：★ 自取单由 Redis `INCR` 生成取餐号，key 为 `shanyue:pickup:yyyy-MM-dd`（按天自增、跨天自然重置），格式 `A01`/`A02`…（原子操作并发不重号，面试讲点）；
  ⑤**外送地址快照**：★ 外送单把 `consignee/phone/详细地址` 快照进订单表（地址以后被改/删也不影响历史订单，与订单明细快照同一思想）；
  ⑥**成功清空购物车**：下单成功后删除该用户购物车全部行。
  ★ 教学取舍（与课程差异点）：骨架「提交订单」直接进入 `status=2 待接单`（`payStatus=0` 未支付）——模拟支付场景用户总是秒支付，独立待付款单停留时间极短；真实电商应为 `1 → 支付回调 → 2`（paySuccess 注释中有完整讲解）；取消接口对状态 1/2 都放行，兼容两种口径。订单号 `number` 由「时间戳 + 3 位随机」生成。

### 15.1.2 请求参数

参数格式：application/json（OrdersSubmitDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| addressBookId | number | 条件必须 | 地址簿 id：**外送必填**；自取可空 |
| payMethod | number | 是 | 支付方式：`1` 微信 / `2` 支付宝 |
| remark | string | 否 | 订单备注 |
| deliveryMode | number | 是 | ★ 下单模式：`1` 门店自取 / `2` 外送（不传默认按自取处理） |
| amount | number | 否 | 前端展示用金额（服务端重算覆盖，仅作参考） |
| estimatedDeliveryTime | string | 否 | 预计送达时间（外送场景，`yyyy-MM-dd HH:mm:ss`） |
| deliveryStatus | number | 否 | 配送状态：1 立即送出 / 0 选择具体时间 |
| packAmount | number | 否 | 打包费（元，默认 0） |
| tablewareNumber | number | 否 | 餐具数量 |
| tablewareStatus | number | 否 | 餐具状态：1 按餐量提供 / 0 选择具体数量 |

请求参数样例（外送）：

```json
{
  "addressBookId": 3,
  "payMethod": 1,
  "remark": "少冰，谢谢",
  "deliveryMode": 2,
  "amount": 42.8
}
```

请求参数样例（门店自取）：

```json
{
  "payMethod": 1,
  "remark": "",
  "deliveryMode": 1,
  "amount": 18.9
}
```

### 15.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 下单结果（OrderSubmitVO） |
| \|- id | number | 是 | 订单 id |
| \|- orderNumber | string | 是 | 订单号 |
| \|- orderAmount | number | 是 | 订单金额（元，服务端重算：商品小计 + 外送费） |
| \|- orderTime | string | 是 | 下单时间 |
| \|- pickupNumber | string | 否 | ★ 取餐号（仅自取单返回，如 `A01`；外送单为 null） |
| \|- deliveryFee | number | 否 | ★ 外送费（元，仅外送单返回 5；自取单为 null） |

响应数据样例（门店自取）：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 104,
    "orderNumber": "202609260002",
    "orderAmount": 18.9,
    "orderTime": "2026-09-26 10:02:21",
    "pickupNumber": "A01",
    "deliveryFee": null
  }
}
```

响应数据样例（外送）：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 103,
    "orderNumber": "202609260001",
    "orderAmount": 42.8,
    "orderTime": "2026-09-26 09:30:12",
    "pickupNumber": null,
    "deliveryFee": 5
  }
}
```

## 15.2 订单支付（第一步：获取支付参数）

### 15.2.1 基本信息

- 请求路径：`/user/order/payment`
- 请求方式：PUT
- 接口描述：订单支付第一步：获取微信支付参数（模拟）。返回的参数在真实场景会被传入 `wx.requestPayment()` 拉起收银台。权限：需 C 端登录。
- 业务规则：★ **支付拆两步（课程契约，联调最容易懵的点）**：真实微信支付是 `PUT /payment` 拿参数 → `wx.requestPayment(参数)` 拉起收银台 → 用户付款 → 微信服务器回调后端 → 置已支付；山月骨架本地模拟：本接口只返回**占位参数**不改订单状态，「支付成功」后必须再调 15.3 `paySuccess`（等价于模拟微信回调入口）。两步缺一不可：只调第一步钱没付（状态未变），只调第二步等于跳过收银台。

### 15.2.2 请求参数

参数格式：application/json（OrdersPaymentDTO）

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| orderNumber | string | 是 | 订单号（15.1 返回的 orderNumber） |
| payMethod | number | 是 | 支付方式：1 微信 / 2 支付宝 |

请求参数样例：

```json
{
  "orderNumber": "202609260002",
  "payMethod": 1
}
```

### 15.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 支付参数（OrderPaymentVO，模拟占位值） |
| \|- nonceStr | string | 是 | 随机字符串 |
| \|- paySign | string | 是 | 签名 |
| \|- timeStamp | string | 是 | 时间戳 |
| \|- signType | string | 是 | 签名算法（如 RSA） |
| \|- packageStr | string | 是 | 统一下单返回的 prepay_id 参数值（微信原字段名 `package` 与 Java 关键字冲突，改为 packageStr） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "nonceStr": "5ea52ea55f0e4c3ab1e4f8e2c9d1b6a7",
    "paySign": "mockPaySignOfShanyueServer",
    "timeStamp": "1789287761",
    "signType": "RSA",
    "packageStr": "prepay_id=wx26100235882904a1b2c3d4e5f6g7"
  }
}
```

## 15.3 支付成功回调（第二步：模拟）

### 15.3.1 基本信息

- 请求路径：`/user/order/paySuccess`
- 请求方式：PUT
- 接口描述：订单支付第二步：模拟「微信支付成功回调」，将订单置为已支付（`payStatus 0 → 1`、写入 `checkoutTime`、状态推进到 `2 待接单`），并通过 WebSocket 向管理端推送来单提醒（type=1）。权限：需 C 端登录。
- 业务规则：★ `orderNumber` 用 **query** 传参（`PUT /user/order/paySuccess?orderNumber=xxx`，课程契约如此）——真实场景该请求由微信服务器发起（notify_url），山月由前端「支付成功」页发起；★ 幂等性：重复回调不应重复推进状态（按 payStatus 判断后再更新，面试讲点）。

### 15.3.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| orderNumber | string | 是 | query 参数，订单号 |

请求参数样例：

```
PUT /user/order/paySuccess?orderNumber=202609260002
```

### 15.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 15.4 查询历史订单

### 15.4.1 基本信息

- 请求路径：`/user/order/historyOrders`
- 请求方式：GET
- 接口描述：分页查询当前用户历史订单（订单列表页），支持按状态筛选。返回 PageResult（records 为 OrderVO）。权限：需 C 端登录。
- 业务规则：★ `userId` 由服务端从 token 强制注入查询条件——接口参数里没有 userId，用户只能看到自己的订单（防越权）。

### 15.4.2 请求参数

参数格式：query

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| page | number | 是 | 页码，从 1 开始 |
| pageSize | number | 是 | 每页条数 |
| status | number | 否 | 订单状态：1 待付款 / 2 待接单 / 3 已接单 / 4 派送中·待取餐 / 5 已完成 / 6 已取消，不传查全部 |

请求参数样例：

```
GET /user/order/historyOrders?page=1&pageSize=10&status=2
```

### 15.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 分页结果（PageResult） |
| \|- total | number | 是 | 总记录数 |
| \|- records | object[] | 是 | 订单列表（OrderVO，字段同 6.1.3） |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 2,
    "records": [
      {
        "id": 104,
        "number": "202609260002",
        "status": 2,
        "userId": 1,
        "addressBookId": null,
        "orderTime": "2026-09-26 10:02:21",
        "checkoutTime": "2026-09-26 10:02:35",
        "payMethod": 1,
        "payStatus": 1,
        "amount": 18.9,
        "remark": "",
        "userName": "山月客人",
        "phone": "13700002222",
        "address": "",
        "consignee": "",
        "cancelReason": null,
        "rejectionReason": null,
        "cancelTime": null,
        "estimatedDeliveryTime": null,
        "deliveryStatus": null,
        "deliveryTime": null,
        "packAmount": 0,
        "tablewareNumber": 0,
        "tablewareStatus": null,
        "deliveryMode": 1,
        "pickupNumber": "A01",
        "deliveryFee": 0,
        "orderDrinkes": "山月拿铁（大杯/冰）*1",
        "orderDetailList": [
          {
            "id": 3,
            "name": "山月拿铁（大杯/冰）",
            "orderId": 104,
            "drinkId": 5,
            "setmealId": null,
            "drinkFlavor": null,
            "specSelected": "[{\"groupId\":1,\"groupName\":\"杯型\",\"optionId\":2,\"optionName\":\"大杯\",\"priceDelta\":3}]",
            "number": 1,
            "amount": 18.9,
            "image": "http://localhost:8080/upload/latte-5.png"
          }
        ]
      }
    ]
  }
}
```

## 15.5 查询订单详情

### 15.5.1 基本信息

- 请求路径：`/user/order/orderDetail/{id}`
- 请求方式：GET
- 接口描述：按订单 id 查询订单详情（订单详情页：状态步骤条、取餐号大卡、收货信息、明细、金额）。返回 OrderVO（字段同 6.1.3）。权限：需 C 端登录。
- 业务规则：★ 校验订单归属：订单存在但不是当前用户的，按「订单不存在」处理（不暴露他人订单存在性，防越权三板斧之一）。

### 15.5.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 15.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 是 | 订单详情（OrderVO，字段同 6.1.3） |

响应数据样例（外送单，已取消场景）：

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 105,
    "number": "202609260003",
    "status": 6,
    "userId": 1,
    "addressBookId": 3,
    "orderTime": "2026-09-26 11:10:05",
    "checkoutTime": "2026-09-26 11:10:20",
    "payMethod": 1,
    "payStatus": 2,
    "amount": 23.9,
    "remark": "",
    "userName": "山月客人",
    "phone": "13900001111",
    "address": "杭州市西湖区文三路 100 号山月大厦 3 层",
    "consignee": "小月",
    "cancelReason": "桂花冰酿今日原料售罄",
    "rejectionReason": null,
    "cancelTime": "2026-09-26 11:15:00",
    "estimatedDeliveryTime": null,
    "deliveryStatus": 1,
    "deliveryTime": null,
    "packAmount": 0,
    "tablewareNumber": 0,
    "tablewareStatus": 1,
    "deliveryMode": 2,
    "pickupNumber": null,
    "deliveryFee": 5,
    "orderDrinkes": "桂花冰酿*1",
    "orderDetailList": [
      {
        "id": 4,
        "name": "桂花冰酿",
        "orderId": 105,
        "drinkId": 8,
        "setmealId": null,
        "drinkFlavor": null,
        "specSelected": null,
        "number": 1,
        "amount": 18.9,
        "image": "http://localhost:8080/upload/osmanthus-oolong.png"
      }
    ]
  }
}
```

## 15.6 取消订单

### 15.6.1 基本信息

- 请求路径：`/user/order/cancel/{id}`
- 请求方式：PUT
- 接口描述：用户取消订单，订单状态 → `6 已取消`。权限：需 C 端登录。
- 业务规则：★ 仅待付款（1）/待接单（2）可取消，其余状态返回 `code=0`（msg「当前状态不能取消」）；★ 已支付订单模拟原路退款（`payStatus 1 → 2`）；★ 校验订单归属。

### 15.6.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 15.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 15.7 再来一单

### 15.7.1 基本信息

- 请求路径：`/user/order/repetition/{id}`
- 请求方式：POST
- 接口描述：将历史订单的商品明细原样复刻回购物车（「再来一单」按钮），复刻完成后前端跳转菜单页/购物车。权限：需 C 端登录。
- 业务规则：★ 按 `order_detail` 逐行复制回购物车：`drinkId/setmealId/specSelected` 原样入车（含规格快照），行金额由服务端按当前价格重算——历史价格不入购物车（若商品已改价/停售，行金额反映的是最新价，服务端会校验起售状态）；★ 校验订单归属。

### 15.7.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 15.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 15.8 用户催单

### 15.8.1 基本信息

- 请求路径：`/user/order/reminder/{id}`
- 请求方式：GET
- 接口描述：用户催单：通过 WebSocket 向管理端推送催单提醒（type=2），前端弹「已提醒商家」提示。权限：需 C 端登录。
- 业务规则：★ 仅待接单（2）/已接单（3）状态可催单，其余返回 `code=0`（状态机校验）；★ 推送消息格式见模块 16；★ 校验订单归属。

### 15.8.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，订单 id |

### 15.8.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 响应码，1 成功 0 失败 |
| msg | string | 是 | 响应信息 |
| data | object | 否 | 无业务数据，固定为 null |

响应数据样例：

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

---

# 16. WebSocket 推送

## 16.1 连接方式

- 连接路径：`ws://localhost:8080/ws/{sid}`（如 `ws://localhost:8080/ws/1`；`{sid}` 为客户端标识，管理端固定用 `1`）
- 由**管理端**建立连接（登录成功后连接并保活），服务端通过该连接单向推送 JSON 文本消息。
- 使用场景：来单提醒（type=1）、催单提醒（type=2）。
- ★ 服务端点为 `@ServerEndpoint("/ws/{sid}")`，多客户端并发连接的会话表用 `ConcurrentHashMap` 管理（课程演示用 HashMap 有并发隐患，面试讲点）。

## 16.2 推送消息格式

服务端推送的消息为统一 JSON 结构（★ 与课程一致的**数字消息类型**，v1.6 的 NEW_ORDER/REMINDER 字符串枚举废弃）：

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| type | number | 是 | 消息类型：`1` 来单提醒 / `2` 催单提醒 |
| orderId | number | 是 | 订单 id |
| content | string | 是 | 提示文案，前端可直接 toast/语音播报 |

## 16.3 消息类型说明

| type | 含义 | 触发时机 | 前端处理建议 |
| --- | --- | --- | --- |
| 1 | 来单提醒 | ★ C 端支付成功回调（15.3 paySuccess）后推送 | 管理端播放提示音、刷新待接单列表与 6.2 角标 |
| 2 | 催单提醒 | C 端催单成功（15.8）后推送 | 管理端弹出催单提示、刷新对应订单 |

推送消息样例（来单提醒）：

```json
{
  "type": 1,
  "orderId": 103,
  "content": "您有新的订单，请及时处理"
}
```

推送消息样例（催单提醒）：

```json
{
  "type": 2,
  "orderId": 101,
  "content": "用户催单"
}
```

---

# 17. C端-优惠券与会员【扩展阶段】

> **扩展阶段：主链路联调完成后再做**（详表见扩展任务卡 T9）。优惠券是茶咖点单系统相对课程的**业务增量**（简历亮点）。模型分两层：`coupon`（券模板，管理端配置）与 `user_coupon`（用户券，领取后生成，含有效期与状态机）。用户券状态：`1` 可用 / `2` 已核销 / `3` 已过期；**每张模板限领 1 张**（防刷）；下单核销由服务端在同一事务中完成。需 C 端登录（token）。

## 17.1 接口概览

| 接口路径 | 请求方式 | 一句话说明 |
| --- | --- | --- |
| `/user/coupon/templates` | GET | 券广场：查询可领取的券模板列表（过滤已领、已下架、库存为 0） |
| `/user/coupon/receive/{templateId}` | POST | 领取优惠券：校验库存与「每模板限领 1 张」，生成用户券 |
| `/user/coupon/list` | GET | 我的优惠券列表：按状态（可用/已核销/已过期）分组查询 |
| `/user/coupon/usable` | GET | 下单可用券：按订单金额过滤门槛，返回本单可用的券及可抵扣金额 |
| `/user/member/summary` | GET | 会员概要：会员信息 + 券/积分汇总数字（会员中心页头部） |

> 本模块共 5 个接口，完整三小节详表见扩展任务卡 T9（dev-guide）。

---

# 18. 管理端-优惠券模板【扩展阶段】

> **扩展阶段：主链路联调完成后再做**（详表见扩展任务卡 T9）。管理端职责：券模板的配置与生命周期管理（创建 → 上架 → 下架 → 删除），库存（发行量）在创建时确定。需管理端登录（JWT；浏览器端经 nginx `/api/**` 前缀替换转发为 `/admin/**`）。

## 18.1 接口概览

| 接口路径 | 请求方式 | 一句话说明 |
| --- | --- | --- |
| `/admin/coupon/page` | GET | 优惠券模板分页查询（名称/状态筛选） |
| `/admin/coupon` | POST | 新增券模板（面额、门槛、发行量、有效期） |
| `/admin/coupon` | PUT | 修改券模板（仅未上架/库存未动时可改面额与库存） |
| `/admin/coupon/status/{status}` | POST | 上架/下架券模板（status：1 上架 / 0 下架，id 用 query 传） |
| `/admin/coupon` | DELETE | 删除券模板（已发行用户券的模板禁止物理删除） |

> 本模块共 5 个接口，完整三小节详表见扩展任务卡 T9（dev-guide）。

---

# 19. C端-积分【扩展阶段】

> **扩展阶段：主链路联调完成后再做**（详表见扩展任务卡 T10）。积分体系：消费得积分、每日连签奖励、积分兑换优惠券；积分流水表 `point_log` 记录每一笔增减（可追溯）。10.2 用户资料中的 `points` 即本模块的余额数据源。需 C 端登录（token）。

## 19.1 接口概览

| 接口路径 | 请求方式 | 一句话说明 |
| --- | --- | --- |
| `/user/point/summary` | GET | 积分概要：余额、累计获得/消耗、连签天数、今日是否已签 |
| `/user/point/checkin` | POST | 每日签到：每日首签 +N 分，连签有阶梯奖励（幂等：当日重复签到返回失败） |
| `/user/point/records` | GET | 积分明细：分页查询积分流水（获得/消耗，含来源说明） |
| `/user/point/exchanges` | GET | 兑换货架：可积分兑换的券列表（含所需积分与剩余可兑张数） |
| `/user/point/exchange/{templateId}` | POST | 积分兑换优惠券：同一事务「扣积分 + 生成用户券 + 扣库存」 |

> 本模块共 5 个接口，完整三小节详表见扩展任务卡 T10（dev-guide）。

---

# 20. C端-评价【扩展阶段】

> **扩展阶段：主链路联调完成后再做**（详表见扩展任务卡 T11）。评价模型：`order_review` 一行一条评价，**order_id 唯一**（一单一评）；仅已完成（status=5）订单可评价；星级 1~5，标签为固定枚举多选，文字 ≤ 50 字选填；商家回复（21.3）展示在评价下方，被隐藏（21.4）的评价不参与评分统计。需 C 端登录（token）。

## 20.1 接口概览

| 接口路径 | 请求方式 | 一句话说明 |
| --- | --- | --- |
| `/user/review` | POST | 提交评价：校验订单归属 + 状态机（status=5）+ 一单一评 + 星级/标签/字数合法性 |
| `/user/review/order/{orderId}` | GET | 查询某订单是否已评价：已评价返回详情，未评价 data=null（前端据此渲染评价按钮） |
| `/user/review/stats` | GET | 门店评价统计：平均分/好评率/星级分布/标签热度（已隐藏评价不参与统计） |
| `/user/review/mine` | GET | 我的评价列表（含商家回复，按时间倒序） |

> 本模块共 4 个接口，完整三小节详表见扩展任务卡 T11（dev-guide）。

---

# 21. 管理端-评价【扩展阶段】

> **扩展阶段：主链路联调完成后再做**（详表见扩展任务卡 T11）。管理端职责：查看 / 回复 / 隐藏恢复 / 统计，不代顾客写评价；待回复口径：≤ 3 星且未回复。需管理端登录（JWT；浏览器端经 nginx `/api/**` 前缀替换转发为 `/admin/**`）。

## 21.1 接口概览

| 接口路径 | 请求方式 | 一句话说明 |
| --- | --- | --- |
| `/admin/review/page` | GET | 评价分页查询（星级/展示状态/关键词筛选，倒序） |
| `/admin/review/stats` | GET | 评价统计：同 20.3 口径，额外返回待回复数（仪表盘口碑卡） |
| `/admin/review/reply` | PUT | 回复评价（一评一回复，可修改；内容 ≤ 50 字） |
| `/admin/review/visible/{visible}` | PUT | 隐藏/恢复评价（visible：0 隐藏 / 1 恢复，id 用 query 传；隐藏后不参与评分统计） |

> 本模块共 4 个接口，完整三小节详表见扩展任务卡 T11（dev-guide）。

---

# 附录

## A. 接口计数表

| 端 | 模块构成 | 接口数 |
| --- | --- | --- |
| 管理端主链路 | 员工 7 + 分类 6 + 饮品 7 + 套餐 6 + 公共 1 + 订单 8 + 数据统计 5 + 工作台 4 + 店铺 4 | 48 |
| 管理端扩展 | 优惠券模板 5 + 评价管理 4 | 9 |
| **管理端合计** | | **57** |
| C 端主链路 | 微信登录 2 + 店铺 1 + 分类与商品 4 + 购物车 4 + 地址簿 7 + 订单 8 | 26 |
| C 端扩展 | 优惠券与会员 5 + 积分 5 + 评价 4 | 14 |
| **C 端合计** | | **40** |
| WebSocket | 推送通道 `/ws/{sid}` | 1 |
| **总计** | 管理端 55 + C 端 40 + WebSocket 1 | **96** |

## B. 模块目录（速查）

| # | 模块 | 端 | 接口数 | 详表 |
| --- | --- | --- | --- | --- |
| 1 | 员工管理 | 管理端 | 7 | §1 |
| 2 | 分类管理 | 管理端 | 6 | §2 |
| 3 | 饮品管理 | 管理端 | 7 | §3 |
| 4 | 套餐管理 | 管理端 | 6 | §4 |
| 5 | 公共（图片上传） | 管理端 | 1 | §5 |
| 6 | 订单管理 | 管理端 | 8 | §6 |
| 7 | 数据统计 | 管理端 | 5 | §7 |
| 8 | 工作台 | 管理端 | 4 | §8 |
| 9 | 店铺管理 | 管理端 | 2 | §9 |
| 10 | 微信登录【v2.0 新增】 | C 端 | 2 | §10 |
| 11 | 店铺营业状态 | C 端 | 1 | §11 |
| 12 | 分类与商品 | C 端 | 4 | §12 |
| 13 | 购物车 | C 端 | 4 | §13 |
| 14 | 地址簿【v2.0 新增】 | C 端 | 7 | §14 |
| 15 | 订单（下单/支付/流转） | C 端 | 8 | §15 |
| 16 | WebSocket 推送 | 服务端→管理端 | 1 | §16 |
| 17 | 优惠券与会员【扩展阶段】 | C 端 | 5 | 任务卡 T9 |
| 18 | 优惠券模板【扩展阶段】 | 管理端 | 5 | 任务卡 T9 |
| 19 | 积分【扩展阶段】 | C 端 | 5 | 任务卡 T10 |
| 20 | 评价【扩展阶段】 | C 端 | 4 | 任务卡 T11 |
| 21 | 评价管理【扩展阶段】 | 管理端 | 4 | 任务卡 T11 |

## C. 修订历史

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0~v1.5 | 2026-09 | 初版建立管理端核心链路与 C 端匿名自取契约；金额单位「分」；阶段三加入报表扩展 |
| v1.6 | 2026-09-26 | 加入阶段四/五扩展（优惠券与会员、积分、评价体系、门店信息页），共 67 接口（管理端 42 + C 端 25） |
| v2.0 | 2026-09-26 | **对齐骨架工程 v2.0 增量，按课程工程模块划分整体重构**：①C 端改为微信登录（先登录后下单，与课程模式一致）②订单支持门店自取/外送双模式（deliveryMode 1/2、外送固定配送费 5 元 + 地址快照）③规格组 specGroups 结构化（替代 flavors 字符串）④月售 monthSales 动态统计 ⑤取餐号 pickupNumber（自取，Redis 按天 A01）；金额单位改为「元」；新增套餐管理/工作台/地址簿模块与按类型/按分类查询接口；WebSocket 消息类型改为数字 type=1/2；扩展模块（17~21）改为概览表，主链路联调完成后再做；合计 96 接口（管理端 55 + C 端 40 + WebSocket 1） |

> 文档版本：v2.0 ｜ 编写依据：骨架工程 `shanyue-server-skeleton`（v2.0 增量：controller/pojo 真值）+ 课程工程 sky-take-out（路径与参数基准） ｜ Base URL：`http://localhost:8080`
> 管理端接口共 55 个（主链路模块 1~9 共 46 个 + 扩展模块 18 共 5 个 + 扩展模块 21 共 4 个），C 端接口共 40 个（主链路模块 10~15 共 26 个 + 扩展模块 17/19/20 共 14 个），WebSocket 推送通道 1 个（模块 16），合计 96 个。
