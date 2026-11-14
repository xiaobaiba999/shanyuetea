package com.shanyue.controller.user;

import com.shanyue.context.BaseContext;
import com.shanyue.exception.BaseException;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【C 端：每日签到】功能包 F5（对照 utils/mock-v2.js 的 /user/checkin/* 契约）
 * ┌ GET  /user/checkin/info  签到日历视图（本月签到日期/连续天数/累计天数/今日是否已签/积分）
 * └ POST /user/checkin/sign  签到（+5 积分；重复签到报「今日已签到，明天再来哦」）
 *
 * 【本类知识点：为什么签到天然适合 BITMAP？—— 黑马 Redis 实战「签到」章节的映射】
 *  一年 365 天 = 365 个"是/否"的布尔位。用字符串存（"2024-05-01,2024-05-02,..."）：
 *  一个用户一年几 KB；用 BITMAP 存：一天 1 个 bit，一年 46 字节，空间差几十倍，
 *  而且"本月签到日历 / 连续签到天数 / 共签几天"三条统计 Redis 一条命令全出来：
 *    SETBIT（写一天） / BITFIELD（读一段） / BITCOUNT（数一共几个 1）。
 *  这是"用对数据结构"的教科书案例，答辩讲空间复杂度对比很加分。
 *  完整替换步骤见 info()/sign() 方法头上的 TODO。
 */
@RestController("userCheckinController")
@RequestMapping("/user/checkin")
@Tag(name = "C端-每日签到接口")
@Slf4j
public class CheckinController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.CheckinService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private CheckinService checkinService;

    /**
     * 演示签到记录（yyyy-MM-dd 字符串集合；真实实现换 Redis BITMAP，见 info() 的 TODO）。
     * 种子与 mock-v2.js ensureV2 一致：昨天起连签 3 天 + 更早零散 2 天 → 签到后 streak=4。
     */
    private final Set<String> signedDates = ConcurrentHashMap.newKeySet();

    /** 演示积分（真实实现读 user 表 points 字段，见 v2_features.sql 的 ALTER 说明） */
    private final AtomicInteger points = new AtomicInteger(120);

    public CheckinController() {
        LocalDate today = LocalDate.now();
        signedDates.add(today.minusDays(1).toString()); // 昨天
        signedDates.add(today.minusDays(2).toString()); // 前天
        signedDates.add(today.minusDays(3).toString()); // 大前天（连签 3 天到此为止）
        signedDates.add(today.minusDays(6).toString()); // 6 天前（零散）
        signedDates.add(today.minusDays(9).toString()); // 9 天前（零散）
    }

    /**
     * 查询签到视图（签到日历页：统计条 + 当月日历 + 大按钮状态）
     *
     * TODO 【重点练习：BITMAP 三连】换成真实现的步骤（预计 1.5~2 天）：
     *  1. 签到记录 —— Redis BITMAP（对应黑马「签到」章节）：
     *       key    = "checkin:" + userId + ":" + yyyyMM        （一个月一个 key，如 checkin:1:202405）
     *       写一天 = SETBIT key offset 1，offset = dayOfMonth - 1（14 号 → offset 13，别忘减一）
     *       Java：stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
     *  2. 本月签到列表（signedDates）—— BITFIELD 取当月位段再遍历：
     *       BITFIELD checkin:1:202405 GET U{dayOfMonth} 0
     *       （取第 0 ~ dayOfMonth-1 位的无符号数；Java 用
     *        opsForValue().bitField(key, BitFieldSubCommands.create()
     *            .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0))）
     *       拿到十进制数转成二进制位串，从右往左第 i 位是 1 → 第 i 天签过 → 拼 yyyy-MM-dd 列表。
     *  3. 连续签到天数（streak）—— 同一个 BITFIELD 结果从右往左数连续 1 的个数：
     *       今天已签则从今天数起，否则从昨天数起（对照本桩 calcStreak 的逻辑）；
     *       黑马「连续签到签到天数」小节的经典位运算题：n & (n-1) 消最低位的 1，
     *       循环消几次就连续签了几天（想让 streak 从今天起算，先把今天的位抹掉再数）。
     *  4. 累计天数（totalDays）—— 跨月口径：BITCOUNT 多个月 key 累加（本演示只有当月数据，
     *     当月 BITCOUNT 即可）；也可对账 checkin_log 表 COUNT(*)。
     *  5. todaySigned —— GETBIT key (dayOfMonth-1) 返回 1 即已签。
     *
     * 前端契约（utils/mock-v2.js）：
     *   {signedDates[yyyy-MM-dd], streak, totalDays, todaySigned, points}
     */
    @GetMapping("/info")
    @Operation(summary = "C端-查询签到视图")
    public Result<Map<String, Object>> info() {
        log.info("查询签到视图（桩）：userId={}", BaseContext.getCurrentId());
        return Result.success(buildView(false, null));
    }

    /**
     * 签到（写一条记录 + 积分入账；返回签到后的最新视图）
     *
     * TODO 换成真实现的步骤：
     *  1. 防重：先 GETBIT 判今天是否已签（真实场景还要防并发双击 —— 用 SETBIT 的
     *     返回值：SETBIT 返回 0 说明原来没签（本次签到生效），返回 1 说明早就签过 → 抛
     *     BaseException("今日已签到，明天再来哦")，这一步天然原子，比 check-then-set 安全）；
     *  2. 积分入账：user 表 points + 5（entity/User.java 补 points 字段后
     *     userMapper.updateById 或一条 UPDATE ... SET points = points + 5）；
     *     进阶：Lv2 青绿以上权益是"签到积分翻倍（+10/天）"，先查成长值再决定加 5 还是 10；
     *  3. 成长值同步 +5（F2 会员等级方案 A 的三个计算点之一，见 MemberController 的 TODO）；
     *  4. 兜底对账（可选）：INSERT checkin_log（user_id, checkin_date, points_gained），
     *     生产用 BITMAP 替代表存储，本表只作对账/审计（见 v2_features.sql 表头注释）；
     *  5. 组装返回 {signedDates, streak, totalDays, todaySigned:true, gained:5, points}。
     *
     * 本桩的失败口径（与 mock-v2.js 一字不差）：重复签到 →「今日已签到，明天再来哦」
     */
    @PostMapping("/sign")
    @Operation(summary = "C端-每日签到")
    public Result<Map<String, Object>> sign() {
        log.info("每日签到（桩）：userId={}", BaseContext.getCurrentId());

        String today = LocalDate.now().toString();
        if (signedDates.contains(today)) {
            throw new BaseException("今日已签到，明天再来哦");
        }
        signedDates.add(today);
        int newPoints = points.addAndGet(5);
        log.info("签到成功（桩）：gained=5, points={}", newPoints);
        return Result.success(buildView(true, 5));
    }

    /* ============================== 桩数据工具方法（替换桩时整体删除） ============================== */

    /**
     * 组装签到视图。gained 为 null 时是 info 查询（todaySigned 按实际记录算）；
     * 非 null 时是 sign 成功后的视图（todaySigned 恒 true、多一个 gained 字段）。
     */
    private Map<String, Object> buildView(boolean signed, Integer gained) {
        LocalDate today = LocalDate.now();
        List<String> sorted = sortedDates();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("signedDates", sorted);
        view.put("streak", calcStreak(today));
        view.put("totalDays", sorted.size());
        view.put("todaySigned", signed || signedDates.contains(today.toString()));
        if (gained != null) {
            view.put("gained", gained);
        }
        view.put("points", points.get());
        return view;
    }

    /** 签到日期升序（yyyy-MM-dd 字典序即时间序，与 mock 的 sort() 同口径） */
    private List<String> sortedDates() {
        List<String> list = new ArrayList<>(signedDates);
        Collections.sort(list);
        return list;
    }

    /**
     * 连续签到天数：从今天（已签）或昨天起往前数连续命中的天数（与 mock 的 calcStreak 同口径）。
     * 真实实现换成 BITFIELD 位串的位运算（见 info() 的 TODO 第 3 步）。
     */
    private int calcStreak(LocalDate today) {
        LocalDate cur = signedDates.contains(today.toString()) ? today : today.minusDays(1);
        int streak = 0;
        while (signedDates.contains(cur.toString())) {
            streak++;
            cur = cur.minusDays(1);
        }
        return streak;
    }
}
