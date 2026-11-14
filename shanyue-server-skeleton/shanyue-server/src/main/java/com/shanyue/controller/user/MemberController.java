package com.shanyue.controller.user;

import com.shanyue.context.BaseContext;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【C 端：会员等级】功能包 F2（对照 utils/mock-v2.js 的 /user/member/level 契约）
 * └ GET /user/member/level  当前用户成长值/等级/权益/等级时间轴/成长值规则
 *
 * 【本类知识点：怎么在 C 端控制器里拿"当前用户"？】
 * 拦截器 JwtTokenUserInterceptor 校验 token 成功后把 userId 放进了 ThreadLocal
 * （BaseContext.setCurrentId），Service/Controller 里 BaseContext.getCurrentId()
 * 随取随用 —— 不从前端传 userId，天然防越权（照抄 UserController.profile 的写法）。
 *
 * 【等级体系常量（与前端 mock-v2.js LEVELS 完全一致）】
 *   Lv1 月白·新芽 0 / Lv2 青绿·抽枝 200 / Lv3 松风·亭亭 600
 *   Lv4 山岚·成林 1200 / Lv5 入盏·知味 2000
 * 成长值规则：下单 1 元 = 2、每日签到 = 5、完成订单评价 = 10。
 * 演示数据 growth = 308 → Lv2 青绿·抽枝，距 Lv3 松风还差 292（progress = 27%）。
 */
@RestController("userMemberController")
@RequestMapping("/user/member")
@Tag(name = "C端-会员等级接口")
@Slf4j
public class MemberController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.MemberLevelService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private MemberLevelService memberLevelService;

    /**
     * 查询当前用户会员等级视图（等级英雄卡 + 权益 + 等级时间轴 + 成长值规则）
     *
     * TODO 换成真实现的步骤（预计 1 天）：
     *  1. 执行 sql/v2_features.sql：user 表 ALTER 加 growth 字段（方案 A，见该文件尾部说明）；
     *  2. shanyue-pojo 的 entity/User.java 补 private Integer growth; 字段
     *     （user 表加列后驼峰映射自动对应，UserMapper 无需改动）；
     *  3. MemberLevelService 里按 growth 落在哪个阈值区间算出当前等级/下一等级/进度，
     *     等级常量（5 档阈值与权益文案）建议单独放一个 MemberLevelConstant 常量类，
     *     别散落在代码里（对照 common/constant/MessageConstant 的做法）；
     *  4. 本类方法体改成：
     *       Long userId = BaseContext.getCurrentId();
     *       return Result.success(memberLevelService.levelView(userId));
     *
     * TODO 【落库方案对比：成长值存哪？】（毕设答辩常问，想清楚再动手）
     *  方案 A（推荐，简单直接）：user 表加冗余字段 growth，每次"产生成长值的行为"
     *    实时 UPDATE user SET growth = growth + ? WHERE id = ?。
     *    - 计算点只挂三处（与规则一一对应）：
     *      ① OrderServiceImpl.paySuccess：支付成功后 growth += amount * 2（下单 1 元 = 2）；
     *      ② CheckinController.sign 对应的 Service：签到成功后 growth += 5；
     *      ③ ReviewController.submit 对应的 Service：评价成功后 growth += 10。
     *    - 优点：查询 O(1)，代码量最小；缺点：冗余字段，算错一次就永久错（对账难）。
     *  方案 B（无冗余）：不存 growth，每次查询时聚合三张表：
     *      growth = SUM(已支付订单金额) * 2 + COUNT(签到记录) * 5 + COUNT(评价) * 10
     *    - 优点：数据永远自洽；缺点：订单表大了以后聚合慢，SQL 复杂。
     *  毕设推荐 A：行为入口就三个、都可控，冗余带来的风险远小于聚合带来的复杂度。
     *
     * 前端契约（utils/mock-v2.js memberLevelView）：
     *   {level, levelName, levelTitle, growth, nextLevel{level,name,minGrowth}|null,
     *    remainGrowth, progress, privileges[], levels[5], growthRules[3]}
     */
    @GetMapping("/level")
    @Operation(summary = "C端-查询会员等级视图")
    public Result<Map<String, Object>> level() {
        Long userId = BaseContext.getCurrentId();
        log.info("查询会员等级视图（桩）：userId={}", userId);

        // ---- 等级时间轴（5 档，文案与 mock-v2.js LEVELS 一字不差） ----
        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(levelDef(1, "月白", "新芽", 0,
                arr("注册即享每日签到赚积分", "生日月饮品券一张")));
        levels.add(levelDef(2, "青绿", "抽枝", 200,
                arr("签到积分翻倍（+10/天）", "会员券包抢先领")));
        levels.add(levelDef(3, "松风", "亭亭", 600,
                arr("高峰期优先出餐通道", "每月专属折扣券一张")));
        levels.add(levelDef(4, "山岚", "成林", 1200,
                arr("新品优先试饮资格", "免配送费特权")));
        levels.add(levelDef(5, "入盏", "知味", 2000,
                arr("专属茶席活动邀请", "专属客服通道")));

        // ---- 成长值规则（3 条） ----
        List<Map<String, Object>> growthRules = new ArrayList<>();
        growthRules.add(rule("下单消费 1 元 = 2 成长值", "order"));
        growthRules.add(rule("每日签到 = 5 成长值", "checkin"));
        growthRules.add(rule("完成订单评价 = 10 成长值", "review"));

        // ---- 演示数据：growth=308 → Lv2 青绿·抽枝，距松风差 292，进度 27% ----
        Map<String, Object> nextLevel = new LinkedHashMap<>();
        nextLevel.put("level", 3);
        nextLevel.put("name", "松风");
        nextLevel.put("minGrowth", 600);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("level", 2);
        view.put("levelName", "青绿");
        view.put("levelTitle", "抽枝");
        view.put("growth", 308);
        view.put("nextLevel", nextLevel);
        view.put("remainGrowth", 292);
        view.put("progress", 27);
        view.put("privileges", arr("签到积分翻倍（+10/天）", "会员券包抢先领"));
        view.put("levels", levels);
        view.put("growthRules", growthRules);
        return Result.success(view);
    }

    /** 构造一档等级定义 {level, name, title, minGrowth, privileges[]} */
    private static Map<String, Object> levelDef(Integer level, String name, String title,
                                                Integer minGrowth, List<String> privileges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("name", name);
        m.put("title", title);
        m.put("minGrowth", minGrowth);
        m.put("privileges", privileges);
        return m;
    }

    /** 构造一条成长值规则 {text, icon} */
    private static Map<String, Object> rule(String text, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("icon", icon);
        return m;
    }

    /** 可变参数转 List（LinkedHashMap 保持字段顺序；本类值皆非 null，用 ArrayList 即可） */
    private static List<String> arr(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) {
            list.add(item);
        }
        return list;
    }
}
