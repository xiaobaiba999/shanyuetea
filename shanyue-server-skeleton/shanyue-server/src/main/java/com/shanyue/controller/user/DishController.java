package com.shanyue.controller.user;

import com.shanyue.result.Result;
import com.shanyue.service.DishService;
import com.shanyue.vo.DishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【C 端：饮品菜单】（对照课程 DishController，山月带月售版）
 * ⚠ 路径口径：URL 用 /user/drink（与三端前端、api-docs v1.6 一致）；类名保持苍穹原名，方便对照课程。
 * └ GET /user/drink/list?categoryId=xx  按分类查询启售饮品（含规格树 + 月售）
 *
 * 【本类知识点：为什么这个 list 没有像课程那样加 Redis 缓存？（对照学习）】
 * 课程方案：GET /user/dish/list 先查 redis key "dish_分类id"，命中直接返回，
 * 未命中查库再写缓存；管理端每次增删改都 clearCache（见管理端 DishController）。
 * 山月的取舍：getList 返回的 monthSales 是【实时统计】（order_detail 聚合 SQL），
 * 缓存后月售会"冻结"在第一次缓存的时刻，管理端清除缓存前一直不准 ——
 * 教学阶段宁可每次多一条聚合 SQL，也要保证月售数字可信。
 * 若要启用缓存：把 monthSales 改成"订单完成时 +1 的冗余字段"（课程方案 A），
 * 然后放开下面的注释即可 —— 两种方案的取舍见 DishServiceImpl 类注释。
 */
@RestController("userDishController")
@RequestMapping("/user/drink")
@Tag(name = "C端-饮品接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    /**
     * C 端：按分类查询启售饮品列表
     *
     * @param categoryId 分类 id（点单页左侧分类栏点击切换）
     * @return DishVO 列表（含 specGroups 规格树 + monthSales 月售）
     */
    @GetMapping("/list")
    @Operation(summary = "C端-按分类查询饮品列表")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("C 端查询饮品列表，categoryId={}", categoryId);
        // Service 内：status=1（只出起售的）→ 查规格树 → 一次聚合 SQL 补月售
        List<DishVO> dishVOList = dishService.getList(categoryId);
        return Result.success(dishVOList);
    }

    /* —— 启用缓存后的参考写法（教学备查，需要先解决 monthSales 实时性问题） ——
    @Autowired
    private RedisTemplate redisTemplate;

    public Result<List<DishVO>> listWithCache(Long categoryId) {
        String key = "dish_" + categoryId;
        List<DishVO> cached = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isEmpty()) {
            return Result.success(cached);        // 命中缓存直接返回
        }
        List<DishVO> dishVOList = dishService.getList(categoryId);
        redisTemplate.opsForValue().set(key, dishVOList); // 查库后写缓存
        return Result.success(dishVOList);
    }
    */
}
