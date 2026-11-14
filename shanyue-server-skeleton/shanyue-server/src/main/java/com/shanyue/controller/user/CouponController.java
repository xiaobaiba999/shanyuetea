package com.shanyue.controller.user;

import com.shanyue.context.BaseContext;
import com.shanyue.exception.BaseException;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【C 端：券包】功能包 F4（对照 utils/mock-v2.js 的 /user/coupon/* 契约）
 * ┌ GET  /user/coupon/list            券包列表（券模板 + 本人领取/使用状态合并视图）
 * ├ POST /user/coupon/receive/{id}    领取券（一人一单 + 秒杀库存校验）
 * └ GET  /user/coupon/usable?amount=  下单页"选可用券"（满足门槛的可用券，抵扣额预计算）
 *
 * 【本类知识点：一人一单与超卖 —— 黑马 Redis 实战「优惠券秒杀」章节的映射（本工程核心练习）】
 *  场景：周五秒杀券库存 100 张，高并发下两个经典事故：
 *   事故 1「一人多单」：同一用户连点两次领券按钮，两个请求都通过"没领过"校验 → 领两张；
 *   事故 2「超卖」：库存剩 1，两个请求都读到 remain=1 → 都通过校验 → 扣成 -1。
 *  根因：check（查库存/查是否领过）与 set（扣库存/插记录）不是原子操作。
 *  修法（照黑马项目的两板斧）：
 *   板斧 1「一人一单」：Redis SETNX 天然原子 —— key 不存在才能设置成功；
 *   板斧 2「防超卖」：把"校验库存 → 扣库存 → 记录已领"三步合成一个 Lua 脚本，
 *           Redis 单线程执行脚本，中间不会插队别的请求（原子性）。
 *  数据库侧的最后一道防线：user_coupon 表 (user_id, coupon_id) 唯一索引兜底。
 *  完整替换步骤见 list()/receive()/usable() 三个方法头上的 TODO。
 */
@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Tag(name = "C端-券包接口")
@Slf4j
public class CouponController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.CouponService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private CouponService couponService;

    /**
     * 券模板样例（与 mock-v2.js SEED_COUPONS 一致；真实实现读 coupon 表）。
     * type：1 满减 | 2 折扣 | 3 限时秒杀满减（本演示用 seckill 布尔位区分秒杀场次）；
     * totalDays：领取后有效天数（mock 契约字段名 validDays 由 list 视图转换输出）；
     * stock/remain：只有秒杀券有意义（非秒杀券不限量，视图里输出 null）。
     */
    private static final List<Map<String, Object>> COUPON_TEMPLATES = buildTemplates();

    /** 秒杀券（id=2）实时库存演示值：37 张，领一张减一张（真实实现用 Lua 脚本原子扣减） */
    private final AtomicInteger seckillRemain = new AtomicInteger(37);

    /** 演示用户已领的券（真实实现读 user_coupon 表 + Redis）；并发容器，桩演示够用 */
    private final List<Map<String, Object>> userCoupons = new CopyOnWriteArrayList<>();

    /** 演示用户券自增 id（mock 的 couponSeq 从 100 起，新领的券编号 101、102…） */
    private final AtomicLong userCouponSeq = new AtomicLong(100);

    public CouponController() {
        // 种子与 mock-v2.js ensureV2 一致：
        // id 91：新人礼（券模板 1）已领、可用（status=1），27 天后过期；
        // id 92：老友回归（券模板 4）已领、已使用（status=2）。
        LocalDate today = LocalDate.now();
        userCoupons.add(userCoupon(91L, 1L, 1, today.plusDays(27).toString()));
        userCoupons.add(userCoupon(92L, 4L, 2, today.plusDays(18).toString()));
    }

    /**
     * 券包列表（券模板 + 本人领取状态合并视图）
     *
     * TODO 换成真实现的步骤（预计 2.5~3 天，含 Redis 秒杀练习）：
     *  1. 执行 sql/v2_features.sql：建 coupon（券模板）与 user_coupon（用户券）两张表，
     *     4 张券模板 + 2 条用户券种子已插入（演示用户 user_id=1）；
     *  2. entity/Coupon.java、entity/UserCoupon.java + CouponMapper/UserCouponMapper；
     *  3. CouponService.list(userId)：coupon 表全量（status=1 上架） LEFT JOIN 本人的
     *     user_coupon（一人一单，一条模板最多对应本人一条记录），拼合并视图：
     *       received = 本人记录是否存在；status = 记录状态（0 未领取|1 可用|2 已使用|3 已过期）；
     *       validDays 由模板 total_days 转换输出；秒杀 stock/remain 输出实时值（Redis）。
     *  4. 过期状态（status=3）：懒加载口径 —— list/usable 查询时比对 expire_date 顺手置 3；
     *     进阶做法是 @Scheduled 每天零点批量刷（对照苍穹订单状态定时任务的写法）。
     */
    @GetMapping("/list")
    @Operation(summary = "C端-查询券包列表")
    public Result<List<Map<String, Object>>> list() {
        log.info("查询券包列表（桩）：userId={}", BaseContext.getCurrentId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : COUPON_TEMPLATES) {
            long couponId = ((Number) c.get("id")).longValue();
            Map<String, Object> owned = findOwned(couponId);
            boolean seckill = (Boolean) c.get("seckill");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.get("id"));
            item.put("name", c.get("name"));
            item.put("type", c.get("type"));
            item.put("thresholdAmount", c.get("thresholdAmount"));
            item.put("discountAmount", c.get("discountAmount"));
            item.put("discountRate", c.get("discountRate"));
            item.put("validDays", c.get("totalDays"));
            item.put("seckill", seckill);
            item.put("stock", seckill ? c.get("stock") : null);
            item.put("remain", seckill ? seckillRemain.get() : null);
            item.put("received", owned != null);
            item.put("userCouponId", owned == null ? null : owned.get("id"));
            item.put("status", owned == null ? 0 : owned.get("status"));
            item.put("expireDate", owned == null ? null : owned.get("expireDate"));
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 领取优惠券（一人一单 + 秒杀库存校验）
     *
     * TODO 【重点练习：黑马「优惠券秒杀」超卖章节的两板斧】替换步骤：
     *  1. 一人一单 —— SETNX（对应黑马「秒杀优惠券」防止一人多单小节）：
     *       key   = "seckill:coupon:uid:" + couponId + ":" + userId
     *       SETNX 成功 = 之前没领过（占坑成功）；失败 = 已领过 → 抛
     *       BaseException("您已领取过该券，一人限领一张")
     *     注意：占坑成功后如果后续步骤失败（没库存/插库失败），记得 DEL 掉 key 释放坑位，
     *     否则用户永远领不了这张券（黑马项目里专门踩过这个坑）。
     *  2. 防超卖 —— Lua 脚本三步合一（对应黑马「Lua 脚本草稿」章节）：
     *     只有秒杀券需要走 Lua（普通券无库存概念，SETNX + 插库即可）。脚本草稿：
     *       -- KEYS[1]=库存 key "seckill:stock:coupon:{couponId}"
     *       -- KEYS[2]=一人一单 key "seckill:coupon:uid:{couponId}:{userId}"
     *       -- ARGV[1]=userId
     *       if (redis.call('EXISTS', KEYS[2]) == 1) then return 1 end      -- 已领过
     *       local stock = tonumber(redis.call('GET', KEYS[1]))
     *       if (stock == nil or stock <= 0) then return 2 end              -- 没库存
     *       redis.call('DECR', KEYS[1])
     *       redis.call('SET', KEYS[2], ARGV[1])
     *       return 0                                                       -- 成功
     *     Java 侧（DefaultRedisScript + StringRedisTemplate，工程已有 RedisTemplateConfiguration）：
     *       Long r = stringRedisTemplate.execute(SECKILL_SCRIPT, keys, userId);
     *       r==1 → "您已领取过该券，一人限领一张"；r==2 → "手慢了，本场秒杀券已抢光"
     *  3. 落库：INSERT user_coupon（user_id, coupon_id, status=1, receive_time=now,
     *     expire_date=今天+total_days）；唯一索引 uk_user_coupon(user_id, coupon_id)
     *     作为最后防线 —— 并发漏网时插库抛 Duplicate entry，全局异常处理器统一兜底
     *     （对照 GlobalExceptionHandler 的 SQLIntegrityConstraintViolationException 分支）。
     *  4. Redis 与数据库的一致性：秒杀库存以 Redis 为准（预热：券上架时 SET 库存），
     *     user_coupon 表只做凭证与对账 —— 这是黑马项目的标准架构，答辩按这个讲。
     *
     * 本桩的两种失败口径（与 mock-v2.js 一字不差）：
     *   已领过 →「您已领取过该券，一人限领一张」；秒杀没库存 →「手慢了，本场秒杀券已抢光」
     */
    @PostMapping("/receive/{id}")
    @Operation(summary = "C端-领取优惠券")
    public Result receive(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        log.info("领取优惠券（桩）：userId={}, couponId={}", userId, id);

        Map<String, Object> coupon = findTemplate(id);
        if (coupon == null) {
            throw new BaseException("券不存在或已下架");
        }
        if (findOwned(id) != null) {
            throw new BaseException("您已领取过该券，一人限领一张");
        }
        boolean seckill = (Boolean) coupon.get("seckill");
        if (seckill && seckillRemain.get() <= 0) {
            throw new BaseException("手慢了，本场秒杀券已抢光");
        }
        if (seckill) {
            seckillRemain.decrementAndGet();
        }
        int totalDays = (Integer) coupon.get("totalDays");
        userCoupons.add(userCoupon(userCouponSeq.incrementAndGet(), id, 1,
                LocalDate.now().plusDays(totalDays).toString()));
        return Result.success(null);
    }

    /**
     * 下单页查询可用券（满足门槛的 status=1 用户券，抵扣额/文案预计算，按抵扣额降序）
     *
     * TODO 换成真实现的步骤：
     *  1. CouponService.usable(userId, amount)：查本人 status=1 的 user_coupon，
     *     逐张比对 amount >= threshold_amount，不满足门槛的直接过滤（前端灰按钮都懒得显示）；
     *  2. 抵扣额预计算（type=1 固定满减额；type=2 按 amount * (100 - discountRate) / 100
     *     四舍五入到分，对照 mock 的 fenRound）；
     *  3. discountText 文案：type=1 "满30减8"、type=2 "满0打8.8折"（前端票面直接渲染）。
     *
     * TODO 【下单用券：挂在 OrderServiceImpl.submit 的事务里】（联调口径，别单独做接口）：
     *   前端 mock.js 的 applyCoupon 挂钩就是这套流程的镜像：
     *     页面在 /user/order/submit 请求体里带 userCouponId + couponBaseAmount
     *     （couponBaseAmount = 商品金额，不含配送费 —— 外送费不参与满减门槛与折扣基准）；
     *   submit 事务内四步：校验券归属/状态（status=1 且属于当前用户） →
     *     校验门槛（商品金额 >= threshold_amount，否则抛"未满足用券门槛"） →
     *     算抵扣（type=1 固定额 / type=2 按商品金额打折） →
     *     UPDATE user_coupon SET status=2, used_time=now；订单 amount = 商品金额 - 抵扣 + 配送费。
     *   注意回滚边界：扣券与建单在同一个 @Transactional 里，下单失败券不能被烧掉。
     */
    @GetMapping("/usable")
    @Operation(summary = "C端-查询下单可用券")
    public Result<List<Map<String, Object>>> usable(@RequestParam(required = false) Double amount) {
        double baseAmount = amount == null ? 0.0 : amount;
        log.info("查询可用券（桩）：userId={}, amount={}", BaseContext.getCurrentId(), baseAmount);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> uc : userCoupons) {
            if (((Number) uc.get("status")).intValue() != 1) {
                continue; // 只看可用券（1）；已使用（2）/已过期（3）不出现
            }
            Map<String, Object> c = findTemplate(((Number) uc.get("couponId")).longValue());
            if (c == null) {
                continue;
            }
            if (baseAmount < ((Number) c.get("thresholdAmount")).doubleValue()) {
                continue; // 门槛不满足不返回（与 mock 口径一致）
            }
            result.add(usableView(uc, c, baseAmount));
        }
        // 按抵扣额降序：最划算的券排最前（mock 同款排序）
        result.sort((a, b) -> Double.compare(((Number) b.get("discountAmount")).doubleValue(),
                ((Number) a.get("discountAmount")).doubleValue()));
        return Result.success(result);
    }

    /* ============================== 桩数据工具方法（替换桩时整体删除） ============================== */

    /** 构造券模板（字段与 mock-v2.js SEED_COUPONS 一一对应） */
    private static Map<String, Object> coupon(Long id, String name, Integer type, Integer thresholdAmount,
                                              Integer discountAmount, Integer discountRate, Integer totalDays,
                                              boolean seckill, Integer stock, Integer remain) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type);
        m.put("thresholdAmount", thresholdAmount);
        m.put("discountAmount", discountAmount);
        m.put("discountRate", discountRate);
        m.put("totalDays", totalDays);
        m.put("seckill", seckill);
        m.put("stock", stock);
        m.put("remain", remain);
        return m;
    }

    private static List<Map<String, Object>> buildTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        templates.add(coupon(1L, "新人礼·满30减8", 1, 30, 8, 100, 30, false, 0, 0));
        templates.add(coupon(2L, "周五秒杀·满20减10", 1, 20, 10, 100, 7, true, 100, 37));
        templates.add(coupon(3L, "会员日·全场88折", 2, 0, 0, 88, 15, false, 0, 0));
        templates.add(coupon(4L, "老友回归·满50减15", 1, 50, 15, 100, 30, false, 0, 0));
        return templates;
    }

    /** 构造一条演示用户券 {id, couponId, status, expireDate} */
    private static Map<String, Object> userCoupon(Long id, Long couponId, Integer status, String expireDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("couponId", couponId);
        m.put("status", status);
        m.put("expireDate", expireDate);
        return m;
    }

    /** 按券模板 id 找模板（真实实现换成 CouponMapper.getById） */
    private static Map<String, Object> findTemplate(Long couponId) {
        for (Map<String, Object> c : COUPON_TEMPLATES) {
            if (((Number) c.get("id")).longValue() == couponId) {
                return c;
            }
        }
        return null;
    }

    /** 找本人领过的某张券（一人一单：一条模板最多一条记录；真实实现换 UserCouponMapper） */
    private Map<String, Object> findOwned(long couponId) {
        for (Map<String, Object> uc : userCoupons) {
            if (((Number) uc.get("couponId")).longValue() == couponId) {
                return uc;
            }
        }
        return null;
    }

    /**
     * 拼下单页可用券视图 {userCouponId, couponId, name, type, thresholdAmount,
     * discountAmount, discountText, expireDate}，抵扣额与文案按当前订单金额预计算。
     */
    private static Map<String, Object> usableView(Map<String, Object> uc, Map<String, Object> c, double baseAmount) {
        int type = ((Number) c.get("type")).intValue();
        int threshold = ((Number) c.get("thresholdAmount")).intValue();
        Number discount = calcDiscount(c, baseAmount);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCouponId", uc.get("id"));
        m.put("couponId", c.get("id"));
        m.put("name", c.get("name"));
        m.put("type", type);
        m.put("thresholdAmount", threshold);
        m.put("discountAmount", discount);
        if (type == 2) {
            // 折扣券文案：满X打Y折（mock 口径：88 折 → discountRate/10 = 8.8）
            m.put("discountText", "满" + threshold + "打" + (((Number) c.get("discountRate")).doubleValue() / 10) + "折");
        } else {
            // 满减券文案：满X减Y
            m.put("discountText", "满" + threshold + "减" + c.get("discountAmount"));
        }
        m.put("expireDate", uc.get("expireDate"));
        return m;
    }

    /** 折扣金额计算（type=1 满减固定额 / type=2 折扣率，基准为商品金额；对照 mock 的 calcDiscount+fenRound） */
    private static Number calcDiscount(Map<String, Object> coupon, double baseAmount) {
        int type = ((Number) coupon.get("type")).intValue();
        if (type == 2) {
            int rate = ((Number) coupon.get("discountRate")).intValue();
            double discount = baseAmount * (100 - rate) / 100.0;
            return Math.round(discount * 100.0) / 100.0;
        }
        return (Number) coupon.get("discountAmount");
    }
}
