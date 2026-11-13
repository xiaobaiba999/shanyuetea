/* =========================================================
   山月茶咖 · API-SPEC v2.0 类型定义（对齐苍穹外卖接口模式）
   ---------------------------------------------------------
   请求路径约定：前端统一走 /api/** 前缀（vite/nginx 把 /api 重写为 /admin 转发 8080），
   因此本文件里不再出现 /admin 段。
   统一响应 Result：code=1 成功 / code=0 失败；价格单位：元（v2.1 起，与后端 BigDecimal 直出一致；v1.6 曾为分，已全部对齐）。
   订单状态机（苍穹 6 状态）：
   1 待付款 / 2 待接单 / 3 已接单(制作中) / 4 派送中(外送)·待取餐(自取) / 5 已完成 / 6 已取消
   ========================================================= */

/** 统一响应结构（与苍穹外卖一致） */
export interface Result<T = unknown> {
  code: 0 | 1
  msg: string
  data: T
}

/** 分页响应（苍穹口径：total + records） */
export interface PageResult<T> {
  total: number
  records: T[]
}

/* ---------------- 员工（/api/employee/*） ---------------- */
export interface LoginBody {
  username: string
  password: string
}

/** POST /employee/login → data：EmployeeLoginVO（注意 userName 大小写与苍穹一致） */
export interface LoginResult {
  id: number
  userName: string
  name: string
  token: string
}

export interface EmployeeVO {
  id: number
  name: string
  username: string
  phone: string
  /** '1' 男 / '0' 女 */
  sex: string
  /** 1 启用 / 0 禁用 */
  status: number
  idNumber?: string
  createTime: string
  updateTime: string
}

export interface EmployeeDTO {
  id?: number
  name: string
  username: string
  phone: string
  sex: string
  idNumber?: string
}

export interface EmployeePageQuery {
  name?: string
  page: number
  pageSize: number
}

/** PUT /employee/editPassword 入参（empId 由后端从登录态覆盖，前端只传两个密码） */
export interface PasswordEditBody {
  oldPassword: string
  newPassword: string
}

/* ---------------- 分类（/api/category/*） ---------------- */
export interface Category {
  id: number
  /** 1 菜品分类 / 2 套餐分类（苍穹口径） */
  type: 1 | 2
  name: string
  sort: number
  /** 1 启用 / 0 禁用 */
  status: number
  createTime: string
  updateTime: string
}

export interface CategoryDTO {
  id?: number
  type: 1 | 2
  name: string
  sort: number
}

export interface CategoryPageQuery {
  name?: string
  type?: 1 | 2
  page: number
  pageSize: number
}

/* ---------------- 饮品（/api/drink/*，Drink → Drink + 结构化规格组） ---------------- */
export interface SpecOption {
  id?: number
  name: string
  /** 加价（元），0 表示不加价 */
  priceDelta: number
  sort: number
}

export interface SpecGroup {
  id?: number
  name: string
  sort: number
  /** 山月 UI 扩展：1 必选 / 0 可选（苍穹 DTO 无此字段，后端会忽略，不影响联调） */
  required?: number
  /** 契约字段名：options（v2.0 起不再叫 specOptions） */
  options: SpecOption[]
}

/** DrinkVO：GET /drink/{id}、/drink/page 的行结构（含 categoryName + monthSales + specGroups 树） */
export interface Drink {
  id: number
  name: string
  categoryId: number
  /** 售价（元） */
  price: number
  image: string
  description: string
  /** 1 起售 / 0 停售 */
  status: number
  /** 月售（苍穹 v2.0：近 30 日销量口径，mock 里即累计销量） */
  monthSales: number
  /** 山月 UI 扩展：1 招牌推荐 / 0 普通 */
  featured?: number
  /** 分类名称（DrinkVO 冗余字段，列表页免二次查询） */
  categoryName?: string
  updateTime: string
  specGroups: SpecGroup[]
}

/** DrinkDTO：新增/修改整体提交（specGroups 先删旧后插新） */
export interface DrinkDTO {
  id?: number
  name: string
  categoryId: number
  /** 元 */
  price: number
  image: string
  description: string
  status?: number
  /** 山月 UI 扩展字段 */
  featured?: number
  specGroups: SpecGroup[]
}

export interface DrinkPageQuery {
  name?: string
  categoryId?: number
  status?: number
  page: number
  pageSize: number
}

/* ---------------- 套餐（/api/setmeal/*，本轮新增页面） ---------------- */
/** 套餐-饮品关联（SetmealDrink；amount 口径：price 为饮品原价，copies 为份数） */
export interface SetmealDrink {
  id?: number
  setmealId?: number
  drinkId: number
  /** 冗余饮品名称（提交时可只带 drinkId，后端回填） */
  name: string
  /** 饮品原价（元） */
  price: number
  copies: number
}

/** SetmealVO：分页/详情行结构 */
export interface Setmeal {
  id: number
  categoryId: number
  name: string
  /** 套餐价（元） */
  price: number
  image: string
  description: string
  /** 1 起售 / 0 停售 */
  status: number
  updateTime: string
  categoryName?: string
  setmealDrinkes: SetmealDrink[]
}

/** SetmealDTO：新增/修改提交（setmealDrinkes 整体覆盖） */
export interface SetmealDTO {
  id?: number
  categoryId: number
  name: string
  price: number
  image?: string
  description?: string
  status?: number
  setmealDrinkes: SetmealDrink[]
}

export interface SetmealPageQuery {
  name?: string
  categoryId?: number
  status?: number
  page: number
  pageSize: number
}

/* ---------------- 订单（/api/order/*，苍穹 6 状态 + 双配送模式） ---------------- */
/** 1 待付款 / 2 待接单 / 3 已接单(制作中) / 4 派送中·待取餐 / 5 已完成 / 6 已取消 */
export type OrderStatus = 1 | 2 | 3 | 4 | 5 | 6

/** 配送模式：1 自取 / 2 外送 */
export type DeliveryMode = 1 | 2

/** 订单明细（OrderDetail；amount = 单价，小计 = amount × number，苍穹口径） */
export interface OrderDetail {
  id: number
  /** 商品名称（drink/setmeal 通用冗余字段） */
  name: string
  drinkId?: number
  setmealId?: number
  /** 规格快照 JSON 串：[{"groupName":"杯型","optionName":"大杯","priceDelta":3}] */
  specSelected: string
  number: number
  /** 单价（元） */
  amount: number
  image: string
}

/** OrderVO（extends Orders + orderDetailList；v2.0 增加 deliveryMode/pickupNumber/deliveryFee 等） */
export interface OrderVO {
  id: number
  /** 订单号（苍穹字段名：number） */
  number: string
  status: OrderStatus
  /** 支付状态：0 未支付 / 1 已支付 */
  payStatus?: 0 | 1
  /** 订单总额（元；外送单 = 商品小计 + deliveryFee） */
  amount: number
  /** 配送模式：1 自取 / 2 外送 */
  deliveryMode: DeliveryMode
  /** 取餐号（自取单才有，如 A01） */
  pickupNumber?: string
  /** 配送费（元，外送单 5） */
  deliveryFee?: number
  /** 收货快照（外送单） */
  consignee?: string
  phone?: string
  deliveryAddress?: string
  remark: string
  cancelReason?: string
  rejectionReason?: string
  orderTime: string
  checkoutTime?: string
  /** 预计送达时间（外送单，可选） */
  estimatedDeliveryTime?: string
  orderDetailList: OrderDetail[]
}

export interface OrderSearchQuery {
  page: number
  pageSize: number
  /** 订单号（模糊） */
  number?: string
  phone?: string
  /** 订单状态，1~6（不传查全部） */
  status?: number
  /** 配送模式 1 自取 / 2 外送（不传查全部） */
  deliveryMode?: number
  /** 下单时间区间 'yyyy-MM-dd HH:mm:ss' */
  beginTime?: string
  endTime?: string
}

/** 订单统计（GET /order/statistics 三卡口径） */
export interface OrderStatistics {
  /** 待接单（status=2） */
  toBeConfirmed: number
  /** 已接单/制作中（status=3） */
  confirmed: number
  /** 派送中/待取餐（status=4） */
  deliveryInProgress: number
}

/* ---------------- 数据统计（/api/report/*，列表字段为逗号分隔字符串） ---------------- */

/** 营业额统计：dateList/turnoverList 均为逗号分隔字符串，如 "2026-9.1,2026-9.2" */
export interface TurnoverReportVO {
  dateList: string
  turnoverList: string
}

/** 用户统计 */
export interface UserReportVO {
  dateList: string
  totalUserList: string
  newUserList: string
}

/** 订单统计（有效订单口径：status ≠ 6 已取消） */
export interface OrdersReportVO {
  dateList: string
  orderCountList: string
  validOrderCountList: string
  totalOrderCount: number
  validOrderCount: number
  /** 完成率（0~1 小数，展示时 ×100） */
  orderCompletionRate: number
}

/** 销量 TOP10（nameList/numberList 逗号分隔字符串） */
export interface SalesTop10ReportVO {
  nameList: string
  numberList: string
}

/* ---------------- 工作台（/api/workspace/*） ---------------- */

/** 经营数据总览（金额单位：元） */
export interface BusinessDataVO {
  /** 营业额（今日已完成订单金额和） */
  turnover: number
  /** 有效订单数（status ≠ 6） */
  validOrderCount: number
  /** 订单完成率（0~1） */
  orderCompletionRate: number
  /** 平均客单价（元） */
  unitPrice: number
  /** 新增用户数 */
  newUsers: number
}

/** 订单概览 */
export interface OrdersOverviewVO {
  /** 待接单（status=2） */
  waitingOrders: number
  /** 进行中：制作中 + 派送中/待取餐（status=3/4） */
  deliveredOrders: number
  completedOrders: number
  cancelledOrders: number
  allOrders: number
}

/** 菜品/套餐总览 */
export interface GoodsOverviewVO {
  /** 起售中数量 */
  sold: number
  /** 停售数量 */
  discontinued: number
}

/* ---------------- 店铺（/api/shop/*） ---------------- */

/** 店铺设置（苍穹只管营业状态；notice 为山月扩展字段） */
export interface ShopSetting {
  /** 1 营业中 / 0 已打烊 */
  status: number
  notice: string
}

/* ---------------- 优惠券模板（管理端配置，模块 14【阶段五扩展】） ---------------- */
export interface CouponTemplate {
  id: number
  name: string
  /** 1 满减券 / 2 无门槛券 */
  type: 1 | 2
  /** 使用门槛（元），0 = 无门槛 */
  threshold: number
  /** 抵扣金额（元） */
  discount: number
  /** 领取后有效天数 */
  validDays: number
  /** 发放总量 */
  totalCount: number
  /** 剩余可领数量（totalCount - 已领取） */
  remainCount: number
  /** 1 发放中（C 端券广场可见）/ 0 已下架 */
  status: number
  /** 券说明，如「满 25 元减 4 元」 */
  description: string
  createTime: string
  updateTime: string
}

export interface CouponDTO {
  id?: number
  name: string
  type: 1 | 2
  /** 元，type=2 时固定 0 */
  threshold: number
  /** 元 */
  discount: number
  /** 领取后有效天数 */
  validDays: number
  /** 发放总量；修改时不可小于已领取张数 */
  totalCount: number
  /** 选填，不传由后端按类型自动生成 */
  description?: string
}

export interface CouponPageQuery {
  name?: string
  status?: number
  page: number
  pageSize: number
}

/* ---------------- 评价管理（管理端模块 17【阶段五扩展】） ---------------- */

/** 评价（order_review，一行对应一条 C 端评价；orderNumber/pickUpCode 为模块自身冗余快照） */
export interface Review {
  id: number
  orderId: number
  orderNumber: string
  pickUpCode: string
  /** 星级 1~5 */
  score: number
  /** 快捷标签（JSON 数组字符串存储，后端返回数组） */
  tags: string[]
  /** 文字评价（可为空） */
  content: string
  /** 商家回复（可为空） */
  reply: string
  /** 1 正常展示 / 0 已隐藏（不当言论） */
  visible: number
  createTime: string
}

/** 评价分页查询 */
export interface ReviewPageQuery {
  page: number
  pageSize: number
  /** 星级筛选（0 = 全部） */
  score?: number
  /** 关键词：匹配评价内容/订单号/取餐号 */
  keyword?: string
  /** 是否隐藏态筛选（0 = 正常 / 1 = 已隐藏；不传 = 全部） */
  hidden?: number
}

/** 门店评价统计（总览卡片 + 星级分布 + 标签热度） */
export interface ReviewStats {
  /** 平均分（1 位小数） */
  avgScore: number
  /** 评价总数（不含已隐藏） */
  total: number
  /** 好评率（4/5 星占比百分数） */
  goodRate: number
  /** 待回复数（低分未回复） */
  pendingReply: number
  /** 各星级数量，下标 0 = 5 星 … 下标 4 = 1 星 */
  distribution: number[]
  /** 标签热度（按次数降序） */
  tagStats: { tag: string; count: number }[]
}
