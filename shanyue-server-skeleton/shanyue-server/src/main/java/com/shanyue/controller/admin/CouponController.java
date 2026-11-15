package com.shanyue.controller.admin;

import com.shanyue.exception.BaseException;
import com.shanyue.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【管理端：优惠券模板管理】功能包 F4 · B 端（对照 admin 前端 /api/coupon/* 契约，模块 14）
 * ┌ GET    /admin/coupon/page              分页查询（name/status 筛选；发放中在前、同状态按创建时间倒序）
 * ├ POST   /admin/coupon                   新增券模板（名称唯一、满减门槛≥面额、总量>0 等校验）
 * ├ PUT    /admin/coupon                  修改券模板（总量不可小于已领取张数）
 * ├ POST   /admin/coupon/status/{status}?id=  上架/下架（下架仅影响 C 端券广场可见性）
 * └ DELETE /admin/coupon?id=               删除券模板（已有用户领取的模板禁止物理删除，只能下架）
 *
 * 【本类知识点：模板 / 用户券两层模型 + 生命周期】
 *  coupon（模板，本接口管）与 user_coupon（用户券，C 端领出来的一条条凭证）是两层：
 *  模板 = 发行计划（面额/门槛/总量/有效期），用户券 = 凭证实例（谁、什么时候到期、用了没）。
 *  生命周期：创建 → 上架（C 端可见可领）→ 下架（不可再领，已领的不受影响）→ 删除
 *  （仅当从未有人领过才允许物理删；有领取记录只能下架——保护已发出券的可用性）。
 *
 * TODO 换成真实现的步骤（预计 1~1.5 天）：
 *  1. 执行 sql/v2_features.sql 第 2 节：coupon 表已建（4 张种子模板）；
 *     注意口径映射：coupon 表 type=1 满减 / 2 折扣（88 折存 discount_rate）+ seckill 秒杀位，
 *     而 B 端页面契约是 type=1 满减 / 2 无门槛立减（threshold=0、discount 存面额）——
 *     两端口径不完全一致，真实现时在 Service 做视图转换（或统一两端契约），
 *     这是真实的接口契约治理问题，答辩可以当作讲点（不建议无脑改表迁就某一端）。
 *  2. shanyue-pojo 建 entity/Coupon.java + dto/CouponDTO.java / CouponPageQueryDTO.java
 *     （字段与本桩返回的 Map 键一一对应：name/type/threshold/discount/validDays/totalCount/…）；
 *  3. CouponService + CouponMapper：page 用 PageHelper（对照员工分页的写法），
 *     写操作补 create_time/update_time（@AutoFill 公共字段填充已就绪，加注解即可）；
 *  4. B 端这几个写操作同样要考虑与 C 端缓存/Redis 的一致性：下架后 C 端 list 要立刻不可见
 *     （本工程 C 端券列表未启用 Redis 缓存，读库即生效；启用缓存时记得同步清 key）。
 *
 * 本桩数据与 admin 前端 mock（mock/seed.ts 的 seedCoupons）一字不差，
 * 页面在 mock 模式与真实后端之间无感切换。
 */
@RestController("adminCouponController")
@RequestMapping("/admin/coupon")
@Tag(name = "B端-优惠券模板接口")
@Slf4j
public class CouponController {

    /** 演示用时间格式（与前端 mock 的 now() 一致：yyyy-MM-dd HH:mm:ss） */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 券模板种子（与 admin 前端 mock 的 seedCoupons 一致；真实实现读 coupon 表） */
    private static final List<Map<String, Object>> SEED = buildSeed();

    /** 内存券模板表（桩演示够用；真实实现换成 coupon 表） */
    private final List<Map<String, Object>> templates = new CopyOnWriteArrayList<>(SEED);

    /** 新券模板自增 id（mock 的 couponSeq 从 100 起） */
    private final AtomicLong seq = new AtomicLong(100);

    /**
     * 14.1 券模板分页查询
     */
    @GetMapping("/page")
    @Operation(summary = "B端-优惠券模板分页查询")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) Integer status) {
        log.info("券模板分页查询（桩）：page={}, pageSize={}, name={}, status={}", page, pageSize, name, status);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> c : templates) {
            if (name != null && !name.trim().isEmpty()
                    && !String.valueOf(c.get("name")).contains(name.trim())) {
                continue;
            }
            if (status != null && (status == 0 || status == 1)
                    && ((Number) c.get("status")).intValue() != status) {
                continue;
            }
            list.add(new LinkedHashMap<>(c));
        }
        // 发放中的在前，同状态按创建时间倒序（与 mock 同款排序）
        list.sort((a, b) -> {
            int byStatus = ((Number) b.get("status")).intValue() - ((Number) a.get("status")).intValue();
            if (byStatus != 0) return byStatus;
            return String.valueOf(b.get("createTime")).compareTo(String.valueOf(a.get("createTime")));
        });

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
     * 14.2 新增券模板
     */
    @PostMapping
    @Operation(summary = "B端-新增优惠券模板")
    public Result save(@RequestBody Map<String, Object> dto) {
        log.info("新增券模板（桩）：{}", dto);
        String err = validate(dto, null);
        if (err != null) {
            throw new BaseException(err);
        }
        Map<String, Object> tpl = fill(dto, null);
        templates.add(tpl);
        return Result.success(null);
    }

    /**
     * 14.3 修改券模板（总量不可小于已领取张数）
     */
    @PutMapping
    @Operation(summary = "B端-修改优惠券模板")
    public Result update(@RequestBody Map<String, Object> dto) {
        log.info("修改券模板（桩）：{}", dto);
        Long id = longOf(dto.get("id"));
        Map<String, Object> tpl = findById(id);
        if (tpl == null) {
            throw new BaseException("优惠券不存在");
        }
        String err = validate(dto, id);
        if (err != null) {
            throw new BaseException(err);
        }
        int received = receivedOf(tpl);
        long totalCount = longOf(dto.get("totalCount"));
        if (totalCount < received) {
            throw new BaseException("发放总量不能小于已领取张数（" + received + " 张），如需止损请下架");
        }
        fill(dto, tpl);
        // 保持「已领取」不变：剩余 = 总量 - 已领
        tpl.put("remainCount", Math.max(0, totalCount - received));
        return Result.success(null);
    }

    /**
     * 14.4 上架/下架（只影响 C 端券广场可见性，不影响已领取未使用的券）
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "B端-上架/下架优惠券模板")
    public Result status(@PathVariable Integer status, @RequestParam Long id) {
        log.info("上/下架券模板（桩）：id={}, status={}", id, status);
        Map<String, Object> tpl = findById(id);
        if (tpl == null) {
            throw new BaseException("优惠券不存在");
        }
        tpl.put("status", status);
        tpl.put("updateTime", LocalDateTime.now().format(FMT));
        return Result.success(null);
    }

    /**
     * 14.5 删除券模板（已有用户领取的模板禁止物理删除——保护已发出券的可用性）
     */
    @DeleteMapping
    @Operation(summary = "B端-删除优惠券模板")
    public Result remove(@RequestParam Long id) {
        log.info("删除券模板（桩）：id={}", id);
        Map<String, Object> tpl = findById(id);
        if (tpl == null) {
            throw new BaseException("优惠券不存在");
        }
        if (receivedOf(tpl) > 0) {
            throw new BaseException("该券已有用户领取，不可删除；如需停止发放请下架");
        }
        templates.remove(tpl);
        return Result.success(null);
    }

    /* ============================ 桩内部工具（替换真实现时整段删除） ============================ */

    /** 已领取张数 = 发放总量 - 剩余可领 */
    private int receivedOf(Map<String, Object> tpl) {
        return (int) Math.max(0, longOf(tpl.get("totalCount")) - longOf(tpl.get("remainCount")));
    }

    /** 按 id 内存查找 */
    private Map<String, Object> findById(Long id) {
        for (Map<String, Object> c : templates) {
            if (longOf(c.get("id")).equals(id)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 校验（与 admin mock 的 validate 一字不差，保证 mock 模式与真后端行为一致）
     */
    private String validate(Map<String, Object> dto, Long selfId) {
        String name = dto.get("name") == null ? "" : String.valueOf(dto.get("name")).trim();
        if (name.isEmpty()) {
            return "优惠券名称不能为空";
        }
        if (name.length() > 20) {
            return "优惠券名称不能超过 20 个字";
        }
        for (Map<String, Object> c : templates) {
            if (selfId == null || !longOf(c.get("id")).equals(selfId)) {
                if (String.valueOf(c.get("name")).equals(name)) {
                    return "优惠券名称已存在";
                }
            }
        }
        int type = intOf(dto.get("type"));
        double discount = dto.get("discount") == null ? 0 : Double.parseDouble(String.valueOf(dto.get("discount")));
        if (discount <= 0) {
            return "优惠金额必须大于 0";
        }
        if (type == 1) {
            double threshold = dto.get("threshold") == null ? 0 : Double.parseDouble(String.valueOf(dto.get("threshold")));
            if (threshold < discount) {
                return "满减券的使用门槛不能低于优惠金额（如满 25 减 4）";
            }
        }
        if (intOf(dto.get("validDays")) <= 0) {
            return "有效期天数必须大于 0";
        }
        if (intOf(dto.get("totalCount")) <= 0) {
            return "发放总量必须大于 0";
        }
        return null;
    }

    /** 新增/修改共用的字段填充（type=2 无门槛券 threshold 归零 + 描述兜底，与 mock 同款） */
    private Map<String, Object> fill(Map<String, Object> dto, Map<String, Object> target) {
        int type = intOf(dto.get("type"));
        double threshold = type == 2 ? 0
                : Double.parseDouble(String.valueOf(dto.get("threshold")));
        double discount = Double.parseDouble(String.valueOf(dto.get("discount")));
        String now = LocalDateTime.now().format(FMT);

        Map<String, Object> tpl = target != null ? target : new LinkedHashMap<String, Object>();
        if (target == null) {
            tpl.put("id", seq.incrementAndGet());
            tpl.put("status", 1);          // 新券默认上架（与 mock 一致）
            tpl.put("createTime", now);
            tpl.put("remainCount", intOf(dto.get("totalCount")));   // 新券：剩余=总量
        }
        tpl.put("name", String.valueOf(dto.get("name")).trim());
        tpl.put("type", type);
        tpl.put("threshold", threshold);
        tpl.put("discount", discount);
        tpl.put("validDays", intOf(dto.get("validDays")));
        tpl.put("totalCount", intOf(dto.get("totalCount")));
        tpl.put("description", strOr(dto.get("description"),
                type == 2 ? "无门槛立减 " + fmtMoney(discount) + " 元" : "满 " + fmtMoney(threshold) + " 元减 " + fmtMoney(discount) + " 元"));
        tpl.put("updateTime", now);
        return tpl;
    }

    /** 金额展示（整数不带小数点，与前端 toFixed 口径近似；仅桩演示用） */
    private String fmtMoney(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String strOr(Object v, String def) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private long longOf(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v).replace(".0", ""));
    }

    private int intOf(Object v) {
        return v == null ? 0 : (int) Double.parseDouble(String.valueOf(v));
    }

    /** 种子（与 admin 前端 mock 的 seedCoupons 一字不差；id 2 的库存 34/200=17% 是「库存告急」演示样例） */
    private static List<Map<String, Object>> buildSeed() {
        String t = "2026-01-01 08:00:00";
        List<Map<String, Object>> seed = new ArrayList<>();
        seed.add(row(1L, "山月新人礼", 2, 0, 5, 7, 999, 999, 1, "无门槛立减 5 元，新客见面礼", t));
        seed.add(row(2L, "午后轻松享", 1, 25, 4, 7, 200, 34, 1, "满 25 元减 4 元", t));
        seed.add(row(3L, "闺蜜同行券", 1, 40, 8, 7, 100, 100, 1, "满 40 元减 8 元", t));
        seed.add(row(4L, "开业大酬宾", 1, 20, 6, 3, 300, 274, 0, "满 20 元减 6 元", t));
        return seed;
    }

    private static Map<String, Object> row(Long id, String name, int type, int threshold, int discount,
                                          int validDays, int totalCount, int remainCount, int status,
                                          String description, String time) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type);
        m.put("threshold", threshold);
        m.put("discount", discount);
        m.put("validDays", validDays);
        m.put("totalCount", totalCount);
        m.put("remainCount", remainCount);
        m.put("status", status);
        m.put("description", description);
        m.put("createTime", time);
        m.put("updateTime", time);
        return m;
    }
}
