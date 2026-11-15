package com.shanyue.controller.user;

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
 * 【C 端：轮播横幅】功能包 F1（对照 utils/mock-v2.js 的 /user/banner/list 契约）
 * └ GET /user/banner/list  首页/菜单页顶部轮播横幅列表
 *
 * 【本类知识点：接口桩（Stub）是什么？为什么先写桩？】
 * 前端页面已经按 mock 契约开发完毕，后端表结构/Service 还没建 ——
 * 先返回一份"与 mock 完全同构"的样例数据，前端把 config.js 的 USE_MOCK
 * 改成 false 就能立刻联调页面样式；后端再逐步把样例数据换成真库查询。
 * 桩的特点：不建 VO、不建 Service、不连库，Controller 里直接内联数据，
 * 每个方法头上的 TODO 注释写清楚"换成真实现"的完整步骤。
 *
 * 【为什么 /user/** 不用改拦截器配置？】
 * WebMvcConfiguration 已注册 JwtTokenUserInterceptor 拦截 /user/**，
 * 本类路径挂在 /user/banner 下，自动被校验 token —— 与 mock 的 requireAuth 同口径。
 */
@RestController("userBannerController")
@RequestMapping("/user/banner")
@Tag(name = "C端-轮播横幅接口")
@Slf4j
public class BannerController {

    // TODO 替换桩时：文件头补 import com.shanyue.service.BannerService; 与
    //  org.springframework.beans.factory.annotation.Autowired; 再放开下面的注入
    // （桩阶段不建 Service，直接返回样例数据）：
    // @Autowired
    // private BannerService bannerService;

    /**
     * 查询启用中的轮播横幅（status=1，按 sort 升序）
     *
     * TODO 换成真实现的步骤（预计 0.5 天，建议最后做，当热身）：
     *  1. 执行 sql/v2_features.sql 建 banner 表（3 条种子已插入，与本桩返回一致）；
     *  2. shanyue-pojo 建 entity/Banner.java（字段与表列一一对应，
     *     image/linkType/linkUrl 注意驼峰映射：link_type ↔ linkType）；
     *  3. shanyue-server 建 mapper/BannerMapper.java + resources/mapper/BannerMapper.xml：
     *       SELECT * FROM banner WHERE status = 1 ORDER BY sort ASC
     *     （对照 CategoryMapper 的写法：接口零 SQL，SQL 全在 XML）；
     *  4. 建 BannerService/BannerServiceImpl，注入 BannerMapper，返回 List<Banner>；
     *  5. 本类注入 BannerService，方法体改成一行：
     *       return Result.success(bannerService.listEnabled());
     *  6. 管理端想加横幅管理的话，照抄既有 controller/admin/DrinkController 的 CRUD 套路：
     *     新增/修改/启用禁用/按条件分页（路径挂 /admin/banner/**，被 JwtTokenAdminInterceptor 拦截）。
     *
     * 前端契约（utils/mock-v2.js SEED_BANNERS）：
     *   [{id, title, subtitle, theme('pine'|'gold'|'terracotta'), image:"", linkType:'page',
     *     linkUrl, sort, status}]
     *   image 为空串时前端按 theme 渲染新中式渐变卡；管理端上传图片后回传 URL 即可切真图。
     */
    @GetMapping("/list")
    @Operation(summary = "C端-查询轮播横幅列表")
    public Result<List<Map<String, Object>>> list() {
        log.info("查询轮播横幅列表（桩：返回 mock 同构样例数据）");

        List<Map<String, Object>> banners = new ArrayList<>();
        banners.add(banner(1L, "桂花冰酿 · 秋日上新", "第二杯半价 · 限时一周", "pine",
                "/pages/menu/menu", 1, 1));
        banners.add(banner(2L, "每日签到 · 茶香不断", "连签 7 天额外得 50 积分", "gold",
                "/pages/checkin/checkin", 2, 1));
        banners.add(banner(3L, "新人见面礼", "满 30 减 8 券包待领取", "terracotta",
                "/pages/coupons/coupons", 3, 1));
        return Result.success(banners);
    }

    /** 构造一条横幅样例（LinkedHashMap 保持字段顺序且允许空值，与 mock 字段一一对应） */
    private static Map<String, Object> banner(Long id, String title, String subtitle, String theme,
                                              String linkUrl, Integer sort, Integer status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("theme", theme);
        m.put("image", "");
        m.put("linkType", "page");
        m.put("linkUrl", linkUrl);
        m.put("sort", sort);
        m.put("status", status);
        return m;
    }
}
