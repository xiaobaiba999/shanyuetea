package com.shanyue.controller.user;

import com.shanyue.context.BaseContext;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【C 端：茶小月 AI 点单】功能包 F7 —— 毕设主菜（对照 utils/mock-v2.js 的 /user/ai/* 契约）
 * ┌ GET  /user/ai/suggests  快捷提问 chips（5 条字符串）
 * └ POST /user/ai/order     AI 点单对话（请求体 {message, history, lastRecommendId}）
 *
 * 【本类知识点：接大模型的三个工程化要点】
 *  1. 结构化输出：让模型"必须按 JSON 格式说话"（写进系统提示词 + 温度调低），
 *     后端才好程序化解析 reply/recommend —— 自由文本是没法挂"加购按钮"的；
 *  2. 解析要防御：模型偶尔会皮一下（包一层 ```json 代码块、多说一句话），
 *     所以解析失败必须有降级路径（本桩的关键词匹配版就是降级逻辑）；
 *  3. 上下文管理：history 由前端带回最近几轮对话，服务端拼进 messages，
 *     模型才有"记性"（换一个/刚才说的那杯）；拼多少轮要设上限，token 就是钱。
 *  三级接入方案见 order() 方法头上的 TODO。
 */
@RestController("userAiOrderController")
@RequestMapping("/user/ai")
@Tag(name = "C端-AI点单接口")
@Slf4j
public class AiOrderController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.AiOrderService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private AiOrderService aiOrderService;

    /**
     * 快捷提问 chips（与 mock-v2.js AI_SUGGESTS 一字不差）。
     * TODO 真实实现可以从 drink 表按月售 Top 生成"来杯今日招牌"这类动态文案，
     *      静态配置也完全够用（本演示就当一条不变的运营配置）。
     */
    private static final List<String> SUGGESTS = buildSuggests();

    /**
     * 菜单摘要（与 mock-v2.js 的 db.drinkes 中 status=1 的 7 款一致；108 柚见鲜茶售罄不参与推荐）。
     * spec 字段对应 mock 的 specOf（展示用的默认规格建议）。
     * TODO 真实实现：drinkMapper + setmealMapper 查 status=1 的在售商品拼菜单，
     *      这份摘要同时服务于"喂给大模型的提示词"与"降级关键词匹配"两个用途。
     */
    private static final List<Map<String, Object>> MENU = buildMenu();

    /** 预算识别（「15元以内」「20块」；对照 mock 的 /(\d+)\s*(元|块|rmb|RMB)/） */
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d+)\\s*(元|块|[rR][mM][bB])");

    /**
     * 快捷提问列表（聊天页底部 chips，点击即发送）
     */
    @GetMapping("/suggests")
    @Operation(summary = "C端-AI点单快捷提问")
    public Result<List<String>> suggests() {
        log.info("查询 AI 点单快捷提问（桩）：userId={}", BaseContext.getCurrentId());
        return Result.success(SUGGESTS);
    }

    /**
     * AI 点单对话：用户一句自然语言 → 小月回复 + 推荐一杯（或一个套餐）
     *
     * 请求体契约（utils/mock-v2.js 文件头）：
     *   {message: "刚吃完火锅，解腻一点的",
     *    history: [{role:'user'|'assistant', content:'...'}, ...],   // 最近几轮对话，拼大模型上下文用
     *    lastRecommendId: 101}                                       // 上一轮推荐的饮品 id（"换一个"时避开）
     *
     * TODO 【大模型接入三级方案】（答辩亮点，量力选级）：
     *
     * ── 系统提示词（可直接用，放 messages[0]，role=system）────────────────────────
     *  你是「茶小月」，山月茶咖的宋制茶童点单官，自称小月、称呼用户为您，温柔茶语，
     *  只推荐菜单内的饮品与套餐。回复控制在 60 字内，必须且只能输出一个 JSON 对象，
     *  不要输出 JSON 以外的任何文字，格式：
     *  {"reply":"给用户的一句话","recommend":{...}|null}
     *  recommend 为 null（纯寒暄），或包含字段：
     *  kind("drink"|"setmeal")/drinkId/setmealId/name/price/description/monthSales/
     *  reason(推荐理由)/specSuggestion(规格建议)/image。
     *  菜单 JSON 摘要（拼在提示词后面）：
     *  {"drinks":[
     *   {"id":101,"name":"生椰拿铁","price":15.9,"monthSales":356,"description":"厚椰乳遇上浓缩，椰香清冽"},
     *   {"id":102,"name":"桂花冰酿拿铁","price":17.9,"monthSales":289,"description":"桂花米酿打底，秋日限定风味"},
     *   {"id":103,"name":"焦糖玛奇朵","price":18.9,"monthSales":173,"description":"香草奶与焦糖网的经典层次"},
     *   {"id":104,"name":"美式咖啡","price":9.9,"monthSales":412,"description":"双份浓缩，干净纯粹（无规格直接加购）"},
     *   {"id":105,"name":"澳白","price":15.9,"monthSales":198,"description":"双份 ristretto 与绵密奶泡"},
     *   {"id":106,"name":"黑糖波波奶茶","price":14.9,"monthSales":331,"description":"现熬黑糖挂壁，波波弹牙"},
     *   {"id":107,"name":"茉莉奶绿","price":12.9,"monthSales":245,"description":"茉莉花茶底与轻乳茶"},
     *   {"id":108,"name":"柚见鲜茶","price":13.9,"monthSales":88,"description":"满杯柚子果肉，清爽解腻（今日售罄，不推荐）"}],
     *  "setmeals":[
     *   {"id":201,"name":"山月双人下午茶套餐","price":39.9,"description":"生椰拿铁×2 + 黄油可颂×2 + 巴斯克芝士蛋糕×1"},
     *   {"id":202,"name":"经典咖啡轻食套餐","price":29.9,"description":"美式咖啡×1 + 燕麦曲奇×2"}]}
     * ────────────────────────────────────────────────────────────────────────────
     *
     * 一级（保底，先做，预计 1 天）：Java 直接 HTTP 调 OpenAI 兼容接口
     *  - 工程已有 RestTemplateConfiguration（RestTemplate Bean），参考苍穹外卖 HttpClient
     *    工具类的模式封装一个 AiOrderService；
     *  - application-dev.yml 追加配置（密钥用占位，换真实 key 后【不要提交到公开仓库】）：
     *      shanyue.llm.base-url: https://api.xxx.com/v1
     *      shanyue.llm.api-key: change-me-llm-key
     *      shanyue.llm.model: glm-4-flash
     *  - 请求 OpenAI 兼容的 /chat/completions：
     *      POST {base-url}/chat/completions
     *      Header: Authorization: Bearer change-me-llm-key
     *      Body: {"model":"glm-4-flash","temperature":0.7,
     *             "messages":[系统提示词, ...history 截最近 6 轮, 本轮 user 消息]}
     *  - 解析（Jackson）：readTree(resp).path("choices").get(0).path("message")
     *    .path("content").asText() 拿到模型输出 → 再 readTree 一次反序列化成
     *    reply/recommend 结构（content 本身应是一个 JSON 对象字符串）；
     *  - 解析失败降级：catch 住 JSON 解析异常，走本桩的关键词匹配版 aiReply()
     *    （把本类私有方法搬进 Service 即可）—— 前端永远拿到合法结构，页面永不白屏。
     *
     * 二级（可选，+1~2 天）：手搓 RAG —— 菜单/历史评价入库，
     *  用户消息先关键词检索出 TopN 条相关数据拼进 prompt，模型答得更"懂店"。
     *
     * 三级（可选，+2~3 天）：FastAPI + LangChain 独立 Python 服务，
     *  Spring 这边只做转发（RestTemplate 调 Python 的 /chat 接口），
     *  好处是 prompt/Agent 迭代不用重启 Java；答辩可讲"异构语言服务拆分"。
     */
    @PostMapping("/order")
    @Operation(summary = "C端-AI点单对话")
    public Result<Map<String, Object>> order(@RequestBody Map<String, Object> body) {
        Object messageRaw = body.get("message");
        String message = messageRaw == null ? "" : String.valueOf(messageRaw).trim();
        Long lastRecommendId = parseLongQuietly(body.get("lastRecommendId"));
        log.info("AI 点单对话（桩）：userId={}, message={}, lastRecommendId={}",
                BaseContext.getCurrentId(), message, lastRecommendId);

        // history 字段本桩不消费（mock 也只用当前句 + lastRecommendId）；
        // 真实实现把它转成 messages 数组拼进大模型上下文，见上方 TODO。
        return Result.success(aiReply(message, lastRecommendId));
    }

    /* ============================== 桩数据工具方法（替换桩时整体删除） ============================== */

    /**
     * 茶小月关键词匹配版应答（即真实实现的"降级兜底"分支；分支与文案对照 mock-v2.js 的 aiReply）。
     * TODO 真实实现：本方法整体搬进 AiOrderService 当 fallback，主路径换大模型。
     */
    private static Map<String, Object> aiReply(String msg, Long lastRecommendId) {
        String lower = msg.toLowerCase();

        // 问候（无推荐）
        if (startsWithAny(lower, "你好", "您好", "嗨", "hi", "hello", "在吗", "早", "晚上好")) {
            return textReply("小月参见～今日想喝点什么？说说口味、心情或预算，小月替您参谋。");
        }
        // 感谢（无推荐）
        if (containsAny(msg, "谢谢", "感谢", "多谢", "辛苦")) {
            return textReply("客官慢走～杯壁余温尚在，欢迎常来山月坐坐。");
        }
        // 换一个：月售最高的其它一杯（避开上轮推荐）
        if (containsAny(msg, "换一个", "换换", "其他", "别的", "不想喝这个")) {
            Map<String, Object> alt = pickAlternative(lastRecommendId);
            return drinkReply("好嘞，那小月换一杯为您斟上——" + alt.get("name") + "，月售 "
                            + alt.get("monthSales") + " 杯，同样不踩雷。",
                    alt, "换换口味好主意，这一杯也是店里的常胜将军");
        }
        // 预算（「15元以内」「20块」）
        Matcher budget = BUDGET_PATTERN.matcher(msg);
        if (budget.find()) {
            int limit = Integer.parseInt(budget.group(1));
            Map<String, Object> fit = bestSellerWithin(limit);
            if (fit == null) {
                Map<String, Object> cheapest = cheapest();
                return drinkReply(limit + " 元以内暂时没有合适的，最实惠的 " + cheapest.get("name")
                                + " 只要 " + cheapest.get("price") + " 元，小月先替您留意着～",
                        cheapest, "小月翻遍价目表，这是最亲民的一杯");
            }
            return drinkReply("明白，小月替您精打细算～" + fit.get("name") + " 只要 " + fit.get("price")
                            + " 元，月售 " + fit.get("monthSales") + " 杯，是 " + limit + " 元以内的人气之选。",
                    fit, limit + " 元档里的人气之选");
        }
        // 口味关键词（分支顺序与 mock-v2.js aiReply 保持一致，前面的优先命中）
        if (containsAny(msg, "睡不着", "睡眠", "低因", "失眠", "晚上", "夜里", "熬夜")) {
            return drinkReply("夜里馋茶又怕睡不好？小月给您备了茉莉奶绿——茶底轻盈，晚上喝也踏实。",
                    findDrink(107L), "茶底轻盈、咖啡因含量低，晚间饮用也安心", "中杯 · 冰 · 不另加糖");
        }
        if (containsAny(msg, "解腻", "清爽", "腻", "火锅", "油腻", "大餐")) {
            return drinkReply("刚吃完火锅吧～茉莉花茶底配轻乳，一口漱去油腻，正是解腻的好帮手。",
                    findDrink(107L), "茉莉花茶底配轻乳，清爽解腻", "中杯 · 冰 · 标准糖");
        }
        if (containsAny(msg, "招牌", "推荐", "热销", "人气", "必点", "什么好")) {
            return drinkReply("遵命～小月为您斟上本店人气第一的生椰拿铁，厚椰乳清冽不腻，喝过的客官十有八九会回头。",
                    findDrink(101L), "本店人气第一，厚椰乳清冽不腻", "中杯 · 冰 · 半糖");
        }
        if (containsAny(msg, "甜", "奶茶", "珍珠", "波波", "黑糖", "快乐水")) {
            return drinkReply("想讨点甜头呀～现熬黑糖挂壁、波波弹牙，这一杯的快乐很扎实。",
                    findDrink(106L), "现熬黑糖挂壁，波波弹牙", "大杯 · 冰 · 半糖 + 珍珠");
        }
        if (containsAny(msg, "咖啡", "提神", "困", "加班", "醒", "美式")) {
            return drinkReply("困意来袭？双份浓缩的美式最直接，干净纯粹，一口回魂。",
                    findDrink(104L), "双份浓缩干净纯粹，提神直接", "无规格，可直接加购");
        }
        if (containsAny(msg, "拿铁", "奶", "生椰")) {
            return drinkReply("想喝奶咖呀～小月推荐生椰拿铁，椰香清冽、奶感顺滑，是店里走得最快的一杯。",
                    findDrink(101L), "椰香与浓缩的经典相遇", "中杯 · 冰 · 半糖");
        }
        if (containsAny(msg, "热", "冷", "冬天", "暖", "凉")) {
            return drinkReply("天凉了，小月把炉子烧上了～焦糖玛奇朵做热饮最熨帖，暖手又暖心。",
                    findDrink(103L), "热饮最熨帖，香草奶裹焦糖网", "大杯 · 热 · 半糖");
        }
        if (containsAny(msg, "套餐", "两人", "双人", "下午茶", "朋友", "闺蜜", "同事")) {
            return setmealReply();
        }
        // 兜底：招牌
        return drinkReply("小月猜您今日想试试本店招牌——生椰拿铁，椰香清冽、月售 356 杯，十有八九不踩雷。不合心意的话，再与小月说说口味？",
                findDrink(101L), "本店招牌，椰香清冽", "中杯 · 冰 · 半糖");
    }

    /** 纯文本回复（寒暄类，无推荐卡） */
    private static Map<String, Object> textReply(String reply) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("recommend", null);
        return result;
    }

    /** 推荐一杯饮品（specSuggestion 不传时用菜单里的默认规格建议，对应 mock 的 specOf） */
    private static Map<String, Object> drinkReply(String reply, Map<String, Object> drink, String reason) {
        return drinkReply(reply, drink, reason, (String) drink.get("spec"));
    }

    private static Map<String, Object> drinkReply(String reply, Map<String, Object> drink, String reason,
                                                  String specSuggestion) {
        Map<String, Object> recommend = new LinkedHashMap<>();
        recommend.put("kind", "drink");
        recommend.put("drinkId", drink.get("id"));
        recommend.put("setmealId", null);
        recommend.put("name", drink.get("name"));
        recommend.put("price", drink.get("price"));
        recommend.put("description", drink.get("description"));
        recommend.put("monthSales", drink.get("monthSales"));
        recommend.put("reason", reason);
        recommend.put("specSuggestion", specSuggestion);
        recommend.put("image", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("recommend", recommend);
        return result;
    }

    /** 推荐双人套餐（对照 mock 的套餐分支：201 山月双人下午茶套餐） */
    private static Map<String, Object> setmealReply() {
        Map<String, Object> recommend = new LinkedHashMap<>();
        recommend.put("kind", "setmeal");
        recommend.put("drinkId", null);
        recommend.put("setmealId", 201L);
        recommend.put("name", "山月双人下午茶套餐");
        recommend.put("price", 39.9);
        recommend.put("description", "生椰拿铁×2 + 黄油可颂×2 + 巴斯克芝士蛋糕×1");
        recommend.put("monthSales", 128);
        recommend.put("reason", "双人出行性价比之选");
        recommend.put("specSuggestion", "套餐无需选规格");
        recommend.put("image", "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", "两位客官呀～山月双人下午茶套餐最合适：两杯生椰拿铁，配黄油可颂与巴斯克芝士蛋糕，39.9 元比单点省 12 元。");
        result.put("recommend", recommend);
        return result;
    }

    /** "换一个"：月售最高且不等于上轮推荐的那杯（全被排除时退回第一杯，与 mock 同口径） */
    private static Map<String, Object> pickAlternative(Long lastRecommendId) {
        Map<String, Object> best = null;
        for (Map<String, Object> d : MENU) {
            if (lastRecommendId != null && ((Number) d.get("id")).longValue() == lastRecommendId) {
                continue;
            }
            if (best == null || ((Number) d.get("monthSales")).intValue() > ((Number) best.get("monthSales")).intValue()) {
                best = d;
            }
        }
        return best != null ? best : MENU.get(0);
    }

    /** 预算内月售最高的一杯；没有则返回 null */
    private static Map<String, Object> bestSellerWithin(double limit) {
        Map<String, Object> best = null;
        for (Map<String, Object> d : MENU) {
            if (((Number) d.get("price")).doubleValue() > limit) {
                continue;
            }
            if (best == null || ((Number) d.get("monthSales")).intValue() > ((Number) best.get("monthSales")).intValue()) {
                best = d;
            }
        }
        return best;
    }

    /** 全菜单最便宜的一杯 */
    private static Map<String, Object> cheapest() {
        Map<String, Object> cheapest = MENU.get(0);
        for (Map<String, Object> d : MENU) {
            if (((Number) d.get("price")).doubleValue() < ((Number) cheapest.get("price")).doubleValue()) {
                cheapest = d;
            }
        }
        return cheapest;
    }

    private static Map<String, Object> findDrink(Long drinkId) {
        for (Map<String, Object> d : MENU) {
            if (((Number) d.get("id")).longValue() == drinkId) {
                return d;
            }
        }
        return MENU.get(0);
    }

    private static boolean containsAny(String msg, String... keys) {
        for (String key : keys) {
            if (msg.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String lowerMsg, String... keys) {
        for (String key : keys) {
            if (lowerMsg.startsWith(key)) {
                return true;
            }
        }
        return false;
    }

    /** 宽松解析前端传来的 lastRecommendId（数字或字符串都认，解析失败当没传） */
    private static Long parseLongQuietly(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> buildSuggests() {
        List<String> list = new ArrayList<>();
        list.add("睡不着，想喝低因的");
        list.add("来杯今日招牌");
        list.add("刚吃完火锅，解腻一点的");
        list.add("预算 15 元以内");
        list.add("天冷了，想喝热的");
        return list;
    }

    /** 菜单摘要（与 mock-v2.js db.drinkes 一致；spec 为展示用的默认规格建议） */
    private static List<Map<String, Object>> buildMenu() {
        List<Map<String, Object>> menu = new ArrayList<>();
        menu.add(drink(101L, "生椰拿铁", 15.9, 356, "厚椰乳遇上浓缩，椰香清冽", "中杯 · 冰 · 半糖"));
        menu.add(drink(102L, "桂花冰酿拿铁", 17.9, 289, "桂花米酿打底，秋日限定风味", "中杯 · 冰 · 半糖"));
        menu.add(drink(103L, "焦糖玛奇朵", 18.9, 173, "香草奶与焦糖网的经典层次", "大杯 · 热 · 半糖"));
        menu.add(drink(104L, "美式咖啡", 9.9, 412, "双份浓缩，干净纯粹（本款无规格，点击直接加购）", "无规格，可直接加购"));
        menu.add(drink(105L, "澳白", 15.9, 198, "双份 ristretto 与绵密奶泡", "中杯 · 热"));
        menu.add(drink(106L, "黑糖波波奶茶", 14.9, 331, "现熬黑糖挂壁，波波弹牙", "大杯 · 冰 · 半糖 + 珍珠"));
        menu.add(drink(107L, "茉莉奶绿", 12.9, 245, "茉莉花茶底与轻乳茶", "中杯 · 冰 · 不另加糖"));
        return menu;
    }

    private static Map<String, Object> drink(Long id, String name, double price, int monthSales,
                                             String description, String spec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("price", price);
        m.put("monthSales", monthSales);
        m.put("description", description);
        m.put("spec", spec);
        return m;
    }
}
