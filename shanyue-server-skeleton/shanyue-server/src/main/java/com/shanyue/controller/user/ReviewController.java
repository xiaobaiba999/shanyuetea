package com.shanyue.controller.user;

import com.shanyue.context.BaseContext;
import com.shanyue.exception.BaseException;
import com.shanyue.result.PageResult;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【C 端：评价墙】功能包 F6（对照 utils/mock-v2.js 的 /user/review/* 契约）
 * ┌ GET  /user/review/wall?page&pageSize&drinkId  评价墙分页（可按饮品过滤）
 * ├ GET  /user/review/tags                        标签聚合 [{tag, count}]（标签筛选行）
 * └ POST /user/review/submit                      提交评价（仅已完成订单，一单一评，+10 积分）
 *
 * 【本类知识点：分页三件套 + MyBatis"一对多"映射】
 *  分页三件套（苍穹同款）：DTO 收 page/pageSize → PageHelper.startPage() 紧跟一条
 *  查询 SQL → new PageResult(page.getTotal(), page.getResult())。坑点：startPage 和
 *  查询之间不能插别的 SQL（ThreadLocal 只对紧跟着的第一条生效）。
 *  一对多映射：一条评价对应多个标签 —— MyBatis 的 collection 映射正好在这练
 *  （两种存法见 wall()/submit() 的 TODO，毕设二选一）。
 */
@RestController("userReviewController")
@RequestMapping("/user/review")
@Tag(name = "C端-评价墙接口")
@Slf4j
public class ReviewController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.ReviewService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private ReviewService reviewService;

    /** 评价时间格式（与 mock 的 dateTimeStr 同口径：yyyy-MM-dd HH:mm） */
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 演示用"已评订单号"（一单一评；真实实现换 review 表唯一索引 + 业务校验） */
    private final Set<Long> reviewedOrderIds = ConcurrentHashMap.newKeySet();

    /** 演示积分（真实实现读 user 表 points 字段，见 v2_features.sql 的 ALTER 说明） */
    private final AtomicInteger points = new AtomicInteger(120);

    /**
     * 评价墙分页查询（种子 8 条他人评价，按时间倒序；可按饮品过滤）
     *
     * TODO 换成真实现的步骤（预计 1.5 天）：
     *  1. 执行 sql/v2_features.sql 建 review 表（8 条种子已插入，与本桩返回一致）；
     *  2. entity/Review.java + ReviewMapper + ReviewMapper.xml；
     *     分页直接照抄既有 OrderController.historyOrders 的写法：
     *       PageHelper.startPage(page, pageSize);
     *       Page<Review> page1 = reviewMapper.page(drinkId);   // XML 里一条带动态 if 的 SELECT
     *       return new PageResult(page1.getTotal(), page1.getResult());
     *     （drinkId 非空时 WHERE drink_id = #{drinkId}，动态 SQL 的 <if> 标签练习点）
     *  3. 排序 ORDER BY create_time DESC（新评价在前，本人评价 submit 后自动出现在墙首）。
     *
     * 前端契约（utils/mock-v2.js）：
     *   {total, records:[{id, userId, nickName, avatarUrl, orderId, drinkId, drinkName,
     *     rating, content, tags[], images[], createTime, likeCount}]}
     */
    @GetMapping("/wall")
    @Operation(summary = "C端-评价墙分页查询")
    public Result<PageResult> wall(@RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer pageSize,
                                   @RequestParam(required = false) Long drinkId) {
        log.info("查询评价墙（桩）：page={}, pageSize={}, drinkId={}", page, pageSize, drinkId);

        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Map<String, Object>> all = buildSeedReviews();
        if (drinkId != null) {
            // 标签筛选行点某个饮品标签 → 只看该饮品的评价（真实实现换 SQL 的 WHERE drink_id = ?）
            all.removeIf(r -> ((Number) r.get("drinkId")).longValue() != drinkId);
        }
        // 按评价时间倒序（yyyy-MM-dd HH:mm 的字典序即时间序）
        all.sort((a, b) -> ((String) b.get("createTime")).compareTo((String) a.get("createTime")));

        int total = all.size();
        int from = Math.min((p - 1) * size, total);
        int to = Math.min(p * size, total);
        return Result.success(new PageResult(total, new ArrayList<>(all.subList(from, to))));
    }

    /**
     * 标签聚合（评价墙顶部标签筛选行：[{tag, count}]，按次数降序）
     *
     * TODO 换成真实现：一条 GROUP BY 就够（标签怎么存见下面的两种方案）——
     *   方案 1（tags 存 JSON 字符串，毕设推荐）：SQL 聚合费劲，Service 里把本页/全量
     *     评价的 tags 拆开计数（本桩 buildTags 的逻辑照搬即可），胜在建表简单；
     *   方案 2（review_tag 关联表）：SELECT tag, COUNT(*) FROM review_tag GROUP BY tag
     *     ORDER BY COUNT(*) DESC，一条 SQL 完事，代价是多一张表 + 多一次 JOIN。
     */
    @GetMapping("/tags")
    @Operation(summary = "C端-评价标签聚合")
    public Result<List<Map<String, Object>>> tags() {
        log.info("查询评价标签聚合（桩）");
        return Result.success(buildTags());
    }

    /**
     * 提交评价（写评价页：星级 + 标签多选 + 文字；一单一评；+10 积分）
     *
     * TODO 换成真实现的步骤：
     *  1. 三个校验（与 mock 口径一字不差，顺序也别换）：
     *     ① 订单存在且属于当前用户：ordersMapper.getById(orderId)，查不到 → "订单不存在"；
     *     ② 订单已完成：status != 5 → BaseException("订单完成后才能评价")
     *        （5=已完成，对照 orders 表状态注释；本人订单校验防越权，参照 OrderController.orderDetail）；
     *     ③ 未评过：reviewMapper.getByOrderId(orderId) 非空 →
     *        BaseException("该订单已评价过啦，一单一评")；
     *  2. 入参修剪（照抄 mock）：rating 夹在 1~5、content 截断 120 字、tags 最多 6 个；
     *  3. INSERT review 表：userId 取 BaseContext.getCurrentId()（别信前端），
     *     drinkId/drinkName 从订单明细第一行快照（orderDetailList[0]），nickName/avatar
     *     从 user 表冗余存一份 —— 评价墙展示不联 user 表（冗余字段换查询性能，schema.sql
     *     的 setmeal_drink 同款思路）；
     *  4. tags 怎么存（MyBatis 章节知识点的取舍，答辩常问）：
     *     方案 1（推荐）：review 表一个 tags 列存 JSON 字符串（如 ["椰香浓","出餐快"]），
     *       读出来 JSON.parseArray 回 List —— 一条表搞定，毕设性价比最高；
     *     方案 2：另建 review_tag 关联表（review_id, tag），ReviewVO 里
     *       private List<String> tags 用 <collection> 映射一次查出 —— 练 MyBatis
     *       一对多嵌套映射（对照苍穹 DishVO 里 flavors 的 collection 写法），
     *       标签聚合统计也更方便（见 tags() 的 TODO）。
     *  5. 一单一评的双保险：业务校验（第 1 步的 ③）+ review 表 uk_order_id 唯一索引兜底
     *     （并发双击漏过校验时插库报 Duplicate entry，全局异常处理器统一接住）；
     *  6. 奖励入账：user 表 points + 10，成长值 growth + 10（F2 方案 A 的三个计算点之一）；
     *  7. 返回 {pointsGained: 10, points: 新值}，前端 toast「评价成功 +10 积分」。
     *
     * 本桩的失败口径（与 mock-v2.js 一字不差）：
     *   未完成订单 →「订单完成后才能评价」；重复评价 →「该订单已评价过啦，一单一评」
     *   （桩无库可查，订单状态校验留给真实实现；一单一评用内存 Set 演示，重启即重置）
     */
    @PostMapping("/submit")
    @Operation(summary = "C端-提交订单评价")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        log.info("提交评价（桩）：userId={}, body={}", BaseContext.getCurrentId(), body);

        Object orderIdRaw = body.get("orderId");
        if (orderIdRaw == null) {
            throw new BaseException("订单不存在");
        }
        long orderId;
        try {
            orderId = Long.parseLong(String.valueOf(orderIdRaw));
        } catch (NumberFormatException e) {
            throw new BaseException("订单不存在");
        }
        if (reviewedOrderIds.contains(orderId)) {
            throw new BaseException("该订单已评价过啦，一单一评");
        }
        reviewedOrderIds.add(orderId);

        int newPoints = points.addAndGet(10);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pointsGained", 10);
        result.put("points", newPoints);
        return Result.success(result);
    }

    /* ============================== 桩数据工具方法（替换桩时整体删除） ============================== */

    /** 8 条他人评价种子（内容/标签与 mock-v2.js SEED_REVIEWS 一字不差；时间按"当前时间减 N 天"生成） */
    private static List<Map<String, Object>> buildSeedReviews() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(review(101L, 21L, "月下独酌", 101L, "生椰拿铁", 5,
                "椰香很正，不齁甜，下午的续命全靠它。", tagList("椰香浓", "出餐快"), now.minusDays(1), 23));
        list.add(review(102L, 22L, "山间客", 106L, "黑糖波波奶茶", 5,
                "波波弹牙，黑糖挂壁看着就治愈，大杯满足。", tagList("分量足", "会回购"), now.minusDays(2), 18));
        list.add(review(103L, 23L, "白鹭未眠", 102L, "桂花冰酿拿铁", 4,
                "桂花香是真实桂花的那种香，就是杯子小了点。", tagList("香气足"), now.minusDays(3), 9));
        list.add(review(104L, 24L, "南山南", 104L, "美式咖啡", 5,
                "双份浓缩够劲，加班救星，性价比无敌。", tagList("提神醒脑", "性价比"), now.minusDays(4), 31));
        list.add(review(105L, 25L, "青梅煮酒", 107L, "茉莉奶绿", 4,
                "茶底清清爽爽，饭后喝刚好，回甘明显。", tagList("回甘明显", "清爽解腻"), now.minusDays(6), 12));
        list.add(review(106L, 26L, "拾月", 103L, "焦糖玛奇朵", 5,
                "层次做得很好，甜度选半糖刚刚好，颜值也在线。", tagList("颜值高", "甜度刚好"), now.minusDays(8), 15));
        list.add(review(107L, 27L, "迟迟", 105L, "澳白", 5,
                "奶泡绵密入口很顺，热饮出品稳定。", tagList("口感顺滑"), now.minusDays(10), 7));
        list.add(review(108L, 28L, "雾岛", 101L, "生椰拿铁", 4,
                "冰的更好喝，建议选去冰再少一档糖。", tagList("椰香浓"), now.minusDays(12), 5));
        return list;
    }

    /** 标签聚合：[{tag, count}] 按次数降序，同次数按首次出现顺序（与 mock 统计同口径） */
    private static List<Map<String, Object>> buildTags() {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> r : buildSeedReviews()) {
            for (Object tag : (List<?>) r.get("tags")) {
                counter.merge(String.valueOf(tag), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> tags = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("tag", entry.getKey());
            t.put("count", entry.getValue());
            tags.add(t);
        }
        // List.sort 是稳定排序：次数相同的标签保持首次出现顺序（椰香浓 2 次居首）
        tags.sort((a, b) -> ((Number) b.get("count")).intValue() - ((Number) a.get("count")).intValue());
        return tags;
    }

    /** 构造一条评价（字段与 mock 的评价对象一一对应，orderId/images 为空的演示口径） */
    private static Map<String, Object> review(Long id, Long userId, String nickName, Long drinkId,
                                              String drinkName, Integer rating, String content,
                                              List<String> tags, LocalDateTime createTime, Integer likeCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("userId", userId);
        m.put("nickName", nickName);
        m.put("avatarUrl", "");
        m.put("orderId", null);
        m.put("drinkId", drinkId);
        m.put("drinkName", drinkName);
        m.put("rating", rating);
        m.put("content", content);
        m.put("tags", tags);
        m.put("images", new ArrayList<String>());
        m.put("createTime", createTime.format(MINUTE_FORMAT));
        m.put("likeCount", likeCount);
        return m;
    }

    private static List<String> tagList(String... tags) {
        List<String> list = new ArrayList<>();
        for (String tag : tags) {
            list.add(tag);
        }
        return list;
    }
}
