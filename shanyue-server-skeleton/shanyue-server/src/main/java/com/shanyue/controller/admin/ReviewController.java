package com.shanyue.controller.admin;

import com.shanyue.exception.BaseException;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 【管理端：评价管理】功能包 F6 · B 端（对照 admin 前端 /api/review/* 契约，模块 17【阶段五扩展】）
 * ┌ GET  /admin/review/page                评价分页（星级 score / 隐藏态 hidden / 关键词 keyword 筛选，时间倒序）
 * ├ GET  /admin/review/stats               评价统计（平均分/好评率/星级分布/标签热度 + 待回复数；已隐藏不参与）
 * ├ PUT  /admin/review/reply               回复评价（一评一回复、可修改；内容 ≤ 50 字）
 * └ PUT  /admin/review/visible/{visible}?id=  隐藏/恢复评价（隐藏后 C 端评价墙不展示、不参与统计）
 *
 * 【本类知识点：管理端只「治」不「写」】
 *  管理端职责 = 查看 / 回复 / 隐藏恢复 / 统计，绝不代顾客写评价（数据可信性的底线）。
 *  待回复口径：≤ 3 星且未回复（与仪表盘待办卡同口径）；好评率 = 4/5 星占比（百分数取整）。
 *  隐藏的语义是「软删除」：行还在（留痕可恢复），但 C 端墙与统计都当它不存在；
 *  生产环境的进阶做法是「先审后显」或敏感词机审（答辩可讲的治理思路）。
 *
 * TODO 换成真实现的步骤（预计 1 天）：
 *  1. 执行 sql/v2_features.sql 第 4 节：review 表已建（10 条种子与本桩返回一致；
 *     其中 reply / visible 两列就是本模块要写的状态位；C 端氛围种子的 order_id 为
 *     NULL → 本页 orderNumber/pickUpCode 展示为空，真实顾客提交的评价才有值）；
 *  2. 真实现的 B 端 page/stats：FROM review WHERE visible 按筛选拼接；orderNumber/
 *     pickUpCode 用 JOIN orders 快照（或提交评价时冗余进 review 表，二选一，答辩讲取舍）；
 *     C 端评价墙（user/ReviewController 的 /wall）同表 WHERE visible=1，并带上 reply 展示商家回复；
 *  3. reply 走 UPDATE review SET reply=? WHERE id=?（一评一回复：列只有一个，天然一回复，
 *     重复回复=覆盖修改，正好满足「可修改」的契约）；visible 同理 UPDATE visible=?；
 *  4. 两个字段拼接建议用 MyBatis 动态 SQL（<if test="score != null">…），样板照
 *     CategoryMapper.xml 写；分页照员工模块用 PageHelper。
 *
 * 本桩数据与 sql/v2_features.sql 的 review 种子一字不差（含 109 待回复、110 已隐藏两条
 * 演示行），页面在 mock 模式与真实后端之间无感切换。
 */
@RestController("adminReviewController")
@RequestMapping("/admin/review")
@Tag(name = "B端-评价管理接口")
@Slf4j
public class ReviewController {

    /** 低分阈值：≤ 3 星未回复视为「待回复」（与仪表盘待办同口径） */
    private static final int PENDING_SCORE_MAX = 3;

    /** 时间格式（与前端一致：yyyy-MM-dd HH:mm:ss） */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 内存评价表（桩演示够用；真实实现读 review 表） */
    private final List<Map<String, Object>> reviews = new CopyOnWriteArrayList<>(buildSeed());

    /**
     * 17.1 评价分页查询（关键词匹配内容/订单号/取餐号；hidden=1 查已隐藏、0 查正常、不传查全部）
     */
    @GetMapping("/page")
    @Operation(summary = "B端-评价分页查询")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Integer score,
                                            @RequestParam(required = false) Integer hidden,
                                            @RequestParam(required = false) String keyword) {
        log.info("评价分页查询（桩）：page={}, pageSize={}, score={}, hidden={}, keyword={}",
                page, pageSize, score, hidden, keyword);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : reviews) {
            if (score != null && score > 0 && ((Number) r.get("score")).intValue() != score) {
                continue;
            }
            if (hidden != null) {
                int wantVisible = hidden == 1 ? 0 : 1;
                if (((Number) r.get("visible")).intValue() != wantVisible) {
                    continue;
                }
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim().toLowerCase();
                String hay = (String.valueOf(r.get("content")) + r.get("orderNumber") + r.get("pickUpCode"))
                        .toLowerCase();
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            list.add(new LinkedHashMap<>(r));
        }
        // 时间倒序（最新在前）
        list.sort((a, b) -> String.valueOf(b.get("createTime")).compareTo(String.valueOf(a.get("createTime"))));

        int total = list.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<Map<String, Object>> records = from < to ? list.subList(from, to) : new ArrayList<>();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("records", records);
        return Result.success(data);
    }

    /**
     * 17.2 评价统计（已隐藏不参与统计；额外返回待回复数）
     */
    @GetMapping("/stats")
    @Operation(summary = "B端-评价统计")
    public Result<Map<String, Object>> stats() {
        log.info("评价统计（桩）");

        // 已隐藏不参与统计
        List<Map<String, Object>> visibleList = new ArrayList<>();
        for (Map<String, Object> r : reviews) {
            if (((Number) r.get("visible")).intValue() == 1) {
                visibleList.add(r);
            }
        }
        int total = visibleList.size();
        int[] distribution = new int[5];   // 下标 0 = 5 星 … 下标 4 = 1 星
        int sum = 0;
        int pendingReply = 0;
        for (Map<String, Object> r : visibleList) {
            int s = ((Number) r.get("score")).intValue();
            distribution[5 - s]++;
            sum += s;
            if (s <= PENDING_SCORE_MAX && String.valueOf(r.get("reply")).trim().isEmpty()) {
                pendingReply++;
            }
        }
        double avg = total == 0 ? 0 : Math.round(sum * 10.0 / total) / 10.0;
        int goodRate = total == 0 ? 0 : (int) Math.round((distribution[0] + distribution[1]) * 100.0 / total);

        // 标签热度（已隐藏不参与）
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> r : visibleList) {
            Object tags = r.get("tags");
            if (tags instanceof List) {
                for (Object t : (List<?>) tags) {
                    counter.merge(String.valueOf(t), 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> tagStats = new ArrayList<>();
        counter.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("tag", e.getKey());
                    t.put("count", e.getValue());
                    tagStats.add(t);
                });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avgScore", avg);
        data.put("total", total);
        data.put("goodRate", goodRate);
        data.put("pendingReply", pendingReply);
        data.put("distribution", Arrays.asList(distribution[0], distribution[1], distribution[2],
                distribution[3], distribution[4]));
        data.put("tagStats", tagStats);
        return Result.success(data);
    }

    /**
     * 17.3 回复评价（一评一回复，可修改；内容 ≤ 50 字）
     */
    @PutMapping("/reply")
    @Operation(summary = "B端-回复评价")
    public Result reply(@RequestBody Map<String, Object> body) {
        Long id = longOf(body.get("id"));
        String text = body.get("reply") == null ? "" : String.valueOf(body.get("reply")).trim();
        log.info("回复评价（桩）：id={}, reply={}", id, text);

        Map<String, Object> review = findById(id);
        if (review == null) {
            throw new BaseException("评价不存在");
        }
        if (text.isEmpty()) {
            throw new BaseException("回复内容不能为空");
        }
        if (text.length() > 50) {
            throw new BaseException("回复内容最多 50 字");
        }
        review.put("reply", text);
        return Result.success(null);
    }

    /**
     * 17.4 隐藏/恢复评价（隐藏后 C 端不展示、不参与统计）
     */
    @PutMapping("/visible/{visible}")
    @Operation(summary = "B端-隐藏/恢复评价")
    public Result visible(@PathVariable Integer visible, @RequestParam Long id) {
        log.info("隐藏/恢复评价（桩）：id={}, visible={}", id, visible);
        Map<String, Object> review = findById(id);
        if (review == null) {
            throw new BaseException("评价不存在");
        }
        review.put("visible", visible);
        return Result.success(null);
    }

    /* ============================ 桩内部工具（替换真实现时整段删除） ============================ */

    private Map<String, Object> findById(Long id) {
        for (Map<String, Object> r : reviews) {
            if (longOf(r.get("id")).equals(id)) {
                return r;
            }
        }
        return null;
    }

    private Long longOf(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v).replace(".0", ""));
    }

    /** N 天前的时间串（让评价墙永远「新鲜」） */
    private static String daysAgo(int days, int hour) {
        return LocalDateTime.now().minusDays(days).withHour(hour).withMinute(0).withSecond(0).format(FMT);
    }

    /**
     * 种子（与 sql/v2_features.sql 的 review 种子一字不差；氛围评价不挂订单 → orderNumber/pickUpCode 为空）
     * 109：3 星未回复 → 「待回复」演示行；110：2 星已隐藏 → 「隐藏态」演示行
     */
    private static List<Map<String, Object>> buildSeed() {
        List<Map<String, Object>> seed = new ArrayList<>();
        seed.add(row(101L, 5, "椰香很正，不齁甜，下午的续命全靠它。",
                Arrays.asList("椰香浓", "出餐快"), "谢谢喜爱～欢迎常来 🌼", 1, daysAgo(1, 10)));
        seed.add(row(102L, 5, "波波弹牙，黑糖挂壁看着就治愈，大杯满足。",
                Arrays.asList("分量足", "会回购"), "", 1, daysAgo(2, 14)));
        seed.add(row(103L, 4, "桂花香是真实桂花的那种香，就是杯子小了点。",
                Arrays.asList("香气足"), "", 1, daysAgo(3, 9)));
        seed.add(row(104L, 5, "双份浓缩够劲，加班救星，性价比无敌。",
                Arrays.asList("提神醒脑", "性价比"), "", 1, daysAgo(4, 16)));
        seed.add(row(105L, 4, "茶底清清爽爽，饭后喝刚好，回甘明显。",
                Arrays.asList("回甘明显", "清爽解腻"), "", 1, daysAgo(6, 15)));
        seed.add(row(106L, 5, "层次做得很好，甜度选半糖刚刚好，颜值也在线。",
                Arrays.asList("颜值高", "甜度刚好"), "哈哈欢迎常来打卡 ✨", 1, daysAgo(8, 11)));
        seed.add(row(107L, 5, "奶泡绵密入口很顺，热饮出品稳定。",
                Arrays.asList("口感顺滑"), "", 1, daysAgo(10, 13)));
        seed.add(row(108L, 4, "冰的更好喝，建议选去冰再少一档糖。",
                Arrays.asList("椰香浓"), "", 1, daysAgo(12, 17)));
        seed.add(row(109L, 3, "工作日下午人也这么多，等了小二十分钟才出餐，建议错峰。",
                Arrays.asList("等太久"), "", 1, daysAgo(5, 19)));
        seed.add(row(110L, 2, "餐车脏了都没擦……失望。",
                Arrays.asList("包装瑕疵"), "", 0, daysAgo(7, 12)));
        return seed;
    }

    /** 氛围种子不挂订单：orderId/orderNumber/pickUpCode 统一空值（真实顾客提交的评价才有值） */
    private static Map<String, Object> row(Long id, int score, String content,
                                          List<String> tags, String reply, int visible, String createTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("orderId", null);
        m.put("orderNumber", "");
        m.put("pickUpCode", "");
        m.put("score", score);
        m.put("tags", tags);
        m.put("content", content);
        m.put("reply", reply);
        m.put("visible", visible);
        m.put("createTime", createTime);
        return m;
    }
}
