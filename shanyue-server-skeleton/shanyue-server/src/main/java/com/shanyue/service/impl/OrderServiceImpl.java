package com.shanyue.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shanyue.constant.MessageConstant;
import com.shanyue.context.BaseContext;
import com.shanyue.dto.OrdersCancelDTO;
import com.shanyue.dto.OrdersConfirmDTO;
import com.shanyue.dto.OrdersPageQueryDTO;
import com.shanyue.dto.OrdersPaymentDTO;
import com.shanyue.dto.OrdersRejectionDTO;
import com.shanyue.dto.OrdersSubmitDTO;
import com.shanyue.entity.AddressBook;
import com.shanyue.entity.OrderDetail;
import com.shanyue.entity.Orders;
import com.shanyue.entity.ShoppingCart;
import com.shanyue.entity.User;
import com.shanyue.exception.OrderBusinessException;
import com.shanyue.exception.ShoppingCartBusinessException;
import com.shanyue.mapper.AddressBookMapper;
import com.shanyue.mapper.OrderDetailMapper;
import com.shanyue.mapper.OrdersMapper;
import com.shanyue.mapper.ShoppingCartMapper;
import com.shanyue.mapper.UserMapper;
import com.shanyue.result.PageResult;
import com.shanyue.service.OrderService;
import com.shanyue.vo.OrderPaymentVO;
import com.shanyue.vo.OrderStatisticsVO;
import com.shanyue.vo.OrderSubmitVO;
import com.shanyue.vo.OrderVO;
import com.shanyue.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 【本类知识点：订单服务（C 端完整范本 + 管理端全套）—— 本任务最大最核心的一个类】
 *
 * 一、下单六步法（submit，顺序即业务）：
 *   ① 查购物车（空车不能下单）→ ② 校验地址（外送必须有有效地址簿）→ ③ 服务端重算金额
 *   → ④ 存订单主表 → ⑤ 存订单明细（快照）→ ⑥ 清购物车。①②③⑥ 之外的两步都是 insert，
 *   所以整个方法 @Transactional —— 要么全成，要么全不成（不能出现"订单建了明细没建"）。
 *
 * 二、金额安全（与购物车同一条铁律）：
 *   订单金额 = Σ(购物车行单价 × 数量) + 外送费(5 元，仅外送)。单价在加购时就已服务端重算，
 *   下单时再合计一遍；前端 OrdersSubmitDTO.amount 仅作展示参考，服务端一律覆盖。
 *
 * 三、山月 v2.0 双模式（deliveryMode）：
 *   1 门店自取：地址可不传；生成取餐号 pickupNumber——Redis key
 *      "shanyue:pickup:yyyy-MM-dd" 按天自增（INCR 原子操作，并发不重号），格式 A01/A02…
 *   2 外送：必须有 addressBookId；把 consignee/phone/address 快照进订单表
 *      （地址以后被改/删也不影响历史订单，快照思想与订单明细一致）。
 *
 * 四、状态机（苍穹 6 态，常量定义在 Orders）：
 *   1 待付款 → 2 待接单 → 3 已接单 → 4 派送中(外送)/待取餐(自取) → 5 已完成；任意环节可 → 6 已取消。
 *   【教学取舍】本骨架"提交订单"直接进入 2 待接单（payStatus=0 未支付）：
 *   模拟支付场景下用户总是"秒支付"，独立待付款单价值不大；真实电商应是 1 → 支付回调 → 2，
 *   见 paySuccess 的教学注释。取消接口对 1/2 都放行，兼容两种口径。
 *
 * 五、越权防护三板斧（C 端订单类接口通用）：
 *   1. userId 一律取 BaseContext，不取前端参数；
 *   2. 查单后校验归属：不是你的订单一律按"订单不存在"处理（不暴露他人订单存在性）；
 *   3. 状态流转前先校验当前状态，非法流转抛 OrderBusinessException(ORDER_STATUS_ERROR)。
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    /** 外送费（山月固定 5 元；真实业务可按距离阶梯计费——报表里能看到它的独立明细） */
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("5");

    /** 取餐号 Redis key 前缀（完整 key：shanyue:pickup:2026-09-26，按天自增、跨天自然清零） */
    private static final String PICKUP_KEY_PREFIX = "shanyue:pickup:";

    /** 订单号随机后缀生成器（时间戳保证趋势递增唯一，3 位随机防同毫秒碰撞） */
    private static final Random RANDOM = new Random();

    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private WebSocketServer webSocketServer;

    /* ==================== C 端 ==================== */

    /**
     * 提交订单（六步范本）
     */
    @Transactional
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        // 当前下单人（ThreadLocal，绝不取前端传参）
        Long userId = BaseContext.getCurrentId();

        // -------- 第 1 步：查购物车，空车不能下单 --------
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.listByUserId(userId);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // -------- 第 2 步：下单模式校验 + 外送地址校验 --------
        // 不传默认按"门店自取"处理（DTO 注释同口径）；传了就必须是 1 或 2
        Integer deliveryMode = ordersSubmitDTO.getDeliveryMode() == null
                ? Orders.DELIVERY_MODE_PICKUP : ordersSubmitDTO.getDeliveryMode();
        if (!deliveryMode.equals(Orders.DELIVERY_MODE_PICKUP) && !deliveryMode.equals(Orders.DELIVERY_MODE_DELIVERY)) {
            throw new OrderBusinessException(MessageConstant.DELIVERY_MODE_ERROR);
        }

        AddressBook addressBook = null;
        if (deliveryMode.equals(Orders.DELIVERY_MODE_DELIVERY)) {
            // 外送：必须有地址簿 id（前端没传直接拦）
            if (ordersSubmitDTO.getAddressBookId() == null) {
                throw new OrderBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            // 外送：按 id 查地址，查不到（或不是本人的地址）同抛——
            // 归属校验放在这里：拿别人的地址簿 id 下单属于越权，一律视为"地址不存在"
            addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
            if (addressBook == null || !addressBook.getUserId().equals(userId)) {
                throw new OrderBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            // （课程对照：课程这里还会调百度地图 API 校验"是否超出配送范围"，checkOutOfRange，
            //   本骨架省略——思路是店铺地址与收货地址分别解析经纬度再算路线距离，超 5km 拒单）
        }

        // -------- 第 3 步：服务端重算金额（明细合计 + 外送费） --------
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ShoppingCart cart : shoppingCartList) {
            // 每行小计 = 单价 × 数量（单价在加购时已按"基础价+Σ加价"服务端重算过）
            subtotal = subtotal.add(cart.getAmount().multiply(new BigDecimal(cart.getNumber())));
        }
        boolean isDelivery = deliveryMode.equals(Orders.DELIVERY_MODE_DELIVERY);
        BigDecimal deliveryFee = isDelivery ? DELIVERY_FEE : BigDecimal.ZERO;
        BigDecimal amount = subtotal.add(deliveryFee);

        // -------- 第 4 步：组装并保存订单主表 --------
        Orders orders = new Orders();
        // 手动逐字段拷贝（不用 BeanUtils 的原因：Orders.packAmount/tablewareNumber 是 int 原始类型，
        // DTO 里是 Integer，null 直接反射拷贝会拆箱异常——手动判空给默认值更稳）
        orders.setUserId(userId);
        orders.setNumber(String.valueOf(System.currentTimeMillis()) + String.format("%03d", RANDOM.nextInt(1000)));
        // 山月骨架：提交即进入"待接单"（payStatus 仍是未支付）；真实电商为 1 待付款 → 支付回调后置 2
        orders.setStatus(Orders.TO_BE_CONFIRMED);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setPayMethod(ordersSubmitDTO.getPayMethod());
        orders.setRemark(ordersSubmitDTO.getRemark());
        orders.setEstimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime());
        orders.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus());
        orders.setTablewareStatus(ordersSubmitDTO.getTablewareStatus());
        orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null ? 0 : ordersSubmitDTO.getTablewareNumber());
        orders.setPackAmount(ordersSubmitDTO.getPackAmount() == null ? 0 : ordersSubmitDTO.getPackAmount());
        orders.setOrderTime(LocalDateTime.now());
        orders.setAmount(amount);
        orders.setDeliveryMode(deliveryMode);
        orders.setDeliveryFee(deliveryFee);
        // 自取单：生成取餐号（Redis INCR 原子自增，A01/A02…，按天换 key 自然重置）
        if (!isDelivery) {
            orders.setPickupNumber(generatePickupNumber());
        }
        // 外送单：收货信息快照进订单表（consignee/phone/address 三件套）
        if (isDelivery && addressBook != null) {
            orders.setAddressBookId(addressBook.getId());
            orders.setConsignee(addressBook.getConsignee());
            orders.setPhone(addressBook.getPhone());
            orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName()
                    + addressBook.getDistrictName() + addressBook.getDetail());
        }
        // 用户名快照（历史订单页展示"谁下的单"，即使用户以后改名也不影响）
        User user = userMapper.getById(userId);
        if (user != null) {
            orders.setUserName(user.getName());
        }
        ordersMapper.insert(orders);
        log.info("订单已保存：number={}，amount={}，deliveryMode={}，pickupNumber={}",
                orders.getNumber(), orders.getAmount(), orders.getDeliveryMode(), orders.getPickupNumber());

        // -------- 第 5 步：保存订单明细（购物车行 → 明细行，快照含 specSelected） --------
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            // 同名属性拷贝：name/image/drinkId/setmealId/drinkFlavor/specSelected/number/amount
            BeanUtils.copyProperties(cart, orderDetail);
            // 关键回填：明细挂到刚插入的订单 id 上（useGeneratedKeys 的功劳）
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // -------- 第 6 步：清空购物车 --------
        shoppingCartMapper.deleteByUserId(userId);

        // -------- 返回 VO（金额是服务端重算后的值，前端照它收款） --------
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .pickupNumber(orders.getPickupNumber())
                .deliveryFee(orders.getDeliveryFee())
                .build();
    }

    /**
     * 订单支付（模拟版）
     *
     * 【真实微信支付流程（教学）】
     *   1. 后端调微信"统一下单"API（jsapi 方式）：带 商户订单号 out_trade_no / 金额 / openid，
     *      微信返回 prepay_id；
     *   2. 后端用商户密钥对 appId/时间戳/随机串/prepay_id 二次签名，生成 OrderPaymentVO 下发；
     *   3. 小程序 wx.requestPayment(该 VO) 拉起收银台，用户输密码；
     *   4. 微信异步回调 notify_url（POST XML/JSON）——后端必须【验签】（防伪造回调）、
     *      校验订单金额一致（防少付）、幂等处理（重复通知只生效一次）；
     *   5. 回调处理成功后微信才停止重试通知。
     * 【山月模拟】没有商户号，第 1~3 步改为返回占位参数；状态变更只发生在 paySuccess
     * （由前端"支付成功"后显式调用，等价于第 4 步回调的模拟入口），本方法不改任何状态。
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        // 按订单号查单：不存在 → 拒付；不是本人的单 → 同样按"不存在"处理（防越权探测）
        Orders ordersDB = ordersMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 已支付的单不允许再次拉起支付（幂等第一道闸，状态变更侧还有第二道）
        if (!ordersDB.getPayStatus().equals(Orders.UN_PAID)) {
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_PAID);
        }

        log.info("【模拟支付】生成占位支付参数：orderNumber={}", ordersDB.getNumber());
        // 时间戳（秒级字符串，微信要求）、随机串（防重放，真实场景参与签名）
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        return OrderPaymentVO.builder()
                .timeStamp(timeStamp)
                .nonceStr(nonceStr)
                .paySign("mock-sign")                       // 真实场景：商户密钥计算的签名，前端不参与
                .signType("RSA")                            // 微信支付 V3 的签名算法（V2 为 MD5/HMAC-SHA256）
                .packageStr("prepay_id=mock_" + ordersDB.getNumber()) // 真实场景：统一下单返回的 prepay_id
                .build();
    }

    /**
     * 支付成功回调（模拟）：校验归属 + 幂等 → 置已支付 → WebSocket 来单提醒
     */
    @Override
    public void paySuccess(String orderNumber) {
        // 1、按订单号查单
        Orders ordersDB = ordersMapper.getByNumber(orderNumber);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 2、归属校验：只能"支付成功"自己的订单（越权一律按订单不存在处理）
        if (!ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 3、幂等校验：payStatus==0 才处理，重复回调/重复点击直接返回（真实回调场景必做）
        if (!ordersDB.getPayStatus().equals(Orders.UN_PAID)) {
            log.info("订单已支付，忽略本次支付成功通知：{}", orderNumber);
            return;
        }

        /*
         * 4、更新支付状态：payStatus 0→1、写结账时间；status 保持 2（待接单）不变——
         *    【教学注释·真实回调】微信回调进来要做三件事：
         *      ① 验签（用微信平台证书/商户密钥验证报文签名，防伪造回调直接改单）；
         *      ② 金额核对（回调金额必须与订单 amount 一致，防"1 分钱买大单"）；
         *      ③ 幂等（唯一约束/状态机，重复通知只生效一次）。
         *    验签通过后才做与本方法相同的"置已支付"更新；本骨架把验签省略为可信入口。
         */
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        ordersMapper.update(orders);

        // 5、WebSocket 来单提醒（type=1）：推给所有在线的管理端页面，前端按 type 播提示音
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", ordersDB.getId());
        map.put("type", 1);
        map.put("content", "订单号：" + ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        log.info("支付成功，已推送来单提醒：{}", orderNumber);
    }

    /**
     * C 端：分页查询历史订单（自动填当前用户 id，带订单明细）
     */
    @Override
    public PageResult page(OrdersPageQueryDTO ordersPageQueryDTO) {
        // C 端强制只查自己的订单（覆盖前端可能传的 userId，防越权看别人的订单列表）
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> pageInfo = ordersMapper.page(ordersPageQueryDTO);

        // 逐单补明细与快照（PageHelper 分页后每页最多 10 条，循环查明细可接受；
        // 数据量大时可改 join 一次查回后内存分组——教学进阶）
        List<OrderVO> orderVOList = new ArrayList<>();
        if (pageInfo != null && !pageInfo.isEmpty()) {
            for (Orders order : pageInfo) {
                orderVOList.add(buildOrderVO(order));
            }
        }
        return new PageResult(pageInfo.getTotal(), orderVOList);
    }

    /**
     * C 端：查询订单详情（校验订单归属）
     */
    @Override
    public OrderVO getorderDetailById(Long id) {
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 归属校验：不是本人的订单按"不存在"处理（不暴露他人订单）
        if (!orders.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return buildOrderVO(orders);
    }

    /**
     * C 端：取消订单
     * 规则：仅本人订单；状态 1（待付款）/ 2（待接单）可取消（3 起商家已接单制作，需走退款协商）；
     * 取消后：status=6、cancelReason="用户取消"、cancelTime=now；已支付单模拟退款。
     */
    @Override
    public void cancel(Long id) {
        Orders orderDB = ordersMapper.getById(id);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 仅本人订单可取消
        if (!orderDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 状态校验：只有待付款(1)/待接单(2)可取消
        // （注意 Integer 比较用 equals：== 只比较引用，超出 -128~127 缓存范围必错）
        if (!orderDB.getStatus().equals(Orders.PENDING_PAYMENT)
                && !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        /*
         * 已支付的订单取消 → 需要退款。
         * 【教学注释·退款 TODO】真实实现：调用微信退款 API（WeChatPayUtil.refund）
         *   参数：商户订单号 / 商户退款单号（可用订单号）/ 退款金额 / 原订单金额；
         *   退款也有异步回调，成功后才把 payStatus 置 2（退款中→已退款可再加中间态）。
         * 本骨架模拟：直接把 payStatus 置为 REFUND(2) 表示"已原路退回"。
         */
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            // TODO 真实退款：weChatPayUtil.refund(orderDB.getNumber(), orderDB.getNumber(), 退款金额, 原金额)
            log.info("【模拟退款】订单已支付，取消后原路退回：{}", orderDB.getNumber());
            orders.setPayStatus(Orders.REFUND);
        }

        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        ordersMapper.update(orders);
    }

    /**
     * C 端：再来一单（把订单明细复刻回购物车，含规格快照）
     */
    @Override
    public void repetition(Long id) {
        // 明细挂在被复刻的订单上 → 先校验订单归属（别人的单不能"再来一单"）
        Orders orderDB = ordersMapper.getById(id);
        if (orderDB == null || !orderDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 1、查订单明细（下单瞬间的快照：名称/单价/规格都在）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 2、明细 → 购物车行（照工程：金额/数量原样复刻，specSelected 快照原样带回，
        //    用户再次进规格弹窗前就能"一键重买"）
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart cart = new ShoppingCart();
            cart.setUserId(userId);
            cart.setName(orderDetail.getName());
            cart.setImage(orderDetail.getImage());
            cart.setDrinkId(orderDetail.getDrinkId());
            cart.setSetmealId(orderDetail.getSetmealId());
            cart.setDrinkFlavor(orderDetail.getDrinkFlavor());
            cart.setSpecSelected(orderDetail.getSpecSelected());
            cart.setNumber(orderDetail.getNumber());
            cart.setAmount(orderDetail.getAmount());
            cart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(cart);
        }

        // 3、整批插入购物车
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * C 端：催单（WebSocket 推送 type=2 给管理端）
     */
    @Override
    public void reminder(Long id) {
        // 查订单 + 归属校验（只能催自己的单）
        Orders orderDB = ordersMapper.getById(id);
        if (orderDB == null || !orderDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // （课程对照：课程会再校验订单状态——只有待接单/已接单允许催，本骨架从简不卡状态，
        //   TODO 进阶：status ∉ {2,3} 时抛 ORDER_STATUS_ERROR）

        // WebSocket 消息体约定：orderId / type（1 来单提醒 2 用户催单）/ content
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", orderDB.getId());
        map.put("type", 2);
        map.put("content", "用户催单，订单号：" + orderDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        log.info("已推送催单提醒：orderId={}", id);
    }

    /* ==================== 管理端 ==================== */

    /**
     * 管理端：各状态订单数量统计（三张卡片：待接单/已接单/派送中·待取餐）
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 三条 count(*) 简单查询（订单表数据量大时可合并为一条
        // "select sum(status=2),sum(status=3),sum(status=4)"——教学进阶）
        Integer toBeConfirmed = ordersMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = ordersMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = ordersMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 管理端：订单条件搜索（不强制 userId，全量查询；支持状态/单号/手机号/模式/时间段筛选）
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = ordersMapper.page(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page.getResult() != null && !page.getResult().isEmpty()) {
            for (Orders orders : page.getResult()) {
                OrderVO orderVO = buildOrderVO(orders);
                // 课程同款：拼"订单饮品"摘要字符串（"拿铁*1; 美式*2;"），管理端列表不展开明细也能看懂卖了什么
                String orderDrinks = orderVO.getOrderDetailList().stream()
                        .map(detail -> detail.getName() + "*" + detail.getNumber() + ";")
                        .collect(Collectors.joining(" "));
                orderVO.setOrderDrinks(orderDrinks);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 管理端：根据订单 id 查询详情（无需归属校验——管理员可见全部订单）
     */
    @Override
    public OrderVO searchDetail(Long id) {
        Orders orders = ordersMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return buildOrderVO(orders);
    }

    /**
     * 管理端：接单（status 2 → 3）
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orderDB = ordersMapper.getById(ordersConfirmDTO.getId());
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有"待接单"能接（Integer 比较用 equals，== 在缓存范围外必错）
        if (!orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.CONFIRMED)
                .build();
        ordersMapper.update(orders);
    }

    /**
     * 管理端：拒单（status 2 → 6，记录拒单原因；已支付单需退款）
     */
    @Override
    public void reject(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orderDB = ordersMapper.getById(ordersRejectionDTO.getId());
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有"待接单"可以拒（接了单再拒=先取消再拒，流程上不允许）
        if (!orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        // 已支付的单被拒 → 钱要退给用户（模拟退款 + TODO 真实微信退款 API，见 cancel 的注释）
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            // TODO 真实退款：weChatPayUtil.refund(orderDB.getNumber(), orderDB.getNumber(), 退款金额, 原金额)
            log.info("【模拟退款】拒单退款：{}", orderDB.getNumber());
            orders.setPayStatus(Orders.REFUND);
        }

        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        ordersMapper.update(orders);
    }

    /**
     * 管理端：派送订单（status 3 → 4；外送单="开始配送"，自取单="出餐待取"）
     */
    @Override
    public void delivery(Long id) {
        Orders orderDB = ordersMapper.getById(id);
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            // 只有"已接单"能进入派送/待取餐
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        ordersMapper.update(orders);
    }

    /**
     * 管理端：完成订单（status 4 → 5）
     */
    @Override
    public void complete(Long id) {
        Orders orderDB = ordersMapper.getById(id);
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            // 只有"派送中/待取餐"能完成
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.COMPLETED)
                .build();
        ordersMapper.update(orders);
        // （教学提示：订单完成是"月售统计/报表"的统计口径起点——status=5 的明细才计入销量）
    }

    /**
     * 管理端：取消订单（商家主动取消，原因由商家填写；区别于 C 端 cancel 的固定"用户取消"）
     */
    @Override
    public void cancelByAdmin(OrdersCancelDTO ordersCancelDTO) {
        Orders orderDB = ordersMapper.getById(ordersCancelDTO.getId());
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 商家可取消：待接单(2)/已接单(3)（已派送/已完成对顾客侧已有承诺，不允许直接取消）
        if (!orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)
                && !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        // 已支付 → 退款（模拟 + TODO，同 C 端 cancel）
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            // TODO 真实退款：weChatPayUtil.refund(...)
            log.info("【模拟退款】商家取消订单退款：{}", orderDB.getNumber());
            orders.setPayStatus(Orders.REFUND);
        }

        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason() == null || ordersCancelDTO.getCancelReason().isEmpty()
                ? "商家取消" : ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        ordersMapper.update(orders);
    }

    /* -------------------- 私有辅助方法 -------------------- */

    /**
     * 生成自取餐号：Redis key "shanyue:pickup:yyyy-MM-dd" 按天自增 → A01、A02…
     * 为什么用 Redis INCR？——原子操作，并发下单也不会重号；key 带日期，跨天自动从 1 重新计数。
     */
    private String generatePickupNumber() {
        String key = PICKUP_KEY_PREFIX + LocalDate.now();
        Long seq = redisTemplate.opsForValue().increment(key);
        // A%02d：A + 至少两位数字（不足补零），99 单以后自然变成 A100（教学：%02d 是"最少两位"不是"最多两位"）
        return String.format("A%02d", seq);
    }

    /**
     * 组装订单 VO：订单字段 + 明细列表 + 地址/用户名快照兜底
     * （照工程：orders 表里的 address/phone/consignee 是下单时的快照；老数据可能为空，
     *   兜底从地址簿现查——注意：地址簿后来改过的话，兜底值与下单快照会不一致，这正是
     *   "为什么必须做快照"的活教材）
     */
    private OrderVO buildOrderVO(Orders order) {
        OrderVO orderVO = new OrderVO();
        // Orders → OrderVO 同名属性拷贝（OrderVO 继承 Orders，全字段可拷）
        BeanUtils.copyProperties(order, orderVO);

        // 1、订单明细列表（含 specSelected 规格快照）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
        orderVO.setOrderDetailList(orderDetailList);

        // 2、收货信息兜底（快照字段为空时从地址簿补）
        if (order.getAddressBookId() != null) {
            AddressBook ab = addressBookMapper.getById(order.getAddressBookId());
            if (ab != null) {
                if (orderVO.getAddress() == null) {
                    orderVO.setAddress(ab.getProvinceName() + ab.getCityName()
                            + ab.getDistrictName() + ab.getDetail());
                }
                if (orderVO.getPhone() == null) {
                    orderVO.setPhone(ab.getPhone());
                }
                if (orderVO.getConsignee() == null) {
                    orderVO.setConsignee(ab.getConsignee());
                }
            }
        }

        // 3、用户名兜底（老订单没存 userName 时从 user 表补）
        if (orderVO.getUserName() == null && order.getUserId() != null) {
            User u = userMapper.getById(order.getUserId());
            if (u != null) {
                orderVO.setUserName(u.getName());
            }
        }
        return orderVO;
    }
}
